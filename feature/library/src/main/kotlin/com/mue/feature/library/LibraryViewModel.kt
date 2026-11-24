package com.mue.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.database.DownloadDao
import com.mue.core.database.DownloadEntity
import com.mue.core.database.ImportedPlaylistDao
import com.mue.core.database.ImportedPlaylistTrackDao
import com.mue.core.database.LibraryDao
import com.mue.core.database.LikedSongEntity
import com.mue.core.database.PlaybackHistoryEntity
import com.mue.core.database.PlaylistEntity
import com.mue.core.database.toTrack
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.network.PlaylistApiClient
import com.mue.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class UnifiedPlaylist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val source: String = "local",
    val updatedAt: Long = System.currentTimeMillis(),
    val isImported: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val importedPlaylistDao: ImportedPlaylistDao,
    private val importedPlaylistTrackDao: ImportedPlaylistTrackDao,
    private val downloadDao: DownloadDao,
    private val playerController: PlayerController,
    private val playlistApiClient: PlaylistApiClient,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val librarySortOrder: StateFlow<String> = settingsDataStore.librarySortOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Recently Added")

    fun setLibrarySortOrder(order: String) {
        viewModelScope.launch { settingsDataStore.setLibrarySortOrder(order) }
    }

    val libraryViewMode: StateFlow<String> = settingsDataStore.libraryViewModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "grid")

    fun setLibraryViewMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setLibraryViewMode(mode) }
    }

    val likedCount: StateFlow<Int> = libraryDao.getLikedSongsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadsCount: StateFlow<Int> = downloadDao.getDownloadsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val historyCount: StateFlow<Int> = libraryDao.getRecentHistoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val likedSongs: StateFlow<List<LikedSongEntity>> = libraryDao.getLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongs: StateFlow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = libraryDao.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<PlaybackHistoryEntity>> = libraryDao.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedArtworks: StateFlow<List<String>> = likedSongs
        .map { it.take(3).mapNotNull { s -> s.artworkUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyArtworks: StateFlow<List<String>> = recentHistory
        .map { it.take(3).mapNotNull { h -> h.artworkUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Unified Playlists (Local + Imported) with actual track count
    val unifiedPlaylists: StateFlow<List<UnifiedPlaylist>> = combine(
        libraryDao.getPlaylists(),
        importedPlaylistDao.getAll(),
        libraryDao.getAllPlaylistTracks()
    ) { userPls, importedPls, allTracks ->
        val userMapped = userPls.map { p ->
            val count = allTracks.count { it.playlistId == p.id }
            UnifiedPlaylist(
                id = p.id,
                name = p.name,
                artworkUrl = p.artworkUrl,
                trackCount = count,
                source = "local",
                updatedAt = p.updatedAt,
                isImported = false
            )
        }
        val importedMapped = importedPls.map { ip ->
            UnifiedPlaylist(
                id = ip.id,
                name = ip.name,
                artworkUrl = ip.artworkUrl,
                trackCount = ip.trackCount,
                source = ip.source,
                updatedAt = ip.importedAt,
                isImported = true
            )
        }
        (userMapped + importedMapped).sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistArtworks: StateFlow<List<String>> = unifiedPlaylists
        .map { it.take(3).mapNotNull { p -> p.artworkUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsCount: StateFlow<Int> = unifiedPlaylists
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            try {
                playlistApiClient.getUnifiedPlaylists()
            } catch (_: Exception) {}
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track), source: String = "Library") {
        viewModelScope.launch {
            playerController.setPlayingFrom(source)
            playerController.setQueue(queue)
            playerController.playTrack(track)
        }
    }

    fun playQueue(queue: List<Track>, shuffle: Boolean = false, source: String = "Library") {
        if (queue.isEmpty()) return
        viewModelScope.launch {
            val targetQueue = if (shuffle) queue.shuffled() else queue
            playerController.setPlayingFrom(source)
            playerController.setQueue(targetQueue)
            playerController.playTrack(targetQueue.first())
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val entity = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                artworkUrl = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            libraryDao.insertPlaylist(entity)
        }
    }

    fun playPlaylist(id: String, isImported: Boolean) {
        viewModelScope.launch {
            val playlistName = unifiedPlaylists.value.find { it.id == id }?.name ?: "Playlist"
            playerController.setPlayingFrom(playlistName)
            if (isImported) {
                val tracks = importedPlaylistTrackDao.getTracksForPlaylist(id).firstOrNull() ?: emptyList()
                if (tracks.isNotEmpty()) {
                    val domainTracks = tracks.map { it.toTrack() }
                    playerController.setQueue(domainTracks)
                    playerController.playTrack(domainTracks.first())
                }
            } else {
                val tracks = libraryDao.getPlaylistTracks(id).firstOrNull() ?: emptyList()
                if (tracks.isNotEmpty()) {
                    val domainTracks = tracks.map { pt ->
                        Track(
                            id = pt.providerTrackId,
                            title = pt.title,
                            artist = pt.artist,
                            album = pt.album ?: "",
                            artworkUrl = pt.artworkUrl,
                            durationMs = pt.durationMs,
                            provider = try { ProviderId.valueOf(pt.provider) } catch (_: Exception) { ProviderId.JIOSAAVN }
                        )
                    }
                    playerController.setQueue(domainTracks)
                    playerController.playTrack(domainTracks.first())
                }
            }
        }
    }
}
