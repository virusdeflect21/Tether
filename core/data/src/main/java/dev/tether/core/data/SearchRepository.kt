package dev.tether.core.data

import dev.tether.core.database.SearchHistoryEntity
import dev.tether.core.database.TetherDatabase
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.Playlist
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderSection
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.RecentSearch
import dev.tether.core.model.Track
import dev.tether.core.model.groupAlbums
import dev.tether.core.model.groupArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SearchResults(
    val query: String = "",
    val localTracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val remoteTracks: List<ProviderTrack> = emptyList(),
    val remoteReleases: List<ProviderRelease> = emptyList(),
    val providerNote: String? = null,
    val searchedOnline: Boolean = false,
) {
    val hasLocal: Boolean get() = localTracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()
    val hasRemote: Boolean get() = remoteTracks.isNotEmpty() || remoteReleases.isNotEmpty()
    val isEmpty: Boolean get() = !hasLocal && !hasRemote
}

class SearchRepository(
    private val database: TetherDatabase,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
) {
    private val searchDao = database.searchHistoryDao()

    val recentSearches: Flow<List<RecentSearch>> = searchDao.observeRecent().map { rows ->
        rows.map { RecentSearch(it.query, it.searchedAt) }
    }

    suspend fun searchLocal(query: String): SearchResults = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResults(query)
        val tracks = library.searchLocal(query, 60)
        val all = library.allLibraryTracks()
        val albums = groupAlbums(all.filter { albumMatches(it, query) })
        val artists = groupArtists(all.filter { artistMatches(it, query) })
        val matchingPlaylists = playlists.playlists.first()
            .filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        SearchResults(
            query = query,
            localTracks = tracks,
            albums = albums,
            artists = artists,
            playlists = matchingPlaylists,
        )
    }

    suspend fun searchOnline(query: String): SearchResults = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResults(query, searchedOnline = true)
        val currentSettings = settings.settings.first()
        val result = providers.search(query, currentSettings)
        if (currentSettings.searchHistoryEnabled && result.tracks.isNotEmpty()) {
            searchDao.insert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        }
        SearchResults(
            query = query,
            remoteTracks = result.tracks,
            remoteReleases = result.releases,
            providerNote = result.note,
            searchedOnline = true,
        )
    }

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val local = searchLocal(query)
        val remote = if (query.isNotBlank()) searchOnline(query) else SearchResults(query)
        local.copy(
            remoteTracks = remote.remoteTracks,
            remoteReleases = remote.remoteReleases,
            providerNote = remote.providerNote,
            searchedOnline = remote.searchedOnline,
        )
    }

    suspend fun discoverySections(): List<ProviderSection> = withContext(Dispatchers.IO) {
        providers.browse(settings.settings.first())
    }

    suspend fun resolveRelease(releaseId: String, providerId: String): List<ProviderTrack> = withContext(Dispatchers.IO) {
        providers.getTracks(providerId, releaseId)
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty() && settings.settings.first().searchHistoryEnabled) {
            searchDao.insert(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { searchDao.clear() }

    suspend fun removeSearch(query: String) = withContext(Dispatchers.IO) { searchDao.delete(query) }

    private fun albumMatches(track: Track, query: String): Boolean =
        track.album.contains(query, ignoreCase = true) || track.albumArtist.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true)

    private fun artistMatches(track: Track, query: String): Boolean =
        track.artist.contains(query, ignoreCase = true) || track.albumArtist.contains(query, ignoreCase = true)
}
