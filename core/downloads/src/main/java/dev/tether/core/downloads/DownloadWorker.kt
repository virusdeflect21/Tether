package dev.tether.core.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resumable HTTP transfer for one download record.
 *
 * A `.part` file always represents bytes that can be trusted: a ranged request
 * appends to it, while a server that ignores Range starts the file over. The
 * completed container is validated before the database and library are updated.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = Downloads.repository(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val initial = repository.record(trackId) ?: return Result.failure()
        if (initial.status == DownloadStatus.COMPLETED) return Result.success()
        if (!initial.status.isActive) return Result.success()

        repository.markDownloading(trackId)
        val record = repository.record(trackId) ?: return Result.failure()
        if (record.status != DownloadStatus.DOWNLOADING) return Result.success()

        val finalFile = record.filePath?.let(::File)
            ?: return fail(trackId, "The download has no destination file.")
        val partial = File("${finalFile.absolutePath}.part")
        finalFile.parentFile?.mkdirs()

        val startingBytes = partial.takeIf { it.isFile }?.length() ?: 0L
        val startingTotal = if (record.totalBytes > 0) record.totalBytes else startingBytes
        runCatching {
            setForeground(
                foregroundInfo(
                    record.copy(bytesDownloaded = startingBytes, totalBytes = startingTotal),
                    startingBytes,
                    startingTotal,
                ),
            )
        }

        return try {
            val (extension, total) = transfer(trackId, record, partial)
            val validation = DownloadFiles.validate(partial, total, extension)
            if (!validation.valid) {
                partial.delete()
                return fail(trackId, validation.reason ?: "The downloaded file did not validate.")
            }
            val completed = repository.complete(
                trackId = trackId,
                partial = partial,
                finalFile = finalFile,
                mimeType = DownloadFiles.mimeFor(extension),
            )
            if (completed != null) {
                DownloadNotifications.notify(
                    applicationContext,
                    DownloadNotifications.COMPLETED_NOTIFICATION_ID,
                    DownloadNotifications.completed(applicationContext, completed),
                )
                Result.success()
            } else {
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                repository.markInterrupted(trackId, "The system paused this download. Resume when ready.")
            }
            throw cancelled
        } catch (error: Exception) {
            fail(trackId, error.message ?: "The download could not be completed.")
        }
    }

    private suspend fun transfer(
        trackId: String,
        record: DownloadRecord,
        partial: File,
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val url = record.sourceUri
            ?: throw IOException("The source URL is missing.")
        val existing = partial.takeIf { it.isFile }?.length() ?: 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Tether/1.0 (offline music download)")
            .header("Accept", "audio/*,application/octet-stream;q=0.9,*/*;q=0.5")
            .apply {
                if (existing > 0) header("Range", "bytes=$existing-")
            }
            .build()

        val call = client.newCall(request)
        val job = currentCoroutineContext().job
        val cancelHandle = job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    partial.delete()
                    throw IOException("The server rejected the resume request. Tap retry to start again.")
                }
                if (!response.isSuccessful) {
                    throw IOException("The server returned HTTP ${response.code}.")
                }
                val contentType = response.header("Content-Type")
                if (contentType != null &&
                    (contentType.startsWith("text/", ignoreCase = true) ||
                        contentType.contains("html", ignoreCase = true))
                ) {
                    throw IOException("The server returned a web page instead of an audio file.")
                }
                val body = response.body ?: throw IOException("The server sent an empty response.")
                val length = body.contentLength()
                val ranged = response.code == 206
                val appending = ranged && existing > 0 && partial.isFile
                if (!appending && partial.exists()) partial.delete()

                val total = when {
                    ranged -> contentRangeTotal(response.header("Content-Range"))
                        ?: if (length > 0) existing + length else record.totalBytes
                    length > 0 -> length
                    else -> record.totalBytes
                }
                var written = if (appending) existing else 0L
                var lastReport = 0L

                suspend fun publish(force: Boolean) {
                    val now = System.currentTimeMillis()
                    if (!force && now - lastReport < 900) return
                    lastReport = now
                    val snapshot = record.copy(
                        bytesDownloaded = written,
                        totalBytes = if (total > 0) total else 0,
                    )
                    repository.reportProgress(trackId, written, if (total > 0) total else 0)
                    DownloadNotifications.notify(
                        applicationContext,
                        DownloadNotifications.PROGRESS_NOTIFICATION_ID,
                        DownloadNotifications.progress(
                            applicationContext,
                            snapshot,
                            if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            indeterminate = total <= 0,
                        ),
                    )
                }

                publish(force = true)
                FileOutputStream(partial, appending).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            written += read
                            publish(force = false)
                        }
                        output.flush()
                    }
                }
                if (total > 0 && written < total) {
                    throw IOException("The connection ended early: $written of $total bytes arrived.")
                }
                repository.reportProgress(trackId, written, if (total > 0) total else written)
                val extension = DownloadFiles.extensionFor(url, contentType, record.format.lowercase())
                extension to if (total > 0) total else written
            }
        } finally {
            cancelHandle.dispose()
        }
    }

    private suspend fun fail(trackId: String, message: String): Result {
        repository.markFailed(trackId, message)
        return Result.failure()
    }

    private fun foregroundInfo(record: DownloadRecord, bytes: Long, total: Long): ForegroundInfo {
        DownloadNotifications.ensureChannel(applicationContext)
        val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        val notification = DownloadNotifications.progress(
            applicationContext,
            record,
            progress,
            indeterminate = total <= 0,
        )
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                DownloadNotifications.PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotifications.PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun contentRangeTotal(header: String?): Long? {
        val total = header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
        return total?.takeIf { it > 0 }
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
    }
}
