package com.mue.core.providers

import android.util.Log
import com.mue.core.model.PlaybackSource
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * InnerTubeXProvider:
 * On-device primary client for YouTube Music's InnerTube (youtubei/v1) API using WEB_REMIX / ANDROID contexts.
 * Direct device-to-YouTube communication without server hops for low-latency search, browsing, and stream resolution.
 */
@Singleton
class InnerTubeXProvider @Inject constructor(
    private val httpClient: HttpClient
) : MusicProvider {
    override val providerId: ProviderId = ProviderId.YOUTUBE_MUSIC

    companion object {
        private const val TAG = "InnerTubeXProvider"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val CLIENT_VERSION = "1.20240101.01.00"
        private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun buildWebRemixContext(): String {
        return """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                }
            }
        """.trimIndent()
    }

    private fun buildAndroidPlayerContext(videoId: String): String {
        return """
            {
                "context": {
                    "client": {
                        "clientName": "ANDROID",
                        "clientVersion": "19.09.37",
                        "hl": "en"
                    }
                },
                "videoId": "$videoId"
            }
        """.trimIndent()
    }

    private suspend fun postInnerTube(endpoint: String, bodyJson: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = httpClient.post("$BASE_URL/$endpoint?key=$API_KEY&prettyPrint=false") {
                contentType(ContentType.Application.Json)
                headers {
                    append("User-Agent", USER_AGENT)
                    append("Referer", "https://music.youtube.com/")
                    append("Origin", "https://music.youtube.com")
                    append("X-YouTube-Client-Name", "67")
                    append("X-YouTube-Client-Version", CLIENT_VERSION)
                }
                setBody(bodyJson)
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                if (text.isNotBlank()) {
                    return@withContext json.parseToJsonElement(text).jsonObject
                }
            } else {
                Log.w(TAG, "InnerTube request to $endpoint failed with status ${response.status}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during InnerTube request to $endpoint: ${e.message}")
        }
        null
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val escapedQuery = query.replace("\"", "\\\"").replace("\n", " ").trim()
        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                },
                "query": "$escapedQuery"
            }
        """.trimIndent()

        val root = postInnerTube("search", requestBody) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()

        try {
            extractTracksFromSearch(root, tracks)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing search results for '$query': ${e.message}")
        }

        val filtered = tracks.filter { !ContentQualityFilter.shouldReject(it, query) }
        TrackDeduplicator.deduplicate(filtered)
    }

    private fun extractTracksFromSearch(root: JsonObject, outTracks: MutableList<Track>) {
        val tabbed = root["contents"]?.jsonObject
            ?.get("tabbedSearchResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray ?: return

        for (section in tabbed) {
            val shelfContents = section.jsonObject["musicShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                ?: section.jsonObject["musicCardShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                ?: continue

            for (item in shelfContents) {
                val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                val track = parseMusicResponsiveListItem(renderer)
                if (track != null) {
                    outTracks.add(track)
                }
            }
        }
    }

    private fun parseMusicResponsiveListItem(renderer: JsonObject): Track? {
        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        if (flexColumns.isEmpty()) return null

        // Column 0: Title and Video ID
        val col0Runs = flexColumns.getOrNull(0)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
        val title = col0Runs?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

        val navEndpoint = col0Runs?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject
        val videoId = navEndpoint?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: renderer["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null

        // Column 1: Artist, Album, Duration
        val col1Runs = flexColumns.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray

        var artist = "Unknown Artist"
        var album: String? = null
        var durationMs = 0L

        if (col1Runs != null && col1Runs.isNotEmpty()) {
            val textParts = col1Runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .filter { it != " • " && it != "•" }

            if (textParts.isNotEmpty()) {
                artist = textParts[0]
            }
            if (textParts.size > 1) {
                // Check if last part is duration like "3:45"
                val lastPart = textParts.last()
                val parsedDuration = parseDuration(lastPart)
                if (parsedDuration > 0) {
                    durationMs = parsedDuration
                    if (textParts.size > 2) {
                        album = textParts[1]
                    }
                } else {
                    album = lastPart
                }
            }
        }

        // Thumbnail
        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
        val artworkUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            album = album ?: "",
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = videoId,
            isOfficial = true
        )
    }

    private fun parseDuration(text: String): Long {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> {
                val mins = parts[0].toLongOrNull() ?: 0L
                val secs = parts[1].toLongOrNull() ?: 0L
                (mins * 60 + secs) * 1000L
            }
            3 -> {
                val hrs = parts[0].toLongOrNull() ?: 0L
                val mins = parts[1].toLongOrNull() ?: 0L
                val secs = parts[2].toLongOrNull() ?: 0L
                (hrs * 3600 + mins * 60 + secs) * 1000L
            }
            else -> 0L
        }
    }

    suspend fun streamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val requestBody = buildAndroidPlayerContext(videoId)
        val root = postInnerTube("player", requestBody) ?: return@withContext null

        try {
            val streamingData = root["streamingData"]?.jsonObject ?: return@withContext null
            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray
                ?: streamingData["formats"]?.jsonArray
                ?: return@withContext null

            // Prioritize audio/mp4 (M4A) or audio/webm
            var bestUrl: String? = null
            var bestBitrate = 0L

            for (fmt in adaptiveFormats) {
                val obj = fmt.jsonObject
                val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L

                if (!url.isNullOrBlank() && mimeType.startsWith("audio/")) {
                    if (bestUrl == null || (mimeType.contains("audio/mp4") && bitrate >= bestBitrate)) {
                        bestUrl = url
                        bestBitrate = bitrate
                    }
                }
            }
            bestUrl
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving stream for videoId $videoId: ${e.message}")
            null
        }
    }

    override suspend fun getPlaybackSource(track: Track): PlaybackSource? {
        return getPlaybackSource(track, "auto")
    }

    suspend fun getPlaybackSource(track: Track, quality: String): PlaybackSource? {
        val videoId = track.youtubeVideoId ?: track.id
        if (videoId.isBlank()) return null
        val resolved = streamUrl(videoId) ?: return null
        return PlaybackSource(
            streamUrl = resolved,
            format = "M4A",
            provider = ProviderId.YOUTUBE_MUSIC
        )
    }

    suspend fun getTrack(trackId: String): Track? = withContext(Dispatchers.IO) {
        val results = search(trackId)
        results.firstOrNull { it.id == trackId || it.youtubeVideoId == trackId }
            ?: results.firstOrNull()
    }

    suspend fun getHomeSections(): List<Track> = withContext(Dispatchers.IO) {
        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                },
                "browseId": "FEmusic_home"
            }
        """.trimIndent()

        val root = postInnerTube("browse", requestBody) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()

        try {
            val tabs = root["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return@withContext emptyList()

            for (section in tabs) {
                val items = section.jsonObject["musicCarouselShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                    ?: section.jsonObject["musicShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                    ?: continue

                for (item in items) {
                    val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                        ?: item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                        ?: continue
                    val track = parseMusicResponsiveListItem(renderer)
                    if (track != null) {
                        tracks.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing home sections: ${e.message}")
        }

        val filtered = tracks.filter { !ContentQualityFilter.shouldReject(it) }
        TrackDeduplicator.deduplicate(filtered)
    }

    suspend fun getAlbum(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val browseId = if (albumId.startsWith("MPREb_")) albumId else "MPREb_$albumId"
        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                },
                "browseId": "$browseId"
            }
        """.trimIndent()

        val root = postInnerTube("browse", requestBody) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()

        try {
            val contents = root["contents"]?.jsonObject
                ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                ?.get("secondaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("musicShelfRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return@withContext emptyList()

            for (item in contents) {
                val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                val track = parseMusicResponsiveListItem(renderer)
                if (track != null) tracks.add(track)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing album $albumId: ${e.message}")
        }
        tracks
    }

    suspend fun getArtist(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        val browseId = if (artistId.startsWith("UC")) artistId else "UC$artistId"
        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                },
                "browseId": "$browseId"
            }
        """.trimIndent()

        val root = postInnerTube("browse", requestBody) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()

        try {
            val tabs = root["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return@withContext emptyList()

            for (section in tabs) {
                val shelfContents = section.jsonObject["musicShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                    ?: section.jsonObject["musicCarouselShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                    ?: continue

                for (item in shelfContents) {
                    val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    val track = parseMusicResponsiveListItem(renderer)
                    if (track != null) tracks.add(track)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing artist $artistId: ${e.message}")
        }
        tracks.filter { !ContentQualityFilter.shouldReject(it) }
    }

    suspend fun getPlaylist(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$CLIENT_VERSION",
                        "hl": "en",
                        "gl": "IN"
                    }
                },
                "browseId": "$browseId"
            }
        """.trimIndent()

        val root = postInnerTube("browse", requestBody) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()

        try {
            val contents = root["contents"]?.jsonObject
                ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                ?.get("secondaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("musicPlaylistShelfRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return@withContext emptyList()

            for (item in contents) {
                val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                val track = parseMusicResponsiveListItem(renderer)
                if (track != null) tracks.add(track)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing playlist $playlistId: ${e.message}")
        }
        tracks
    }
}
