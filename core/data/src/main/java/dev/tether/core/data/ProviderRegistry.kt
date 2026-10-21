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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ProviderRegistry(
    private val context: Context,
    private val lyricsClient: LrclibClient = LrclibClient(),
) {
    private val ccmixter = CcmixterProvider()

    val musicProviders: List<MusicProvider> = listOf(ccmixter)
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
        if (!track.isOfflinePlayable) {
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
        return DownloadPermission(true, "This file is already available on your device.")
    }

    fun providerSummary(): String = musicProviders.joinToString(", ") { it.descriptor.name } + ", LRCLIB"
}
