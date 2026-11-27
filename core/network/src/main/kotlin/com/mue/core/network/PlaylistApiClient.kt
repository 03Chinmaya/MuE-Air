package com.mue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

class LoginRequiredException(message: String = "Sign in required to import more playlists") : Exception(message)

sealed class ImportError(message: String) : Exception(message) {
    class InvalidUrl(reason: String) : ImportError(reason)
    data object SpotifyCookieRequired : ImportError("This Spotify playlist needs a login cookie")
    data object SpotifyAuthFailed : ImportError("Spotify login expired. Re-paste your cookie.")
    data object SidecarError : ImportError("Import service is waking up. Try again.")
    data object Unauthorized : ImportError("Authentication required")
    data object Network : ImportError("No connection. Check your network.")
    class Unknown(detail: String) : ImportError(detail)
}

@Serializable
data class ImportPlaylistRequest(
    val url: String,
    val spDc: String? = null
)

@Serializable
data class ImportedTrackDto(
    val videoId: String = "",
    val title: String = "Unknown",
    val artists: String = "Unknown Artist",
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null
)

@Serializable
data class ImportPlaylistResponse(
    val title: String = "Imported Playlist",
    val thumbnail: String? = null,
    val tracks: List<ImportedTrackDto> = emptyList()
)

@Serializable
data class UnifiedPlaylistDto(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val source: String,
    val updatedAt: String? = null,
    val isImported: Boolean = false
)

@Serializable
data class GetPlaylistsResponse(
    val playlists: List<UnifiedPlaylistDto> = emptyList(),
    val error: String? = null
)

@Singleton
class PlaylistApiClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getUnifiedPlaylists(): Result<List<UnifiedPlaylistDto>> = runCatching {
        NetworkConfig.executeWithFallback("/api/playlists") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to fetch playlists: HTTP ${response.status.value}")
            }
            val body = response.body<GetPlaylistsResponse>()
            body.playlists
        }
    }

    suspend fun importPlaylist(
        url: String,
        spDc: String? = null
    ): Result<ImportPlaylistResponse> = runCatching {
        try {
            NetworkConfig.executeWithFallback("/api/import/playlist") { fullUrl ->
                val response = httpClient.post(fullUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(ImportPlaylistRequest(url = url, spDc = spDc))
                }
                if (!response.status.isSuccess()) {
                    val errText = runCatching { response.bodyAsText() }.getOrDefault("")
                    val errObj = runCatching { Json.parseToJsonElement(errText).jsonObject }.getOrNull()
                    val errCode = errObj?.get("error")?.jsonPrimitive?.content
                    val reason = errObj?.get("reason")?.jsonPrimitive?.content
                    if (response.status.value == 403 && errCode == "LOGIN_REQUIRED") {
                        throw LoginRequiredException()
                    }
                    when (errCode) {
                        "INVALID_URL" -> throw ImportError.InvalidUrl(
                            reason ?: "That doesn't look like a YouTube Music or Spotify playlist URL"
                        )
                        "SPOTIFY_COOKIE_REQUIRED" -> throw ImportError.SpotifyCookieRequired
                        "SPOTIFY_AUTH_FAILED" -> throw ImportError.SpotifyAuthFailed
                        "SIDECAR_ERROR" -> throw ImportError.SidecarError
                        else -> if (response.status.value == 401) {
                            throw ImportError.Unauthorized
                        } else {
                            throw ImportError.Unknown("HTTP ${response.status.value}")
                        }
                    }
                }
                response.body<ImportPlaylistResponse>()
            }
        } catch (e: ImportError) {
            throw e
        } catch (e: LoginRequiredException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ImportError.Network
        } catch (e: IOException) {
            throw ImportError.Network
        } catch (e: Exception) {
            throw ImportError.Unknown(e.message ?: "Unknown error")
        }
    }
}
