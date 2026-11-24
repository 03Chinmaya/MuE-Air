package com.mue.feature.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.database.LibraryDao
import com.mue.core.database.LikedSongEntity
import com.mue.core.database.PlaylistEntity
import com.mue.core.database.PlaylistTrackEntity
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.mue.core.lyrics.LyricsRepository
import com.mue.core.model.PlaybackState
import com.mue.core.model.Track
import com.mue.core.player.PlayerController
import com.mue.core.player.TrackLinkResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    val lyricsRepository: LyricsRepository,
    private val trackLinkResolver: TrackLinkResolver,
    private val libraryDao: LibraryDao
) : ViewModel() {
    val state: StateFlow<PlaybackState> = playerController.state
    val queue: StateFlow<List<Track>> = playerController.queue
    val audioRoute: StateFlow<String> = playerController.audioRoute
    val playingFrom: StateFlow<String> = playerController.playingFrom
    val sleepTimerMinutes: StateFlow<Int?> = playerController.sleepTimerMinutes

    @OptIn(ExperimentalCoroutinesApi::class)
    val isLiked: StateFlow<Boolean> = state
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId.isNullOrBlank()) {
                flowOf(false)
            } else {
                libraryDao.isSongLiked(trackId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleLike(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentlyLiked = libraryDao.isSongLiked(track.id).first()
            if (currentlyLiked) {
                libraryDao.deleteLikedSong(track.id)
            } else {
                libraryDao.insertLikedSong(
                    LikedSongEntity(
                        songId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        provider = track.provider.name,
                        likedAt = System.currentTimeMillis(),
                        isDirty = true
                    )
                )
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        playerController.setPreferredAudioDevice(device)
    }

    fun setSleepTimer(minutes: Int?) {
        playerController.setSleepTimer(minutes)
    }

    fun setPlayingFrom(source: String) {
        playerController.setPlayingFrom(source)
    }

    fun saveToLibrary(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryDao.insertLikedSong(
                LikedSongEntity(
                    songId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl,
                    durationMs = track.durationMs,
                    provider = track.provider.name,
                    likedAt = System.currentTimeMillis(),
                    isDirty = true
                )
            )
        }
    }

    fun getPlaylists(): Flow<List<PlaylistEntity>> = libraryDao.getPlaylists()

    val userPlaylists: StateFlow<List<PlaylistEntity>> = libraryDao.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTrackToPlaylist(track: Track, playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trackEntity = PlaylistTrackEntity(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                position = 0,
                provider = track.provider.name,
                providerTrackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                addedAt = System.currentTimeMillis(),
                isDirty = true
            )
            libraryDao.insertPlaylistTrack(trackEntity)
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = UUID.randomUUID().toString()
            val playlistEntity = PlaylistEntity(
                id = playlistId,
                name = name.trim(),
                artworkUrl = track.artworkUrl,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isDirty = true
            )
            libraryDao.insertPlaylist(playlistEntity)
            val trackEntity = PlaylistTrackEntity(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                position = 0,
                provider = track.provider.name,
                providerTrackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                addedAt = System.currentTimeMillis(),
                isDirty = true
            )
            libraryDao.insertPlaylistTrack(trackEntity)
        }
    }

    fun playTrack(track: Track) {
        playerController.playTrack(track)
    }

    fun playTrack(track: Track, streamUrl: String) {
        playerController.playTrack(track, streamUrl)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun skipToNext() {
        playerController.skipToNext()
    }

    fun skipToPrevious() {
        playerController.skipToPrevious()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeat() {
        playerController.toggleRepeat()
    }

    fun setQueue(tracks: List<Track>) {
        playerController.setQueue(tracks)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, source: String = "Unknown") {
        playerController.playQueue(tracks, startIndex, source)
    }

    fun removeFromQueue(trackId: String) {
        playerController.removeFromQueue(trackId)
    }

    fun shareTrack(track: Track, context: Context) {
        viewModelScope.launch {
            val ytmId = trackLinkResolver.resolveYtmVideoId(track)
            val shareText = if (!ytmId.isNullOrBlank()) {
                "https://music.youtube.com/watch?v=$ytmId"
            } else {
                "Listening to ${track.title} by ${track.artist} on MuE"
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "${track.title} — ${track.artist}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share track").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        }
    }
}


