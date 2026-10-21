package dev.tether.core.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Caches artwork that belongs to local/downloaded tracks so the UI and media
 * notification keep working offline. Artwork URLs remain usable for remote tracks.
 */
class ArtworkStore(
    private val context: Context,
    private val metadataReader: MetadataReader,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val directory: File
        get() = File(context.filesDir, "artwork").apply { mkdirs() }

    fun cachedUri(trackId: String): String? {
        val file = fileFor(trackId)
        return if (file.isFile && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    fun fileFor(trackId: String): File = File(directory, sha256(trackId).take(28) + ".jpg")

    suspend fun cacheEmbedded(trackId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = metadataReader.embeddedArtwork(uri) ?: return@runCatching null
            writeBitmap(trackId, bytes)
        }.getOrNull()
    }

    suspend fun cacheBytes(trackId: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching { writeBitmap(trackId, bytes) }.getOrNull()
    }

    suspend fun cacheUrl(trackId: String, url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Tether/1.0 (offline artwork cache)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                if (body.contentLength() > 8L * 1024 * 1024) return@runCatching null
                val bytes = body.bytes()
                writeBitmap(trackId, bytes)
            }
        }.getOrNull()
    }

    private fun writeBitmap(trackId: String, bytes: ByteArray): String? {
        val bitmap = MetadataReader.artworkFromBytes(bytes) ?: return null
        return try {
            val file = fileFor(trackId)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { stream -> stream.write(MetadataReader.compressArtwork(bitmap)) }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            Uri.fromFile(file).toString()
        } finally {
            bitmap.recycle()
        }
    }

    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(): Long {
        val freed = sizeBytes()
        directory.listFiles()?.forEach { it.delete() }
        return freed
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
