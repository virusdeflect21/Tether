package dev.tether.mobile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tether.core.data.DiagnosticReport
import dev.tether.core.data.ScanResult
import dev.tether.core.data.Tether
import dev.tether.core.database.toTrack
import dev.tether.core.downloads.DownloadRepository
import dev.tether.core.downloads.Downloads
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.AppSettings
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.BackupPayload
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.FolderSummary
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.LibrarySort
import dev.tether.core.model.MediaFolder
import dev.tether.core.model.Playlist
import dev.tether.core.model.PlaylistDetail
import dev.tether.core.model.QueueAction
import dev.tether.core.model.RecentSearch
import dev.tether.core.model.StorageInfo
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.Track
import dev.tether.core.model.TvDisplayMode
import dev.tether.core.model.TvStartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile screen state and all user-triggered mutations. Playback state lives in
 * PlayerViewModel; this class owns the library, playlists, downloads and settings.
 */
class MobileViewModel(application: Application) : AndroidViewModel(application) {
    val graph = Tether.graph(application)
    private val downloadsRepository: DownloadRepository = Downloads.repository(application)

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val tracks = graph.library.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyPlayed = graph.library.recentlyPlayed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyAdded = graph.library.recentlyAdded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = graph.library.favoriteTracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = graph.library.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = graph.library.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = graph.library.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres = graph.library.genres.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaFolders = graph.library.mediaFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = graph.playlists.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteIds = graph.library.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloads = downloadsRepository.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentSearches = graph.search.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _stats = MutableStateFlow(LibraryStats())
    val stats: StateFlow<LibraryStats> = _stats.asStateFlow()

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private var pendingAutoScan = true

