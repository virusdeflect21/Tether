package dev.tether.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import dev.tether.core.database.AlbumRow
import dev.tether.core.database.ArtistRow
import dev.tether.core.database.FavoriteCollectionEntity
import dev.tether.core.database.FavoriteEntity
import dev.tether.core.database.FolderRow
import dev.tether.core.database.GenreRow
import dev.tether.core.database.MediaFolderEntity
import dev.tether.core.database.TetherDatabase
import dev.tether.core.database.toEntity
import dev.tether.core.database.toTrack
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.FavoriteCollection
import dev.tether.core.model.FolderSummary
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.LocalSource
import dev.tether.core.model.MediaFolder
import dev.tether.core.model.TetherIds
import dev.tether.core.model.Track
import dev.tether.core.model.TrackOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class ScanResult(
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val removed: Int = 0,
    val errors: List<String> = emptyList(),
    val cancelled: Boolean = false,
) {
    val total: Int get() = added + updated
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val summary: String
        get() = buildString {
            append("$total track${if (total == 1) "" else "s"} found")
            if (added > 0) append(" · $added new")
            if (removed > 0) append(" · $removed removed")
            if (skipped > 0) append(" · $skipped skipped")
        }
}

class LibraryRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val metadataReader: MetadataReader,
    private val artworkStore: ArtworkStore,
    private val settings: SettingsRepository,
) {
    private val trackDao = database.trackDao()
    private val favoriteDao = database.favoriteDao()
    private val collectionDao = database.favoriteCollectionDao()
    private val folderDao = database.folderDao()
    private val historyDao = database.historyDao()

    val tracks: Flow<List<Track>> = trackDao.observeLibrary().map { rows -> rows.map { it.toTrack() } }
    val recentlyAdded: Flow<List<Track>> = trackDao.observeLibraryRecent().map { rows -> rows.map { it.toTrack() } }
    val downloadedTracks: Flow<List<Track>> = trackDao.observeDownloaded().map { rows -> rows.map { it.toTrack() } }
    val localTracks: Flow<List<Track>> = trackDao.observeLocalOnly().map { rows -> rows.map { it.toTrack() } }
    val favoriteTracks: Flow<List<Track>> = trackDao.observeFavoriteTracks().map { rows -> rows.map { it.toTrack() } }
    val favoriteIds: Flow<List<String>> = trackDao.observeFavoriteIds()
    val recentlyPlayed: Flow<List<Track>> = trackDao.observeRecentlyPlayed().map { rows -> rows.map { it.toTrack() } }
    val albums: Flow<List<AlbumSummary>> = trackDao.observeAlbums().map { rows -> rows.map(::toAlbum) }
    val artists: Flow<List<ArtistSummary>> = trackDao.observeArtists().map { rows -> rows.map(::toArtist) }
    val folders: Flow<List<FolderSummary>> = trackDao.observeFolders().map { rows -> rows.map(::toFolder) }
    val genres: Flow<List<FolderSummary>> = trackDao.observeGenres().map { rows -> rows.map(::toGenre) }
    val mediaFolders: Flow<List<MediaFolder>> = folderDao.observeAll().map { rows ->
        rows.map { MediaFolder(it.treeUri, it.name, it.addedAt, it.lastScanAt, it.trackCount, it.error) }
    }
    val favoriteCollections: Flow<List<FavoriteCollection>> = collectionDao.observeAll().map { rows ->
        rows.map { FavoriteCollection(it.key, it.type, it.name, it.artworkUri, it.addedAt) }
    }

    fun observeFavoriteCollection(type: String): Flow<List<FavoriteCollection>> =
        favoriteCollections.map { list -> list.filter { it.type == type } }

    suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        trackDao.getById(id)?.toTrack()
    }

    suspend fun getTrackEntity(id: String): dev.tether.core.database.TrackEntity? = withContext(Dispatchers.IO) {
        trackDao.getById(id)
    }

    suspend fun getTracks(ids: List<String>): List<Track> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList()
        else trackDao.getByIds(ids).map { it.toTrack() }
    }

    suspend fun allLibraryTracks(): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getLibraryOnce().map { it.toTrack() }
    }

    suspend fun albumTracks(albumKey: String): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getAlbumTracks(albumKey).map { it.toTrack() }
    }

    suspend fun artistTracks(artistKey: String): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getByArtist(artistKey).map { it.toTrack() }
    }

    suspend fun tracksForGenreOrFolder(genre: String? = null, folder: String? = null): List<Track> =
        withContext(Dispatchers.IO) {
            trackDao.getFiltered(null, genre, folder).map { it.toTrack() }
        }

    suspend fun searchLocal(query: String, limit: Int = 50): List<Track> = withContext(Dispatchers.IO) {
        trackDao.searchLocal(query, limit).map { it.toTrack() }
    }

    suspend fun isFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        favoriteDao.isFavorite(trackId)
    }

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = favoriteDao.get(track.id)
        if (existing != null) {
            favoriteDao.delete(track.id)
            false
        } else {
            ensureTrackRow(track)
            favoriteDao.insert(FavoriteEntity(track.id, System.currentTimeMillis()))
            true
        }
    }

    suspend fun setFavorite(track: Track, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val existing = favoriteDao.get(track.id)
        if (favorite && existing == null) {
            ensureTrackRow(track)
            favoriteDao.insert(FavoriteEntity(track.id, System.currentTimeMillis()))
        } else if (!favorite && existing != null) {
            favoriteDao.delete(track.id)
        }
        favorite
    }

    suspend fun toggleCollectionFavorite(type: String, key: String, name: String, artworkUri: String?): Boolean =
        withContext(Dispatchers.IO) {
            val id = "$type:$key"
            if (collectionDao.exists(id)) {
                collectionDao.delete(id)
                false
            } else {
                collectionDao.insert(
                    FavoriteCollectionEntity(id, type, name, artworkUri, System.currentTimeMillis()),
                )
                true
            }
        }

    suspend fun saveRemoteTrack(track: Track): Track = withContext(Dispatchers.IO) {
        val existing = trackDao.getById(track.id)?.toTrack()
        val merged = if (existing != null) {
            track.copy(
                addedAt = existing.addedAt,
                playCount = existing.playCount,
                lastPlayedAt = existing.lastPlayedAt,
                artworkUri = track.artworkUri ?: existing.artworkUri,
                videoUri = track.videoUri ?: existing.videoUri,
                origin = if (existing.origin == TrackOrigin.DOWNLOADED) existing.origin else track.origin,
                uri = if (existing.origin == TrackOrigin.DOWNLOADED) existing.uri else track.uri,
                remoteUri = track.remoteUri ?: existing.remoteUri,
            )
        } else {
            track
        }
        trackDao.upsert(merged.toEntity())
        merged
    }

    suspend fun ensureTrackRow(track: Track) {
        withContext(Dispatchers.IO) {
            if (trackDao.getById(track.id) == null) trackDao.upsert(track.toEntity())
        }
    }

    suspend fun recordPlay(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.recordPlay(trackId, System.currentTimeMillis())
    }

    suspend fun refreshArtwork(track: Track): String? = withContext(Dispatchers.IO) {
        artworkStore.cachedUri(track.id)?.let { cached ->
            if (track.artworkUri != cached) trackDao.updateArtwork(track.id, cached)
            return@withContext cached
        }
        val uri = track.uri?.let(Uri::parse) ?: return@withContext null
        if (uri.scheme != "content" && uri.scheme != "file") return@withContext null
        val cached = artworkStore.cacheEmbedded(track.id, uri) ?: return@withContext null
        trackDao.updateArtwork(track.id, cached)
        cached
    }

    suspend fun cacheRemoteArtwork(trackId: String, url: String): String? = withContext(Dispatchers.IO) {
        artworkStore.cachedUri(trackId) ?: artworkStore.cacheUrl(trackId, url)?.also { cached ->
            trackDao.updateArtwork(trackId, cached)
        }
    }

    suspend fun setVideo(trackId: String, videoUri: String?) = withContext(Dispatchers.IO) {
        trackDao.updateVideo(trackId, videoUri)
    }

    /** Points a provider track at its app-managed offline copy. */
    suspend fun markDownloaded(trackId: String, fileUri: String, mimeType: String?, sizeBytes: Long) =
        withContext(Dispatchers.IO) {
            trackDao.updateLocation(
                id = trackId,
                uri = fileUri,
                origin = TrackOrigin.DOWNLOADED.name,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                folder = "Tether downloads",
                artworkUri = null,
            )
        }

    /** Restores streaming after an offline copy is deleted, keeping metadata and playback stats. */
    suspend fun restoreStreaming(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.restoreRemote(trackId)
    }

    suspend fun deleteTrack(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.delete(trackId)
        artworkStore.fileFor(trackId).delete()
    }

    // ---------------------------------------------------------------- MediaStore

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanMediaStore(): ScanResult = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) {
            val error = "Media permission is not granted. Allow music access, or add a folder with the system picker."
            settings.recordScan(System.currentTimeMillis(), error)
            return@withContext ScanResult(errors = listOf(error))
        }
        try {
            val now = System.currentTimeMillis()
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.TRACK)
                add(MediaStore.Audio.Media.YEAR)
                if (Build.VERSION.SDK_INT >= 30) add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.DATE_ADDED)
                if (Build.VERSION.SDK_INT >= 29) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    add(MediaStore.Audio.Media.DATA)
                }
            }.toTypedArray()
            val existing = trackDao.getLocalOnce().associateBy { it.id }
            val existingSignatures = existing.values.map { signature(it.toTrack()) }.toMutableSet()
            val tracks = mutableListOf<Track>()
            var added = 0
            var updated = 0
            var skipped = 0
            var errors = 0

            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val folderCol = if (Build.VERSION.SDK_INT >= 29) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                }
                while (cursor.moveToNext()) {
                    try {
                        val mediaId = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, mediaId)
                        val id = TetherIds.local(uri.toString())
                        val title = cursor.getString(titleCol)?.trim().orEmpty()
                        if (title.isBlank()) {
                            skipped++
                            continue
                        }
                        val artist = cursor.getString(artistCol)?.trim().orEmpty().ifBlank { "Unknown artist" }
                        val album = cursor.getString(albumCol)?.trim().orEmpty().ifBlank { "Unknown album" }
                        val albumArtist = cursor.getString(albumArtistCol)?.trim().orEmpty().ifBlank { artist }
                        val albumId = cursor.getLong(albumIdCol)
                        val duration = cursor.getLong(durationCol)
                        val trackNumber = cursor.getInt(trackCol).let { if (it > 0) it % 1000 else null }
                        val year = cursor.getInt(yearCol).takeIf { it > 0 }
                        val genre = genreCol.takeIf { it >= 0 }?.let { cursor.getString(it) }?.trim()?.takeIf { it.isNotBlank() }
                        val name = cursor.getString(nameCol)
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol)
                        val addedSeconds = cursor.getLong(dateCol)
                        val rawFolder = folderCol.takeIf { it >= 0 }?.let { cursor.getString(it) }
                        val folder = normalizeFolder(rawFolder)
                        val previous = existing[id]
                        val type = "LOCAL"
                        if (previous == null) {
                            added++
                            existingSignatures += "$title|$artist|$album|${duration / 1000}|$size"
                        } else {
                            updated++
                        }
                        val artwork = previous?.artworkUri ?: if (albumId > 0) {
                            "content://media/external/audio/albumart/$albumId"
                        } else {
                            null
                        }
                        tracks += Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            durationMs = duration,
                            uri = uri.toString(),
                            artworkUri = artwork,
                            origin = TrackOrigin.LOCAL,
                            sourceId = LocalSource.ID,
                            sourceLabel = LocalSource.LABEL,
                            mimeType = mime,
                            sizeBytes = size,
                            year = year,
                            trackNumber = trackNumber,
                            genre = genre,
                            folder = folder,
                            addedAt = if (previous != null) previous.addedAt else addedSeconds.takeIf { it > 0 }?.times(1000) ?: now,
                            playCount = previous?.playCount ?: 0,
                            lastPlayedAt = previous?.lastPlayedAt,
                        )
                    } catch (_: Exception) {
                        errors++
                    }
                }
            }
            tracks.chunked(400).forEach { trackDao.upsertAll(it.map(Track::toEntity)) }
            val errorMessage = if (errors > 0) "$errors unreadable MediaStore entries were skipped." else null
            settings.recordScan(now, errorMessage)
            ScanResult(
                added = added,
                updated = updated,
                skipped = skipped,
                errors = errorMessage?.let(::listOf).orEmpty(),
            )
        } catch (error: Exception) {
            val message = error.message ?: "MediaStore scan failed."
            settings.recordScan(System.currentTimeMillis(), message)
            ScanResult(errors = listOf(message))
        }
    }

    // ----------------------------------------------------------------- SAF trees

    suspend fun scanFolder(treeUri: String): ScanResult = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
        if (root == null || !root.exists() || !root.isDirectory) {
            val error = "The selected folder is no longer accessible."
            folderDao.upsert(
                MediaFolderEntity(treeUri, folderName(treeUri), System.currentTimeMillis(), null, 0, error),
            )
            return@withContext ScanResult(errors = listOf(error))
        }
        val rootName = root.name?.takeIf { it.isNotBlank() } ?: "Music folder"
        val existing = trackDao.getLocalOnce().associateBy { it.id }
        val signatures = existing.values.map { signature(it.toTrack()) }.toMutableSet()
        val found = mutableListOf<Track>()
        val errors = mutableListOf<String>()
        var skipped = 0
        var added = 0
        var updated = 0

        suspend fun walk(directory: DocumentFile, relative: String, depth: Int) {
            if (depth > 12) return
            val children = runCatching { directory.listFiles() }.getOrElse {
                errors += "Could not read a folder: ${it.message ?: "permission denied"}"
                return
            }
            for (child in children) {
                if (child.isDirectory) {
                    walk(child, if (relative.isBlank()) child.name.orEmpty() else "$relative/${child.name}", depth + 1)
                    continue
                }
                val name = child.name ?: continue
                val mime = child.type
                if (!looksLikeAudio(name, mime)) continue
                try {
                    val uri = child.uri
                    val id = TetherIds.local(uri.toString())
                    val meta = metadataReader.read(uri, name, mime)
                    if (meta.durationMs <= 0) {
                        // Some formats do not report duration; still import them, but ignore
                        // obviously broken files.
                    }
                    val previous = existing[id]
                    val sig = signature(
                        Track(
                            id = id,
                            title = meta.title,
                            artist = meta.artist,
                            album = meta.album,
                            durationMs = meta.durationMs,
                            sizeBytes = child.length(),
                        ),
                    )
                    if (previous == null && sig in signatures) {
                        skipped++
                        continue
                    }
                    if (previous == null) added++ else updated++
                    signatures += sig
                    val artwork = previous?.artworkUri ?: artworkStore.cacheEmbedded(id, uri)
                    val folder = if (relative.isBlank()) rootName else "$rootName/$relative"
                    val track = Track(
                        id = id,
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        albumArtist = meta.albumArtist,
                        durationMs = meta.durationMs,
                        uri = uri.toString(),
                        artworkUri = artwork,
                        origin = TrackOrigin.LOCAL,
                        sourceId = LocalSource.ID,
                        sourceLabel = LocalSource.LABEL,
                        mimeType = mime,
                        sizeBytes = child.length(),
                        year = meta.year,
                        trackNumber = meta.trackNumber,
                        discNumber = meta.discNumber,
                        genre = meta.genre,
                        folder = folder,
                        addedAt = previous?.addedAt ?: (child.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()),
                        playCount = previous?.playCount ?: 0,
                        lastPlayedAt = previous?.lastPlayedAt,
                    )
                    found += track
                    trackDao.upsert(track.toEntity())
                } catch (error: Exception) {
                    errors += "$name: ${error.message ?: "could not be read"}"
                }
            }
        }

        try {
            walk(root, "", 0)
            val scanAt = System.currentTimeMillis()
            folderDao.upsert(
                MediaFolderEntity(
                    treeUri = treeUri,
                    name = rootName,
                    addedAt = folderDao.getAll().firstOrNull { it.treeUri == treeUri }?.addedAt ?: scanAt,
                    lastScanAt = scanAt,
                    trackCount = found.size,
                    error = errors.firstOrNull(),
                ),
            )
            settings.recordScan(scanAt, errors.firstOrNull())
            ScanResult(added = added, updated = updated, skipped = skipped, errors = errors.take(5))
        } catch (error: Exception) {
            val message = error.message ?: "Folder scan failed."
            folderDao.upsert(
                MediaFolderEntity(treeUri, rootName, System.currentTimeMillis(), null, 0, message),
            )
            settings.recordScan(System.currentTimeMillis(), message)
            ScanResult(errors = listOf(message))
        }
    }

    suspend fun addFolder(treeUri: String): ScanResult {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(treeUri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return scanFolder(treeUri)
    }

    suspend fun removeFolder(treeUri: String) = withContext(Dispatchers.IO) {
        folderDao.delete(treeUri)
    }

    suspend fun importFiles(uris: List<Uri>): ScanResult = withContext(Dispatchers.IO) {
        var added = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val existing = trackDao.getLocalOnce().associateBy { it.id }
        uris.forEach { uri ->
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val name = displayName(uri)
                val mime = context.contentResolver.getType(uri)
                if (!looksLikeAudio(name.orEmpty(), mime)) {
                    errors += "${name ?: uri}: not an audio file"
                    return@forEach
                }
                val meta = metadataReader.read(uri, name, mime)
                val id = TetherIds.local(uri.toString())
                val previous = existing[id]
                val size = sizeOf(uri)
                val artwork = previous?.artworkUri ?: artworkStore.cacheEmbedded(id, uri)
                val track = Track(
                    id = id,
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    albumArtist = meta.albumArtist,
                    durationMs = meta.durationMs,
                    uri = uri.toString(),
                    artworkUri = artwork,
                    origin = TrackOrigin.LOCAL,
                    sourceId = LocalSource.ID,
                    sourceLabel = LocalSource.LABEL,
                    mimeType = mime,
                    sizeBytes = size,
                    year = meta.year,
                    trackNumber = meta.trackNumber,
                    discNumber = meta.discNumber,
                    genre = meta.genre,
                    folder = "Imported files",
                    addedAt = previous?.addedAt ?: System.currentTimeMillis(),
                    playCount = previous?.playCount ?: 0,
                    lastPlayedAt = previous?.lastPlayedAt,
                )
                trackDao.upsert(track.toEntity())
                if (previous == null) added++ else updated++
            } catch (error: Exception) {
                errors += "${displayName(uri) ?: uri}: ${error.message ?: "could not be imported"}"
            }
        }
        settings.recordScan(System.currentTimeMillis(), errors.firstOrNull())
        ScanResult(added = added, updated = updated, errors = errors.take(8))
    }

    /** Removes local rows whose files no longer exist or cannot be opened. */
    suspend fun pruneMissing(): ScanResult = withContext(Dispatchers.IO) {
        var removed = 0
        val notes = mutableListOf<String>()
        trackDao.getLocalOnce().forEach { entity ->
            val track = entity.toTrack()
            val uri = track.uri?.let(Uri::parse) ?: return@forEach
            val exists = when (uri.scheme) {
                "content" -> runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length >= 0 } ?: false
                }.getOrDefault(false)
                "file" -> File(uri.path.orEmpty()).exists()
                else -> true
            }
            if (!exists) {
                trackDao.delete(track.id)
                artworkStore.fileFor(track.id).delete()
                removed++
            }
        }
        ScanResult(removed = removed, errors = notes)
    }

    suspend fun pruneDownloadedMissing(downloadedFiles: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        trackDao.getLibraryOnce()
            .filter { it.origin == TrackOrigin.DOWNLOADED.name }
            .forEach { entity ->
                val path = entity.uri?.let(Uri::parse)?.path
                if (path == null || !File(path).exists()) {
                    trackDao.clearDownloadedUri(entity.id)
                    removed++
                }
            }
        removed
    }

    suspend fun stats(): LibraryStats = withContext(Dispatchers.IO) {
        val local = trackDao.getLibraryOnce().map { it.toTrack() }
        LibraryStats(
            trackCount = local.size,
            albumCount = local.map { it.albumKey }.distinct().size,
            artistCount = local.map { it.artistKey }.distinct().size,
            favoriteCount = favoriteDao.count(),
            playlistCount = database.playlistDao().count(),
            downloadCount = local.count { it.origin == TrackOrigin.DOWNLOADED },
            totalDurationMs = local.sumOf { it.durationMs },
            totalPlays = local.sumOf { it.playCount },
            listeningMs = historyDao.listeningMs(),
            offlineBytes = local.filter { it.origin == TrackOrigin.DOWNLOADED }.sumOf { it.sizeBytes },
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { historyDao.clear() }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    fun mimeOf(uri: Uri): String? = context.contentResolver.getType(uri)

    fun takeVideoPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun signature(track: Track): String =
        "${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}|${track.album.trim().lowercase()}|${track.durationMs / 1000}|${track.sizeBytes}"

    private fun normalizeFolder(raw: String?): String {
        if (raw.isNullOrBlank()) return "Music"
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.contains('/')) {
            val parent = trimmed.substringBeforeLast('/')
            return parent.substringAfterLast('/').ifBlank { "Music" }
        }
        return trimmed.ifBlank { "Music" }
    }

    private fun folderName(treeUri: String): String =
        runCatching { Uri.decode(treeUri.substringAfterLast('/')) }.getOrDefault("Music folder")

    private fun looksLikeAudio(name: String, mime: String?): Boolean {
        if (mime?.startsWith("audio/") == true) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "aiff", "alac", "mka")
    }

    private fun toAlbum(row: AlbumRow): AlbumSummary = AlbumSummary(
        key = row.albumKey,
        title = row.title,
        artist = row.artist,
        artworkUri = row.artworkUri,
        trackCount = row.trackCount,
        durationMs = row.durationMs,
        sourceId = row.sourceId,
        year = row.year,
    )

    private fun toArtist(row: ArtistRow): ArtistSummary = ArtistSummary(
        key = row.artistKey,
        name = row.name,
        artworkUri = row.artworkUri,
        trackCount = row.trackCount,
        albumCount = row.albumCount,
    )

    private fun toFolder(row: FolderRow): FolderSummary = FolderSummary(
        key = row.folder,
        name = row.folder.substringAfterLast('/'),
        trackCount = row.trackCount,
    )

    private fun toGenre(row: GenreRow): FolderSummary = FolderSummary(
        key = row.genre,
        name = row.genre,
        trackCount = row.trackCount,
    )
}
