package com.mue.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        AudioCacheEntity::class,
        DownloadEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class,
        ChatMessageFtsEntity::class,
        ChatOutboxEntity::class,
        UserEntity::class,
        FollowEntity::class,
        BlockEntity::class,
        NotificationEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaybackHistoryEntity::class,
        ImportedPlaylistEntity::class,
        ImportedPlaylistTrackEntity::class,
        FollowedArtistEntity::class,
        SearchHistoryEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class MuEDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun audioCacheDao(): AudioCacheDao
    abstract fun downloadDao(): DownloadDao
    abstract fun chatDao(): ChatDao
    abstract fun socialDao(): SocialDao
    abstract fun libraryDao(): LibraryDao
    abstract fun importedPlaylistDao(): ImportedPlaylistDao
    abstract fun importedPlaylistTrackDao(): ImportedPlaylistTrackDao
    abstract fun syncDao(): SyncDao
    abstract fun followedArtistDao(): FollowedArtistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
