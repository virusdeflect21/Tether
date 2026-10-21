package dev.tether.mobile

import android.os.Build
import androidx.compose.foundation.Canvas
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
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherEmptyState
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
                    TetherMark(38.dp)
                    Spacer(Modifier.width(10.dp))
                    TetherWordmark(titleSize = 19, showTagline = false)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        if (tracks.isEmpty()) "Bring your music. Tether does the rest." else "Your music, untethered.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scanning) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
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

        item {
            Spacer(Modifier.height(10.dp))
            if (hero != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(224.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            onPlayQueue(heroQueue.ifEmpty { listOf(hero) }, heroQueue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0), false)
                        },
                ) {
                    TetherCover(
                        artworkUri = hero.artworkUri,
                        seed = hero.albumKey + hero.artist,
                        title = hero.displayAlbum,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 24.dp,
                        initialsSize = 58.sp,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        TetherInk.copy(alpha = 0.28f),
                                        TetherInk.copy(alpha = 0.92f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                    ) {
                        TetherBadge(heroLabel, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            hero.displayTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                            .padding(18.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                onPlayQueue(heroQueue.ifEmpty { listOf(hero) }, heroQueue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0), false)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant),
                            ),
                        )
                        .padding(22.dp),
                ) {
                    Column {
                        TetherMark(54.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Make Tether yours",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Add a music folder or allow access to the audio already on this device. Nothing is uploaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TetherPrimaryButton("Choose a folder", onAddFolder, icon = Icons.Rounded.Folder)
                            TetherOutlineButton("Import files", onImportFiles, icon = Icons.Rounded.Add)
                        }
                        if (!hasAudioPermission) {
                            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    HomeShortcut(
                        icon = Icons.Rounded.Shuffle,
                        label = "Shuffle all",
                        onClick = {
                            if (tracks.isNotEmpty()) onPlayQueue(tracks.shuffled(), 0, true)
                        },
                        enabled = tracks.isNotEmpty(),
                    )
                }
                item {
                    HomeShortcut(
                        icon = Icons.Rounded.Favorite,
                        label = "Favorites",
                        onClick = {
                            onOpenCollection(CollectionKind.FAVORITES, "favorites", "Favorites", null)
                        },
                        enabled = favorites.isNotEmpty(),
                    )
                }
                item {
                    HomeShortcut(
                        icon = Icons.Rounded.Download,
                        label = "Offline",
                        onClick = onOpenDownloads,
                        enabled = true,
                    )
                }
                item {
                    HomeShortcut(
                        icon = Icons.Rounded.Refresh,
                        label = "Rescan",
                        onClick = {
                            if (hasAudioPermission) viewModel.scanMediaStore() else onRequestPermission()
                        },
                        enabled = true,
                    )
                }
            }
        }

        if (settings.showPlaybackStats && stats.trackCount > 0) {
            item {
                StatsStrip(stats, Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
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
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    TetherInfoBanner(
                        "Local files and completed downloads play offline. Streamed tracks and lyrics depend on their providers.",
                        tone = MaterialTheme.colorScheme.secondary,
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
    Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        TetherSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant,
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
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatsStrip(stats: LibraryStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(stats.trackCount.toString(), "tracks", Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Stat(formatDurationLong(stats.totalDurationMs), "of music", Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Stat(stats.offlineBytes.let { dev.tether.core.model.formatBytes(it) }, "offline", Modifier.weight(1f))
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
            subtitle = "Independent, openly licensed music",
            actions = {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = exploreViewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    TetherInfoBanner(
                        "Explore only shows music a provider actually returned. Tether does not invent artists, streams or download permissions.",
                        tone = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            when {
                !state.onlineEnabled -> item {
                    TetherEmptyState(
                        title = "Online discovery is off",
                        body = "Turn it on in Settings to browse Creative Commons music from ccMixter. Your local library is unaffected.",
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
                                "Asking ccMixter for real tracks\u2026",
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
                        Column(Modifier.padding(20.dp)) {
                            TetherInfoBanner(
                                "Music, artwork and license data come from ccMixter. Downloads are offered only when the release declares a license that permits them.",
                            )
                        }
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
            .width(172.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
    ) {
        Box {
            TetherCover(
                artworkUri = release.artworkUri,
                seed = "${release.providerId}:${release.id}:${release.title}",
                title = release.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.18f),
                cornerRadius = 17.dp,
                initialsSize = 32.sp,
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                TetherBadge("ccMixter", color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Text(
                release.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                release.creator.ifBlank { "Unknown creator" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onAllowAccess: () -> Unit,
    onChooseFolder: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TetherInk, Color(0xFF14102B), TetherInk),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF8FA6FF).copy(alpha = 0.17f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.14f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.85f, size.height * 0.14f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFF0A6FF).copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.72f),
                    radius = size.width * 0.6f,
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.08f, size.height * 0.72f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TetherMark(48.dp)
                Spacer(Modifier.width(12.dp))
                TetherWordmark(titleSize = 24)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Music,\nuntethered.",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFF5F3FF),
            )
            Text(
                "Tether plays the music you own and helps you discover independent artists who share their work openly. No account. No ads. No invented streams.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFA8A6BC),
            )
            Spacer(Modifier.height(2.dp))
            OnboardingFeature(
                icon = Icons.Rounded.Headphones,
                title = "Your library first",
                body = "Scan on-device audio, add folders with the system picker, or import individual files.",
            )
            OnboardingFeature(
                icon = Icons.Rounded.Search,
                title = "Real providers only",
                body = "ccMixter for Creative Commons music and LRCLIB for lyrics. If a result is missing, Tether says so.",
            )
            OnboardingFeature(
                icon = Icons.Rounded.Shield,
                title = "Private by design",
                body = "No login, no advertising ID, and no listening history sent to a server.",
            )
            Spacer(Modifier.height(8.dp))
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
                if (Build.VERSION.SDK_INT >= 33) {
                    "You can skip this and add music later. Tether can still use the system folder picker without broad media access."
                } else {
                    "You can skip this and add music later with the system folder picker."
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFA8A6BC),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC3A6FF),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF8FA6FF).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFC3D0FF), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF5F3FF))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA8A6BC))
        }
    }
}
