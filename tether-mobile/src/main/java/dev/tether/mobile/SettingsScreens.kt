package dev.tether.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tether.core.data.DiagnosticCheck
import dev.tether.core.data.DiagnosticReport
import dev.tether.core.data.DiagnosticStatus
import dev.tether.core.data.Tether
import dev.tether.core.design.TetherBadge
import dev.tether.core.design.TetherMark
import dev.tether.core.design.TetherWordmark
import dev.tether.core.model.DownloadQuality
import dev.tether.core.model.QueueAction
import dev.tether.core.model.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MobileViewModel,
    hasAudioPermission: Boolean,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val mediaFolders by viewModel.mediaFolders.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showClearHistory by rememberSaveable { mutableStateOf(false) }
    var showClearCaches by rememberSaveable { mutableStateOf(false) }
    var showAccent by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.backupTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreFrom(uri)
    }

    Column(Modifier.fillMaxSize()) {
        MobileTopBar(title = "Settings", onBack = onBack)
        if (busy) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                Text(
                    "Working\\u2026",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 34.dp),
        ) {
            item {
                SettingsHeader(
                    trackCount = stats.trackCount,
                    folderCount = mediaFolders.size,
                    onOpenAbout = onOpenAbout,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            item {
                SettingsSection("Appearance", Icons.Rounded.Palette) {
                    SettingsChoiceRow(
                        label = "Theme",
                        options = ThemeMode.entries.map { it.name to it.label() },
                        selected = settings.themeMode.name,
                        onSelect = { value -> viewModel.setThemeMode(ThemeMode.valueOf(value)) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        "Accent",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                    AccentPicker(
                        selected = settings.customAccent,
                        onSelect = { viewModel.setAccent(it) },
                        onCustom = { showAccent = true },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Show library statistics",
                        subtitle = "Track counts, listening duration and offline storage on Home.",
                        checked = settings.showPlaybackStats,
                        onCheckedChange = viewModel::setShowStats,
                    )
                }
            }
            item {
                SettingsSection("Playback", Icons.Rounded.Speed) {
                    ToggleRow(
                        icon = Icons.Rounded.Speed,
                        title = "Gapless playback",
                        subtitle = "Let the player preload the next local track when possible.",
                        checked = settings.gaplessEnabled,
                        onCheckedChange = viewModel::setGapless,
                    )
                    ToggleRow(
                        icon = Icons.Rounded.PlayArrow,
                        title = "Autoplay next",
                        subtitle = "Continue to the next queued track automatically.",
                        checked = settings.autoplayNext,
                        onCheckedChange = viewModel::setAutoplayNext,
                    )
                    ToggleRow(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Keep queue on exit",
                        subtitle = "Restore the playback queue after the app is reopened.",
                        checked = settings.keepQueueOnExit,
                        onCheckedChange = viewModel::setKeepQueue,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsChoiceRow(
                        label = "When a track is chosen",
                        options = QueueAction.entries.map { it.name to it.label() },
                        selected = settings.queueAction.name,
                        onSelect = { viewModel.setQueueAction(QueueAction.valueOf(it)) },
                    )
                }
            }
            item {
                SettingsSection("Library", Icons.Rounded.LibraryMusic) {
                    ToggleRow(
                        icon = Icons.Rounded.Search,
                        title = "Scan device at startup",
                        subtitle = "Index audio visible through Android media permissions. This never uploads files.",
                        checked = settings.scanMediaStoreOnStartup,
                        onCheckedChange = viewModel::setScanOnStartup,
                    )
                    ToggleRow(
                        icon = Icons.Rounded.Search,
                        title = "Save search history",
                        subtitle = "Keep recent query text locally. Clear it any time.",
                        checked = settings.searchHistoryEnabled,
                        onCheckedChange = viewModel::setSearchHistory,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Headphones,
                        title = if (hasAudioPermission) "Music access granted" else "Allow music access",
                        subtitle = if (hasAudioPermission) {
                            "Android media audio permission is active."
                        } else {
                            "Grant access to scan audio already on this device, or use folders only."
                        },
                        onClick = if (hasAudioPermission) null else onRequestPermission,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Folder,
                        title = "Add a music folder",
                        subtitle = "Choose a folder with the system picker. Tether only reads the folders you choose.",
                        onClick = onAddFolder,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Upload,
                        title = "Import individual audio files",
                        subtitle = "Add a few songs without granting access to your whole library.",
                        onClick = onImportFiles,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Refresh,
                        title = "Rescan device audio",
                        subtitle = "Look for new or removed music on this device. Playlists stay intact.",
                        onClick = {
                            if (hasAudioPermission) viewModel.scanMediaStore() else onRequestPermission()
                        },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Delete,
                        title = "Clear playback history",
                        subtitle = "Removes the recently played list. Favorites and playlists stay.",
                        onClick = { showClearHistory = true },
                    )
                }
            }
            item {
                SettingsSection("Online providers", Icons.Rounded.CloudDownload) {
                    ToggleRow(
                        icon = Icons.Rounded.CloudDownload,
                        title = "Online discovery",
                        subtitle = "Browse Creative Commons music from ccMixter. Your local library works offline.",
                        checked = settings.onlineDiscoveryEnabled,
                        onCheckedChange = viewModel::setOnlineDiscovery,
                    )
                    ToggleRow(
                        icon = Icons.Rounded.Search,
                        title = "Lyrics from LRCLIB",
                        subtitle = "Find synced or plain lyrics. Imported .lrc files always take priority.",
                        checked = settings.lrclibEnabled,
                        onCheckedChange = viewModel::setLrclib,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsChoiceRow(
                        label = "Download quality",
                        options = DownloadQuality.entries.map { it.name to it.label },
                        selected = settings.downloadQuality.name,
                        onSelect = { viewModel.setDownloadQuality(DownloadQuality.valueOf(it)) },
                    )
                    Text(
                        settings.downloadQuality.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                    Text(
                        "Available quality depends on the source and its license. Tether uses the best file the provider offers.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            item {
                SettingsSection("Storage & data", Icons.Rounded.Storage) {
                    SettingsRow(
                        icon = Icons.Rounded.Storage,
                        title = "Downloads and cache",
                        subtitle = "${dev.tether.core.model.formatBytes(storage.appDownloadsBytes)} downloads \u00b7 ${dev.tether.core.model.formatBytes(storage.artworkBytes)} artwork",
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Upload,
                        title = "Export backup",
                        subtitle = "Save playlists, favorites, and settings as a JSON file. Audio files are not included.",
                        onClick = { exportLauncher.launch("tether-backup.json") },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Download,
                        title = "Restore backup",
                        subtitle = "Merge playlists and settings from a Tether backup.",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Delete,
                        title = "Clear artwork and lyric caches",
                        subtitle = "Remove cached artwork and saved lyrics. Music and downloads stay.",
                        onClick = { showClearCaches = true },
                    )
                }
            }
            item {
                SettingsSection("About", Icons.Rounded.Info) {
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = "About Tether",
                        subtitle = "What the app does, what it does not do, and the mobile/TV feature split.",
                        onClick = onOpenAbout,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Shield,
                        title = "Licenses & credits",
                        subtitle = "Open-source libraries, provider attribution and app license.",
                        onClick = onOpenLicenses,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Headphones,
                        title = "Diagnostics",
                        subtitle = "Check permissions, storage, network, and provider status.",
                        onClick = onOpenDiagnostics,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = "Version",
                        trailingText = BuildConfig.VERSION_NAME,
                    )
                    Text(
                        "Tether does not require an account and does not include advertising or analytics SDKs.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (showClearHistory) {
        ConfirmDialog(
            title = "Clear playback history?",
            message = "The recently played row will be emptied. This does not delete any file.",
            confirmLabel = "Clear",
            destructive = true,
            onDismiss = { showClearHistory = false },
            onConfirm = viewModel::clearPlaybackHistory,
        )
    }
    if (showClearCaches) {
        ConfirmDialog(
            title = "Clear caches?",
            message = "Artwork thumbnails and cached service lyrics will be removed. Downloaded audio and library entries stay.",
            confirmLabel = "Clear",
            onDismiss = { showClearCaches = false },
            onConfirm = {
                scope.launch {
                    val freed = viewModel.clearCaches()
                    viewModel.notify("Cleared ${dev.tether.core.model.formatBytes(freed)} of cache")
                }
            },
        )
    }
    if (showAccent) {
        AccentDialog(
            initial = settings.customAccent.orEmpty(),
            onDismiss = { showAccent = false },
            onConfirm = { hex ->
                viewModel.setAccent(hex)
                showAccent = false
            },
        )
    }
}

@Composable
private fun SettingsHeader(
    trackCount: Int,
    folderCount: Int,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .clickable(onClick = onOpenAbout)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TetherMark(52.dp)
            Column(Modifier.weight(1f)) {
                TetherWordmark(titleSize = 22, showTagline = false)
                Text(
                    "$trackCount tracks \u00b7 $folderCount saved " +
                        if (folderCount == 1) "folder" else "folders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Tether Mobile ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TetherBadge("LOCAL FIRST")
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
        )
        FilterChips(options = options, selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun AccentPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    onCustom: () -> Unit,
) {
    val presets = listOf(
        null to "Default",
        "#8FA6FF" to "Blue",
        "#C3A6FF" to "Violet",
        "#F0A6FF" to "Magenta",
        "#8DE3C0" to "Mint",
        "#FFD18A" to "Amber",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { (hex, label) ->
            val color = hex?.let { parseHex(it) } ?: MaterialTheme.colorScheme.primary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(hex) }
                    .padding(2.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (hex == null) {
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                                )
                            } else {
                                Brush.linearGradient(listOf(color, color))
                            },
                        )
                        .border(
                            width = if (selected == hex) 2.dp else 1.dp,
                            color = if (selected == hex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontSize = 9.sp,
                )
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCustom),
            contentAlignment = Alignment.Center,
        ) {
            Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AccentDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = value.isBlank() || Regex("^#[0-9A-Fa-f]{6}$").matches(value.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter a six-digit hex color. Leave blank to use the default blue-violet accent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(7) },
                    label = { Text("#8FA6FF") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text(if (valid) "Used for highlights and controls." else "Use #RRGGBB format.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim().ifBlank { null }) },
                enabled = valid,
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF8FA6FF))

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
}

private fun QueueAction.label(): String = when (this) {
    QueueAction.PLAY_NOW -> "Play now"
    QueueAction.PLAY_NEXT -> "Play next"
    QueueAction.ADD_TO_QUEUE -> "Add to queue"
}

@Composable
fun DiagnosticsScreen(
    viewModel: MobileViewModel,
    onBack: () -> Unit,
) {
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        loading = true
        report = runCatching { viewModel.runDiagnostics() }.getOrNull()
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        MobileTopBar(
            title = "Diagnostics",
            subtitle = "Facts only, no secret values",
            onBack = onBack,
            actions = {
                if (!loading) {
                    TextButton(onClick = { reload++ }) { Text("Run again") }
                }
            },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else {
            val current = report
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item {
                    Text(
                        "Checks permissions, storage, the local database, provider connections, and saved lyrics. No passwords or full file paths are shown.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (current == null) {
                        TetherErrorCard("Diagnostics could not run.")
                    } else {
                        Text(
                            "Generated ${dev.tether.core.model.formatDate(current.generatedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        TetherBadge(
                            if (current.hasFailure) "ATTENTION NEEDED" else "CHECKS COMPLETE",
                            color = if (current.hasFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (current != null) {
                    items(current.checks) { check -> DiagnosticRow(check) }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Tether diagnostics", current.asText()))
                                viewModel.notify("Diagnostic report copied")
                            },
                        ) { Text("Copy diagnostic report") }
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(check: DiagnosticCheck) {
    val color = when (check.status) {
        DiagnosticStatus.OK -> MaterialTheme.colorScheme.secondary
        DiagnosticStatus.WARN -> Color(0xFFFFD18A)
        DiagnosticStatus.FAIL -> MaterialTheme.colorScheme.error
        DiagnosticStatus.INFO -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(check.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        check.status.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MobileTopBar(title = "About Tether", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TetherMark(62.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    TetherWordmark(titleSize = 28)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Tether plays the music you own and independent music shared under Creative Commons. No account, no ads, no analytics.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AboutBullet("Plays offline", "Your device library, chosen folders, imported files, and saved downloads work without a connection.")
            AboutBullet("Open discovery", "Find Creative Commons music from ccMixter and lyrics from LRCLIB. If a service is down, Tether tells you.")
            AboutBullet("Downloads you can trust", "Tether saves a track only when its license allows it and the artist provides a downloadable file.")
            AboutBullet("Playlists and backups", "Build playlists from real library tracks. Backups save your playlists and settings, never your audio.")
            AboutBullet("Tether on TV", "Install Tether TV for a big-screen player with D-pad navigation and cover, lyrics, and video modes.")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Your library stays on this device. Tether goes online only for discovery, lyrics, and downloads you start. No advertising ID is collected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenLicenses) { Text("Open licenses and credits") }
            Text(
                "Tether is an independent project. Provider catalogs and license terms are controlled by their respective services and artists.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutBullet(title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val libraries = listOf(
        "AndroidX / Jetpack Compose" to "Apache License 2.0",
        "Media3 / ExoPlayer" to "Apache License 2.0",
        "Room" to "Apache License 2.0",
        "DataStore" to "Apache License 2.0",
        "WorkManager" to "Apache License 2.0",
        "Kotlin & kotlinx.coroutines" to "Apache License 2.0",
        "OkHttp" to "Apache License 2.0",
        "Coil" to "Apache License 2.0",
        "Material Icons" to "Apache License 2.0",
        "Poppins typeface" to "SIL Open Font License 1.1",
    )
    Column(Modifier.fillMaxSize()) {
        MobileTopBar(title = "Licenses & credits", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Tether", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Copyright the Tether contributors. Licensed under the MIT License. The license text is included with the source distribution.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Open-source libraries",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(libraries) { (name, license) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(license, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Music and lyrics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "ccMixter is a community music service. Each track keeps the Creative Commons license and creator attribution supplied by ccMixter. Tether does not relicense provider content.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "LRCLIB provides community lyrics metadata. Lyrics remain attributable to their source. Tether does not invent or rewrite missing lyrics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "No code, artwork or brand assets from InnerTune or BeatWave are included. The feather mark is the Tether project's own brand asset.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun TetherErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(14.dp),
        )
    }
}
