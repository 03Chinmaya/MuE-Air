package com.mue.feature.home

import com.mue.core.database.ImportedPlaylistEntity
import com.mue.core.database.SongDao
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.TrackDeduplicator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSectionOrchestrator @Inject constructor(
    private val songDao: SongDao
) {
    // Section weights (post Phase 22.K, Top Picks removed):
    //   Quick Picks        : 1000 (index 0 lock)
    //   Imported Playlists : 100
    //   Albums & Singles   : 15 (+50 play history boost)
    //   Featured For You   : 12
    //   Trending           : 10
    //   Community Playlists: 8
    suspend fun calculateSections(
        quickPicks: List<Track>,
        importedPlaylists: List<ImportedPlaylistEntity>,
        newReleases: List<Track>,
        communityPlaylists: List<Track>,
        trendingIndia: List<Track>,
        albumsAndSingles: List<Track>,
        sectionStates: Map<String, SkeletonState>
    ): List<HomeSection> {
        val weightedList = mutableListOf<Pair<Int, HomeSection>>()
        val seenKeys = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        // 1. Quick Picks: Always index 0 (Weight: 1000)
        val qState = sectionStates["quick_picks"] ?: SkeletonState.Content(quickPicks)
        val dedupedQuickPicks = mutableListOf<Track>()
        val quickSeen = mutableSetOf<String>()
        for (track in quickPicks) {
            val key = TrackDeduplicator.dedupeKey(track)
            val titleKey = track.title.lowercase().trim()
            if (key.isBlank() || key in quickSeen || titleKey in seenTitles) continue
            quickSeen.add(key)
            seenTitles.add(titleKey)
            dedupedQuickPicks.add(track)
            seenKeys.add(key)
        }
        if (dedupedQuickPicks.isNotEmpty() || qState is SkeletonState.Visible) {
            weightedList.add(
                1000 to HomeSection(
                    id = "quick_picks",
                    title = "Quick Picks",
                    items = dedupedQuickPicks,
                    type = "QUICK_PICKS",
                    skeletonState = if (qState is SkeletonState.Visible) qState else SkeletonState.Content(dedupedQuickPicks)
                )
            )
        }

        // 2. Imported Playlists: If not empty, boost weight to 100; if empty, keep at weight 5
        val playlistTracks = importedPlaylists.map { pl ->
            Track(
                id = pl.id,
                title = pl.name,
                artist = "${pl.trackCount} tracks",
                album = pl.source,
                artworkUrl = pl.artworkUrl,
                durationMs = 0L,
                provider = ProviderId.YOUTUBE_MUSIC
            )
        }
        val importedWeight = if (importedPlaylists.isNotEmpty()) 100 else 5
        val ipState = sectionStates["imported_playlists"] ?: SkeletonState.Content(playlistTracks)
        if (playlistTracks.isNotEmpty() || ipState is SkeletonState.Visible) {
            weightedList.add(
                importedWeight to HomeSection(
                    id = "imported_playlists",
                    title = "Imported Playlists",
                    items = playlistTracks,
                    type = "IMPORTED_PLAYLISTS",
                    skeletonState = ipState
                )
            )
        }

        // 3. Albums & Singles: Filter out seen keys
        val filteredAlbums = albumsAndSingles.filter { track ->
            val key = TrackDeduplicator.dedupeKey(track)
            val titleKey = track.title.lowercase().trim()
            if (key.isBlank() || key in seenKeys || titleKey in seenTitles) false else {
                seenKeys.add(key)
                seenTitles.add(titleKey)
                true
            }
        }
        var albWeight = 15
        var hasAlbPlays = false
        for (track in filteredAlbums.take(10)) {
            val count = songDao.getPlayCount(track.id) ?: 0
            if (count > 0) {
                hasAlbPlays = true
                break
            }
        }
        if (hasAlbPlays) albWeight += 50
        val asState = sectionStates["albums_singles"] ?: SkeletonState.Content(filteredAlbums)
        if (filteredAlbums.isNotEmpty() || asState is SkeletonState.Visible) {
            weightedList.add(
                albWeight to HomeSection(
                    id = "albums_singles",
                    title = "Albums & Singles",
                    items = filteredAlbums,
                    type = "CAROUSEL",
                    skeletonState = if (asState is SkeletonState.Visible) asState else SkeletonState.Content(filteredAlbums)
                )
            )
        }

        // 5. Trending: Filter out seen keys
        val filteredTrending = trendingIndia.filter { track ->
            val key = TrackDeduplicator.dedupeKey(track)
            val titleKey = track.title.lowercase().trim()
            if (key.isBlank() || key in seenKeys || titleKey in seenTitles) false else {
                seenKeys.add(key)
                seenTitles.add(titleKey)
                true
            }
        }
        val tiState = sectionStates["trending_feed"] ?: SkeletonState.Content(filteredTrending)
        val trendingTitle = "Trending in India"
        if (filteredTrending.isNotEmpty() || tiState is SkeletonState.Visible) {
            weightedList.add(
                10 to HomeSection(
                    id = "trending_feed",
                    title = trendingTitle,
                    items = filteredTrending,
                    type = "CAROUSEL",
                    skeletonState = if (tiState is SkeletonState.Visible) tiState else SkeletonState.Content(filteredTrending)
                )
            )
        }

        // 6. New Releases ("Featured For You"): Filter out seen keys
        val filteredNewRel = newReleases.filter { track ->
            val key = TrackDeduplicator.dedupeKey(track)
            val titleKey = track.title.lowercase().trim()
            if (key.isBlank() || key in seenKeys || titleKey in seenTitles) false else {
                seenKeys.add(key)
                seenTitles.add(titleKey)
                true
            }
        }
        val nrState = sectionStates["new_releases"] ?: SkeletonState.Content(filteredNewRel)
        if (filteredNewRel.isNotEmpty() || nrState is SkeletonState.Visible) {
            weightedList.add(
                12 to HomeSection(
                    id = "new_releases",
                    title = "Featured For You",
                    items = filteredNewRel,
                    type = "CAROUSEL",
                    skeletonState = if (nrState is SkeletonState.Visible) nrState else SkeletonState.Content(filteredNewRel)
                )
            )
        }

        // 7. Community Playlists: Filter out seen keys
        val filteredPlaylists = communityPlaylists.filter { track ->
            val key = TrackDeduplicator.dedupeKey(track)
            val titleKey = track.title.lowercase().trim()
            if (key.isBlank() || key in seenKeys || titleKey in seenTitles) false else {
                seenKeys.add(key)
                seenTitles.add(titleKey)
                true
            }
        }
        val cpState = sectionStates["community_playlists"] ?: SkeletonState.Content(filteredPlaylists)
        val playlistsTitle = "Top Community Playlists in India"
        if (filteredPlaylists.isNotEmpty() || cpState is SkeletonState.Visible) {
            weightedList.add(
                8 to HomeSection(
                    id = "community_playlists",
                    title = playlistsTitle,
                    items = filteredPlaylists,
                    type = "CAROUSEL",
                    skeletonState = if (cpState is SkeletonState.Visible) cpState else SkeletonState.Content(filteredPlaylists)
                )
            )
        }

        // Sort descending by weight. Quick Picks has weight 1000 so it is guaranteed at index 0.
        return weightedList.sortedByDescending { it.first }.map { it.second }
    }
}

