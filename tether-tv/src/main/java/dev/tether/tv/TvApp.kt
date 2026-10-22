package dev.tether.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tether.core.design.TetherCover
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherSnackbarHost
import dev.tether.core.design.TetherTheme
import dev.tether.core.design.TetherWordmark
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.Track
import dev.tether.core.model.TvStartScreen
import dev.tether.core.player.PlaybackState
import dev.tether.core.player.PlayerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface TvRoute {
    data object Home : TvRoute
    data object Search : TvRoute
    data object Library : TvRoute
    data object Playlists : TvRoute
    data object Downloads : TvRoute
    data object Settings : TvRoute
    data object About : TvRoute
    data object NowPlaying : TvRoute
    data class Album(val key: String, val title: String, val artist: String, val artworkUri: String?) : TvRoute
    data class Artist(val key: String, val name: String, val artworkUri: String?) : TvRoute
    data class Playlist(val id: Long) : TvRoute
}

private fun TvRoute.sidebarKey(): String = when (this) {
    TvRoute.Home -> "home"
    TvRoute.Search -> "search"
    TvRoute.Library, is TvRoute.Album, is TvRoute.Artist -> "library"
    TvRoute.Playlists, is TvRoute.Playlist -> "playlists"
    TvRoute.Downloads -> "downloads"
    TvRoute.Settings, TvRoute.About -> "settings"
    TvRoute.NowPlaying -> ""
}

private fun routeForSidebar(key: String): TvRoute = when (key) {
    "search" -> TvRoute.Search
    "library" -> TvRoute.Library
    "playlists" -> TvRoute.Playlists
    "downloads" -> TvRoute.Downloads
    "settings" -> TvRoute.Settings
    else -> TvRoute.Home
}

@Composable
fun TetherTvRoot(
    tvViewModel: TvViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val settings by tvViewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by tvViewModel.settingsLoaded.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    TetherTheme(themeMode = settings.themeMode, customAccent = settings.customAccent) {
        var permissionGranted by remember { mutableStateOf(hasTvAudioPermission(context)) }
        val snackbarHostState = remember { SnackbarHostState() }
        val stack = remember { mutableStateListOf<TvRoute>(TvRoute.Home) }
        val current = stack.last()
        var startupApplied by rememberSaveable { mutableStateOf(false) }
        var showScreensaver by remember { mutableStateOf(false) }
        var lastInteraction by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
        val searchViewModel: TvSearchViewModel = viewModel()

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionGranted = hasTvAudioPermission(context)
            if (permissionGranted) {
                tvViewModel.scanMediaStore()
            } else {
                tvViewModel.notify("Media access was not granted. Add a folder or import files instead.")
            }
        }
        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) tvViewModel.addFolder(uri)
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) tvViewModel.importFiles(uris)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionGranted = hasTvAudioPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            tvViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
        }
        LaunchedEffect(permissionGranted) {
            tvViewModel.maybeAutoScan(permissionGranted)
        }
        LaunchedEffect(settingsLoaded) {
            if (settingsLoaded && !startupApplied) {
                startupApplied = true
                if (settings.startupScreen == TvStartScreen.NOW_PLAYING && playerState.currentTrack != null) {
                    stack.add(TvRoute.NowPlaying)
                }
            }
        }
        LaunchedEffect(settings.screensaverMinutes) {
            while (true) {
                delay(10_000)
                val minutes = settings.screensaverMinutes
                if (minutes > 0 && !showScreensaver &&
                    SystemClock.elapsedRealtime() - lastInteraction > minutes * 60_000L
                ) {
                    showScreensaver = true
                }
            }
        }

        BackHandler(enabled = stack.size > 1) {
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }

        fun navigate(route: TvRoute) {
            stack.add(route)
        }

        fun navigateRoot(key: String) {
            stack.clear()
            stack.add(routeForSidebar(key))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent {
                    lastInteraction = SystemClock.elapsedRealtime()
                    false
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (current !is TvRoute.NowPlaying) {
                    TvSidebar(
                        selectedKey = current.sidebarKey(),
                        nowPlaying = playerState.currentTrack,
                        isPlaying = playerState.isPlaying,
                        onSelect = ::navigateRoot,
                        onOpenNowPlaying = {
                            if (stack.lastOrNull() !is TvRoute.NowPlaying) navigate(TvRoute.NowPlaying)
                        },
                    )
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (current) {
                        TvRoute.Home -> TvHomeScreen(
                            viewModel = tvViewModel,
                            playerState = playerState,
                            hasAudioPermission = permissionGranted,
                            onRequestPermission = { permissionLauncher.launch(audioPermissions()) },
                            onAddFolder = { folderLauncher.launch(null) },
                            onImportFiles = { importLauncher.launch(arrayOf("audio/*")) },
                            onOpenSearch = { navigateRoot("search") },
                            onOpenLibrary = { navigateRoot("library") },
                            onOpenPlaylists = { navigateRoot("playlists") },
                            onOpenAlbum = { album ->
                                navigate(TvRoute.Album(album.key, album.title, album.artist, album.artworkUri))
                            },
                            onOpenPlaylist = { id -> navigate(TvRoute.Playlist(id)) },
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                        )
                        TvRoute.Search -> TvSearchScreen(
                            searchViewModel = searchViewModel,
                            playerState = playerState,
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                            onOpenAlbum = { album ->
                                navigate(TvRoute.Album(album.key, album.title, album.artist, album.artworkUri))
                            },
                            onOpenPlaylist = { id -> navigate(TvRoute.Playlist(id)) },
                        )
                        TvRoute.Library -> TvLibraryScreen(
                            viewModel = tvViewModel,
                            playerState = playerState,
                            hasAudioPermission = permissionGranted,
                            onOpenSearch = { navigateRoot("search") },
                            onOpenAlbum = { album ->
                                navigate(TvRoute.Album(album.key, album.title, album.artist, album.artworkUri))
                            },
                            onOpenArtist = { artist ->
                                navigate(TvRoute.Artist(artist.key, artist.name, artist.artworkUri))
                            },
                            onRequestPermission = { permissionLauncher.launch(audioPermissions()) },
                            onAddFolder = { folderLauncher.launch(null) },
                            onImportFiles = { importLauncher.launch(arrayOf("audio/*")) },
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                        )
                        TvRoute.Playlists -> TvPlaylistsScreen(
                            viewModel = tvViewModel,
                            onOpenPlaylist = { id -> navigate(TvRoute.Playlist(id)) },
                        )
                        is TvRoute.Playlist -> TvPlaylistDetailScreen(
                            viewModel = tvViewModel,
                            playlistId = current.id,
                            playerState = playerState,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                        )
                        TvRoute.Downloads -> TvDownloadsScreen(
                            viewModel = tvViewModel,
                            onOpenSearch = { navigateRoot("search") },
                            onPlayTrack = { track -> playerViewModel.playNow(track) },
                        )
                        TvRoute.Settings -> TvSettingsScreen(
                            viewModel = tvViewModel,
                            hasAudioPermission = permissionGranted,
                            onRequestPermission = { permissionLauncher.launch(audioPermissions()) },
                            onAddFolder = { folderLauncher.launch(null) },
                            onImportFiles = { importLauncher.launch(arrayOf("audio/*")) },
                            onOpenAbout = { navigate(TvRoute.About) },
                        )
                        TvRoute.About -> TvAboutScreen(
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                        )
                        TvRoute.NowPlaying -> TvNowPlayingScreen(
                            viewModel = tvViewModel,
                            playerViewModel = playerViewModel,
                            state = playerState,
                            settings = settings,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                        )
                        is TvRoute.Album -> TvCollectionLoader(
                            title = current.title,
                            subtitle = current.artist,
                            artworkUri = current.artworkUri,
                            seed = current.key,
                            load = { tvViewModel.albumTracks(current.key) },
                            playerState = playerState,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                        )
                        is TvRoute.Artist -> TvCollectionLoader(
                            title = current.name,
                            subtitle = "Artist",
                            artworkUri = current.artworkUri,
                            seed = current.key,
                            load = { tvViewModel.artistTracks(current.key) },
                            playerState = playerState,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            onPlayQueue = { tracks, index, shuffle ->
                                playerViewModel.playQueue(tracks, index, shuffle)
                            },
                        )
                    }
                }
            }
            TetherSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )
            if (showScreensaver) {
                TvScreensaver(
                    track = playerState.currentTrack,
                    onDismiss = {
                        showScreensaver = false
                        lastInteraction = SystemClock.elapsedRealtime()
                    },
                )
            }
        }
    }
}

