package dev.tether.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherGlass
import dev.tether.core.design.TetherIconBubble
import dev.tether.core.design.TetherOutlineButton
import dev.tether.core.design.TetherPrimaryButton
import dev.tether.core.design.TetherSectionHeader
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.DownloadStatus
import dev.tether.core.model.Playlist
import dev.tether.core.model.PlaylistDetail
import dev.tether.core.model.PlaylistEntry
import dev.tether.core.model.Track
import dev.tether.core.model.formatBytes
import dev.tether.core.model.formatDate
import dev.tether.core.model.formatDuration
import dev.tether.core.model.formatDurationLong
import dev.tether.core.player.PlaybackState

@Composable
fun DownloadsScreen(
    viewModel: MobileViewModel,
    playerState: PlaybackState,
    onOpenExplore: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val active = downloads.filter { it.status.isActive }
    val paused = downloads.filter { it.status == DownloadStatus.PAUSED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }.sortedByDescending { it.completedAt ?: it.updatedAt }
    var showMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Downloads",
            subtitle = if (completed.isEmpty()) {
                "Saved music, available offline"
            } else {
                "${completed.size} offline \u00b7 ${formatBytes(completed.sumOf { it.totalBytes })}"
            },
            actions = {
                Box {
                    TetherIconBubble(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "Download options",
                        onClick = { showMenu = true },
                        size = 40.dp,
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Pause all active") },
                            enabled = active.isNotEmpty(),
                            onClick = {
                                viewModel.pauseAllDownloads()
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Resume paused") },
                            enabled = paused.isNotEmpty(),
                            onClick = {
                                viewModel.resumeAllDownloads()
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear failed and cancelled") },
                            enabled = failed.isNotEmpty(),
                            onClick = {
                                viewModel.clearDownloadHistory()
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Check offline files") },
                            onClick = {
                                viewModel.restoreDownloadedMissing()
                                showMenu = false
                            },
                        )
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            item {
                StorageCard(
                    usedBytes = storage.appDownloadsBytes,
                    artworkBytes = storage.artworkBytes,
                    availableBytes = storage.availableBytes,
                    activeCount = active.size,
                    completedCount = completed.size,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (downloads.isEmpty()) {
                item {
                    TetherEmptyState(
                        title = "No downloads yet",
                        body = "Save music from providers that allow downloads. Offline copies stay in Tether's private storage.",
                        actionLabel = "Find music to download",
                        onAction = onOpenExplore,
                        icon = Icons.Rounded.CloudDownload,
                        showMark = false,
                    )
                }
            }
            if (active.isNotEmpty()) {
                item { DownloadSectionHeader("Downloading", "${active.size} active") }
                items(active, key = { "active-${it.trackId}" }) { record ->
                    DownloadRow(
                        record = record,
                        onPrimary = { viewModel.pauseDownload(record.trackId) },
                        onCancel = { viewModel.cancelDownload(record.trackId) },
                        onDelete = { viewModel.deleteDownload(record.trackId) },
                        onRetry = { viewModel.retryDownload(record.trackId) },
                        onPlay = {
                            viewModel.trackById(record.trackId) { track ->
                                if (track == null) viewModel.notify("This track is no longer linked to the offline file.")
                                else onPlayTrack(track)
                            }
                        },
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item { DownloadSectionHeader("Paused", "Resume whenever you are ready") }
                items(paused, key = { "paused-${it.trackId}" }) { record ->
                    DownloadRow(
                        record = record,
                        onPrimary = { viewModel.resumeDownload(record.trackId) },
                        onCancel = { viewModel.cancelDownload(record.trackId) },
                        onDelete = { viewModel.deleteDownload(record.trackId) },
                        onRetry = { viewModel.retryDownload(record.trackId) },
                        onPlay = {
                            viewModel.trackById(record.trackId) { track -> track?.let(onPlayTrack) }
                        },
                    )
                }
            }
            if (failed.isNotEmpty()) {
                item { DownloadSectionHeader("Needs attention", "Failed or cancelled transfers") }
                items(failed, key = { "failed-${it.trackId}" }) { record ->
                    DownloadRow(
                        record = record,
                        onPrimary = { viewModel.retryDownload(record.trackId) },
                        onCancel = { viewModel.deleteDownload(record.trackId) },
                        onDelete = { viewModel.deleteDownload(record.trackId) },
                        onRetry = { viewModel.retryDownload(record.trackId) },
                        onPlay = {},
                    )
                }
            }
            if (completed.isNotEmpty()) {
                item { DownloadSectionHeader("Available offline", "${completed.size} completed") }
                items(completed, key = { "done-${it.trackId}" }) { record ->
                    CompletedDownloadRow(
                        record = record,
                        isCurrent = playerState.currentTrack?.id == record.trackId,
                        onPlay = {
                            viewModel.trackById(record.trackId) { track ->
                                if (track == null) viewModel.notify("The offline file is not linked to a library track. Delete this entry or check offline files.")
                                else onPlayTrack(track)
                            }
                        },
                        onOpenMenu = {
                            viewModel.trackById(record.trackId) { track -> track?.let(onTrackMenu) }
                        },
                        onDelete = { viewModel.deleteDownload(record.trackId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(title: String, subtitle: String) {
    TetherSectionHeader(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun StorageCard(
    usedBytes: Long,
    artworkBytes: Long,
    availableBytes: Long,
    activeCount: Int,
    completedCount: Int,
    modifier: Modifier = Modifier,
) {
    TetherGlass(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("On-device storage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Tether's private app storage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StorageMeter(usedBytes = usedBytes, availableBytes = availableBytes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${completedCount} offline \u00b7 ${activeCount} active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Artwork ${formatBytes(artworkBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TetherCover(
                record.artworkUri,
                "${record.trackId}:${record.title}",
                record.title,
                Modifier.size(48.dp),
                cornerRadius = 11.dp,
                initialsSize = 15.sp,
            )
            Column(Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${record.artist} \u00b7 ${record.status.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (record.status) {
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = onPrimary) {
                        Icon(Icons.Rounded.Pause, contentDescription = "Pause download", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onPrimary) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume download", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete download", tint = MaterialTheme.colorScheme.error)
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry download", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove download entry", tint = MaterialTheme.colorScheme.error)
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play offline", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (record.status.isActive) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(record.progress.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${(record.progress * 100).toInt()}% \u00b7 ${formatBytes(record.bytesDownloaded)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (record.totalBytes > 0) formatBytes(record.totalBytes) else record.format,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        record.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (record.license != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                "License: ${record.license}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompletedDownloadRow(
    record: DownloadRecord,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onOpenMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TetherCover(
            record.artworkUri,
            "${record.trackId}:${record.title}",
            record.title,
            Modifier.size(50.dp),
            cornerRadius = 11.dp,
            initialsSize = 15.sp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                record.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${record.artist} \u00b7 ${record.format.ifBlank { "audio" }} \u00b7 ${formatBytes(record.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            record.license?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play offline", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onOpenMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Track options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete offline copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete offline copy?",
            message = "The downloaded file and its license sidecar will be removed. If the provider still supplies this track, it will remain playable by streaming.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}

private fun DownloadStatus.label(): String = when (this) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Complete"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

@Composable
fun PlaylistsScreen(
    viewModel: MobileViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportM3u: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Playlists",
            subtitle = if (playlists.isEmpty()) "Your saved collections" else "${stats.playlistCount} playlist${if (stats.playlistCount == 1) "" else "s"}",
            actions = {
                TetherIconBubble(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Create playlist",
                    onClick = { showCreate = true },
                    size = 40.dp,
                )
            },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TetherPrimaryButton("New playlist", { showCreate = true }, icon = Icons.Rounded.PlaylistAdd)
                    TetherOutlineButton("Import M3U", onImportM3u, icon = Icons.Rounded.Upload)
                }
            }
            if (playlists.isEmpty()) {
                item {
                    TetherEmptyState(
                        title = "No playlists yet",
                        body = "Create a collection here, save the queue from the player, or import an M3U file.",
                        actionLabel = "Create playlist",
                        onAction = { showCreate = true },
                        icon = Icons.Rounded.PlaylistAdd,
                        showMark = false,
                    )
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistManageRow(
                        playlist = playlist,
                        onOpen = { onOpenPlaylist(playlist.id) },
                        onRename = { name -> viewModel.renamePlaylist(playlist.id, name) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) },
                    )
                }
            }
        }
    }
    if (showCreate) {
        NameDialog(
            title = "New playlist",
            label = "Playlist name",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun PlaylistManageRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    TetherGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            TetherCover(
                playlist.coverArtUri,
                "playlist:${playlist.id}:${playlist.name}",
                playlist.name,
                Modifier.size(58.dp),
                cornerRadius = 14.dp,
                initialsSize = 19.sp,
            )
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}" +
                        if (playlist.updatedAt > 0) " \u00b7 ${formatDate(playlist.updatedAt)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        playlist.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        renameOpen = true
                    },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        deleteOpen = true
                    },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
        }
    }
    if (renameOpen) {
        NameDialog(
            title = "Rename playlist",
            label = "Playlist name",
            initial = playlist.name,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                onRename(name)
                renameOpen = false
            },
        )
    }
    if (deleteOpen) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${playlist.name}\" will be removed. Your audio files and downloads are not deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteOpen = false },
            onConfirm = onDelete,
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    viewModel: MobileViewModel,
    playlistId: Long,
    playerState: PlaybackState,
    onBack: () -> Unit,
    onExportM3u: (Long) -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val flow = remember(playlistId) { viewModel.playlistDetail(playlistId) }
    val detail by flow.collectAsStateWithLifecycle(initialValue = null)
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var removeEntry by remember { mutableStateOf<PlaylistEntry?>(null) }
    var entryMenuId by remember { mutableStateOf<String?>(null) }
    val currentId = playerState.currentTrack?.id

    if (detail == null) {
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(title = "Playlist", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
        }
        return
    }
    val data = detail ?: return
    val tracks = data.entries.map { it.track }
    val subtitle = buildString {
        append("${data.playlist.trackCount} track${if (data.playlist.trackCount == 1) "" else "s"}")
        val duration = tracks.sumOf { it.durationMs }
        if (duration > 0) append(" \u00b7 ").append(formatDurationLong(duration))
    }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = data.playlist.name,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                renameOpen = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export M3U") },
                            onClick = {
                                menuOpen = false
                                onExportM3u(playlistId)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                menuOpen = false
                                deleteOpen = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    TetherCover(
                        data.playlist.coverArtUri ?: tracks.firstNotNullOfOrNull { it.artworkUri },
                        "playlist:${data.playlist.id}:${data.playlist.name}",
                        data.playlist.name,
                        Modifier.size(132.dp),
                        cornerRadius = 20.dp,
                        initialsSize = 34.sp,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            data.playlist.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (data.playlist.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                data.playlist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            if (tracks.isEmpty()) {
                item {
                    TetherEmptyState(
                        title = "This playlist is empty",
                        body = "Add tracks from any track menu. Playlist order is yours, and removing a track here never deletes the file.",
                        icon = Icons.Rounded.PlaylistAdd,
                        showMark = false,
                    )
                }
            } else {
                itemsIndexed(data.entries, key = { _, entry -> entry.track.id }) { index, entry ->
                    PlaylistEntryRow(
                        entry = entry,
                        index = index,
                        isCurrent = currentId == entry.track.id,
                        isPlaying = playerState.isPlaying,
                        canMoveUp = index > 0,
                        canMoveDown = index < data.entries.lastIndex,
                        onPlay = { onPlayQueue(tracks, index, false) },
                        onMenuOpen = { entryMenuId = entry.track.id },
                        menuOpen = entryMenuId == entry.track.id,
                        onMenuDismiss = { entryMenuId = null },
                        onMoveUp = {
                            val ids = data.entries.map { it.track.id }.toMutableList()
                            val moved = ids.removeAt(index).also { ids.add(index - 1, it) }
                            viewModel.reorderPlaylist(playlistId, ids)
                            entryMenuId = null
                        },
                        onMoveDown = {
                            val ids = data.entries.map { it.track.id }.toMutableList()
                            val moved = ids.removeAt(index).also { ids.add(index + 1, it) }
                            viewModel.reorderPlaylist(playlistId, ids)
                            entryMenuId = null
                        },
                        onTrackActions = {
                            entryMenuId = null
                            onTrackMenu(entry.track)
                        },
                        onRemove = {
                            entryMenuId = null
                            removeEntry = entry
                        },
                    )
                }
            }
        }
    }

    if (renameOpen) {
        NameDialog(
            title = "Rename playlist",
            label = "Playlist name",
            initial = data.playlist.name,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                viewModel.renamePlaylist(playlistId, name)
                renameOpen = false
            },
        )
    }
    if (deleteOpen) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${data.playlist.name}\" will be removed. Your files, downloads and favorites stay intact.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                viewModel.deletePlaylist(playlistId)
                onBack()
            },
        )
    }
    removeEntry?.let { entry ->
        ConfirmDialog(
            title = "Remove from playlist?",
            message = "\"${entry.track.displayTitle}\" will be removed from this playlist. The audio file stays in your library.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removeEntry = null },
            onConfirm = {
                viewModel.removeTrackFromPlaylist(playlistId, entry.track.id)
                removeEntry = null
            },
        )
    }
}

@Composable
private fun PlaylistEntryRow(
    entry: PlaylistEntry,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMenuOpen: () -> Unit,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTrackActions: () -> Unit,
    onRemove: () -> Unit,
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            TetherCover(
                entry.track.artworkUri,
                entry.track.albumKey + entry.track.artist,
                entry.track.displayAlbum,
                Modifier.size(46.dp),
                cornerRadius = 10.dp,
                initialsSize = 14.sp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.track.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.track.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                formatDuration(entry.track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onMenuOpen, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Track options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(
                text = { Text("Track actions") },
                onClick = onTrackActions,
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
            DropdownMenuItem(
                text = { Text("Remove from playlist") },
                onClick = onRemove,
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            )
        }
    }
}
