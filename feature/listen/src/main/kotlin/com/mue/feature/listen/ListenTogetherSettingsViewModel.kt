package com.mue.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.player.ListenTogetherService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListenTogetherSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val listenTogetherService: ListenTogetherService
) : ViewModel() {

    val activeRoom: StateFlow<String?> = listenTogetherService.currentRoomFlow
    val isHost: StateFlow<Boolean> = listenTogetherService.isHost

    val roomMaxListeners: StateFlow<Int> = listenTogetherService.maxListenersFlow
    val roomDjMode: StateFlow<String> = listenTogetherService.djModeFlow
    val roomPrivacy: StateFlow<String> = listenTogetherService.roomPrivacyFlow

    val highSyncQuality: StateFlow<Boolean> = settingsDataStore.listenSyncQualityHighFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val allowGuestsControl: StateFlow<Boolean> = settingsDataStore.allowGuestsControlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultMaxListeners: StateFlow<Int> = settingsDataStore.defaultMaxListenersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val defaultDjMode: StateFlow<String> = settingsDataStore.defaultDjModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DJ")

    val defaultRoomPrivacy: StateFlow<String> = settingsDataStore.defaultRoomPrivacyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Invite Link")

    val ltJoinViaLinkBehavior: StateFlow<String> = settingsDataStore.ltJoinViaLinkBehaviorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ask first")

    fun setRoomMaxListeners(limit: Int) {
        listenTogetherService.setRoomMaxListeners(limit)
    }

    fun setRoomDjMode(mode: String) {
        listenTogetherService.setRoomDjMode(mode)
    }

    fun setRoomPrivacy(privacy: String) {
        listenTogetherService.setRoomPrivacy(privacy)
    }

    fun setHighSyncQuality(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setListenSyncQualityHigh(enabled)
        }
    }

    fun setAllowGuestsControl(allowed: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAllowGuestsControl(allowed)
        }
    }

    fun setDefaultMaxListeners(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setDefaultMaxListeners(limit)
        }
    }

    fun setDefaultDjMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setDefaultDjMode(mode)
        }
    }

    fun setDefaultRoomPrivacy(privacy: String) {
        viewModelScope.launch {
            settingsDataStore.setDefaultRoomPrivacy(privacy)
        }
    }

    fun setLtJoinViaLinkBehavior(behavior: String) {
        viewModelScope.launch {
            settingsDataStore.setLtJoinViaLinkBehavior(behavior)
        }
    }

    fun endSession() {
        listenTogetherService.endRoom()
    }

    fun leaveRoom() {
        listenTogetherService.leaveRoom()
    }

    fun leaveCurrentRoom() {
        listenTogetherService.leaveRoom()
    }

    fun clearRoomHistory() {
        // No-op for privacy
    }
}
