package dev.tether.core.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.tether.core.data.LibraryRepository
import dev.tether.core.data.ProviderRegistry
import dev.tether.core.data.SettingsRepository
import dev.tether.core.data.Tether
import dev.tether.core.database.TetherDatabase
import dev.tether.core.database.toEntity
import dev.tether.core.database.toRecord
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.DownloadStatus
import dev.tether.core.model.StorageInfo
import dev.tether.core.model.Track
import dev.tether.core.model.TrackOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadActionResult(
    val ok: Boolean,
    val message: String,
    val record: DownloadRecord? = null,
)

/**
 * Owns app-managed downloads: permission checks, resumable work scheduling,
 * records, storage accounting and restoring streaming when a copy is deleted.
 *
 * The worker never trusts a provider blindly: [ProviderRegistry.downloadPermission]
 * must allow the license, and [DownloadFiles.validate] must recognise the finished
 * container before the track becomes offline-playable.
 */
class DownloadRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val library: LibraryRepository,
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {
    private val dao = database.downloadDao()

    val records: Flow<List<DownloadRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toRecord() } }

    val active: Flow<List<DownloadRecord>> =
        dao.observeActive().map { rows -> rows.map { it.toRecord() } }

    val completedBytes: Flow<Long> = dao.observeDownloadedBytes()

    fun observe(trackId: String): Flow<DownloadRecord?> =
        dao.observe(trackId).map { it?.toRecord() }

    fun downloadDirectory(): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(root, "Tether downloads").apply { mkdirs() }
    }

    suspend fun record(trackId: String): DownloadRecord? = withContext(Dispatchers.IO) {
        dao.get(trackId)?.toRecord()
    }

    /**
     * Queues a provider track only when the license explicitly allows it. Local
     * files are already offline and are rejected with a clear explanation.
     */
    suspend fun enqueue(track: Track, quality: DownloadQuality? = null): DownloadActionResult =
        withContext(Dispatchers.IO) {
            if (track.origin != TrackOrigin.REMOTE) {
                return@withContext DownloadActionResult(
                    ok = false,
                    message = "This track is already available on this device.",
                )
            }
            val permission = providers.downloadPermission(track)
            if (!permission.allowed) {
                return@withContext DownloadActionResult(ok = false, message = permission.reason)
            }
            // Signed provider URLs expire, so fetch a fresh one right before transferring.
            val source = runCatching { providers.resolveStream(track) }.getOrDefault(track)
            val url = source.remoteUri ?: source.uri
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                return@withContext DownloadActionResult(
                    ok = false,
                    message = "This provider did not supply a downloadable file for the track.",
                )
            }

            val existing = dao.get(track.id)?.toRecord()
            if (existing?.status == DownloadStatus.COMPLETED) {
                val completedFile = existing.filePath?.let(::File)
                if (completedFile != null && completedFile.isFile) {
                    return@withContext DownloadActionResult(
                        ok = false,
                        message = "Already downloaded. Delete the offline copy before downloading it again.",
                        record = existing,
                    )
                }
            }
            if (existing?.status?.isActive == true) {
                return@withContext DownloadActionResult(
                    ok = false,
                    message = "This track is already downloading.",
                    record = existing,
                )
            }

            library.ensureTrackRow(source)
            val requested = quality ?: settings.settings.first().downloadQuality
            val extension = DownloadFiles.extensionFor(url, contentType = null, fallbackMime = source.mimeType)
            val directory = downloadDirectory()
            val previousFile = existing?.filePath?.let(::File)
            val file = previousFile?.takeIf { it.parentFile == directory }
                ?: File(
                    directory,
                    DownloadFiles.fileName(track.id, track.title, track.artist, extension),
                )
            val partialBytes = File("${file.absolutePath}.part").takeIf { it.isFile }?.length() ?: 0L
            val now = System.currentTimeMillis()
            val record = DownloadRecord(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                sourceLabel = track.sourceLabel,
                sourceId = track.sourceId,
                status = DownloadStatus.QUEUED,
                progress = if (partialBytes > 0 && existing?.totalBytes != null && existing.totalBytes > 0) {
                    (partialBytes.toFloat() / existing.totalBytes).coerceIn(0f, 0.99f)
                } else {
                    0f
                },
                bytesDownloaded = partialBytes,
                totalBytes = existing?.totalBytes ?: track.sizeBytes,
                format = extension.uppercase(),
                quality = requested.label,
                filePath = file.absolutePath,
                license = permission.license ?: track.license,
                error = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                completedAt = existing?.completedAt,
                attempts = existing?.attempts ?: 0,
                sourceUri = url,
                artworkUri = track.artworkUri,
            )
            dao.upsert(record.toEntity())
            schedule(track.id)
            DownloadActionResult(
                ok = true,
                message = "Queued ${track.displayTitle} (${extension.uppercase()}).",
                record = record,
            )
        }

    suspend fun pause(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val record = dao.get(trackId)?.toRecord() ?: return@withContext false
        if (!record.status.isActive) return@withContext false
        val now = System.currentTimeMillis()
        dao.updateStatus(trackId, DownloadStatus.PAUSED.name, null, now)
        workManager.cancelUniqueWork(workName(trackId))
        val paused = dao.get(trackId)?.toRecord()
        if (paused != null) {
            DownloadNotifications.notify(
                context,
                DownloadNotifications.PAUSED_NOTIFICATION_ID,
                DownloadNotifications.paused(context, paused),
            )
        }
        true
    }

    suspend fun resume(trackId: String): DownloadActionResult = withContext(Dispatchers.IO) {
        val record = dao.get(trackId)?.toRecord()
            ?: return@withContext DownloadActionResult(false, "This download no longer exists.")
        if (record.status == DownloadStatus.COMPLETED) {
            return@withContext DownloadActionResult(false, "This download is already complete.", record)
        }
        if (record.status.isActive) {
            return@withContext DownloadActionResult(true, "Download is already running.", record)
        }
        val url = record.sourceUri
        if (url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) {
            return@withContext DownloadActionResult(false, "The source URL is missing. Retry from the track menu.", record)
        }
        val now = System.currentTimeMillis()
        val updated = record.copy(
            status = DownloadStatus.QUEUED,
            error = null,
            updatedAt = now,
        )
        dao.upsert(updated.toEntity())
        DownloadNotifications.cancel(context, DownloadNotifications.PAUSED_NOTIFICATION_ID)
        DownloadNotifications.cancel(context, DownloadNotifications.COMPLETED_NOTIFICATION_ID)
        schedule(trackId)
        DownloadActionResult(true, "Resuming ${record.title}.", updated)
    }

    suspend fun pauseAll(): Int = withContext(Dispatchers.IO) {
        val activeRecords = dao.observeAll().first().map { it.toRecord() }.filter { it.status.isActive }
        activeRecords.forEach { pause(it.trackId) }
        activeRecords.size
    }

    suspend fun resumeAll(): Int = withContext(Dispatchers.IO) {
        val resumable = dao.observeAll().first().map { it.toRecord() }
            .filter { it.status == DownloadStatus.PAUSED }
        resumable.forEach { resume(it.trackId) }
        resumable.size
    }

    suspend fun retry(trackId: String): DownloadActionResult {
        val record = record(trackId)
        if (record?.status != DownloadStatus.FAILED && record?.status != DownloadStatus.CANCELLED &&
            record?.status != DownloadStatus.PAUSED
        ) {
            return DownloadActionResult(false, "Only failed, cancelled or paused downloads can be retried.")
        }
        return resume(trackId)
    }

    suspend fun cancel(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val record = dao.get(trackId)?.toRecord() ?: return@withContext false
        if (record.status == DownloadStatus.COMPLETED) {
            return@withContext false
        }
        workManager.cancelUniqueWork(workName(trackId))
        dao.updateStatus(trackId, DownloadStatus.CANCELLED.name, null, System.currentTimeMillis())
        record.filePath?.let { path ->
            File("$path.part").delete()
        }
        DownloadNotifications.cancel(context, DownloadNotifications.PAUSED_NOTIFICATION_ID)
        DownloadNotifications.cancel(context, DownloadNotifications.PROGRESS_NOTIFICATION_ID)
        true
    }

    /**
     * Deletes the offline copy, its attribution sidecar and the download record,
     * then points the track back at its original stream when one was retained.
     */
    suspend fun delete(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val record = dao.get(trackId)?.toRecord() ?: return@withContext false
        workManager.cancelUniqueWork(workName(trackId))
        val path = record.filePath
        if (path != null) {
            File(path).delete()
            File("$path.part").delete()
            File("$path.license.txt").delete()
        }
        val track = library.getTrack(trackId)
        if (track?.origin == TrackOrigin.DOWNLOADED) {
            library.restoreStreaming(trackId)
        }
        dao.delete(trackId)
        DownloadNotifications.cancel(context, DownloadNotifications.PAUSED_NOTIFICATION_ID)
        DownloadNotifications.cancel(context, DownloadNotifications.COMPLETED_NOTIFICATION_ID)
        true
    }

    suspend fun clearHistory(): Int = withContext(Dispatchers.IO) {
        val activeFiles = dao.observeAll().first()
            .filter { it.status == DownloadStatus.CANCELLED.name || it.status == DownloadStatus.FAILED.name }
        activeFiles.forEach { entity ->
            entity.filePath?.let { path -> File("$path.part").delete() }
        }
        dao.clearInactive()
        activeFiles.size
    }

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val downloads = downloadDirectory()
        val appDownloads = downloads.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val artwork = File(context.filesDir, "artwork").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val stat = runCatching { StatFs(downloads.absolutePath) }.getOrNull()
        StorageInfo(
            appDownloadsBytes = appDownloads,
            artworkBytes = artwork,
            lyricsBytes = 0L,
            availableBytes = stat?.availableBytes ?: 0L,
            totalBytes = stat?.totalBytes ?: 0L,
        )
    }

    fun permissionReason(track: Track) = providers.downloadPermission(track)

    /** Called by [DownloadWorker] once the transfer is about to start. */
    suspend fun markDownloading(trackId: String) = withContext(Dispatchers.IO) {
        dao.markDownloading(trackId, System.currentTimeMillis())
    }

    suspend fun reportProgress(trackId: String, bytes: Long, total: Long) = withContext(Dispatchers.IO) {
        val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        dao.updateProgress(trackId, progress, bytes, total, System.currentTimeMillis())
    }

    suspend fun markInterrupted(trackId: String, message: String) = withContext(Dispatchers.IO) {
        dao.markInterrupted(trackId, message, System.currentTimeMillis())
    }

    suspend fun markFailed(trackId: String, message: String) = withContext(Dispatchers.IO) {
        dao.markFailed(trackId, message.take(500), System.currentTimeMillis())
    }

    /**
     * Promotes a validated partial file, writes the attribution sidecar and makes
     * the track offline-playable. Returns the completed record, or null when the
     * user cancelled before the final commit.
     */
    suspend fun complete(trackId: String, partial: File, finalFile: File, mimeType: String?): DownloadRecord? =
        withContext(Dispatchers.IO) {
            val current = dao.get(trackId)?.toRecord() ?: return@withContext null
            if (current.status == DownloadStatus.CANCELLED || current.status == DownloadStatus.PAUSED) {
                return@withContext null
            }
            val size = partial.length()
            if (finalFile.exists()) finalFile.delete()
            if (!partial.renameTo(finalFile)) {
                partial.copyTo(finalFile, overwrite = true)
                partial.delete()
            }
            if (!finalFile.isFile || finalFile.length() < 512) {
                finalFile.delete()
                markFailed(trackId, "The finished file could not be saved.")
                return@withContext null
            }
            val now = System.currentTimeMillis()
            finalFile.parentFile?.let { parent ->
                File(parent, "${finalFile.name}.license.txt").writeText(
                    DownloadFiles.attributionText(
                        title = current.title,
                        artist = current.artist,
                        sourceLabel = current.sourceLabel,
                        license = current.license,
                        sourceUri = current.sourceUri,
                        downloadedAt = now,
                    ),
                )
            }
            library.markDownloaded(
                trackId = trackId,
                fileUri = Uri.fromFile(finalFile).toString(),
                mimeType = mimeType,
                sizeBytes = finalFile.length(),
            )
            val updated = dao.markCompleted(trackId, finalFile.length(), now)
            if (updated == 0) {
                finalFile.delete()
                File("${finalFile.absolutePath}.license.txt").delete()
                return@withContext null
            }
            dao.get(trackId)?.toRecord()
        }

    private fun schedule(trackId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to trackId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(trackId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(trackId: String): String = "tether-download-${DownloadFiles.shortHash(trackId)}"

    companion object {
        const val TAG = "tether_download"
    }
}

/** Process-wide access for screens, workers and notification actions. */
object Downloads {
    @Volatile
    private var instance: DownloadRepository? = null

    fun repository(context: Context): DownloadRepository {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            instance ?: run {
                val graph = Tether.graph(context.applicationContext)
                DownloadRepository(
                    context = graph.appContext,
                    database = graph.database,
                    library = graph.library,
                    providers = graph.providers,
                    settings = graph.settings,
                ).also { instance = it }
            }
        }
    }
}
