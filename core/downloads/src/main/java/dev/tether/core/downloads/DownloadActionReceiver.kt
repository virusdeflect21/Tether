package dev.tether.core.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles Pause/Resume/Cancel from download notifications. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID) ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = Downloads.repository(context)
                when (action) {
                    ACTION_PAUSE -> repository.pause(trackId)
                    ACTION_RESUME -> repository.resume(trackId)
                    ACTION_CANCEL -> repository.cancel(trackId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRACK_ID = "dev.tether.downloads.TRACK_ID"
        const val ACTION_PAUSE = "dev.tether.downloads.action.PAUSE"
        const val ACTION_RESUME = "dev.tether.downloads.action.RESUME"
        const val ACTION_CANCEL = "dev.tether.downloads.action.CANCEL"
    }
}
