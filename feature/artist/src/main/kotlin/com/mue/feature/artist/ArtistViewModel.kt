package com.mue.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.model.PlaybackState
import com.mue.core.model.Track
import com.mue.core.providers.ContentQualityFilter
import com.mue.core.network.ArtistApiClient
import com.mue.core.network.ArtistDetailDto
import com.mue.core.network.ArtistTrackDto
import com.mue.core.network.ArtistVideoDto
import com.mue.core.player.PlayerController
import com.mue.core.artist.ArtistService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Success(
        val artist: ArtistDetailDto,
        val isFollowing: Boolean,
        val isContentLoading: Boolean = false
    ) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val artistService: ArtistService,
    private val artistApiClient: ArtistApiClient,
    private val playerController: PlayerController
) : ViewModel() {

    val artistId: String = savedStateHandle.get<String>("artistId") ?: ""
    private val initialName: String? = savedStateHandle.get<String>("name")?.takeIf { it.isNotBlank() }
    private val initialSubs: String? = savedStateHandle.get<String>("subs")?.takeIf { it.isNotBlank() }
    private val initialArt: String? = savedStateHandle.get<String>("art")?.takeIf { it.isNotBlank() }

    private val _artistDetail = MutableStateFlow<ArtistDetailDto?>(
        if (!initialName.isNullOrBlank()) {
            ArtistDetailDto(
                id = artistId,
                name = initialName,
                artworkUrl = initialArt,
                subscriberCount = initialSubs
            )
        } else null
    )
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _videos = MutableStateFlow<List<ArtistVideoDto>>(emptyList())
    val videos: StateFlow<List<ArtistVideoDto>> = _videos.asStateFlow()

    private fun filterAndSortTracks(tracks: List<ArtistTrackDto>): List<ArtistTrackDto> {
        return tracks
            .filter { t ->
                val title = t.title ?: ""
                val artist = t.artist ?: ""
                val album = t.album ?: ""
                val isAi = ContentQualityFilter.isAI(title, artist, album) || ContentQualityFilter.isCounterfeit(title, artist, album)
                val isUgc = t.videoType.equals("UGC", ignoreCase = true)
                !isAi && !isUgc
            }
            .sortedBy { t ->
                when (t.videoType) {
                    "ATV" -> 0
                    "OMV" -> 1
                    else -> 2
                }
            }
    }

    val playerState: StateFlow<PlaybackState> = playerController.state

    val uiState: StateFlow<ArtistUiState> = combine(
        _artistDetail,
        _isLoading,
        _error,
        artistService.isFollowing(artistId)
    ) { detail: ArtistDetailDto?, loading: Boolean, error: String?, isFollowing: Boolean ->
        when {
            error != null && (detail == null || detail.topTracks.isEmpty()) -> ArtistUiState.Error(error)
            detail != null -> ArtistUiState.Success(
                artist = detail,
                isFollowing = isFollowing,
                isContentLoading = loading
            )
            loading -> ArtistUiState.Loading
            error != null -> ArtistUiState.Error(error)
            else -> ArtistUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistUiState.Loading)

    init {
        loadArtist()
    }

    fun loadArtist() {
        if (artistId.isBlank()) {
            _error.value = "Invalid artist identifier"
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            var resolvedId = artistId
            if (!resolvedId.startsWith("UC")) {
                val directTest = artistService.getArtistDetails(resolvedId)
                if (directTest.isSuccess) {
                    val data = directTest.getOrNull()
                    if (data != null) {
                        val cleanedTracks = filterAndSortTracks(data.topTracks)
                        _artistDetail.value = data.copy(
                            artworkUrl = data.artworkUrl ?: initialArt,
                            subscriberCount = data.subscriberCount ?: initialSubs,
                            topTracks = cleanedTracks
                        )
                    }
                    _isLoading.value = false
                    loadVideos(resolvedId)
                    return@launch
                }
                val searchList = artistApiClient.searchArtists(resolvedId).getOrNull()
                val firstArtist = searchList?.firstOrNull()
                if (firstArtist != null) {
                    resolvedId = firstArtist.id
                }
            }
            artistService.getArtistDetails(resolvedId)
                .onSuccess { data ->
                    val cleanedTracks = filterAndSortTracks(data.topTracks)
                    _artistDetail.value = data.copy(
                        artworkUrl = data.artworkUrl ?: initialArt,
                        subscriberCount = data.subscriberCount ?: initialSubs,
                        topTracks = cleanedTracks
                    )
                    _isLoading.value = false
                    loadVideos(resolvedId)
                }
                .onFailure { err ->
                    _error.value = err.message ?: "Failed to load artist details"
                    _isLoading.value = false
                }
        }
    }

    private fun loadVideos(id: String) {
        viewModelScope.launch {
            artistService.getArtistVideos(id).onSuccess { vids ->
                _videos.value = vids.filter { v ->
                    val title = v.title ?: v.name ?: ""
                    !ContentQualityFilter.isAI(title) && !ContentQualityFilter.isCounterfeit(title) && !v.videoType.equals("UGC", ignoreCase = true)
                }
            }
        }
    }

    fun toggleFollow() {
        val detail = _artistDetail.value ?: return
        viewModelScope.launch {
            artistService.toggleFollow(
                artistId = detail.id,
                artistName = detail.name,
                thumbnailUrl = detail.artworkUrl
            )
        }
    }

    fun playTrack(track: Track) {
        val artistName = _artistDetail.value?.name ?: "Artist Profile"
        playerController.setPlayingFrom(artistName)
        val tracks = _artistDetail.value?.topTracks?.map { it.toTrack() } ?: listOf(track)
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.playQueue(tracks, index, artistName)
    }

    fun playAll() {
        val artistName = _artistDetail.value?.name ?: "Artist Profile"
        playerController.setPlayingFrom(artistName)
        val tracks = _artistDetail.value?.topTracks?.map { it.toTrack() } ?: return
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, 0, artistName)
        }
    }

    fun shuffle() {
        val artistName = _artistDetail.value?.name ?: "Artist Profile"
        playerController.setPlayingFrom(artistName)
        val tracks = _artistDetail.value?.topTracks?.map { it.toTrack() }?.shuffled() ?: return
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, 0, artistName)
        }
    }
}

