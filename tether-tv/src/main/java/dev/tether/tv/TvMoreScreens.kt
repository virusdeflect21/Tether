package dev.tether.tv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherWordmark
import dev.tether.core.model.ArtworkScale
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.DownloadStatus
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.Playlist
import dev.tether.core.model.StorageInfo
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.Track
import dev.tether.core.model.TvDisplayMode
import dev.tether.core.model.TvStartScreen
import dev.tether.core.model.formatBytes
import dev.tether.core.model.formatDuration
import dev.tether.core.player.PlaybackState
import dev.tether.core.player.PlayerViewModel

@Composable
fun TvPlaylistsScreen(
    viewModel: TvViewModel,
    onOpenPlaylist: (Long) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val newFocus = remember { FocusRequester() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importM3u(uri)
    }
    LaunchedEffect(playlists.size) {
        runCatching {
            if (playlists.isEmpty()) newFocus.requestFocus() else firstFocus.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 30.dp)) {
        TvScreenHeader(
            title = "Playlists",
            subtitle = if (playlists.isEmpty()) "Build collections that stay in sync with Tether Mobile." else "${playlists.size} playlists",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Import M3U", { importLauncher.launch(arrayOf("audio/x-mpegurl", "text/plain", "application/octet-stream")) }, icon = Icons.Rounded.Upload)
                    TvButton(
                        "New playlist",
                        { showCreate = true },
                        icon = Icons.Rounded.Add,
                        primary = true,
                        focusRequester = if (playlists.isEmpty()) newFocus else null,
                    )
                }
            },
        )
        Spacer(Modifier.height(20.dp))
        if (playlists.isEmpty()) {
            TvEmptyState(
                title = "No playlists yet",
                body = "Create a playlist on the TV, or import an M3U file. Playlists use real tracks from your shared library.",
                action = {
                    TvButton("Create a playlist", { showCreate = true }, icon = Icons.Rounded.Add, primary = true)
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(playlists.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEachIndexed { index, playlist ->
                            TvPlaylistCard(
                                artworkUri = playlist.coverArtUri,
                                name = playlist.name,
                                trackCount = playlist.trackCount,
                                onClick = { onOpenPlaylist(playlist.id) },
                                focusRequester = if (playlist == playlists.first()) firstFocus else null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        TvTextInputDialog(
            title = "New playlist",
            label = "Playlist name",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
fun TvPlaylistDetailScreen(
    viewModel: TvViewModel,
    playlistId: Long,
    playerState: PlaybackState,
    onBack: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
) {
    val detailFlow = remember(playlistId) { viewModel.observePlaylist(playlistId) }
    val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)
    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    } else {
        val playlist = detail!!.playlist
        val tracks = detail!!.entries.map { it.track }
        TvCollectionScreen(
            title = playlist.name,
            subtitle = if (playlist.description.isBlank()) "${tracks.size} tracks" else playlist.description,
            artworkUri = playlist.coverArtUri,
            seed = "playlist:${playlist.id}",
            tracks = tracks,
            playerState = playerState,
            onBack = onBack,
            onPlayQueue = onPlayQueue,
        )
    }
}

@Composable
fun TvDownloadsScreen(
    viewModel: TvViewModel,
    onOpenSearch: () -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    val records by viewModel.downloadsState.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val trackById = remember(tracks) { tracks.associateBy { it.id } }
    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    val firstFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val active = records.filter { it.isActive }
    val paused = records.filter { it.status == DownloadStatus.PAUSED }
    val completed = records.filter { it.status == DownloadStatus.COMPLETED }
    val failed = records.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
    val visible = active + paused + failed + completed
    LaunchedEffect(records.size) {
        runCatching {
            if (records.isEmpty()) emptyFocus.requestFocus() else firstFocus.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 30.dp)) {
        TvScreenHeader(
            title = "Downloads",
            subtitle = "${formatBytes(storage.appDownloadsBytes)} stored for offline playback",
            trailing = { TvButton("Find music", onOpenSearch, icon = Icons.Rounded.Search) },
        )
        Spacer(Modifier.height(17.dp))
        if (records.isEmpty()) {
            TvEmptyState(
                title = "No downloads yet",
                body = "Remote tracks appear in Now Playing when their provider permits a real download. Tether never invents a file or ignores a license.",
                action = {
                    TvButton(
                        "Search open music",
                        onOpenSearch,
                        icon = Icons.Rounded.Search,
                        primary = true,
                        focusRequester = emptyFocus,
                    )
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (active.isNotEmpty()) {
                    item { DownloadSectionTitle("In progress", active.size) }
                    items(active, key = { "active-${it.trackId}" }) { record ->
                        TvDownloadRow(
                            record = record,
                            track = trackById[record.trackId],
                            initialFocus = if (record == visible.firstOrNull()) firstFocus else null,
                            onPlay = {},
                            onPause = { viewModel.pauseDownload(record.trackId) },
                            onResume = { viewModel.resumeDownload(record.trackId) },
                            onRetry = { viewModel.retryDownload(record.trackId) },
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
                if (paused.isNotEmpty()) {
                    item { DownloadSectionTitle("Paused", paused.size) }
                    items(paused, key = { "paused-${it.trackId}" }) { record ->
                        TvDownloadRow(
                            record = record,
                            track = trackById[record.trackId],
                            initialFocus = if (record == visible.firstOrNull()) firstFocus else null,
                            onPlay = {},
                            onPause = {},
                            onResume = { viewModel.resumeDownload(record.trackId) },
                            onRetry = { viewModel.retryDownload(record.trackId) },
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
                if (failed.isNotEmpty()) {
                    item { DownloadSectionTitle("Needs attention", failed.size) }
                    items(failed, key = { "failed-${it.trackId}" }) { record ->
                        TvDownloadRow(
                            record = record,
                            track = trackById[record.trackId],
                            initialFocus = if (record == visible.firstOrNull()) firstFocus else null,
                            onPlay = {},
                            onPause = {},
                            onResume = {},
                            onRetry = { viewModel.retryDownload(record.trackId) },
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
                if (completed.isNotEmpty()) {
                    item { DownloadSectionTitle("Available offline", completed.size) }
                    items(completed, key = { "done-${it.trackId}" }) { record ->
                        TvDownloadRow(
                            record = record,
                            track = trackById[record.trackId],
                            initialFocus = if (record == visible.firstOrNull()) firstFocus else null,
                            onPlay = {
                                trackById[record.trackId]?.let(onPlayTrack)
                            },
                            onPause = {},
                            onResume = {},
                            onRetry = {},
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        TvConfirmDialog(
            title = "Delete offline copy?",
            body = "This removes the downloaded audio and its download record. Your library entry and playlists stay. You can download it again if the provider still allows it.",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteDownload(record.trackId)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun DownloadSectionTitle(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvDownloadRow(
    record: DownloadRecord,
    track: Track?,
    initialFocus: FocusRequester?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val canPlay = record.status == DownloadStatus.COMPLETED && track != null
    val mainContent = @Composable {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dev.tether.core.design.TetherCover(
                artworkUri = record.artworkUri,
                seed = record.trackId,
                title = record.title,
                modifier = Modifier.size(58.dp),
                cornerRadius = 8.dp,
                initialsSize = 18.sp,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${record.artist}  ·  ${record.sourceLabel}  ·  ${record.status.label()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.isActive || record.status == DownloadStatus.PAUSED) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { record.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else if (record.error != null) {
                    Text(
                        record.error ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        "${record.sizeLabel}  ·  ${record.quality.ifBlank { record.format }}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (record.isActive) {
                Text(
                    "${(record.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (record.status == DownloadStatus.COMPLETED) {
                TetherBadge("OFFLINE", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (canPlay) MaterialTheme.colorScheme.surface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canPlay) {
            TvFocusable(
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.005f,
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                focusRequester = initialFocus,
                contentDescription = "Play ${record.title}",
            ) {
                mainContent()
            }
        } else {
            Box(Modifier.weight(1f)) { mainContent() }
        }
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (record.status) {
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                    TvIconButton(
                        Icons.Rounded.Pause,
                        "Pause download",
                        onPause,
                        focusRequester = if (!canPlay && initialFocus != null) initialFocus else null,
                    )
                }
                DownloadStatus.PAUSED -> {
                    TvIconButton(
                        Icons.Rounded.PlayArrow,
                        "Resume download",
                        onResume,
                        focusRequester = if (!canPlay && initialFocus != null) initialFocus else null,
                    )
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    TvIconButton(
                        Icons.Rounded.Refresh,
                        "Retry download",
                        onRetry,
                        focusRequester = if (!canPlay && initialFocus != null) initialFocus else null,
                    )
                }
                DownloadStatus.COMPLETED -> Unit
            }
            TvIconButton(Icons.Rounded.Delete, "Delete offline copy", onDelete)
        }
    }
}

private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

private enum class SettingsTab(val label: String) {
    APPEARANCE("Appearance"),
    PLAYBACK("Playback"),
    LIBRARY("Library"),
    ABOUT("About"),
}

@Composable
fun TvSettingsScreen(
    viewModel: TvViewModel,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val mediaFolders by viewModel.mediaFolders.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(SettingsTab.APPEARANCE) }
    var showClearCaches by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 30.dp)) {
        TvScreenHeader(
            title = "Settings",
            subtitle = "Shared preferences with Tether Mobile · ${mediaFolders.size} saved folders",
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingsTab.entries.forEach { tab ->
                TvPill(tab.label, section == tab, { section = tab })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (section) {
            SettingsTab.APPEARANCE -> LazyColumn(
                contentPadding = PaddingValues(bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    TvSettingsSection("Theme") {
                        TvChoiceRow(
                            title = "Color mode",
                            subtitle = "The shared Tether theme. System follows the TV's dark theme.",
                            options = ThemeMode.entries.map { it.name to it.label() },
                            selected = settings.themeMode.name,
                            onSelect = { viewModel.setThemeMode(ThemeMode.valueOf(it)) },
                            focusRequester = firstFocus,
                        )
                        TvChoiceRow(
                            title = "Accent",
                            subtitle = "Blue-violet is the default Tether brand accent.",
                            options = listOf(
                                "" to "Default",
                                "#8FA6FF" to "Blue",
                                "#C3A6FF" to "Violet",
                                "#F0A6FF" to "Magenta",
                                "#8DE3C0" to "Mint",
                            ),
                            selected = settings.customAccent.orEmpty(),
                            onSelect = { viewModel.setAccent(it.ifBlank { null }) },
                        )
                    }
                }
                item {
                    TvSettingsSection("Now Playing") {
                        TvChoiceRow(
                            title = "Default view",
                            subtitle = "Cover, lyrics and video switch without stopping the audio.",
                            options = TvDisplayMode.entries.map { it.name to it.label() },
                            selected = settings.defaultDisplayMode.name,
                            onSelect = { viewModel.setDefaultDisplayMode(TvDisplayMode.valueOf(it)) },
                        )
                        TvChoiceRow(
                            title = "Artwork scaling",
                            subtitle = "Fit keeps the whole cover visible. Fill crops to the frame.",
                            options = ArtworkScale.entries.map { it.name to it.label() },
                            selected = settings.artworkScale.name,
                            onSelect = { viewModel.setArtworkScale(ArtworkScale.valueOf(it)) },
                        )
                        TvChoiceRow(
                            title = "Lyrics size",
                            subtitle = "TV lyrics use this size. Plain lyrics never pretend to scroll.",
                            options = listOf("32" to "Small", "42" to "Default", "52" to "Large", "62" to "Huge"),
                            selected = settings.lyricsTextSize.coerceIn(32, 62).toString(),
                            onSelect = { viewModel.setLyricsTextSize(it.toInt()) },
                        )
                        TvChoiceRow(
                            title = "Screensaver",
                            subtitle = "Dim artwork and a clock appear after this much idle time.",
                            options = listOf("0" to "Off", "2" to "2 min", "5" to "5 min", "10" to "10 min", "20" to "20 min"),
                            selected = settings.screensaverMinutes.toString(),
                            onSelect = { viewModel.setScreensaverMinutes(it.toInt()) },
                        )
                        TvToggleRow(
                            title = "Show controls on cover",
                            subtitle = "Keep transport controls visible over the cover. Turn off for a cleaner TV view.",
                            checked = settings.showControlsInCoverMode,
                            onToggle = { viewModel.setShowControlsInCover(!settings.showControlsInCoverMode) },
                        )
                        TvChoiceRow(
                            title = "Start screen",
                            subtitle = "Choose where Tether opens when the app starts.",
                            options = TvStartScreen.entries.map { it.name to it.label() },
                            selected = settings.startupScreen.name,
                            onSelect = { viewModel.setStartupScreen(TvStartScreen.valueOf(it)) },
                        )
                    }
                }
            }
            SettingsTab.PLAYBACK -> LazyColumn(
                contentPadding = PaddingValues(bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    TvSettingsSection("Online and downloads") {
                        TvToggleRow(
                            title = "Online discovery",
                            subtitle = "Use ccMixter for Creative Commons music. Local playback never depends on it.",
                            checked = settings.onlineDiscoveryEnabled,
                            onToggle = { viewModel.setOnlineDiscovery(!settings.onlineDiscoveryEnabled) },
                            focusRequester = firstFocus,
                        )
                        TvToggleRow(
                            title = "Lyrics from LRCLIB",
                            subtitle = "Look up synced or plain lyrics. Imported .lrc files take priority.",
                            checked = settings.lrclibEnabled,
                            onToggle = { viewModel.setLrclib(!settings.lrclibEnabled) },
                        )
                        TvToggleRow(
                            title = "Save search history",
                            subtitle = "Recent searches stay on this device.",
                            checked = settings.searchHistoryEnabled,
                            onToggle = { viewModel.setSearchHistory(!settings.searchHistoryEnabled) },
                        )
                        TvChoiceRow(
                            title = "Download quality",
                            subtitle = settings.downloadQuality.description,
                            options = DownloadQuality.entries.map { it.name to it.label },
                            selected = settings.downloadQuality.name,
                            onSelect = { viewModel.setDownloadQuality(DownloadQuality.valueOf(it)) },
                        )
                    }
                }
                item {
                    TvSettingsSection("Playback") {
                        TvToggleRow(
                            title = "Scan MediaStore at startup",
                            subtitle = "Indexes audio visible through Android media permission. Nothing is uploaded.",
                            checked = settings.scanMediaStoreOnStartup,
                            onToggle = { viewModel.setScanOnStartup(!settings.scanMediaStoreOnStartup) },
                        )
                    }
                }
            }
            SettingsTab.LIBRARY -> LazyColumn(
                contentPadding = PaddingValues(bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    TvSettingsSection("Music sources") {
                        TvActionRow(
                            title = if (hasAudioPermission) "Device music access granted" else "Allow device music access",
                            subtitle = if (hasAudioPermission) "MediaStore scanning is allowed." else "Request Android media audio permission. Folder picks still work without it.",
                            onClick = if (hasAudioPermission) null else onRequestPermission,
                            focusRequester = firstFocus,
                        )
                        TvActionRow(
                            title = "Add a music folder",
                            subtitle = "Use the system folder picker. Tether keeps read access only.",
                            onClick = onAddFolder,
                        )
                        TvActionRow(
                            title = "Import individual audio files",
                            subtitle = "Useful for a few files without broad library access.",
                            onClick = onImportFiles,
                        )
                        TvActionRow(
                            title = "Rescan device audio",
                            subtitle = "Refresh the index. Playlists and favorites stay intact.",
                            onClick = if (hasAudioPermission) viewModel::scanMediaStore else onRequestPermission,
                        )
                    }
                }
                item {
                    TvSettingsSection("Storage") {
                        TvActionRow(
                            title = "Downloads and cache",
                            subtitle = "${formatBytes(storage.appDownloadsBytes)} downloads · ${formatBytes(storage.artworkBytes)} artwork · ${formatBytes(storage.availableBytes)} free",
                        )
                        TvActionRow(
                            title = "Clear artwork and lyric caches",
                            subtitle = "Frees cached images and remote lyrics. Audio and downloads stay.",
                            onClick = { showClearCaches = true },
                        )
                    }
                }
                item {
                    TvSettingsSection("Folders") {
                        if (mediaFolders.isEmpty()) {
                            Text(
                                "No saved folders yet. Add one with the system picker.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            mediaFolders.forEach { folder ->
                                Text(
                                    "${folder.name} · ${folder.trackCount} tracks" + (folder.error?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            SettingsTab.ABOUT -> LazyColumn(
                contentPadding = PaddingValues(bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    TvSettingsSection("About") {
                        TvActionRow(
                            title = "About Tether and licenses",
                            subtitle = "Version, local-first principles, provider credits and open-source licenses.",
                            onClick = onOpenAbout,
                            focusRequester = firstFocus,
                        )
                        TvActionRow(
                            title = "Tether TV 1.0.0",
                            subtitle = "Separate TV app · shared library and playback engine",
                        )
                    }
                }
                item {
                    TvSettingsSection("Privacy") {
                        Text(
                            "No account, no advertising ID, no analytics. Library scanning and playback happen on this device. Network calls happen only for online discovery, lyrics you request and downloads whose licenses allow them.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showClearCaches) {
        TvConfirmDialog(
            title = "Clear caches?",
            body = "Artwork thumbnails and cached provider lyrics will be removed. Downloaded audio and local files stay.",
            confirmLabel = "Clear",
            onDismiss = { showClearCaches = false },
            onConfirm = {
                viewModel.clearCaches()
                showClearCaches = false
            },
        )
    }
}

@Composable
private fun TvSettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 5.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun TvChoiceRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEachIndexed { index, (value, label) ->
                TvPill(
                    label,
                    selected == value,
                    { onSelect(value) },
                    focusRequester = if (index == 0) focusRequester else null,
                )
            }
        }
    }
}

@Composable
private fun TvToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(12.dp),
        scaleOnFocus = 1.005f,
        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
        focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        focusRequester = focusRequester,
        contentDescription = "$title, currently ${if (checked) "on" else "off"}",
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .width(48.dp)
                    .height(26.dp)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(20.dp).background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            }
        }
    }
}

@Composable
private fun TvActionRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val modifier = Modifier.fillMaxWidth().height(62.dp)
    if (onClick != null) {
        TvFocusable(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            scaleOnFocus = 1.005f,
            backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            focusRequester = focusRequester,
            contentDescription = title,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        Row(
            modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
}

private fun TvDisplayMode.label(): String = when (this) {
    TvDisplayMode.COVER -> "Cover"
    TvDisplayMode.LYRICS -> "Lyrics"
    TvDisplayMode.VIDEO -> "Video"
}

private fun ArtworkScale.label(): String = when (this) {
    ArtworkScale.FIT -> "Fit"
    ArtworkScale.FILL -> "Fill"
}

private fun TvStartScreen.label(): String = when (this) {
    TvStartScreen.HOME -> "Home"
    TvStartScreen.NOW_PLAYING -> "Now Playing"
}

@Composable
fun TvAboutScreen(onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TetherMark(64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    TetherWordmark(titleSize = 30)
                    Text(
                        "Tether TV 1.0.0",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text(
                "Tether is a local-first music player for the music you own and for openly licensed music you choose to explore. It does not require an account and has no ads or analytics.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            TvAboutPoint("Honest playback", "The TV app controls the same media session as Tether Mobile. Audio continues when Cover, Lyrics or Video changes.")
            TvAboutPoint("Real providers", "ccMixter supplies Creative Commons discovery. LRCLIB supplies lyrics. If a service is unavailable, Tether says so instead of inventing a result.")
            TvAboutPoint("Licensed downloads", "A remote track can be downloaded only when its provider confirms permission and supplies a real file. Attribution and license data stay with the track.")
            TvAboutPoint("Local files", "SAF folders and device audio are read from local storage. They cannot be downloaded and are never uploaded.")
        }
        item {
            TvFocusable(
                onClick = onBack,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                focusRequester = focusRequester,
                contentDescription = "Back to settings",
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    Text("Back to settings", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))
            Text(
                "Open-source libraries",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "AndroidX and Jetpack Compose · Apache 2.0\nMedia3 / ExoPlayer · Apache 2.0\nRoom, DataStore, WorkManager · Apache 2.0\nKotlin and kotlinx.coroutines · Apache 2.0\nOkHttp · Apache 2.0\nCoil · Apache 2.0",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item {
            Text(
                "Tether is licensed under the Apache License, Version 2.0. Provider catalogs and their content remain under their own licenses. No code or brand assets from other music players are included.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvAboutPoint(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
