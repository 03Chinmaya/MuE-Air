package com.mue.core.model

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val isRepeatEnabled: Boolean = false,
    val queue: List<Track> = emptyList()
)

