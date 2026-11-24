package com.mue.core.providers

import com.mue.core.model.Track

object TrackDeduplicator {
    fun dedupeKey(track: Track): String {
        val cleanTitle = track.title.lowercase()
            .replace(Regex("\\(from.*?\\)"), "")  // Strip "(From "Brahmastra")"
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(official|video|lyrics|hd|4k)"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
        val cleanArtist = track.artist.lowercase()
            .split(",").take(2).joinToString("")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
        return if (cleanArtist.isBlank() || cleanArtist == "unknownartist") {
            cleanTitle
        } else {
            "$cleanTitle:::$cleanArtist"
        }
    }

    fun dedupeTitleKey(track: Track): String {
        return track.title.lowercase()
            .replace(Regex("\\(from.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(official|video|lyrics|hd|4k)"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    fun List<Track>.deduplicated(): List<Track> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(dedupeKey(it)) }
    }

    fun deduplicate(tracks: List<Track>): List<Track> {
        val seen = mutableSetOf<String>()
        return tracks.filter { seen.add(dedupeKey(it)) }
    }
}
