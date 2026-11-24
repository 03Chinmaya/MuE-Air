package com.mue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

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
}