@Composable
private fun TvCollectionLoader(
    title: String,
    subtitle: String,
    artworkUri: String?,
    seed: String,
    load: suspend () -> List<Track>,
    playerState: PlaybackState,
    onBack: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
) {
    var tracks by remember(seed) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(seed) { tracks = load() }
    val loaded = tracks
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    } else {
        TvCollectionScreen(
            title = title,
            subtitle = subtitle,
            artworkUri = artworkUri,
            seed = seed,
            tracks = loaded,
            playerState = playerState,
            onBack = onBack,
            onPlayQueue = onPlayQueue,
        )
    }
}

@Composable
private fun TvScreensaver(
    track: Track?,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05050A))
            .onPreviewKeyEvent {
                onDismiss()
                true
            }
            .clickable(onClick = onDismiss),
    ) {
        if (track != null) {
            TetherCover(
                artworkUri = track.artworkUri,
                seed = track.albumKey,
                title = track.displayAlbum,
                modifier = Modifier.align(Alignment.Center).size(900.dp).blur(120.dp),
                cornerRadius = 0.dp,
                initialsSize = 1.sp,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF05050A).copy(alpha = 0.68f),
                            Color(0xFF05050A).copy(alpha = 0.84f),
                        ),
                    ),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(46.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TetherMark(58.dp)
            Spacer(Modifier.height(14.dp))
            TetherWordmark(titleSize = 24, showTagline = false)
            Spacer(Modifier.height(24.dp))
            Text(
                time,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 92.sp, lineHeight = 96.sp),
                color = Color(0xFFF7F5FF),
                fontWeight = FontWeight.Light,
            )
            Text(
                date,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = Color(0xFFC8C6DA),
            )
            if (track != null) {
                Spacer(Modifier.height(34.dp))
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    color = Color(0xFFF7F5FF),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = Color(0xFF9C9AAF),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Press any key to continue",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                color = Color(0xFF9C9AAF).copy(alpha = 0.8f),
            )
        }
    }
}

private fun audioPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasTvAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
