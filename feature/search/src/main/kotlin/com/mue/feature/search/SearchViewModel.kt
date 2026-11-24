package com.mue.feature.search

import android.content.Context
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.datastore.SearchHistoryItem
import com.mue.core.datastore.SearchHistoryRepository
import com.mue.core.datastore.SecureStore
import com.mue.core.model.PlaybackState
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.network.ArtistApiClient
import com.mue.core.network.YtmArtistSearchItemDto
import com.mue.core.player.PlayerController
import com.mue.core.player.TrackLinkResolver
import com.mue.core.providers.SearchCoordinator
import com.mue.core.providers.ServerYouTubeProvider
import com.mue.core.providers.YtmAlbumDto
import com.mue.core.providers.YtmPlaylistDto
import com.mue.core.providers.YtmTopResultDto
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    VIDEOS("Videos"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

sealed interface TopResult {
    data class TrackResult(val track: Track) : TopResult
    data class ArtistResult(val artist: YtmArtistSearchItemDto) : TopResult
    data class AlbumResult(val album: YtmAlbumDto) : TopResult
}

data class SearchAllResults(
    val topResult: TopResult? = null,
    val songs: List<Track> = emptyList(),
    val videos: List<Track> = emptyList(),
    val albums: List<YtmAlbumDto> = emptyList(),
    val artists: List<YtmArtistSearchItemDto> = emptyList(),
    val playlists: List<YtmPlaylistDto> = emptyList(),
    val continuations: Map<String, String?>? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCoordinator: SearchCoordinator,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val playerController: PlayerController,
    private val trackLinkResolver: TrackLinkResolver,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val artistApiClient: ArtistApiClient,
    private val secureStore: SecureStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SearchCategory.ALL)
    val selectedCategory: StateFlow<SearchCategory> = _selectedCategory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val playerState: StateFlow<PlaybackState> = playerController.state

    val recentSearches: StateFlow<List<SearchHistoryItem>> = searchHistoryRepository
        .getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val searchCache = LruCache<Pair<String, SearchCategory>, Any>(30)

    private val _allResults = MutableStateFlow(SearchAllResults())
    val allResults: StateFlow<SearchAllResults> = _allResults.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    val rawResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _artistResults = MutableStateFlow<List<YtmArtistSearchItemDto>>(emptyList())
    val artistResults: StateFlow<List<YtmArtistSearchItemDto>> = _artistResults.asStateFlow()

    private val _resolvedArtistId = MutableStateFlow<String?>(null)
    val resolvedArtistId: StateFlow<String?> = _resolvedArtistId.asStateFlow()

    init {
        val debouncedQuery = _query
            .debounce { q -> if (q.isBlank()) 0L else 300L }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(debouncedQuery, _selectedCategory) { q, cat -> q to cat }
                .collectLatest { (rawQ, category) ->
                    val q = rawQ.trim()
                    if (q.isBlank() || extractYoutubeVideoId(q) != null) {
                        _isLoading.value = false
                        _resolvedArtistId.value = null
                        _allResults.value = SearchAllResults()
                        _searchResults.value = emptyList()
                        _artistResults.value = emptyList()
                        return@collectLatest
                    }

                    // Check cache (0ms immediate switch)
                    val cached = searchCache.get(q to category)
                    if (cached != null) {
                        _isLoading.value = false
                        when (category) {
                            SearchCategory.ALL -> {
                                val all = cached as SearchAllResults
                                _allResults.value = all
                                _searchResults.value = all.songs
                                _artistResults.value = all.artists
                                if (all.topResult is TopResult.ArtistResult) {
                                    _resolvedArtistId.value = all.topResult.artist.id
                                } else {
                                    _resolvedArtistId.value = null
                                }
                            }
                            SearchCategory.ARTISTS -> {
                                @Suppress("UNCHECKED_CAST")
                                _artistResults.value = cached as List<YtmArtistSearchItemDto>
                            }
                            else -> {
                                @Suppress("UNCHECKED_CAST")
                                _searchResults.value = cached as List<Track>
                            }
                        }
                        return@collectLatest
                    }

                    _isLoading.value = true

                    when (category) {
                        SearchCategory.ALL -> {
                            val fullSearch = serverYouTubeProvider.searchFull(q)

                            val resolvedSongs = fullSearch?.tracks?.map { it.toTrack() } ?: emptyList()
                            val resolvedVideos = fullSearch?.videos?.map { it.toTrack() } ?: emptyList()
                            val resolvedAlbums = fullSearch?.albums ?: emptyList()
                            val resolvedArtists = fullSearch?.artists ?: emptyList()
                            val resolvedPlaylists = fullSearch?.playlists ?: emptyList()

                            val resolvedTop = resolveTopResult(fullSearch?.topResult, resolvedSongs.firstOrNull())
                            if (resolvedTop is TopResult.ArtistResult) {
                                _resolvedArtistId.value = resolvedTop.artist.id
                            } else {
                                _resolvedArtistId.value = null
                            }

                            val all = SearchAllResults(
                                topResult = resolvedTop,
                                songs = resolvedSongs,
                                videos = resolvedVideos,
                                albums = resolvedAlbums,
                                artists = resolvedArtists,
                                playlists = resolvedPlaylists,
                                continuations = fullSearch?.continuations
                            )

                            // Populate cache for ALL and subcategories
                            searchCache.put(q to SearchCategory.ALL, all)
                            if (resolvedSongs.isNotEmpty()) {
                                searchCache.put(q to SearchCategory.SONGS, resolvedSongs)
                            }
                            if (resolvedArtists.isNotEmpty()) {
                                searchCache.put(q to SearchCategory.ARTISTS, resolvedArtists)
                            }
                            // Only pre-populate generic video/album/playlist cache if top result is NOT an artist
                            if (_resolvedArtistId.value == null) {
                                if (resolvedVideos.isNotEmpty()) {
                                    searchCache.put(q to SearchCategory.VIDEOS, resolvedVideos)
                                }
                                if (resolvedAlbums.isNotEmpty()) {
                                    searchCache.put(q to SearchCategory.ALBUMS, resolvedAlbums.map { it.toTrack() })
                                }
                                if (resolvedPlaylists.isNotEmpty()) {
                                    searchCache.put(q to SearchCategory.PLAYLISTS, resolvedPlaylists.map { it.toTrack() })
                                }
                            }

                            _allResults.value = all
                            _searchResults.value = resolvedSongs
                            _artistResults.value = resolvedArtists
                            _isLoading.value = false
                        }
                        SearchCategory.SONGS -> {
                            searchCoordinator.search(q).collect { songs ->
                                searchCache.put(q to SearchCategory.SONGS, songs)
                                _searchResults.value = songs
                                _isLoading.value = false
                            }
                        }
                        SearchCategory.VIDEOS -> {
                            val artistId = _resolvedArtistId.value
                            val videos = if (!artistId.isNullOrBlank()) {
                                runCatching {
                                    serverYouTubeProvider.getArtistVideos(artistId)
                                }.getOrDefault(emptyList())
                            } else {
                                runCatching {
                                    serverYouTubeProvider.search(q, filter = "videos")
                                }.getOrDefault(emptyList())
                            }
                            searchCache.put(q to SearchCategory.VIDEOS, videos)
                            _searchResults.value = videos
                            _isLoading.value = false
                        }
                        SearchCategory.ALBUMS -> {
                            val artistId = _resolvedArtistId.value
                            val albums = if (!artistId.isNullOrBlank()) {
                                runCatching {
                                    serverYouTubeProvider.getArtistAlbums(artistId)
                                }.getOrDefault(emptyList())
                            } else {
                                runCatching {
                                    serverYouTubeProvider.search(q, filter = "albums")
                                }.getOrDefault(emptyList())
                            }
                            searchCache.put(q to SearchCategory.ALBUMS, albums)
                            _searchResults.value = albums
                            _isLoading.value = false
                        }
                        SearchCategory.PLAYLISTS -> {
                            val artistId = _resolvedArtistId.value
                            val playlists = if (!artistId.isNullOrBlank()) {
                                runCatching {
                                    serverYouTubeProvider.getArtistPlaylists(artistId)
                                }.getOrDefault(emptyList())
                            } else {
                                runCatching {
                                    serverYouTubeProvider.search(q, filter = "playlists")
                                }.getOrDefault(emptyList())
                            }
                            searchCache.put(q to SearchCategory.PLAYLISTS, playlists)
                            _searchResults.value = playlists
                            _isLoading.value = false
                        }
                        SearchCategory.ARTISTS -> {
                            val artists = artistApiClient.searchArtists(q).getOrDefault(emptyList())
                            searchCache.put(q to SearchCategory.ARTISTS, artists)
                            _artistResults.value = artists
                            _isLoading.value = false
                        }
                    }
                }
        }

        viewModelScope.launch {
            rawResults.collect { tracks ->
                val top3 = tracks.take(3)
                if (top3.isNotEmpty()) {
                    val imageLoader = SingletonImageLoader.get(context)
                    top3.forEach { track ->
                        val url = track.artworkUrl
                        if (!url.isNullOrBlank()) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .build()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveTopResult(topResultDto: YtmTopResultDto?, fallbackTrack: Track?): TopResult? {
        if (topResultDto != null) {
            val parsed = serverYouTubeProvider.parseTopResult(topResultDto)
            when (parsed) {
                is Track -> return TopResult.TrackResult(parsed)
                is YtmArtistSearchItemDto -> return TopResult.ArtistResult(parsed)
                is YtmAlbumDto -> return TopResult.AlbumResult(parsed)
            }
        }
        return fallbackTrack?.let { TopResult.TrackResult(it) }
    }

    fun extractYoutubeVideoId(text: String): String? {
        val trimmed = text.trim()
        val watchRegex = Regex(
            """(?:https?://)?(?:music\.|www\.)?youtube\.com/(?:watch\?(?:[^&]+&)*v=|v/|embed/)([a-zA-Z0-9_-]{11})""",
            RegexOption.IGNORE_CASE
        )
        val watchMatch = watchRegex.find(trimmed)
        if (watchMatch != null) {
            return watchMatch.groupValues[1]
        }

        val youtuBeRegex = Regex(
            """(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})""",
            RegexOption.IGNORE_CASE
        )
        val youtuBeMatch = youtuBeRegex.find(trimmed)
        if (youtuBeMatch != null) {
            return youtuBeMatch.groupValues[1]
        }

        return null
    }

    fun onQueryChange(newQuery: String) {
        val trimmed = newQuery.trim()
        val previousTrimmed = _query.value.trim()
        _query.value = newQuery

        // Auto-reset category to ALL when search term changes significantly
        if (trimmed != previousTrimmed && trimmed.isNotEmpty() && previousTrimmed.isNotEmpty()) {
            _selectedCategory.value = SearchCategory.ALL
        }

        val videoId = extractYoutubeVideoId(newQuery)
        if (videoId != null) {
            val track = Track(
                id = videoId,
                title = "YouTube Video",
                artist = "YouTube Music",
                album = "YouTube",
                artworkUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = 0L,
                provider = ProviderId.YOUTUBE_MUSIC,
                youtubeVideoId = videoId
            )
            playerController.setPlayingFrom("Search")
            playerController.playTrack(track)
            viewModelScope.launch {
                searchHistoryRepository.addSearch(
                    query = newQuery,
                    type = "track",
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    subtitle = track.artist,
                    id = track.id
                )
            }
        }
    }

    fun submitSearch() {
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            val topResult = _allResults.value.topResult
            if (topResult is TopResult.ArtistResult) {
                _resolvedArtistId.value = topResult.artist.id
            }
            val top = rawResults.value.firstOrNull()
            viewModelScope.launch {
                searchHistoryRepository.addSearch(
                    query = q,
                    type = if (top != null) "track" else "query",
                    artworkUrl = top?.artworkUrl,
                    title = top?.title ?: q,
                    subtitle = if (top != null) "${top.artist}${if (top.album != null) " • ${top.album}" else ""}" else "Search",
                    id = top?.id ?: q
                )
            }
        }
    }

    fun onCategorySelect(category: SearchCategory) {
        _selectedCategory.value = category
    }

    fun playTrack(track: Track) {
        playerController.setPlayingFrom("Search")
        playerController.playTrack(track)
        viewModelScope.launch {
            searchHistoryRepository.addSearch(
                query = track.title,
                type = "track",
                artworkUrl = track.artworkUrl,
                title = track.title,
                subtitle = track.artist,
                id = track.id
            )
        }
    }

    fun onHistoryItemClick(item: SearchHistoryItem) {
        _query.value = item.query
    }

    fun removeSearch(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeSearch(query)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    suspend fun getShareUrl(track: Track): String {
        val videoId = trackLinkResolver.resolveYtmVideoId(track)
            ?: track.youtubeVideoId
            ?: track.id
        return "https://music.youtube.com/watch?v=$videoId"
    }

    fun addToQueue(track: Track) {
        playerController.addToQueue(track)
    }
}
