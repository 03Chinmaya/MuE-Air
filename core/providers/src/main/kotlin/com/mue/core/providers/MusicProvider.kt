package com.mue.core.providers

import com.mue.core.model.PlaybackSource
import com.mue.core.model.ProviderId
import com.mue.core.model.Track

interface MusicProvider {
    val providerId: ProviderId
    suspend fun search(query: String): List<Track>
    suspend fun getPlaybackSource(track: Track): PlaybackSource?
}
