package com.mue.core.network

import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

object SafeStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SafeString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.content
            }
            return null
        }
        return try {
            decoder.decodeString()
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}

@Serializable
data class ArtistThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class ArtistAlbumDto(
    val id: String,
    val title: String,
    @Serializable(with = SafeStringSerializer::class)
    val year: String? = null,
    val artworkUrl: String? = null,
    val type: String? = null
)

@Serializable
data class ArtistTrackDto(
    val id: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val artworkUrl: String? = null,
    val duration: Long? = null,
    val durationMs: Long? = null,
    val provider: String? = null,
    val videoType: String? = null,
    val artistChannelId: String? = null,
    val albumId: String? = null
) {
    fun toTrack(): Track {
        val resolvedId = id ?: videoId ?: java.util.UUID.randomUUID().toString()
        val resolvedTitle = title ?: "Unknown Track"
        val resolvedArtist = if (artists.isNotEmpty()) artists.joinToString(", ") else (artist ?: "Unknown Artist")
        val resolvedDuration = durationMs ?: (duration?.let { it * 1000 } ?: 180000L)
        return Track(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = album ?: "",
            artworkUrl = artworkUrl,
            durationMs = resolvedDuration,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = videoId ?: resolvedId,
            videoType = videoType,
            artistChannelId = artistChannelId,
            albumId = albumId
        )
    }
}

@Serializable
data class ArtistVideoDto(
    val id: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val name: String? = null,
    val channelName: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val thumbnail: String? = null,
    val artworkUrl: String? = null,
    val duration: Long? = null,
    val durationMs: Long? = null,
    val videoType: String? = null,
    val provider: String? = null
) {
    fun toTrack(): Track {
        val resolvedId = videoId ?: id ?: java.util.UUID.randomUUID().toString()
        val resolvedTitle = title ?: name ?: "Video"
        val resolvedChannel = channelName ?: artist ?: "Artist"
        val resolvedArtwork = thumbnail ?: artworkUrl
        val resolvedDuration = durationMs ?: (duration?.let { it * 1000 } ?: 180000L)
        return Track(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedChannel,
            album = "Video",
            artworkUrl = resolvedArtwork,
            durationMs = resolvedDuration,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = resolvedId,
            videoType = videoType ?: "OMV"
        )
    }
}

@Serializable
data class ArtistVideosResponse(
    val videos: List<ArtistVideoDto> = emptyList()
)

@Serializable
data class ArtistDetailDto(
    val id: String,
    val name: String,
    val thumbnails: List<ArtistThumbnailDto> = emptyList(),
    val artworkUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val topTracks: List<ArtistTrackDto> = emptyList(),
    val albums: List<ArtistAlbumDto> = emptyList()
)

@Serializable
data class FollowArtistRequest(
    val artistId: String,
    val artistName: String,
    val thumbnailUrl: String? = null
)

@Serializable
data class FollowedArtistItemDto(
    val artistId: String,
    val artistName: String,
    val thumbnailUrl: String? = null,
    val followedAt: String? = null
)

@Serializable
data class FollowedArtistsResponse(
    val artists: List<FollowedArtistItemDto> = emptyList()
)

@Serializable
data class YtmArtistSearchItemDto(
    val id: String,
    val name: String,
    val subscribers: String? = null,
    val artworkUrl: String? = null,
    val type: String? = null
)

@Serializable
data class YtmArtistSearchResponse(
    val artists: List<YtmArtistSearchItemDto> = emptyList(),
    val query: String? = null
)

@Singleton
class ArtistApiClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getArtistDetails(artistId: String): Result<ArtistDetailDto> = runCatching {
        NetworkConfig.executeWithFallback("/api/youtube/artist/$artistId") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to fetch artist details: HTTP ${response.status.value}")
            }
            response.body<ArtistDetailDto>()
        }
    }

    suspend fun followArtist(artistId: String, artistName: String, thumbnailUrl: String? = null): Result<Unit> = runCatching {
        NetworkConfig.executeWithFallback("/api/artists/follow") { fullUrl ->
            val response = httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(FollowArtistRequest(artistId = artistId, artistName = artistName, thumbnailUrl = thumbnailUrl))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Failed to follow artist: HTTP ${response.status.value}")
            }
        }
    }

    suspend fun unfollowArtist(artistId: String): Result<Unit> = runCatching {
        NetworkConfig.executeWithFallback("/api/artists/unfollow/$artistId") { fullUrl ->
            val response = httpClient.delete(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to unfollow artist: HTTP ${response.status.value}")
            }
        }
    }

    suspend fun getFollowedArtists(): Result<List<FollowedArtistItemDto>> = runCatching {
        NetworkConfig.executeWithFallback("/api/artists/followed") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to fetch followed artists: HTTP ${response.status.value}")
            }
            response.body<FollowedArtistsResponse>().artists
        }
    }

    suspend fun searchArtists(query: String): Result<List<YtmArtistSearchItemDto>> = runCatching {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        NetworkConfig.executeWithFallback("/api/youtube/search?q=$encoded&filter=artists") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to search artists: HTTP ${response.status.value}")
            }
            response.body<YtmArtistSearchResponse>().artists
        }
    }

    suspend fun getArtistVideos(artistId: String): Result<List<ArtistVideoDto>> = runCatching {
        NetworkConfig.executeWithFallback("/api/youtube/artist/$artistId/videos") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to fetch artist videos: HTTP ${response.status.value}")
            }
            response.body<ArtistVideosResponse>().videos
        }
    }
}

