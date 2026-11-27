package com.mue.core.auth

import com.mue.core.network.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AuthUserDto(
    val id: String,
    val username: String? = null,
    val displayName: String? = null,
    val email: String? = null
)

@Serializable
data class DeviceDto(
    val id: String,
    val userId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val pushToken: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class AuthResponse(
    val user: AuthUserDto? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val userId: String? = null,
    val isGuest: Boolean? = null,
    val merged: Boolean? = null,
    val mergeId: String? = null
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
    val deviceName: String = "Android App"
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val deviceName: String = "Android App"
)

@Serializable
data class GuestSetupRequest(
    val deviceId: String,
    val deviceName: String = "Android Device",
    val platform: String = "android"
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class MergeGuestRequest(
    val guestId: String,
    val clientBatchId: String? = null
)

@Serializable
data class MergeGuestResponse(
    val status: String,
    val guestId: String? = null,
    val authId: String? = null
)

@Singleton
class KtorAuthService @Inject constructor(
    private val httpClient: HttpClient
) : AuthService {
    override suspend fun login(email: String, password: String): AuthResponse {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/login") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = email, password = password, deviceName = "Android App"))
            }
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            throw Exception("Login failed: ${response.status}")
        }
    }

    override suspend fun register(email: String, password: String, username: String): AuthResponse {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/signup") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username = username, email = email, password = password, deviceName = "Android App"))
            }
        }
        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
            return response.body()
        } else {
            throw Exception("Registration failed: ${response.status}")
        }
    }

    override suspend fun guestSetup(deviceId: String, deviceName: String): AuthResponse {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/guest-setup") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(GuestSetupRequest(deviceId = deviceId, deviceName = deviceName))
            }
        }
        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
            return response.body()
        } else {
            throw Exception("Guest setup failed: ${response.status}")
        }
    }

    override suspend fun refresh(refreshToken: String): AuthResponse {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/refresh") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken = refreshToken))
            }
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            throw Exception("Refresh failed: ${response.status}")
        }
    }

    override suspend fun mergeGuest(guestId: String, clientBatchId: String?): MergeGuestResponse {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/merge-guest") { fullUrl ->
            httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(MergeGuestRequest(guestId = guestId, clientBatchId = clientBatchId))
            }
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            throw Exception("Merge guest failed: ${response.status}")
        }
    }

    override suspend fun logout() {
        NetworkConfig.executeWithFallback("/api/auth/logout") { fullUrl ->
            httpClient.post(fullUrl)
        }
    }

    override suspend fun deleteAccount(): Boolean {
        val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/account") { fullUrl ->
            httpClient.delete(fullUrl)
        }
        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
            return true
        } else {
            throw Exception("Delete account failed: ${response.status}")
        }
    }

    override suspend fun getLinkedDevices(): List<DeviceDto> {
        return try {
            val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/devices") { fullUrl ->
                httpClient.get(fullUrl)
            }
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun revokeDevice(deviceId: String): Boolean {
        return try {
            val response: HttpResponse = NetworkConfig.executeWithFallback("/api/auth/devices/$deviceId") { fullUrl ->
                httpClient.delete(fullUrl)
            }
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent
        } catch (_: Exception) {
            false
        }
    }
}
