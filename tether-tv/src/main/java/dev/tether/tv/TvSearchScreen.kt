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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.Playlist
import dev.tether.core.model.Track
import dev.tether.core.player.PlaybackState

@Composable
fun TvSearchScreen(
    searchViewModel: TvSearchViewModel,
    playerState: PlaybackState,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val recent by searchViewModel.recentSearches.collectAsStateWithLifecycle()
    var showInput by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val resultFocus = remember { FocusRequester() }
    val remoteTracks = remember(state.remoteTracks) { state.remoteTracks.map { it.toTrack() } }

    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 28.dp)) {
        TvScreenHeader(
            title = "Search",
            subtitle = "Your library first, then openly licensed music from ccMixter.",
        )
        Spacer(Modifier.height(16.dp))
        TvFocusable(
            onClick = { showInput = true },
            modifier = Modifier.fillMaxWidth().height(62.dp),
            focusRequester = searchFocus,
            contentDescription = "Search. Current query: ${state.query.ifBlank { "none" }}",
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(
                    state.query.ifBlank { "Search tracks, albums, artists and playlists" },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = if (state.query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.query.isNotBlank()) {
                    Text(
                        "CHANGE",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.query.isBlank() && recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvSectionTitle("Recent searches")
                Spacer(Modifier.weight(1f))
                TvButton("Clear", searchViewModel::clearHistory, icon = Icons.Rounded.Clear)
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.query }) { entry ->
                    TvPill(entry.query, false, { searchViewModel.setQuery(entry.query) })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (state.loadingLocal || state.loadingRemote) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.query.isBlank()) {
                item {
                    TvEmptyState(
                        title = "Find something to play",
                        body = "Search local files or CC-licensed music. Nothing is inferred: if a provider cannot stream a track, Tether tells you.",
                    )
                }
            } else {
                if (state.local.localTracks.isNotEmpty()) {
                    item { TvSectionTitle("In your library", "${state.local.localTracks.size} tracks") }
                    itemsIndexed(state.local.localTracks, key = { _, t -> "local-${t.id}" }) { index, track ->
                        TvSearchRow(
                            track = track,
                            current = playerState.currentTrack?.id == track.id,
                            focusRequester = if (index == 0) resultFocus else null,
                            onClick = { onPlayQueue(state.local.localTracks, index, false) },
                        )
                    }
                }
                if (state.local.albums.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); TvSectionTitle("Albums") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.local.albums, key = { "album-${it.key}" }) { album ->
                                TvAlbumCard(
                                    artworkUri = album.artworkUri,
                                    title = album.title,
                                    subtitle = "${album.artist} · ${album.trackCount} tracks",
                                    seed = album.key,
                                    onClick = { onOpenAlbum(album) },
                                )
                            }
                        }
                    }
                }
                if (state.local.playlists.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); TvSectionTitle("Playlists") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.local.playlists, key = { "playlist-${it.id}" }) { playlist ->
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
                if (remoteTracks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvSectionTitle("Online · ccMixter")
                            TetherBadge("LICENSED")
                        }
                    }
                    itemsIndexed(remoteTracks, key = { _, t -> "remote-${t.id}" }) { index, track ->
                        TvSearchRow(
                            track = track,
                            current = playerState.currentTrack?.id == track.id,
                            onClick = { onPlayQueue(remoteTracks, index, false) },
                        )
                    }
                }
                state.providerNote?.let { note ->
                    item {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (state.isEmpty) {
                    item {
                        TvEmptyState(
                            title = "No matches",
                            body = if (state.searchedOnline) {
                                "Nothing matched in your library or from ccMixter. Try a different spelling."
                            } else {
                                "Nothing matched in your library. Online discovery is off or unavailable."
                            },
                        )
                    }
                }
                item {
                    Text(
                        "Online entries are catalog results. Playback starts only when the provider returns a real stream; downloads appear only when the declared license permits them.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (showInput) {
        TvTextInputDialog(
            title = "Search Tether",
            label = "Track, album, artist or playlist",
            confirmLabel = "Search",
            initialValue = state.query,
            onDismiss = { showInput = false },
            onConfirm = { query ->
                showInput = false
                searchViewModel.setQuery(query)
                searchViewModel.rememberCurrentQuery()
            },
        )
    }
}

@Composable
private fun TvSearchRow(
    track: Track,
    current: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(66.dp),
        shape = RoundedCornerShape(12.dp),
        focusRequester = focusRequester,
        backgroundColor = if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey,
                title = track.displayAlbum,
                modifier = Modifier.size(48.dp),
                cornerRadius = 7.dp,
                initialsSize = 18.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.displayArtist + " · " + track.displayAlbum,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current) {
                TetherBadge("PLAYING")
            } else if (track.origin == dev.tether.core.model.TrackOrigin.REMOTE) {
                TetherBadge(track.sourceLabel.uppercase(), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
