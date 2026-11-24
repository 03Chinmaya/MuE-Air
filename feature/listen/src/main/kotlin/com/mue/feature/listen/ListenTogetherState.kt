package com.mue.feature.listen

enum class SyncStatus {
    SYNCED,
    BUFFERING,
    DRIFT,
    RECONNECTING,
    IDLE
}

data class Participant(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isHost: Boolean = false
)

sealed interface UiState {
    data object Lobby : UiState

    data class Connecting(
        val isHost: Boolean,
        val roomCode: String
    ) : UiState

    data class InRoom(
        val isHost: Boolean,
        val roomCode: String,
        val syncStatus: SyncStatus,
        val participants: List<Participant>,
        val currentDriftMs: Long = 0L,
        val isHostDisconnected: Boolean = false,
        val graceSecondsRemaining: Int = 0
    ) : UiState

    data class Error(
        val message: String,
        val reason: String? = null
    ) : UiState
}

