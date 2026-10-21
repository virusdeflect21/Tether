package dev.tether.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        FavoriteCollectionEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        LyricsEntity::class,
        SearchHistoryEntity::class,
        MediaFolderEntity::class,
        PlayerStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TetherDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteCollectionDao(): FavoriteCollectionDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun folderDao(): FolderDao
    abstract fun playerStateDao(): PlayerStateDao

    companion object {
        const val NAME = "tether.db"

        fun create(context: Context): TetherDatabase =
            Room.databaseBuilder(context.applicationContext, TetherDatabase::class.java, NAME)
                .build()
    }
}
