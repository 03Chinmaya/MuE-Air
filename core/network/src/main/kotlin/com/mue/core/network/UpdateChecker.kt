package com.mue.core.network

import android.content.Context
import com.mue.core.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val sha256: String? = null,
    val versionCode: Long = 0L,
    val apkSize: Long = 0L
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

@Serializable
data class ServerUpdateResponse(
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val currentVersion: String? = null,
    val releaseNotes: String? = null,
    val apkUrl: String? = null,
    val apkSize: Long? = null,
    val error: String? = null
)

@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: HttpClient,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) {
    private val githubReleasesUrl = "https://api.github.com/repos/03Chinmaya/MuE-Air/releases/latest"
    private val prefs = context.getSharedPreferences("mue_air_updates", Context.MODE_PRIVATE)

    suspend fun checkForUpdates(currentVersion: String = "1.0", force: Boolean = true): UpdateInfo? {
        val lastCheck = prefs.getLong("last_check_ms", 0L)
        val now = System.currentTimeMillis()
        val sixHoursMs = 6 * 60 * 60 * 1000L

        if (!force && now - lastCheck < sixHoursMs) {
            return readCachedUpdateInfo()
        }

        prefs.edit().putLong("last_check_ms", now).apply()

        // 1. First try server endpoint /api/updates/check
        try {
            val serverResult = NetworkConfig.executeWithFallback("/api/updates/check?version=$currentVersion") { url ->
                val response: HttpResponse = httpClient.get(url)
                if (response.status == HttpStatusCode.OK) {
                    val body: ServerUpdateResponse = response.body()
                    if (body.updateAvailable && !body.apkUrl.isNullOrBlank() && !body.latestVersion.isNullOrBlank()) {
                        UpdateInfo(
                            version = body.latestVersion,
                            changelog = body.releaseNotes ?: "",
                            downloadUrl = body.apkUrl,
                            apkSize = body.apkSize ?: 0L,
                            versionCode = parseVersionCode(body.latestVersion)
                        )
                    } else null
                } else null
            }
            if (serverResult != null) {
                cacheUpdateInfo(serverResult)
                return serverResult
            }
        } catch (_: Exception) {}

        // 2. Direct GitHub Releases fallback
        return try {
            val response: HttpResponse = httpClient.get(githubReleasesUrl) {
                header("Accept", "application/vnd.github.v3+json")
                header("User-Agent", "MuE-Air-App")
            }
            if (response.status == HttpStatusCode.OK) {
                val release: GitHubRelease = response.body()
                val latestVersion = release.tagName.removePrefix("v").trim()
                if (isNewerVersion(latestVersion, currentVersion)) {
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    val apkUrl = apkAsset?.browserDownloadUrl ?: ""
                    val info = UpdateInfo(
                        version = latestVersion,
                        changelog = release.body,
                        downloadUrl = apkUrl,
                        apkSize = apkAsset?.size ?: 0L,
                        versionCode = parseVersionCode(release.tagName)
                    )
                    cacheUpdateInfo(info)
                    info
                } else {
                    prefs.edit().remove("last_update_info_json").apply()
                    null
                }
            } else {
                readCachedUpdateInfo()
            }
        } catch (_: Exception) {
            readCachedUpdateInfo()
        }
    }

    suspend fun fetchServerVersion(): String {
        return try {
            NetworkConfig.executeWithFallback("/health") { url ->
                val response: HttpResponse = httpClient.get(url)
                if (response.status == HttpStatusCode.OK) {
                    val body: JsonObject = response.body()
                    body["version"]?.jsonPrimitive?.content ?: "1.0.0"
                } else {
                    "1.0.0"
                }
            }
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun parseVersionCode(version: String): Long {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".", "-").filter { it.isNotEmpty() }
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val patch = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return major * 10000L + minor * 100L + patch
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = remoteVersion.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun cacheUpdateInfo(info: UpdateInfo) {
        try {
            prefs.edit().putString("last_update_info_json", Json.encodeToString(UpdateInfo.serializer(), info)).apply()
        } catch (_: Exception) {}
    }

    private fun readCachedUpdateInfo(): UpdateInfo? {
        val json = prefs.getString("last_update_info_json", null) ?: return null
        return try {
            Json.decodeFromString(UpdateInfo.serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}
