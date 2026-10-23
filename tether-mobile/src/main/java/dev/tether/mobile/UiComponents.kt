package dev.tether.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tether.core.design.PlayingBars
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherChip
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherGlass
import dev.tether.core.design.TetherIconBubble
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherPrimaryButton
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.DownloadPermission
import dev.tether.core.model.Playlist
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.Track
import dev.tether.core.model.formatDuration
import dev.tether.core.model.formatDurationLong
import dev.tether.core.player.PlaybackState
import kotlinx.coroutines.CancellationException

@Composable
fun MobileTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TetherIconBubble(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable (() -> Unit))? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: onMenuClick)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            leading()
        } else {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey + track.artist,
                title = track.displayAlbum,
                modifier = Modifier.size(52.dp),
                cornerRadius = 14.dp,
                initialsSize = 15.sp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isCurrent && isPlaying) {
                    PlayingBars(Modifier.height(12.dp), barCount = 3)
                }
                Text(
                    text = subtitle ?: buildString {
                        append(track.displayArtist)
                        if (!track.isOfflinePlayable) append(" \u00b7 ").append(track.sourceLabel)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    width: Int = 164,
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(bottom = 3.dp),
    ) {
        Box {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey + track.artist,
                title = track.displayAlbum,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                cornerRadius = 22.dp,
                initialsSize = 34.sp,
            )
            if (isCurrent && isPlaying) {
                TetherGlass(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    shape = CircleShape,
                    tone = Color.Black.copy(alpha = 0.32f),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    PlayingBars(barCount = 3)
                }
            }
            TetherGlass(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(31.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick),
                shape = CircleShape,
                tone = Color.Black.copy(alpha = 0.28f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Options for ${track.displayTitle}",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            TetherGlass(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                tone = MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${track.displayTitle}",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = track.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumCard(album: AlbumSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(164.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        TetherCover(
            artworkUri = album.artworkUri,
            seed = album.key,
            title = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 22.dp,
            initialsSize = 30.sp,
        )
        Spacer(Modifier.height(10.dp))
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
fun ArtistCard(artist: ArtistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TetherCover(
            artworkUri = artist.artworkUri,
            seed = artist.key,
            title = artist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 100.dp,
            initialsSize = 28.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${artist.trackCount} track${if (artist.trackCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(164.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        TetherCover(
            artworkUri = playlist.coverArtUri,
            seed = "playlist:${playlist.id}:${playlist.name}",
            title = playlist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 22.dp,
            initialsSize = 32.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val density = LocalDensity.current
    var dragY by remember { mutableFloatStateOf(0f) }
    val dropIn = remember { Animatable(0f) }
    LaunchedEffect(track.id) { dropIn.animateTo(1f, tween(220)) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = dropIn.value
                translationY = (1f - dropIn.value) * 24.dp.toPx() + dragY
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        TetherGlass(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(27.dp), clip = false)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragY = (dragY + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragY > with(density) { 56.dp.toPx() }) onDismiss() else dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    )
                },
            shape = RoundedCornerShape(27.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpen)
                        .padding(start = 8.dp, top = 8.dp, end = 5.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    TetherCover(
                        artworkUri = track.artworkUri,
                        seed = track.albumKey + track.artist,
                        title = track.displayAlbum,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 14.dp,
                        initialsSize = 14.sp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(track.displayArtist)
                                if (state.isBuffering) append(" \u00b7 buffering")
                                if (!track.isOfflinePlayable) append(" \u00b7 streaming")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TetherGlass(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPlayPause),
                        shape = CircleShape,
                        tone = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = if (state.hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

data class TetherNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun TetherBottomBar(items: List<TetherNavItem>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        TetherGlass(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(29.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val color by animateColorAsState(
                        if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "navColor",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = item.onClick)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (item.selected) {
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                            ),
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(21.dp))
                        }
                        Text(
                            item.label,
                            color = color,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            onSearch()
        }),
        decorationBox = { inner ->
            TetherGlass(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        if (value.isBlank()) {
                            Text(
                                "Search tracks, albums, artists\u2026",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
    if (autoFocus) {
        // The field is intentionally focusable by tap; autofocus from the caller can
        // be added later without changing search semantics.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    favorite: Boolean,
    playlists: List<Playlist>,
    downloadPermission: DownloadPermission,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onDownload: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (mode == 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { mode = 0 }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Add to playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showNewPlaylist = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "New playlist", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (playlists.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TetherPrimaryButton("Create playlist", { showNewPlaylist = true }, icon = Icons.Rounded.Add)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddToPlaylist(playlist.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TetherCover(
                                playlist.coverArtUri,
                                "playlist:${playlist.id}:${playlist.name}",
                                playlist.name,
                                Modifier.size(46.dp),
                                cornerRadius = 10.dp,
                                initialsSize = 15.sp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    TetherCover(
                        track.artworkUri,
                        track.albumKey + track.artist,
                        track.displayAlbum,
                        Modifier.size(74.dp),
                        cornerRadius = 15.dp,
                        initialsSize = 22.sp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${track.displayArtist} \u00b7 ${track.displayAlbum}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TetherBadge(if (track.isOfflinePlayable) "Offline" else "Streaming")
                            if (track.hasVideo) TetherBadge("Video", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
                ActionRow(Icons.Rounded.PlayArrow, "Play now", onClick = {
                    onPlay()
                    onDismiss()
                })
                ActionRow(Icons.Rounded.SkipNext, "Play next", onClick = {
                    onPlayNext()
                    onDismiss()
                })
                ActionRow(Icons.Rounded.QueueMusic, "Add to queue", onClick = {
                    onAddToQueue()
                    onDismiss()
                })
                ActionRow(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    iconTint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                )
                ActionRow(Icons.Rounded.PlaylistPlay, "Add to playlist", onClick = { mode = 1 })
                if (track.origin == dev.tether.core.model.TrackOrigin.REMOTE) {
                    ActionRow(
                        Icons.Rounded.CloudDownload,
                        if (downloadPermission.allowed) "Download for offline listening" else "Download unavailable",
                        subtitle = if (downloadPermission.allowed) {
                            "Only because this track's license permits it"
                        } else {
                            downloadPermission.reason
                        },
                        enabled = downloadPermission.allowed,
                        onClick = {
                            onDownload()
                            onDismiss()
                        },
                    )
                } else {
                    ActionRow(
                        Icons.Rounded.Download,
                        "Available offline",
                        subtitle = if (track.origin == dev.tether.core.model.TrackOrigin.DOWNLOADED) "Stored in Tether downloads" else "On this device",
                        enabled = false,
                        onClick = {},
                    )
                }
                if (onGoToAlbum != null && track.origin != dev.tether.core.model.TrackOrigin.REMOTE) {
                    ActionRow(Icons.Rounded.MusicNote, "Go to album", onClick = {
                        onGoToAlbum()
                        onDismiss()
                    })
                }
                if (onGoToArtist != null && track.origin != dev.tether.core.model.TrackOrigin.REMOTE) {
                    ActionRow(Icons.Rounded.MusicNote, "Go to artist", onClick = {
                        onGoToArtist()
                        onDismiss()
                    })
                }
                if (onRemoveFromPlaylist != null) {
                    ActionRow(
                        Icons.Rounded.Delete,
                        "Remove from this playlist",
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = {
                            onRemoveFromPlaylist()
                            onDismiss()
                        },
                    )
                }
                track.license?.takeIf { it.isNotBlank() }?.let { license ->
                    Text(
                        text = "License: $license",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showNewPlaylist) {
        NameDialog(
            title = "New playlist",
            label = "Playlist name",
            initial = "",
            confirmLabel = "Create and add",
            onDismiss = { showNewPlaylist = false },
            onConfirm = { name ->
                showNewPlaylist = false
                onCreatePlaylistAndAdd(name)
                onDismiss()
            },
        )
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(21.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun FilterChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.first }) { (value, label) ->
            TetherChip(
                text = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmLabel: String = "Save",
    supportingText: String? = null,
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(80) },
                label = { Text(label) },
                singleLine = true,
                supportingText = supportingText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderReleaseSheet(
    release: ProviderRelease,
    loadTracks: suspend (ProviderRelease) -> List<ProviderTrack>,
    onDismiss: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember(release.id) { mutableStateOf(true) }
    var error by remember(release.id) { mutableStateOf<String?>(null) }
    var tracks by remember(release.id) { mutableStateOf<List<ProviderTrack>>(emptyList()) }
    LaunchedEffect(release.id) {
        loading = true
        error = null
        try {
            tracks = loadTracks(release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "The provider could not load this release."
        } finally {
            loading = false
        }
    }
    val libraryTracks = remember(tracks) { tracks.map { it.toTrack() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TetherCover(
                    release.artworkUri,
                    release.providerId + release.id,
                    release.title,
                    Modifier.size(84.dp),
                    cornerRadius = 16.dp,
                    initialsSize = 24.sp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        release.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        release.creator.ifBlank { "YouTube" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TetherBadge(release.providerId)
                        release.year?.let { TetherBadge(it.toString()) }
                    }
                }
            }
            release.license?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "License: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            if (error != null) {
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            } else if (loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                if (libraryTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TetherPrimaryButton(
                            "Play release",
                            { onPlay(libraryTracks, 0) },
                            icon = Icons.Rounded.PlayArrow,
                        )
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                    itemsIndexed(libraryTracks, key = { _, track -> track.id }) { index, track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlay(libraryTracks, index) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrackRow(
                                track = track,
                                onClick = { onPlay(libraryTracks, index) },
                                onMenuClick = { onTrackMenu(track) },
                                modifier = Modifier.weight(1f),
                                trailing = {
                                    IconButton(onClick = { onDownload(track) }, modifier = Modifier.size(38.dp)) {
                                        Icon(
                                            Icons.Rounded.CloudDownload,
                                            contentDescription = "Download",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceNote(track: Track, modifier: Modifier = Modifier) {
    val text = when (track.origin) {
        dev.tether.core.model.TrackOrigin.LOCAL -> "On this device"
        dev.tether.core.model.TrackOrigin.DOWNLOADED -> "Offline copy \u00b7 ${track.sourceLabel}"
        dev.tether.core.model.TrackOrigin.REMOTE -> "Streaming from ${track.sourceLabel}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun StorageMeter(
    usedBytes: Long,
    availableBytes: Long,
    modifier: Modifier = Modifier,
) {
    val total = usedBytes + availableBytes
    val fraction = if (total > 0) (usedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Downloads: ${dev.tether.core.model.formatBytes(usedBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${dev.tether.core.model.formatBytes(availableBytes)} free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
