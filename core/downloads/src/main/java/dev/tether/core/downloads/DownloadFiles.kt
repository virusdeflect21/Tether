package dev.tether.core.downloads

import java.io.File
import java.security.MessageDigest

/** Result of checking a finished transfer before it is promoted to a playable file. */
data class FileValidation(val valid: Boolean, val reason: String? = null)

/**
 * Naming and validation for app-managed downloads. Nothing here trusts the
 * server: the extension is derived from the URL, and well-known containers are
 * checked against their magic bytes before a track is marked playable.
 */
object DownloadFiles {
    val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "webm", "mka", "aiff", "alac",
    )

    private val mimeByExtension = mapOf(
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "webm" to "audio/webm",
        "mka" to "audio/x-matroska",
        "aiff" to "audio/aiff",
        "alac" to "audio/mp4",
    )

    fun extensionFor(url: String, contentType: String?, fallbackMime: String? = null): String {
        val clean = url.substringBefore('#').substringBefore('?')
        val fromUrl = clean.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
        if (fromUrl in audioExtensions) return fromUrl
        val mime = (contentType ?: fallbackMime)?.substringBefore(';')?.trim()?.lowercase()
        return when {
            mime == null -> "mp3"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("flac") -> "flac"
            mime.contains("mp4") || mime.contains("m4a") -> "m4a"
            mime.contains("aac") -> "aac"
            mime.contains("ogg") || mime.contains("opus") -> "ogg"
            mime.contains("wav") -> "wav"
            mime.contains("webm") -> "webm"
            mime.contains("matroska") -> "mka"
            mime.contains("aiff") -> "aiff"
            else -> "mp3"
        }
    }

    fun mimeFor(extension: String): String? = mimeByExtension[extension.lowercase()]

    /** Builds a readable, collision-free file name. The short id suffix is stable. */
    fun fileName(trackId: String, title: String, artist: String, extension: String): String {
        val base = sanitize("$artist - $title", 76)
        val safeBase = base.ifBlank { "Tether download" }
        return "$safeBase [${shortHash(trackId)}].${extension.lowercase()}"
    }

    fun sanitize(value: String, maxLength: Int): String {
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
        return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength).trim()
    }

    fun validate(file: File, expectedBytes: Long, extensionHint: String? = null): FileValidation {
        if (!file.isFile) return FileValidation(false, "The downloaded file is missing.")
        val length = file.length()
        if (length < 512) return FileValidation(false, "The downloaded file is empty or truncated.")
        if (expectedBytes > 0 && length < expectedBytes) {
            return FileValidation(false, "Only $length of $expectedBytes bytes arrived.")
        }
        val header = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        if (read < 4) return FileValidation(false, "The downloaded file has no readable audio header.")
        val text = String(header, 0, read, Charsets.ISO_8859_1)
        val extension = (extensionHint ?: file.extension).lowercase()
        val looksRight = when (extension) {
            "mp3" -> text.startsWith("ID3") ||
                (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
            "flac" -> text.startsWith("fLaC")
            "ogg", "opus" -> text.startsWith("OggS")
            "wav" -> text.startsWith("RIFF") && text.substring(8, 12) == "WAVE"
            "m4a", "alac" -> read >= 8 && text.substring(4, 8) == "ftyp"
            "aac" -> header[0] == 0xFF.toByte() && (header[1].toInt() and 0xF6) == 0xF0
            "aiff" -> text.startsWith("FORM")
            "webm", "mka" -> header[0] == 0x1A.toByte() && header[1] == 0x45.toByte()
            else -> true
        }
        return if (looksRight) {
            FileValidation(true)
        } else {
            FileValidation(false, "The server sent data that is not a valid ${extension.uppercase()} file.")
        }
    }

    fun attributionText(
        title: String,
        artist: String,
        sourceLabel: String,
        license: String?,
        sourceUri: String?,
        downloadedAt: Long,
    ): String = buildString {
        appendLine("Tether download attribution")
        appendLine("Title: $title")
        appendLine("Artist: $artist")
        appendLine("Source: $sourceLabel")
        appendLine("License: ${license ?: "Not declared"}")
        sourceUri?.let { appendLine("Original file: $it") }
        appendLine("Downloaded: ${java.time.Instant.ofEpochMilli(downloadedAt)}")
        appendLine()
        appendLine("Keep this file with the audio copy. If the license is noncommercial,")
        appendLine("this download is intended for personal, noncommercial listening.")
    }

    fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
