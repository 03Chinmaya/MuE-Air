package com.mue.core.datastore

import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SearchHistoryTrackJsonTest {

    private val trackJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    @Test
    fun roundTripTrack() {
        val original = Track(
            id = "Jn_G-YMBjjk",
            title = "No Fluke",
            artist = "Dhanda Nyoliwala",
            album = "No Fluke",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 209000L,
            provider = ProviderId.YOUTUBE_MUSIC,
            youtubeVideoId = "Jn_G-YMBjjk",
            artistChannelId = "UC123",
            albumId = "MPREb_123"
        )
        val encoded = trackJson.encodeToString(original)
        val decoded = runCatching { trackJson.decodeFromString<Track>(encoded) }.getOrNull()
        assertEquals(original, decoded)
    }

    @Test
    fun legacyJsonDecodes() {
        val legacyJson = """
            {
              "id": "Jn_G-YMBjjk",
              "title": "No Fluke",
              "artist": "Dhanda Nyoliwala",
              "album": "No Fluke",
              "artworkUrl": "https://example.com/art.jpg",
              "durationMs": 209000,
              "provider": "YOUTUBE_MUSIC",
              "youtubeVideoId": "Jn_G-YMBjjk",
              "artistChannelId": "",
              "albumId": ""
            }
        """.trimIndent()
        val decoded = runCatching { trackJson.decodeFromString<Track>(legacyJson) }.getOrNull()
        assertNotNull(decoded)
        assertEquals("Jn_G-YMBjjk", decoded!!.id)
        assertEquals("No Fluke", decoded.title)
        assertEquals("Dhanda Nyoliwala", decoded.artist)
        assertEquals(ProviderId.YOUTUBE_MUSIC, decoded.provider)
        assertEquals(209000L, decoded.durationMs)
    }
}
