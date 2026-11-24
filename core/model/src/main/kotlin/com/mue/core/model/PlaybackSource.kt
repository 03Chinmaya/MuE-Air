package com.mue.core.model

data class PlaybackSource(
    val streamUrl: String,
    val format: String,
    val provider: ProviderId
)

