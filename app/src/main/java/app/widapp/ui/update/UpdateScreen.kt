package app.widapp.ui.update

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.widapp.BuildConfig
import app.widapp.R
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.Markdown
import app.widapp.ui.components.RollingText
import app.widapp.ui.components.dotGrid
import app.widapp.ui.components.rememberApp
import app.widapp.ui.theme.MonoBody
import app.widapp.ui.theme.MonoLabel
import app.widapp.update.ReleaseInfo
import app.widapp.update.UpdateManager
import app.widapp.update.UpdatePhase
import app.widapp.update.UpdateWorker
import app.widapp.update.VerifyStep
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import kotlin.random.Random

fun formatBytes(b: Long): String = when {
    b >= 1_048_576 -> "%.1f ".format(b / 1_048_576.0) + app.widapp.core.L.t("МБ", "MB")
    b >= 1024 -> "%.0f ".format(b / 1024.0) + app.widapp.core.L.t("КБ", "KB")
    else -> "$b " + app.widapp.core.L.t("Б", "B")
}

fun releaseDate(iso: String): String = runCatching {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(Instant.parse(iso)))
}.getOrDefault(iso.take(10))

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val app = rememberApp()
    val updates = app.updates
    val phase by updates.phase.collectAsState()
    val cache by updates.cache.collectAsState()
    val settings by app.graph.settings.flow.collectAsState()
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { if (phase is UpdatePhase.Idle || phase is UpdatePhase.UpToDate || phase is UpdatePhase.Failed) updates.check() }
    // Coming back from "Install unknown apps" settings: continue automatically.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        (phase as? UpdatePhase.NeedsPermission)?.let { if (updates.canInstall()) updates.install(it.release, it.file) }
    }

    val release: ReleaseInfo? = when (val p = phase) {
        is UpdatePhase.Available -> p.release
        is UpdatePhase.Downloading -> p.release
        is UpdatePhase.Verifying -> p.release
        is UpdatePhase.Ready -> p.release
        is UpdatePhase.NeedsPermission -> p.release
        is UpdatePhase.Installing -> p.release
        is UpdatePhase.Failed -> p.release
        else -> null
    }
    val notesRelease = release ?: cache.releases.firstOrNull { it.version == BuildConfig.VERSION_NAME }

    Scaffold(
        containerColor = c.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Hero(phase, release) }
            item { Actions(phase, onSettings = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }) }
            if (notesRelease != null && notesRelease.notes.isNotBlank()) item {
                Section(
                    if (release != null) stringResource(R.string.update_whats_new_in, notesRelease.version)
                    else stringResource(R.string.update_this_version, notesRelease.version),
                ) { Markdown(notesRelease.notes) }
            }
            item {
                Section(stringResource(R.string.update_prefs)) {
                    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        app.graph.settings.update { it.copy(updateNotify = granted) }
                    }
                    Toggle(stringResource(R.string.update_auto), stringResource(R.string.update_auto_hint), settings.updateAutoCheck) { v ->
                        app.graph.settings.update { it.copy(updateAutoCheck = v) }
                    }
                    Toggle(stringResource(R.string.update_beta), stringResource(R.string.update_beta_hint), settings.updateBeta) { v ->
                        app.graph.settings.update { it.copy(updateBeta = v, skippedVersion = null) }; updates.check()
                    }
                    Toggle(stringResource(R.string.update_notify), stringResource(R.string.update_notify_hint), settings.updateNotify && UpdateWorker.canNotify(context)) { v ->
                        if (v && Build.VERSION.SDK_INT >= 33 && !UpdateWorker.canNotify(context)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else app.graph.settings.update { it.copy(updateNotify = v) }
                    }
                }
            }
            val history = cache.releases.filter { it.version != release?.version }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); Kicker(stringResource(R.string.update_history)) }
                items(history, key = { it.tag }) { r -> HistoryItem(r) }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("github.com/${UpdateManager.REPO}", style = MonoLabel, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.htmlUrl ?: "https://github.com/${UpdateManager.REPO}/releases")))
                    }) {
                        Text(stringResource(R.string.update_open_github)); Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_north_east), null, Modifier.size(16.dp))
                    }
                }
                if (cache.lastCheck > 0) Text(
                    stringResource(R.string.update_checked_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(cache.lastCheck))),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
    }
}

