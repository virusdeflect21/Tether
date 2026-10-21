package dev.tether.mobile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.LyricsPane
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherInfoBanner
import dev.tether.core.design.TetherInk
import dev.tether.core.design.TetherPrimaryButton
import dev.tether.core.model.LyricsDocument
import dev.tether.core.model.LyricsLoadState
import dev.tether.core.model.LyricsViewState
import dev.tether.core.model.Track
import dev.tether.core.model.formatDuration
import dev.tether.core.player.PlaybackState
import dev.tether.core.player.PlayerViewModel
import dev.tether.core.player.RepeatMode
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun PlayerScreen(
    viewModel: MobileViewModel,
    playerViewModel: PlayerViewModel,
    state: PlaybackState,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenAlbum: (String, String, String?) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val track = state.currentTrack
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var showSaveQueue by rememberSaveable { mutableStateOf(false) }
    var duration by remember(track?.id) { mutableFloatStateOf(0f) }
    var dragging by remember(track?.id) { mutableStateOf(false) }
    var scrub by remember(track?.id) { mutableFloatStateOf(0f) }

    if (track == null) {
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(title = "Now playing", onBack = onBack)
            TetherEmptyState(
                title = "Nothing playing yet",
                body = "Choose a track from your library or explore openly licensed music. Playback continues in the background once it starts.",
                icon = Icons.Rounded.PlayArrow,
                showMark = true,
                actionLabel = "Back",
                onAction = onBack,
            )
        }
        return
    }

    val maxDuration = max(max(state.durationMs, track.durationMs), 1L).toFloat()
    LaunchedEffect(track.id) { duration = maxDuration }
    LaunchedEffect(state.positionMs, dragging) {
        if (!dragging) scrub = state.positionMs.toFloat().coerceIn(0f, maxDuration)
    }
    val favorite = favoriteIds.contains(track.id)
    val next = state.queue.getOrNull(state.currentIndex + 1)

    Box(
        Modifier
            .fillMaxSize()
            .background(TetherInk),
    ) {
        TetherCover(
            artworkUri = track.artworkUri,
            seed = track.albumKey + track.artist,
            title = track.displayAlbum,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.30f)
                .blur(46.dp),
            cornerRadius = 0.dp,
            initialsSize = 80.sp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TetherInk.copy(alpha = 0.42f),
                            TetherInk.copy(alpha = 0.88f),
                            TetherInk,
                        ),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            MobileTopBar(
                title = "Now playing",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        viewModel.toggleFavorite(track)
                    }) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                            tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { onTrackMenu(track) }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    TetherCover(
                        artworkUri = track.artworkUri,
                        seed = track.albumKey + track.artist,
                        title = track.displayAlbum,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 380.dp)
                            .aspectRatio(1f),
                        cornerRadius = 26.dp,
                        initialsSize = 64.sp,
                    )
                    if (state.isBuffering) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(TetherInk.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        track.displayArtist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFC7C3D8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { if (track.album.isNotBlank()) onOpenAlbum(track.albumKey, track.displayAlbum, track.artworkUri) }) {
                    Icon(
                        Icons.Rounded.QueueMusic,
                        contentDescription = "Open album",
                        tint = Color(0xFFC7C3D8),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TetherBadge(
                    when (track.origin) {
                        dev.tether.core.model.TrackOrigin.LOCAL -> "LOCAL"
                        dev.tether.core.model.TrackOrigin.DOWNLOADED -> "OFFLINE"
                        dev.tether.core.model.TrackOrigin.REMOTE -> "STREAM"
                    },
                )
                Text(
                    when (track.origin) {
                        dev.tether.core.model.TrackOrigin.LOCAL -> "On this device"
                        dev.tether.core.model.TrackOrigin.DOWNLOADED -> "Offline copy"
                        dev.tether.core.model.TrackOrigin.REMOTE -> track.sourceLabel
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFA8A6BC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.license?.isNotBlank() == true) {
                    Text(
                        "\u00b7 ${track.license}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA8A6BC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                )
                TextButton(
                    onClick = playerViewModel::retry,
                    modifier = Modifier.padding(horizontal = 18.dp),
                ) { Text("Try again") }
            }
            Column(Modifier.padding(horizontal = 22.dp, vertical = 3.dp)) {
                Slider(
                    value = scrub.coerceIn(0f, maxDuration),
                    onValueChange = {
                        dragging = true
                        scrub = it
                    },
                    onValueChangeFinished = {
                        playerViewModel.seekTo(scrub.toLong())
                        dragging = false
                    },
                    valueRange = 0f..maxDuration,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(if (dragging) scrub.toLong() else state.positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA8A6BC),
                    )
                    Text(
                        formatDuration(maxDuration.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA8A6BC),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = playerViewModel::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color(0xFFA8A6BC),
                    )
                }
                IconButton(onClick = playerViewModel::previous, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = playerViewModel::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = playerViewModel::next, enabled = state.hasNext, modifier = Modifier.size(54.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = if (state.hasNext) Color.White else Color(0xFF6C6980),
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = playerViewModel::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color(0xFFA8A6BC),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlayerAction(
                    icon = Icons.Rounded.Subtitles,
                    label = "Lyrics",
                    onClick = onOpenLyrics,
                )
                PlayerAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Queue",
                    onClick = onOpenQueue,
                )
                PlayerAction(
                    icon = Icons.Rounded.Timer,
                    label = if (state.sleepTimerActive) formatDuration(state.sleepRemainingMs) else "Sleep",
                    active = state.sleepTimerActive,
                    onClick = { showSleep = true },
                )
                PlayerAction(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Save",
                    onClick = { showSaveQueue = true },
                )
            }
            if (next != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable(onClick = onOpenQueue),
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        TetherCover(
                            next.artworkUri,
                            next.albumKey + next.artist,
                            next.displayAlbum,
                            Modifier.size(42.dp),
                            cornerRadius = 9.dp,
                            initialsSize = 13.sp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                next.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                next.displayArtist,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA8A6BC),
                                maxLines = 1,
                            )
                        }
                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color(0xFFA8A6BC))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            TetherInfoBanner(
                "Playback is managed by Tether's media service, so it survives screen changes and backgrounding. Nothing is uploaded.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                tone = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showSleep) {
        SleepTimerDialog(
            active = state.sleepTimerActive,
            remainingMs = state.sleepRemainingMs,
            onDismiss = { showSleep = false },
            onChoose = { minutes ->
                playerViewModel.setSleepTimer(minutes)
                showSleep = false
            },
        )
    }
    if (showSaveQueue) {
        NameDialog(
            title = "Save queue as playlist",
            label = "Playlist name",
            initial = "Queue",
            confirmLabel = "Save playlist",
            supportingText = "The current queue order is kept. Missing provider tracks remain in the playlist honestly.",
            onDismiss = { showSaveQueue = false },
            onConfirm = { name ->
                showSaveQueue = false
                viewModel.saveQueueAsPlaylist(name, state.queue) { _, message ->
                    viewModel.notify(message)
                }
            },
        )
    }
}

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else Color(0xFFC7C3D8),
            modifier = Modifier.size(21.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else Color(0xFFA8A6BC),
            maxLines = 1,
        )
    }
}

