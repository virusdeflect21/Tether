package dev.tether.core.data

import android.content.Context
import dev.tether.core.database.TetherDatabase

/**
 * Small explicit dependency graph. The app has one database and one repository
 * set shared by activities, the media service and background download workers.
 */
class TetherGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val database: TetherDatabase = TetherDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val metadata = MetadataReader(appContext)
    val artwork = ArtworkStore(appContext, metadata)
    val providers = ProviderRegistry(appContext)
    val library = LibraryRepository(appContext, database, metadata, artwork, settings)
    val playlists = PlaylistRepository(appContext, database, library)
    val search = SearchRepository(database, library, playlists, providers, settings)
    val lyrics = LyricsRepository(appContext, database, library, settings)
    val backup = BackupRepository(appContext, database, library, playlists, settings)
    val diagnostics = DiagnosticsRepository(appContext, database, library, providers, settings, artwork, lyrics)
}

object Tether {
    @Volatile
    private var graph: TetherGraph? = null

    fun graph(context: Context): TetherGraph {
        val existing = graph
        if (existing != null) return existing
        return synchronized(this) {
            graph ?: TetherGraph(context.applicationContext).also { graph = it }
        }
    }

    fun peek(): TetherGraph? = graph
}
