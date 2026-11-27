package com.mue.core.player

data class SessionTasteVector(
    val penalizedArtists: Map<String, Int> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun penalize(artist: String): SessionTasteVector {
        val key = artist.lowercase().trim()
        if (key.isBlank() || key == "unknown artist") return this
        val updated = penalizedArtists.toMutableMap()
        updated[key] = (updated[key] ?: 0) + 1
        return copy(penalizedArtists = updated, timestamp = System.currentTimeMillis())
    }
}
