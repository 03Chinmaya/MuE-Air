package com.mue.feature.home

import com.mue.core.model.Track
import com.mue.core.providers.ContentQualityFilter
import com.mue.core.providers.TrackDeduplicator

object QuickPicksBackfiller {
    fun backfill(
        current: List<Track>,
        candidates: List<Track>,
        targetSize: Int = 21
    ): List<Track> {
        if (current.size >= targetSize || candidates.isEmpty()) return current

        val existingIds = current.map { it.id }.toSet()
        val existingKeys = current.map { TrackDeduplicator.dedupeKey(it) }.toSet()
        val existingTitles = current.map { it.title.lowercase().trim() }.toSet()
        val needed = targetSize - current.size

        val added = candidates
            .filter { candidate ->
                val key = TrackDeduplicator.dedupeKey(candidate)
                val titleKey = candidate.title.lowercase().trim()
                candidate.id !in existingIds &&
                key !in existingKeys &&
                titleKey !in existingTitles &&
                !ContentQualityFilter.shouldReject(candidate)
            }
            .take(needed)

        return current + added
    }
}

