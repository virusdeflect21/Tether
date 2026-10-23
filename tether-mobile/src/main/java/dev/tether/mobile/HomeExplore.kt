package dev.tether.mobile

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherBackdrop
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherGlass
import dev.tether.core.design.TetherIconBubble
import dev.tether.core.design.TetherInfoBanner
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherOutlineButton
import dev.tether.core.design.TetherPrimaryButton
import dev.tether.core.design.TetherSectionHeader
import dev.tether.core.design.TetherWordmark
import dev.tether.core.design.TetherInk
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.Playlist
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.Track
import dev.tether.core.model.formatDurationLong
import dev.tether.core.player.PlaybackState
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: MobileViewModel,
    playerState: PlaybackState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenCollection: (CollectionKind, String, String, String?) -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val recentAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val currentId = playerState.currentTrack?.id
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val hero = recent.firstOrNull() ?: recentAdded.firstOrNull() ?: tracks.firstOrNull()
    val heroLabel = when {
        hero != null && recent.any { it.id == hero.id } -> "RESUME LISTENING"
        hero != null -> "NEW IN YOUR LIBRARY"
        else -> "WELCOME TO TETHER"
    }
    val heroQueue = when {
        hero == null -> emptyList()
        recent.any { it.id == hero.id } -> recent
        recentAdded.any { it.id == hero.id } -> recentAdded
        else -> tracks
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TetherMark(34.dp, cornerRadius = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    TetherWordmark(titleSize = 18, showTagline = false)
                    Spacer(Modifier.weight(1f))
                    TetherIconBubble(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search",
                        onClick = onOpenSearch,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    TetherIconBubble(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        size = 40.dp,
                    )
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                    Text(
                        greeting.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tracks.isEmpty()) "Bring your\nmusic." else "Your music,\nuntethered.",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tracks.isEmpty()) {
                            "A quiet, local-first home for everything you listen to."
                        } else {
                            "Local files, playlists, and independent music."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scanning) {
                    TetherGlass(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Scanning your library\u2026",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            if (hero != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(268.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .clickable {
                            onPlayQueue(
                                heroQueue.ifEmpty { listOf(hero) },
                                heroQueue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0),
                                false,
                            )
                        },
                ) {
                    TetherCover(
                        artworkUri = hero.artworkUri,
                        seed = hero.albumKey + hero.artist,
                        title = hero.displayAlbum,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 30.dp,
                        initialsSize = 58.sp,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.06f),
                                        TetherInk.copy(alpha = 0.20f),
                                        TetherInk.copy(alpha = 0.94f),
                                    ),
                                ),
                            ),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TetherBadge(heroLabel, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        TetherGlass(
                            shape = CircleShape,
                            tone = Color.Black.copy(alpha = 0.24f),
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "ON THIS DEVICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.86f),
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, end = 94.dp, bottom = 20.dp),
                    ) {
                        Text(
                            hero.displayTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            hero.displayArtist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.76f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.34f), CircleShape)
                            .clickable {
                                onPlayQueue(
                                    heroQueue.ifEmpty { listOf(hero) },
                                    heroQueue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0),
                                    false,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            } else {
                TetherGlass(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(30.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TetherMark(50.dp, cornerRadius = 15.dp)
                            Spacer(Modifier.width(13.dp))
                            Column {
                                Text(
                                    "YOUR LIBRARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.8.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "Start with what you have.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Add a music folder, import a few files, or let Tether find the audio already on this device. Nothing is uploaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        TetherPrimaryButton(
                            "Add a music folder",
                            onAddFolder,
                            icon = Icons.Rounded.Folder,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(9.dp))
                        TetherOutlineButton(
                            "Import audio files",
                            onImportFiles,
                            icon = Icons.Rounded.Add,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!hasAudioPermission) {
                            Spacer(Modifier.height(9.dp))
                            TetherOutlineButton(
                                "Allow music access",
                                onRequestPermission,
                                icon = Icons.Rounded.Headphones,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                HomeShortcut(
                    icon = Icons.Rounded.Shuffle,
                    label = "Shuffle",
                    onClick = {
                        if (tracks.isNotEmpty()) onPlayQueue(tracks.shuffled(), 0, true)
                    },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
                HomeShortcut(
                    icon = Icons.Rounded.Favorite,
                    label = "Favorites",
                    onClick = {
                        onOpenCollection(CollectionKind.FAVORITES, "favorites", "Favorites", null)
                    },
                    enabled = favorites.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
                HomeShortcut(
                    icon = Icons.Rounded.Download,
                    label = "Offline",
                    onClick = onOpenDownloads,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
                HomeShortcut(
                    icon = Icons.Rounded.Refresh,
                    label = "Rescan",
                    onClick = {
                        if (hasAudioPermission) viewModel.scanMediaStore() else onRequestPermission()
                    },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionContent(
                    title = "Recently played",
                    actionLabel = "See all",
                    onAction = { onOpenCollection(CollectionKind.RECENT, "recent", "Recently played", null) },
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(recent.take(18), key = { "recent-${it.id}" }) { track ->
                            TrackCard(
                                track = track,
                                onClick = { onPlayQueue(recent, recent.indexOf(track), false) },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                            )
                        }
                    }
                }
            }
        }

        if (recentAdded.isNotEmpty()) {
            item {
                SectionContent(title = "Recently added") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(recentAdded.take(18), key = { "new-${it.id}" }) { track ->
                            TrackCard(
                                track = track,
                                onClick = { onPlayQueue(recentAdded, recentAdded.indexOf(track), false) },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                            )
                        }
                    }
                }
            }
        }

        if (settings.showPlaybackStats && stats.trackCount > 0) {
            item {
                StatsStrip(stats, Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                SectionContent(
                    title = "Playlists",
                    actionLabel = "All",
                    onAction = onOpenPlaylists,
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(playlists.take(12), key = { "playlist-${it.id}" }) { playlist ->
                            PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist.id) })
                        }
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                SectionContent(
                    title = "Favorites",
                    actionLabel = "See all",
                    onAction = { onOpenCollection(CollectionKind.FAVORITES, "favorites", "Favorites", null) },
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(favorites.take(18), key = { "fav-${it.id}" }) { track ->
                            TrackCard(
                                track = track,
                                onClick = { onPlayQueue(favorites, favorites.indexOf(track), false) },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                            )
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                SectionContent(
                    title = "Albums",
                    actionLabel = "Library",
                    onAction = { onOpenCollection(CollectionKind.ALBUM, "all", "Albums", null) },
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(albums.take(16), key = { "album-${it.key}" }) { album ->
                            AlbumCard(album, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
            }
        }

        if (artists.isNotEmpty()) {
            item {
                SectionContent(title = "Artists") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(artists.take(16), key = { "artist-${it.key}" }) { artist ->
                            ArtistCard(artist, onClick = { onOpenArtist(artist) })
                        }
                    }
                }
            }
        }

        if (tracks.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TetherMark(26.dp, cornerRadius = 8.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Local-first. No account. Nothing leaves this device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionContent(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 18.dp, bottom = 7.dp)) {
        TetherSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
            actionLabel = actionLabel,
            onAction = onAction,
        )
        content()
    }
}

@Composable
private fun HomeShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    TetherGlass(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier
                    .size(35.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                ),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatsStrip(stats: LibraryStats, modifier: Modifier = Modifier) {
    TetherGlass(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(vertical = 15.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(stats.trackCount.toString(), "tracks", Modifier.weight(1f))
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            )
            Stat(formatDurationLong(stats.totalDurationMs), "of music", Modifier.weight(1f))
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            )
            Stat(stats.offlineBytes.let { dev.tether.core.model.formatBytes(it) }, "offline", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExploreScreen(
    exploreViewModel: ExploreViewModel,
    loadRelease: suspend (ProviderRelease) -> List<ProviderTrack>,
    onOpenSettings: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
) {
    val state by exploreViewModel.state.collectAsStateWithLifecycle()
    var selectedRelease by remember { mutableStateOf<ProviderRelease?>(null) }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Explore",
            subtitle = "Music and video, straight from YouTube",
            actions = {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TetherIconBubble(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        onClick = exploreViewModel::refresh,
                        size = 40.dp,
                    )
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)) {
                    Text(
                        "TRENDING NOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Find something\nto play.",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Charts, music and gaming picks pulled live from YouTube.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !state.onlineEnabled -> item {
                    TetherEmptyState(
                        title = "Online discovery is off",
                        body = "Turn it on in Settings to browse YouTube. Your local library is unaffected.",
                        actionLabel = "Open settings",
                        onAction = onOpenSettings,
                        icon = Icons.Rounded.WifiOff,
                        showMark = false,
                    )
                }
                state.offline -> item {
                    TetherEmptyState(
                        title = "You're offline",
                        body = "Explore needs a connection. Your library, downloads and imported lyrics still work offline.",
                        actionLabel = "Try again",
                        onAction = exploreViewModel::refresh,
                        icon = Icons.Rounded.WifiOff,
                        showMark = false,
                    )
                }
                state.loading && state.sections.isEmpty() -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(
                                "Loading YouTube\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.sections.isEmpty() -> item {
                    TetherEmptyState(
                        title = "Nothing to explore right now",
                        body = state.error ?: "The provider returned no sections. Try refreshing in a moment.",
                        actionLabel = "Refresh",
                        onAction = exploreViewModel::refresh,
                        icon = Icons.Rounded.CloudDownload,
                        showMark = false,
                    )
                }
                else -> {
                    state.sections.forEach { section ->
                        item(key = "header-${section.title}") {
                            Column(Modifier.padding(top = 18.dp)) {
                                TetherSectionHeader(
                                    title = section.title,
                                    subtitle = section.subtitle,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(section.releases, key = { "${it.providerId}-${it.id}" }) { release ->
                                        ExploreReleaseCard(release, onClick = { selectedRelease = release })
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Music, video and artwork from YouTube.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                        )
                    }
                }
            }
        }
    }

    selectedRelease?.let { release ->
        ProviderReleaseSheet(
            release = release,
            loadTracks = loadRelease,
            onDismiss = { selectedRelease = null },
            onPlay = { tracks, index ->
                selectedRelease = null
                onPlayQueue(tracks, index, false)
            },
            onTrackMenu = onTrackMenu,
            onDownload = onDownloadTrack,
        )
    }
}

@Composable
fun ExploreReleaseCard(release: ProviderRelease, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            TetherCover(
                artworkUri = release.artworkUri,
                seed = "${release.providerId}:${release.id}:${release.title}",
                title = release.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                cornerRadius = 20.dp,
                initialsSize = 30.sp,
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp),
            ) {
                TetherBadge(release.providerId.replaceFirstChar { it.uppercase() }, color = Color.White)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            release.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            release.creator.ifBlank { "YouTube" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OnboardingScreen(
    onAllowAccess: () -> Unit,
    onChooseFolder: () -> Unit,
    onSkip: () -> Unit,
) {
    TetherBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TetherMark(44.dp, cornerRadius = 13.dp)
                    Spacer(Modifier.width(11.dp))
                    TetherWordmark(titleSize = 22)
                    Spacer(Modifier.weight(1f))
                    TetherGlass(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onSkip),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "A LOCAL-FIRST MUSIC PLAYER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    "Music,\nuntethered.",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Play the music you own. Discover independent artists. No account, no ads.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                OnboardingFeature(
                    icon = Icons.Rounded.Headphones,
                    title = "Your library first",
                    body = "Play files on this device or add folders with the system picker.",
                )
                OnboardingFeature(
                    icon = Icons.Rounded.Search,
                    title = "Real providers only",
                    body = "Music and video from YouTube and lyrics from LRCLIB.",
                )
                OnboardingFeature(
                    icon = Icons.Rounded.Shield,
                    title = "Private by design",
                    body = "No login, no advertising ID, and no listening history sent anywhere.",
                )
            }
            Column(
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TetherPrimaryButton(
                    text = "Allow music access",
                    onClick = onAllowAccess,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.MusicNote,
                )
                TetherOutlineButton(
                    text = "Choose a folder instead",
                    onClick = onChooseFolder,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Folder,
                )
                Text(
                    "You can change this later in Settings. Tether never uploads your library.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, body: String) {
    TetherGlass(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(15.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
