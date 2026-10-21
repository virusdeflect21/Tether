package dev.tether.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.DownloadStatus
import dev.tether.core.model.LyricsDocument
import dev.tether.core.model.Playlist
import dev.tether.core.model.Track
import dev.tether.core.model.TrackOrigin

@Entity(
    tableName = "tracks",
    indices = [
        Index("sourceId"),
        Index("origin"),
        Index("albumKey"),
        Index("artistKey"),
        Index("lastPlayedAt"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
    val uri: String?,
    val remoteUri: String?,
    val artworkUri: String?,
    val origin: String,
    val sourceId: String,
    val sourceLabel: String,
    val providerItemId: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val genre: String?,
    val folder: String?,
    val license: String?,
    val videoUri: String?,
    val addedAt: Long,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val albumKey: String,
    val artistKey: String,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long,
)

@Entity(tableName = "favorite_collections")
data class FavoriteCollectionEntity(
    @PrimaryKey val key: String,
    val type: String,
    val name: String,
    val artworkUri: String?,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId"), Index("playedAt")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val listeningMs: Long,
)

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("updatedAt")],
)
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val sourceLabel: String,
    val sourceId: String,
    val status: String,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val format: String,
    val quality: String,
    val filePath: String?,
    val license: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val attempts: Int,
    val sourceUri: String?,
    val artworkUri: String?,
)

@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val raw: String,
    val synced: Boolean,
    val providerId: String,
    val sourceLabel: String,
    val attribution: String,
    val externalUrl: String?,
    val local: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Entity(tableName = "media_folders")
data class MediaFolderEntity(
    @PrimaryKey val treeUri: String,
    val name: String,
    val addedAt: Long,
    val lastScanAt: Long?,
    val trackCount: Int,
    val error: String?,
)

@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = 0,
    val queueIds: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val updatedAt: Long,
)

data class AlbumRow(
    val albumKey: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int?,
    val sourceId: String,
)

data class ArtistRow(
    val artistKey: String,
    val name: String,
    val artworkUri: String?,
    val trackCount: Int,
    val albumCount: Int,
)

data class FolderRow(
    val folder: String,
    val trackCount: Int,
)

data class GenreRow(
    val genre: String,
    val trackCount: Int,
)

data class PlaylistRow(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val coverArtUri: String?,
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    uri = uri,
    remoteUri = remoteUri,
    artworkUri = artworkUri,
    origin = runCatching { TrackOrigin.valueOf(origin) }.getOrDefault(TrackOrigin.LOCAL),
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    providerItemId = providerItemId,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    genre = genre,
    folder = folder,
    license = license,
    videoUri = videoUri,
    addedAt = addedAt,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    uri = uri,
    remoteUri = remoteUri,
    artworkUri = artworkUri,
    origin = origin.name,
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    providerItemId = providerItemId,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    genre = genre,
    folder = folder,
    license = license,
    videoUri = videoUri,
    addedAt = addedAt,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    albumKey = albumKey,
    artistKey = artistKey,
)

fun PlaylistRow.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trackCount = trackCount,
    coverArtUri = coverArtUri,
)

fun DownloadEntity.toRecord(): DownloadRecord = DownloadRecord(
    trackId = trackId,
    title = title,
    artist = artist,
    sourceLabel = sourceLabel,
    sourceId = sourceId,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    progress = progress,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    format = format,
    quality = quality,
    filePath = filePath,
    license = license,
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    attempts = attempts,
    sourceUri = sourceUri,
    artworkUri = artworkUri,
)

fun DownloadRecord.toEntity(): DownloadEntity = DownloadEntity(
    trackId = trackId,
    title = title,
    artist = artist,
    sourceLabel = sourceLabel,
    sourceId = sourceId,
    status = status.name,
    progress = progress,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    format = format,
    quality = quality,
    filePath = filePath,
    license = license,
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    attempts = attempts,
    sourceUri = sourceUri,
    artworkUri = artworkUri,
)

fun LyricsEntity.toDocument(lines: List<dev.tether.core.model.LyricsLine>): LyricsDocument = LyricsDocument(
    trackId = trackId,
    lines = lines,
    raw = raw,
    synced = synced,
    sourceLabel = sourceLabel,
    providerId = providerId,
    attribution = attribution,
    updatedAt = updatedAt,
    externalUrl = externalUrl,
    local = local,
)
