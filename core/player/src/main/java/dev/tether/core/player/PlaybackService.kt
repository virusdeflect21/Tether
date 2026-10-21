package dev.tether.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.tether.core.data.Tether
import dev.tether.core.data.TetherGraph
import dev.tether.core.database.HistoryEntity
import dev.tether.core.database.PlayerStateEntity
import dev.tether.core.database.toTrack
import dev.tether.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Custom session commands used by the mobile and TV player screens. */
object PlaybackCommands {
    const val SLEEP_TIMER = "dev.tether.player.SLEEP_TIMER"
    const val GET_SLEEP_TIMER = "dev.tether.player.GET_SLEEP_TIMER"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_ACTIVE = "active"
    const val EXTRA_REMAINING_MS = "remaining_ms"
}

/**
 * Background playback for both apps.
 *
 * The service owns ExoPlayer and a MediaSession, persists the queue and position,
 * keeps playback history, and hosts the sleep timer so it survives the UI being
 * backgrounded. Only app-managed files and explicitly permitted provider streams
 * are ever handed to ExoPlayer.
 */
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var graph: TetherGraph
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    private val historyMutex = Mutex()
    private var historyEntryId: Long? = null
    private var historyTrackId: String? = null
    private var historyDurationMs = 0L
    private var historyPositionMs = 0L
    private var historyListeningMs = 0L
    private var lastTickAt: Long? = null

    private var saveJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepUntilMs = 0L
    private var keepQueueOnExit = true
    private var lastQueueSignature: String? = null

    override fun onCreate() {
        super.onCreate()
        graph = Tether.graph(this)
        player = buildPlayer()
        player.addListener(playerListener)

        session = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(openAppIntent())
            .setPeriodicPositionUpdateEnabled(true)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("tether_playback")
                .setChannelName(R.string.tether_playback_channel)
                .setNotificationId(7501)
                .build(),
        )

        restoreQueue()
        observeSettings()
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) {
                    tickHistory()
                    persistState()
                }
            }
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val httpDataSource = OkHttpDataSource.Factory(client)
            .setUserAgent("Tether/1.0 (background playback)")
        val dataSource = DefaultDataSource.Factory(this, httpDataSource)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun observeSettings() {
        scope.launch {
            graph.settings.settings.collectLatest { settings ->
                player.pauseAtEndOfMediaItems = !settings.autoplayNext
                keepQueueOnExit = settings.keepQueueOnExit
            }
        }
    }

    private fun restoreQueue() {
        scope.launch {
            val settings = runCatching { graph.settings.settings.first() }.getOrNull()
            keepQueueOnExit = settings?.keepQueueOnExit ?: keepQueueOnExit
            if (!keepQueueOnExit) return@launch
            val state = withContext(Dispatchers.IO) {
                graph.database.playerStateDao().get()
            } ?: return@launch
            val ids = parseIds(state.queueIds)
            if (ids.isEmpty()) return@launch
            val tracks = withContext(Dispatchers.IO) {
                graph.database.trackDao().getByIds(ids)
            }.associateBy { it.id }
            val ordered = ids.mapNotNull { tracks[it]?.toTrack() }
            if (ordered.isEmpty()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (player.mediaItemCount == 0) {
                    val index = state.currentIndex.coerceIn(0, ordered.lastIndex)
                    player.setMediaItems(
                        ordered.map { it.toMediaItem() },
                        index,
                        state.positionMs.coerceAtLeast(0),
                    )
                    player.shuffleModeEnabled = state.shuffle
                    player.repeatMode = state.repeatMode
                    player.playWhenReady = false
                    lastQueueSignature = ordered.joinToString("|") { it.id }
                    scheduleSave()
                }
            }
        }
    }

    private suspend fun beginHistory(trackId: String, durationMs: Long) {
        historyMutex.withLock {
            val now = System.currentTimeMillis()
            if (historyTrackId == trackId && historyEntryId != null) {
                if (lastTickAt == null && player.isPlaying) lastTickAt = now
                return
            }
            finalizeHistoryLocked(completed = false, at = now)
            if (!player.playWhenReady && !player.isPlaying) return
            historyTrackId = trackId
            historyDurationMs = durationMs.coerceAtLeast(0)
            historyPositionMs = 0
            historyListeningMs = 0
            lastTickAt = if (player.isPlaying) now else null
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    graph.database.trackDao().recordPlay(trackId, now)
                    graph.database.historyDao().insert(
                        HistoryEntity(
                            trackId = trackId,
                            playedAt = now,
                            positionMs = 0,
                            durationMs = historyDurationMs,
                            completed = false,
                            listeningMs = 0,
                        ),
                    )
                }.getOrNull()
            }
            historyEntryId = id
        }
    }

    private suspend fun tickHistory() {
        historyMutex.withLock {
            val entryId = historyEntryId ?: return
            val now = System.currentTimeMillis()
            val tick = lastTickAt
            if (tick != null && player.isPlaying) {
                historyListeningMs += (now - tick).coerceIn(0, 10_000)
            }
            lastTickAt = if (player.isPlaying) now else null
            historyPositionMs = player.currentPosition.coerceAtLeast(0)
            withContext(Dispatchers.IO) {
                runCatching {
                    graph.database.historyDao().updateProgress(
                        id = entryId,
                        positionMs = historyPositionMs,
                        listeningMs = historyListeningMs,
                        completed = false,
                    )
                }
            }
        }
    }

    private suspend fun finishHistory(completed: Boolean) {
        historyMutex.withLock {
            finalizeHistoryLocked(completed, System.currentTimeMillis())
        }
    }

    private suspend fun finalizeHistoryLocked(completed: Boolean, at: Long) {
        val entryId = historyEntryId
        val duration = historyDurationMs
        val tick = lastTickAt
        if (tick != null && player.isPlaying) {
            historyListeningMs += (at - tick).coerceIn(0, 10_000)
        }
        if (entryId != null) {
            val position = if (completed && duration > 0) duration else historyPositionMs
            withContext(Dispatchers.IO) {
                runCatching {
                    graph.database.historyDao().updateProgress(
                        id = entryId,
                        positionMs = position,
                        listeningMs = historyListeningMs,
                        completed = completed,
                    )
                }
            }
        }
        historyEntryId = null
        historyTrackId = null
        historyDurationMs = 0
        historyPositionMs = 0
        historyListeningMs = 0
        lastTickAt = null
    }

    private fun scheduleSave(delayMs: Long = 700) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(delayMs)
            persistState()
        }
    }

    private suspend fun persistState() {
        if (!keepQueueOnExit) return
        val count = player.mediaItemCount
        if (count == 0) {
            withContext(Dispatchers.IO) { runCatching { graph.database.playerStateDao().clear() } }
            return
        }
        val ids = (0 until count).mapNotNull { index ->
            runCatching { player.getMediaItemAt(index).mediaId.takeIf { it.isNotBlank() } }.getOrNull()
        }
        if (ids.isEmpty()) return
        val state = PlayerStateEntity(
            queueIds = JSONArray(ids).toString(),
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            updatedAt = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) {
            runCatching { graph.database.playerStateDao().save(state) }
        }
    }

    private fun updateQueueSignature(): Boolean {
        val signature = (0 until player.mediaItemCount).joinToString("|") { index ->
            runCatching { player.getMediaItemAt(index).mediaId }.getOrDefault("")
        }
        val changed = signature != lastQueueSignature
        lastQueueSignature = signature
        return changed
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateQueueSignature()
            val trackId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            if (trackId != null) {
                val duration = mediaItem.mediaMetadata.durationMs ?: 0L
                scope.launch { beginHistory(trackId, duration) }
            } else {
                scope.launch { finishHistory(completed = false) }
            }
            scheduleSave(delayMs = 400)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                val media = player.currentMediaItem ?: return
                val id = media.mediaId.takeIf { it.isNotBlank() } ?: return
                val duration = media.mediaMetadata.durationMs ?: 0L
                scope.launch { beginHistory(id, duration) }
            } else {
                scope.launch { tickHistory() }
            }
            scheduleSave()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                scope.launch { finishHistory(completed = true) }
            }
            scheduleSave()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            scheduleSave()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            scheduleSave()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            scheduleSave()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                if (updateQueueSignature()) scheduleSave()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            scheduleSave()
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.GET_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val result = when (customCommand.customAction) {
                PlaybackCommands.SLEEP_TIMER -> {
                    val minutes = args.getInt(PlaybackCommands.EXTRA_MINUTES, 0).coerceIn(0, 180)
                    scheduleSleep(minutes)
                    sleepResult()
                }
                PlaybackCommands.GET_SLEEP_TIMER -> sleepResult()
                else -> SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
            return Futures.immediateFuture(result)
        }

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val restored = withContext(Dispatchers.IO) { readSavedQueue() }
                future.set(
                    restored ?: MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0),
                )
            }
            return future
        }
    }

    private fun scheduleSleep(minutes: Int): Long {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            sleepUntilMs = 0
            return 0
        }
        val ms = minutes * 60_000L
        sleepUntilMs = System.currentTimeMillis() + ms
        sleepJob = scope.launch {
            delay(ms)
            sleepUntilMs = 0
            player.pause()
        }
        return ms
    }

    private fun sleepResult(): SessionResult {
        val remaining = (sleepUntilMs - System.currentTimeMillis()).coerceAtLeast(0)
        return SessionResult(
            SessionResult.RESULT_SUCCESS,
            Bundle().apply {
                putBoolean(PlaybackCommands.EXTRA_ACTIVE, remaining > 0)
                putLong(PlaybackCommands.EXTRA_REMAINING_MS, remaining)
            },
        )
    }

    private suspend fun readSavedQueue(): MediaSession.MediaItemsWithStartPosition? {
        val state = runCatching { graph.database.playerStateDao().get() }.getOrNull() ?: return null
        val ids = parseIds(state.queueIds)
        if (ids.isEmpty()) return null
        val tracks = graph.database.trackDao().getByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { tracks[it]?.toTrack() }
        if (ordered.isEmpty()) return null
        val index = state.currentIndex.coerceIn(0, ordered.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(
            ordered.map(Track::toMediaItem),
            index,
            state.positionMs.coerceAtLeast(0),
        )
    }

    private fun parseIds(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index, "").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        val now = System.currentTimeMillis()
        val entryId = historyEntryId
        val listening = historyListeningMs + if (player.isPlaying) {
            lastTickAt?.let { (now - it).coerceIn(0, 10_000) } ?: 0L
        } else {
            0L
        }
        val position = historyPositionMs
        runBlocking {
            withContext(NonCancellable) {
                if (entryId != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            graph.database.historyDao().updateProgress(
                                id = entryId,
                                positionMs = position,
                                listeningMs = listening,
                                completed = false,
                            )
                        }
                    }
                }
                if (keepQueueOnExit) {
                    persistState()
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { graph.database.playerStateDao().clear() }
                    }
                }
            }
        }
        sleepJob?.cancel()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
