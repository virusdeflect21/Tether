package dev.tether.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.tether.core.data.Tether
import dev.tether.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val connecting: Boolean = true,
    val connected: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
    val sleepTimerActive: Boolean = false,
    val sleepRemainingMs: Long = 0L,
) {
    val hasQueue: Boolean get() = queue.isNotEmpty()
    val canPlay: Boolean get() = currentTrack != null
}

/**
 * One MediaController connection per process. Screens observe [state] and call
 * the command methods; the MediaSessionService owns actual playback so it keeps
 * running when the UI goes away.
 */
class PlaybackController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val graph = Tether.graph(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())
    val state: kotlinx.coroutines.flow.StateFlow<PlaybackState> = _state

    private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private var queueJob: Job? = null
    private var lastQueueSignature: String? = null
    private var sleepDeadlineMs = 0L
    private var localSleepJob: Job? = null
    private val trackCache = mutableMapOf<String, Track>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.value = _state.value.copy(error = null)
            refreshState(forceQueue = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                error = error.message?.takeIf { it.isNotBlank() }
                    ?: "Playback failed. Check the file or connection and try again.",
            )
        }
    }

    init {
        connect()
        scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isConnected) updatePosition(c)
                delay(if (c?.isPlaying == true) 400 else 1_000)
            }
        }
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        connection = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    _state.value = _state.value.copy(connecting = false, connected = true)
                    refreshState(forceQueue = true)
                    querySleepTimer()
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        connecting = false,
                        connected = false,
                        error = "The playback service could not start: ${error.message ?: "unknown error"}",
                    )
                }
            },
            mainExecutor,
        )
    }

    private fun refreshState(forceQueue: Boolean = false) {
        val c = controller ?: return
        val count = c.mediaItemCount
        val ids = (0 until count).map { index -> c.getMediaItemAt(index).mediaId }
        val signature = ids.joinToString("\u0000")
        val queueChanged = forceQueue || signature != lastQueueSignature
        if (!queueChanged) {
            applyState(c)
            return
        }
        lastQueueSignature = signature
        queueJob?.cancel()
        queueJob = scope.launch {
            val missing = ids.filter { it.isNotBlank() && it !in trackCache }
            if (missing.isNotEmpty()) {
                val rows = withContext(Dispatchers.IO) { graph.library.getTracks(missing) }
                rows.forEach { trackCache[it.id] = it }
            }
            applyState(c)
        }
    }

    private fun applyState(c: MediaController) {
        val count = c.mediaItemCount
        val queue = (0 until count).mapNotNull { index ->
            val item = c.getMediaItemAt(index)
            trackCache[item.mediaId] ?: item.toTrackStub()
        }
        val index = c.currentMediaItemIndex
        val current = queue.getOrNull(index) ?: trackCache[c.currentMediaItem?.mediaId]
        val rawDuration = c.duration
        val duration = when {
            rawDuration > 0 && rawDuration != C.TIME_UNSET -> rawDuration
            current != null -> current.durationMs
            else -> 0L
        }
        val remaining = sleepRemaining()
        _state.value = _state.value.copy(
            connected = c.isConnected,
            connecting = false,
            queue = queue,
            currentTrack = current,
            currentIndex = index,
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0).takeUnless { it == C.TIME_UNSET } ?: 0L,
            durationMs = duration.coerceAtLeast(0),
            bufferedPercent = c.bufferedPercentage.coerceIn(0, 100),
            shuffle = c.shuffleModeEnabled,
            repeatMode = RepeatMode.from(c.repeatMode),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            sleepTimerActive = remaining > 0,
            sleepRemainingMs = remaining,
        )
    }

    private fun updatePosition(c: MediaController) {
        val current = _state.value.currentTrack
        val rawDuration = c.duration
        val duration = when {
            rawDuration > 0 && rawDuration != C.TIME_UNSET -> rawDuration
            current != null -> current.durationMs
            else -> 0L
        }
        val remaining = sleepRemaining()
        _state.value = _state.value.copy(
            positionMs = c.currentPosition.coerceAtLeast(0).takeUnless { it == C.TIME_UNSET } ?: 0L,
            durationMs = duration.coerceAtLeast(0),
            bufferedPercent = c.bufferedPercentage.coerceIn(0, 100),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            sleepTimerActive = remaining > 0,
            sleepRemainingMs = remaining,
        )
    }

    private fun sleepRemaining(): Long {
        if (sleepDeadlineMs <= 0) return 0
        val remaining = (sleepDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
        if (remaining == 0L) sleepDeadlineMs = 0
        return remaining
    }

    private fun querySleepTimer() {
        val c = controller ?: return
        val future = runCatching {
            c.sendCustomCommand(
                SessionCommand(PlaybackCommands.GET_SLEEP_TIMER, Bundle.EMPTY),
                Bundle.EMPTY,
            )
        }.getOrNull() ?: return
        future.addListener(
            {
                runCatching { future.get() }.getOrNull()?.let { result ->
                    if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                        val remaining = result.extras.getLong(PlaybackCommands.EXTRA_REMAINING_MS, 0)
                        if (localSleepJob == null) {
                            sleepDeadlineMs = if (remaining > 0) {
                                System.currentTimeMillis() + remaining
                            } else {
                                0
                            }
                            _state.value = _state.value.copy(
                                sleepTimerActive = remaining > 0,
                                sleepRemainingMs = remaining,
                            )
                        }
                    }
                }
            },
            mainExecutor,
        )
    }

    fun playNow(track: Track, queue: List<Track> = listOf(track)) {
        val items = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val start = items.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(items, start)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val distinct = tracks.distinctBy { it.id }.filter { TrackMedia.playableUri(it) != null }
        if (distinct.isEmpty()) {
            _state.value = _state.value.copy(error = "There is nothing playable in this selection.")
            return
        }
        scope.launch {
            ensureRows(distinct)
            val c = controller ?: return@launch
            val index = startIndex.coerceIn(0, distinct.lastIndex)
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(distinct.map { it.toMediaItem() }, index, 0)
            c.prepare()
            c.play()
            _state.value = _state.value.copy(error = null)
            refreshState(forceQueue = true)
        }
    }

    fun playNext(track: Track) {
        scope.launch {
            ensureRows(listOf(track))
            val c = controller ?: return@launch
            if (c.mediaItemCount == 0 || c.currentMediaItemIndex < 0) {
                c.setMediaItems(listOf(track.toMediaItem()), 0, 0)
                c.prepare()
            } else {
                val target = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
                c.addMediaItem(target, track.toMediaItem())
            }
            refreshState(forceQueue = true)
        }
    }

    fun addToQueue(track: Track) {
        scope.launch {
            ensureRows(listOf(track))
            val c = controller ?: return@launch
            c.addMediaItem(track.toMediaItem())
            if (c.mediaItemCount == 1) c.prepare()
            refreshState(forceQueue = true)
        }
    }

    fun addToQueueNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        scope.launch {
            ensureRows(tracks)
            val c = controller ?: return@launch
            val at = if (c.currentMediaItemIndex >= 0) c.currentMediaItemIndex + 1 else 0
            c.addMediaItems(at.coerceAtMost(c.mediaItemCount), tracks.distinctBy { it.id }.map { it.toMediaItem() })
            if (c.mediaItemCount == tracks.size) c.prepare()
            refreshState(forceQueue = true)
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying || c.playWhenReady) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
        refreshState()
    }

    fun play() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0)
        else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs.coerceAtLeast(0))
        updatePosition(c)
    }

    fun seekToIndex(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0)
        c.play()
        refreshState()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        refreshState()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = RepeatMode.from(c.repeatMode).next().value
        refreshState()
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        if (c.mediaItemCount == 1) {
            c.stop()
            c.clearMediaItems()
        } else {
            c.removeMediaItem(index)
        }
        refreshState(forceQueue = true)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return
        c.moveMediaItem(from, to)
        refreshState(forceQueue = true)
    }

    fun clearQueue() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        refreshState(forceQueue = true)
    }

    fun retry() {
        val c = controller ?: return
        _state.value = _state.value.copy(error = null)
        c.prepare()
        c.play()
    }

    fun setSleepTimer(minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 180)
        localSleepJob?.cancel()
        localSleepJob = null
        if (safeMinutes == 0) {
            sleepDeadlineMs = 0
            _state.value = _state.value.copy(sleepTimerActive = false, sleepRemainingMs = 0)
        } else {
            sleepDeadlineMs = System.currentTimeMillis() + safeMinutes * 60_000L
            _state.value = _state.value.copy(
                sleepTimerActive = true,
                sleepRemainingMs = safeMinutes * 60_000L,
            )
        }
        val c = controller
        val args = Bundle().apply { putInt(PlaybackCommands.EXTRA_MINUTES, safeMinutes) }
        val future = runCatching {
            c?.sendCustomCommand(SessionCommand(PlaybackCommands.SLEEP_TIMER, Bundle.EMPTY), args)
        }.getOrNull()
        if (future == null) {
            if (safeMinutes > 0) startLocalSleep(safeMinutes)
            return
        }
        future.addListener(
            {
                val result = runCatching { future.get() }.getOrNull()
                val remaining = result?.extras?.getLong(PlaybackCommands.EXTRA_REMAINING_MS, 0) ?: 0
                if (result == null || result.resultCode != SessionResult.RESULT_SUCCESS) {
                    if (safeMinutes > 0) startLocalSleep(safeMinutes)
                } else {
                    sleepDeadlineMs = if (remaining > 0) System.currentTimeMillis() + remaining else 0
                    _state.value = _state.value.copy(
                        sleepTimerActive = remaining > 0,
                        sleepRemainingMs = remaining,
                    )
                }
            },
            mainExecutor,
        )
    }

    private fun startLocalSleep(minutes: Int) {
        localSleepJob?.cancel()
        sleepDeadlineMs = System.currentTimeMillis() + minutes * 60_000L
        localSleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            sleepDeadlineMs = 0
            _state.value = _state.value.copy(sleepTimerActive = false, sleepRemainingMs = 0)
        }
    }

    private suspend fun ensureRows(tracks: List<Track>) {
        withContext(Dispatchers.IO) {
            tracks.forEach { graph.library.ensureTrackRow(it) }
        }
    }

    private fun MediaItem.toTrackStub(): Track {
        val extras = mediaMetadata.extras
        val originName = extras?.getString(TrackMedia.EXTRA_ORIGIN)
        val origin = runCatching { dev.tether.core.model.TrackOrigin.valueOf(originName.orEmpty()) }
            .getOrDefault(dev.tether.core.model.TrackOrigin.REMOTE)
        return Track(
            id = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString().orEmpty(),
            albumArtist = mediaMetadata.albumArtist?.toString().orEmpty(),
            durationMs = mediaMetadata.durationMs ?: extras?.getLong(TrackMedia.EXTRA_DURATION, 0L) ?: 0L,
            artworkUri = mediaMetadata.artworkUri?.toString(),
            origin = origin,
            sourceId = extras?.getString(TrackMedia.EXTRA_SOURCE_ID).orEmpty(),
            sourceLabel = extras?.getString(TrackMedia.EXTRA_SOURCE_LABEL).orEmpty(),
            license = extras?.getString(TrackMedia.EXTRA_LICENSE),
            remoteUri = extras?.getString(TrackMedia.EXTRA_REMOTE_URI),
        )
    }

    companion object {
        @Volatile
        private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PlaybackController(context.applicationContext).also { instance = it }
            }
        }
    }
}
