package com.mue.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.database.ImportedPlaylistDao
import com.mue.core.database.ImportedPlaylistTrackDao
import com.mue.core.database.LibraryDao
import com.mue.core.database.toTrack
import com.mue.core.model.PlaybackState
import com.mue.core.model.Track
import com.mue.core.player.PlayerController
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.ServerYouTubeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionInfo(
    val id: String,
    val type: String, // "playlist", "album", "ep"
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<Track>,
    val isImported: Boolean = false
)

sealed interface CollectionUiState {
    data class Loading(
        val initialTitle: String,
        val initialSubtitle: String,
        val initialArtwork: String?,
        val type: String
    ) : CollectionUiState

    data class Success(
        val collection: CollectionInfo,
        val currentPlayingId: String? = null,
        val isPlaying: Boolean = false
    ) : CollectionUiState

    data class Error(val message: String) : CollectionUiState
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val libraryDao: LibraryDao,
    private val importedPlaylistDao: ImportedPlaylistDao,
    private val importedPlaylistTrackDao: ImportedPlaylistTrackDao,
    private val playerController: PlayerController
) : ViewModel() {

    val collectionId: String = savedStateHandle.get<String>("id")
        ?: savedStateHandle.get<String>("collectionId")
        ?: savedStateHandle.get<String>("albumId")
        ?: savedStateHandle.get<String>("playlistId")
        ?: ""

    val type: String = savedStateHandle.get<String>("type") ?: "playlist"
    val initialTitle: String = savedStateHandle.get<String>("title")?.let { android.net.Uri.decode(it) } ?: ""
    val initialSubtitle: String = savedStateHandle.get<String>("sub")?.let { android.net.Uri.decode(it) }
        ?: savedStateHandle.get<String>("artist")?.let { android.net.Uri.decode(it) }
        ?: savedStateHandle.get<String>("author")?.let { android.net.Uri.decode(it) }
        ?: ""
    val initialArt: String? = savedStateHandle.get<String>("art")?.let { android.net.Uri.decode(it) }
    val isImported: Boolean = savedStateHandle.get<Boolean>("isImported") ?: false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _resolvedTitle = MutableStateFlow(initialTitle)
    private val _resolvedSubtitle = MutableStateFlow(initialSubtitle)
    private val _resolvedArtwork = MutableStateFlow(initialArt)
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val playerState: StateFlow<PlaybackState> = playerController.state

    val uiState: StateFlow<CollectionUiState> = combine(
        _tracks,
        _isLoading,
        _error,
        playerState
    ) { tracks, loading, error, playState ->
        val title = _resolvedTitle.value.ifBlank { initialTitle }
        val subtitle = _resolvedSubtitle.value.ifBlank { initialSubtitle }
        val artwork = _resolvedArtwork.value ?: initialArt

        when {
            loading && tracks.isEmpty() -> CollectionUiState.Loading(
                initialTitle = title,
                initialSubtitle = subtitle,
                initialArtwork = artwork,
                type = type
            )
            error != null && tracks.isEmpty() -> CollectionUiState.Error(error)
            else -> CollectionUiState.Success(
                collection = CollectionInfo(
                    id = collectionId,
                    type = type,
                    title = title.ifBlank { if (type.equals("album", true) || type.equals("ep", true)) "Album" else "Playlist" },
                    subtitle = subtitle,
                    artworkUrl = artwork,
                    tracks = tracks,
                    isImported = isImported
                ),
                currentPlayingId = playState.currentTrack?.id,
                isPlaying = playState.isPlaying
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CollectionUiState.Loading(initialTitle, initialSubtitle, initialArt, type)
    )

    init {
        loadCollection()
    }

    fun loadCollection() {
        if (collectionId.isBlank()) {
            _error.value = "Missing collection identifier"
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                when {
                    type.equals("album", ignoreCase = true) || type.equals("ep", ignoreCase = true) -> {
                        var fetched = innerTubeXProvider.getAlbum(collectionId)
                        if (fetched.isEmpty()) {
                            val playlistId = if (collectionId.startsWith("MPREb_")) {
                                collectionId.removePrefix("MPREb_")
                            } else collectionId
                            fetched = innerTubeXProvider.getPlaylist(playlistId)
                        }
                        if (fetched.isEmpty()) {
                            val searchResult = serverYouTubeProvider.search(initialTitle.ifBlank { collectionId }, filter = "songs")
                            if (searchResult.isNotEmpty()) {
                                fetched = searchResult
                            }
                        }

                        if (fetched.isNotEmpty()) {
                            _tracks.value = fetched
                            if (_resolvedArtwork.value.isNullOrBlank()) {
                                _resolvedArtwork.value = fetched.firstOrNull()?.artworkUrl
                            }
                            if (_resolvedSubtitle.value.isBlank()) {
                                _resolvedSubtitle.value = fetched.firstOrNull()?.artist ?: ""
                            }
                        } else {
                            _error.value = "Unable to load album tracks"
                        }
                    }

                    else -> { // Playlist
                        val localTracks = libraryDao.getPlaylistTracks(collectionId).firstOrNull()
                        if (!localTracks.isNullOrEmpty()) {
                            _tracks.value = localTracks.map { it.toTrack() }
                            _isLoading.value = false
                            return@launch
                        }

                        if (isImported) {
                            val importedTracks = importedPlaylistTrackDao.getTracksForPlaylist(collectionId).firstOrNull()
                            if (!importedTracks.isNullOrEmpty()) {
                                _tracks.value = importedTracks.map { it.toTrack() }
                                _isLoading.value = false
                                return@launch
                            }
                        }

                        var fetched = innerTubeXProvider.getPlaylist(collectionId)
                        if (fetched.isEmpty()) {
                            val altId = if (collectionId.startsWith("VL")) collectionId.removePrefix("VL") else collectionId
                            fetched = innerTubeXProvider.getPlaylist(altId)
                        }
                        if (fetched.isEmpty()) {
                            val searchResult = serverYouTubeProvider.search(initialTitle.ifBlank { collectionId }, filter = "songs")
                            if (searchResult.isNotEmpty()) {
                                fetched = searchResult
                            }
                        }

                        if (fetched.isNotEmpty()) {
                            _tracks.value = fetched
                            if (_resolvedArtwork.value.isNullOrBlank()) {
                                _resolvedArtwork.value = fetched.firstOrNull()?.artworkUrl
                            }
                        } else {
                            _error.value = "No tracks found for this playlist"
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load collection"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playAll(shuffle: Boolean = false) {
        val list = _tracks.value
        if (list.isEmpty()) return
        val queue = if (shuffle) list.shuffled() else list
        playerController.setPlayingFrom(_resolvedTitle.value.ifBlank { "Collection" })
        playerController.setQueue(queue)
        playerController.playTrack(queue.first())
    }

    fun playTrack(track: Track) {
        val list = _tracks.value
        val queue = if (list.isNotEmpty()) list else listOf(track)
        playerController.setPlayingFrom(_resolvedTitle.value.ifBlank { "Collection" })
        playerController.setQueue(queue)
        playerController.playTrack(track)
    }

    fun addToQueue(track: Track) {
        playerController.addToQueue(track)
    }
}
