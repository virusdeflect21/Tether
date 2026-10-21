package dev.tether.core.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == DOWNLOADING
}

data class DownloadRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val sourceLabel: String,
    val sourceId: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val format: String = "",
    val quality: String = "",
    val filePath: String? = null,
    val license: String? = null,
    val error: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null,
    val attempts: Int = 0,
    val sourceUri: String? = null,
    val artworkUri: String? = null,
) {
    val isActive: Boolean get() = status.isActive
    val sizeLabel: String get() = when {
        totalBytes > 0 -> formatBytes(totalBytes)
        bytesDownloaded > 0 -> formatBytes(bytesDownloaded)
        else -> "Size unknown"
    }
}

enum class DownloadQuality(val label: String, val description: String) {
    STANDARD("Standard", "Smallest file, roughly 128 kbps MP3 when available"),
    HIGH("High", "Better quality MP3 or Ogg when the source offers it"),
    LOSSLESS("Lossless", "FLAC when the item offers it; falls back to the best available"),
}

data class StorageInfo(
    val appDownloadsBytes: Long = 0L,
    val artworkBytes: Long = 0L,
    val lyricsBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0 || value >= 100) {
        "${value.toInt()} ${units[index]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }
}
