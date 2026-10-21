package dev.tether.core.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.formatBytes

/** Download notifications shared by phones and TV (TV shows them in the system tray). */
object DownloadNotifications {
    const val CHANNEL_ID = "tether_downloads"
    const val PROGRESS_NOTIFICATION_ID = 7401
    const val PAUSED_NOTIFICATION_ID = 7402
    const val COMPLETED_NOTIFICATION_ID = 7403

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress for music stored on this device"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun progress(context: Context, record: DownloadRecord, progress: Float, indeterminate: Boolean): Notification {
        ensureChannel(context)
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        val detail = when {
            indeterminate -> "Starting download…"
            record.totalBytes > 0 -> "$percent% · ${formatBytes(record.bytesDownloaded)} of ${formatBytes(record.totalBytes)}"
            else -> formatBytes(record.bytesDownloaded)
        }
        return base(context, record)
            .setContentTitle("Downloading · ${record.title}")
            .setContentText("${record.artist} · $detail")
            .setSubText("Keep the attribution: ${record.license ?: record.sourceLabel}")
            .setProgress(if (indeterminate) 0 else 100, if (indeterminate) 0 else percent, indeterminate)
            .setOngoing(true)
            .addAction(0, "Pause", action(context, DownloadActionReceiver.ACTION_PAUSE, record.trackId, 1))
            .addAction(0, "Cancel", action(context, DownloadActionReceiver.ACTION_CANCEL, record.trackId, 2))
            .build()
    }

    fun paused(context: Context, record: DownloadRecord): Notification {
        ensureChannel(context)
        return base(context, record)
            .setContentTitle("Paused · ${record.title}")
            .setContentText("${record.artist} · ${record.progressPercent} complete. Resume whenever you like.")
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(0, "Resume", action(context, DownloadActionReceiver.ACTION_RESUME, record.trackId, 3))
            .addAction(0, "Cancel", action(context, DownloadActionReceiver.ACTION_CANCEL, record.trackId, 4))
            .build()
    }

    fun completed(context: Context, record: DownloadRecord): Notification {
        ensureChannel(context)
        return base(context, record)
            .setContentTitle("Ready offline · ${record.title}")
            .setContentText("${record.artist} · ${record.sizeLabel}")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun base(context: Context, record: DownloadRecord): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF8FA6FF.toInt())

    private fun openApp(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(context: Context, action: String, trackId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_TRACK_ID, trackId)
        return PendingIntent.getBroadcast(
            context,
            requestCode + trackId.hashCode().and(0x0FFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private val DownloadRecord.progressPercent: String
    get() = if (totalBytes > 0) "${(progress * 100).toInt()}%" else formatBytes(bytesDownloaded)
