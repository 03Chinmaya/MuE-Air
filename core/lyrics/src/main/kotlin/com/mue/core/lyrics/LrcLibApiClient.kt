package com.mue.core.lyrics

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LrcLibResponse(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@Serializable
data class LyricsOvhResponse(
    val lyrics: String? = null,
    val error: String? = null
)

@Singleton
class LrcLibApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            engine {
                config {
                    connectTimeout(Duration.ofSeconds(8))
                    readTimeout(Duration.ofSeconds(8))
                }
            }
        }
    }

    suspend fun fetchLyrics(title: String, artist: String, durationMs: Long? = null): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null

        val cleanTitle = cleanTrackTitle(title)
        val cleanArtist = cleanArtistName(artist)

        // 1. Try LRCLIB with raw or cleaned titles
        val lrcFromLrclib = queryLrcLib(title, artist, durationMs)
            ?: queryLrcLib(cleanTitle, cleanArtist, durationMs)

        if (!lrcFromLrclib.isNullOrBlank()) {
            return@withContext lrcFromLrclib
        }

        // 2. Secondary fallback: lyrics.ovh
        val lrcFromOvh = queryLyricsOvh(cleanTitle, cleanArtist)
            ?: queryLyricsOvh(title, artist)

        return@withContext lrcFromOvh
    }

    private suspend fun queryLrcLib(title: String, artist: String, durationMs: Long?): String? {
        return try {
            val encArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
            val encTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            var url = "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTitle"
            if (durationMs != null && durationMs > 0) {
                val durSec = durationMs / 1000
                url += "&duration=$durSec"
            }

            val response = client.get(url) {
                header("User-Agent", "MuE-MusicApp/1.0 (https://github.com/03Chinmaya/MuE)")
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val parsed = json.decodeFromString<LrcLibResponse>(body)
                if (parsed.instrumental == true) {
                    return "[00:00.00] ♪ Instrumental ♪"
                }
                if (!parsed.syncedLyrics.isNullOrBlank()) {
                    return parsed.syncedLyrics
                }
                if (!parsed.plainLyrics.isNullOrBlank()) {
                    return parsed.plainLyrics
                }
            }
            null
        } catch (e: Exception) {
            Log.d("LrcLibApiClient", "LRCLIB fetch failed for $title - $artist: ${e.message}")
            null
        }
    }

    private suspend fun queryLyricsOvh(title: String, artist: String): String? {
        return try {
            val encArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
            val encTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"

            val response = client.get(url) {
                header("User-Agent", "MuE-MusicApp/1.0 (https://github.com/03Chinmaya/MuE)")
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val parsed = json.decodeFromString<LyricsOvhResponse>(body)
                if (!parsed.lyrics.isNullOrBlank()) {
                    return parsed.lyrics
                }
            }
            null
        } catch (e: Exception) {
            Log.d("LrcLibApiClient", "LyricsOvh fetch failed for $title - $artist: ${e.message}")
            null
        }
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("\\s*[\\[(](feat\\.|ft\\.|with|featuring)[^\\])]*[\\])]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\[(](official|video|audio|lyrics|visualizer|remastered|version|bonus track)[^\\])]*[\\])]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(remastered|live|radio edit|original mix).*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .split(",", "&", ";", " feat", " ft")
            .firstOrNull()?.trim() ?: artist.trim()
    }
}

