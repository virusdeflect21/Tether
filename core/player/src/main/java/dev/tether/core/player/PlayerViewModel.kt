package dev.tether.core.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.tether.core.model.Track

/**
 * UI-facing facade over [PlaybackController]. Shared by phone and TV screens so
 * both apps use exactly the same playback semantics.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = PlaybackController.get(application)

    val state = controller.state

    fun playNow(track: Track, queue: List<Track> = listOf(track)) = controller.playNow(track, queue)

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) =
        controller.playQueue(tracks, startIndex, shuffle)

    fun playNext(track: Track) = controller.playNext(track)

    fun addToQueue(track: Track) = controller.addToQueue(track)

    fun addToQueueNext(tracks: List<Track>) = controller.addToQueueNext(tracks)

    fun togglePlayPause() = controller.togglePlayPause()

    fun play() = controller.play()

    fun pause() = controller.pause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun seekToIndex(index: Int) = controller.seekToIndex(index)

    fun toggleShuffle() = controller.toggleShuffle()

    fun cycleRepeat() = controller.cycleRepeat()

    fun removeQueueItem(index: Int) = controller.removeQueueItem(index)

    fun moveQueueItem(from: Int, to: Int) = controller.moveQueueItem(from, to)

    fun clearQueue() = controller.clearQueue()

    fun retry() = controller.retry()

    fun setSleepTimer(minutes: Int) = controller.setSleepTimer(minutes)
}
