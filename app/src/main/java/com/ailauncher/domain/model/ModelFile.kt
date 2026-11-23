package com.ailauncher.domain.model

/** Description of a downloadable GGUF build. */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** Hex SHA-256 of the file, verified after download. Null skips verification. */
    val sha256: String?,
    val license: String,
)

/** State of the model file on disk and of its download. */
sealed interface ModelFileState {
    data object Missing : ModelFileState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : ModelFileState {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        val etaSeconds: Long? get() = if (bytesPerSecond <= 0) null else (totalBytes - downloadedBytes) / bytesPerSecond
    }

    /** Download stopped by the user; the partial file is kept and resumed with an HTTP Range. */
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : ModelFileState

    data class Verifying(val totalBytes: Long) : ModelFileState

    data class Importing(val copiedBytes: Long, val totalBytes: Long) : ModelFileState

    data class Failed(val message: String, val downloadedBytes: Long, val totalBytes: Long) : ModelFileState

    data class Ready(val sizeBytes: Long, val path: String) : ModelFileState
}
