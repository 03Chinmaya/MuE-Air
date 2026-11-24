package com.mue.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.auth.AuthRepository
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.Track
import com.mue.core.player.ListenTogetherService
import com.mue.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    private val listenTogetherService: ListenTogetherService,
    private val playerController: PlayerController,
    private val settingsDataStore: SettingsDataStore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Lobby)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var connectingJob: Job? = null

    val currentRoom: StateFlow<String?> = listenTogetherService.currentRoomFlow
    val isHost: StateFlow<Boolean> = listenTogetherService.isHost
    val syncStatus: StateFlow<String> = listenTogetherService.syncStatusFlow
    val participantsCount: StateFlow<Int> = listenTogetherService.participantsCountFlow

    val maxListeners: StateFlow<Int> = listenTogetherService.maxListenersFlow
    val djMode: StateFlow<String> = listenTogetherService.djModeFlow
    val roomPrivacy: StateFlow<String> = listenTogetherService.roomPrivacyFlow

    val defaultMaxListeners: StateFlow<Int> = settingsDataStore.defaultMaxListenersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val defaultDjMode: StateFlow<String> = settingsDataStore.defaultDjModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DJ")

    val defaultRoomPrivacy: StateFlow<String> = settingsDataStore.defaultRoomPrivacyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Invite Link")

    val ltJoinViaLinkBehavior: StateFlow<String> = settingsDataStore.ltJoinViaLinkBehaviorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ask first")

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    val currentTrack: StateFlow<Track?> = playerController.state
        .map { it.currentTrack }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerController.state.value.currentTrack)

    val isPlaying: StateFlow<Boolean> = playerController.state
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerController.state.value.isPlaying)

    init {
        viewModelScope.launch {
            authRepository.ensureGuestSession()
        }

        viewModelScope.launch {
            listenTogetherService.hostDisconnectedFlow.collect { graceSeconds ->
                val current = _uiState.value
                if (current is UiState.InRoom && !current.isHost) {
                    _uiState.value = current.copy(
                        isHostDisconnected = true,
                        graceSecondsRemaining = graceSeconds,
                        syncStatus = SyncStatus.RECONNECTING
                    )
                    startGraceCountdown(graceSeconds)
                }
            }
        }

        viewModelScope.launch {
            listenTogetherService.hostReconnectedFlow.collect {
                graceCountdownJob?.cancel()
                graceCountdownJob = null
                val current = _uiState.value
                if (current is UiState.InRoom && !current.isHost) {
                    _uiState.value = current.copy(
                        isHostDisconnected = false,
                        graceSecondsRemaining = 0,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
            }
        }

        viewModelScope.launch {
            listenTogetherService.sessionEndedFlow.collect { reason ->
                graceCountdownJob?.cancel()
                graceCountdownJob = null
                _uiState.value = UiState.Error(reason)
            }
        }

        viewModelScope.launch {
            listenTogetherService.authFailedFlow.collect { msg ->
                _uiState.value = UiState.Error("Authentication expired: $msg")
            }
        }

        viewModelScope.launch {
            combine(
                listenTogetherService.currentRoomFlow,
                listenTogetherService.isHost,
                listenTogetherService.syncStatusFlow,
                listenTogetherService.participantsCountFlow,
                listenTogetherService.currentDriftMsFlow
            ) { room, host, statusStr, count, driftMs ->
                StateSnapshot(room, host, statusStr, count, driftMs)
            }.collect { snapshot ->
                if (snapshot.room == null) {
                    if (_uiState.value !is UiState.Connecting && _uiState.value !is UiState.Error) {
                        _uiState.value = UiState.Lobby
                    }
                } else {
                    val current = _uiState.value
                    val isHostDisconnected = (current as? UiState.InRoom)?.isHostDisconnected == true
                    val graceRemaining = (current as? UiState.InRoom)?.graceSecondsRemaining ?: 0
                    if (current is UiState.InRoom || current is UiState.Lobby || current is UiState.Connecting) {
                        val participants = generateParticipants(snapshot.isHost, snapshot.count)
                        val status = when {
                            isHostDisconnected -> SyncStatus.RECONNECTING
                            snapshot.statusStr.equals("reconnecting", ignoreCase = true) -> SyncStatus.RECONNECTING
                            snapshot.statusStr.equals("synced", ignoreCase = true) -> SyncStatus.SYNCED
                            snapshot.statusStr.equals("syncing", ignoreCase = true) ||
                                snapshot.statusStr.equals("buffering", ignoreCase = true) -> SyncStatus.BUFFERING
                            snapshot.statusStr.equals("drift", ignoreCase = true) -> SyncStatus.DRIFT
                            else -> SyncStatus.IDLE
                        }
                        _uiState.value = UiState.InRoom(
                            isHost = snapshot.isHost,
                            roomCode = snapshot.room,
                            syncStatus = status,
                            participants = participants,
                            currentDriftMs = snapshot.driftMs,
                            isHostDisconnected = isHostDisconnected,
                            graceSecondsRemaining = graceRemaining
                        )
                    }
                }
            }
        }
    }

    private var graceCountdownJob: Job? = null

    private fun startGraceCountdown(initialSeconds: Int) {
        graceCountdownJob?.cancel()
        graceCountdownJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                val current = _uiState.value
                if (current is UiState.InRoom && current.isHostDisconnected) {
                    _uiState.value = current.copy(graceSecondsRemaining = remaining)
                }
            }
        }
    }

    fun createRoom(
        maxListeners: Int = defaultMaxListeners.value,
        djMode: String = defaultDjMode.value,
        roomPrivacy: String = defaultRoomPrivacy.value
    ) {
        _uiState.value = UiState.Connecting(isHost = true, roomCode = "")
        connectingJob?.cancel()
        connectingJob = viewModelScope.launch {
            authRepository.ensureGuestSession()
            val result = listenTogetherService.createRoomSession(
                maxListeners = maxListeners,
                djMode = djMode,
                roomPrivacy = roomPrivacy
            )
            result.onSuccess { code ->
                _uiState.value = UiState.InRoom(
                    isHost = true,
                    roomCode = code,
                    syncStatus = SyncStatus.SYNCED,
                    participants = listOf(Participant("host_me", "You (Host)", isHost = true))
                )
            }.onFailure { error ->
                _uiState.value = UiState.Error(error.message ?: "Failed to create room")
            }
        }
    }

    fun joinRoom(code: String) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isNotBlank()) {
            _uiState.value = UiState.Connecting(isHost = false, roomCode = cleanCode)
            connectingJob?.cancel()
            connectingJob = viewModelScope.launch {
                authRepository.ensureGuestSession()
                val result = listenTogetherService.joinRoomSession(cleanCode)
                result.onSuccess {
                    val participants = generateParticipants(false, listenTogetherService.participantsCountFlow.value)
                    _uiState.value = UiState.InRoom(
                        isHost = false,
                        roomCode = cleanCode,
                        syncStatus = SyncStatus.BUFFERING,
                        participants = participants
                    )
                }.onFailure { error ->
                    _uiState.value = UiState.Error(error.message ?: "Room not found or full")
                }
            }
        }
    }

    fun leaveRoom() {
        graceCountdownJob?.cancel()
        graceCountdownJob = null
        connectingJob?.cancel()
        connectingJob = null
        if (_isMuted.value) {
            _isMuted.value = false
            playerController.setMuted(false)
        }
        listenTogetherService.leaveRoom()
        _uiState.value = UiState.Lobby
    }

    fun endSession() {
        graceCountdownJob?.cancel()
        graceCountdownJob = null
        connectingJob?.cancel()
        connectingJob = null
        if (_isMuted.value) {
            _isMuted.value = false
            playerController.setMuted(false)
        }
        listenTogetherService.endRoom()
        _uiState.value = UiState.Lobby
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        playerController.setMuted(next)
    }

    fun skipToNext() {
        playerController.skipToNext()
    }

    fun skipToPrevious() {
        playerController.skipToPrevious()
    }

    fun resync() {
        listenTogetherService.syncPlayback()
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    private fun generateParticipants(isHost: Boolean, count: Int): List<Participant> {
        val list = mutableListOf<Participant>()
        if (isHost) {
            list.add(Participant(id = "host_me", displayName = "You (Host)", isHost = true))
            for (i in 1 until maxOf(1, count)) {
                list.add(Participant(id = "listener_$i", displayName = "Listener $i", isHost = false))
            }
        } else {
            list.add(Participant(id = "host_user", displayName = "Host", isHost = true))
            list.add(Participant(id = "guest_me", displayName = "You", isHost = false))
            for (i in 2 until maxOf(2, count)) {
                list.add(Participant(id = "listener_$i", displayName = "Listener $i", isHost = false))
            }
        }
        return list
    }

    private data class StateSnapshot(
        val room: String?,
        val isHost: Boolean,
        val statusStr: String,
        val count: Int,
        val driftMs: Long
    )
}