    init {
        refreshStats()
        refreshStorage()
        viewModelScope.launch {
            graph.settings.settings.first()
            _settingsLoaded.value = true
        }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _stats.value = graph.library.stats()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _storage.value = downloadsRepository.storageInfo()
        }
    }

    fun maybeAutoScan(hasPermission: Boolean) {
        if (!pendingAutoScan) return
        if (!settings.value.scanMediaStoreOnStartup) {
            pendingAutoScan = false
            return
        }
        if (!hasPermission) return
        pendingAutoScan = false
        scanMediaStore()
    }

    fun scanMediaStore() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.library.scanMediaStore() }
            _scanning.value = false
            publishScan(result)
            refreshStats()
            refreshStorage()
        }
    }

    fun addFolder(uri: Uri) {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.library.addFolder(uri.toString()) }
            _scanning.value = false
            publishScan(result)
            refreshStats()
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _scanning.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.library.importFiles(uris) }
            _scanning.value = false
            publishScan(result)
            refreshStats()
        }
    }

    fun rescanFolder(treeUri: String) {
        addFolder(Uri.parse(treeUri))
    }

    fun removeFolder(treeUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.library.removeFolder(treeUri)
            notify("Folder removed. Its tracks remain until you prune missing files.")
        }
    }

    fun pruneMissing() {
        _busy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.library.pruneMissing() }
            _busy.value = false
            notify(if (result.removed == 0) "No missing files found." else "Removed ${result.removed} missing local track(s).")
            refreshStats()
        }
    }

    private fun publishScan(result: ScanResult) {
        if (result.errors.isNotEmpty()) {
            notify(result.errors.first())
        } else {
            notify(result.summary)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val favorite = graph.library.toggleFavorite(track)
            refreshStats()
            notify(if (favorite) "Added to favorites" else "Removed from favorites")
        }
    }

    fun isFavorite(trackId: String): Boolean = favoriteIds.value.contains(trackId)

    fun playlistDetail(id: Long): Flow<PlaylistDetail?> = graph.playlists.observePlaylist(id)

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = graph.playlists.create(name)
            refreshStats()
            withContext(Dispatchers.Main) { onCreated?.invoke(id) }
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.playlists.rename(id, name)
            notify("Playlist renamed")
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.playlists.delete(id)
            refreshStats()
            notify("Playlist deleted")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = graph.playlists.addTrack(playlistId, track)
            notify(if (added) "Added to playlist" else "Already in this playlist")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.playlists.removeTrack(playlistId, trackId)
            refreshStats()
        }
    }

    fun reorderPlaylist(playlistId: Long, ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { graph.playlists.reorder(playlistId, ids) }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) { graph.playlists.importM3u(uri) }
            _busy.value = false
            notify(result.message)
            refreshStats()
        }
    }

    fun exportM3u(playlistId: Long, uri: Uri) {
        viewModelScope.launch {
            val count = runCatching { withContext(Dispatchers.IO) { graph.playlists.exportM3u(playlistId, uri) } }
            notify(
                count.fold(
                    onSuccess = { "Exported $it track(s) as M3U." },
                    onFailure = { it.message ?: "Could not export the playlist." },
                ),
            )
        }
    }

    fun download(track: Track) {
        viewModelScope.launch {
            val result = downloadsRepository.enqueue(track)
            notify(result.message)
            refreshStorage()
            if (result.ok) refreshStats()
        }
    }

    fun pauseDownload(trackId: String) {
        viewModelScope.launch { downloadsRepository.pause(trackId) }
    }

    fun resumeDownload(trackId: String) {
        viewModelScope.launch {
            val result = downloadsRepository.resume(trackId)
            notify(result.message)
        }
    }

    fun retryDownload(trackId: String) {
        viewModelScope.launch {
            val result = downloadsRepository.retry(trackId)
            notify(result.message)
            refreshStorage()
        }
    }

    fun resumeAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = downloadsRepository.resumeAll()
            notify("Resumed $count download(s)")
            refreshStorage()
        }
    }

    fun cancelDownload(trackId: String) {
        viewModelScope.launch {
            downloadsRepository.cancel(trackId)
            refreshStorage()
        }
    }

    fun deleteDownload(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = downloadsRepository.delete(trackId)
            notify(if (deleted) "Offline copy deleted" else "Download not found")
            refreshStorage()
            refreshStats()
        }
    }

    fun clearDownloadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = downloadsRepository.clearHistory()
            notify(if (cleared == 0) "Nothing to clear" else "Cleared $cleared failed download(s)")
            refreshStorage()
        }
    }

    fun pauseAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = downloadsRepository.pauseAll()
            notify("Paused $count download(s)")
        }
    }

    fun restoreDownloadedMissing() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = downloads.value.mapNotNull { it.filePath }.toSet()
            val restored = graph.library.pruneDownloadedMissing(files)
            notify(if (restored == 0) "Offline files look healthy." else "Restored $restored track(s) to streaming.")
            refreshStats()
        }
    }

    fun downloadPermission(track: Track) = downloadsRepository.permissionReason(track)

    fun saveVideo(track: Track, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.library.takeVideoPermission(uri)
            graph.library.setVideo(track.id, uri.toString())
            notify("Video attached to this track")
        }
    }

    fun removeVideo(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.library.setVideo(track.id, null)
            notify("Video association removed")
        }
    }

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { graph.backup.exportTo(uri) } }
            _busy.value = false
            notify(
                result.fold(
                    onSuccess = { "Backup written with ${it.playlists.size} playlist(s) and ${it.favorites.size} favorite(s)." },
                    onFailure = { it.message ?: "Backup failed." },
                ),
            )
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { graph.backup.importFrom(uri) } }
            _busy.value = false
            notify(
                result.fold(
                    onSuccess = { it.message.ifBlank { "Backup restored." } },
                    onFailure = { it.message ?: "Restore failed." },
                ),
            )
            refreshStats()
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            graph.library.clearHistory()
            notify("Playback history cleared")
            refreshStats()
        }
    }

    suspend fun runDiagnostics(): DiagnosticReport = withContext(Dispatchers.IO) { graph.diagnostics.runChecks() }

    suspend fun clearCaches(): Long = withContext(Dispatchers.IO) { graph.diagnostics.clearCaches() }

    suspend fun albumTracks(album: AlbumSummary): List<Track> =
        withContext(Dispatchers.IO) { graph.library.albumTracks(album.key) }

    suspend fun artistTracks(artist: ArtistSummary): List<Track> =
        withContext(Dispatchers.IO) { graph.library.artistTracks(artist.key) }

    suspend fun tracksForFolder(folder: FolderSummary): List<Track> =
        withContext(Dispatchers.IO) { graph.library.tracksForGenreOrFolder(folder = folder.key) }

    suspend fun tracksForGenre(genre: FolderSummary): List<Track> =
        withContext(Dispatchers.IO) { graph.library.tracksForGenreOrFolder(genre = genre.key) }

    suspend fun allTracksOnce(): List<Track> = withContext(Dispatchers.IO) { graph.library.allLibraryTracks() }

    suspend fun favoritesOnce(): List<Track> = withContext(Dispatchers.IO) { graph.library.favoriteTracks.first() }

    suspend fun recentlyPlayedOnce(): List<Track> = withContext(Dispatchers.IO) { graph.library.recentlyPlayed.first() }

    suspend fun downloadedTracksOnce(): List<Track> = withContext(Dispatchers.IO) { graph.library.downloadedTracks.first() }

    fun trackById(id: String, onResult: (Track?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = graph.library.getTrack(id)
            withContext(Dispatchers.Main) { onResult(track) }
        }
    }

    fun saveQueueAsPlaylist(name: String, queue: List<Track>, onDone: (Long, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (queue.isEmpty()) {
                withContext(Dispatchers.Main) { onDone(0, "The queue is empty.") }
                return@launch
            }
            val id = graph.playlists.saveQueueAsPlaylist(name, queue)
            refreshStats()
            withContext(Dispatchers.Main) { onDone(id, "Saved ${queue.size} track(s) to \"$name\".") }
        }
    }

    fun setThemeMode(mode: ThemeMode) = updateSetting { graph.settings.setThemeMode(mode) }
    fun setAccent(hex: String?) = updateSetting { graph.settings.setCustomAccent(hex) }
    fun setOnlineDiscovery(enabled: Boolean) = updateSetting { graph.settings.setOnlineDiscoveryEnabled(enabled) }
    fun setLrclib(enabled: Boolean) = updateSetting { graph.settings.setLrclibEnabled(enabled) }
    fun setGapless(enabled: Boolean) = updateSetting { graph.settings.setGaplessEnabled(enabled) }
    fun setAutoplayNext(enabled: Boolean) = updateSetting { graph.settings.setAutoplayNext(enabled) }
    fun setQueueAction(action: QueueAction) = updateSetting { graph.settings.setQueueAction(action) }
    fun setDownloadQuality(quality: DownloadQuality) = updateSetting { graph.settings.setDownloadQuality(quality) }
    fun setSearchHistory(enabled: Boolean) = updateSetting { graph.settings.setSearchHistoryEnabled(enabled) }
    fun setScanOnStartup(enabled: Boolean) = updateSetting { graph.settings.setScanMediaStoreOnStartup(enabled) }
    fun setShowStats(enabled: Boolean) = updateSetting { graph.settings.setShowPlaybackStats(enabled) }
    fun setKeepQueue(enabled: Boolean) = updateSetting { graph.settings.setKeepQueueOnExit(enabled) }
    fun setStartupScreen(value: TvStartScreen) = updateSetting { graph.settings.setStartupScreen(value) }
    fun setDefaultDisplayMode(value: TvDisplayMode) = updateSetting { graph.settings.setDefaultDisplayMode(value) }
    fun setShowControlsInCover(value: Boolean) = updateSetting { graph.settings.setShowControlsInCover(value) }
    fun setArtworkScale(value: dev.tether.core.model.ArtworkScale) = updateSetting { graph.settings.setArtworkScale(value) }
    fun setLyricsTextSize(value: Int) = updateSetting { graph.settings.setLyricsTextSize(value) }
    fun setScreensaverMinutes(value: Int) = updateSetting { graph.settings.setScreensaverMinutes(value) }

    private fun updateSetting(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { block() } }
    }

    suspend fun settingsSnapshot(): AppSettings = graph.settings.settings.first()

    fun searchLocal(query: String, callback: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.library.searchLocal(query, 50) }
            withContext(Dispatchers.Main) { callback(result) }
        }
    }

    fun importLyricsFile(track: Track, uri: Uri, callback: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.lyrics.importFile(track, uri) }
            withContext(Dispatchers.Main) { callback(result.message ?: "Lyrics updated") }
        }
    }

    suspend fun lyrics(track: Track, force: Boolean = false): dev.tether.core.model.LyricsViewState =
        withContext(Dispatchers.IO) { graph.lyrics.load(track, force = force) }

    suspend fun importLyrics(track: Track, uri: Uri): dev.tether.core.model.LyricsViewState =
        withContext(Dispatchers.IO) { graph.lyrics.importFile(track, uri) }

    suspend fun resolveRelease(releaseId: String, providerId: String): List<dev.tether.core.model.ProviderTrack> =
        withContext(Dispatchers.IO) { graph.search.resolveRelease(releaseId, providerId) }

    suspend fun searchAll(query: String): dev.tether.core.data.SearchResults =
        withContext(Dispatchers.IO) { graph.search.search(query) }

    fun trackForPlaylistEntry(trackId: String, callback: (Track?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = graph.library.getTrack(trackId)
            withContext(Dispatchers.Main) { callback(track) }
        }
    }

    fun recentSearchesValue(): List<RecentSearch> = recentSearches.value

    fun rememberSearch(query: String) {
        // SearchRepository records provider-backed searches; this keeps local-only
        // searches in recents too without inventing remote results.
        if (query.isBlank() || !settings.value.searchHistoryEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            graph.search.recordSearch(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) { graph.search.clearHistory() }
    }

    fun removeSearch(query: String) {
        viewModelScope.launch(Dispatchers.IO) { graph.search.removeSearch(query) }
    }

    fun archiveSearch(query: String) {
        rememberSearch(query)
    }

    fun setPlaylistEntriesPlaylistIdNoop() = Unit

    fun markOnboardingComplete() {
        viewModelScope.launch(Dispatchers.IO) { graph.settings.setOnboardingComplete(true) }
    }

    fun librarySortComparator(sort: LibrarySort): Comparator<Track> = when (sort) {
        LibrarySort.TITLE -> compareBy { it.title.lowercase() }
        LibrarySort.ARTIST -> compareBy<Track> { it.artist.lowercase() }
            .thenBy { it.title.lowercase() }
        LibrarySort.ALBUM -> compareBy<Track> { it.album.lowercase() }
            .thenBy { it.trackNumber ?: 0 }
        LibrarySort.RECENTLY_ADDED -> compareByDescending { it.addedAt }
        LibrarySort.DURATION -> compareByDescending { it.durationMs }
    }

    fun makePlaylistFromTracks(name: String, tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = graph.playlists.saveQueueAsPlaylist(name, tracks)
            notify("Created playlist with ${tracks.size} track(s).")
            refreshStats()
        }
    }

    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            var added = 0
            tracks.forEach { if (graph.playlists.addTrack(playlistId, it)) added++ }
            notify("Added $added track(s) to the playlist")
        }
    }

    fun savePlaylistId(id: Long): Long = id

    fun blankPlaylistName(): String = "New playlist"
}
