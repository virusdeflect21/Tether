package dev.tether.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

data class ExtractedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
)

class MetadataReader(private val context: Context) {
    fun read(uri: Uri, fallbackName: String?, mimeType: String?): ExtractedMetadata {
        val fallbackTitle = cleanFileName(fallbackName)
        var title = fallbackTitle
        var artist = ""
        var album = ""
        var albumArtist = ""
        var duration = 0L
        var track: Int? = null
        var disc: Int? = null
        var year: Int? = null
        var genre: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim().orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim().orEmpty()
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.trim().orEmpty()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            disc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.take(4)?.toIntOrNull()
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Missing/corrupt tags are expected in real libraries.
        } finally {
            runCatching { retriever.release() }
        }
        if (title.isBlank()) title = fallbackTitle
        if (artist.isBlank()) artist = "Unknown artist"
        if (album.isBlank()) album = "Unknown album"
        if (albumArtist.isBlank()) albumArtist = artist
        if (mimeType.isNullOrBlank() && fallbackName != null) {
            // MIME is only a hint; callers supply it when available.
        }
        return ExtractedMetadata(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            albumArtist = albumArtist.trim(),
            durationMs = duration,
            trackNumber = track,
            discNumber = disc,
            year = year,
            genre = genre,
        )
    }

    fun readFile(file: File): ExtractedMetadata = read(Uri.fromFile(file), file.name, null)

    fun embeddedArtwork(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun durationOf(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun cleanFileName(name: String?): String {
        if (name.isNullOrBlank()) return "Untitled track"
        return name.substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
            .ifBlank { "Untitled track" }
    }

    companion object {
        fun artworkFromBytes(bytes: ByteArray, maxDimension: Int = 900): Bitmap? {
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        fun compressArtwork(bitmap: Bitmap): ByteArray {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            return output.toByteArray()
        }
    }
}
