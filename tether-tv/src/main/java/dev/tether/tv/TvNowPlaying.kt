package dev.tether.tv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.tether.core.design.LyricsPane
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherCover
import dev.tether.core.model.AppSettings
import dev.tether.core.model.LyricsLoadState
import dev.tether.core.model.LyricsViewState
import dev.tether.core.model.Track
import dev.tether.core.model.TvDisplayMode
import dev.tether.core.model.formatDuration
import dev.tether.core.player.PlaybackState
import dev.tether.core.player.PlayerViewModel
import dev.tether.core.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TvNowPlayingScreen(
    viewModel: TvViewModel,
    playerViewModel: PlayerViewModel,
    state: PlaybackState,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val track = state.currentTrack
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var mode by remember(track?.id) { mutableStateOf(settings.defaultDisplayMode) }
    var reloadLyrics by remember(track?.id) { mutableIntStateOf(0) }
    var lyricsState by remember(track?.id) { mutableStateOf<LyricsViewState?>(null) }
    var videoUri by remember(track?.id) { mutableStateOf(track?.videoUri) }
    var videoLoading by remember(track?.id) { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val playFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val autoHideControls = !settings.showControlsInCoverMode && mode == TvDisplayMode.COVER

    val lyricsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && track != null) {
            scope.launch {
                lyricsState = viewModel.importLyrics(track, uri)
                viewModel.notify(lyricsState?.message ?: "Lyrics updated")
            }
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && track != null) {
            viewModel.attachVideo(track, uri) { updated ->
                videoUri = updated?.videoUri ?: uri.toString()
            }
        }
    }

    LaunchedEffect(track?.id, reloadLyrics) {
        if (track == null) return@LaunchedEffect
        lyricsState = LyricsViewState(LyricsLoadState.LOADING)
        lyricsState = viewModel.lyrics(track, force = reloadLyrics > 0)
    }
    LaunchedEffect(track?.videoUri) {
        if (!track?.videoUri.isNullOrBlank()) videoUri = track.videoUri
    }
    LaunchedEffect(track?.id, mode) {
        if (track == null || mode != TvDisplayMode.VIDEO) return@LaunchedEffect
        if (!videoUri.isNullOrBlank()) return@LaunchedEffect
        if (!viewModel.supportsVideo(track)) return@LaunchedEffect
        videoLoading = true
        videoUri = viewModel.resolveVideo(track)
        videoLoading = false
    }
    LaunchedEffect(mode, autoHideControls, track?.id) {
        controlsVisible = true
        if (autoHideControls) {
            delay(8_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(track?.id) {
        delay(80)
        runCatching { playFocus.requestFocus() }
    }

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvEmptyState(
                title = "Nothing playing",
                body = "Choose a track from Home, Library, or Search.",
                action = { TvButton("Back", onBack, primary = true) },
            )
        }
        return
    }

    val favorite = favorites.contains(track.id)
    val permission = remember(track.id, track.remoteUri) { viewModel.downloadPermission(track) }
    val totalMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A12), Color(0xFF111022), Color(0xFF07070C)),
                ),
            )
            .onPreviewKeyEvent { event ->
                if (!controlsVisible) {
                    controlsVisible = true
                    true
                } else {
                    false
                }
            },
    ) {
        TetherCover(
            artworkUri = track.artworkUri,
            seed = track.albumKey,
            title = track.displayAlbum,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(760.dp)
                .blur(85.dp),
            cornerRadius = 0.dp,
            initialsSize = 1.sp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF07070C).copy(alpha = 0.90f),
                            Color(0xFF07070C).copy(alpha = 0.62f),
                            Color(0xFF07070C).copy(alpha = 0.86f),
                        ),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().padding(start = 38.dp, end = 38.dp, top = 22.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.6.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                TvPill(
                    "Cover",
                    mode == TvDisplayMode.COVER,
                    { mode = TvDisplayMode.COVER },
                    focusRequester = modeFocus,
                )
                TvPill("Lyrics", mode == TvDisplayMode.LYRICS, { mode = TvDisplayMode.LYRICS })
                TvPill("Video", mode == TvDisplayMode.VIDEO, { mode = TvDisplayMode.VIDEO })
                Spacer(Modifier.weight(1f))
                if (state.sleepTimerActive) {
                    Text(
                        "SLEEP ${formatDuration(state.sleepRemainingMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                TvIconButton(Icons.Rounded.ArrowBack, "Back", onBack)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f)) {
                when (mode) {
                    TvDisplayMode.COVER -> CoverMode(
                        track = track,
                        state = state,
                        settings = settings,
                    )
                    TvDisplayMode.LYRICS -> LyricsMode(
                        track = track,
                        state = state,
                        settings = settings,
                        lyricsState = lyricsState,
                        onRetry = { reloadLyrics++ },
                        onImport = { lyricsLauncher.launch(arrayOf("*/*")) },
                    )
                    TvDisplayMode.VIDEO -> VideoMode(
                        track = track,
                        videoUri = videoUri,
                        loading = videoLoading,
                        state = state,
                        onAttach = { videoLauncher.launch(arrayOf("video/*")) },
                        onShowCover = { mode = TvDisplayMode.COVER },
                    )
                }
            }
            if (controlsVisible) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        formatDuration(state.positionMs.coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (totalMs > 0) (state.positionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.14f),
                    )
                    Text(
                        formatDuration(totalMs),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TvIconButton(
                        Icons.Rounded.Shuffle,
                        "Shuffle",
                        playerViewModel::toggleShuffle,
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    TvIconButton(
                        Icons.Rounded.SkipPrevious,
                        "Previous track",
                        playerViewModel::previous,
                        tint = if (state.hasPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    TvIconButton(
                        Icons.Rounded.Replay10,
                        "Back 10 seconds",
                        { playerViewModel.seekTo((state.positionMs - 10_000).coerceAtLeast(0)) },
                    )
                    TvFocusable(
                        onClick = playerViewModel::togglePlayPause,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        focusRequester = playFocus,
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        focusedBackgroundColor = MaterialTheme.colorScheme.primary,
                        focusBorderColor = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (state.isBuffering) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(
                                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }
                    TvIconButton(
                        Icons.Rounded.Forward10,
                        "Forward 10 seconds",
                        { playerViewModel.seekTo((state.positionMs + 10_000).coerceAtMost(totalMs.takeIf { it > 0 } ?: Long.MAX_VALUE)) },
                    )
                    TvIconButton(
                        Icons.Rounded.SkipNext,
                        "Next track",
                        playerViewModel::next,
                        tint = if (state.hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    TvIconButton(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat mode",
                        playerViewModel::cycleRepeat,
                        tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    TvIconButton(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (favorite) "Remove favorite" else "Add favorite",
                        { viewModel.toggleFavorite(track) },
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (track.origin == dev.tether.core.model.TrackOrigin.REMOTE) {
                        if (permission.allowed) {
                            TvIconButton(
                                Icons.Rounded.Download,
                                "Download for offline playback",
                                { viewModel.download(track) },
                            )
                        }
                    }
                    TvIconButton(
                        Icons.Rounded.PlaylistAdd,
                        "Add to playlist",
                        { showPlaylistPicker = true },
                    )
                    TvIconButton(
                        Icons.Rounded.Upload,
                        "Import lyrics",
                        { lyricsLauncher.launch(arrayOf("*/*")) },
                    )
                }
            } else {
                Text(
                    "Press any key to show controls",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }

    if (showPlaylistPicker) {
        TvPlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onPick = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track)
                showPlaylistPicker = false
            },
        )
    }
}

@Composable
private fun CoverMode(
    track: Track,
    state: PlaybackState,
    settings: AppSettings,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TetherCover(
            artworkUri = track.artworkUri,
            seed = track.albumKey,
            title = track.displayAlbum,
            modifier = Modifier.size(292.dp),
            cornerRadius = 18.dp,
            initialsSize = 66.sp,
        )
        Spacer(Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 41.sp, lineHeight = 47.sp),
                color = Color(0xFFF7F5FF),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                color = Color(0xFFC8C6DA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                track.displayAlbum + if (track.year != null) " · ${track.year}" else "",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = Color(0xFF9C9AAF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(14.dp))
            val license = track.license
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                TetherBadge(if (track.origin == dev.tether.core.model.TrackOrigin.DOWNLOADED) "OFFLINE" else track.sourceLabel.uppercase())
                if (!license.isNullOrBlank()) {
                    TetherBadge(license.uppercase(), color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (!license.isNullOrBlank()) {
                Text(
                    "Licensed by the source · $license",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFF9C9AAF),
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            val playbackError = state.error
            if (playbackError != null) {
                Text(
                    playbackError,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsMode(
    track: Track,
    state: PlaybackState,
    settings: AppSettings,
    lyricsState: LyricsViewState?,
    onRetry: () -> Unit,
    onImport: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "LYRICS",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.4.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                val document = lyricsState?.document
                if (document != null) {
                    TetherBadge(if (document.synced) "SYNCED" else "PLAIN TEXT")
                    Text(
                        document.sourceLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TvButton("Import .lrc", onImport, icon = Icons.Rounded.Upload)
            }
            Spacer(Modifier.height(8.dp))
            when (lyricsState?.state) {
                null, LyricsLoadState.IDLE, LyricsLoadState.LOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Looking for real lyrics…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                    }
                }
                LyricsLoadState.AVAILABLE -> {
                    val document = lyricsState.document
                    if (document == null) {
                        LyricsUnavailable(
                            title = "No lyrics available",
                            body = "The provider did not return readable lyrics for this track.",
                            onRetry = onRetry,
                            onImport = onImport,
                        )
                    } else {
                        if (!document.synced) {
                            Text(
                                "Plain lyrics from ${document.sourceLabel}. Tether will not pretend they are timed.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        LyricsPane(
                            document = document,
                            positionMs = if (document.synced) state.positionMs else 0L,
                            modifier = Modifier.fillMaxSize(),
                            textSize = settings.lyricsTextSize.coerceIn(26, 72).sp,
                            contentPadding = PaddingValues(vertical = 60.dp),
                        )
                    }
                }
                LyricsLoadState.NOT_FOUND, LyricsLoadState.OFFLINE, LyricsLoadState.ERROR -> {
                    LyricsUnavailable(
                        title = when (lyricsState.state) {
                            LyricsLoadState.OFFLINE -> "Lyrics unavailable offline"
                            LyricsLoadState.ERROR -> "Lyrics lookup failed"
                            else -> "No lyrics found"
                        },
                        body = lyricsState.message ?: "Tether could not find lyrics for this track. It will not display placeholders.",
                        onRetry = onRetry,
                        onImport = onImport,
                    )
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.width(232.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey,
                title = track.displayAlbum,
                modifier = Modifier.size(196.dp),
                cornerRadius = 14.dp,
                initialsSize = 46.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lyricsState?.document != null && !lyricsState.document!!.attribution.isBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    lyricsState.document!!.attribution,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LyricsUnavailable(
    title: String,
    body: String,
    onRetry: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(520.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Try again", onRetry)
            TvButton("Import .lrc", onImport, icon = Icons.Rounded.Upload)
        }
    }
}

@Composable
private fun VideoMode(
    track: Track,
    videoUri: String?,
    loading: Boolean,
    state: PlaybackState,
    onAttach: () -> Unit,
    onShowCover: () -> Unit,
) {
    if (videoUri.isNullOrBlank()) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey,
                title = track.displayAlbum,
                modifier = Modifier.size(250.dp),
                cornerRadius = 16.dp,
                initialsSize = 56.sp,
            )
            Spacer(Modifier.width(32.dp))
            Column {
                if (loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                } else {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (loading) "Loading video" else "No video attached",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (loading) {
                        "Fetching the matching clip from YouTube\u2026"
                    } else {
                        "Pick a track from YouTube to load its clip, or attach a local video file you own. Tether keeps playing the audio and shows the video muted and in sync."
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(500.dp).padding(top = 5.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Attach video", onAttach, icon = Icons.Rounded.VideoLibrary, primary = true)
                    TvButton("Back to cover", onShowCover, icon = Icons.Rounded.Image)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            TvSynchronizedVideo(
                videoUri = videoUri,
                positionMs = state.positionMs,
                isPlaying = state.isPlaying,
                modifier = Modifier.width(570.dp).height(321.dp),
            )
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Video is muted. Audio comes from Tether's player, so switching modes never restarts the song.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                TvButton("Replace video", onAttach, icon = Icons.Rounded.VideoLibrary)
            }
        }
    }
}

@Composable
private fun TvSynchronizedVideo(
    videoUri: String,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }
    var error by remember(videoUri) { mutableStateOf<String?>(null) }
    val latestPosition by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(isPlaying)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playerError: androidx.media3.common.PlaybackException) {
                error = playerError.localizedMessage ?: "Video could not be opened."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (abs(exoPlayer.currentPosition - latestPosition) > 1_400L) {
                exoPlayer.seekTo(latestPosition.coerceAtLeast(0))
            }
            exoPlayer.playWhenReady = latestPlaying
            delay(650)
        }
    }

    Box(
        modifier
            .background(Color.Black, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Video unavailable", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(error!!, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}