/** Big version readout on a dot-grid plate, with a shape badge that reflects the state. */
@Composable
private fun Hero(phase: UpdatePhase, release: ReleaseInfo?) {
    val c = MaterialTheme.colorScheme
    val current = BuildConfig.VERSION_NAME
    val busy = phase is UpdatePhase.Checking || phase is UpdatePhase.Downloading || phase is UpdatePhase.Verifying || phase is UpdatePhase.Installing

    // While checking, the digits shuffle like a slot machine; then they settle on the answer.
    val shuffled by produceState(current, phase is UpdatePhase.Checking) {
        while (phase is UpdatePhase.Checking) {
            value = current.map { if (it.isDigit()) ('0' + Random.nextInt(10)) else it }.joinToString("")
            delay(110)
        }
        value = current
    }
    val shown = when {
        phase is UpdatePhase.Checking -> shuffled
        release != null -> release.version
        else -> current
    }
    val plate by animateColorAsState(
        when (phase) {
            is UpdatePhase.UpToDate -> c.secondaryContainer
            is UpdatePhase.Failed -> c.errorContainer
            is UpdatePhase.Checking, UpdatePhase.Idle -> c.surfaceContainerLow
            else -> c.inverseSurface
        }, tween(500), label = "plate",
    )
    val ink by animateColorAsState(
        when (phase) {
            is UpdatePhase.UpToDate -> c.onSecondaryContainer
            is UpdatePhase.Failed -> c.onErrorContainer
            is UpdatePhase.Checking, UpdatePhase.Idle -> c.onSurface
            else -> c.inverseOnSurface
        }, tween(500), label = "ink",
    )
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "angle")

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(plate)
            .dotGrid(ink.copy(alpha = 0.10f)).padding(24.dp),
    ) {
        Column {
            AnimatedContent(kickerFor(phase), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "kicker") { k ->
                Kicker(stringResource(k), color = ink.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(18.dp))
            RollingText(shown, MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp), ink)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(subtitle(phase, release, current), transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }, label = "sub") { s ->
                Text(s, style = MonoBody, color = ink.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // State badge in the corner: rotates slowly while work is in progress, pops in when done.
        val badgeShape = when (phase) {
            is UpdatePhase.UpToDate -> MaterialShapes.Cookie9Sided
            is UpdatePhase.Failed -> MaterialShapes.Diamond
            is UpdatePhase.Available, is UpdatePhase.Ready -> MaterialShapes.SoftBurst
            else -> MaterialShapes.Cookie6Sided
        }
        val icon = when (phase) {
            is UpdatePhase.UpToDate -> R.drawable.ic_check
            is UpdatePhase.Failed -> R.drawable.ic_error
            is UpdatePhase.Ready, is UpdatePhase.NeedsPermission -> R.drawable.ic_check_circle
            is UpdatePhase.Installing -> R.drawable.ic_mobile_arrow_up_right
            else -> R.drawable.ic_download
        }
        AnimatedContent(phase::class, Modifier.align(Alignment.TopEnd), transitionSpec = {
            scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), initialScale = 0.4f) + fadeIn() togetherWith fadeOut(tween(120))
        }, label = "badge") {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(56.dp).rotate(if (busy) angle else 0f).clip(badgeShape.toShape()).background(c.tertiary))
                Icon(painterResource(icon), null, Modifier.size(24.dp), tint = c.onTertiary)
            }
        }
    }
}

private fun kickerFor(p: UpdatePhase): Int = when (p) {
    is UpdatePhase.Checking -> R.string.update_k_checking
    is UpdatePhase.UpToDate -> R.string.update_k_latest
    is UpdatePhase.Available -> R.string.update_k_available
    is UpdatePhase.Downloading -> R.string.update_k_downloading
    is UpdatePhase.Verifying -> R.string.update_k_verifying
    is UpdatePhase.Ready, is UpdatePhase.NeedsPermission -> R.string.update_k_ready
    is UpdatePhase.Installing -> R.string.update_k_installing
    is UpdatePhase.Failed -> R.string.update_k_failed
    UpdatePhase.Idle -> R.string.update_k_version
}

@Composable
private fun subtitle(p: UpdatePhase, r: ReleaseInfo?, current: String): String = when {
    p is UpdatePhase.Failed -> p.message
    p is UpdatePhase.UpToDate -> stringResource(R.string.update_sub_latest, current)
    p is UpdatePhase.Checking -> stringResource(R.string.update_sub_checking)
    r != null -> listOfNotNull(
        stringResource(R.string.update_sub_from, current),
        r.apk?.let { formatBytes(it.size) },
        releaseDate(r.publishedAt),
        if (r.prerelease) "beta" else null,
    ).joinToString(" · ")
    else -> stringResource(R.string.update_sub_installed)
}

