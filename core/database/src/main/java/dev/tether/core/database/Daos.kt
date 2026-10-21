package dev.tether.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE origin != 'REMOTE' ORDER BY title COLLATE NOCASE")
    fun observeLibrary(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE origin != 'REMOTE' ORDER BY addedAt DESC")
    fun observeLibraryRecent(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE origin = 'DOWNLOADED' ORDER BY addedAt DESC")
    fun observeDownloaded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE origin = 'LOCAL' ORDER BY title COLLATE NOCASE")
    fun observeLocalOnly(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE origin != 'REMOTE' ORDER BY title COLLATE NOCASE")
    suspend fun getLibraryOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE origin = 'LOCAL'")
    suspend fun getLocalOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE origin != 'REMOTE'
          AND (title LIKE '%' || :query || '%' COLLATE NOCASE
            OR artist LIKE '%' || :query || '%' COLLATE NOCASE
            OR album LIKE '%' || :query || '%' COLLATE NOCASE
            OR albumArtist LIKE '%' || :query || '%' COLLATE NOCASE
            OR genre LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY
          CASE WHEN title LIKE :query || '%' COLLATE NOCASE THEN 0 ELSE 1 END,
          title COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchLocal(query: String, limit: Int = 50): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE origin != 'REMOTE'
          AND (:albumKey IS NULL OR albumKey = :albumKey)
          AND (:genre IS NULL OR genre = :genre)
          AND (:folder IS NULL OR folder = :folder)
        ORDER BY discNumber, trackNumber, title COLLATE NOCASE
        """,
    )
    suspend fun getFiltered(albumKey: String?, genre: String?, folder: String?): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE artistKey = :artistKey AND origin != 'REMOTE' ORDER BY year, album, trackNumber")
    suspend fun getByArtist(artistKey: String): List<TrackEntity>

    @Query(
        """
        SELECT albumKey, album AS title, albumArtist AS artist,
          (SELECT artworkUri FROM tracks t2 WHERE t2.albumKey = t.albumKey AND t2.artworkUri IS NOT NULL LIMIT 1) AS artworkUri,
          COUNT(*) AS trackCount, SUM(durationMs) AS durationMs, MAX(year) AS year, MIN(sourceId) AS sourceId
        FROM tracks t WHERE origin != 'REMOTE'
        GROUP BY albumKey ORDER BY album COLLATE NOCASE
        """,
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT artistKey, artist AS name,
          (SELECT artworkUri FROM tracks t2 WHERE t2.artistKey = t.artistKey AND t2.artworkUri IS NOT NULL LIMIT 1) AS artworkUri,
          COUNT(*) AS trackCount, COUNT(DISTINCT albumKey) AS albumCount
        FROM tracks t WHERE origin != 'REMOTE'
        GROUP BY artistKey ORDER BY artist COLLATE NOCASE
        """,
    )
    fun observeArtists(): Flow<List<ArtistRow>>

    @Query(
        """
        SELECT folder, COUNT(*) AS trackCount FROM tracks
        WHERE origin != 'REMOTE' AND folder IS NOT NULL AND folder != ''
        GROUP BY folder ORDER BY folder COLLATE NOCASE
        """,
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Query(
        """
        SELECT genre, COUNT(*) AS trackCount FROM tracks
        WHERE origin != 'REMOTE' AND genre IS NOT NULL AND genre != ''
        GROUP BY genre ORDER BY genre COLLATE NOCASE
        """,
    )
    fun observeGenres(): Flow<List<GenreRow>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN (SELECT trackId, MAX(playedAt) AS maxPlayed FROM playback_history GROUP BY trackId) h
          ON t.id = h.trackId
        ORDER BY h.maxPlayed DESC LIMIT :limit
        """,
    )
    fun observeRecentlyPlayed(limit: Int = 12): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id IN (SELECT trackId FROM favorites) ORDER BY title COLLATE NOCASE")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE origin = 'REMOTE' ORDER BY addedAt DESC LIMIT :limit")
    fun observeCachedRemote(limit: Int = 20): Flow<List<TrackEntity>>

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE tracks SET uri = :uri, origin = :origin, mimeType = :mimeType, sizeBytes = :sizeBytes, folder = :folder, artworkUri = COALESCE(:artworkUri, artworkUri) WHERE id = :id")
    suspend fun updateLocation(
        id: String,
        uri: String?,
        origin: String,
        mimeType: String?,
        sizeBytes: Long,
        folder: String?,
        artworkUri: String?,
    )

    @Query("UPDATE tracks SET artworkUri = :artworkUri WHERE id = :id")
    suspend fun updateArtwork(id: String, artworkUri: String?)

    @Query("UPDATE tracks SET videoUri = :videoUri WHERE id = :id")
    suspend fun updateVideo(id: String, videoUri: String?)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun recordPlay(id: String, playedAt: Long)

    @Query("SELECT COUNT(*) FROM tracks WHERE origin != 'REMOTE'")
    suspend fun libraryCount(): Int

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun totalTrackCount(): Int

    @Query("SELECT * FROM tracks WHERE albumKey = :albumKey AND origin != 'REMOTE'")
    suspend fun getAlbumTracks(albumKey: String): List<TrackEntity>

    @Query("SELECT DISTINCT folder FROM tracks WHERE origin = 'LOCAL' AND folder IS NOT NULL")
    suspend fun localFolders(): List<String>

    @Query("UPDATE tracks SET uri = NULL WHERE origin = 'DOWNLOADED' AND id = :id")
    suspend fun clearDownloadedUri(id: String)

    @Query("UPDATE tracks SET uri = remoteUri, origin = 'REMOTE', sizeBytes = 0, folder = NULL WHERE id = :id")
    suspend fun restoreRemote(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE trackId = :trackId")
    suspend fun get(trackId: String): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}

@Dao
interface FavoriteCollectionDao {
    @Query("SELECT * FROM favorite_collections ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteCollectionEntity)

    @Query("DELETE FROM favorite_collections WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_collections WHERE key = :key)")
    suspend fun exists(key: String): Boolean
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.description, p.createdAt, p.updatedAt,
          (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
          (SELECT t.artworkUri FROM playlist_tracks pt
             INNER JOIN tracks t ON t.id = pt.trackId
             WHERE pt.playlistId = p.id AND t.artworkUri IS NOT NULL
             ORDER BY pt.position LIMIT 1) AS coverArtUri
        FROM playlists p ORDER BY p.updatedAt DESC
        """,
    )
    fun observeRows(): Flow<List<PlaylistRow>>

    @Query(
        """
        SELECT p.id, p.name, p.description, p.createdAt, p.updatedAt,
          (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
          (SELECT t.artworkUri FROM playlist_tracks pt
             INNER JOIN tracks t ON t.id = pt.trackId
             WHERE pt.playlistId = p.id AND t.artworkUri IS NOT NULL
             ORDER BY pt.position LIMIT 1) AS coverArtUri
        FROM playlists p WHERE p.id = :id
        """,
    )
    fun observeRow(id: Long): Flow<PlaylistRow?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PlaylistEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeEntry(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: Long, trackId: String, position: Int)

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun playlistsContaining(trackId: String): List<Long>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Transaction
    suspend fun addEntry(playlistId: Long, trackId: String, addedAt: Long): Boolean {
        val position = nextPosition(playlistId)
        return insertEntry(PlaylistTrackEntity(playlistId, trackId, position, addedAt)) != -1L
    }

    @Transaction
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>, updatedAt: Long) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            updatePosition(playlistId, trackId, index)
        }
        touch(playlistId, updatedAt)
    }

    @Transaction
    suspend fun replaceEntries(playlistId: Long, trackIds: List<String>, addedAt: Long) {
        clear(playlistId)
        trackIds.forEachIndexed { position, trackId ->
            insertEntry(PlaylistTrackEntity(playlistId, trackId, position, addedAt))
        }
        touch(playlistId, addedAt)
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<HistoryEntity>

    @Query("SELECT * FROM playback_history WHERE trackId = :trackId ORDER BY playedAt DESC LIMIT 1")
    suspend fun latestForTrack(trackId: String): HistoryEntity?

    @Query("UPDATE playback_history SET positionMs = :positionMs, listeningMs = :listeningMs, completed = :completed WHERE id = :id")
    suspend fun updateProgress(id: Long, positionMs: Long, listeningMs: Long, completed: Boolean)

    @Query("SELECT COALESCE(SUM(listeningMs), 0) FROM playback_history")
    fun observeListeningMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(listeningMs), 0) FROM playback_history")
    suspend fun listeningMs(): Long

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun count(): Int

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query("DELETE FROM playback_history WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING') ORDER BY createdAt")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun get(trackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<DownloadEntity?>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :updatedAt WHERE trackId = :trackId")
    suspend fun updateStatus(trackId: String, status: String, error: String?, updatedAt: Long)

    @Query(
        """
        UPDATE downloads SET status = 'DOWNLOADING', progress = :progress, bytesDownloaded = :bytes,
          totalBytes = :total, updatedAt = :updatedAt
        WHERE trackId = :trackId AND status = 'DOWNLOADING'
        """,
    )
    suspend fun updateProgress(trackId: String, progress: Float, bytes: Long, total: Long, updatedAt: Long)

    @Query(
        """
        UPDATE downloads SET status = 'DOWNLOADING', error = NULL, updatedAt = :updatedAt
        WHERE trackId = :trackId AND status IN ('QUEUED','DOWNLOADING')
        """,
    )
    suspend fun markDownloading(trackId: String, updatedAt: Long)

    @Query(
        """
        UPDATE downloads SET status = 'PAUSED', error = :error, updatedAt = :updatedAt
        WHERE trackId = :trackId AND status = 'DOWNLOADING'
        """,
    )
    suspend fun markInterrupted(trackId: String, error: String, updatedAt: Long)

    @Query(
        """
        UPDATE downloads SET status = 'FAILED', error = :error, updatedAt = :updatedAt,
          attempts = attempts + 1
        WHERE trackId = :trackId AND status IN ('QUEUED','DOWNLOADING')
        """,
    )
    suspend fun markFailed(trackId: String, error: String, updatedAt: Long)

    @Query(
        """
        UPDATE downloads SET status = 'COMPLETED', progress = 1, bytesDownloaded = :bytes,
          totalBytes = :bytes, completedAt = :completedAt, updatedAt = :completedAt, error = NULL
        WHERE trackId = :trackId AND status IN ('QUEUED','DOWNLOADING')
        """,
    )
    suspend fun markCompleted(trackId: String, bytes: Long, completedAt: Long): Int

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    fun observeDownloadedBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun downloadedBytes(): Long

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM downloads WHERE status IN ('FAILED','CANCELLED')")
    suspend fun clearInactive()
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<LyricsEntity?>

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT COUNT(*) FROM lyrics")
    suspend fun count(): Int

    @Query("DELETE FROM lyrics")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM media_folders ORDER BY addedAt")
    fun observeAll(): Flow<List<MediaFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: MediaFolderEntity)

    @Query("DELETE FROM media_folders WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)

    @Query("SELECT * FROM media_folders")
    suspend fun getAll(): List<MediaFolderEntity>
}

@Dao
interface PlayerStateDao {
    @Query("SELECT * FROM player_state WHERE id = 0")
    suspend fun get(): PlayerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: PlayerStateEntity)

    @Query("DELETE FROM player_state")
    suspend fun clear()
}
