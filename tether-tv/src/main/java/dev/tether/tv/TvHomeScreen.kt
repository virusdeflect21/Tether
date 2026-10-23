package dev.tether.tv

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherCover
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.Track
import dev.tether.core.player.PlaybackState
import java.util.Calendar

@Composable
fun TvHomeScreen(
    viewModel: TvViewModel,
    playerState: PlaybackState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val recent by viewModel.recentTracks.collectAsStateWithLifecycle()
    val added by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()

    val heroFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val heroTrack = playerState.currentTrack ?: recent.firstOrNull() ?: added.firstOrNull() ?: tracks.firstOrNull()
    val heroQueue = remember(recent, added, tracks, heroTrack) {
        (if (recent.isNotEmpty()) recent else added.ifEmpty { tracks }).distinctBy { it.id }
    }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    LaunchedEffect(tracks.isEmpty(), hasAudioPermission) {
        runCatching {
            if (tracks.isNotEmpty()) heroFocus.requestFocus() else emptyFocus.requestFocus()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 38.dp, end = 38.dp, top = 30.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (stats.trackCount > 0) {
                        Text(
                            "${stats.trackCount} tracks · ${stats.albumCount} albums",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    "Your music, on your screen.",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, lineHeight = 44.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            if (heroTrack != null) {
                TvHero(
                    track = heroTrack,
                    playing = playerState.isPlaying && playerState.currentTrack?.id == heroTrack.id,
                    focusRequester = heroFocus,
                    onPlay = {
                        if (playerState.currentTrack?.id == heroTrack.id) {
                            onPlayQueue(heroQueue, heroQueue.indexOfFirst { it.id == heroTrack.id }.coerceAtLeast(0), false)
                        } else {
                            onPlayQueue(heroQueue, heroQueue.indexOfFirst { it.id == heroTrack.id }.coerceAtLeast(0), false)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Shuffle library", { onPlayQueue(tracks, 0, true) }, icon = Icons.Rounded.Shuffle)
                    TvButton("Library", onOpenLibrary, icon = Icons.Rounded.LibraryMusic)
                    TvButton("Playlists", onOpenPlaylists, icon = Icons.Rounded.PlayArrow)
                }
            } else {
                TvEmptyState(
                    title = if (hasAudioPermission) "Your library is waiting" else "Bring your own music",
                    body = if (hasAudioPermission) {
                        "Tether did not find audio yet. Rescan your device, add a folder with the system picker, or import a few files. Nothing is uploaded."
                    } else {
                        "Tether plays audio already on this device. Allow media access to scan it, or choose a folder manually. No account is required."
                    },
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (hasAudioPermission) {
                                TvButton(
                                    if (scanning) "Scanning…" else "Rescan device",
                                    onClick = viewModel::scanMediaStore,
                                    icon = Icons.Rounded.Refresh,
                                    primary = true,
                                    focusRequester = emptyFocus,
                                )
                            } else {
                                TvButton(
                                    "Allow music access",
                                    onRequestPermission,
                                    icon = Icons.Rounded.LibraryMusic,
                                    primary = true,
                                    focusRequester = emptyFocus,
                                )
                            }
                            TvButton("Add folder", onAddFolder, icon = Icons.Rounded.Folder)
                            TvButton("Import files", onImportFiles, icon = Icons.Rounded.Add)
                        }
                    },
                )
            }
        }

        if (scanning) {
            item {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Text("Scanning audio on this device…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { TvSectionTitle("Keep listening") }
            item {
                TvTrackRowList(
                    tracks = recent,
                    currentId = playerState.currentTrack?.id,
                    onPlay = { index -> onPlayQueue(recent, index, false) },
                )
            }
        }

        if (added.isNotEmpty()) {
            item { TvSectionTitle("Recently added") }
            item {
                TvTrackCardRow(
                    tracks = added.take(12),
                    currentId = playerState.currentTrack?.id,
                    onPlay = { index -> onPlayQueue(added, index, false) },
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item { TvSectionTitle("Favorites") }
            item {
                TvTrackCardRow(
                    tracks = favorites.take(12),
                    currentId = playerState.currentTrack?.id,
                    onPlay = { index -> onPlayQueue(favorites, index, false) },
                )
            }
        }

        if (albums.isNotEmpty()) {
            item { TvSectionTitle("Albums") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(albums, key = { it.key }) { album ->
                        TvAlbumCard(
                            artworkUri = album.artworkUri,
                            title = album.title,
                            subtitle = album.artist.ifBlank { "${album.trackCount} tracks" },
                            seed = album.key,
                            onClick = { onOpenAlbum(album) },
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item { TvSectionTitle("Playlists", action = "All playlists", onAction = onOpenPlaylists) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        TvPlaylistCard(
                            artworkUri = playlist.coverArtUri,
                            name = playlist.name,
                            trackCount = playlist.trackCount,
                            onClick = { onOpenPlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHero(
    track: Track,
    playing: Boolean,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
) {
    TvFocusable(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(236.dp),
        shape = RoundedCornerShape(22.dp),
        focusRequester = focusRequester,
        backgroundColor = MaterialTheme.colorScheme.surface,
        focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        contentDescription = "Play ${track.displayTitle}",
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TetherCover(
                    artworkUri = track.artworkUri,
                    seed = track.albumKey,
                    title = track.displayAlbum,
                    modifier = Modifier.size(200.dp),
                    cornerRadius = 14.dp,
                    initialsSize = 52.sp,
                )
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playing) "NOW PLAYING" else "CONTINUE LISTENING",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.4.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.displayArtist,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            if (playing) "Press OK to restart this queue" else "Press OK to play",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTrackCardRow(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TvTrackCard(
                track = track,
                current = track.id == currentId,
                onClick = { onPlay(index) },
            )
        }
    }
}

@Composable
private fun TvTrackRowList(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tracks.take(6).forEachIndexed { index, track ->
            TvTrackRow(
                track = track,
                current = track.id == currentId,
                onClick = { onPlay(index) },
            )
        }
    }
}

private enum class LibraryTab(val label: String) {
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FAVORITES("Favorites"),
}

@Composable
fun TvLibraryScreen(
    viewModel: TvViewModel,
    playerState: PlaybackState,
    hasAudioPermission: Boolean,
    onOpenSearch: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.TRACKS) }
    val tabFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { tabFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 38.dp, end = 38.dp, top = 30.dp)) {
        TvScreenHeader(
            title = "Library",
            subtitle = "${tracks.size} tracks · ${albums.size} albums · ${artists.size} artists",
            trailing = { TvButton("Search", onOpenSearch, icon = Icons.Rounded.Search) },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            LibraryTab.entries.forEachIndexed { index, entry ->
                TvPill(
                    text = entry.label,
                    selected = tab == entry,
                    onClick = { tab = entry },
                    focusRequester = if (index == 0) tabFocus else null,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !hasAudioPermission && tracks.isEmpty() -> {
                TvEmptyState(
                    title = "Library access needed",
                    body = "Allow media access to index audio on this device, or add a folder with the system picker. Tether never uploads files.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvButton("Allow access", onRequestPermission, icon = Icons.Rounded.LibraryMusic, primary = true)
                            TvButton("Add folder", onAddFolder, icon = Icons.Rounded.Folder)
                            TvButton("Import files", onImportFiles, icon = Icons.Rounded.Add)
                        }
                    },
                )
            }
            tracks.isEmpty() -> {
                TvEmptyState(
                    title = "No tracks yet",
                    body = "Rescan your device, add a music folder, or import individual files. Downloads from online providers also appear here.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvButton("Rescan", viewModel::scanMediaStore, icon = Icons.Rounded.Refresh, primary = true)
                            TvButton("Add folder", onAddFolder, icon = Icons.Rounded.Folder)
                            TvButton("Import files", onImportFiles, icon = Icons.Rounded.Add)
                        }
                    },
                )
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        LibraryTab.TRACKS -> TvTrackList(
                            tracks = tracks,
                            currentId = playerState.currentTrack?.id,
                            onPlay = { index -> onPlayQueue(tracks, index, false) },
                        )
                        LibraryTab.ALBUMS -> TvAlbumGrid(albums = albums, onOpen = onOpenAlbum)
                        LibraryTab.ARTISTS -> TvArtistGrid(artists = artists, onOpen = onOpenArtist)
                        LibraryTab.FAVORITES -> {
                            if (favorites.isEmpty()) {
                                TvEmptyState(
                                    title = "No favorites yet",
                                    body = "Add tracks to favorites from Now Playing.",
                                )
                            } else {
                                TvTrackList(
                                    tracks = favorites,
                                    currentId = playerState.currentTrack?.id,
                                    onPlay = { index -> onPlayQueue(favorites, index, false) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTrackList(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TvTrackRow(
                track = track,
                current = track.id == currentId,
                onClick = { onPlay(index) },
            )
        }
    }
}

@Composable
private fun TvAlbumGrid(
    albums: List<AlbumSummary>,
    onOpen: (AlbumSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { album ->
                    TvAlbumCard(
                        artworkUri = album.artworkUri,
                        title = album.title,
                        subtitle = album.artist.ifBlank { "${album.trackCount} tracks" },
                        seed = album.key,
                        onClick = { onOpen(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvArtistGrid(
    artists: List<ArtistSummary>,
    onOpen: (ArtistSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(artists.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { artist ->
                    TvAlbumCard(
                        artworkUri = artist.artworkUri,
                        title = artist.name,
                        subtitle = "${artist.albumCount} albums · ${artist.trackCount} tracks",
                        seed = artist.key,
                        onClick = { onOpen(artist) },
                    )
                }
            }
        }
    }
}

@Composable
fun TvCollectionScreen(
    title: String,
    subtitle: String?,
    artworkUri: String?,
    seed: String,
    tracks: List<Track>,
    playerState: PlaybackState,
    onBack: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(tracks.size) {
        runCatching { playFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("Back", onBack)
            Spacer(Modifier.width(20.dp))
            TvScreenHeader(
                title = title,
                subtitle = subtitle ?: "${tracks.size} tracks",
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TetherCover(
                artworkUri = artworkUri,
                seed = seed,
                title = title,
                modifier = Modifier.size(132.dp),
                cornerRadius = 14.dp,
                initialsSize = 38.sp,
            )
            Spacer(Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    "Play all",
                    onClick = { onPlayQueue(tracks, 0, false) },
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    focusRequester = playFocus,
                )
                TvButton(
                    "Shuffle",
                    onClick = { onPlayQueue(tracks, 0, true) },
                    icon = Icons.Rounded.Shuffle,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (tracks.isEmpty()) {
            TvEmptyState("Nothing here yet", "This collection does not have playable tracks.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TvTrackRow(
                        track = track,
                        current = track.id == playerState.currentTrack?.id,
                        onClick = { onPlayQueue(tracks, index, false) },
                    )
                }
            }
        }
    }
}