@Composable
private fun Actions(phase: UpdatePhase, onSettings: () -> Unit) {
    val app = rememberApp()
    val u = app.updates
    val c = MaterialTheme.colorScheme
    AnimatedContent(
        phase::class,
        transitionSpec = { (fadeIn(tween(220)) + expandVertically()) togetherWith (fadeOut(tween(120)) + shrinkVertically()) },
        label = "actions",
    ) { _ ->
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            when (val p = phase) {
                is UpdatePhase.Available -> {
                    BigButton(stringResource(R.string.update_download, p.release.apk?.let { formatBytes(it.size) } ?: ""), R.drawable.ic_download) { u.download(p.release) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { u.skip(p.release) }) { Text(stringResource(R.string.update_skip)) }
                    }
                }
                is UpdatePhase.Downloading -> Downloading(p)
                is UpdatePhase.Verifying -> Checklist(p.done, p.skipped, p.release.apk?.size ?: 0)
                is UpdatePhase.Ready -> {
                    Checklist(VerifyStep.entries.toSet(), p.skipped, p.release.apk?.size ?: 0)
                    Spacer(Modifier.height(14.dp))
                    BigButton(stringResource(R.string.update_install), R.drawable.ic_mobile_arrow_up_right) { u.install(p.release, p.file) }
                }
                is UpdatePhase.NeedsPermission -> {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerLow).padding(18.dp)) {
                        Text(stringResource(R.string.update_perm_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.update_perm_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    BigButton(stringResource(R.string.update_perm_action), R.drawable.ic_lock, onClick = onSettings)
                }
                is UpdatePhase.Installing -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(44.dp), color = c.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.update_installing_hint), style = MaterialTheme.typography.bodyLarge)
                }
                is UpdatePhase.Failed -> FilledTonalButton(onClick = { p.release?.let(u::download) ?: u.check() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ic_refresh), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.update_retry))
                }
                is UpdatePhase.Checking -> Spacer(Modifier.height(56.dp))
                else -> FilledTonalButton(onClick = u::check, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ic_refresh), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.update_check))
                }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary),
    ) {
        Icon(painterResource(icon), null); Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Downloading(p: UpdatePhase.Downloading) {
    val c = MaterialTheme.colorScheme
    val target = if (p.total > 0) (p.read.toFloat() / p.total).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, spring(stiffness = Spring.StiffnessLow), label = "dl")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerLow).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText("${(target * 100).toInt()}", MaterialTheme.typography.displaySmall, c.onSurface)
            Text("%", style = MaterialTheme.typography.headlineMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(14.dp), color = c.tertiary, trackColor = c.surfaceContainerHighest)
        Spacer(Modifier.height(12.dp))
        Row {
            Text("${formatBytes(p.read)} / ${formatBytes(p.total)}", style = MonoLabel, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (p.bytesPerSec > 0) {
                val left = ((p.total - p.read) / p.bytesPerSec.coerceAtLeast(1)).coerceAtLeast(0)
                Text("${formatBytes(p.bytesPerSec)}/" + app.widapp.core.L.t("с", "s") + " · ~$left " + app.widapp.core.L.t("с", "s"), style = MonoLabel, color = c.onSurfaceVariant)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val app = rememberApp()
        TextButton(onClick = app.updates::cancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

/** Three checks that tick off one by one. */
@Composable
private fun Checklist(done: Set<VerifyStep>, skipped: Set<VerifyStep>, size: Long) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VerifyStep.entries.forEach { step ->
            val ok = step in done
            val skip = step in skipped
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(ok, transitionSpec = {
                        scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.3f) + fadeIn() togetherWith fadeOut(tween(90))
                    }, label = "tick") { isOk ->
                        if (isOk) Box(Modifier.size(32.dp).clip(CircleShape).background(if (skip) c.surfaceContainerHighest else c.tertiary), contentAlignment = Alignment.Center) {
                            Icon(painterResource(if (skip) R.drawable.ic_remove else R.drawable.ic_check), null, Modifier.size(18.dp), tint = if (skip) c.onSurfaceVariant else c.onTertiary)
                        } else LoadingIndicator(Modifier.size(32.dp), color = c.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(when (step) {
                        VerifyStep.Size -> R.string.update_v_size
                        VerifyStep.Checksum -> R.string.update_v_sha
                        VerifyStep.Signature -> R.string.update_v_sig
                    }), style = MaterialTheme.typography.bodyLarge, color = if (ok || step == done.size.let { VerifyStep.entries.getOrNull(it) }) c.onSurface else c.onSurfaceVariant)
                    Text(
                        when {
                            skip -> stringResource(R.string.update_v_skipped)
                            step == VerifyStep.Size -> formatBytes(size)
                            step == VerifyStep.Checksum -> "SHA-256"
                            else -> stringResource(R.string.update_v_sig_hint)
                        },
                        style = MonoLabel, color = c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp)) {
        Kicker(title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Toggle(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }
}

@Composable
private fun HistoryItem(r: ReleaseInfo) {
    val c = MaterialTheme.colorScheme
    var open by rememberSaveable(r.tag) { mutableStateOf(false) }
    val installed = r.version == BuildConfig.VERSION_NAME
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceContainerLow)
            .clickable { open = !open }.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).then(if (installed) Modifier.background(c.onSurface) else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(50)))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) { Text(r.version, style = MonoLabel, color = if (installed) c.surface else c.onSurface) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.takeIf { it != r.tag } ?: releaseDate(r.publishedAt), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(releaseDate(r.publishedAt), if (r.prerelease) "beta" else null, if (installed) stringResource(R.string.update_installed) else null).joinToString(" · "),
                    style = MonoLabel, color = c.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.rotate(rot), tint = c.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp)) {
                if (r.notes.isBlank()) Text(stringResource(R.string.update_no_notes), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                else Markdown(r.notes)
            }
        }
    }
}

