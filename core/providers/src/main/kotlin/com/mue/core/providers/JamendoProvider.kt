package com.mue.core.providers

import com.mue.core.model.PlaybackSource
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamendoProvider @Inject constructor(
    private val httpClient: HttpClient
) : MusicProvider {
    override val providerId = ProviderId.JAMENDO
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val clientId = BuildConfig.JAMENDO_CLIENT_ID

    override suspend fun search(query: String): List<Track> {
        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val response: HttpResponse = httpClient.get(
                "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=20&search=$encodedQuery"
            )
            if (response.status == HttpStatusCode.OK) {
                val text: String = response.body()
                val root = json.parseToJsonElement(text).jsonObject
                val results = root["results"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    ?: return emptyList()
                results.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                        val artist = obj["artist_name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                        val album = obj["album_name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val artwork = obj["image"]?.jsonPrimitive?.contentOrNull ?: ""
                        val duration = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { it * 1000 } ?: 0L

                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUrl = artwork,
                            durationMs = duration,
                            provider = providerId
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getPlaybackSource(track: Track): PlaybackSource? {
        return try {
            val response: HttpResponse = httpClient.get(
                "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&id=${track.id}&audioformat=flac"
            )
            if (response.status == HttpStatusCode.OK) {
                val text: String = response.body()
                val root = json.parseToJsonElement(text).jsonObject
                val results = root["results"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                val trackObj = results?.firstOrNull()?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                val audioUrl = trackObj?.get("audio")?.jsonPrimitive?.contentOrNull
                    ?: trackObj?.get("audiodl")?.jsonPrimitive?.contentOrNull

                if (!audioUrl.isNullOrBlank()) {
                    PlaybackSource(
                        streamUrl = audioUrl,
                        format = "FLAC",
                        provider = providerId
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
