package dev.tether.core.data

import dev.tether.core.model.DownloadPermission
import dev.tether.core.model.MusicProvider
import dev.tether.core.model.ProviderCapability
import dev.tether.core.model.ProviderDescriptor
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderSearchResult
import dev.tether.core.model.ProviderSection
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit

/**
 * YouTube search and streaming through NewPipeExtractor.
 *
 * NewPipeExtractor talks to YouTube's InnerTube endpoints and is the piece that keeps up with
 * player signatures and rolling client changes, so this class stays a thin adapter:
 * search / kiosk browse in, catalog [ProviderTrack]s out, and stream resolution on demand.
 *
 * Nothing is cached as playable: YouTube hands out signed `googlevideo` URLs that expire after
 * a few hours, so [resolveStream] / [resolveVideo] are re-run at play and download time.
 */
class YouTubeProvider(
    private val downloader: Downloader = NewPipeOkHttpDownloader(),
) : MusicProvider {

    override val descriptor = ProviderDescriptor(
        id = SOURCE_ID,
        name = "YouTube",
        description = "Search YouTube for music and video, stream audio, and keep a clip for the TV player.",
        capabilities = setOf(
            ProviderCapability.DISCOVERY,
            ProviderCapability.STREAMING,
            ProviderCapability.VIDEO,
            ProviderCapability.DOWNLOADS,
        ),
        enabledByDefault = true,
    )

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(downloader, Localization.DEFAULT, ContentCountry.DEFAULT)
                initialized = true
            }
        }
    }

    private val service
        get() = ServiceList.YouTube

    override suspend fun search(query: String, limit: Int): ProviderSearchResult = withContext(Dispatchers.IO) {
        ensureInit()
        val page = service.getSearchExtractor(query).let { extractor ->
            extractor.fetchPage()
            extractor.initialPage
        }
        val cap = limit.coerceIn(1, 40)
        val streams = page.items.filterIsInstance<StreamInfoItem>().take(cap)
        val playlists = page.items.filterIsInstance<PlaylistInfoItem>().take(MAX_PLAYLISTS)
        ProviderSearchResult(
            tracks = streams.mapNotNull { it.toProviderTrack() },
            releases = streams.mapNotNull { it.toRelease() } + playlists.map { it.toRelease() },
            note = if (streams.isEmpty() && playlists.isEmpty()) {
                "YouTube returned no results for this search."
            } else {
                null
            },
        )
    }

    override suspend fun browse(): List<ProviderSection> = coroutineScope {
        ensureInit()
        val kiosks = listOf(
            "Trending" to "Trending on YouTube",
            "Music" to "Music",
            "Gaming" to "Gaming",
        )
        kiosks.map { (id, title) ->
            async {
                runCatching {
                    val extractor = service.kioskList.getExtractorById(id, null)
                    extractor.fetchPage()
                    val items = extractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .take(SECTION_SIZE)
                    ProviderSection(
                        title = title,
                        subtitle = "From YouTube",
                        releases = items.mapNotNull { it.toRelease() },
                    )
                }.getOrNull()
            }
        }.mapNotNull { it.await() }.filter { it.releases.isNotEmpty() }
    }

    override suspend fun getRelease(releaseId: String): ProviderRelease? = withContext(Dispatchers.IO) {
        ensureInit()
        val url = releaseId.removePrefix(URL_PREFIX)
        runCatching {
            if (isPlaylist(url)) {
                service.getPlaylistExtractor(url).let { extractor ->
                    extractor.fetchPage()
                    extractor.toRelease()
                }
            } else {
                service.getStreamExtractor(url).let { extractor ->
                    extractor.fetchPage()
                    ProviderRelease(
                        id = releaseId,
                        providerId = SOURCE_ID,
                        title = extractor.name.orEmpty().ifBlank { "Untitled video" },
                        creator = extractor.uploaderName.orEmpty().ifBlank { "YouTube" },
                        description = extractor.description?.content.orEmpty().take(600),
                        artworkUri = bestThumbnail(extractor.thumbnails),
                        license = extractor.licence?.takeIf { it.isNotBlank() },
                        trackCount = 1,
                        isCollection = false,
                    )
                }
            }
        }.getOrNull()
    }

    override suspend fun getTracks(releaseId: String): List<ProviderTrack> = withContext(Dispatchers.IO) {
        ensureInit()
        val url = releaseId.removePrefix(URL_PREFIX)
        runCatching {
            if (isPlaylist(url)) {
                val extractor = service.getPlaylistExtractor(url)
                extractor.fetchPage()
                extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toProviderTrack() }
            } else {
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                listOfNotNull(extractor.toProviderTrack())
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Re-resolves the freshest progressive audio stream for a video. YouTube URLs are signed
     * and expire, so this is called at play and download time rather than being stored.
     */
    override suspend fun resolveStream(track: ProviderTrack): ResolvedStream? = withContext(Dispatchers.IO) {
        ensureInit()
        val url = track.providerItemId.takeIf { it.isNotBlank() } ?: track.videoUri ?: return@withContext null
        runCatching {
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            extractor.pickAudio()?.let { ResolvedStream(it.content, it.format?.mimeType) }
        }.getOrNull()
    }

    /** Returns the best muxed (audio + video) clip for the TV "Video" mode. */
    override suspend fun resolveVideo(track: ProviderTrack): ResolvedStream? = withContext(Dispatchers.IO) {
        ensureInit()
        val url = track.providerItemId.takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching {
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            extractor.videoStreams
                .filter { it.isUrl }
                .maxByOrNull { resolutionHeight(it) }
                ?.let { ResolvedStream(it.content, it.format?.mimeType) }
        }.getOrNull()
    }

    /**
     * Downloading is enabled for YouTube. There is no declared open licence to gate on, so the
     * permission is granted and the user is told exactly what they are saving.
     */
    override fun downloadPermission(track: ProviderTrack): DownloadPermission = DownloadPermission(
        allowed = true,
        reason = "YouTube audio is kept for offline listening. Check that you have the right to " +
            "download this content and keep it personal.",
        license = null,
    )

    // ---------------------------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------------------------

    private fun StreamInfoItem.toProviderTrack(): ProviderTrack? {
        val videoId = videoIdFrom(url) ?: return null
        return ProviderTrack(
            id = "$SOURCE_ID:$videoId",
            providerItemId = url,
            title = name.orEmpty().ifBlank { "Untitled" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube" },
            album = "YouTube",
            sourceId = SOURCE_ID,
            durationMs = duration.coerceAtLeast(0) * 1000L,
            streamUri = null,
            artworkUri = bestThumbnail(thumbnails),
            mimeType = null,
            license = null,
            videoUri = null,
            sourceLabel = SOURCE_LABEL,
        )
    }

    private fun StreamInfoItem.toRelease(): ProviderRelease? {
        val videoId = videoIdFrom(url) ?: return null
        return ProviderRelease(
            id = "$URL_PREFIX$url",
            providerId = SOURCE_ID,
            title = name.orEmpty().ifBlank { "Untitled" },
            creator = uploaderName.orEmpty().ifBlank { "YouTube" },
            description = shortDescription.orEmpty(),
            artworkUri = bestThumbnail(thumbnails),
            trackCount = 1,
            isCollection = false,
        )
    }

    private fun StreamExtractor.toProviderTrack(): ProviderTrack? {
        val best = pickAudio() ?: return null
        val videoId = videoIdFrom(url) ?: return null
        val seconds = @Suppress("DEPRECATION") runCatching { length }.getOrDefault(0L)
        return ProviderTrack(
            id = "$SOURCE_ID:$videoId",
            providerItemId = url,
            title = name.orEmpty().ifBlank { "Untitled" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube" },
            album = "YouTube",
            sourceId = SOURCE_ID,
            durationMs = seconds.coerceAtLeast(0) * 1000L,
            streamUri = best.content,
            artworkUri = bestThumbnail(thumbnails),
            mimeType = best.format?.mimeType,
            license = licence?.takeIf { it.isNotBlank() },
            videoUri = null,
            sourceLabel = SOURCE_LABEL,
        )
    }

    private fun StreamExtractor.toRelease(): ProviderRelease = ProviderRelease(
        id = "$URL_PREFIX$url",
        providerId = SOURCE_ID,
        title = name.orEmpty().ifBlank { "Untitled playlist" },
        creator = uploaderName.orEmpty().ifBlank { "YouTube" },
        description = description?.content.orEmpty().take(600),
        artworkUri = bestThumbnail(thumbnails),
        trackCount = null,
        isCollection = true,
    )

    private fun PlaylistExtractor.toRelease(): ProviderRelease = ProviderRelease(
        id = "$URL_PREFIX$url",
        providerId = SOURCE_ID,
        title = name.orEmpty().ifBlank { "Untitled playlist" },
        creator = uploaderName.orEmpty().ifBlank { "YouTube" },
        artworkUri = bestThumbnail(thumbnails),
        trackCount = streamCount.takeIf { it > 0 }?.toInt(),
        isCollection = true,
    )

    private fun PlaylistInfoItem.toRelease(): ProviderRelease = ProviderRelease(
        id = "$URL_PREFIX$url",
        providerId = SOURCE_ID,
        title = name.orEmpty().ifBlank { "Untitled playlist" },
        creator = uploaderName.orEmpty().ifBlank { "YouTube" },
        artworkUri = bestThumbnail(thumbnails),
        trackCount = streamCount.takeIf { it > 0 }?.toInt(),
        isCollection = true,
    )

    private fun StreamExtractor.pickAudio(): AudioStream? =
        audioStreams
            .filter { it.isUrl }
            .sortedWith(
                compareByDescending<AudioStream> { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()

    private fun bestThumbnail(images: List<Image>?): String? =
        images
            ?.filter { it.url.isNotBlank() }
            ?.maxByOrNull { it.height.coerceAtLeast(0) }
            ?.url

    private fun resolutionHeight(stream: VideoStream): Int =
        stream.resolution.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun isPlaylist(url: String): Boolean =
        url.contains("/playlist") || url.contains("list=")

    private fun videoIdFrom(url: String): String? {
        if (url.isBlank()) return null
        Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return url.substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    companion object {
        const val SOURCE_ID = "youtube"
        const val SOURCE_LABEL = "YouTube"

        /** Release ids are prefixed so [getTracks] knows whether to open a video or a playlist. */
        private const val URL_PREFIX = "url:"
        private const val MAX_PLAYLISTS = 4
        private const val SECTION_SIZE = 12

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

/**
 * Minimal OkHttp [Downloader] for NewPipeExtractor. It forwards the extractor's headers and body
 * untouched and detects the reCAPTCHA wall so extraction failures surface as a real error.
 */
internal class NewPipeOkHttpDownloader(
    private val client: OkHttpClient = YouTubeProvider.defaultClient(),
) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val builder = okhttp3.Request.Builder().url(request.url())
        when (request.httpMethod().uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> builder.method(request.httpMethod(), body)
        }
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        builder.header("User-Agent", USER_AGENT)

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 429 && text.contains("recaptcha", ignoreCase = true)) {
                throw ReCaptchaException("reCAPTCHA required by YouTube", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                text,
                request.url(),
            )
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }
}
