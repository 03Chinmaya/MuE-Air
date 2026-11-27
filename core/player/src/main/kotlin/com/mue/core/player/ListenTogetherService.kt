package com.mue.core.player

import com.mue.core.datastore.SecureStore
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.Track
import com.mue.core.network.CreateSessionResult
import com.mue.core.network.JoinSessionResult
import com.mue.core.network.ListenApiClient
import com.mue.core.network.SocketIOManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

data class RoomChatMessage(
    val userId: String,
    val text: String,
    val timestamp: Long
)

data class RoomReaction(
    val userId: String,
    val emoji: String,
    val trackId: String,
    val id: Long = System.nanoTime()
)

@Singleton
class ListenTogetherService @Inject constructor(
    private val playerController: PlayerController,
    private val socketManager: SocketIOManager,
    private val settingsDataStore: SettingsDataStore,
    private val secureStore: SecureStore? = null,
    private val listenApiClient: ListenApiClient? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var pingJob: Job? = null

    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomFlow: StateFlow<String?> = _currentRoomId.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _syncStatus = MutableStateFlow("idle")
    val syncStatusFlow: StateFlow<String> = _syncStatus.asStateFlow()

    private val _clockOffsetMs = MutableStateFlow(0L)
    val clockOffsetMsFlow: StateFlow<Long> = _clockOffsetMs.asStateFlow()

    private val _rttMs = MutableStateFlow(0L)
    val rttMsFlow: StateFlow<Long> = _rttMs.asStateFlow()

    private val _currentDriftMs = MutableStateFlow(0L)
    val currentDriftMsFlow: StateFlow<Long> = _currentDriftMs.asStateFlow()

    private val _hostDisconnectedFlow = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val hostDisconnectedFlow: SharedFlow<Int> = _hostDisconnectedFlow.asSharedFlow()

    private val _hostReconnectedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val hostReconnectedFlow: SharedFlow<Unit> = _hostReconnectedFlow.asSharedFlow()

    private val _sessionEndedFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val sessionEndedFlow: SharedFlow<String> = _sessionEndedFlow.asSharedFlow()

    private val _authFailedFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val authFailedFlow: SharedFlow<String> = _authFailedFlow.asSharedFlow()

    private val _participantsCount = MutableStateFlow(1)
    val participantsCountFlow: StateFlow<Int> = _participantsCount.asStateFlow()

    private val _maxListeners = MutableStateFlow(10)
    val maxListenersFlow: StateFlow<Int> = _maxListeners.asStateFlow()

    private val _djMode = MutableStateFlow("DJ")
    val djModeFlow: StateFlow<String> = _djMode.asStateFlow()

    private val _roomPrivacy = MutableStateFlow("Invite Link")
    val roomPrivacyFlow: StateFlow<String> = _roomPrivacy.asStateFlow()

    private val _joinViaLinkBehavior = MutableStateFlow("Ask first")
    val joinViaLinkBehaviorFlow: StateFlow<String> = _joinViaLinkBehavior.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<RoomChatMessage>>(emptyList())
    val chatMessagesFlow: StateFlow<List<RoomChatMessage>> = _chatMessages.asStateFlow()

    private val _reactions = MutableStateFlow<List<RoomReaction>>(emptyList())
    val reactionsFlow: StateFlow<List<RoomReaction>> = _reactions.asStateFlow()

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActiveFlow: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    init {
        scope.launch {
            settingsDataStore.ltJoinViaLinkBehaviorFlow.collect { _joinViaLinkBehavior.value = it }
        }
        scope.launch {
            settingsDataStore.defaultMaxListenersFlow.collect { _maxListeners.value = it }
        }
        scope.launch {
            settingsDataStore.defaultDjModeFlow.collect { _djMode.value = it }
        }
        scope.launch {
            settingsDataStore.defaultRoomPrivacyFlow.collect { _roomPrivacy.value = it }
        }

        socketManager.onListenTogetherSync { roomId, trackId, positionMs, isPlaying ->
            if (!_isHost.value && roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                val adjustedPos = maxOf(0L, positionMs + _clockOffsetMs.value)
                val currentPos = playerController.state.value.positionMs
                val drift = kotlin.math.abs(currentPos - adjustedPos)
                _currentDriftMs.value = drift
                if (drift > 150L) {
                    _syncStatus.value = "drift"
                    if (drift > 1500L) {
                        playerController.applyListenTogetherSync(trackId, adjustedPos, isPlaying)
                    }
                } else {
                    _syncStatus.value = "synced"
                    playerController.applyListenTogetherSync(trackId, adjustedPos, isPlaying)
                }
            }
        }

        socketManager.onSyncPing { t0, _, roomId ->
            if (_isHost.value && roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                socketManager.emitSyncPong(t0 = t0, t1 = System.currentTimeMillis(), roomId = roomId)
            }
        }

        socketManager.onSyncPong { t0, t1, roomId ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                val t1Local = System.currentTimeMillis()
                val rtt = maxOf(0L, t1Local - t0)
                _rttMs.value = rtt
                _clockOffsetMs.value = t1 - (t0 + rtt / 2)
            }
        }

        socketManager.onHostDisconnected { roomId, graceSeconds ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                _syncStatus.value = "reconnecting"
                scope.launch { _hostDisconnectedFlow.emit(graceSeconds) }
            }
        }

        socketManager.onHostReconnected { roomId ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                _syncStatus.value = "synced"
                scope.launch { _hostReconnectedFlow.emit(Unit) }
            }
        }

        socketManager.onListenTogetherEnded { roomId, reason ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                val endReason = reason ?: "Host left session"
                scope.launch { _sessionEndedFlow.emit(endReason) }
                leaveRoom()
            }
        }

        socketManager.onAuthFailed { message ->
            scope.launch { _authFailedFlow.emit(message) }
        }

        socketManager.onListenTogetherParticipantJoined { roomId, _ ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                _participantsCount.value += 1
            }
        }

        socketManager.onListenTogetherParticipantLeft { roomId, _ ->
            if (roomId.equals(_currentRoomId.value, ignoreCase = true)) {
                _participantsCount.value = maxOf(1, _participantsCount.value - 1)
            }
        }

        socketManager.onChatMessage { userId, text, timestamp ->
            if (_currentRoomId.value != null) {
                _chatMessages.value = (_chatMessages.value + RoomChatMessage(userId, text, timestamp)).takeLast(100)
            }
        }

        socketManager.onReaction { userId, emoji, trackId ->
            if (_currentRoomId.value != null) {
                _reactions.value = (_reactions.value + RoomReaction(userId, emoji, trackId)).takeLast(12)
            }
        }

        socketManager.onVoiceActive { _, isActive ->
            if (_currentRoomId.value != null) {
                _isVoiceActive.value = isActive
                playerController.setVoiceDucking(isActive)
            }
        }
    }

    fun sendChatMessage(text: String) {
        val roomId = _currentRoomId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            socketManager.emitChatMessage(roomId, trimmed)
        }
    }

    fun sendReaction(emoji: String, trackId: String) {
        val roomId = _currentRoomId.value ?: return
        socketManager.emitReaction(roomId, emoji, trackId)
    }

    fun setVoiceActive(active: Boolean) {
        val roomId = _currentRoomId.value ?: return
        _isVoiceActive.value = active
        playerController.setVoiceDucking(active)
        socketManager.emitVoiceActive(roomId, active)
    }

    fun addTrackToRoom(track: Track) {
        val roomId = _currentRoomId.value ?: return
        playerController.addToUserQueue(track)
        socketManager.emitQueueUpdate(roomId, track.title)
    }

    private suspend fun ensureSocketConnected() {
        val token = secureStore?.getAccessToken() ?: secureStore?.getGuestToken() ?: ""
        val deviceId = try { settingsDataStore.deviceIdFlow.first() } catch (_: Throwable) { "android-device" }
        if (token.isNotBlank()) {
            socketManager.connect(token, deviceId)
        }
    }

    suspend fun createRoomSession(
        maxListeners: Int = _maxListeners.value,
        djMode: String = _djMode.value,
        roomPrivacy: String = _roomPrivacy.value
    ): Result<String> {
        val trackId = playerController.state.value.currentTrack?.id ?: ""
        val result = if (listenApiClient != null) {
            listenApiClient.createSession(
                trackId = trackId,
                provider = "jiosaavn",
                startPositionMs = playerController.state.value.positionMs,
                isPlaying = playerController.state.value.isPlaying,
                controlMode = if (djMode == "DJ") "open" else "host-only"
            )
        } else {
            CreateSessionResult.Success(com.mue.core.network.CreateSessionResponse(joinCode = "TEST01", sessionId = "test-uuid"))
        }

        return when (result) {
            is CreateSessionResult.Success -> {
                val joinCode = result.response.joinCode
                _currentRoomId.value = joinCode
                _isHost.value = true
                _syncStatus.value = "synced"
                _participantsCount.value = 1
                _maxListeners.value = maxListeners.coerceIn(2, 20)
                _djMode.value = djMode
                _roomPrivacy.value = roomPrivacy

                ensureSocketConnected()
                socketManager.emitListenTogetherJoin(joinCode)
                startSyncHeartbeat(isHost = true)
                Result.success(joinCode)
            }
            is CreateSessionResult.Error -> Result.failure(Exception(result.message))
        }
    }

    fun createRoom(
        maxListeners: Int = _maxListeners.value,
        djMode: String = _djMode.value,
        roomPrivacy: String = _roomPrivacy.value
    ): String = runBlocking {
        createRoomSession(maxListeners, djMode, roomPrivacy).getOrDefault("DEMO01")
    }

    fun setRoomMaxListeners(limit: Int) {
        _maxListeners.value = limit.coerceIn(2, 20)
    }

    fun setRoomDjMode(mode: String) {
        _djMode.value = mode
    }

    fun setRoomPrivacy(privacy: String) {
        _roomPrivacy.value = privacy
    }

    fun setJoinViaLinkBehavior(behavior: String) {
        _joinViaLinkBehavior.value = behavior
    }

    suspend fun joinRoomSession(roomId: String): Result<Unit> {
        val cleanRoomId = roomId.trim().uppercase()
        val result = if (listenApiClient != null) {
            listenApiClient.joinSession(cleanRoomId)
        } else {
            JoinSessionResult.Success(com.mue.core.network.JoinSessionResponse(joinCode = cleanRoomId, sessionId = "test-uuid"))
        }

        return when (result) {
            is JoinSessionResult.Success -> {
                _currentRoomId.value = cleanRoomId
                _isHost.value = false
                _syncStatus.value = "syncing"
                _participantsCount.value = 2

                ensureSocketConnected()
                socketManager.emitListenTogetherJoin(cleanRoomId)
                startSyncHeartbeat(isHost = false)
                Result.success(Unit)
            }
            is JoinSessionResult.NotFound -> Result.failure(Exception("Room not found or ended"))
            is JoinSessionResult.SessionFull -> Result.failure(Exception("Session is full (max 20 participants)"))
            is JoinSessionResult.Error -> Result.failure(Exception(result.message))
        }
    }

    fun joinRoom(roomId: String) {
        scope.launch {
            joinRoomSession(roomId)
        }
    }

    fun leaveRoom() {
        val room = _currentRoomId.value
        if (room != null) {
            socketManager.emitListenTogetherLeave(room)
        }
        heartbeatJob?.cancel()
        heartbeatJob = null
        pingJob?.cancel()
        pingJob = null
        _clockOffsetMs.value = 0L
        _currentDriftMs.value = 0L
        _currentRoomId.value = null
        _isHost.value = false
        _syncStatus.value = "idle"
        _participantsCount.value = 1
    }

    fun endRoom() {
        val room = _currentRoomId.value
        if (room != null && _isHost.value) {
            socketManager.emitListenTogetherEnd(room)
        }
        leaveRoom()
    }

    fun getCurrentRoomId(): String? = _currentRoomId.value

    fun startSyncHeartbeat(isHost: Boolean) {
        heartbeatJob?.cancel()
        pingJob?.cancel()

        if (isHost) {
            heartbeatJob = scope.launch {
                while (isActive) {
                    delay(2000)
                    val room = _currentRoomId.value ?: break
                    val state = playerController.state.value
                    val trackId = state.currentTrack?.id
                    if (trackId != null) {
                        socketManager.emitListenTogetherSync(
                            roomId = room,
                            trackId = trackId,
                            positionMs = state.positionMs,
                            isPlaying = state.isPlaying
                        )
                    }
                }
            }
        } else {
            // Guest periodic NTP sync ping
            pingJob = scope.launch {
                while (isActive) {
                    val room = _currentRoomId.value ?: break
                    socketManager.emitSyncPing(System.currentTimeMillis(), room)
                    delay(5000)
                }
            }
        }
    }

    fun syncPlayback() {
        _currentRoomId.value?.let { roomId ->
            if (_isHost.value) {
                val state = playerController.state.value
                val trackId = state.currentTrack?.id ?: return
                socketManager.emitListenTogetherSync(roomId, trackId, state.positionMs, state.isPlaying)
            } else {
                socketManager.emitSyncPing(System.currentTimeMillis(), roomId)
                _syncStatus.value = "synced"
            }
        }
    }
}
