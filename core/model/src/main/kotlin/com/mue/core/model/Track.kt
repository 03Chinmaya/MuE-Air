package com.mue.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val animatedArtworkUrl: String? = null,
    val durationMs: Long = 0L,
    val provider: ProviderId = ProviderId.JIOSAAVN,
    val youtubeVideoId: String? = null,
    val year: Int? = null,
    val language: String? = null,
    val genre: String? = null,
    val mood: String? = null,
    val type: String? = null,
    val videoType: String? = null,
    val artistChannelId: String? = null,
    val albumId: String? = null,
    val isOfficial: Boolean = videoType == "ATV" || videoType == "OMV"
)

