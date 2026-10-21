package dev.tether.core.model

/**
 * Where a track came from and how it can be played.
 *
 * [REMOTE] tracks stream from a provider and may disappear when offline.
 * [LOCAL] tracks are owned by the user on the device. [DOWNLOADED] tracks were
 * obtained from a provider that explicitly permitted downloads and are stored in
 * app-managed storage. Both [LOCAL] and [DOWNLOADED] are offline-playable.
 */
enum class TrackOrigin {
    LOCAL,
    DOWNLOADED,
    REMOTE;

    val isOfflinePlayable: Boolean get() = this != REMOTE
    val isOnDevice: Boolean get() = this != REMOTE
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = artist,
    val durationMs: Long = 0L,
    val uri: String? = null,
    /** Original remote stream, retained when a downloaded copy replaces [uri]. */
    val remoteUri: String? = null,
    val artworkUri: String? = null,
    val origin: TrackOrigin = TrackOrigin.LOCAL,
    val sourceId: String = LocalSource.ID,
    val sourceLabel: String = LocalSource.LABEL,
    val providerItemId: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val genre: String? = null,
    val folder: String? = null,
    val license: String? = null,
    val videoUri: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
) {
    val displayTitle: String get() = title.ifBlank { "Untitled track" }
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown album" }
    val isOfflinePlayable: Boolean get() = origin.isOfflinePlayable
    val hasVideo: Boolean get() = !videoUri.isNullOrBlank()

    /** Album/artist grouping keys must not merge unrelated "Unknown" buckets too aggressively. */
    val albumKey: String get() = "$albumArtist\u0000$album"
    val artistKey: String get() = artist.ifBlank { "Unknown artist" }
}

object LocalSource {
    const val ID = "local"
    const val LABEL = "On this device"
}

object ArchiveSource {
    const val ID = "archive"
    const val LABEL = "Internet Archive"
}

data class AlbumSummary(
    val key: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
    val sourceId: String = LocalSource.ID,
    val year: Int? = null,
)

data class ArtistSummary(
    val key: String,
    val name: String,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val sourceId: String = LocalSource.ID,
)

data class FolderSummary(
    val key: String,
    val name: String,
    val trackCount: Int = 0,
)

data class Playlist(
    val id: Long,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val trackCount: Int = 0,
    val coverArtUri: String? = null,
)

data class PlaylistEntry(
    val track: Track,
    val position: Int,
    val addedAt: Long,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val entries: List<PlaylistEntry>,
)

data class PlaybackHistoryEntry(
    val id: Long,
    val track: Track,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val listeningMs: Long,
)

data class RecentSearch(
    val query: String,
    val searchedAt: Long,
)

data class FavoriteCollection(
    val key: String,
    val type: String,
    val name: String,
    val artworkUri: String? = null,
    val addedAt: Long = 0L,
)

data class LibraryStats(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val favoriteCount: Int = 0,
    val playlistCount: Int = 0,
    val downloadCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalPlays: Int = 0,
    val listeningMs: Long = 0L,
    val offlineBytes: Long = 0L,
)

data class MediaFolder(
    val treeUri: String,
    val name: String,
    val addedAt: Long,
    val lastScanAt: Long? = null,
    val trackCount: Int = 0,
    val error: String? = null,
)

object TetherIds {
    fun local(uri: String): String = "local:" + stableHash(uri)
    fun archive(identifier: String, file: String): String = "ia:$identifier/$file"
    fun artist(name: String): String = "artist:" + name.trim().lowercase()
    fun album(artist: String, album: String): String =
        "album:" + (artist.trim().lowercase() + "\u0000" + album.trim().lowercase())
}

internal fun stableHash(value: String): String {
    // FNV-1a, 64-bit. Stable across processes and app restarts, which is what
    // database keys need (unlike String.hashCode, which is also stable but easier
    // to collide for similar document URIs).
    var hash = -0x340d631b7bdddcdbL
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return java.lang.Long.toUnsignedString(hash, 16)
}
