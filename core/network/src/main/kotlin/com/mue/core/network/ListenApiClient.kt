package com.mue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CreateSessionRequest(
    val trackId: String = "",
    val provider: String = "jiosaavn",
    val startPositionMs: Long = 0L,
    val isPlaying: Boolean = true,
    val controlMode: String = "open"
)

@Serializable
data class CreateSessionResponse(
    val id: String = "",
    val sessionId: String = "",
    val joinCode: String = "",
    val hostUserId: String = "",
    val currentProviderTrackId: String? = null,
    val currentProvider: String? = null,
    val positionMs: Long = 0L,
    val playing: Boolean = true
)

@Serializable
data class JoinSessionResponse(
    val status: String = "",
    val sessionId: String = "",
    val joinCode: String = "",
    val hostUserId: String = "",
    val trackId: String? = null,
    val provider: String? = null,
    val positionMs: Long = 0L,
    val playing: Boolean = true
)

sealed interface JoinSessionResult {
    data class Success(val response: JoinSessionResponse) : JoinSessionResult
    data object NotFound : JoinSessionResult
    data object SessionFull : JoinSessionResult
    data class Error(val message: String) : JoinSessionResult
}

sealed interface CreateSessionResult {
    data class Success(val response: CreateSessionResponse) : CreateSessionResult
    data class Error(val message: String) : CreateSessionResult
}

@Singleton
class ListenApiClient @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSession(
        trackId: String = "",
        provider: String = "jiosaavn",
        startPositionMs: Long = 0L,
        isPlaying: Boolean = true,
        controlMode: String = "open"
    ): CreateSessionResult = try {
        val response = NetworkConfig.executeWithFallback("/api/listen/sessions") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateSessionRequest(
                        trackId = trackId,
                        provider = provider,
                        startPositionMs = startPositionMs,
                        isPlaying = isPlaying,
                        controlMode = controlMode
                    )
                )
            }
        }
        if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            val body = json.decodeFromString<CreateSessionResponse>(response.bodyAsText())
            CreateSessionResult.Success(body)
        } else {
            CreateSessionResult.Error("Failed to create room: ${response.status}")
        }
    } catch (e: Exception) {
        CreateSessionResult.Error(e.message ?: "Network error creating room")
    }

    suspend fun joinSession(joinCode: String): JoinSessionResult = try {
        val cleanCode = joinCode.trim().uppercase()
        val response = NetworkConfig.executeWithFallback("/api/listen/join/$cleanCode") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
            }
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val body = json.decodeFromString<JoinSessionResponse>(response.bodyAsText())
                JoinSessionResult.Success(body)
            }
            HttpStatusCode.NotFound -> JoinSessionResult.NotFound
            HttpStatusCode.BadRequest -> {
                val text = response.bodyAsText()
                if (text.contains("full", ignoreCase = true)) {
                    JoinSessionResult.SessionFull
                } else {
                    JoinSessionResult.Error(text)
                }
            }
            else -> JoinSessionResult.Error("Join failed with HTTP ${response.status}")
        }
    } catch (e: Exception) {
        JoinSessionResult.Error(e.message ?: "Network error joining room")
    }
}
