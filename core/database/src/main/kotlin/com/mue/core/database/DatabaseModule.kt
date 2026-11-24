package com.mue.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_conversations ADD COLUMN isUnread INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chat_conversations ADD COLUMN readReceiptsEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE chat_conversations ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_playlists (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    artworkUrl TEXT,
                    trackCount INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    importedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE liked_songs ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE liked_songs ADD COLUMN serverId TEXT DEFAULT NULL")

            db.execSQL("ALTER TABLE playlists ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE playlists ADD COLUMN serverId TEXT DEFAULT NULL")

            db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN serverId TEXT DEFAULT NULL")

            db.execSQL("ALTER TABLE playback_history ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE playback_history ADD COLUMN serverId TEXT DEFAULT NULL")

            db.execSQL("ALTER TABLE imported_playlists ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE imported_playlists ADD COLUMN serverId TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_playlist_tracks (
                    id TEXT PRIMARY KEY NOT NULL,
                    importedPlaylistId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    provider TEXT NOT NULL,
                    providerTrackId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artists TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    artworkUrl TEXT,
                    jiosaavnTrackId TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_playlist_tracks_importedPlaylistId_position ON imported_playlist_tracks(importedPlaylistId, position)")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN youtubeVideoId TEXT")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_history ADD COLUMN completionPercentage INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playback_history ADD COLUMN skippedFast INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS followed_artists (
                    artistId TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    thumbnailUrl TEXT,
                    followedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN language TEXT")
            db.execSQL("ALTER TABLE playback_history ADD COLUMN language TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MuEDatabase =
        Room.databaseBuilder(context, MuEDatabase::class.java, "mue.db")
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .build()

    @Provides
    fun provideSongDao(db: MuEDatabase): SongDao = db.songDao()

    @Provides
    fun provideAudioCacheDao(db: MuEDatabase): AudioCacheDao = db.audioCacheDao()

    @Provides
    fun provideDownloadDao(db: MuEDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideChatDao(db: MuEDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideSocialDao(db: MuEDatabase): SocialDao = db.socialDao()

    @Provides
    fun provideLibraryDao(db: MuEDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun provideImportedPlaylistDao(db: MuEDatabase): ImportedPlaylistDao = db.importedPlaylistDao()

    @Provides
    fun provideImportedPlaylistTrackDao(db: MuEDatabase): ImportedPlaylistTrackDao = db.importedPlaylistTrackDao()

    @Provides
    fun provideSyncDao(db: MuEDatabase): SyncDao = db.syncDao()

    @Provides
    fun provideFollowedArtistDao(db: MuEDatabase): FollowedArtistDao = db.followedArtistDao()
}
