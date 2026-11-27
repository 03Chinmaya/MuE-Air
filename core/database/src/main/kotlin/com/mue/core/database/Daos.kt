package com.mue.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY id DESC LIMIT 20")
    fun getRecentSongs(): Flow<List<SongEntity>>

    @Query("""
        SELECT songs.* FROM songs 
        JOIN songs_fts ON songs.id = songs_fts.rowid 
        WHERE songs_fts MATCH :query
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)

    @Query("SELECT playCount FROM songs WHERE id = :songId")
    suspend fun getPlayCount(songId: String): Int?

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT 21")
    fun getTop21MostPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("UPDATE songs SET youtubeVideoId = :videoId WHERE id = :songId")
    suspend fun updateYoutubeVideoId(songId: String, videoId: String)
}

@Dao
interface AudioCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: AudioCacheEntity)

    @Query("SELECT * FROM audio_cache WHERE songId = :songId")
    suspend fun getBySongId(songId: String): AudioCacheEntity?

    @Query("SELECT * FROM audio_cache ORDER BY lastAccessedAt ASC")
    suspend fun getAllSortedByLru(): List<AudioCacheEntity>

    @Query("SELECT * FROM audio_cache ORDER BY lastAccessedAt DESC")
    fun observeAllSortedByLru(): Flow<List<AudioCacheEntity>>

    @Query("SELECT COUNT(*) FROM audio_cache")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM audio_cache WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM audio_cache")
    suspend fun deleteAll()

    @Query("SELECT SUM(fileSize) FROM audio_cache")
    suspend fun getTotalSizeBytes(): Long?
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun getBySongId(songId: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET status = :status WHERE songId = :songId")
    suspend fun updateStatus(songId: String, status: String)

    @Query("UPDATE downloads SET status = :status, fileSize = :fileSize WHERE songId = :songId")
    suspend fun updateProgress(songId: String, status: String, fileSize: Long)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getDownloadsCount(): Flow<Int>
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversationEntity)

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: String): Flow<ChatConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(outbox: ChatOutboxEntity)

    @Query("SELECT * FROM chat_outbox ORDER BY createdAt ASC")
    suspend fun getPendingOutbox(): List<ChatOutboxEntity>

    @Query("DELETE FROM chat_outbox WHERE id = :id")
    suspend fun deleteOutboxItem(id: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE chat_messages SET isUnsent = 1, content = 'Message unsent' WHERE id = :messageId")
    suspend fun markMessageUnsent(messageId: String)

    @Query("SELECT * FROM chat_messages_fts WHERE content MATCH :query")
    fun searchMessages(query: String): Flow<List<ChatMessageFtsEntity>>

    @Query("UPDATE chat_conversations SET isUnread = :isUnread WHERE id = :id")
    suspend fun setUnread(id: String, isUnread: Boolean)

    @Query("UPDATE chat_conversations SET isLocked = :isLocked WHERE id = :id")
    suspend fun setLocked(id: String, isLocked: Boolean)

    @Query("UPDATE chat_conversations SET readReceiptsEnabled = :enabled WHERE id = :id")
    suspend fun setReadReceipts(id: String, enabled: Boolean)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND type IN ('IMAGE', 'VIDEO', 'DOCUMENT', 'AUDIO') ORDER BY timestamp DESC")
    fun getMediaForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT chat_messages.* FROM chat_messages JOIN chat_messages_fts ON chat_messages.rowid = chat_messages_fts.docid WHERE chat_messages.conversationId = :conversationId AND chat_messages_fts MATCH :query")
    fun searchMessagesInConversation(conversationId: String, query: String): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpiredMessages(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM chat_conversations")
    suspend fun clearAllConversations()
}

@Dao
interface SocialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUser(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Query("SELECT * FROM follows WHERE userId = :userId")
    suspend fun getFollow(userId: String): FollowEntity?

    @Query("DELETE FROM follows WHERE userId = :userId")
    suspend fun deleteFollow(userId: String)

    @Query("SELECT * FROM follows")
    fun getAllFollows(): Flow<List<FollowEntity>>

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE userId = :userId AND isMutual = 1)")
    suspend fun isMutualFollow(userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: BlockEntity)

    @Query("DELETE FROM blocks WHERE userId = :userId")
    suspend fun deleteBlock(userId: String)

    @Query("SELECT * FROM blocks")
    suspend fun getAllBlocks(): List<BlockEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocks WHERE userId = :userId)")
    suspend fun isBlocked(userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getNotifications(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun getLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT COUNT(*) FROM liked_songs")
    fun getLikedSongsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteLikedSong(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)")
    fun isSongLiked(songId: String): Flow<Boolean>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlists")
    fun getPlaylistsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistTracks(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND id = :trackId")
    suspend fun deletePlaylistTrack(playlistId: String, trackId: String)

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 200")
    suspend fun getRecentHistoryList(): List<PlaybackHistoryEntity>

    @Query("SELECT COUNT(*) FROM playback_history")
    fun getRecentHistoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("UPDATE playback_history SET skippedFast = 1 WHERE id = :historyId")
    suspend fun markSkippedFast(historyId: String)

    @Query("UPDATE playback_history SET completionPercentage = :percentage WHERE id = :historyId")
    suspend fun updateCompletion(historyId: String, percentage: Int)

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getDownloadsCount(): Flow<Int>
}

typealias HistoryDao = LibraryDao

@Dao
interface ImportedPlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: ImportedPlaylistEntity)

    @Query("DELETE FROM imported_playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM imported_playlists ORDER BY importedAt DESC")
    fun getAll(): Flow<List<ImportedPlaylistEntity>>
}

@Dao
interface ImportedPlaylistTrackDao {
    @Query("SELECT * FROM imported_playlist_tracks WHERE importedPlaylistId = :playlistId ORDER BY position ASC")
    fun getTracksForPlaylist(playlistId: String): Flow<List<ImportedPlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<ImportedPlaylistTrackEntity>)

    @Query("DELETE FROM imported_playlist_tracks WHERE importedPlaylistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: String)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM liked_songs WHERE isDirty = 1")
    suspend fun getDirtyLikes(): List<LikedSongEntity>

    @Query("SELECT * FROM playlists WHERE isDirty = 1")
    suspend fun getDirtyPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_tracks WHERE isDirty = 1")
    suspend fun getDirtyPlaylistTracks(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playback_history WHERE isDirty = 1")
    suspend fun getDirtyHistory(): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM imported_playlists WHERE isDirty = 1")
    suspend fun getDirtyImportedPlaylists(): List<ImportedPlaylistEntity>

    @Query("UPDATE liked_songs SET isDirty = 0, serverId = COALESCE(:serverId, serverId) WHERE songId = :songId")
    suspend fun markLikedSongSynced(songId: String, serverId: String? = null)

    @Query("UPDATE playlists SET isDirty = 0, serverId = COALESCE(:serverId, serverId) WHERE id = :id")
    suspend fun markPlaylistSynced(id: String, serverId: String? = null)

    @Query("UPDATE playlist_tracks SET isDirty = 0, serverId = COALESCE(:serverId, serverId) WHERE id = :id")
    suspend fun markPlaylistTrackSynced(id: String, serverId: String? = null)

    @Query("UPDATE playback_history SET isDirty = 0, serverId = COALESCE(:serverId, serverId) WHERE id = :id")
    suspend fun markHistorySynced(id: String, serverId: String? = null)

    @Query("UPDATE imported_playlists SET isDirty = 0, serverId = COALESCE(:serverId, serverId) WHERE id = :id")
    suspend fun markImportedPlaylistSynced(id: String, serverId: String? = null)

    @Query("SELECT isDirty FROM liked_songs WHERE songId = :songId")
    suspend fun isLikedSongDirty(songId: String): Boolean?

    @Query("SELECT isDirty FROM playlists WHERE id = :id")
    suspend fun isPlaylistDirty(id: String): Boolean?

    @Query("SELECT isDirty FROM playlist_tracks WHERE id = :id")
    suspend fun isPlaylistTrackDirty(id: String): Boolean?

    @Query("SELECT isDirty FROM playback_history WHERE id = :id")
    suspend fun isHistoryDirty(id: String): Boolean?

    @Query("SELECT isDirty FROM imported_playlists WHERE id = :id")
    suspend fun isImportedPlaylistDirty(id: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLikedSong(song: LikedSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTrack(track: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: PlaybackHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedPlaylist(playlist: ImportedPlaylistEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteLikedSong(songId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND (id = :trackId OR providerTrackId = :trackId)")
    suspend fun deletePlaylistTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playback_history WHERE id = :id OR providerTrackId = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM imported_playlists WHERE id = :id")
    suspend fun deleteImportedPlaylist(id: String)
}

@Dao
interface FollowedArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: FollowedArtistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<FollowedArtistEntity>)

    @Query("DELETE FROM followed_artists WHERE artistId = :artistId")
    suspend fun delete(artistId: String)

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun getAll(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE artistId = :artistId)")
    fun isFollowing(artistId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE artistId = :artistId)")
    suspend fun isFollowingSync(artistId: String): Boolean
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY lastPlayedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :q")
    suspend fun remove(q: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun count(): Int

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY lastPlayedAt DESC LIMIT 20)")
    suspend fun trimTo20()
}
