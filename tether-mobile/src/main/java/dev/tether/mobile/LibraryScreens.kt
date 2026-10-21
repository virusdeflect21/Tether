package dev.tether.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherInfoBanner
import dev.tether.core.design.TetherOutlineButton
import dev.tether.core.design.TetherPrimaryButton
import dev.tether.core.design.TetherSectionHeader
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.FolderSummary
import dev.tether.core.model.LibrarySort
import dev.tether.core.model.MediaFolder
import dev.tether.core.model.Track
import dev.tether.core.model.formatDate
import dev.tether.core.model.formatDurationLong
import dev.tether.core.player.PlaybackState

private enum class LibraryTab(val key: String, val label: String) {
    TRACKS("tracks", "Tracks"),
    ALBUMS("albums", "Albums"),
    ARTISTS("artists", "Artists"),
    GENRES("genres", "Genres"),
    FOLDERS("folders", "Folders"),
}

@Composable
fun LibraryScreen(
    viewModel: MobileViewModel,
    playerState: PlaybackState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenGenre: (FolderSummary) -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val mediaFolders by viewModel.mediaFolders.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val currentId = playerState.currentTrack?.id
    var tabKey by rememberSaveable { mutableStateOf(LibraryTab.TRACKS.key) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var showSort by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    val tab = LibraryTab.entries.firstOrNull { it.key == tabKey } ?: LibraryTab.TRACKS
    val sorted = remember(tracks, sort) { tracks.sortedWith(viewModel.librarySortComparator(sort)) }
    val listState = rememberLazyListState()

    LaunchedEffect(tabKey) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Library",
            subtitle = if (stats.trackCount > 0) {
                "${stats.trackCount} tracks \u00b7 ${formatDurationLong(stats.totalDurationMs)}"
            } else {
                "Your local files and downloads"
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
        FilterChips(
            options = LibraryTab.entries.map { it.key to it.label },
            selected = tabKey,
            onSelect = { tabKey = it },
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                LibraryToolbar(
                    hasAudioPermission = hasAudioPermission,
                    scanning = scanning,
                    onRequestPermission = onRequestPermission,
                    onScan = viewModel::scanMediaStore,
                    onAddFolder = onAddFolder,
                    onImportFiles = onImportFiles,
                    onManageFolders = { showFolders = true },
                    lastScanAt = settings.lastScanAt,
                    folderCount = mediaFolders.size,
                )
            }
            when (tab) {
                LibraryTab.TRACKS -> {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${sorted.size} track${if (sorted.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(onClick = { showSort = true }) {
                                    Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(sort.label())
                                }
                                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                    LibrarySort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label()) },
                                            onClick = {
                                                sort = option
                                                showSort = false
                                            },
                                            trailingIcon = {
                                                if (sort == option) {
                                                    Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (sorted.isEmpty()) {
                        item {
                            TetherEmptyState(
                                title = "Your library is quiet",
                                body = "Add a folder, import audio files, or allow access to music on this device. Tether never downloads music on its own.",
                                actionLabel = "Choose a folder",
                                onAction = onAddFolder,
                            )
                        }
                    } else {
                        items(sorted, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                onClick = { onPlayQueue(sorted, sorted.indexOf(track), false) },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
                LibraryTab.ALBUMS -> {
                    if (albums.isEmpty()) {
                        item {
                            TetherEmptyState(
                                title = "No albums yet",
                                body = "Albums are grouped from the tags in your local audio files.",
                                actionLabel = "Add a folder",
                                onAction = onAddFolder,
                            )
                        }
                    } else {
                        items(albums.chunked(2), key = { row -> "album-row-${row.first().key}" }) { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                row.forEach { album ->
                                    AlbumGridTile(album, onClick = { onOpenAlbum(album) }, modifier = Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                LibraryTab.ARTISTS -> {
                    if (artists.isEmpty()) {
                        item {
                            TetherEmptyState(
                                title = "No artists yet",
                                body = "Artist tags from your library will appear here.",
                                actionLabel = "Add a folder",
                                onAction = onAddFolder,
                            )
                        }
                    } else {
                        items(artists.chunked(3), key = { row -> "artist-row-${row.first().key}" }) { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { artist ->
                                    ArtistGridTile(artist, onClick = { onOpenArtist(artist) }, modifier = Modifier.weight(1f))
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                LibraryTab.GENRES -> {
                    if (genres.isEmpty()) {
                        item {
                            TetherEmptyState(
                                title = "No genres tagged",
                                body = "Files without a genre tag still appear under Tracks. Tagging happens in your file metadata.",
                                showMark = false,
                                icon = Icons.Rounded.MusicNote,
                            )
                        }
                    } else {
                        items(genres, key = { "genre-${it.key}" }) { genre ->
                            FolderListRow(genre.name, genre.trackCount, onClick = { onOpenGenre(genre) })
                        }
                    }
                }
                LibraryTab.FOLDERS -> {
                    if (folders.isEmpty()) {
                        item {
                            TetherEmptyState(
                                title = "No folder groups",
                                body = "Folders are grouped from the location of imported files. You can also add a whole folder with the system picker.",
                                actionLabel = "Add a folder",
                                onAction = onAddFolder,
                            )
                        }
                    } else {
                        items(folders, key = { "folder-${it.key}" }) { folder ->
                            FolderListRow(folder.name.ifBlank { folder.key }, folder.trackCount, onClick = { onOpenFolder(folder) })
                        }
                    }
                }
            }
        }
    }

    if (showFolders) {
        ManageFoldersDialog(
            folders = mediaFolders,
            onDismiss = { showFolders = false },
            onAdd = onAddFolder,
            onRescan = viewModel::rescanFolder,
            onRemove = viewModel::removeFolder,
            onPrune = viewModel::pruneMissing,
        )
    }
}

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.TITLE -> "Title"
    LibrarySort.ARTIST -> "Artist"
    LibrarySort.ALBUM -> "Album"
    LibrarySort.RECENTLY_ADDED -> "Recently added"
    LibrarySort.DURATION -> "Duration"
}

@Composable
private fun LibraryToolbar(
    hasAudioPermission: Boolean,
    scanning: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onManageFolders: () -> Unit,
    lastScanAt: Long,
    folderCount: Int,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!hasAudioPermission) {
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Music access isn't granted. Choose individual folders or allow access to scan the device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestPermission) { Text("Allow") }
                }
            }
        }
        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Scanning\u2026 this can take a moment for large libraries.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallActionChip(Icons.Rounded.Refresh, "Scan device", onClick = {
                if (hasAudioPermission) onScan() else onRequestPermission()
            })
            SmallActionChip(Icons.Rounded.Folder, "Add folder", onClick = onAddFolder)
            SmallActionChip(Icons.Rounded.Add, "Import files", onClick = onImportFiles)
            SmallActionChip(Icons.Rounded.LibraryMusic, "Folders ($folderCount)", onClick = onManageFolders)
        }
        if (lastScanAt > 0) {
            Text(
                "Last scan ${formatDate(lastScanAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmallActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AlbumGridTile(album: AlbumSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        TetherCover(
            album.artworkUri,
            album.key,
            album.title,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16.dp,
            initialsSize = 30.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            album.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistGridTile(artist: ArtistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TetherCover(
            artist.artworkUri,
            artist.key,
            artist.name,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 100.dp,
            initialsSize = 26.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${artist.trackCount} track${if (artist.trackCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderListRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$count track${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ManageFoldersDialog(
    folders: List<MediaFolder>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRescan: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPrune: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music folders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Folders added with the system picker stay readable across restarts. Removing a folder does not delete your audio files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (folders.isEmpty()) {
                    Text(
                        "No system folders added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(folders, key = { it.treeUri }) { folder ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            folder.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            buildString {
                                                append("${folder.trackCount} tracks")
                                                folder.lastScanAt?.let { append(" \u00b7 scanned ${formatDate(it)}") }
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onRescan(folder.treeUri) }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Rescan folder")
                                    }
                                    IconButton(onClick = { onRemove(folder.treeUri) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Remove folder",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                folder.error?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onPrune) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Prune missing local files")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onAdd()
                    onDismiss()
                }) { Text("Add folder") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
fun CollectionScreen(
    kind: CollectionKind,
    collectionKey: String,
    title: String,
    subtitle: String,
    artworkUri: String?,
    viewModel: MobileViewModel,
    playerState: PlaybackState,
    onBack: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    var tracks by remember(kind, collectionKey) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(kind, collectionKey) { mutableStateOf(true) }
    var error by remember(kind, collectionKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, collectionKey) {
        loading = true
        error = null
        tracks = try {
            when (kind) {
                CollectionKind.ALBUM -> if (collectionKey == "all") viewModel.allTracksOnce() else {
                    viewModel.graph.library.albumTracks(collectionKey)
                }
                CollectionKind.ARTIST -> viewModel.graph.library.artistTracks(collectionKey)
                CollectionKind.GENRE -> viewModel.graph.library.tracksForGenreOrFolder(genre = collectionKey)
                CollectionKind.FOLDER -> viewModel.graph.library.tracksForGenreOrFolder(folder = collectionKey)
                CollectionKind.FAVORITES -> viewModel.favoritesOnce()
                CollectionKind.RECENT -> viewModel.recentlyPlayedOnce()
                CollectionKind.DOWNLOADED -> viewModel.downloadedTracksOnce()
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not load these tracks."
            emptyList()
        } finally {
            loading = false
        }
    }
    val currentId = playerState.currentTrack?.id
    val headerArtwork = artworkUri ?: tracks.firstNotNullOfOrNull { it.artworkUri }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(title = title, subtitle = if (subtitle.isBlank()) null else subtitle, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    TetherCover(
                        headerArtwork,
                        "$kind:$collectionKey",
                        title,
                        Modifier.size(124.dp),
                        cornerRadius = 59.dp.takeIf { kind == CollectionKind.ARTIST } ?: 20.dp,
                        initialsSize = 32.sp,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${tracks.size} track${if (tracks.size == 1) "" else "s"} \u00b7 ${formatDurationLong(tracks.sumOf { it.durationMs })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TetherPrimaryButton(
                        "Play",
                        { if (tracks.isNotEmpty()) onPlayQueue(tracks, 0, false) },
                        icon = Icons.Rounded.PlayArrow,
                        enabled = tracks.isNotEmpty(),
                    )
                    TetherOutlineButton(
                        "Shuffle",
                        { if (tracks.isNotEmpty()) onPlayQueue(tracks, 0, true) },
                        icon = Icons.Rounded.Shuffle,
                        enabled = tracks.isNotEmpty(),
                    )
                }
            }
            if (loading) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(strokeWidth = 2.dp) }
                }
            } else if (error != null) {
                item {
                    TetherEmptyState(
                        title = "Couldn't load this collection",
                        body = error!!,
                        icon = Icons.Rounded.MusicNote,
                        showMark = false,
                    )
                }
            } else if (tracks.isEmpty()) {
                item {
                    TetherEmptyState(
                        title = "No tracks here",
                        body = "This collection is empty. Local album and artist pages are built from your own file tags.",
                        icon = Icons.Rounded.MusicNote,
                    )
                }
            } else {
                items(tracks, key = { "${kind}-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlayQueue(tracks, tracks.indexOf(track), false) },
                        onMenuClick = { onTrackMenu(track) },
                        isCurrent = currentId == track.id,
                        isPlaying = playerState.isPlaying,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    TetherInfoBanner(
                        "This page was built from your local library. Tether does not fill missing tracks from the network.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