@Composable
private fun SleepTimerDialog(
    active: Boolean,
    remainingMs: Long,
    onDismiss: () -> Unit,
    onChoose: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (active) "Pausing in ${formatDuration(remainingMs)}. The timer runs in the playback service." else "Pause playback automatically after a set time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf(0, 15, 30, 45, 60, 90).forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onChoose(minutes) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                    ) {
                        Text(
                            if (minutes == 0) "Turn off timer" else "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    viewModel: MobileViewModel,
    state: PlaybackState,
    onBack: () -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    var showSave by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var menuIndex by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Queue",
            subtitle = if (state.queue.isEmpty()) "Nothing queued" else "${state.queue.size} tracks \u00b7 ${formatDuration(state.queue.sumOf { it.durationMs })}",
            onBack = onBack,
            actions = {
                if (state.queue.isNotEmpty()) {
                    TextButton(onClick = { showSave = true }) { Text("Save") }
                    TextButton(onClick = { showClear = true }) { Text("Clear") }
                }
            },
        )
        if (state.queue.isEmpty()) {
            TetherEmptyState(
                title = "The queue is empty",
                body = "Play a track or use a track menu to add music to the queue. The queue appears here and stays with the playback service.",
                icon = Icons.Rounded.QueueMusic,
                showMark = false,
                actionLabel = "Back",
                onAction = onBack,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TetherInfoBanner(
                            "Tap a row to jump to it. Reordering never changes your playlists.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                items(state.queue, key = { it.id }) { track ->
                    val index = state.queue.indexOf(track)
                    val isCurrent = index == state.currentIndex
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { playerViewModel.seekToIndex(index) }
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            if (isCurrent) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = "Current track",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(26.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            TetherCover(
                                track.artworkUri,
                                track.albumKey + track.artist,
                                track.displayAlbum,
                                Modifier.size(44.dp),
                                cornerRadius = 10.dp,
                                initialsSize = 13.sp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.displayArtist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                formatDuration(track.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { menuIndex = index }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Queue options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        DropdownMenu(expanded = menuIndex == index, onDismissRequest = { menuIndex = null }) {
                            DropdownMenuItem(
                                text = { Text("Play now") },
                                onClick = {
                                    menuIndex = null
                                    playerViewModel.seekToIndex(index)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                enabled = index > 0,
                                onClick = {
                                    menuIndex = null
                                    playerViewModel.moveQueueItem(index, index - 1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                enabled = index < state.queue.lastIndex,
                                onClick = {
                                    menuIndex = null
                                    playerViewModel.moveQueueItem(index, index + 1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Track actions") },
                                onClick = {
                                    menuIndex = null
                                    onTrackMenu(track)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from queue") },
                                onClick = {
                                    menuIndex = null
                                    playerViewModel.removeQueueItem(index)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showSave) {
        NameDialog(
            title = "Save queue as playlist",
            label = "Playlist name",
            initial = "My queue",
            confirmLabel = "Save",
            onDismiss = { showSave = false },
            onConfirm = { name ->
                showSave = false
                viewModel.saveQueueAsPlaylist(name, state.queue) { _, message -> viewModel.notify(message) }
            },
        )
    }
    if (showClear) {
        ConfirmDialog(
            title = "Clear the queue?",
            message = "Playback stops and the queue is emptied. The tracks remain in your library and playlists.",
            confirmLabel = "Clear",
            destructive = true,
            onDismiss = { showClear = false },
            onConfirm = playerViewModel::clearQueue,
        )
    }
}

@Composable
fun LyricsScreen(
    viewModel: MobileViewModel,
    playerViewModel: PlayerViewModel,
    state: PlaybackState,
    onBack: () -> Unit,
) {
    val track = state.currentTrack
    var lyricsState by remember(track?.id) { mutableStateOf(LyricsViewState(LyricsLoadState.LOADING)) }
    var reload by remember(track?.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && track != null) {
            scope.launch {
                lyricsState = viewModel.importLyrics(track, uri)
                viewModel.notify(lyricsState.message ?: "Lyrics updated")
            }
        }
    }

    LaunchedEffect(track?.id, reload) {
        if (track == null) return@LaunchedEffect
        lyricsState = viewModel.lyrics(track, force = reload > 0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TetherInk),
    ) {
        if (track != null) {
            TetherCover(
                track.artworkUri,
                track.albumKey + track.artist,
                track.displayAlbum,
                Modifier
                    .fillMaxSize()
                    .alpha(0.16f)
                    .blur(42.dp),
                cornerRadius = 0.dp,
                initialsSize = 70.sp,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(TetherInk.copy(alpha = 0.64f), TetherInk, TetherInk),
                        ),
                    ),
            )
        }
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(
                title = "Lyrics",
                subtitle = track?.displayTitle,
                onBack = onBack,
                actions = {
                    if (track != null) {
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.Upload, contentDescription = "Import lyrics", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        if (lyricsState.state != LyricsLoadState.LOADING) {
                            IconButton(onClick = { reload++ }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reload lyrics", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
            )
            if (track == null) {
                TetherEmptyState(
                    title = "No track playing",
                    body = "Start a track to look for lyrics. Tether never displays placeholder lyrics.",
                    icon = Icons.Rounded.Subtitles,
                    showMark = false,
                    actionLabel = "Back",
                    onAction = onBack,
                )
                return@Column
            }
            val document = lyricsState.document
            when (lyricsState.state) {
                LyricsLoadState.IDLE, LyricsLoadState.LOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(
                                "Looking for real lyrics\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                LyricsLoadState.AVAILABLE -> {
                    if (document == null) {
                        TetherEmptyState(
                            title = "No lyrics available",
                            body = "The provider did not return readable lyrics for this track.",
                            icon = Icons.Rounded.Subtitles,
                            showMark = false,
                            actionLabel = "Try again",
                            onAction = { reload++ },
                        )
                    } else {
                        LyricsHeader(document)
                        if (!document.synced) {
                            TetherInfoBanner(
                                "These are plain lyrics from ${document.sourceLabel}. They have no timestamps, so Tether will not pretend to scroll along with the song.",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                tone = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        LyricsPane(
                            document = document,
                            positionMs = if (document.synced) state.positionMs else 0L,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 4.dp),
                            textSize = if (document.synced) 20.sp else 19.sp,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = Color(0xFFA8A6BC),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
                            onSeek = if (document.synced) {
                                { position -> playerViewModel.seekTo(position) }
                            } else {
                                null
                            },
                        )
                    }
                }
                LyricsLoadState.NOT_FOUND, LyricsLoadState.OFFLINE, LyricsLoadState.ERROR -> {
                    Column(Modifier.fillMaxSize()) {
                        TetherEmptyState(
                            title = when (lyricsState.state) {
                                LyricsLoadState.OFFLINE -> "Lyrics unavailable offline"
                                LyricsLoadState.ERROR -> "Lyrics lookup failed"
                                else -> "No lyrics found"
                            },
                            body = lyricsState.message ?: "Tether could not find lyrics for this track.",
                            icon = Icons.Rounded.Subtitles,
                            showMark = false,
                            actionLabel = "Try again",
                            onAction = { reload++ },
                        )
                        TextButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Import an .lrc or .txt file") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsHeader(document: LyricsDocument) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TetherBadge(
                if (document.synced) "SYNCED" else "PLAIN TEXT",
                color = if (document.synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Text(
                document.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            document.attribution,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
