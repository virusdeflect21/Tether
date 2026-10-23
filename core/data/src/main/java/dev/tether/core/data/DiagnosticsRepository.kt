package dev.tether.core.data

import android.content.Context
import android.os.Build
import android.os.StatFs
import dev.tether.core.database.TetherDatabase
import dev.tether.core.lyrics.LrclibClient
import dev.tether.core.model.DownloadRecord
import dev.tether.core.model.LibraryStats
import dev.tether.core.model.ProviderCapability
import dev.tether.core.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class DiagnosticStatus { OK, WARN, FAIL, INFO }

data class DiagnosticCheck(
    val label: String,
    val status: DiagnosticStatus,
    val detail: String,
)

data class DiagnosticReport(
    val generatedAt: Long = System.currentTimeMillis(),
    val checks: List<DiagnosticCheck>,
) {
    val hasFailure: Boolean get() = checks.any { it.status == DiagnosticStatus.FAIL }
    fun asText(): String = buildString {
        appendLine("Tether diagnostics")
        appendLine("Generated: ${java.util.Date(generatedAt)}")
        appendLine()
        checks.forEach { check ->
            appendLine("[${check.status}] ${check.label}")
            appendLine("  ${check.detail}")
        }
    }
}

class DiagnosticsRepository(
    private val context: Context,
    private val database: TetherDatabase,
    private val library: LibraryRepository,
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
    private val artwork: ArtworkStore,
    private val lyrics: LyricsRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun runChecks(): DiagnosticReport = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val checks = mutableListOf<DiagnosticCheck>()
        checks += DiagnosticCheck(
            "App",
            DiagnosticStatus.INFO,
            "${context.packageName} · ${runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("unknown")} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        checks += DiagnosticCheck(
            "Network",
            if (NetworkStatus.isOnline(context)) DiagnosticStatus.OK else DiagnosticStatus.WARN,
            if (NetworkStatus.isOnline(context)) "Connected" else "No network. Local playback, downloads and imported lyrics still work.",
        )
        val localCount = runCatching { database.trackDao().libraryCount() }.getOrDefault(0)
        checks += DiagnosticCheck(
            "Local library",
            if (localCount > 0) DiagnosticStatus.OK else DiagnosticStatus.WARN,
            if (localCount > 0) "$localCount playable tracks in the library" else "No local tracks yet. Add a folder or allow music access.",
        )
        checks += DiagnosticCheck(
            "Media permission",
            if (library.hasAudioPermission()) DiagnosticStatus.OK else DiagnosticStatus.WARN,
            if (library.hasAudioPermission()) "Music access granted" else "Not granted. The system folder picker can still add music.",
        )
        checks += DiagnosticCheck(
            "Online discovery",
            if (current.onlineDiscoveryEnabled) DiagnosticStatus.OK else DiagnosticStatus.INFO,
            if (current.onlineDiscoveryEnabled) "Enabled for ${providers.musicProviders.joinToString { it.descriptor.name }}" else "Disabled by the user",
        )
        current.lastScanError?.let {
            checks += DiagnosticCheck("Last scan warning", DiagnosticStatus.WARN, it)
        }
        if (NetworkStatus.isOnline(context) && current.onlineDiscoveryEnabled) {
            val results = coroutineScope {
                val youtube = async {
                    checkHttp("YouTube", "https://www.youtube.com/")
                }
                val lrclib = async {
                    checkHttp("LRCLIB API", "https://lrclib.net/api/search?track_name=test")
                }
                listOf(youtube.await(), lrclib.await())
            }
            checks += results
        } else {
            checks += DiagnosticCheck("Provider checks", DiagnosticStatus.INFO, "Skipped while offline or online discovery is off.")
        }
        checks += DiagnosticCheck(
            "Provider capabilities",
            DiagnosticStatus.INFO,
            providers.descriptors.joinToString("\n") { descriptor ->
                "${descriptor.name}: " + descriptor.capabilities.joinToString { it.name.lowercase() }
            } + "\nOn this device: discovery, streaming, downloads",
        )
        val storage = storageInfo()
        checks += DiagnosticCheck(
            "Storage",
            if (storage.availableBytes > 250L * 1024 * 1024) DiagnosticStatus.OK else DiagnosticStatus.WARN,
            "Downloads ${dev.tether.core.model.formatBytes(storage.appDownloadsBytes)} · artwork ${dev.tether.core.model.formatBytes(storage.artworkBytes)} · " +
                "${dev.tether.core.model.formatBytes(storage.availableBytes)} free",
        )
        checks += DiagnosticCheck(
            "Privacy",
            DiagnosticStatus.INFO,
            "Checks send only a plain HTTP request to provider endpoints. No account, advertising ID or listening history leaves the device.",
        )
        DiagnosticReport(checks = checks)
    }

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val downloadDir = TetherStorage.downloadsDir(context)
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val available = stat?.availableBytes ?: 0L
        val total = stat?.totalBytes ?: 0L
        StorageInfo(
            appDownloadsBytes = downloadDir.listFiles()?.sumOf { it.length() } ?: 0L,
            artworkBytes = artwork.sizeBytes(),
            lyricsBytes = 0L,
            availableBytes = available,
            totalBytes = total,
        )
    }

    suspend fun clearCaches(): Long = withContext(Dispatchers.IO) {
        val artworkFreed = artwork.clear()
        val cachedLyrics = lyrics.cacheCount()
        lyrics.clearAll()
        artworkFreed + cachedLyrics
    }

    private fun checkHttp(label: String, url: String): DiagnosticCheck {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Tether/1.0 diagnostics (no personal data)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    DiagnosticCheck(label, DiagnosticStatus.OK, "HTTP ${response.code} · API reachable")
                } else {
                    DiagnosticCheck(label, DiagnosticStatus.WARN, "HTTP ${response.code} · API reachable but returned an error")
                }
            }
        }.getOrElse { error ->
            DiagnosticCheck(label, DiagnosticStatus.FAIL, "Unreachable: ${error.message ?: "network error"}")
        }
    }
}

object TetherStorage {
    fun downloadsDir(context: Context): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(base, "Tether downloads").apply { mkdirs() }
    }

    fun artworkDir(context: Context): File = File(context.filesDir, "artwork").apply { mkdirs() }
}
