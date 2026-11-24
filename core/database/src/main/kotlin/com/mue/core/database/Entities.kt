package com.mue.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.mue.core.model.ProviderId
import com.mue.core.model.Track

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val provider: String = ProviderId.JIOSAAVN.name,
    val playCount: Int = 0,
    val youtubeVideoId: String? = null,
    val language: String? = null
)

@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongFtsEntity(
    val title: String,
    val artist: String
)

@Entity(tableName = "audio_cache")
data class AudioCacheEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val fileSize: Long,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val status: String, // "PENDING", "DOWNLOADING", "COMPLETED", "FAILED"
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val fileSize: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey val id: String,
    val participantName: String,
    val participantAvatar: String?,
    val lastMessage: String?,
    val updatedAt: Long = System.currentTimeMillis(),
    val isUnread: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val isLocked: Boolean = false
)

@Entity(
    tableName = "chat_messages",
    indices = [androidx.room.Index(value = ["conversationId", "timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String, // Plaintext locally, Ciphertext on network
    val type: String = "TEXT",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENDING", "SENT", "DELIVERED", "READ", "FAILED"
    val expiresAt: Long? = null,
    val isUnsent: Boolean = false,
    val deletedAt: Long? = null
)

@Fts4(contentEntity = ChatMessageEntity::class)
@Entity(tableName = "chat_messages_fts")
data class ChatMessageFtsEntity(
    val content: String
)

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: String,
    val provider: String = ProviderId.JIOSAAVN.name,
    val providerTrackId: String = songId,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val likedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
    val serverId: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val visibility: String = "public",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
    val serverId: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    indices = [androidx.room.Index(value = ["playlistId", "position"])]
)
data class PlaylistTrackEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val position: Int = 0,
    val provider: String = ProviderId.JIOSAAVN.name,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
    val serverId: String? = null
)

@Entity(
    tableName = "playback_history",
    indices = [androidx.room.Index(value = ["playedAt"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey val id: String,
    val provider: String = ProviderId.JIOSAAVN.name,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val playedAt: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long = 0,
    val completed: Boolean = false,
    val isDirty: Boolean = true,
    val serverId: String? = null,
    val completionPercentage: Int = 0,
    val skippedFast: Boolean = false,
    val language: String? = null
)

@Entity(tableName = "chat_outbox")
data class ChatOutboxEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val ciphertext: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isPrivate: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0
)

@Entity(tableName = "follows")
data class FollowEntity(
    @PrimaryKey val userId: String, // The user being followed
    val isMutual: Boolean = false,
    val status: String = "ACTIVE" // ACTIVE, PENDING
)

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val userId: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String, // FOLLOW, LIKE, MENTION
    val actorId: String,
    val actorName: String,
    val content: String,
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "imported_playlists")
data class ImportedPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val source: String,
    val importedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
    val serverId: String? = null
)

@Entity(
    tableName = "imported_playlist_tracks",
    indices = [androidx.room.Index(value = ["importedPlaylistId", "position"])]
)
data class ImportedPlaylistTrackEntity(
    @PrimaryKey val id: String,
    val importedPlaylistId: String,
    val position: Int = 0,
    val provider: String = "youtube",
    val providerTrackId: String,
    val title: String,
    val artists: String,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val jiosaavnTrackId: String? = null
)

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val followedAt: Long = System.currentTimeMillis()
)

fun ImportedPlaylistTrackEntity.toTrack(): Track = Track(
    id = jiosaavnTrackId ?: providerTrackId,
    title = title,
    artist = artists,
    album = "",
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    provider = if (provider.equals("spotify", ignoreCase = true)) ProviderId.JIOSAAVN else ProviderId.YOUTUBE_MUSIC
)

fun SongEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    provider = try { ProviderId.valueOf(provider) } catch (_: Exception) { ProviderId.JIOSAAVN },
    youtubeVideoId = youtubeVideoId,
    language = language
)

fun Track.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    provider = provider.name,
    youtubeVideoId = youtubeVideoId,
    language = language
)

fun LikedSongEntity.toTrack(): Track = Track(
    id = songId,
    title = title,
    artist = artist,
    album = album ?: "",
    artworkUrl = artworkUrl ?: "",
    durationMs = durationMs,
    provider = try { ProviderId.valueOf(provider) } catch (_: Exception) { ProviderId.JIOSAAVN }
)

fun Track.toLikedSongEntity(): LikedSongEntity = LikedSongEntity(
    songId = id,
    provider = provider.name,
    providerTrackId = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs
)

fun PlaybackHistoryEntity.toTrack(): Track = Track(
    id = providerTrackId,
    title = title,
    artist = artist,
    album = album ?: "",
    artworkUrl = artworkUrl ?: "",
    durationMs = durationMs,
    provider = try { ProviderId.valueOf(provider) } catch (_: Exception) { ProviderId.JIOSAAVN }
)

fun DownloadEntity.toTrack(): Track = Track(
    id = songId,
    title = title,
    artist = artist,
    album = "",
    artworkUrl = artworkUrl ?: "",
    durationMs = durationMs,
    provider = ProviderId.LOCAL
)
