package dev.tether.core.lyrics

import dev.tether.core.model.LyricsCandidate
import dev.tether.core.model.LyricsProvider
import dev.tether.core.model.LyricsQuery
import dev.tether.core.model.ProviderCapability
import dev.tether.core.model.ProviderDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * LRCLIB is a free, public lyrics database with an open API:
 * https://lrclib.net/docs
 *
 * Results are cached by the app. Tether never generates lyrics; if the API has no
 * match, the UI says so.
 */
class LrclibClient(
    private val client: OkHttpClient = defaultClient(),
) : LyricsProvider {
    override val descriptor = ProviderDescriptor(
        id = "lrclib",
        name = "LRCLIB",
        description = "Community lyrics database with synced and plain lyrics.",
        capabilities = setOf(ProviderCapability.LYRICS),
        enabledByDefault = true,
    )

    override suspend fun findLyrics(query: LyricsQuery): LyricsCandidate? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", query.title)
            addQueryParameter("artist_name", query.artist)
            if (query.album.isNotBlank()) addQueryParameter("album_name", query.album)
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Tether/1.0 (open-source music player; https://github.com/tether-music)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("LRCLIB returned HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            val results = runCatching { JSONArray(body) }.getOrElse { return null }
            if (results.length() == 0) return null

            val candidates = buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
                    val raw = synced ?: plain ?: continue
                    val duration = item.optDouble("duration", 0.0).toLong() * 1000
                    val durationDelta = if (query.durationMs > 0 && duration > 0) {
                        kotlin.math.abs(duration - query.durationMs)
                    } else {
                        0L
                    }
                    add(
                        Scored(
                            candidate = LyricsCandidate(
                                providerId = descriptor.id,
                                sourceLabel = "LRCLIB",
                                synced = synced != null,
                                raw = raw,
                                attribution = "Lyrics provided by LRCLIB (lrclib.net)",
                                externalUrl = item.optLong("id", 0L).takeIf { it > 0 }?.let { "https://lrclib.net/lyrics/$it" },
                            ),
                            score = durationDelta + if (synced != null) 0 else 90_000,
                        ),
                    )
                }
            }
            return candidates.minByOrNull { it.score }?.candidate
        }
    }

    private data class Scored(val candidate: LyricsCandidate, val score: Long)

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
