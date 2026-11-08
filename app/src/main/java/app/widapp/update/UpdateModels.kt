package app.widapp.update

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ReleaseAsset(
    val name: String,
    val size: Long,
    val url: String,
    /** "sha256:<hex>" — GitHub computes it for every uploaded asset. */
    val digest: String? = null,
    val downloads: Int = 0,
)

@Serializable
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val htmlUrl: String,
    val apk: ReleaseAsset?,
)

@Serializable
data class UpdateCache(
    val lastCheck: Long = 0,
    val etag: String? = null,
    val releases: List<ReleaseInfo> = emptyList(),
)

enum class VerifyStep { Size, Checksum, Signature }

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data class UpToDate(val latest: ReleaseInfo?) : UpdatePhase
    data class Available(val release: ReleaseInfo) : UpdatePhase
    data class Downloading(val release: ReleaseInfo, val read: Long, val total: Long, val bytesPerSec: Long) : UpdatePhase
    data class Verifying(val release: ReleaseInfo, val done: Set<VerifyStep>, val skipped: Set<VerifyStep> = emptySet()) : UpdatePhase
    data class Ready(val release: ReleaseInfo, val file: File, val skipped: Set<VerifyStep>) : UpdatePhase
    data class NeedsPermission(val release: ReleaseInfo, val file: File) : UpdatePhase
    data class Installing(val release: ReleaseInfo) : UpdatePhase
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdatePhase
}
