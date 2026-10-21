package dev.tether.core.data

import android.content.Context
import android.net.Uri
import dev.tether.core.database.PlaylistEntity
import dev.tether.core.database.TetherDatabase
import dev.tether.core.database.toPlaylist
import dev.tether.core.database.toTrack
import dev.tether.core.model.Playlist
import dev.tether.core.model.PlaylistDetail
import dev.tether.core.model.PlaylistEntry
import dev.tether.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class PlaylistImportResult(
    val playlistId: Long?,
    val added: Int,
    val missing: Int,
    val message: String,
)

class PlaylistRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val library: LibraryRepository,
) {
    private val playlistDao = database.playlistDao()
    private val trackDao = database.trackDao()

    val playlists: Flow<List<Playlist>> = playlistDao.observeRows().map { rows -> rows.map { it.toPlaylist() } }

    fun observePlaylist(id: Long): Flow<PlaylistDetail?> =
        combine(playlistDao.observeRow(id), playlistDao.observeEntries(id)) { row, entries ->
            if (row == null) {
                null
            } else {
                val tracks = trackDao.getByIds(entries.map { it.trackId }).associateBy { it.id }
                PlaylistDetail(
                    playlist = row.toPlaylist(),
                    entries = entries.mapNotNull { entry ->
                        tracks[entry.trackId]?.let { entity -> PlaylistEntry(entity.toTrack(), entry.position, entry.addedAt) }
                    },
                )
            }
        }

    suspend fun create(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        playlistDao.insert(
            PlaylistEntity(
                name = name.trim().ifBlank { "New playlist" },
                description = description.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        playlistDao.rename(id, name.trim().ifBlank { "Untitled playlist" }, System.currentTimeMillis())
    }

    suspend fun updateDescription(id: Long, description: String) = withContext(Dispatchers.IO) {
        playlistDao.updateDescription(id, description.trim(), System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { playlistDao.delete(id) }

    suspend fun addTrack(playlistId: Long, track: Track): Boolean = withContext(Dispatchers.IO) {
        library.ensureTrackRow(track)
        playlistDao.addEntry(playlistId, track.id, System.currentTimeMillis())
    }

    suspend fun removeTrack(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeEntry(playlistId, trackId)
        val remaining = playlistDao.getEntries(playlistId).map { it.trackId }
        playlistDao.reorder(playlistId, remaining, System.currentTimeMillis())
    }

    suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>) = withContext(Dispatchers.IO) {
        playlistDao.reorder(playlistId, orderedTrackIds, System.currentTimeMillis())
    }

    suspend fun setTracks(playlistId: Long, tracks: List<Track>) = withContext(Dispatchers.IO) {
        tracks.forEach { library.ensureTrackRow(it) }
        playlistDao.replaceEntries(playlistId, tracks.map { it.id }, System.currentTimeMillis())
    }

    suspend fun saveQueueAsPlaylist(name: String, tracks: List<Track>): Long = withContext(Dispatchers.IO) {
        val id = create(name)
        setTracks(id, tracks)
        id
    }

    suspend fun ensurePlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: create(name)
    }

    suspend fun exportM3u(playlistId: Long, uri: Uri): Int = withContext(Dispatchers.IO) {
        val entries = playlistDao.getEntries(playlistId)
        val tracks = trackDao.getByIds(entries.map { it.trackId }).associateBy { it.id }
        val lines = buildString {
            append("#EXTM3U\n")
            for (entry in entries) {
                val track = tracks[entry.trackId]?.toTrack() ?: continue
                append("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}\n")
                append(track.uri.orEmpty()).append('\n')
            }
        }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(lines) }
            ?: error("Could not open the selected file for writing.")
        entries.size
    }

    suspend fun importM3u(uri: Uri, fallbackName: String? = null): PlaylistImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader: BufferedReader ->
            buildString {
                var count = 0
                while (count < 4_000_000) {
                    val line = reader.readLine() ?: break
                    append(line).append('\n')
                    count += line.length + 1
                }
            }
        } ?: return@withContext PlaylistImportResult(null, 0, 0, "Could not read the playlist file.")
        val entries = M3uParser.parse(text)
        if (entries.isEmpty()) {
            return@withContext PlaylistImportResult(null, 0, 0, "No playable entries were found in this playlist.")
        }
        val name = fallbackName
            ?: library.displayName(uri)?.substringBeforeLast('.')
            ?: "Imported playlist"
        val playlistId = create(name)
        var added = 0
        var missing = 0
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            var track = entry.uri?.let { trackDao.getByUri(it)?.toTrack() }
            if (track == null && entry.title != null) {
                val query = entry.title
                track = library.searchLocal(query, 10).firstOrNull { candidate ->
                    candidate.title.equals(entry.title, ignoreCase = true) &&
                        (entry.artist == null || candidate.artist.equals(entry.artist, ignoreCase = true))
                }
            }
            if (track == null) {
                missing++
            } else if (seen.add(track.id)) {
                if (addTrack(playlistId, track)) added++
            }
        }
        if (added == 0) {
            delete(playlistId)
            PlaylistImportResult(null, 0, missing, "No tracks matched your library. Nothing was imported.")
        } else {
            val note = if (missing > 0) " · $missing missing" else ""
            PlaylistImportResult(playlistId, added, missing, "Imported $added track${if (added == 1) "" else "s"}$note.")
        }
    }
}

data class M3uEntry(
    val uri: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
)

/**
 * Small, forgiving M3U/M3U8 parser. It never fetches a remote URL; the caller
 * matches entries against the local library.
 */
object M3uParser {
    fun parse(text: String): List<M3uEntry> {
        val result = mutableListOf<M3uEntry>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDuration = 0L
        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    val duration = body.substringBefore(',').trim().toLongOrNull() ?: 0L
                    pendingDuration = duration * 1000
                    val label = body.substringAfter(',', "").trim()
                    if (label.contains(" - ")) {
                        pendingArtist = label.substringBefore(" - ").trim().ifBlank { null }
                        pendingTitle = label.substringAfter(" - ").trim().ifBlank { null }
                    } else if (label.isNotBlank()) {
                        pendingTitle = label
                        pendingArtist = null
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val uri = line.takeIf { it.contains("://") || it.startsWith("/") || it.matches(Regex("^[A-Za-z]:[\\\\/].*")) }
                    if (uri != null) {
                        result += M3uEntry(uri, pendingTitle, pendingArtist, pendingDuration)
                    }
                    pendingTitle = null
                    pendingArtist = null
                    pendingDuration = 0L
                }
            }
        }
        return result
    }
}
