package dev.tether.tv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tether.core.data.ScanResult
import dev.tether.core.data.Tether
import dev.tether.core.downloads.Downloads
import dev.tether.core.model.AppSettings
import dev.tether.core.model.ArtworkScale
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.FolderSummary
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.LyricsViewState
import dev.tether.core.model.MediaFolder
import dev.tether.core.model.Playlist
import dev.tether.core.model.StorageInfo
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.Track
import dev.tether.core.model.TvDisplayMode
import dev.tether.core.model.TvStartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = Tether.graph(application)
    private val downloads = Downloads.repository(application)

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val settingsLoaded: StateFlow<Boolean> = graph.settings.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val tracks: StateFlow<List<Track>> = graph.library.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentTracks: StateFlow<List<Track>> = graph.library.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyAdded: StateFlow<List<Track>> = graph.library.recentlyAdded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteTracks: StateFlow<List<Track>> = graph.library.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteIds: StateFlow<List<String>> = graph.library.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = graph.library.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = graph.library.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<Playlist>> = graph.playlists.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloadsState: StateFlow<List<DownloadRecord>> = downloads.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaFolders: StateFlow<List<MediaFolder>> = graph.library.mediaFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _stats = MutableStateFlow(LibraryStats())
    val stats: StateFlow<LibraryStats> = _stats
    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var autoScanned = false

    init {
        refreshStats()
        refreshStorage()
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun maybeAutoScan(hasPermission: Boolean) {
        if (autoScanned || !hasPermission) return
        if (!settings.value.scanMediaStoreOnStartup) {
            autoScanned = true
            return
        }
        autoScanned = true
        scanMediaStoreInternal(showSummary = false)
    }

    fun scanMediaStore() {
        scanMediaStoreInternal(showSummary = true)
    }

    private fun scanMediaStoreInternal(showSummary: Boolean) {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val result = graph.library.scanMediaStore()
            _scanning.value = false
            if (showSummary) announceScan(result)
            refreshStats()
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            _scanning.value = true
            val result = graph.library.addFolder(uri.toString())
            _scanning.value = false
            announceScan(result)
            refreshStats()
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _scanning.value = true
            val result = graph.library.importFiles(uris)
            _scanning.value = false
            announceScan(result)
            refreshStats()
        }
    }

    private fun announceScan(result: ScanResult) {
        notify(if (result.hasErrors) result.errors.first() else result.summary)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val favorite = graph.library.toggleFavorite(track)
            notify(if (favorite) "Added to favorites" else "Removed from favorites")
            refreshStats()
        }
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = graph.playlists.create(trimmed)
            notify("Playlist created")
            withContext(Dispatchers.Main) { onCreated(id) }
            refreshStats()
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            graph.playlists.addTrack(playlistId, track)
            notify("Added to playlist")
            refreshStats()
        }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            val result = graph.playlists.importM3u(uri)
            notify(result.message)
            refreshStats()
        }
    }

    fun download(track: Track) {
        viewModelScope.launch {
            val result = downloads.enqueue(track)
            notify(result.message)
            refreshStorage()
            refreshStats()
        }
    }

    fun downloadPermission(track: Track) = graph.providers.downloadPermission(track)

    fun pauseDownload(trackId: String) {
        viewModelScope.launch {
            downloads.pause(trackId)
            refreshStorage()
        }
    }

    fun resumeDownload(trackId: String) {
        viewModelScope.launch {
            val result = downloads.resume(trackId)
            notify(result.message)
            refreshStorage()
        }
    }

    fun deleteDownload(trackId: String) {
        viewModelScope.launch {
            val deleted = downloads.delete(trackId)
            notify(if (deleted) "Offline copy deleted" else "Download not found")
            refreshStorage()
            refreshStats()
        }
    }

    fun retryDownload(trackId: String) {
        viewModelScope.launch {
            val result = downloads.retry(trackId)
            notify(result.message)
            refreshStorage()
        }
    }

    suspend fun albumTracks(key: String): List<Track> = withContext(Dispatchers.IO) {
        graph.library.albumTracks(key)
    }

    suspend fun artistTracks(key: String): List<Track> = withContext(Dispatchers.IO) {
        graph.library.artistTracks(key)
    }

    suspend fun folderTracks(folder: String): List<Track> = withContext(Dispatchers.IO) {
        graph.library.tracksForGenreOrFolder(folder = folder)
    }

    suspend fun playlistTracks(id: Long): Pair<Playlist?, List<Track>> = withContext(Dispatchers.IO) {
        val detail = graph.playlists.observePlaylist(id).first()
        detail?.playlist to (detail?.entries?.map { it.track } ?: emptyList())
    }

    suspend fun lyrics(track: Track, force: Boolean = false): LyricsViewState = withContext(Dispatchers.IO) {
        graph.lyrics.load(track, force = force)
    }

    suspend fun importLyrics(track: Track, uri: Uri): LyricsViewState = withContext(Dispatchers.IO) {
        graph.lyrics.importFile(track, uri)
    }

    fun attachVideo(track: Track, uri: Uri, onUpdated: (Track?) -> Unit) {
        viewModelScope.launch {
            graph.library.takeVideoPermission(uri)
            graph.library.setVideo(track.id, uri.toString())
            val updated = graph.library.getTrack(track.id)
            notify(if (updated?.videoUri != null) "Video attached" else "Could not attach that video")
            withContext(Dispatchers.Main) { onUpdated(updated) }
        }
    }

    fun clearCaches() {
        viewModelScope.launch {
            val freed = graph.diagnostics.clearCaches()
            graph.artwork.sizeBytes()
            notify("Cached images and service lyrics cleared")
            refreshStorage()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = graph.library.stats()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = downloads.storageInfo()
        }
    }

    fun observePlaylist(id: Long) = graph.playlists.observePlaylist(id)

    fun setThemeMode(value: ThemeMode) = updateSetting { graph.settings.setThemeMode(value) }
    fun setAccent(value: String?) = updateSetting { graph.settings.setCustomAccent(value) }
    fun setOnlineDiscovery(value: Boolean) = updateSetting { graph.settings.setOnlineDiscoveryEnabled(value) }
    fun setLrclib(value: Boolean) = updateSetting { graph.settings.setLrclibEnabled(value) }
    fun setStartupScreen(value: TvStartScreen) = updateSetting { graph.settings.setStartupScreen(value) }
    fun setDefaultDisplayMode(value: TvDisplayMode) = updateSetting { graph.settings.setDefaultDisplayMode(value) }
    fun setShowControlsInCover(value: Boolean) = updateSetting { graph.settings.setShowControlsInCover(value) }
    fun setArtworkScale(value: ArtworkScale) = updateSetting { graph.settings.setArtworkScale(value) }
    fun setLyricsTextSize(value: Int) = updateSetting { graph.settings.setLyricsTextSize(value) }
    fun setScreensaverMinutes(value: Int) = updateSetting { graph.settings.setScreensaverMinutes(value) }
    fun setDownloadQuality(value: DownloadQuality) = updateSetting { graph.settings.setDownloadQuality(value) }
    fun setScanOnStartup(value: Boolean) = updateSetting { graph.settings.setScanMediaStoreOnStartup(value) }
    fun setSearchHistory(value: Boolean) = updateSetting { graph.settings.setSearchHistoryEnabled(value) }

    private fun updateSetting(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { block() } }
    }
}

