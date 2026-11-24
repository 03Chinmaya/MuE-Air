package com.mue.core.providers

import com.mue.core.model.Track

object ContentQualityFilter {
    val AI_KEYWORDS_REGEX = Regex(
        "\\b(ai cover|ai generated|ai music|suno|udio|rvc|so-vits|diff-svc|vocaloid ai|ai vocal)\\b",
        RegexOption.IGNORE_CASE
    )
    val COUNTERFEIT_REGEX = Regex(
        "\\b(king -|dhurandhar|ramayana official|fake)\\b|\\bfrom\\s+[\"']?(king|dhurandhar|ramayana)[\"']?|\\b(ramayana|namit malhotra)\\b",
        RegexOption.IGNORE_CASE
    )
    val BLACKLIST_KEYWORDS = listOf(
        "full album", "status video", "karaoke", "interview", "reaction",
        "lyrics video", "audio jukebox", "jukebox", "mashup", "remix",
        "1 hour", "2 hour", "10 hour", "slowed", "reverb", "8d audio",
        "nightcore", "cover", "acoustic", "lofi", "lo-fi", "flip",
        "instrumental", "bass boosted", "tribute", "bootleg", "fan made"
    )

    fun isAI(title: String?, artist: String? = null, album: String? = null): Boolean =
        (!title.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(title)) ||
        (!artist.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(artist)) ||
        (!album.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(album))

    fun isCounterfeit(title: String?, artist: String? = null, album: String? = null): Boolean =
        (!title.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(title)) ||
        (!artist.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(artist)) ||
        (!album.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(album))

    fun isAI(track: Track): Boolean = isAI(track.title, track.artist, track.album)

    fun isCounterfeit(track: Track): Boolean = isCounterfeit(track.title, track.artist, track.album)

    fun isBlacklisted(track: Track): Boolean =
        BLACKLIST_KEYWORDS.any { track.title.lowercase().contains(it) }

    fun shouldReject(track: Track, userQuery: String = ""): Boolean {
        if (isAI(track) || isCounterfeit(track)) return true
        if (track.videoType == "UGC") {
            val explicitQuery = Regex("\\b(cover|remix|live|acoustic|flip)\\b", RegexOption.IGNORE_CASE).containsMatchIn(userQuery)
            if (!explicitQuery) return true
        }
        val qLower = userQuery.lowercase().trim()
        val titleLower = track.title.lowercase()
        val hasDisallowedBlacklist = BLACKLIST_KEYWORDS.any { kw ->
            titleLower.contains(kw) && (qLower.isBlank() || !qLower.contains(kw))
        }
        if (hasDisallowedBlacklist) return true
        return false
    }
}
