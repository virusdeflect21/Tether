package dev.tether.core.model

enum class ProviderCapability {
    DISCOVERY,
    STREAMING,
    VIDEO,
    DOWNLOADS,
    LYRICS,
}

data class ProviderDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: Set<ProviderCapability>,
    val enabledByDefault: Boolean = false,
) {
    fun supports(capability: ProviderCapability): Boolean = capability in capabilities
}

data class ProviderRelease(
    val id: String,
    val providerId: String,
    val title: String,
    val creator: String,
    val description: String = "",
    val artworkUri: String? = null,
    val year: Int? = null,
    val license: String? = null,
    val trackCount: Int? = null,
    val isCollection: Boolean = true,
)

/**
 * A provider track is deliberately separate from [Track]: it is catalog data and
 * does not claim to be in the user's library until the user plays, favorites or
 * downloads it.
 */
data class ProviderTrack(
    val id: String,
    val providerItemId: String,
    val title: String,
    val artist: String,
    val album: String,
    val sourceId: String = ArchiveSource.ID,
    val durationMs: Long = 0L,
    val streamUri: String? = null,
    val artworkUri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val license: String? = null,
    val videoUri: String? = null,
    val sourceLabel: String = ArchiveSource.LABEL,
) {
    fun toTrack(now: Long = System.currentTimeMillis()): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album.ifBlank { title },
        albumArtist = artist,
        durationMs = durationMs,
        uri = streamUri,
        remoteUri = streamUri,
        artworkUri = artworkUri,
        origin = TrackOrigin.REMOTE,
        sourceId = sourceId,
        sourceLabel = sourceLabel,
        providerItemId = providerItemId,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        year = year,
        trackNumber = trackNumber,
        license = license,
        videoUri = videoUri,
        addedAt = now,
    )
}

/**
 * Reconstructs the provider view of a persisted [Track] so a provider can re-resolve a
 * fresh stream. Only meaningful for [TrackOrigin.REMOTE] provider tracks.
 */
fun Track.toProviderTrack(): ProviderTrack = ProviderTrack(
    id = id,
    providerItemId = providerItemId ?: remoteUri ?: "",
    title = title,
    artist = artist,
    album = album,
    sourceId = sourceId,
    durationMs = durationMs,
    streamUri = remoteUri,
    artworkUri = artworkUri,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    trackNumber = trackNumber,
    year = year,
    license = license,
    videoUri = videoUri,
    sourceLabel = sourceLabel,
)

data class ProviderSearchResult(
    val releases: List<ProviderRelease> = emptyList(),
    val tracks: List<ProviderTrack> = emptyList(),
    val note: String? = null,
)

data class ProviderSection(
    val title: String,
    val subtitle: String? = null,
    val releases: List<ProviderRelease> = emptyList(),
)

data class DownloadPermission(
    val allowed: Boolean,
    val reason: String,
    val license: String? = null,
)

/** A provider that exposes a remote music catalog. */
interface MusicProvider {
    val descriptor: ProviderDescriptor

    suspend fun search(query: String, limit: Int = 20): ProviderSearchResult
    suspend fun browse(): List<ProviderSection> = emptyList()
    suspend fun getRelease(releaseId: String): ProviderRelease?
    suspend fun getTracks(releaseId: String): List<ProviderTrack>

    /** Some providers only permit downloading specific items. Never assume. */
    fun downloadPermission(track: ProviderTrack): DownloadPermission

    /**
     * Resolve a fresh, directly playable route. Providers that hand out signed CDN URLs
     * must be re-resolved at play and download time because those URLs expire.
     */
    suspend fun resolveStream(track: ProviderTrack): ResolvedStream? =
        track.streamUri?.takeIf { it.isNotBlank() }?.let { ResolvedStream(it, track.mimeType) }

    /** Resolve a muxed video clip for a track when the provider has one. Null when unsupported. */
    suspend fun resolveVideo(track: ProviderTrack): ResolvedStream? = null
}

/**
 * A directly playable route to a provider item. [uri] may be a signed, expiring CDN URL,
 * so treat it as short-lived and re-resolve rather than persisting it forever.
 */
data class ResolvedStream(
    val uri: String,
    val mimeType: String? = null,
)

interface LyricsProvider {
    val descriptor: ProviderDescriptor
    suspend fun findLyrics(query: LyricsQuery): LyricsCandidate?
}

data class LyricsQuery(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
)

data class LyricsCandidate(
    val providerId: String,
    val sourceLabel: String,
    val synced: Boolean,
    val raw: String,
    val attribution: String,
    val externalUrl: String? = null,
)
