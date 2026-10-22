package dev.tether.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherWordmark
import dev.tether.core.model.Track
import dev.tether.core.model.formatDuration

data class TvNavEntry(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

val TvNavEntries = listOf(
    TvNavEntry("home", "Home", Icons.Rounded.Home),
    TvNavEntry("search", "Search", Icons.Rounded.Search),
    TvNavEntry("library", "Library", Icons.Rounded.LibraryMusic),
    TvNavEntry("playlists", "Playlists", Icons.Rounded.QueueMusic),
    TvNavEntry("downloads", "Downloads", Icons.Rounded.Download),
    TvNavEntry("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    scaleOnFocus: Float = 1.03f,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    focusedBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusBorderColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = tween(120),
        label = "tvFocusScale",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(if (focused) focusedBackgroundColor else backgroundColor)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) focusBorderColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                shape = shape,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {    TvFocusable(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        focusRequester = focusRequester,
        backgroundColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        focusedBackgroundColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        focusBorderColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        contentDescription = text,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun TvPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        focusRequester = focusRequester,
        backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        focusedBackgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun TvScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 46.sp),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(20.dp))
            trailing()
        }
    }
}

@Composable
fun TvSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        if (action != null && onAction != null) {
            TvButton(text = action, onClick = onAction)
        }
    }
}

@Composable
fun TvTrackCard(
    track: Track,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.width(190.dp).height(252.dp),
        focusRequester = focusRequester,
        contentDescription = "${track.displayTitle} by ${track.displayArtist}",
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                TetherCover(
                    artworkUri = track.artworkUri,
                    seed = track.albumKey,
                    title = track.displayAlbum,
                    modifier = Modifier.size(174.dp),
                    cornerRadius = 10.dp,
                    initialsSize = 38.sp,
                )
                if (current) {
                    Box(
                        Modifier
                            .padding(7.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TvAlbumCard(
    artworkUri: String?,
    title: String,
    subtitle: String,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.width(190.dp).height(250.dp),
        focusRequester = focusRequester,
        contentDescription = "$title, $subtitle",
    ) {
        Column(Modifier.padding(8.dp)) {
            TetherCover(
                artworkUri = artworkUri,
                seed = seed,
                title = title,
                modifier = Modifier.size(174.dp),
                cornerRadius = 10.dp,
                initialsSize = 38.sp,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TvPlaylistCard(
    artworkUri: String?,
    name: String,
    trackCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvAlbumCard(
        artworkUri = artworkUri,
        title = name,
        subtitle = "$trackCount track${if (trackCount == 1) "" else "s"}",
        seed = "playlist:$name",
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
    )
}

@Composable
fun TvTrackRow(
    track: Track,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.01f,
        backgroundColor = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        focusRequester = focusRequester,
        contentDescription = "${track.displayTitle} by ${track.displayArtist}",
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey,
                title = track.displayAlbum,
                modifier = Modifier.size(50.dp),
                cornerRadius = 7.dp,
                initialsSize = 18.sp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track.origin == dev.tether.core.model.TrackOrigin.DOWNLOADED) {
                        Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    "${track.displayArtist}  ·  ${track.displayAlbum}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TvEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TetherMark(58.dp)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(520.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun TvSidebar(
    selectedKey: String,
    nowPlaying: Track?,
    isPlaying: Boolean,
    onSelect: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    Column(
        Modifier
            .width(226.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            .padding(horizontal = 18.dp, vertical = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TetherMark(42.dp)
            Spacer(Modifier.width(10.dp))
            TetherWordmark(titleSize = 21, showTagline = false)
        }
        Spacer(Modifier.height(34.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TvNavEntries.forEach { entry ->
                TvSidebarItem(
                    entry = entry,
                    selected = selectedKey == entry.key,
                    onClick = { onSelect(entry.key) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (nowPlaying != null) {
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
            TvFocusable(
                onClick = onOpenNowPlaying,
                modifier = Modifier.fillMaxWidth().height(78.dp),
                shape = RoundedCornerShape(13.dp),
                contentDescription = "Open now playing, ${nowPlaying.displayTitle}",
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TetherCover(
                        artworkUri = nowPlaying.artworkUri,
                        seed = nowPlaying.albumKey,
                        title = nowPlaying.displayAlbum,
                        modifier = Modifier.size(58.dp),
                        cornerRadius = 8.dp,
                        initialsSize = 20.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            nowPlaying.displayTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            nowPlaying.displayArtist,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (isPlaying) "Playing" else "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Tether TV ${BuildConfig.VERSION_NAME}\nLocal-first · No account",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun TvSidebarItem(
    entry: TvNavEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(11.dp),
        scaleOnFocus = 1.02f,
        backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        contentDescription = entry.label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                entry.label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun TvCoverFrame(
    artworkUri: String?,
    seed: String,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    TetherCover(
        artworkUri = artworkUri,
        seed = seed,
        title = title,
        modifier = modifier.size(size),
        cornerRadius = 18.dp,
        initialsSize = 56.sp,
    )
}
