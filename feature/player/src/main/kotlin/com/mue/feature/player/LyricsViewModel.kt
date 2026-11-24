package com.mue.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.lyrics.LyricsRepository
import com.mue.core.lyrics.model.TrackLyrics
import com.mue.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val trackLyrics: StateFlow<TrackLyrics?> = playerController.state.flatMapLatest { state ->
        val track = state.currentTrack
        if (track != null) {
            lyricsRepository.getLyrics(track.id, track.title, track.artist)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentLine: StateFlow<String?> = combine(
        trackLyrics,
        playerController.state
    ) { lyrics, playbackState ->
        val lines = lyrics?.lines.orEmpty()
        val pos = playbackState.positionMs
        if (lines.isEmpty()) {
            null
        } else {
            val active = lines.firstOrNull { pos in it.lineStartMs..it.lineEndMs }
                ?: lines.lastOrNull { pos >= it.lineStartMs }
                ?: lines.firstOrNull()
            active?.text
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

