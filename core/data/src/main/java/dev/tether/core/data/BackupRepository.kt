package dev.tether.core.data

import android.content.Context
import dev.tether.core.database.TetherDatabase
import dev.tether.core.lyrics.LrclibClient
import dev.tether.core.model.BackupFavorite
import dev.tether.core.model.BackupPayload
import dev.tether.core.model.BackupPlaylist
import dev.tether.core.model.BackupTrackRef
import dev.tether.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RestoreResult(
    val playlists: Int = 0,
    val playlistTracks: Int = 0,
    val favorites: Int = 0,
    val missingTracks: Int = 0,
    val settingsRestored: Boolean = false,
    val message: String = "",
)

class BackupRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
) {
    suspend fun exportTo(uri: android.net.Uri): BackupPayload = withContext(Dispatchers.IO) {
        val payload = buildPayload()
        val json = toJson(payload)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(json)
        } ?: error("Could not open the backup destination for writing.")
        payload
    }

    suspend fun importFrom(uri: android.net.Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val buffer = CharArray(8192)
            val builder = StringBuilder()
            var total = 0
            while (total < 8_000_000) {
                val count = reader.read(buffer)
                if (count <= 0) break
                builder.append(buffer, 0, count)
                total += count
            }
            builder.toString()
        } ?: return@withContext RestoreResult(message = "The selected backup could not be read.")
        runCatching { fromJson(text) }.fold(
            onSuccess = { payload -> restore(payload) },
            onFailure = { error -> RestoreResult(message = "This is not a valid Tether backup: ${error.message}") },
        )
    }

    private suspend fun buildPayload(): BackupPayload {
        val playlistRows = database.playlistDao().getAll()
        val backupPlaylists = playlistRows.map { row ->
            val detail = playlists.observePlaylist(row.id).first()
            BackupPlaylist(
                name = row.name,
                description = row.description,
                tracks = detail?.entries.orEmpty().map { entry ->
                    val track = entry.track
                    BackupTrackRef(track.title, track.artist, track.album, track.durationMs, track.uri)
                },
            )
        }
        val favorites = library.favoriteTracks.first().map {
            BackupFavorite(it.title, it.artist, it.album, it.durationMs, it.uri)
        }
        return BackupPayload(
            exportedAt = System.currentTimeMillis(),
            playlists = backupPlaylists,
            favorites = favorites,
            settings = settings.snapshot(),
        )
    }

    private suspend fun restore(payload: BackupPayload): RestoreResult {
        if (payload.settings.isNotEmpty()) settings.restore(payload.settings)
        var playlistCount = 0
        var trackCount = 0
        var favorites = 0
        var missing = 0
        val existing = library.allLibraryTracks()
        fun match(ref: BackupTrackRef): Track? {
            if (!ref.uri.isNullOrBlank()) {
                existing.firstOrNull { it.uri == ref.uri }?.let { return it }
            }
            return existing.firstOrNull {
                it.title.equals(ref.title, ignoreCase = true) &&
                    it.artist.equals(ref.artist, ignoreCase = true) &&
                    (ref.durationMs <= 0 || kotlin.math.abs(it.durationMs - ref.durationMs) < 3000)
            }
        }
        payload.playlists.forEach { backup ->
            val playlistId = playlists.create(backup.name, backup.description)
            playlistCount++
            val added = mutableListOf<Track>()
            backup.tracks.forEach { ref ->
                val track = match(ref)
                if (track == null) missing++ else if (added.none { it.id == track.id }) added += track
            }
            added.forEach { if (playlists.addTrack(playlistId, it)) trackCount++ }
        }
        payload.favorites.forEach { ref ->
            val track = library.allLibraryTracks().firstOrNull {
                it.title.equals(ref.title, ignoreCase = true) && it.artist.equals(ref.artist, ignoreCase = true)
            }
            if (track == null) {
                missing++
            } else if (library.setFavorite(track, true)) {
                favorites++
            }
        }
        val message = buildString {
            append("Restored $playlistCount playlist${if (playlistCount == 1) "" else "s"} and $favorites favorite${if (favorites == 1) "" else "s"}.")
            if (missing > 0) append(" $missing item${if (missing == 1) "" else "s"} were not found in this library.")
        }
        return RestoreResult(playlistCount, trackCount, favorites, missing, payload.settings.isNotEmpty(), message)
    }

    private fun toJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("format", "tether-backup")
        root.put("version", payload.version)
        root.put("exportedAt", payload.exportedAt)
        root.put("playlists", JSONArray().apply {
            payload.playlists.forEach { playlist ->
                put(JSONObject().apply {
                    put("name", playlist.name)
                    put("description", playlist.description)
                    put("tracks", JSONArray().apply {
                        playlist.tracks.forEach { track ->
                            put(JSONObject().apply {
                                put("title", track.title)
                                put("artist", track.artist)
                                put("album", track.album)
                                put("durationMs", track.durationMs)
                                track.uri?.let { put("uri", it) }
                            })
                        }
                    })
                })
            }
        })
        root.put("favorites", JSONArray().apply {
            payload.favorites.forEach { favorite ->
                put(JSONObject().apply {
                    put("title", favorite.title)
                    put("artist", favorite.artist)
                    put("album", favorite.album)
                    put("durationMs", favorite.durationMs)
                    favorite.uri?.let { put("uri", it) }
                })
            }
        })
        root.put("settings", JSONObject().apply {
            payload.settings.forEach { (key, value) -> put(key, value) }
        })
        return root.toString(2)
    }

    private fun fromJson(text: String): BackupPayload {
        val root = JSONObject(text)
        require(root.optString("format") == "tether-backup") { "missing Tether backup marker" }
        val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()
        val favoritesArray = root.optJSONArray("favorites") ?: JSONArray()
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val settingsMap = buildMap {
            settingsJson.keys().forEach { key -> settingsJson.optString(key).takeIf { it.isNotEmpty() }?.let { put(key, it) } }
        }
        return BackupPayload(
            version = root.optInt("version", 1),
            exportedAt = root.optLong("exportedAt", 0L),
            playlists = buildList {
                for (index in 0 until playlistsArray.length()) {
                    val item = playlistsArray.optJSONObject(index) ?: continue
                    val trackArray = item.optJSONArray("tracks") ?: JSONArray()
                    add(
                        BackupPlaylist(
                            name = item.optString("name").ifBlank { "Imported playlist" },
                            description = item.optString("description"),
                            tracks = buildList {
                                for (trackIndex in 0 until trackArray.length()) {
                                    val ref = trackArray.optJSONObject(trackIndex) ?: continue
                                    add(
                                        BackupTrackRef(
                                            title = ref.optString("title"),
                                            artist = ref.optString("artist"),
                                            album = ref.optString("album"),
                                            durationMs = ref.optLong("durationMs", 0L),
                                            uri = ref.optString("uri").takeIf { it.isNotBlank() },
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            },
            favorites = buildList {
                for (index in 0 until favoritesArray.length()) {
                    val ref = favoritesArray.optJSONObject(index) ?: continue
                    add(
                        BackupFavorite(
                            title = ref.optString("title"),
                            artist = ref.optString("artist"),
                            album = ref.optString("album"),
                            durationMs = ref.optLong("durationMs", 0L),
                            uri = ref.optString("uri").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            },
            settings = settingsMap,
        )
    }
}
