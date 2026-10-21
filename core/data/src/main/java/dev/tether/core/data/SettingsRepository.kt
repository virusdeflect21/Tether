package dev.tether.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.tether.core.model.AppSettings
import dev.tether.core.model.ArtworkScale
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.QueueAction
import dev.tether.core.model.ThemeMode
import dev.tether.core.model.TvDisplayMode
import dev.tether.core.model.TvStartScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.tetherSettingsDataStore: DataStore<Preferences> by preferencesDataStore("tether_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val customAccent = stringPreferencesKey("custom_accent")
        val archiveEnabled = booleanPreferencesKey("online_discovery_enabled")
        val lrclibEnabled = booleanPreferencesKey("lrclib_enabled")
        val startupScreen = stringPreferencesKey("startup_screen")
        val defaultDisplayMode = stringPreferencesKey("default_display_mode")
        val showControlsInCover = booleanPreferencesKey("show_controls_in_cover")
        val artworkScale = stringPreferencesKey("artwork_scale")
        val lyricsTextSize = intPreferencesKey("lyrics_text_size")
        val screensaverMinutes = intPreferencesKey("screensaver_minutes")
        val gaplessEnabled = booleanPreferencesKey("gapless_enabled")
        val autoplayNext = booleanPreferencesKey("autoplay_next")
        val queueAction = stringPreferencesKey("queue_action")
        val downloadQuality = stringPreferencesKey("download_quality")
        val searchHistoryEnabled = booleanPreferencesKey("search_history_enabled")
        val scanOnStartup = booleanPreferencesKey("scan_media_store_on_startup")
        val showPlaybackStats = booleanPreferencesKey("show_playback_stats")
        val keepQueueOnExit = booleanPreferencesKey("keep_queue_on_exit")
        val lastScanAt = stringPreferencesKey("last_scan_at")
        val lastScanError = stringPreferencesKey("last_scan_error")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.tetherSettingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.themeMode].toEnum(ThemeMode.DARK),
                customAccent = prefs[Keys.customAccent]?.takeIf { it.isNotBlank() },
                onlineDiscoveryEnabled = prefs[Keys.archiveEnabled] ?: true,
                lrclibEnabled = prefs[Keys.lrclibEnabled] ?: true,
                startupScreen = prefs[Keys.startupScreen].toEnum(TvStartScreen.HOME),
                defaultDisplayMode = prefs[Keys.defaultDisplayMode].toEnum(TvDisplayMode.COVER),
                showControlsInCoverMode = prefs[Keys.showControlsInCover] ?: true,
                artworkScale = prefs[Keys.artworkScale].toEnum(ArtworkScale.FIT),
                lyricsTextSize = prefs[Keys.lyricsTextSize] ?: 42,
                screensaverMinutes = prefs[Keys.screensaverMinutes] ?: 5,
                gaplessEnabled = prefs[Keys.gaplessEnabled] ?: true,
                autoplayNext = prefs[Keys.autoplayNext] ?: true,
                queueAction = prefs[Keys.queueAction].toEnum(QueueAction.PLAY_NOW),
                downloadQuality = prefs[Keys.downloadQuality].toEnum(DownloadQuality.HIGH),
                searchHistoryEnabled = prefs[Keys.searchHistoryEnabled] ?: true,
                scanMediaStoreOnStartup = prefs[Keys.scanOnStartup] ?: true,
                showPlaybackStats = prefs[Keys.showPlaybackStats] ?: true,
                keepQueueOnExit = prefs[Keys.keepQueueOnExit] ?: true,
                lastScanAt = prefs[Keys.lastScanAt]?.toLongOrNull() ?: 0L,
                lastScanError = prefs[Keys.lastScanError],
                onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.themeMode, value.name)
    suspend fun setCustomAccent(value: String?) = put(Keys.customAccent, value.orEmpty())
    suspend fun setOnlineDiscoveryEnabled(value: Boolean) = put(Keys.archiveEnabled, value)
    suspend fun setLrclibEnabled(value: Boolean) = put(Keys.lrclibEnabled, value)
    suspend fun setStartupScreen(value: TvStartScreen) = put(Keys.startupScreen, value.name)
    suspend fun setDefaultDisplayMode(value: TvDisplayMode) = put(Keys.defaultDisplayMode, value.name)
    suspend fun setShowControlsInCover(value: Boolean) = put(Keys.showControlsInCover, value)
    suspend fun setArtworkScale(value: ArtworkScale) = put(Keys.artworkScale, value.name)
    suspend fun setLyricsTextSize(value: Int) = put(Keys.lyricsTextSize, value.coerceIn(26, 72))
    suspend fun setScreensaverMinutes(value: Int) = put(Keys.screensaverMinutes, value.coerceIn(0, 60))
    suspend fun setGaplessEnabled(value: Boolean) = put(Keys.gaplessEnabled, value)
    suspend fun setAutoplayNext(value: Boolean) = put(Keys.autoplayNext, value)
    suspend fun setQueueAction(value: QueueAction) = put(Keys.queueAction, value.name)
    suspend fun setDownloadQuality(value: DownloadQuality) = put(Keys.downloadQuality, value.name)
    suspend fun setSearchHistoryEnabled(value: Boolean) = put(Keys.searchHistoryEnabled, value)
    suspend fun setScanMediaStoreOnStartup(value: Boolean) = put(Keys.scanOnStartup, value)
    suspend fun setShowPlaybackStats(value: Boolean) = put(Keys.showPlaybackStats, value)
    suspend fun setKeepQueueOnExit(value: Boolean) = put(Keys.keepQueueOnExit, value)
    suspend fun setOnboardingComplete(value: Boolean) = put(Keys.onboardingComplete, value)
    suspend fun recordScan(at: Long, error: String? = null) {
        context.tetherSettingsDataStore.edit { prefs ->
            prefs[Keys.lastScanAt] = at.toString()
            if (error == null) prefs.remove(Keys.lastScanError) else prefs[Keys.lastScanError] = error
        }
    }

    suspend fun snapshot(): Map<String, String> {
        val prefs = context.tetherSettingsDataStore.data.catch { emit(emptyPreferences()) }.first()
        return buildMap {
            prefs[Keys.themeMode]?.let { put("theme_mode", it) }
            prefs[Keys.customAccent]?.let { put("custom_accent", it) }
            prefs[Keys.archiveEnabled]?.let { put("online_discovery_enabled", it.toString()) }
            prefs[Keys.lrclibEnabled]?.let { put("lrclib_enabled", it.toString()) }
            prefs[Keys.startupScreen]?.let { put("startup_screen", it) }
            prefs[Keys.defaultDisplayMode]?.let { put("default_display_mode", it) }
            prefs[Keys.showControlsInCover]?.let { put("show_controls_in_cover", it.toString()) }
            prefs[Keys.artworkScale]?.let { put("artwork_scale", it) }
            prefs[Keys.lyricsTextSize]?.let { put("lyrics_text_size", it.toString()) }
            prefs[Keys.screensaverMinutes]?.let { put("screensaver_minutes", it.toString()) }
            prefs[Keys.gaplessEnabled]?.let { put("gapless_enabled", it.toString()) }
            prefs[Keys.autoplayNext]?.let { put("autoplay_next", it.toString()) }
            prefs[Keys.queueAction]?.let { put("queue_action", it) }
            prefs[Keys.downloadQuality]?.let { put("download_quality", it) }
            prefs[Keys.searchHistoryEnabled]?.let { put("search_history_enabled", it.toString()) }
            prefs[Keys.scanOnStartup]?.let { put("scan_media_store_on_startup", it.toString()) }
            prefs[Keys.showPlaybackStats]?.let { put("show_playback_stats", it.toString()) }
            prefs[Keys.keepQueueOnExit]?.let { put("keep_queue_on_exit", it.toString()) }
        }
    }

    suspend fun restore(values: Map<String, String>) {
        context.tetherSettingsDataStore.edit { prefs ->
            values["theme_mode"]?.toEnumOrNull<ThemeMode>()?.let { prefs[Keys.themeMode] = it.name }
            values["custom_accent"]?.let { prefs[Keys.customAccent] = it }
            values["online_discovery_enabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.archiveEnabled] = it }
            values["lrclib_enabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.lrclibEnabled] = it }
            values["startup_screen"]?.toEnumOrNull<TvStartScreen>()?.let { prefs[Keys.startupScreen] = it.name }
            values["default_display_mode"]?.toEnumOrNull<TvDisplayMode>()?.let { prefs[Keys.defaultDisplayMode] = it.name }
            values["show_controls_in_cover"]?.toBooleanStrictOrNull()?.let { prefs[Keys.showControlsInCover] = it }
            values["artwork_scale"]?.toEnumOrNull<ArtworkScale>()?.let { prefs[Keys.artworkScale] = it.name }
            values["lyrics_text_size"]?.toIntOrNull()?.let { prefs[Keys.lyricsTextSize] = it.coerceIn(26, 72) }
            values["screensaver_minutes"]?.toIntOrNull()?.let { prefs[Keys.screensaverMinutes] = it.coerceIn(0, 60) }
            values["gapless_enabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.gaplessEnabled] = it }
            values["autoplay_next"]?.toBooleanStrictOrNull()?.let { prefs[Keys.autoplayNext] = it }
            values["queue_action"]?.toEnumOrNull<QueueAction>()?.let { prefs[Keys.queueAction] = it.name }
            values["download_quality"]?.toEnumOrNull<DownloadQuality>()?.let { prefs[Keys.downloadQuality] = it.name }
            values["search_history_enabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.searchHistoryEnabled] = it }
            values["scan_media_store_on_startup"]?.toBooleanStrictOrNull()?.let { prefs[Keys.scanOnStartup] = it }
            values["show_playback_stats"]?.toBooleanStrictOrNull()?.let { prefs[Keys.showPlaybackStats] = it }
            values["keep_queue_on_exit"]?.toBooleanStrictOrNull()?.let { prefs[Keys.keepQueueOnExit] = it }
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.tetherSettingsDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
