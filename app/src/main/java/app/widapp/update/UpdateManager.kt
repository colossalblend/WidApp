package app.widapp.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import app.widapp.BuildConfig
import app.widapp.WidApp
import app.widapp.core.AppJson
import app.widapp.core.JsonFileStore
import app.widapp.core.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Over-the-air updates from the GitHub Releases of the public repository.
 * Check → download (with progress) → verify size, SHA-256 and signing certificate → hand the APK to PackageInstaller.
 */
class UpdateManager(private val app: WidApp) {
    companion object {
        const val REPO = "colossalblend/WidApp"
        const val ACTION_INSTALL_STATUS = "app.widapp.update.INSTALL_STATUS"
        private const val AUTO_CHECK_INTERVAL = 6 * 60 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val cacheStore = JsonFileStore(File(app.filesDir, "store/updates.json"), UpdateCache.serializer()) { UpdateCache() }
    val cache: StateFlow<UpdateCache> get() = cacheStore.flow

    private val _phase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
    val phase: StateFlow<UpdatePhase> = _phase.asStateFlow()
    private var job: Job? = null

    val current: Semver = Semver.parse(BuildConfig.VERSION_NAME) ?: Semver(0, 0, 0, null)

    /** The newest release worth offering (respects beta opt-in and "skip this version"). */
    val available: StateFlow<ReleaseInfo?> = combine(cacheStore.flow, app.graph.settings.flow) { c, s ->
        newest(c.releases, s.updateBeta)?.takeIf { it.version != s.skippedVersion }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun newest(list: List<ReleaseInfo>, beta: Boolean): ReleaseInfo? = list
        .filter { it.apk != null && (beta || !it.prerelease) }
        .mapNotNull { r -> Semver.parse(r.version)?.let { it to r } }
        .filter { it.first > current }
        .maxByOrNull { it.first }?.second

    fun releaseFor(version: String): ReleaseInfo? = cache.value.releases.firstOrNull { it.version == version }

    // ---------- Check ----------

    fun checkInBackground() {
        val s = app.graph.settings.value
        if (!s.updateAutoCheck) return
        if (System.currentTimeMillis() - cache.value.lastCheck < AUTO_CHECK_INTERVAL) { settleIdle(); return }
        scope.launch { runCatching { fetch() }; settleIdle() }
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _phase.value = UpdatePhase.Checking
            val started = System.currentTimeMillis()
            val result = runCatching { fetch() }
            // Let the indicator breathe for a moment; an instant flash reads as broken.
            delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            result.onFailure { _phase.value = UpdatePhase.Failed(it.message ?: "GitHub", null); return@launch }
            settleIdle(forceUpToDate = true)
        }
    }

    private fun settleIdle(forceUpToDate: Boolean = false) {
        val p = _phase.value
        if (p is UpdatePhase.Downloading || p is UpdatePhase.Verifying || p is UpdatePhase.Ready || p is UpdatePhase.Installing || p is UpdatePhase.NeedsPermission) return
        val newest = newest(cache.value.releases, app.graph.settings.value.updateBeta)
        _phase.value = when {
            newest != null -> UpdatePhase.Available(newest)
            forceUpToDate || cache.value.lastCheck > 0 -> UpdatePhase.UpToDate(cache.value.releases.firstOrNull { !it.prerelease })
            else -> UpdatePhase.Idle
        }
    }

    /** Pulls the release list; uses the ETag so repeated checks don't spend the 60/h anonymous quota. */
    suspend fun fetch(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WidApp/${BuildConfig.VERSION_NAME} (Android)")
            .apply { cache.value.etag?.let { header("If-None-Match", it) } }
            .build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 304 -> {
                    cacheStore.update { it.copy(lastCheck = System.currentTimeMillis()) }
                    return@withContext cache.value.releases
                }
                r.code == 403 || r.code == 429 -> throw IllegalStateException(L.t("GitHub временно ограничил запросы. Попробуйте позже.", "GitHub rate limit reached. Try again later."))
                r.code == 404 -> throw IllegalStateException(L.t("Репозиторий не найден", "Repository not found"))
                !r.isSuccessful -> throw IllegalStateException("GitHub: HTTP ${r.code}")
            }
            val arr = AppJson.parseToJsonElement(r.body.string()) as? JsonArray ?: JsonArray(emptyList())
            val releases = arr.mapNotNull { (it as? JsonObject)?.let(::parseRelease) }
            cacheStore.update { UpdateCache(System.currentTimeMillis(), r.header("ETag"), releases) }
            releases
        }
    }

    private fun parseRelease(o: JsonObject): ReleaseInfo? {
        fun str(k: String, src: JsonObject = o) = (src[k] as? JsonPrimitive)?.contentOrNull
        if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val tag = str("tag_name") ?: return null
        val apk = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { str("name", it)?.endsWith(".apk", true) == true }
            ?.sortedByDescending { a -> val n = str("name", a).orEmpty().lowercase(); (if ("release" in n) 2 else 0) + (if ("debug" in n) -1 else 0) }
            ?.firstOrNull()?.let { a ->
                ReleaseAsset(
                    name = str("name", a)!!,
                    size = (a["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                    url = str("browser_download_url", a) ?: return@let null,
                    digest = str("digest", a),
                    downloads = (a["download_count"] as? JsonPrimitive)?.intOrNull ?: 0,
                )
            }
        return ReleaseInfo(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            title = str("name")?.ifBlank { null } ?: tag,
            notes = str("body").orEmpty(),
            publishedAt = str("published_at") ?: str("created_at").orEmpty(),
            prerelease = (o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true,
            htmlUrl = str("html_url") ?: "https://github.com/$REPO/releases",
            apk = apk,
        )
    }

    fun skip(release: ReleaseInfo) {
        app.graph.settings.update { it.copy(skippedVersion = release.version) }
        _phase.value = UpdatePhase.UpToDate(release)
    }

    // ---------- Download & verify ----------

    private fun apkFile(r: ReleaseInfo) = File(File(app.cacheDir, "updates").apply { mkdirs() }, "WidApp-${r.version}.apk")

    fun download(release: ReleaseInfo) {
        val asset = release.apk ?: return
        job?.cancel()
        job = scope.launch {
            val file = apkFile(release)
            try {
                if (!(file.exists() && file.length() == asset.size)) {
                    _phase.value = UpdatePhase.Downloading(release, 0, asset.size, 0)
                    val part = File(file.path + ".part")
                    http.newCall(Request.Builder().url(asset.url).header("User-Agent", "WidApp").build()).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException(L.t("Не удалось скачать", "Download failed") + " (HTTP ${r.code})")
                        val total = r.body.contentLength().takeIf { it > 0 } ?: asset.size
                        r.body.byteStream().use { input ->
                            part.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                var read = 0L
                                var lastEmit = 0L
                                val start = System.nanoTime()
                                while (isActive) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n); read += n
                                    val now = System.nanoTime()
                                    if (now - lastEmit > 80_000_000L) {
                                        lastEmit = now
                                        val secs = (now - start) / 1e9
                                        _phase.value = UpdatePhase.Downloading(release, read, total, if (secs > 0) (read / secs).toLong() else 0)
                                    }
                                }
                                _phase.value = UpdatePhase.Downloading(release, read, total, 0)
                            }
                        }
                    }
                    if (!isActive) { part.delete(); return@launch }
                    part.renameTo(file)
                }
                verify(release, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _phase.value = UpdatePhase.Failed(e.message ?: e.javaClass.simpleName, release)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        settleIdle()
    }

    private suspend fun verify(release: ReleaseInfo, file: File) {
        val asset = release.apk!!
        val done = mutableSetOf<VerifyStep>()
        val skipped = mutableSetOf<VerifyStep>()
        _phase.value = UpdatePhase.Verifying(release, done.toSet())
        delay(250)

        if (asset.size > 0 && file.length() != asset.size) fail(file, L.t("Файл скачан не полностью", "The download is incomplete"))
        done += VerifyStep.Size; _phase.value = UpdatePhase.Verifying(release, done.toSet()); delay(300)

        val expected = asset.digest?.takeIf { it.startsWith("sha256:", true) }?.substringAfter(':')?.lowercase()
        if (expected != null) {
            val actual = sha256(file)
            if (actual != expected) fail(file, L.t("Контрольная сумма не совпала — файл повреждён", "Checksum mismatch — the file is corrupted"))
        } else skipped += VerifyStep.Checksum
        done += VerifyStep.Checksum; _phase.value = UpdatePhase.Verifying(release, done.toSet(), skipped.toSet()); delay(300)

        checkPackage(file)
        done += VerifyStep.Signature; _phase.value = UpdatePhase.Verifying(release, done.toSet(), skipped.toSet()); delay(350)

        _phase.value = UpdatePhase.Ready(release, file, skipped.toSet())
    }

    private fun fail(file: File, message: String): Nothing { file.delete(); throw IllegalStateException(message) }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Same package, newer build, signed with the same certificate as the installed app. */
    @Suppress("DEPRECATION")
    private fun checkPackage(file: File) {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: fail(file, L.t("Файл не является APK", "The file is not an APK"))
        if (archive.packageName != app.packageName) fail(file, L.t("Это APK другого приложения", "This APK belongs to another app"))
        val installed = pm.getPackageInfo(app.packageName, flags)
        if (archive.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            fail(file, L.t("Версия в APK не новее установленной", "The APK is not newer than the installed version"))
        }
        val a = certs(archive); val b = certs(installed)
        if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
            fail(file, L.t("Подпись APK не совпадает с установленным приложением", "The APK signature does not match the installed app"))
        }
    }

    @Suppress("DEPRECATION")
    private fun certs(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } else p.signatures
        return sigs?.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }?.toSet() ?: emptySet()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    // ---------- Install ----------

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    fun install(release: ReleaseInfo, file: File) {
        if (!canInstall()) { _phase.value = UpdatePhase.NeedsPermission(release, file); return }
        job?.cancel()
        job = scope.launch {
            try {
                _phase.value = UpdatePhase.Installing(release)
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    if (Build.VERSION.SDK_INT >= 26) setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(app, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                _phase.value = UpdatePhase.Failed(e.message ?: e.javaClass.simpleName, release)
            }
        }
    }

    internal fun onInstallStatus(status: Int, message: String?) {
        val release = (_phase.value as? UpdatePhase.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // the process is replaced right after this
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                val r = release ?: return settleIdle()
                _phase.value = UpdatePhase.Ready(r, apkFile(r), emptySet())
            }
            else -> _phase.value = UpdatePhase.Failed(message ?: L.t("Установка не удалась", "Install failed"), release)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun seedForTest(releases: List<ReleaseInfo>, phase: UpdatePhase) {
        cacheStore.update { UpdateCache(System.currentTimeMillis(), null, releases) }
        _phase.value = phase
    }

    /** Removes downloaded APKs that are already installed. */
    fun cleanup() = scope.launch {
        File(app.cacheDir, "updates").listFiles()?.forEach { f ->
            val v = Semver.parse(f.name.removePrefix("WidApp-").removeSuffix(".apk").removeSuffix(".part"))
            if (v == null || v <= current) f.delete()
        }
    }
}

/** Receives PackageInstaller results; asks the user to confirm when Android requires it. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            return
        }
        (context.applicationContext as WidApp).updates.onInstallStatus(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
