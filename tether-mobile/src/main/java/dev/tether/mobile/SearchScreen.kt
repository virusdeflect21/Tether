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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherEmptyState
import dev.tether.core.design.TetherIconBubble
import dev.tether.core.design.TetherSectionHeader
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.Playlist
import dev.tether.core.model.ProviderRelease
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.Track
import dev.tether.core.player.PlaybackState

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    mobileViewModel: MobileViewModel,
    playerState: PlaybackState,
    loadRelease: suspend (ProviderRelease) -> List<ProviderTrack>,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val recent by searchViewModel.recentSearches.collectAsStateWithLifecycle()
    val currentId = playerState.currentTrack?.id
    var selectedRelease by remember { mutableStateOf<ProviderRelease?>(null) }
    val remoteTracks = remember(state.results.remoteTracks) { state.results.remoteTracks.map { it.toTrack() } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TetherIconBubble(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(8.dp))
            SearchField(
                value = state.query,
                onValueChange = searchViewModel::setQuery,
                onSearch = searchViewModel::submit,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            if (state.query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TetherSectionHeader("Recent searches", modifier = Modifier.weight(1f))
                            TextButton(onClick = searchViewModel::clearRecent) { Text("Clear") }
                        }
                    }
                    items(recent, key = { it.query }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { searchViewModel.chooseRecent(item.query) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Text(
                                item.query,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { searchViewModel.removeRecent(item.query) }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove recent search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Box(
                        if (recent.isEmpty()) Modifier.fillParentMaxSize() else Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TetherEmptyState(
                            title = "Search everything",
                            body = "Find music in your library, then explore Creative Commons tracks from ccMixter.",
                            icon = Icons.Rounded.Search,
                            showMark = false,
                        )
                    }
                }
            } else {
                if (state.searchingLocal && state.results.localTracks.isEmpty() && state.results.remoteTracks.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(strokeWidth = 2.dp) }
                    }
                }
                val hasLocal = state.results.hasLocal
                val hasRemote = state.results.remoteTracks.isNotEmpty() || state.results.remoteReleases.isNotEmpty()
                if (hasLocal) {
                    if (state.results.localTracks.isNotEmpty()) {
                        item { SearchHeader("In your library", "${state.results.localTracks.size} track${if (state.results.localTracks.size == 1) "" else "s"}") }
                        items(state.results.localTracks, key = { "local-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                onClick = {
                                    val queue = state.results.localTracks
                                    onPlayQueue(queue, queue.indexOf(track), false)
                                },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (state.results.albums.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 8.dp)) {
                                TetherSectionHeader("Albums", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(state.results.albums, key = { "album-${it.key}" }) { album ->
                                        AlbumCard(album, onClick = { onOpenAlbum(album) })
                                    }
                                }
                            }
                        }
                    }
                    if (state.results.artists.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 8.dp)) {
                                TetherSectionHeader("Artists", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(state.results.artists, key = { "artist-${it.key}" }) { artist ->
                                        ArtistCard(artist, onClick = { onOpenArtist(artist) })
                                    }
                                }
                            }
                        }
                    }
                    if (state.results.playlists.isNotEmpty()) {
                        item { SearchHeader("Playlists", null) }
                        items(state.results.playlists, key = { "playlist-${it.id}" }) { playlist ->
                            PlaylistSearchRow(playlist, onClick = { onOpenPlaylist(playlist.id) })
                        }
                    }
                }
                if (state.searchingOnline) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Checking ccMixter for openly licensed music\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (hasRemote) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TetherSectionHeader("From ccMixter", modifier = Modifier.weight(1f))
                            TetherBadge("Online")
                        }
                    }
                    if (state.results.remoteTracks.isNotEmpty()) {
                        itemsIndexed(state.results.remoteTracks, key = { _, track -> "remote-${track.id}" }) { index, providerTrack ->
                            val track = providerTrack.toTrack()
                            TrackRow(
                                track = track,
                                onClick = { onPlayQueue(remoteTracks, index, false) },
                                onMenuClick = { onTrackMenu(track) },
                                isCurrent = currentId == track.id,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (state.results.remoteReleases.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.results.remoteReleases, key = { "release-${it.providerId}-${it.id}" }) { release ->
                                    ExploreReleaseCard(release, onClick = { selectedRelease = release })
                                }
                            }
                        }
                    }
                }
                state.results.providerNote?.let { note ->
                    item {
                        dev.tether.core.design.TetherInfoBanner(
                            note,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
                if (!hasLocal && !hasRemote && !state.searchingLocal && !state.searchingOnline) {
                    item {
                        TetherEmptyState(
                            title = "No matches yet",
                            body = if (state.results.searchedOnline) {
                                "Nothing matched in your library or from ccMixter. Try a different spelling or a broader phrase."
                            } else {
                                "Nothing matched in your library. Turn on online discovery in Settings to check ccMixter too."
                            },
                            icon = Icons.Rounded.Search,
                            showMark = false,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    dev.tether.core.design.TetherInfoBanner(
                        "Online results are catalog entries, not downloads. Playback starts only after the provider supplies a stream, and downloads are offered only when the declared license permits them.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
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
            onDownload = { mobileViewModel.download(it) },
        )
    }
}

@Composable
private fun SearchHeader(title: String, subtitle: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TetherSectionHeader(title, modifier = Modifier.weight(1f), subtitle = subtitle)
    }
}

@Composable
private fun PlaylistSearchRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                playlist.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
