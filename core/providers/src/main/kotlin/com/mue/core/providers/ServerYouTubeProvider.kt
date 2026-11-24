package com.mue.core.providers

import android.util.Log
import com.mue.core.model.PlaybackSource
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.network.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

object ServerSafeIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SafeInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.intOrNull ?: element.content.toIntOrNull()
            }
            return null
        }
        return try {
            decoder.decodeInt()
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value != null) {
            encoder.encodeInt(value)
        } else {
            encoder.encodeNull()
        }
    }
}

@Serializable
data class YtmTrackDto(
    val id: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    @Serializable(with = ServerSafeIntSerializer::class)
    val year: Int? = null,
    val views: String? = null,
    val artworkUrl: String? = null,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val durationMs: Long? = null,
    val provider: String? = null,
    val language: String? = null,
    val videoType: String? = null,
    val artistChannelId: String? = null,
    val albumId: String? = null
) {
    fun toTrack(): Track {
        val resolvedId = id ?: videoId ?: java.util.UUID.randomUUID().toString()
        val resolvedTitle = title ?: name ?: "Unknown"
        val cleaned = artists.filter { it.isNotBlank() && it != "Unknown Artist" }
        val resolvedArtist = when {
            cleaned.isNotEmpty() -> cleaned.joinToString(", ")
            !artist.isNullOrBlank() && artist != "Unknown Artist" -> artist
            else -> "Unknown Artist"
        }
        val resolvedArtwork = artworkUrl ?: thumbnail
        val resolvedDuration = durationMs ?: (duration?.let { it * 1000 } ?: 0L)
        return Track(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = album ?: "",
            artworkUrl = resolvedArtwork,
            durationMs = resolvedDuration,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = videoId ?: resolvedId,
            year = year,
            language = language,
            videoType = videoType,
            artistChannelId = artistChannelId,
            albumId = albumId,
            isOfficial = videoType == "ATV" || videoType == "OMV"
        )
    }
}

@Serializable
data class YtmPlaylistDto(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val subtitle: String? = null,
    val author: String? = null,
    val artworkUrl: String? = null,
    val thumbnail: String? = null,
    val type: String? = null
) {
    fun toTrack(): Track {
        val resolvedId = id ?: java.util.UUID.randomUUID().toString()
        val resolvedTitle = title ?: name ?: "Featured Playlist"
        val resolvedArtist = subtitle ?: author ?: "Community Playlist"
        val resolvedArtwork = artworkUrl ?: thumbnail
        return Track(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = "Community Playlists",
            artworkUrl = resolvedArtwork,
            durationMs = 0L,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = resolvedId
        )
    }
}

@Serializable
data class YtmAlbumDto(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val subtitle: String? = null,
    @Serializable(with = ServerSafeIntSerializer::class)
    val year: Int? = null,
    val views: String? = null,
    val artworkUrl: String? = null,
    val type: String? = null
) {
    fun toTrack(): Track {
        val resolvedId = id ?: java.util.UUID.randomUUID().toString()
        val resolvedTitle = title ?: name ?: "Album"
        val resolvedArtist = subtitle ?: "Featured Artist"
        return Track(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedTitle,
            artworkUrl = artworkUrl,
            durationMs = 0L,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = resolvedId,
            year = year,
            type = type ?: "album"
        )
    }
}

@Serializable
data class YtmStreamResponse(
    val streamUrl: String? = null
)

@Serializable
data class YtmNewReleasesResponse(
    val releases: List<YtmAlbumDto> = emptyList(),
    val trending: List<YtmTrackDto> = emptyList()
)

@Serializable
data class YtmAlbumsResponse(
    val releases: List<YtmAlbumDto> = emptyList()
)

@Serializable
data class YtmPlaylistsResponse(
    val playlists: List<YtmPlaylistDto> = emptyList()
)

@Serializable
data class YtmTrendingResponse(
    val tracks: List<YtmTrackDto> = emptyList()
)

