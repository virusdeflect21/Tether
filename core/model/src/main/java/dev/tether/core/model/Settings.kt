package dev.tether.core.model

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class TvStartScreen { HOME, NOW_PLAYING }
enum class TvDisplayMode { COVER, LYRICS, VIDEO }
enum class ArtworkScale { FIT, FILL }
enum class QueueAction { PLAY_NOW, PLAY_NEXT, ADD_TO_QUEUE }
enum class LibrarySort { TITLE, ARTIST, ALBUM, RECENTLY_ADDED, DURATION }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val customAccent: String? = null,
    val onlineDiscoveryEnabled: Boolean = true,
    val lrclibEnabled: Boolean = true,
    val startupScreen: TvStartScreen = TvStartScreen.HOME,
    val defaultDisplayMode: TvDisplayMode = TvDisplayMode.COVER,
    val showControlsInCoverMode: Boolean = true,
    val artworkScale: ArtworkScale = ArtworkScale.FIT,
    val lyricsTextSize: Int = 42,
    val screensaverMinutes: Int = 5,
    val gaplessEnabled: Boolean = true,
    val autoplayNext: Boolean = true,
    val queueAction: QueueAction = QueueAction.PLAY_NOW,
    val downloadQuality: DownloadQuality = DownloadQuality.HIGH,
    val searchHistoryEnabled: Boolean = true,
    val scanMediaStoreOnStartup: Boolean = true,
    val showPlaybackStats: Boolean = true,
    val keepQueueOnExit: Boolean = true,
    val lastScanAt: Long = 0L,
    val lastScanError: String? = null,
    val onboardingComplete: Boolean = false,
)

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val playlists: List<BackupPlaylist>,
    val favorites: List<BackupFavorite>,
    val settings: Map<String, String>,
)

data class BackupPlaylist(
    val name: String,
    val description: String,
    val tracks: List<BackupTrackRef>,
)

data class BackupTrackRef(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String?,
)

data class BackupFavorite(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String?,
)
