package com.mue.core.network

import android.content.Context
import com.mue.core.datastore.SecureStore
import com.mue.core.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClientFactory @Inject constructor(
    private val secureStore: SecureStore?,
    private val settingsDataStore: SettingsDataStore?,
    @ApplicationContext private val appContext: Context? = null
) {
    constructor() : this(null, null, null)

    private val cache: okhttp3.Cache? by lazy {
        appContext?.let {
            okhttp3.Cache(
                directory = File(it.cacheDir, "http_cache"),
                maxSize = 50L * 1024 * 1024
            )
        }
    }

    private val rawClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .apply { cache?.let { cache(it) } }
            .dns(ReliableDns)
            .connectTimeout(4000, TimeUnit.MILLISECONDS)
            .readTimeout(12000, TimeUnit.MILLISECONDS)
            .callTimeout(15000, TimeUnit.MILLISECONDS)
            .build()
    }

    fun create(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                cache?.let { cache(it) }
                dns(ReliableDns)
                connectTimeout(4000, TimeUnit.MILLISECONDS)
                readTimeout(12000, TimeUnit.MILLISECONDS)
                callTimeout(15000, TimeUnit.MILLISECONDS)
                addNetworkInterceptor { chain ->
                    val req = chain.request()
                    val res = chain.proceed(req)
                    val path = req.url.encodedPath
                    val isCacheable = req.method.equals("GET", ignoreCase = true) && (
                        path.startsWith("/api/playlists") ||
                            path.startsWith("/api/home") ||
                            path.startsWith("/api/artists") ||
                            path.startsWith("/api/search")
                        )
                    if (isCacheable && res.isSuccessful) {
                        res.newBuilder()
                            .header("Cache-Control", "max-age=900, stale-while-revalidate=86400")
                            .build()
                    } else {
                        res
                    }
                }
                addInterceptor { chain ->
                    val original = chain.request()
                    val isLoopback = BuildConfig.DEBUG && (original.url.host == "10.0.2.2" || original.url.host == "localhost" || original.url.host == "127.0.0.1")
                    val activeChain = if (isLoopback) {
                        chain.withConnectTimeout(200, TimeUnit.MILLISECONDS)
                    } else {
                        chain.withConnectTimeout(4000, TimeUnit.MILLISECONDS)
                    }
                    val urlPath = original.url.encodedPath

                    val builder = original.newBuilder()

                    // 1. Device ID
                    val deviceId = settingsDataStore?.let { runBlocking { it.deviceIdFlow.first() } }
                    if (!deviceId.isNullOrBlank()) {
                        builder.header("X-Device-Id", deviceId)
                    }

                    // 2. Guest ID on signup/login
                    if (urlPath.endsWith("/api/auth/signup") || urlPath.endsWith("/api/auth/login")) {
                        val guestId = settingsDataStore?.let { runBlocking { it.guestIdFlow.first() } }
                        if (!guestId.isNullOrBlank()) {
                            builder.header("X-Guest-Id", guestId)
                        }
                    }

                    // 3. Authorization header
                    if (original.header("Authorization").isNullOrBlank()) {
                        val token = secureStore?.getAccessToken() ?: secureStore?.getGuestToken()
                        if (!token.isNullOrBlank()) {
                            builder.header("Authorization", "Bearer $token")
                        }
                    }

                    var response = activeChain.proceed(builder.build())

                    // 4. Handle 401 refresh
                    if (response.code == 401 && secureStore != null && settingsDataStore != null &&
                        !urlPath.contains("/api/auth/refresh") && !urlPath.contains("/api/auth/guest-setup")) {
                        synchronized(this) {
                            val refreshToken = secureStore.getRefreshToken()
                            if (!refreshToken.isNullOrBlank()) {
                                try {
                                    val refreshJson = """{"refreshToken":"$refreshToken"}"""
                                    val refreshReq = okhttp3.Request.Builder()
                                        .url(NetworkConfig.url("/api/auth/refresh"))
                                        .post(refreshJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                                        .build()
                                    val refreshRes = rawClient.newCall(refreshReq).execute()
                                    if (refreshRes.isSuccessful) {
                                        val bodyStr = refreshRes.body?.string() ?: ""
                                        val newAccess = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1)
                                        val newRefresh = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1)
                                        if (!newAccess.isNullOrBlank()) {
                                            secureStore.saveAccessToken(newAccess)
                                            if (!newRefresh.isNullOrBlank()) {
                                                secureStore.saveRefreshToken(newRefresh)
                                            }
                                            refreshRes.close()
                                            response.close()
                                            val retryReq = original.newBuilder()
                                                .header("Authorization", "Bearer $newAccess")
                                                .build()
                                            return@addInterceptor activeChain.proceed(retryReq)
                                        }
                                    }
                                    refreshRes.close()
                                } catch (_: Exception) {}
                            }

                            // If refresh failed or was absent:
                            val isGuest = runBlocking { settingsDataStore.isGuestFlow.first() }
                            if (isGuest) {
                                try {
                                    val devId = runBlocking { settingsDataStore.getOrGenerateDeviceId() }
                                    val setupJson = """{"deviceId":"$devId","deviceName":"${android.os.Build.MODEL}","platform":"android"}"""
                                    val setupReq = okhttp3.Request.Builder()
                                        .url(NetworkConfig.url("/api/auth/guest-setup"))
                                        .post(setupJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                                        .build()
                                    val setupRes = rawClient.newCall(setupReq).execute()
                                    if (setupRes.isSuccessful) {
                                        val bodyStr = setupRes.body?.string() ?: ""
                                        val newAccess = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1)
                                        val newRefresh = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1)
                                        val newUserId = Regex(""""userId"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1)
                                        if (!newAccess.isNullOrBlank()) {
                                            secureStore.saveAccessToken(newAccess)
                                            secureStore.saveGuestToken(newAccess)
                                            if (!newRefresh.isNullOrBlank()) secureStore.saveRefreshToken(newRefresh)
                                            if (!newUserId.isNullOrBlank()) {
                                                runBlocking {
                                                    settingsDataStore.setUserId(newUserId)
                                                    settingsDataStore.setGuestId(newUserId)
                                                    settingsDataStore.setIsGuest(true)
                                                }
                                            }
                                            setupRes.close()
                                            response.close()
                                            val retryReq = original.newBuilder()
                                                .header("Authorization", "Bearer $newAccess")
                                                .build()
                                            return@addInterceptor activeChain.proceed(retryReq)
                                        }
                                    }
                                    setupRes.close()
                                } catch (_: Exception) {}
                            } else {
                                secureStore.clearAccessToken()
                                secureStore.clearRefreshToken()
                                runBlocking { settingsDataStore.setUserId(null) }
                            }
                        }
                    }

                    response
                }
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }
}

