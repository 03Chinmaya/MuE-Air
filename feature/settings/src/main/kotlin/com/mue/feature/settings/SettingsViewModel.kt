package com.mue.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mue.core.auth.AuthRepository
import com.mue.core.common.storage.AudioCacheManager
import com.mue.core.database.DatabaseWiper
import com.mue.core.datastore.SettingsDataStore

import com.mue.core.player.DownloadManager
import com.mue.core.player.MediaCacheManager
import com.mue.core.network.UpdateChecker
import com.mue.core.network.UpdateInfo
import com.mue.feature.settings.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class SettingItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val isOnlineOnly: Boolean = false,
    val section: String
)

data class LinkedDeviceItem(
    val id: String,
    val name: String,
    val lastActive: String,
    val isCurrent: Boolean
)

typealias SettingSection = SettingItem

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val audioCacheManager: AudioCacheManager,
    private val downloadManager: DownloadManager,
    private val authRepository: AuthRepository,
    private val databaseWiper: DatabaseWiper,
    private val mediaCacheManager: MediaCacheManager,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    sealed interface UpdateCheckState {
        object Idle : UpdateCheckState
        object Checking : UpdateCheckState
        data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckState
        object UpToDate : UpdateCheckState
        data class Error(val message: String) : UpdateCheckState
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _serverVersion = MutableStateFlow("1.0.0")
    val serverVersion: StateFlow<String> = _serverVersion.asStateFlow()

    private val _releaseChannel = MutableStateFlow("Stable")
    val releaseChannel: StateFlow<String> = _releaseChannel.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<String>>(emptyList())
    val blockedUsers: StateFlow<List<String>> = _blockedUsers.asStateFlow()

    private val _linkedDevices = MutableStateFlow<List<LinkedDeviceItem>>(emptyList())
    val linkedDevices: StateFlow<List<LinkedDeviceItem>> = _linkedDevices.asStateFlow()

    init {
        fetchServerVersion()
        fetchLinkedDevices()
        fetchBlockedUsers()
    }

    fun fetchServerVersion() {
        viewModelScope.launch {
            _serverVersion.value = updateChecker.fetchServerVersion()
        }
    }

    fun checkForUpdates(currentVersion: String = BuildConfig.VERSION_NAME) {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            try {
                val info = updateChecker.checkForUpdates(currentVersion = currentVersion, force = true)
                if (info != null && info.downloadUrl.isNotBlank()) {
                    _updateCheckState.value = UpdateCheckState.UpdateAvailable(info)
                } else {
                    _updateCheckState.value = UpdateCheckState.UpToDate
                }
            } catch (e: Exception) {
                _updateCheckState.value = UpdateCheckState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateCheckState.value = UpdateCheckState.Idle
    }

    fun setReleaseChannel(channel: String) {
        if (channel == "Stable") {
            _releaseChannel.value = channel
        }
    }


    val isGuest: StateFlow<Boolean> = settingsDataStore.isGuestFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userId: StateFlow<String?> = settingsDataStore.userIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val guestId: StateFlow<String?> = settingsDataStore.guestIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settingsSyncEnabled: StateFlow<Boolean> = settingsDataStore.settingsSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setSettingsSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSettingsSyncEnabled(enabled)
        }
    }


    val displayName: StateFlow<String> = settingsDataStore.displayNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MuE Listener")

    val userBio: StateFlow<String> = settingsDataStore.userBioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Music enthusiast & MuE listener")

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            settingsDataStore.setDisplayName(name)
        }
    }

    fun updateUserBio(bio: String) {
        viewModelScope.launch {
            settingsDataStore.setUserBio(bio)
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            databaseWiper.wipeAllTables()
            authRepository.logout()
            withContext(Dispatchers.IO) {
                audioCacheManager.clearAll()
                downloadManager.clearAll()
            }
            onComplete()
        }
    }

    fun clearLocalData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            databaseWiper.wipeAllTables()
            withContext(Dispatchers.IO) {
                audioCacheManager.clearAll()
                downloadManager.clearAll()
            }
            refreshCacheSize()
            onComplete()
        }
    }

    fun resetSettings(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            settingsDataStore.resetSettings()
            onComplete()
        }
    }

    fun deleteAccount(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.deleteAccount()
            withContext(Dispatchers.IO) {
                audioCacheManager.clearAll()
                downloadManager.clearAll()
            }
            onComplete()
        }
    }

    fun restoreIdentity(mnemonic: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.restoreIdentity(mnemonic)
            onResult(result)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val allSettings: List<SettingItem> = listOf(
        SettingItem(
            title = "Playback",
            subtitle = "Streaming quality, crossfade, audio focus",
            icon = Icons.Default.Tune,
            route = "settings/playback",
            section = "AUDIO & PLAYBACK"
        ),
        SettingItem(
            title = "Storage & Downloads",
            subtitle = "Cache limit, offline audio, clear cache",
            icon = Icons.Default.Storage,
            route = "settings/storage",
            section = "STORAGE & CACHE"
        ),
        SettingItem(
            title = "Privacy",
            subtitle = "Listening activity, blocked users",
            icon = Icons.Default.PrivacyTip,
            route = "settings/privacy",
            section = "PRIVACY & SECURITY"
        ),
        SettingItem(
            title = "Linked Devices",
            subtitle = "Active sessions, device handoff",
            icon = Icons.Default.Devices,
            route = "settings/devices",
            isOnlineOnly = true,
            section = "PRIVACY & SECURITY"
        ),
        SettingItem(
            title = "Notifications",
            subtitle = "Push alerts, in-app badges, DND",
            icon = Icons.Default.Notifications,
            route = "settings/notifications",
            section = "SYSTEM & PREFERENCES"
        ),
        SettingItem(
            title = "Appearance & Accessibility",
            subtitle = "Theme, font scale, liquid glass",
            icon = Icons.Default.Palette,
            route = "settings/appearance",
            section = "SYSTEM & PREFERENCES"
        ),
        SettingItem(
            title = "About & Updates",
            subtitle = "App version 1.0, release notes",
            icon = Icons.Default.Info,
            route = "settings/about",
            section = "SYSTEM & PREFERENCES"
        ),
        SettingItem(
            title = "Danger Zone",
            subtitle = "Reset settings, clear local data",
            icon = Icons.Default.Warning,
            route = "settings/danger",
            section = "DANGER ZONE"
        )
    )

    val filteredItems: StateFlow<List<SettingItem>> = _searchQuery.map { query ->
        if (query.isBlank()) {
            allSettings
        } else {
            allSettings.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true) ||
                it.section.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), allSettings)

    val filteredSections: StateFlow<List<SettingItem>> = filteredItems

    val highPerformance: StateFlow<Boolean> = settingsDataStore.highPerformanceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncedLyrics: StateFlow<Boolean> = settingsDataStore.syncedLyricsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadWifiOnly: StateFlow<Boolean> = settingsDataStore.downloadWifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val preferMusicOnly: StateFlow<Boolean> = settingsDataStore.preferMusicOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loudnessNorm: StateFlow<Boolean> = settingsDataStore.loudnessNormFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val crossfade: StateFlow<Boolean> = settingsDataStore.crossfadeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val automix: StateFlow<Boolean> = settingsDataStore.automixFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val skipSilence: StateFlow<Boolean> = settingsDataStore.skipSilenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val spatialAudio: StateFlow<Boolean> = settingsDataStore.spatialAudioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val losslessAudio: StateFlow<Boolean> = settingsDataStore.losslessAudioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reduceAnimation: StateFlow<Boolean> = settingsDataStore.reduceAnimationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reduceDynamicBlur: StateFlow<Boolean> = settingsDataStore.reduceDynamicBlurFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liquidGlass: StateFlow<Boolean> = settingsDataStore.liquidGlassFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allowGuestsControl: StateFlow<Boolean> = settingsDataStore.allowGuestsControlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val listenSyncQualityHigh: StateFlow<Boolean> = settingsDataStore.listenSyncQualityHighFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val cacheLimitMb: StateFlow<Int> = settingsDataStore.cacheLimitMbFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 512)

    val streamingQuality: StateFlow<String> = settingsDataStore.streamingQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Auto")

    val downloadQuality: StateFlow<String> = settingsDataStore.downloadQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "320 kbps")

    val crossfadeSeconds: StateFlow<Int> = settingsDataStore.crossfadeSecondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val audioFocusBehavior: StateFlow<String> = settingsDataStore.audioFocusBehaviorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "duck")

    val bluetoothAvrcp: StateFlow<Boolean> = settingsDataStore.bluetoothAvrcpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val activityVisibility: StateFlow<String> = settingsDataStore.activityVisibilityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Public")

    val readReceiptsEnabled: StateFlow<Boolean> = settingsDataStore.readReceiptsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hideReadMarks: StateFlow<Boolean> = settingsDataStore.hideReadMarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setHideReadMarks(hide: Boolean) {
        viewModelScope.launch { settingsDataStore.setHideReadMarks(hide) }
    }

    val appLanguage: StateFlow<String> = settingsDataStore.appLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System Default")

    fun setAppLanguage(language: String) {
        viewModelScope.launch { settingsDataStore.setAppLanguage(language) }
    }

    val chatMediaAutoDownload: StateFlow<Boolean> = settingsDataStore.chatMediaAutoDownloadFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setChatMediaAutoDownload(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setChatMediaAutoDownload(enabled) }
    }

    val typingIndicators: StateFlow<Boolean> = settingsDataStore.typingIndicatorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setTypingIndicators(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setTypingIndicators(enabled) }
    }

    val messageRequestApproval: StateFlow<Boolean> = settingsDataStore.messageRequestApprovalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setMessageRequestApproval(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMessageRequestApproval(enabled) }
    }

    val advancedChatPrivacy: StateFlow<Boolean> = settingsDataStore.advancedChatPrivacyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAdvancedChatPrivacy(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdvancedChatPrivacy(enabled) }
    }

    val chatWallpaper: StateFlow<String> = settingsDataStore.chatWallpaperFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default OLED")

    fun setChatWallpaper(wallpaper: String) {
        viewModelScope.launch { settingsDataStore.setChatWallpaper(wallpaper) }
    }

    val pushNotifications: StateFlow<Boolean> = settingsDataStore.pushNotificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val inAppBadges: StateFlow<Boolean> = settingsDataStore.inAppBadgesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dndQuietHours: StateFlow<Boolean> = settingsDataStore.dndQuietHoursFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val deviceHandoff: StateFlow<Boolean> = settingsDataStore.deviceHandoffFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val theme: StateFlow<String> = settingsDataStore.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Pure Black OLED")

    val fontSize: StateFlow<String> = settingsDataStore.fontSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Medium")

    val ltEphemeralChat: StateFlow<Boolean> = settingsDataStore.ltEphemeralChatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setLtEphemeralChat(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLtEphemeralChat(enabled) }
    }

    val ltLiveReactions: StateFlow<Boolean> = settingsDataStore.ltLiveReactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setLtLiveReactions(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLtLiveReactions(enabled) }
    }

    val ltJoinViaLinkBehavior: StateFlow<String> = settingsDataStore.ltJoinViaLinkBehaviorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ask first")

    fun setLtJoinViaLinkBehavior(behavior: String) {
        viewModelScope.launch { settingsDataStore.setLtJoinViaLinkBehavior(behavior) }
    }

    fun fetchBlockedUsers() {
        _blockedUsers.value = emptyList()
    }

    fun unblockUser(userId: String) {
        _blockedUsers.value = _blockedUsers.value.filter { it != userId }
    }

    fun fetchLinkedDevices() {
        viewModelScope.launch {
            try {
                val currentDeviceId = settingsDataStore.deviceIdFlow.firstOrNull()
                val dtos = authRepository.getLinkedDevices()
                val items = if (dtos.isNotEmpty()) {
                    dtos.map { dto ->
                        val isCurrent = dto.id == currentDeviceId ||
                            (dto.deviceName?.contains(android.os.Build.MODEL, ignoreCase = true) == true)
                        val lastActive = if (isCurrent) "Active now" else {
                            dto.lastSeenAt?.take(10) ?: "Recently active"
                        }
                        LinkedDeviceItem(
                            id = dto.id,
                            name = dto.deviceName ?: "Unknown Device (${dto.platform ?: "Mobile"})",
                            lastActive = lastActive,
                            isCurrent = isCurrent
                        )
                    }
                } else {
                    listOf(
                        LinkedDeviceItem(
                            id = currentDeviceId ?: "curr-dev",
                            name = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                            lastActive = "Active now",
                            isCurrent = true
                        )
                    )
                }
                _linkedDevices.value = items
            } catch (_: Exception) {
                if (_linkedDevices.value.isEmpty()) {
                    _linkedDevices.value = listOf(
                        LinkedDeviceItem(
                            id = "curr-dev",
                            name = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                            lastActive = "Active now",
                            isCurrent = true
                        )
                    )
                }
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                authRepository.revokeDevice(deviceId)
            } catch (_: Exception) {}
            _linkedDevices.value = _linkedDevices.value.filter { it.id != deviceId }
        }
    }

    private val _cacheSizeFormatted = MutableStateFlow("0 MB")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _audioCacheSizeFormatted = MutableStateFlow("0 MB")
    val audioCacheSizeFormatted: StateFlow<String> = _audioCacheSizeFormatted.asStateFlow()

    private val _downloadsSizeFormatted = MutableStateFlow("0 MB")
    val downloadsSizeFormatted: StateFlow<String> = _downloadsSizeFormatted.asStateFlow()

    private val _audioCacheBytes = MutableStateFlow(0L)
    val audioCacheBytes: StateFlow<Long> = _audioCacheBytes.asStateFlow()

    private val _downloadsBytes = MutableStateFlow(0L)
    val downloadsBytes: StateFlow<Long> = _downloadsBytes.asStateFlow()

    private val _spotifyConnected = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _spotifyUserId = MutableStateFlow<String?>(null)
    val spotifyUserId: StateFlow<String?> = _spotifyUserId.asStateFlow()

    private val _spotifyLoading = MutableStateFlow(false)
    val spotifyLoading: StateFlow<Boolean> = _spotifyLoading.asStateFlow()

    init {
        refreshCacheSize()
        refreshSpotifyStatus()
    }

    fun refreshSpotifyStatus() {
        _spotifyConnected.value = false
        _spotifyUserId.value = null
        _spotifyLoading.value = false
    }

    fun connectSpotify(onUrlReceived: (String) -> Unit) {
        _spotifyLoading.value = false
    }

    fun disconnectSpotify() {
        _spotifyConnected.value = false
        _spotifyUserId.value = null
        _spotifyLoading.value = false
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val cacheBytes = withContext(Dispatchers.IO) { audioCacheManager.getSize() }
            val dlBytes = withContext(Dispatchers.IO) { downloadManager.getCacheSpace() }
            _audioCacheBytes.value = cacheBytes
            _downloadsBytes.value = dlBytes
            _audioCacheSizeFormatted.value = formatBytes(cacheBytes)
            _downloadsSizeFormatted.value = formatBytes(dlBytes)
            _cacheSizeFormatted.value = formatBytes(cacheBytes + dlBytes)
        }
    }

    fun setCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            settingsDataStore.setCacheLimitMb(limitMb)
            val bytes = limitMb * 1024L * 1024L
            audioCacheManager.setMaxSize(bytes)
            mediaCacheManager.updateCacheLimit(bytes)
        }
    }

    fun clearAudioCache(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                audioCacheManager.clearAll()
            }
            refreshCacheSize()
            onComplete()
        }
    }

    fun clearDownloads(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloadManager.clearAll()
            }
            refreshCacheSize()
            onComplete()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                audioCacheManager.clearAll()
                downloadManager.clearAll()
            }
            refreshCacheSize()
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    fun setHighPerformance(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setHighPerformance(enabled)
        }
    }

    fun setSyncedLyrics(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSyncedLyrics(enabled)
        }
    }

    fun setDownloadWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDownloadWifiOnly(enabled)
        }
    }

    fun setPreferMusicOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPreferMusicOnly(enabled)
        }
    }

    fun setLoudnessNorm(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLoudnessNorm(enabled)
        }
    }

    fun setCrossfade(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setCrossfade(enabled)
        }
    }

    fun setAutomix(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutomix(enabled)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSkipSilence(enabled)
        }
    }

    fun setSpatialAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSpatialAudio(enabled)
        }
    }

    fun setLosslessAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLosslessAudio(enabled)
        }
    }

    fun setReduceAnimation(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setReduceAnimation(enabled)
        }
    }

    fun setReduceDynamicBlur(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setReduceDynamicBlur(enabled)
        }
    }

    fun setLiquidGlass(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLiquidGlass(enabled)
        }
    }

    fun setAllowGuestsControl(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAllowGuestsControl(enabled)
        }
    }

    fun setListenSyncQualityHigh(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setListenSyncQualityHigh(enabled)
        }
    }

    fun setStreamingQuality(quality: String) {
        viewModelScope.launch { settingsDataStore.setStreamingQuality(quality) }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { settingsDataStore.setDownloadQuality(quality) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settingsDataStore.setCrossfadeSeconds(seconds) }
    }

    fun setAudioFocusBehavior(behavior: String) {
        viewModelScope.launch { settingsDataStore.setAudioFocusBehavior(behavior) }
    }

    fun setBluetoothAvrcp(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBluetoothAvrcp(enabled) }
    }

    fun setActivityVisibility(visibility: String) {
        viewModelScope.launch { settingsDataStore.setActivityVisibility(visibility) }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setReadReceiptsEnabled(enabled) }
    }

    fun setPushNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPushNotifications(enabled) }
    }

    fun setInAppBadges(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setInAppBadges(enabled) }
    }

    fun setDndQuietHours(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDndQuietHours(enabled) }
    }

    fun setDeviceHandoff(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDeviceHandoff(enabled) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settingsDataStore.setTheme(theme) }
    }

    fun setFontSize(size: String) {
        viewModelScope.launch { settingsDataStore.setFontSize(size) }
    }
}
