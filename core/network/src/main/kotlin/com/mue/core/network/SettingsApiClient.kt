package com.mue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsApiClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getSettings(): Result<List<CloudSettingDto>> = runCatching {
        NetworkConfig.executeWithFallback("/api/settings") { fullUrl ->
            val response = httpClient.get(fullUrl)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to fetch settings: HTTP ${response.status.value}")
            }
            val body = response.body<CloudSettingsResponse>()
            body.settings
        }
    }

    suspend fun postSettings(settings: List<CloudSettingDto>): Result<List<CloudSettingDto>> = runCatching {
        NetworkConfig.executeWithFallback("/api/settings") { fullUrl ->
            val response = httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(CloudSettingsPayload(settings))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Failed to save settings: HTTP ${response.status.value}")
            }
            val body = response.body<CloudSettingsResponse>()
            body.settings
        }
    }
}

