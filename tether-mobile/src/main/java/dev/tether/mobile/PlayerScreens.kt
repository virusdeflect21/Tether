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
import androidx.compose.material.icons.rounded.Album
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.LyricsPane
import dev.tether.core.design.TetherBackdrop
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherGlass
import dev.tether.core.design.TetherIconBubble
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

@OptIn(ExperimentalMaterial3Api::class)
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

    TetherBackdrop(
        artworkUri = track.artworkUri,
        seed = track.albumKey + track.artist,
        title = track.displayAlbum,
        modifier = Modifier.fillMaxSize(),
    ) {        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            MobileTopBar(
                title = "Now playing",
                onBack = onBack,
                actions = {
                    TetherIconBubble(
                        icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                        onClick = { viewModel.toggleFavorite(track) },
                        size = 40.dp,
                        content = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    TetherIconBubble(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        onClick = { onTrackMenu(track) },
                        size = 40.dp,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.shadow(
                        elevation = 26.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                    ),
                ) {
                    TetherCover(
                        artworkUri = track.artworkUri,
                        seed = track.albumKey + track.artist,
                        title = track.displayAlbum,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        cornerRadius = 28.dp,
                        initialsSize = 64.sp,
                    )
                    TetherBadge(
                        text = when (track.origin) {
                            dev.tether.core.model.TrackOrigin.LOCAL -> "Local"
                            dev.tether.core.model.TrackOrigin.DOWNLOADED -> "Offline"
                            dev.tether.core.model.TrackOrigin.REMOTE -> "Stream"
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        color = Color.White,
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
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.displayArtist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                TetherIconBubble(
                    icon = Icons.Rounded.Album,
                    contentDescription = "Go to album",
                    onClick = { if (track.album.isNotBlank()) onOpenAlbum(track.albumKey, track.displayAlbum, track.artworkUri) },
                    size = 42.dp,
                )
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
            TetherGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Column {
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    )
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
                        colors = sliderColors,
                        thumb = {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(5.dp),
                                colors = sliderColors,
                                drawStopIndicator = {},
                            )
                        },
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatDuration(maxDuration.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TetherGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(34.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = playerViewModel::toggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    IconButton(onClick = playerViewModel::previous, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            )
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
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                    IconButton(onClick = playerViewModel::next, enabled = state.hasNext, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = if (state.hasNext) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = playerViewModel::cycleRepeat) {
                        Icon(
                            if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerAction(
                    icon = Icons.Rounded.Subtitles,
                    label = "Lyrics",
                    onClick = onOpenLyrics,
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Queue",
                    onClick = onOpenQueue,
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Rounded.Timer,
                    label = if (state.sleepTimerActive) formatDuration(state.sleepRemainingMs) else "Sleep",
                    active = state.sleepTimerActive,
                    onClick = { showSleep = true },
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Save",
                    onClick = { showSaveQueue = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (next != null) {
                TetherGlass(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenQueue),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        TetherCover(
                            next.artworkUri,
                            next.albumKey + next.artist,
                            next.displayAlbum,
                            Modifier.size(44.dp),
                            cornerRadius = 12.dp,
                            initialsSize = 13.sp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "UP NEXT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                next.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                next.displayArtist,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
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
            supportingText = "The current queue order is kept. Provider tracks that are unavailable stay in the list.",
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
    modifier: Modifier = Modifier,
) {
    TetherGlass(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tone = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else null,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
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
                body = "Play a track or add music from a track menu to build a queue.",
                icon = Icons.Rounded.QueueMusic,
                showMark = false,
                actionLabel = "Back",
                onAction = onBack,
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp),
            ) {
                items(state.queue, key = { it.id }) { track ->
                    val index = state.queue.indexOf(track)
                    val isCurrent = index == state.currentIndex
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable { playerViewModel.seekToIndex(index) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(
                title = "Lyrics",
                subtitle = track?.displayTitle,
                onBack = onBack,
                actions = {
                    if (track != null) {
                        TetherIconBubble(
                            icon = Icons.Rounded.Upload,
                            contentDescription = "Import lyrics",
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            size = 40.dp,
                        )
                        if (lyricsState.state != LyricsLoadState.LOADING) {
                            Spacer(Modifier.width(8.dp))
                            TetherIconBubble(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = "Reload lyrics",
                                onClick = { reload++ },
                                size = 40.dp,
                            )
                        }
                    }
                },
            )
            if (track == null) {
                TetherEmptyState(
                    title = "No track playing",
                    body = "Start a track to look for lyrics. Tether only shows lyrics a provider actually returned.",
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
                                "Searching LRCLIB\u2026",
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
                        LyricsPane(
                            document = document,
                            positionMs = if (document.synced) state.positionMs else 0L,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 4.dp),
                            textSize = if (document.synced) 20.sp else 19.sp,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                LyricsLoadState.ERROR -> "Couldn't load lyrics"
                                else -> "No lyrics found"
                            },
                            body = when (lyricsState.state) {
                                LyricsLoadState.OFFLINE ->
                                    "Connect to the internet to search LRCLIB, or import a local .lrc file."
                                LyricsLoadState.ERROR ->
                                    "Tether couldn't reach LRCLIB. Try again in a moment."
                                else ->
                                    "No lyrics are available for this track yet. You can import an .lrc or .txt file."
                            },
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
