package dev.tether.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tether.core.design.TetherBackdrop
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherSnackbarHost
import dev.tether.core.design.TetherTheme
import dev.tether.core.design.TetherWordmark
import dev.tether.core.model.AlbumSummary
import dev.tether.core.model.ArtistSummary
import dev.tether.core.model.FolderSummary
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.Track
import dev.tether.core.player.PlaybackState
import dev.tether.core.player.PlayerViewModel

@Composable
fun TetherMobileRoot(
    mobileViewModel: MobileViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val settings by mobileViewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by mobileViewModel.settingsLoaded.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    TetherTheme(themeMode = settings.themeMode, customAccent = settings.customAccent) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        var permissionGranted by remember { mutableStateOf(hasAudioPermission(context)) }
        var onboardingDoneLocally by rememberSaveable { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            val granted = hasAudioPermission(context)
            permissionGranted = granted
            if (granted) {
                mobileViewModel.markOnboardingComplete()
                mobileViewModel.scanMediaStore()
            } else {
                mobileViewModel.notify("Music access was not granted. You can still add folders with the system picker.")
            }
        }
        val folderLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                onboardingDoneLocally = true
                mobileViewModel.markOnboardingComplete()
                mobileViewModel.addFolder(uri)
            }
        }
        val audioImportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                onboardingDoneLocally = true
                mobileViewModel.markOnboardingComplete()
                mobileViewModel.importFiles(uris)
            }
        }
        val m3uImportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) mobileViewModel.importM3u(uri)
        }
        var exportPlaylistId by remember { mutableStateOf<Long?>(null) }
        val m3uExportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
        ) { uri ->
            val id = exportPlaylistId
            if (uri != null && id != null) mobileViewModel.exportM3u(id, uri)
            exportPlaylistId = null
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionGranted = hasAudioPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(permissionGranted, settingsLoaded) {
            if (settingsLoaded) mobileViewModel.maybeAutoScan(permissionGranted)
        }
        LaunchedEffect(mobileViewModel) {
            mobileViewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val requestPermission: () -> Unit = {
            if (hasAudioPermission(context)) {
                permissionGranted = true
                mobileViewModel.markOnboardingComplete()
                mobileViewModel.scanMediaStore()
            } else {
                permissionLauncher.launch(audioRequestPermissions())
            }
        }
        val chooseFolder: () -> Unit = {
            folderLauncher.launch(null)
        }
        val importAudio: () -> Unit = {
            audioImportLauncher.launch(arrayOf("audio/*"))
        }
        val importM3u: () -> Unit = {
            m3uImportLauncher.launch(
                arrayOf(
                    "audio/x-mpegurl",
                    "audio/mpegurl",
                    "application/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "text/plain",
                    "application/octet-stream",
                ),
            )
        }
        val requestM3uExport: (Long) -> Unit = { playlistId ->
            exportPlaylistId = playlistId
            val name = mobileViewModel.playlists.value.firstOrNull { it.id == playlistId }?.name ?: "playlist"
            val safeName = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "playlist" }
            m3uExportLauncher.launch("$safeName.m3u")
        }

        if (!settingsLoaded) {
            LoadingSplash()
            return@TetherTheme
        }
        val showOnboarding = !settings.onboardingComplete && !onboardingDoneLocally
        if (showOnboarding) {
            OnboardingScreen(
                onAllowAccess = {
                    onboardingDoneLocally = true
                    mobileViewModel.markOnboardingComplete()
                    requestPermission()
                },
                onChooseFolder = chooseFolder,
                onSkip = {
                    onboardingDoneLocally = true
                    mobileViewModel.markOnboardingComplete()
                },
            )
            TetherSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            return@TetherTheme
        }

        val exploreViewModel: ExploreViewModel = viewModel()
        val searchViewModel: SearchViewModel = viewModel()
        var stack by remember { mutableStateOf<List<MobileDestination>>(listOf(MobileDestination.Home)) }
        val current = stack.lastOrNull() ?: MobileDestination.Home
        var actionTrack by remember { mutableStateOf<Track?>(null) }
        val favoriteIds by mobileViewModel.favoriteIds.collectAsStateWithLifecycle()
        val playlists by mobileViewModel.playlists.collectAsStateWithLifecycle()

        val push: (MobileDestination) -> Unit = { destination ->
            stack = stack + destination
        }
        val pop: () -> Unit = {
            if (stack.size > 1) stack = stack.dropLast(1)
        }
        val selectRoot: (MobileDestination) -> Unit = { destination ->
            stack = listOf(destination)
        }
        val playQueue: (List<Track>, Int, Boolean) -> Unit = { queue, index, shuffle ->
            if (queue.isNotEmpty()) {
                playerViewModel.playQueue(queue, index.coerceIn(0, queue.lastIndex), shuffle)
            }
        }
        val openAlbum: (AlbumSummary) -> Unit = { album ->
            push(
                MobileDestination.Collection(
                    kind = CollectionKind.ALBUM,
                    key = album.key,
                    title = album.title,
                    subtitle = album.artist,
                    artworkUri = album.artworkUri,
                ),
            )
        }
        val openArtist: (ArtistSummary) -> Unit = { artist ->
            push(
                MobileDestination.Collection(
                    kind = CollectionKind.ARTIST,
                    key = artist.key,
                    title = artist.name,
                    subtitle = "${artist.trackCount} tracks",
                    artworkUri = artist.artworkUri,
                ),
            )
        }
        val openAlbumById: (String, String, String?) -> Unit = { key, title, artwork ->
            if (key.isNotBlank()) {
                push(
                    MobileDestination.Collection(
                        kind = CollectionKind.ALBUM,
                        key = key,
                        title = title,
                        artworkUri = artwork,
                    ),
                )
            }
        }

        BackHandler(enabled = stack.size > 1) { pop() }

        TetherBackdrop(
            artworkUri = playerState.currentTrack?.artworkUri,
            seed = playerState.currentTrack?.let { it.albumKey + it.artist } ?: "tether",
            title = playerState.currentTrack?.displayAlbum ?: "Tether",
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (current) {
                        MobileDestination.Home -> HomeScreen(
                            viewModel = mobileViewModel,
                            playerState = playerState,
                            hasAudioPermission = permissionGranted,
                            onRequestPermission = requestPermission,
                            onAddFolder = chooseFolder,
                            onImportFiles = importAudio,
                            onOpenSearch = { push(MobileDestination.Search) },
                            onOpenSettings = { push(MobileDestination.Settings) },
                            onOpenExplore = { selectRoot(MobileDestination.Explore) },
                            onOpenDownloads = { selectRoot(MobileDestination.Downloads) },
                            onOpenPlaylists = { selectRoot(MobileDestination.Playlists) },
                            onOpenAlbum = openAlbum,
                            onOpenArtist = openArtist,
                            onOpenPlaylist = { push(MobileDestination.PlaylistDetail(it)) },
                            onOpenCollection = { kind, key, title, artwork ->
                                if (kind == CollectionKind.ALBUM && key == "all") {
                                    selectRoot(MobileDestination.Library)
                                } else {
                                    push(
                                        MobileDestination.Collection(
                                            kind = kind,
                                            key = key,
                                            title = title,
                                            artworkUri = artwork,
                                        ),
                                    )
                                }
                            },
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Explore -> ExploreScreen(
                            exploreViewModel = exploreViewModel,
                            loadRelease = { release ->
                                mobileViewModel.resolveRelease(release.id, release.providerId)
                            },
                            onOpenSettings = { push(MobileDestination.Settings) },
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                            onDownloadTrack = mobileViewModel::download,
                        )
                        MobileDestination.Library -> LibraryScreen(
                            viewModel = mobileViewModel,
                            playerState = playerState,
                            hasAudioPermission = permissionGranted,
                            onRequestPermission = requestPermission,
                            onOpenSearch = { push(MobileDestination.Search) },
                            onAddFolder = chooseFolder,
                            onImportFiles = importAudio,
                            onOpenAlbum = openAlbum,
                            onOpenArtist = openArtist,
                            onOpenFolder = { folder: FolderSummary ->
                                push(
                                    MobileDestination.Collection(
                                        kind = CollectionKind.FOLDER,
                                        key = folder.key,
                                        title = folder.name,
                                        subtitle = "${folder.trackCount} tracks",
                                    ),
                                )
                            },
                            onOpenGenre = { genre: FolderSummary ->
                                push(
                                    MobileDestination.Collection(
                                        kind = CollectionKind.GENRE,
                                        key = genre.key,
                                        title = genre.name,
                                        subtitle = "${genre.trackCount} tracks",
                                    ),
                                )
                            },
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Downloads -> DownloadsScreen(
                            viewModel = mobileViewModel,
                            playerState = playerState,
                            onOpenExplore = { selectRoot(MobileDestination.Explore) },
                            onPlayTrack = { track -> playerViewModel.playNow(track) },
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Playlists -> PlaylistsScreen(
                            viewModel = mobileViewModel,
                            onOpenPlaylist = { push(MobileDestination.PlaylistDetail(it)) },
                            onCreatePlaylist = { name -> mobileViewModel.createPlaylist(name) },
                            onImportM3u = importM3u,
                        )
                        MobileDestination.Search -> SearchScreen(
                            searchViewModel = searchViewModel,
                            mobileViewModel = mobileViewModel,
                            playerState = playerState,
                            loadRelease = { release ->
                                mobileViewModel.resolveRelease(release.id, release.providerId)
                            },
                            onBack = pop,
                            onOpenAlbum = openAlbum,
                            onOpenArtist = openArtist,
                            onOpenPlaylist = { push(MobileDestination.PlaylistDetail(it)) },
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Settings -> SettingsScreen(
                            viewModel = mobileViewModel,
                            hasAudioPermission = permissionGranted,
                            onBack = pop,
                            onOpenDiagnostics = { push(MobileDestination.Diagnostics) },
                            onOpenAbout = { push(MobileDestination.About) },
                            onOpenLicenses = { push(MobileDestination.Licenses) },
                            onRequestPermission = requestPermission,
                            onAddFolder = chooseFolder,
                            onImportFiles = importAudio,
                        )
                        MobileDestination.Player -> PlayerScreen(
                            viewModel = mobileViewModel,
                            playerViewModel = playerViewModel,
                            state = playerState,
                            onBack = pop,
                            onOpenQueue = { push(MobileDestination.Queue) },
                            onOpenLyrics = { push(MobileDestination.Lyrics) },
                            onOpenAlbum = openAlbumById,
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Queue -> QueueScreen(
                            playerViewModel = playerViewModel,
                            viewModel = mobileViewModel,
                            state = playerState,
                            onBack = pop,
                            onTrackMenu = { actionTrack = it },
                        )
                        MobileDestination.Lyrics -> LyricsScreen(
                            viewModel = mobileViewModel,
                            playerViewModel = playerViewModel,
                            state = playerState,
                            onBack = pop,
                        )
                        MobileDestination.Diagnostics -> DiagnosticsScreen(
                            viewModel = mobileViewModel,
                            onBack = pop,
                        )
                        MobileDestination.About -> AboutScreen(
                            onBack = pop,
                            onOpenLicenses = { push(MobileDestination.Licenses) },
                        )
                        MobileDestination.Licenses -> LicensesScreen(onBack = pop)
                        is MobileDestination.Collection -> CollectionScreen(
                            kind = current.kind,
                            collectionKey = current.key,
                            title = current.title,
                            subtitle = current.subtitle,
                            artworkUri = current.artworkUri,
                            viewModel = mobileViewModel,
                            playerState = playerState,
                            onBack = pop,
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                        )
                        is MobileDestination.PlaylistDetail -> PlaylistDetailScreen(
                            viewModel = mobileViewModel,
                            playlistId = current.playlistId,
                            playerState = playerState,
                            onBack = pop,
                            onExportM3u = requestM3uExport,
                            onPlayQueue = playQueue,
                            onTrackMenu = { actionTrack = it },
                        )
                    }
                }
                var miniDismissed by remember { mutableStateOf(false) }
                LaunchedEffect(playerState.currentTrack?.id) { miniDismissed = false }
                val showMini = playerState.currentTrack != null && !miniDismissed &&
                    current !is MobileDestination.Player &&
                    current !is MobileDestination.Queue &&
                    current !is MobileDestination.Lyrics
                val showBottom = current.isRootTab()
                if (showMini) {
                    MiniPlayer(
                        state = playerState,
                        onOpen = { push(MobileDestination.Player) },
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                        onDismiss = { miniDismissed = true },
                        modifier = if (showBottom) Modifier else Modifier.navigationBarsPadding(),
                    )
                }
                if (showBottom) {
                    TetherBottomBar(
                        items = listOf(
                            TetherNavItem(
                                label = "Home",
                                icon = Icons.Rounded.Home,
                                selected = current == MobileDestination.Home,
                                onClick = { selectRoot(MobileDestination.Home) },
                            ),
                            TetherNavItem(
                                label = "Explore",
                                icon = Icons.Rounded.Explore,
                                selected = current == MobileDestination.Explore,
                                onClick = { selectRoot(MobileDestination.Explore) },
                            ),
                            TetherNavItem(
                                label = "Library",
                                icon = Icons.Rounded.LibraryMusic,
                                selected = current == MobileDestination.Library,
                                onClick = { selectRoot(MobileDestination.Library) },
                            ),
                            TetherNavItem(
                                label = "Downloads",
                                icon = Icons.Rounded.Download,
                                selected = current == MobileDestination.Downloads,
                                onClick = { selectRoot(MobileDestination.Downloads) },
                            ),
                            TetherNavItem(
                                label = "Playlists",
                                icon = Icons.Rounded.QueueMusic,
                                selected = current == MobileDestination.Playlists,
                                onClick = { selectRoot(MobileDestination.Playlists) },
                            ),
                        ),
                    )
                }
            }
            TetherSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = when {
                            current.isRootTab() -> 156.dp
                            current !is MobileDestination.Player &&
                                current !is MobileDestination.Queue &&
                                current !is MobileDestination.Lyrics -> 82.dp
                            else -> 18.dp
                        },
                    ),
            )
        }

        actionTrack?.let { track ->
            TrackActionsSheet(
                track = track,
                favorite = favoriteIds.contains(track.id),
                playlists = playlists,
                downloadPermission = mobileViewModel.downloadPermission(track),
                onDismiss = { actionTrack = null },
                onPlay = { playerViewModel.playNow(track) },
                onPlayNext = { playerViewModel.playNext(track) },
                onAddToQueue = { playerViewModel.addToQueue(track) },
                onToggleFavorite = { mobileViewModel.toggleFavorite(track) },
                onAddToPlaylist = { playlistId -> mobileViewModel.addTrackToPlaylist(playlistId, track) },
                onCreatePlaylistAndAdd = { name ->
                    mobileViewModel.createPlaylist(name) { playlistId ->
                        mobileViewModel.addTrackToPlaylist(playlistId, track)
                    }
                },
                onDownload = { mobileViewModel.download(track) },
                onGoToAlbum = if (track.origin != dev.tether.core.model.TrackOrigin.REMOTE) {
                    {
                        push(
                            MobileDestination.Collection(
                                kind = CollectionKind.ALBUM,
                                key = track.albumKey,
                                title = track.displayAlbum,
                                subtitle = track.displayArtist,
                                artworkUri = track.artworkUri,
                            ),
                        )
                    }
                } else {
                    null
                },
                onGoToArtist = if (track.origin != dev.tether.core.model.TrackOrigin.REMOTE) {
                    {
                        push(
                            MobileDestination.Collection(
                                kind = CollectionKind.ARTIST,
                                key = track.artistKey,
                                title = track.displayArtist,
                                subtitle = "",
                                artworkUri = track.artworkUri,
                            ),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun LoadingSplash() {
    TetherBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TetherMark(82.dp, cornerRadius = 24.dp)
            TetherWordmark(titleSize = 30)
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                "Opening your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hasAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun audioRequestPermissions(): Array<String> = buildList {
    add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
