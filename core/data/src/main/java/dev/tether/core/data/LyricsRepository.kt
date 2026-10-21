package dev.tether.core.data

import android.content.Context
import android.net.Uri
import dev.tether.core.database.LyricsEntity
import dev.tether.core.database.TetherDatabase
import dev.tether.core.lyrics.EmbeddedLyricsReader
import dev.tether.core.lyrics.LrcParser
import dev.tether.core.lyrics.LrclibClient
import dev.tether.core.model.LyricsDocument
import dev.tether.core.model.LyricsLoadState
import dev.tether.core.model.LyricsQuery
import dev.tether.core.model.LyricsViewState
import dev.tether.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

class LyricsRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val lrclib: LrclibClient = LrclibClient(),
) {
    private val dao = database.lyricsDao()

    suspend fun cached(trackId: String): LyricsDocument? = withContext(Dispatchers.IO) {
        dao.get(trackId)?.toDocument()
    }

    suspend fun load(
        track: Track,
        force: Boolean = false,
        allowEmbedded: Boolean = true,
    ): LyricsViewState = withContext(Dispatchers.IO) {
        if (!force) {
            cached(track.id)?.let { return@withContext LyricsViewState(LyricsLoadState.AVAILABLE, it) }
        }

        if (allowEmbedded && track.origin.isOnDevice) {
            val embedded = readEmbedded(track)
            if (embedded != null) {
                val parsed = LrcParser.parse(embedded)
                if (parsed.hasContent) {
                    val doc = save(
                        track = track,
                        raw = embedded,
                        parsedSynced = parsed.synced,
                        providerId = "embedded",
                        sourceLabel = "Embedded lyrics",
                        attribution = "Lyrics embedded in the local audio file",
                        local = true,
                    )
                    return@withContext LyricsViewState(LyricsLoadState.AVAILABLE, doc)
                }
            }
        }

        val current = settings.settings.first()
        if (!current.lrclibEnabled) {
            return@withContext LyricsViewState(
                state = LyricsLoadState.NOT_FOUND,
                message = "Online lyrics lookup is turned off. You can import an .lrc or .txt file.",
            )
        }
        if (!NetworkStatus.isOnline(context)) {
            return@withContext LyricsViewState(
                state = LyricsLoadState.OFFLINE,
                message = "You're offline and no lyrics are cached for this track.",
            )
        }
        try {
            val candidate = lrclib.findLyrics(
                LyricsQuery(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                ),
            )
            if (candidate == null) {
                return@withContext LyricsViewState(
                    state = LyricsLoadState.NOT_FOUND,
                    message = "LRCLIB has no lyrics for this track. You can import your own .lrc file.",
                )
            }
            val parsed = LrcParser.parse(candidate.raw)
            if (!parsed.hasContent) {
                return@withContext LyricsViewState(
                    state = LyricsLoadState.NOT_FOUND,
                    message = "The lyrics returned for this track were empty.",
                )
            }
            val doc = save(
                track = track,
                raw = candidate.raw,
                parsedSynced = parsed.synced,
                providerId = candidate.providerId,
                sourceLabel = candidate.sourceLabel,
                attribution = candidate.attribution,
                externalUrl = candidate.externalUrl,
                local = false,
            )
            LyricsViewState(LyricsLoadState.AVAILABLE, doc)
        } catch (error: IOException) {
            LyricsViewState(
                state = if (NetworkStatus.isOnline(context)) LyricsLoadState.ERROR else LyricsLoadState.OFFLINE,
                message = if (NetworkStatus.isOnline(context)) {
                    "Lyrics lookup failed: ${error.message ?: "network error"}"
                } else {
                    "You're offline and no lyrics are cached for this track."
                },
            )
        } catch (error: Exception) {
            LyricsViewState(
                state = LyricsLoadState.ERROR,
                message = "Lyrics lookup failed: ${error.message ?: "unexpected error"}",
            )
        }
    }

    suspend fun importFile(track: Track, uri: Uri): LyricsViewState = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                    .let { if (it.size > 2 * 1024 * 1024) it.copyOf(2 * 1024 * 1024) else it }
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            return@withContext LyricsViewState(
                state = LyricsLoadState.ERROR,
                message = "The selected file could not be read as text.",
            )
        }
        val parsed = LrcParser.parse(raw)
        if (!parsed.hasContent) {
            return@withContext LyricsViewState(
                state = LyricsLoadState.ERROR,
                message = "No lyric lines were found in this file.",
            )
        }
        val name = library.displayName(uri) ?: "imported lyrics"
        val doc = save(
            track = track,
            raw = raw,
            parsedSynced = parsed.synced,
            providerId = "imported",
            sourceLabel = "Imported file",
            attribution = "Imported from $name",
            local = true,
        )
        LyricsViewState(LyricsLoadState.AVAILABLE, doc)
    }

    suspend fun importText(track: Track, text: String, name: String = "pasted lyrics"): LyricsViewState =
        withContext(Dispatchers.IO) {
            val parsed = LrcParser.parse(text)
            if (!parsed.hasContent) {
                return@withContext LyricsViewState(
                    state = LyricsLoadState.ERROR,
                    message = "No lyric lines were found in the text.",
                )
            }
            val doc = save(
                track = track,
                raw = text,
                parsedSynced = parsed.synced,
                providerId = "imported",
                sourceLabel = "Imported file",
                attribution = "Imported from $name",
                local = true,
            )
            LyricsViewState(LyricsLoadState.AVAILABLE, doc)
        }

    suspend fun deleteCache(trackId: String) = withContext(Dispatchers.IO) { dao.delete(trackId) }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun cacheCount(): Int = withContext(Dispatchers.IO) { dao.count() }

    private fun readEmbedded(track: Track): String? {
        val uri = track.uri?.let(Uri::parse) ?: return null
        if (uri.scheme != "content" && uri.scheme != "file") return null
        val name = library.displayName(uri)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                EmbeddedLyricsReader.read(stream, track.mimeType, name ?: track.title)
            }
        }.getOrNull()
    }

    private suspend fun save(
        track: Track,
        raw: String,
        parsedSynced: Boolean,
        providerId: String,
        sourceLabel: String,
        attribution: String,
        externalUrl: String? = null,
        local: Boolean,
    ): LyricsDocument {
        // Lyrics have a foreign key to tracks. Keep catalog tracks that the user
        // explicitly asks lyrics for, so the association is never lost.
        library.ensureTrackRow(track)
        val entity = LyricsEntity(
            trackId = track.id,
            raw = raw,
            synced = parsedSynced,
            providerId = providerId,
            sourceLabel = sourceLabel,
            attribution = attribution,
            externalUrl = externalUrl,
            local = local,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toDocument()
    }

    private fun LyricsEntity.toDocument(): LyricsDocument {
        val parsed = LrcParser.parse(raw)
        return LyricsDocument(
            trackId = trackId,
            lines = parsed.lines,
            raw = raw,
            synced = parsed.synced && synced,
            sourceLabel = sourceLabel,
            providerId = providerId,
            attribution = attribution,
            updatedAt = updatedAt,
            externalUrl = externalUrl,
            local = local,
        )
    }
}
