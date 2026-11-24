package com.mue.core.network

import java.io.IOException

/**
 * Centralized network configuration and URL resolver.
 * Replaces hardcoded "https://api.mue.app" literals across the application.
 *
 * In debug mode:
 * - Resolves to local emulator loopback (10.0.2.2:4000, fallback 10.0.2.2:3000)
 * - Allows testing against local Hono server without internet or DNS dependencies.
 *
 * In release mode:
 * - Resolves to BuildConfig.API_BASE_URL (defaults to https://mue-backend.onrender.com
 *   or production https://api.mue.app).
 */
object NetworkConfig {
    /**
     * Fallback production domain for reference.
     */
    const val DEFAULT_PRODUCTION_URL = "https://api.mue.app"

    @Volatile
    private var debugBaseUrlOverride: String? = null

    /**
     * Active Base HTTP URL.
     */
    val baseUrl: String
        get() {
            if (BuildConfig.DEBUG) {
                return debugBaseUrlOverride ?: "http://10.0.2.2:4000"
            }
            return BuildConfig.API_BASE_URL
        }

    /**
     * Active Base WebSocket URL.
     */
    val wsUrl: String
        get() = toWsUrl(baseUrl)

    /**
     * Base candidate URLs for fallback probing in debug.
     */
    val candidateBaseUrls: List<String>
        get() = if (BuildConfig.DEBUG) {
            listOf("http://10.0.2.2:4000", "http://10.0.2.2:3000", "https://mue-backend.onrender.com").distinct()
        } else {
            listOf(BuildConfig.API_BASE_URL)
        }

    /**
     * Candidate full URLs for a given relative path.
     */
    fun candidateUrls(path: String): List<String> {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return candidateBaseUrls.map { "${it.trimEnd('/')}$normalizedPath" }
    }

    /**
     * Build full URL with active baseUrl.
     */
    fun url(path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "${baseUrl.trimEnd('/')}$normalizedPath"
    }

    fun setDebugBaseUrl(url: String) {
        debugBaseUrlOverride = url.trimEnd('/')
    }

    /**
     * Executes an operation trying candidate base URLs sequentially in debug mode.
     * Updates [debugBaseUrlOverride] when a candidate succeeds.
     */
    suspend fun <T> executeWithFallback(
        path: String,
        block: suspend (fullUrl: String) -> T
    ): T {
        val candidates = candidateUrls(path)
        var lastException: Exception? = null
        for (candidate in candidates) {
            try {
                val result = block(candidate)
                if (BuildConfig.DEBUG) {
                    val normalizedPath = if (path.startsWith("/")) path else "/$path"
                    val base = candidate.removeSuffix(normalizedPath)
                    if (debugBaseUrlOverride != base) {
                        debugBaseUrlOverride = base
                    }
                }
                return result
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IOException("Network request failed for candidates of $path")
    }

    private fun toWsUrl(httpUrl: String): String {
        val clean = httpUrl.trimEnd('/')
        return when {
            clean.startsWith("https://") -> "wss://${clean.removePrefix("https://")}"
            clean.startsWith("http://") -> "ws://${clean.removePrefix("http://")}"
            else -> "ws://$clean"
        }
    }
}

