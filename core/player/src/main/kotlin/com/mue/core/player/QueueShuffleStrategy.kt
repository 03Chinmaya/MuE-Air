package com.mue.core.player

import com.mue.core.model.Track
import java.util.ArrayDeque

class QueueShuffleStrategy(
    private val maxHistorySize: Int = 5,
    private val artistGap: Int = 2
) {
    private val history = mutableListOf<Track>()
    private val playedIds = ArrayDeque<String>()
    private val backStack = mutableListOf<String>()

    fun recordPlayed(track: Track) {
        if (backStack.lastOrNull() != track.id) {
            backStack.add(track.id)
            if (backStack.size > 50) backStack.removeAt(0)
        }

        if (playedIds.contains(track.id)) {
            playedIds.remove(track.id)
        }
        playedIds.addLast(track.id)
        while (playedIds.size > maxHistorySize) {
            playedIds.removeFirst()
        }

        history.add(track)
        if (history.size > 20) {
            history.removeAt(0)
        }
    }

    fun getPreviousTrackId(): String? {
        if (backStack.size >= 2) {
            backStack.removeAt(backStack.size - 1)
            return backStack.lastOrNull()
        }
        return null
    }

    fun nextIndex(queue: List<Track>, currentIndex: Int): Int {
        if (queue.isEmpty()) return -1
        if (queue.size == 1) return 0
        if (queue.size == 2) {
            return if (currentIndex == 0) 1 else 0
        }

        val currentTrack = if (currentIndex in queue.indices) queue[currentIndex] else null
        val recentHistoryTracks = history.takeLast(artistGap + 1)
        val blockedArtists = mutableSetOf<String>()
        currentTrack?.artist?.lowercase()?.trim()?.let { if (it.isNotBlank()) blockedArtists.add(it) }
        recentHistoryTracks.forEach { t ->
            val a = t.artist.lowercase().trim()
            if (a.isNotBlank()) blockedArtists.add(a)
        }

        val uniqueArtistsCount = queue.map { it.artist.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val otherIndices = queue.indices.filter { it != currentIndex }

        // 1. Exclude recent track IDs AND enforce artist gap
        val candidatePool = otherIndices.filter { idx ->
            val candidate = queue[idx]
            val notInRecent = candidate.id !in playedIds
            val artistOk = (uniqueArtistsCount <= 2) || (candidate.artist.lowercase().trim() !in blockedArtists)
            notInRecent && artistOk
        }

        if (candidatePool.isNotEmpty()) {
            return candidatePool.random()
        }

        // 2. Fallback: Relax artist gap, only exclude recent track IDs
        val fallbackPoolWithoutArtistGap = otherIndices.filter { idx ->
            queue[idx].id !in playedIds
        }
        if (fallbackPoolWithoutArtistGap.isNotEmpty()) {
            return fallbackPoolWithoutArtistGap.random()
        }

        // 3. Fallback: Uniform random across all other indices
        return otherIndices.random()
    }
}

