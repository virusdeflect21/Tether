package dev.tether.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.tether.core.model.Track

/** Stable keys stored on every MediaItem so controllers can recover the track. */
object TrackMedia {
    const val EXTRA_TRACK_ID = "dev.tether.track_id"
    const val EXTRA_ARTIST = "dev.tether.artist"
    const val EXTRA_ALBUM = "dev.tether.album"
    const val EXTRA_ORIGIN = "dev.tether.origin"
    const val EXTRA_SOURCE_ID = "dev.tether.source_id"
    const val EXTRA_SOURCE_LABEL = "dev.tether.source_label"
    const val EXTRA_LICENSE = "dev.tether.license"
    const val EXTRA_REMOTE_URI = "dev.tether.remote_uri"
    const val EXTRA_DURATION = "dev.tether.duration"

    fun playableUri(track: Track): String? {
        val preferred = when {
            track.isOfflinePlayable -> track.uri
            else -> track.uri ?: track.remoteUri
        }
        return preferred?.takeIf { it.isNotBlank() } ?: track.remoteUri?.takeIf { it.isNotBlank() }
    }
}

fun MediaItem.trackIdOrNull(): String? =
    mediaId.takeIf { it.isNotBlank() }
        ?: mediaMetadata.extras?.getString(TrackMedia.EXTRA_TRACK_ID)

fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(TrackMedia.EXTRA_TRACK_ID, id)
        putString(TrackMedia.EXTRA_ARTIST, artist)
        putString(TrackMedia.EXTRA_ALBUM, album)
        putString(TrackMedia.EXTRA_ORIGIN, origin.name)
        putString(TrackMedia.EXTRA_SOURCE_ID, sourceId)
        putString(TrackMedia.EXTRA_SOURCE_LABEL, sourceLabel)
        putString(TrackMedia.EXTRA_LICENSE, license)
        putString(TrackMedia.EXTRA_REMOTE_URI, remoteUri)
        putLong(TrackMedia.EXTRA_DURATION, durationMs)
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(TrackMedia.playableUri(this))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setTrackNumber(trackNumber)
                .setReleaseYear(year)
                .setGenre(genre)
                .setExtras(extras)
                .build(),
        )
        .build()
}

enum class RepeatMode(val value: Int) {
    OFF(Player.REPEAT_MODE_OFF),
    ALL(Player.REPEAT_MODE_ALL),
    ONE(Player.REPEAT_MODE_ONE);

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun from(value: Int): RepeatMode = entries.firstOrNull { it.value == value } ?: OFF
    }
}
