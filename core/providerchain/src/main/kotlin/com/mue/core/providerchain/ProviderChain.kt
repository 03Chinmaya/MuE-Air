package com.mue.core.providerchain

enum class Cap { METADATA, STREAM_LOSSLESS, STREAM_LOSSY, LOCAL }

data class ChainLink(
    val id: String,
    val caps: Set<Cap>,
    val budgetMs: Long,
    val enabled: Boolean = true,
)

data class LinkResult<T>(
    val linkId: String,
    val data: T,
    val durationMs: Long = 0L
)

object ProviderChains {
    val metadata = listOf(
        ChainLink("innertubex",        setOf(Cap.METADATA), 2500),
        ChainLink("innertune",         setOf(Cap.METADATA), 2500, enabled = false), // Evaluated: app not lib, dropped
        ChainLink("server-youtubei",   setOf(Cap.METADATA), 4000),
        ChainLink("server-ytmusicapi", setOf(Cap.METADATA), 4000),
        ChainLink("jiosaavn",          setOf(Cap.METADATA), 3000),
        ChainLink("jamendo",           setOf(Cap.METADATA), 3000),
    )
    val playback = listOf(
        ChainLink("downloaded",        setOf(Cap.LOCAL),            200),
        ChainLink("jamendo",           setOf(Cap.STREAM_LOSSLESS),  3500),
        ChainLink("jiosaavn",          setOf(Cap.STREAM_LOSSY),     3500),
        ChainLink("innertubex",        setOf(Cap.STREAM_LOSSY),     4000),
        ChainLink("innertune",         setOf(Cap.STREAM_LOSSY),     4000, enabled = false), // Evaluated: app not lib, dropped
        ChainLink("server-youtubei",   setOf(Cap.STREAM_LOSSY),     5000),
        ChainLink("server-ytmusicapi", setOf(Cap.STREAM_LOSSY),     5000),
        ChainLink("local",             setOf(Cap.LOCAL),             500),
    )
}
