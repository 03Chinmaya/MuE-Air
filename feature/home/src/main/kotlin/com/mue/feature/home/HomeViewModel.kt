package com.mue.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.database.FollowedArtistDao
import com.mue.core.database.FollowedArtistEntity
import com.mue.core.database.ImportedPlaylistDao
import com.mue.core.database.ImportedPlaylistEntity
import com.mue.core.database.SongDao
import com.mue.core.database.toDomain
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.player.PlayerController
import com.mue.core.providers.ContentQualityFilter
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.JioSaavnProvider
import com.mue.core.providers.ServerYouTubeProvider
import com.mue.core.providers.TrackDeduplicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import android.util.Log
import java.io.IOException
import javax.inject.Inject

sealed class SkeletonState {
    object Visible : SkeletonState()
    data class Content(val tracks: List<Track>) : SkeletonState()
}

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<Track>,
    val type: String, // "QUICK_PICKS", "CAROUSEL", "IMPORTED_PLAYLISTS"
    val skeletonState: SkeletonState = SkeletonState.Content(items)
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songDao: SongDao,
    private val playerController: PlayerController,
    private val settingsDataStore: SettingsDataStore,
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val jioSaavnProvider: JioSaavnProvider,
    private val importedPlaylistDao: ImportedPlaylistDao,
    private val followedArtistDao: FollowedArtistDao,
    private val homeRecommendationEngine: HomeRecommendationEngine,
    private val sectionOrchestrator: HomeSectionOrchestrator
) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _dynamicSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val dynamicSections: StateFlow<List<HomeSection>> = _dynamicSections.asStateFlow()

    private val _isNewUser = MutableStateFlow(true)
    val isNewUser: StateFlow<Boolean> = _isNewUser.asStateFlow()

    private val _sectionStates = MutableStateFlow<Map<String, SkeletonState>>(
        mapOf(
            "quick_picks" to SkeletonState.Visible,
            "imported_playlists" to SkeletonState.Visible,
            "albums_singles" to SkeletonState.Visible,
            "new_releases" to SkeletonState.Visible,
            "trending_feed" to SkeletonState.Visible,
            "community_playlists" to SkeletonState.Visible
        )
    )

    private val _newReleases = MutableStateFlow<List<Track>>(emptyList())
    private val _communityPlaylists = MutableStateFlow<List<Track>>(emptyList())
    private val _trendingIndia = MutableStateFlow<List<Track>>(emptyList())
    private val _albumsAndSingles = MutableStateFlow<List<Track>>(emptyList())

    val importedPlaylists: StateFlow<List<ImportedPlaylistEntity>> = importedPlaylistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedArtists: StateFlow<List<FollowedArtistEntity>> = followedArtistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localQuickPicks = MutableStateFlow<List<Track>>(emptyList())
    private val _quickPicks = MutableStateFlow<List<Track>>(emptyList())
    val quickPicks: StateFlow<List<Track>> = _quickPicks.asStateFlow()

    val recentSongs: StateFlow<List<Track>> = songDao.getRecentSongs()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        recalculateSections()
        viewModelScope.launch {
            quickPicks.collect { tracks ->
                _sectionStates.update { it + ("quick_picks" to SkeletonState.Content(tracks)) }
                recalculateSections()
            }
        }
        viewModelScope.launch {
            importedPlaylists.collect {
                recalculateSections()
            }
        }
        initializeHome()
    }

    private fun recalculateSections() {
        viewModelScope.launch(Dispatchers.IO) {
            val calculated = sectionOrchestrator.calculateSections(
                quickPicks = quickPicks.value,
                importedPlaylists = importedPlaylists.value,
                newReleases = _newReleases.value,
                communityPlaylists = _communityPlaylists.value,
                trendingIndia = _trendingIndia.value,
                albumsAndSingles = _albumsAndSingles.value,
                sectionStates = _sectionStates.value
            )
            _dynamicSections.update { current ->
                if (current.isEmpty()) {
                    calculated
                } else {
                    calculated.map { newSec ->
                        val existing = current.firstOrNull { it.id == newSec.id }
                        if (existing != null && newSec.items.isEmpty() && existing.items.isNotEmpty()) {
                            existing.copy(skeletonState = newSec.skeletonState)
                        } else {
                            newSec
                        }
                    }
                }
            }
        }
    }

    fun retry() {
        initializeHome()
    }

    private fun initializeHome() {
        viewModelScope.launch {
            _isLoading.value = false
            _error.value = null

            // 1. Reset all sections to SkeletonState.Visible
            _sectionStates.value = mapOf(
                "quick_picks" to SkeletonState.Visible,
                "imported_playlists" to SkeletonState.Visible,
                "albums_singles" to SkeletonState.Visible,
                "new_releases" to SkeletonState.Visible,
                "trending_feed" to SkeletonState.Visible,
                "community_playlists" to SkeletonState.Visible
            )
            recalculateSections()

            // -----------------------------------------------------------------
            // Coroutine A (Local Room Data)
            // Fetches SongDao top tracks & Imported playlists immediately.
            // Emits to _dynamicSections in <20-30ms with zero network dependency.
            // -----------------------------------------------------------------
            launch(Dispatchers.IO) {
                delay(25L)
                val topPlayed = songDao.getTop21MostPlayed().firstOrNull()?.map { it.toDomain() } ?: emptyList()
                val currentQuick = homeRecommendationEngine.generateQuickPicks(topPlayed)
                _localQuickPicks.value = currentQuick
                _quickPicks.value = currentQuick
                val currentPlaylists = importedPlaylistDao.getAll().firstOrNull()?.map { pl ->
                    Track(
                        id = pl.id,
                        title = pl.name,
                        artist = "${pl.trackCount} tracks",
                        album = pl.source,
                        artworkUrl = pl.artworkUrl,
                        durationMs = 0L,
                        provider = ProviderId.YOUTUBE_MUSIC
                    )
                } ?: importedPlaylists.value.map { pl ->
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
                _sectionStates.update { current ->
                    current + ("quick_picks" to SkeletonState.Content(currentQuick)) +
                              ("imported_playlists" to SkeletonState.Content(currentPlaylists))
                }
                recalculateSections()
            }

            // -----------------------------------------------------------------
            // Coroutine B (Remote YTM & Providers)
            // Fetches New Releases, Trending, Community Playlists, Albums.
            // Emits each section progressively as soon as its response resolves.
            // -----------------------------------------------------------------
            launch(Dispatchers.IO) {
                val j2 = launch {
                    val start = System.currentTimeMillis()
                    try {
                        val itxHome = innerTubeXProvider.getHomeSections()
                        val newRel = if (itxHome.isNotEmpty()) itxHome else serverYouTubeProvider.getNewReleases("IN")
                        val elapsed = System.currentTimeMillis() - start
                        if (elapsed < 25L) delay(25L - elapsed)
                        if (newRel.isEmpty()) throw IOException("Empty New Releases")
                        _newReleases.value = newRel
                        _sectionStates.update { it + ("new_releases" to SkeletonState.Content(newRel)) }
                        _error.value = null
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeViewModel: Failed to fetch new_releases", e)
                        _newReleases.value = emptyList()
                        _sectionStates.update { it + ("new_releases" to SkeletonState.Content(emptyList())) }
                        if (_newReleases.value.isEmpty() && _trendingIndia.value.isEmpty() && _communityPlaylists.value.isEmpty() && _albumsAndSingles.value.isEmpty()) {
                            _error.value = "Failed to load music"
                        }
                    } finally {
                        recalculateSections()
                    }
                }

                val j3 = launch {
                    val start = System.currentTimeMillis()
                    try {
                        val playlists = serverYouTubeProvider.getCommunityPlaylists("IN")
                        val elapsed = System.currentTimeMillis() - start
                        if (elapsed < 25L) delay(25L - elapsed)
                        if (playlists.isEmpty()) throw IOException("Empty Community Playlists")
                        _communityPlaylists.value = playlists
                        _sectionStates.update { it + ("community_playlists" to SkeletonState.Content(playlists)) }
                        _error.value = null
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeViewModel: Failed to fetch community_playlists", e)
                        _communityPlaylists.value = emptyList()
                        _sectionStates.update { it + ("community_playlists" to SkeletonState.Content(emptyList())) }
                        if (_newReleases.value.isEmpty() && _trendingIndia.value.isEmpty() && _communityPlaylists.value.isEmpty() && _albumsAndSingles.value.isEmpty()) {
                            _error.value = "Failed to load music"
                        }
                    } finally {
                        recalculateSections()
                    }
                }

                val j4 = launch {
                    val start = System.currentTimeMillis()
                    try {
                        val albums = serverYouTubeProvider.getAlbumsAndSingles("IN")
                        val elapsed = System.currentTimeMillis() - start
                        if (elapsed < 25L) delay(25L - elapsed)
                        if (albums.isEmpty()) throw IOException("Empty Albums")
                        _albumsAndSingles.value = albums
                        _sectionStates.update { it + ("albums_singles" to SkeletonState.Content(albums)) }
                        _error.value = null
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeViewModel: Failed to fetch albums_singles", e)
                        _albumsAndSingles.value = emptyList()
                        _sectionStates.update { it + ("albums_singles" to SkeletonState.Content(emptyList())) }
                        if (_newReleases.value.isEmpty() && _trendingIndia.value.isEmpty() && _communityPlaylists.value.isEmpty() && _albumsAndSingles.value.isEmpty()) {
                            _error.value = "Failed to load music"
                        }
                    } finally {
                        recalculateSections()
                    }
                }

                val j5 = launch {
                    val start = System.currentTimeMillis()
                    try {
                        val itxTrending = innerTubeXProvider.getHomeSections()
                        val trending = if (itxTrending.isNotEmpty()) itxTrending else serverYouTubeProvider.getTrending("IN")
                        val elapsed = System.currentTimeMillis() - start
                        if (elapsed < 25L) delay(25L - elapsed)
                        if (trending.isEmpty()) throw IOException("Empty Trending")
                        val ranked = homeRecommendationEngine.rankCandidates(trending)
                        _trendingIndia.value = ranked
                        _sectionStates.update { it + ("trending_feed" to SkeletonState.Content(ranked)) }
                        _error.value = null
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeViewModel: Failed to fetch trending_feed", e)
                        _trendingIndia.value = emptyList()
                        _sectionStates.update { it + ("trending_feed" to SkeletonState.Content(emptyList())) }
                        if (_newReleases.value.isEmpty() && _trendingIndia.value.isEmpty() && _communityPlaylists.value.isEmpty() && _albumsAndSingles.value.isEmpty()) {
                            _error.value = "Failed to load music"
                        }
                    } finally {
                        recalculateSections()
                    }
                }

                joinAll(j2, j3, j4, j5)
                backfillQuickPicks()
                recalculateSections()
            }

            val now = System.currentTimeMillis()
            val firstInstall = runCatching { settingsDataStore.firstInstallTimeFlow.firstOrNull() }.getOrNull()
            if (firstInstall == null || firstInstall <= 0L) {
                runCatching { settingsDataStore.setFirstInstallTime(now) }
                _isNewUser.value = true
            } else {
                _isNewUser.value = (now - firstInstall) < (48 * 3600 * 1000L)
            }

            runCatching { settingsDataStore.setLastHomeRefreshTime(now) }
        }
    }

    private fun backfillQuickPicks() {
        if (_quickPicks.value.size < 21) {
            val backfillPool = _trendingIndia.value.ifEmpty { _newReleases.value }
            val backfilled = QuickPicksBackfiller.backfill(_quickPicks.value, backfillPool, 21)
            if (backfilled.size != _quickPicks.value.size) {
                _quickPicks.value = backfilled
                _sectionStates.update { it + ("quick_picks" to SkeletonState.Content(backfilled)) }
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = emptyList(), source: String = "Quick Picks") {
        playerController.setPlayingFrom(source)
        if (queue.isNotEmpty()) {
            val idx = queue.indexOf(track).coerceAtLeast(0)
            playerController.playQueue(queue, idx, source)
        } else {
            playerController.playTrack(track)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, source: String = "Quick Picks") {
        playerController.setPlayingFrom(source)
        playerController.playQueue(tracks, startIndex, source)
    }
}