@Serializable
data class YtmTopResultDto(
    val type: String,
    val data: JsonElement? = null
)

@Serializable
data class YtmSearchResponse(
    val topResult: YtmTopResultDto? = null,
    val tracks: List<YtmTrackDto> = emptyList(),
    val videos: List<YtmTrackDto> = emptyList(),
    val albums: List<YtmAlbumDto> = emptyList(),
    val artists: List<com.mue.core.network.YtmArtistSearchItemDto> = emptyList(),
    val playlists: List<YtmPlaylistDto> = emptyList(),
    val continuation: String? = null,
    val continuations: Map<String, String?>? = null,
    val query: String? = null
)

/**
 * ServerYouTubeProvider:
 * Client wrapper for backend YouTube endpoints (powered by server-side youtubei.js + ytmusic-api).
 * Used as fallback in the provider chain before local media.
 */
@Singleton
class ServerYouTubeProvider @Inject constructor(
    private val httpClient: HttpClient
) : MusicProvider {
    override val providerId: ProviderId = ProviderId.YOUTUBE_MUSIC
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    companion object {
        private const val TAG = "ServerYouTubeProvider"

        @Volatile
        var resolvedBaseUrl: String? = null
    }

    private suspend fun fetchJson(fullUrl: String): String {
        val response: HttpResponse = httpClient.get(fullUrl)
        if (response.status == HttpStatusCode.OK) {
            val body = response.bodyAsText()
            if (body.isNotBlank()) return body
        }
        throw IOException("HTTP ${response.status} or empty body from $fullUrl")
    }

    private suspend fun executeGet(path: String): String = coroutineScope {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val cached = resolvedBaseUrl
        if (cached != null) {
            val cachedResult = runCatching {
                fetchJson("${cached.trimEnd('/')}$normalizedPath")
            }
            if (cachedResult.isSuccess) {
                return@coroutineScope cachedResult.getOrThrow()
            } else {
                resolvedBaseUrl = null
            }
        }

        val candidates = NetworkConfig.candidateBaseUrls
        val deferreds = candidates.map { baseUrl ->
            async(Dispatchers.IO) {
                runCatching {
                    val body = fetchJson("${baseUrl.trimEnd('/')}$normalizedPath")
                    resolvedBaseUrl = baseUrl
                    NetworkConfig.setDebugBaseUrl(baseUrl)
                    body
                }
            }
        }
        deferreds.awaitAll().firstOrNull { it.isSuccess }?.getOrThrow()
            ?: throw IOException("All candidate base URLs failed for $path")
    }

    override suspend fun search(query: String): List<Track> {
        return search(query, null)
    }

    suspend fun search(query: String, filter: String? = null, engine: String? = null): List<Track> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val filterParam = if (!filter.isNullOrBlank()) "&filter=$filter" else ""
        val engineParam = if (!engine.isNullOrBlank()) "&engine=$engine" else ""
        return try {
            val responseBody = executeGet("/api/youtube/search?q=$encoded$filterParam$engineParam")
            val parsed = json.decodeFromString<YtmSearchResponse>(responseBody)
            when (filter) {
                "albums" -> parsed.albums.map { it.toTrack() }
                "playlists" -> parsed.playlists.map { it.toTrack() }
                "videos" -> parsed.videos.map { it.toTrack() }.filter { !ContentQualityFilter.shouldReject(it, query) }
                else -> parsed.tracks.map { it.toTrack() }.filter { !ContentQualityFilter.shouldReject(it, query) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM search results for filter=$filter engine=$engine", e)
            emptyList()
        }
    }

    suspend fun getArtistAlbums(artistId: String): List<Track> {
        return try {
            val responseBody = executeGet("/api/youtube/artist/$artistId/albums")
            val parsed = json.decodeFromString<YtmSearchResponse>(responseBody)
            parsed.albums.map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist albums for $artistId", e)
            emptyList()
        }
    }

    suspend fun getArtistVideos(artistId: String): List<Track> {
        return try {
            val responseBody = executeGet("/api/youtube/artist/$artistId/videos")
            val parsed = json.decodeFromString<YtmSearchResponse>(responseBody)
            parsed.videos.map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist videos for $artistId", e)
            emptyList()
        }
    }

    suspend fun getArtistPlaylists(artistId: String): List<Track> {
        return try {
            val responseBody = executeGet("/api/youtube/artist/$artistId/playlists")
            val parsed = json.decodeFromString<YtmSearchResponse>(responseBody)
            parsed.playlists.map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist playlists for $artistId", e)
            emptyList()
        }
    }

    suspend fun searchFull(query: String): YtmSearchResponse? {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return try {
            val responseBody = executeGet("/api/youtube/search?q=$encoded")
            json.decodeFromString<YtmSearchResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse full YTM search for $query", e)
            null
        }
    }

    fun parseTopResult(topResultDto: YtmTopResultDto): Any? {
        val data = topResultDto.data ?: return null
        return runCatching {
            when (topResultDto.type.lowercase()) {
                "track", "song", "video" -> json.decodeFromJsonElement<YtmTrackDto>(data).toTrack()
                "artist" -> json.decodeFromJsonElement<com.mue.core.network.YtmArtistSearchItemDto>(data)
                "album" -> json.decodeFromJsonElement<YtmAlbumDto>(data)
                else -> null
            }
        }.getOrNull()
    }

    override suspend fun getPlaybackSource(track: Track): PlaybackSource? {
        return getPlaybackSource(track, "auto")
    }

    suspend fun getPlaybackSource(track: Track, quality: String): PlaybackSource? {
        val videoId = track.youtubeVideoId ?: track.id
        if (videoId.isBlank()) return null
        val qualityQuery = when (quality.lowercase()) {
            "low" -> "?quality=low"
            "high" -> "?quality=high"
            else -> ""
        }
        return try {
            val responseBody = executeGet("/api/youtube/stream/$videoId$qualityQuery")
            val parsed = json.decodeFromString<YtmStreamResponse>(responseBody)
            val streamUrl = parsed.streamUrl
            if (!streamUrl.isNullOrBlank()) {
                PlaybackSource(
                    streamUrl = streamUrl,
                    format = "M4A",
                    provider = ProviderId.YOUTUBE_MUSIC
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM stream URL for $videoId", e)
            null
        }
    }

    suspend fun getNewReleases(region: String = "IN"): List<Track> {
        val responseBody = executeGet("/api/youtube/new-releases/global?region=$region")
        return try {
            val parsed = json.decodeFromString<YtmNewReleasesResponse>(responseBody)
            val tracks = if (parsed.trending.isNotEmpty()) {
                parsed.trending.map { it.toTrack() }
            } else {
                parsed.releases.map { it.toTrack() }
            }
            tracks.filter { !ContentQualityFilter.shouldReject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM new releases", e)
            emptyList()
        }
    }

    suspend fun getCommunityPlaylists(region: String = "IN"): List<Track> {
        val responseBody = executeGet("/api/youtube/playlists/global?region=$region")
        return try {
            val parsed = json.decodeFromString<YtmPlaylistsResponse>(responseBody)
            parsed.playlists.map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM playlists", e)
            emptyList()
        }
    }

    suspend fun getAlbumsAndSingles(region: String = "IN"): List<Track> {
        val responseBody = executeGet("/api/youtube/albums/global?region=$region")
        return try {
            val parsed = json.decodeFromString<YtmAlbumsResponse>(responseBody)
            parsed.releases.map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM albums and singles", e)
            emptyList()
        }
    }

    suspend fun getTrending(region: String = "IN"): List<Track> {
        val responseBody = executeGet("/api/youtube/trending?region=$region")
        return try {
            val parsed = json.decodeFromString<YtmTrendingResponse>(responseBody)
            parsed.tracks.map { it.toTrack() }.filter { !ContentQualityFilter.shouldReject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YTM trending tracks", e)
            emptyList()
        }
    }
}
