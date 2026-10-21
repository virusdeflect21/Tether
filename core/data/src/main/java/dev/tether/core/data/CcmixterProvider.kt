package dev.tether.core.data

import android.text.Html
import dev.tether.core.model.ArchiveSource
import dev.tether.core.model.DownloadPermission
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.MusicProvider
import dev.tether.core.model.ProviderCapability
import dev.tether.core.model.ProviderDescriptor
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderSearchResult
import dev.tether.core.model.ProviderSection
import dev.tether.core.model.ProviderTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ccMixter hosts Creative Commons licensed music and publishes an unauthenticated
 * JSON API: https://ccmixter.org/api/query
 *
 * Only the primary mix file is exposed, not the individual stems. Downloads are
 * allowed only when the API declares a Creative Commons or public-domain license;
 * the license and attribution are shown before downloading.
 */
class CcmixterProvider(
    private val client: OkHttpClient = defaultClient(),
) : MusicProvider {
    override val descriptor = ProviderDescriptor(
        id = SOURCE_ID,
        name = "ccMixter",
        description = "Independent, Creative Commons licensed music with real full-length tracks.",
        capabilities = setOf(
            ProviderCapability.DISCOVERY,
            ProviderCapability.STREAMING,
            ProviderCapability.DOWNLOADS,
        ),
        enabledByDefault = true,
    )

    override suspend fun search(query: String, limit: Int): ProviderSearchResult {
        val uploads = queryUploads(
            mapOf(
                "search" to query,
                "limit" to limit.coerceIn(1, 40).toString(),
                "sort" to "rank",
            ),
        )
        val tracks = uploads.mapNotNull { it.primaryTrack() }
        val releases = uploads.mapNotNull { it.toRelease() }
        return ProviderSearchResult(
            releases = releases,
            tracks = tracks,
            note = if (tracks.isEmpty()) "ccMixter returned no tracks for this search." else null,
        )
    }

    override suspend fun browse(): List<ProviderSection> = coroutineScope {
        val sections = listOf(
            Triple("Instrumental focus", "instrumental", "instrumental"),
            Triple("Jazz & chill", "jazz", "jazz chill"),
            Triple("Electronic journeys", "electronic", "electronic ambient"),
        )
        sections.map { (title, tag, query) ->
            async {
                runCatching {
                    val uploads = queryUploads(
                        mapOf("tags" to tag, "limit" to "8", "sort" to "rank"),
                    )
                    ProviderSection(
                        title = title,
                        subtitle = "Creative Commons music from ccMixter",
                        releases = uploads.mapNotNull { it.toRelease() },
                    )
                }.getOrNull()
            }
        }.mapNotNull { it.await() }.filter { it.releases.isNotEmpty() }
    }

    override suspend fun getRelease(releaseId: String): ProviderRelease? {
        val rawId = releaseId.removePrefix("upload:")
        val upload = queryUploads(mapOf("ids" to rawId, "limit" to "1")).firstOrNull() ?: return null
        return upload.toRelease()
    }

    override suspend fun getTracks(releaseId: String): List<ProviderTrack> {
        val rawId = releaseId.removePrefix("upload:")
        return queryUploads(mapOf("ids" to rawId, "limit" to "1"))
            .mapNotNull { it.primaryTrack() }
    }

    override fun downloadPermission(track: ProviderTrack): DownloadPermission {
        val license = track.license
        if (license.isNullOrBlank()) {
            return DownloadPermission(false, "ccMixter did not declare a license for this track.")
        }
        val permitted = license.contains("creativecommons.org", ignoreCase = true) ||
            license.contains("publicdomain", ignoreCase = true) ||
            license.contains("sampling+", ignoreCase = true)
        return if (permitted) {
            DownloadPermission(
                allowed = true,
                reason = "The creator published this track under $license. Keep the attribution and license with the file. Noncommercial licenses limit use to personal, noncommercial listening.",
                license = license,
            )
        } else {
            DownloadPermission(false, "The declared license ($license) does not explicitly permit downloads.")
        }
    }

    private suspend fun queryUploads(params: Map<String, String>): List<JSONObject> {
        val url = "https://ccmixter.org/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("f", "json")
            addQueryParameter("dataview", "default")
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Tether/1.0 (open-source music player)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ccMixter returned HTTP ${response.code}")
            val body = response.body?.string() ?: return emptyList()
            val array = runCatching { JSONArray(body) }.getOrElse { emptyList<JSONObject>().let(::JSONArray) }
            return buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(it) }
                }
            }
        }
    }

    private fun JSONObject.toRelease(): ProviderRelease? {
        val id = optInt("upload_id", 0).takeIf { it > 0 } ?: return null
        val title = titleText(optString("upload_name"))
        if (title.isBlank()) return null
        val creator = titleText(optString("user_real_name")).ifBlank { titleText(optString("user_name")) }
        return ProviderRelease(
            id = "upload:$id",
            providerId = SOURCE_ID,
            title = title,
            creator = creator.ifBlank { "ccMixter artist" },
            description = description(optString("upload_description_plain")),
            artworkUri = null,
            year = yearFrom(optString("upload_date_format")),
            license = optString("license_url").takeIf { it.isNotBlank() },
            trackCount = 1,
            isCollection = false,
        )
    }

    private fun JSONObject.primaryTrack(): ProviderTrack? {
        val uploadId = optInt("upload_id", 0).takeIf { it > 0 } ?: return null
        val title = titleText(optString("upload_name")).ifBlank { return null }
        val creator = titleText(optString("user_real_name")).ifBlank { titleText(optString("user_name")) }
        val files = optJSONArray("files") ?: return null
        val file = chooseFile(files) ?: return null
        val format = file.optJSONObject("file_format_info")
        val mime = format?.optString("mime_type")?.takeIf { it.isNotBlank() }
        val stream = file.optString("download_url")
            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.replaceFirst("http://", "https://")
            ?: return null
        val duration = parseDuration(format?.optString("ps"))
        val license = optString("license_url").takeIf { it.isNotBlank() }
        return ProviderTrack(
            id = "$SOURCE_ID:$uploadId",
            providerItemId = uploadId.toString(),
            title = title,
            artist = creator.ifBlank { "ccMixter artist" },
            album = "ccMixter · Creative Commons",
            sourceId = SOURCE_ID,
            durationMs = duration,
            streamUri = stream,
            artworkUri = null,
            mimeType = mime,
            sizeBytes = file.optLong("file_rawsize", 0L),
            year = yearFrom(optString("upload_date_format")),
            license = license,
            sourceLabel = "ccMixter · ${optString("license_name").ifBlank { "Creative Commons" }}",
        )
    }

    private fun chooseFile(files: JSONArray): JSONObject? {
        var bestMp3: JSONObject? = null
        var bestFlac: JSONObject? = null
        for (index in 0 until files.length()) {
            val file = files.optJSONObject(index) ?: continue
            if (file.optInt("file_is_remote", 0) == 1) continue
            val format = file.optJSONObject("file_format_info") ?: continue
            val mediaType = format.optString("media-type")
            if (mediaType != "audio") continue
            val name = file.optString("file_name").lowercase()
            if (name.endsWith(".mp3")) {
                if (bestMp3 == null) bestMp3 = file
            } else if (name.endsWith(".flac") && bestFlac == null) {
                bestFlac = file
            }
        }
        return bestMp3 ?: bestFlac
    }

    private fun parseDuration(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val parts = value.split(':')
        return when (parts.size) {
            3 -> {
                (parts[0].toLongOrNull() ?: 0) * 3_600_000 +
                    (parts[1].toLongOrNull() ?: 0) * 60_000 +
                    (parts[2].toLongOrNull() ?: 0) * 1000
            }
            2 -> (parts[0].toLongOrNull() ?: 0) * 60_000 + (parts[1].toLongOrNull() ?: 0) * 1000
            else -> 0L
        }
    }

    private fun yearFrom(date: String?): Int? =
        Regex("(19|20)\\d{2}").find(date.orEmpty())?.value?.toIntOrNull()

    private fun titleText(value: String?): String =
        runCatching { Html.fromHtml(value.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString() }
            .getOrDefault(value.orEmpty())
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun description(value: String?): String = titleText(value).take(600)

    companion object {
        const val SOURCE_ID = "ccmixter"
        const val SOURCE_LABEL = "ccMixter"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
