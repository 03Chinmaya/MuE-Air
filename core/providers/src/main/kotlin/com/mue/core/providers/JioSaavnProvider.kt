package com.mue.core.providers

import com.mue.core.model.PlaybackSource
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.network.NetworkConfig
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnProvider @Inject constructor(
    private val httpClient: HttpClient
) : MusicProvider {
    override val providerId = ProviderId.JIOSAAVN
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cachedEncUrls = ConcurrentHashMap<String, String>()

    private fun decryptMediaUrl(encryptedUrl: String?, quality: String = "Auto"): String? {
        if (encryptedUrl.isNullOrBlank()) return null
        return try {
            val key = SecretKeySpec("38346591".toByteArray(StandardCharsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = android.util.Base64.decode(encryptedUrl.trim(), android.util.Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decoded)
            val rawUrl = String(decryptedBytes, StandardCharsets.UTF_8)
            if (rawUrl.startsWith("http")) {
                val targetSuffix = when (quality.lowercase()) {
                    "low" -> "_160.mp4"
                    "high" -> "_320.mp4"
                    else -> "_320.mp4"
                }
                rawUrl.replace("_96.mp4", targetSuffix)
                    .replace("_160.mp4", targetSuffix)
                    .replace("_320.mp4", targetSuffix)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    suspend fun queryTracks(query: String): List<Track> = search(query)

    override suspend fun search(query: String): List<Track> {
        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=20&p=1&q=$encodedQuery"
            val response: HttpResponse = httpClient.get(url) {
                headers.append("User-Agent", "Mozilla/5.0")
                headers.append("Referer", "https://www.jiosaavn.com/")
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                val root = json.parseToJsonElement(text).jsonObject
                val results = root["results"]?.let {
                    if (it is kotlinx.serialization.json.JsonArray) it else null
                } ?: return emptyList()

                val rawTracks = results.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                        val title = unescapeHtml(rawTitle)

                        val moreInfo = obj["more_info"]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                        val primaryArtists = moreInfo?.get("artistMap")?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                            ?.get("primary_artists")?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                            ?.joinToString(", ")

                        val rawSubtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull
                        val artist = unescapeHtml(
                            if (!primaryArtists.isNullOrBlank()) primaryArtists
                            else rawSubtitle?.split("-")?.firstOrNull()?.trim() ?: "Unknown Artist"
                        )

                        val rawAlbum = moreInfo?.get("album")?.jsonPrimitive?.contentOrNull
                            ?: obj["album"]?.jsonPrimitive?.contentOrNull ?: ""
                        val album = unescapeHtml(rawAlbum)

                        val rawImg = obj["image"]?.jsonPrimitive?.contentOrNull ?: ""
                        val artwork = rawImg.replace("150x150.jpg", "500x500.jpg")
                            .replace("50x50.jpg", "500x500.jpg")

                        val duration = moreInfo?.get("duration")?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { it * 1000 } ?: 0L

                        val encUrl = moreInfo?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull
                        if (!encUrl.isNullOrBlank()) {
                            cachedEncUrls[id] = encUrl
                        }

                        val rawLang = obj["language"]?.jsonPrimitive?.contentOrNull
                            ?: moreInfo?.get("language")?.jsonPrimitive?.contentOrNull
                        val language = rawLang?.trim()?.lowercase()

                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUrl = artwork,
                            durationMs = duration,
                            provider = providerId,
                            language = language
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                rawTracks.filter { !ContentQualityFilter.shouldReject(it, query) }
            } else {
                Log.w("JioSaavnProvider", "Search response status: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("JioSaavnProvider", "Error searching for query: $query", e)
            emptyList()
        }
    }

    override suspend fun getPlaybackSource(track: Track): PlaybackSource? {
        return getPlaybackSource(track, "Auto")
    }

    suspend fun getPlaybackSource(track: Track, quality: String): PlaybackSource? {
        val encUrl = cachedEncUrls[track.id] ?: run {
            try {
                val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0%3F_marker%3D0&_format=json&pids=${track.id}"
                val response: HttpResponse = httpClient.get(url) {
                    headers.append("User-Agent", "Mozilla/5.0")
                    headers.append("Referer", "https://www.jiosaavn.com/")
                }
                if (response.status == HttpStatusCode.OK) {
                    val text = response.bodyAsText()
                    val root = json.parseToJsonElement(text).jsonObject
                    val songObj = root[track.id]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                        ?: root.values.firstOrNull { it is kotlinx.serialization.json.JsonObject }?.jsonObject
                    songObj?.get("more_info")?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                        ?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull
                        ?: songObj?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val directStreamUrl = decryptMediaUrl(encUrl, quality)
        return if (!directStreamUrl.isNullOrBlank()) {
            PlaybackSource(
                streamUrl = directStreamUrl,
                format = "AAC",
                provider = providerId
            )
        } else null
    }

    suspend fun getTrendingIndia(): List<Track> {
        val backendUrls = NetworkConfig.candidateUrls("/api/youtube/trending?region=IN")
        for (url in backendUrls) {
            try {
                val response: HttpResponse = httpClient.get(url)
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val root = json.parseToJsonElement(body).jsonObject
                    val tracksArray = root["tracks"]?.let {
                        if (it is kotlinx.serialization.json.JsonArray) it else null
                    }
                    if (!tracksArray.isNullOrEmpty()) {
                        val parsedTracks = tracksArray.mapNotNull { item ->
                            try {
                                val obj = item.jsonObject
                                val id = obj["videoId"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                val title = unescapeHtml(obj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown")
                                val primaryArtist = obj["artist"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["artists"]?.let {
                                        if (it is kotlinx.serialization.json.JsonArray) it.firstOrNull()?.jsonPrimitive?.contentOrNull else null
                                    } ?: "Unknown Artist"
                                val artist = unescapeHtml(primaryArtist)
                                val album = unescapeHtml(obj["album"]?.jsonPrimitive?.contentOrNull ?: "")
                                val rawArtwork = obj["artworkUrl"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull
                                val artwork = rawArtwork
                                    ?.replace("150x150.jpg", "500x500.jpg")
                                    ?.replace("50x50.jpg", "500x500.jpg")
                                val duration = obj["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    ?: (obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { it * 1000 } ?: 0L)
                                val language = obj["language"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                                Track(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    artworkUrl = artwork,
                                    durationMs = duration,
                                    provider = providerId,
                                    language = language
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val sanitized = parsedTracks.filter { !ContentQualityFilter.shouldReject(it) }
                        if (sanitized.isNotEmpty()) {
                            return sanitized
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next URL or fallback
            }
        }

        return emptyList()
    }
}
