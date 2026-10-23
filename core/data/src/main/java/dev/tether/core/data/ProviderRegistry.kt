package dev.tether.core.data

import android.content.Context
import dev.tether.core.lyrics.LrclibClient
import dev.tether.core.model.AppSettings
import dev.tether.core.model.DownloadPermission
import dev.tether.core.model.MusicProvider
import dev.tether.core.model.ProviderCapability
import dev.tether.core.model.ProviderDescriptor
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderSearchResult
import dev.tether.core.model.ProviderSection
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.Track
import dev.tether.core.model.toProviderTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ProviderRegistry(
    private val context: Context,
    private val lyricsClient: LrclibClient = LrclibClient(),
) {
    private val youTube = YouTubeProvider()

    val musicProviders: List<MusicProvider> = listOf(youTube)
    val lyricsProviders = listOf(lyricsClient)

    val descriptors: List<ProviderDescriptor>
        get() = musicProviders.map { it.descriptor } + lyricsProviders.map { it.descriptor }

    val localDescriptor = ProviderDescriptor(
        id = "local",
        name = "On this device",
        description = "Your audio files, folders and downloads. Always available offline.",
        capabilities = setOf(
            ProviderCapability.DISCOVERY,
            ProviderCapability.STREAMING,
            ProviderCapability.DOWNLOADS,
        ),
        enabledByDefault = true,
    )

    fun provider(id: String): MusicProvider? = musicProviders.firstOrNull { it.descriptor.id == id }

    fun enabledMusicProviders(settings: AppSettings): List<MusicProvider> =
        if (settings.onlineDiscoveryEnabled) musicProviders else emptyList()

    suspend fun search(query: String, settings: AppSettings): ProviderSearchResult = coroutineScope {
        if (!settings.onlineDiscoveryEnabled) {
            return@coroutineScope ProviderSearchResult(
                note = "Online discovery is turned off in Settings. Local search still works.",
            )
        }
        if (!NetworkStatus.isOnline(context)) {
            return@coroutineScope ProviderSearchResult(note = "You are offline. Showing local results only.")
        }
        val results = enabledMusicProviders(settings).map { provider ->
            async {
                runCatching { provider.descriptor to provider.search(query, 20) }
            }
        }.map { it.await() }
        val successful = results.mapNotNull { it.getOrNull() }
        if (successful.isEmpty()) {
            val message = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            return@coroutineScope ProviderSearchResult(
                note = "Online providers could not be reached${message?.let { ": $it" } ?: "."}",
            )
        }
        ProviderSearchResult(
            releases = successful.flatMap { it.second.releases },
            tracks = successful.flatMap { it.second.tracks },
            note = successful.mapNotNull { it.second.note }.firstOrNull(),
        )
    }

    suspend fun browse(settings: AppSettings): List<ProviderSection> = coroutineScope {
        if (!settings.onlineDiscoveryEnabled || !NetworkStatus.isOnline(context)) return@coroutineScope emptyList()
        enabledMusicProviders(settings).map { provider ->
            async { runCatching { provider.browse() }.getOrDefault(emptyList()) }
        }.flatMap { it.await() }
    }

    suspend fun getRelease(providerId: String, releaseId: String): ProviderRelease? =
        provider(providerId)?.getRelease(releaseId)

    suspend fun getTracks(providerId: String, releaseId: String): List<ProviderTrack> =
        provider(providerId)?.getTracks(releaseId).orEmpty()

    fun downloadPermission(track: ProviderTrack): DownloadPermission {
        val provider = provider(track.sourceId) ?: return DownloadPermission(
            false,
            "This provider is not available. Downloads are disabled.",
        )
        return provider.downloadPermission(track)
    }

    fun downloadPermission(track: Track): DownloadPermission {
        if (track.isOfflinePlayable) {
            return DownloadPermission(true, "This file is already available on your device.")
        }
        // Provider tracks follow the provider's own decision. Only sources with no registered
        // provider fall back to inspecting a declared licence.
        provider(track.sourceId)?.let { return it.downloadPermission(track.toProviderTrack()) }
        val license = track.license
        if (license.isNullOrBlank()) {
            return DownloadPermission(false, "The source did not declare a license that permits downloading.")
        }
        val permitted = license.contains("creativecommons.org", ignoreCase = true) ||
            license.contains("publicdomain", ignoreCase = true) ||
            license.contains("sampling+", ignoreCase = true)
        return if (permitted) {
            DownloadPermission(
                allowed = true,
                reason = "The source declares $license. Attribution must be kept with the file; noncommercial licenses are for personal listening only.",
                license = license,
            )
        } else {
            DownloadPermission(false, "The declared license ($license) does not explicitly permit downloads.")
        }
    }

    /**
     * Returns a copy of [track] carrying a fresh, directly playable URL. Signed CDN URLs expire,
     * so this is called right before playback and before a download starts. Tracks that are
     * already local, or whose URL the provider cannot refresh, are returned unchanged.
     */
    suspend fun resolveStream(track: Track): Track {
        if (track.isOfflinePlayable) return track
        val provider = provider(track.sourceId) ?: return track
        val needsRefresh = track.remoteUri.isNullOrBlank() || track.sourceId == YouTubeProvider.SOURCE_ID
        if (!needsRefresh) return track
        val resolved = runCatching { provider.resolveStream(track.toProviderTrack()) }.getOrNull()
            ?: return track
        if (resolved.uri.isBlank()) return track
        return track.copy(
            uri = resolved.uri,
            remoteUri = resolved.uri,
            mimeType = resolved.mimeType ?: track.mimeType,
        )
    }

    /** Resolves a muxed video clip for providers that expose one (used by the TV Video mode). */
    suspend fun resolveVideoStream(track: Track): String? {
        val provider = provider(track.sourceId) ?: return null
        return runCatching { provider.resolveVideo(track.toProviderTrack())?.uri }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun supportsVideo(track: Track): Boolean =
        provider(track.sourceId)?.descriptor?.supports(ProviderCapability.VIDEO) == true

    fun providerSummary(): String = musicProviders.joinToString(", ") { it.descriptor.name } + ", LRCLIB"
}
