package com.mue.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {
    val data = context.dataStore

    val preferencesFlow: Flow<androidx.datastore.preferences.core.Preferences> = context.dataStore.data

    suspend fun getPreferences(): androidx.datastore.preferences.core.Preferences =
        context.dataStore.data.first()

    suspend fun edit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    val highPerformanceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIGH_PERFORMANCE] ?: true
    }

    val losslessAudioFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOSSLESS_AUDIO] ?: false
    }

    val syncedLyricsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SYNCED_LYRICS] ?: true
    }

    val downloadWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_WIFI_ONLY] ?: true
    }

    val preferMusicOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREFER_MUSIC_ONLY] ?: true
    }

    val loudnessNormFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOUDNESS_NORM] ?: false
    }

    val crossfadeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CROSSFADE] ?: false
    }

    val crossfadeSecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CROSSFADE_SECONDS] ?: 0
    }

    val automixFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTOMIX] ?: false
    }

    val skipSilenceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SKIP_SILENCE] ?: false
    }

    val spatialAudioFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SPATIAL_AUDIO] ?: false
    }

    val reduceAnimationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[REDUCE_ANIMATION] ?: false
    }

    val reduceDynamicBlurFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[REDUCE_DYNAMIC_BLUR] ?: false
    }

    val liquidGlassFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LIQUID_GLASS] ?: false
    }

    val allowGuestsControlFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ALLOW_GUESTS_CONTROL] ?: false
    }

    val listenSyncQualityHighFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LISTEN_SYNC_QUALITY_HIGH] ?: true
    }

    val disappearingTtlMsFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[DISAPPEARING_TTL_MS] ?: 0L
    }

    val mediaTtl24hFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MEDIA_TTL_24H] ?: true
    }

    val readReceiptsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READ_RECEIPTS_ENABLED] ?: true
    }

    val streamingQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[STREAMING_QUALITY] ?: "Auto"
    }

    val downloadQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_QUALITY] ?: "320 kbps"
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_QUALITY] = quality }
    }

    val audioFocusBehaviorFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AUDIO_FOCUS_BEHAVIOR] ?: "duck"
    }

    val bluetoothAvrcpFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BLUETOOTH_AVRCP] ?: true
    }

    val avrcpEnabledFlow: Flow<Boolean> = bluetoothAvrcpFlow

    val activityVisibilityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVITY_VISIBILITY] ?: "Public"
    }

    val pushNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PUSH_NOTIFICATIONS] ?: true
    }

    val inAppBadgesFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IN_APP_BADGES] ?: true
    }

    val dndQuietHoursFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DND_QUIET_HOURS] ?: false
    }

    val deviceHandoffFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_HANDOFF] ?: true
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME] ?: "Pure Black OLED"
    }

    val fontSizeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: "Medium"
    }

    val hideReadMarksFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_READ_MARKS] ?: false
    }

    suspend fun setHideReadMarks(hide: Boolean) {
        context.dataStore.edit { prefs -> prefs[HIDE_READ_MARKS] = hide }
    }

    val appLanguageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "System Default"
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[APP_LANGUAGE] = language }
    }

    suspend fun setStreamingQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[STREAMING_QUALITY] = quality }
    }

    suspend fun setAudioFocusBehavior(behavior: String) {
        context.dataStore.edit { prefs -> prefs[AUDIO_FOCUS_BEHAVIOR] = behavior }
    }

    suspend fun setBluetoothAvrcp(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[BLUETOOTH_AVRCP] = enabled }
    }

    suspend fun setActivityVisibility(visibility: String) {
        context.dataStore.edit { prefs -> prefs[ACTIVITY_VISIBILITY] = visibility }
    }

    suspend fun setPushNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PUSH_NOTIFICATIONS] = enabled }
    }

    suspend fun setInAppBadges(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[IN_APP_BADGES] = enabled }
    }

    suspend fun setDndQuietHours(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DND_QUIET_HOURS] = enabled }
    }

    suspend fun setDeviceHandoff(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DEVICE_HANDOFF] = enabled }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[THEME] = theme }
    }

    suspend fun setFontSize(size: String) {
        context.dataStore.edit { prefs -> prefs[FONT_SIZE] = size }
    }

    val cacheLimitMbFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CACHE_LIMIT_MB] ?: 512
    }

    val settingsSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETTINGS_SYNC_ENABLED] ?: true
    }

    suspend fun setSettingsSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SETTINGS_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { prefs ->
            prefs[CACHE_LIMIT_MB] = limitMb
        }
    }

    suspend fun setHighPerformance(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIGH_PERFORMANCE] = enabled
        }
    }

    suspend fun setSyncedLyrics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SYNCED_LYRICS] = enabled
        }
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_WIFI_ONLY] = enabled
        }
    }

    suspend fun setPreferMusicOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PREFER_MUSIC_ONLY] = enabled
        }
    }

    suspend fun setLoudnessNorm(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOUDNESS_NORM] = enabled
        }
    }

    suspend fun setCrossfade(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CROSSFADE] = enabled
        }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[CROSSFADE_SECONDS] = seconds
        }
    }

    suspend fun setAutomix(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTOMIX] = enabled
        }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SKIP_SILENCE] = enabled
        }
    }

    suspend fun setSpatialAudio(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SPATIAL_AUDIO] = enabled
        }
    }

    suspend fun setReduceAnimation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REDUCE_ANIMATION] = enabled
        }
    }

    suspend fun setReduceDynamicBlur(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REDUCE_DYNAMIC_BLUR] = enabled
        }
    }

    suspend fun setLiquidGlass(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LIQUID_GLASS] = enabled
        }
    }

    suspend fun setAllowGuestsControl(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ALLOW_GUESTS_CONTROL] = enabled
        }
    }

    suspend fun setLosslessAudio(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOSSLESS_AUDIO] = enabled
        }
    }

    suspend fun setListenSyncQualityHigh(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LISTEN_SYNC_QUALITY_HIGH] = enabled
        }
    }

    suspend fun setDisappearingTtlMs(ttl: Long) {
        context.dataStore.edit { prefs ->
            prefs[DISAPPEARING_TTL_MS] = ttl
        }
    }

    suspend fun setMediaTtl24h(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[MEDIA_TTL_24H] = enabled
        }
    }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READ_RECEIPTS_ENABLED] = enabled
        }
    }

    val enterToSendFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ENTER_TO_SEND] ?: false
    }

    suspend fun setEnterToSend(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ENTER_TO_SEND] = enabled
        }
    }

    val chatFontSizeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CHAT_FONT_SIZE] ?: "M"
    }

    suspend fun setChatFontSize(size: String) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_FONT_SIZE] = size
        }
    }

    val chatBiometricLockFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_BIOMETRIC_LOCK] ?: false
    }

    suspend fun setChatBiometricLock(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_BIOMETRIC_LOCK] = enabled
        }
    }

    val chatGroupLimitFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CHAT_GROUP_LIMIT] ?: 20
    }

    suspend fun setChatGroupLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_GROUP_LIMIT] = limit.coerceIn(2, 69)
        }
    }

    val chatMediaAutoDownloadFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_MEDIA_AUTO_DOWNLOAD] ?: true
    }

    suspend fun setChatMediaAutoDownload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_MEDIA_AUTO_DOWNLOAD] = enabled
        }
    }

    val typingIndicatorsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_TYPING_INDICATORS] ?: true
    }

    suspend fun setTypingIndicators(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_TYPING_INDICATORS] = enabled
        }
    }

    val messageRequestApprovalFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_MESSAGE_REQUEST_APPROVAL] ?: true
    }

    suspend fun setMessageRequestApproval(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_MESSAGE_REQUEST_APPROVAL] = enabled
        }
    }

    val advancedChatPrivacyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_ADVANCED_PRIVACY] ?: false
    }

    suspend fun setAdvancedChatPrivacy(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_ADVANCED_PRIVACY] = enabled
        }
    }

    val chatWallpaperFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CHAT_WALLPAPER] ?: "Default OLED"
    }

    suspend fun setChatWallpaper(wallpaper: String) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_WALLPAPER] = wallpaper
        }
    }

    val defaultMaxListenersFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_MAX_LISTENERS] ?: 10
    }

    suspend fun setDefaultMaxListeners(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_MAX_LISTENERS] = limit.coerceIn(2, 20)
        }
    }

    val defaultDjModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_DJ_MODE] ?: "DJ"
    }

    suspend fun setDefaultDjMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_DJ_MODE] = mode
        }
    }

    val defaultRoomPrivacyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_ROOM_PRIVACY] ?: "Public"
    }

    suspend fun setDefaultRoomPrivacy(privacy: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_ROOM_PRIVACY] = privacy
        }
    }

    val ltEphemeralChatFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LT_EPHEMERAL_CHAT] ?: true
    }

    suspend fun setLtEphemeralChat(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LT_EPHEMERAL_CHAT] = enabled
        }
    }

    val ltLiveReactionsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LT_LIVE_REACTIONS] ?: true
    }

    suspend fun setLtLiveReactions(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LT_LIVE_REACTIONS] = enabled
        }
    }

    val ltJoinViaLinkBehaviorFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LT_JOIN_VIA_LINK_BEHAVIOR] ?: "Ask first"
    }

    suspend fun setLtJoinViaLinkBehavior(behavior: String) {
        context.dataStore.edit { prefs ->
            prefs[LT_JOIN_VIA_LINK_BEHAVIOR] = behavior
        }
    }

    val librarySortOrderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LIBRARY_SORT_ORDER] ?: "Recently Added"
    }

    suspend fun setLibrarySortOrder(order: String) {
        context.dataStore.edit { prefs ->
            prefs[LIBRARY_SORT_ORDER] = order
        }
    }

    val libraryViewModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LIBRARY_VIEW_MODE] ?: "grid"
    }

    suspend fun setLibraryViewMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[LIBRARY_VIEW_MODE] = mode
        }
    }


    val firstInstallTimeFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[FIRST_INSTALL_TIME]
    }

    val lastHomeRefreshTimeFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_HOME_REFRESH_TIME]
    }

    suspend fun setFirstInstallTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[FIRST_INSTALL_TIME] = time
        }
    }

    suspend fun setLastHomeRefreshTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_HOME_REFRESH_TIME] = time
        }
    }

    val guestIdFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_GUEST_ID] }
    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_DEVICE_ID] }
    val deviceFingerprintFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_DEVICE_FINGERPRINT] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_USER_ID] }
    val isGuestFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_IS_GUEST] ?: true }
    val syncCursorFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_SYNC_CURSOR] }

    suspend fun getSyncCursor(): String? = context.dataStore.data.first()[KEY_SYNC_CURSOR]

    suspend fun setSyncCursor(cursor: String?) {
        context.dataStore.edit { prefs ->
            if (cursor == null) prefs.remove(KEY_SYNC_CURSOR) else prefs[KEY_SYNC_CURSOR] = cursor
        }
    }

    suspend fun setGuestId(guestId: String?) {
        context.dataStore.edit { prefs ->
            if (guestId == null) prefs.remove(KEY_GUEST_ID) else prefs[KEY_GUEST_ID] = guestId
        }
    }

    suspend fun setDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
        }
    }

    suspend fun setDeviceFingerprint(fingerprint: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_FINGERPRINT] = fingerprint
        }
    }

    suspend fun setUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) prefs.remove(KEY_USER_ID) else prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun setIsGuest(isGuest: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_GUEST] = isGuest
        }
    }

    val lastPlayedTrackJsonFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[LAST_PLAYED_TRACK_JSON] }
    val lastQueueJsonFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[LAST_QUEUE_JSON] }
    val lastPositionMsFlow: Flow<Long?> = context.dataStore.data.map { prefs -> prefs[LAST_POSITION_MS] }

    suspend fun getLastPlaybackState(): Triple<String?, String?, Long?> {
        val prefs = context.dataStore.data.first()
        return Triple(prefs[LAST_PLAYED_TRACK_JSON], prefs[LAST_QUEUE_JSON], prefs[LAST_POSITION_MS])
    }

    suspend fun savePlaybackState(trackJson: String?, queueJson: String?, positionMs: Long) {
        context.dataStore.edit { prefs ->
            if (trackJson != null) {
                prefs[LAST_PLAYED_TRACK_JSON] = trackJson
            } else {
                prefs.remove(LAST_PLAYED_TRACK_JSON)
            }
            if (queueJson != null) {
                prefs[LAST_QUEUE_JSON] = queueJson
            } else {
                prefs.remove(LAST_QUEUE_JSON)
            }
            prefs[LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun setLastPlayedTrackJson(trackJson: String?) {
        context.dataStore.edit { prefs ->
            if (trackJson != null) prefs[LAST_PLAYED_TRACK_JSON] = trackJson else prefs.remove(LAST_PLAYED_TRACK_JSON)
        }
    }

    suspend fun setLastQueueJson(queueJson: String?) {
        context.dataStore.edit { prefs ->
            if (queueJson != null) prefs[LAST_QUEUE_JSON] = queueJson else prefs.remove(LAST_QUEUE_JSON)
        }
    }

    suspend fun setLastPositionMs(pos: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_POSITION_MS] = pos
        }
    }

    suspend fun resetSettings() {
        context.dataStore.edit { prefs ->
            val devId = prefs[KEY_DEVICE_ID]
            val fp = prefs[KEY_DEVICE_FINGERPRINT]
            val guestId = prefs[KEY_GUEST_ID]
            val userId = prefs[KEY_USER_ID]
            val isGuest = prefs[KEY_IS_GUEST]
            prefs.clear()
            if (devId != null) prefs[KEY_DEVICE_ID] = devId
            if (fp != null) prefs[KEY_DEVICE_FINGERPRINT] = fp
            if (guestId != null) prefs[KEY_GUEST_ID] = guestId
            if (userId != null) prefs[KEY_USER_ID] = userId
            prefs[KEY_IS_GUEST] = isGuest ?: true
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            val devId = prefs[KEY_DEVICE_ID]
            val fp = prefs[KEY_DEVICE_FINGERPRINT]
            prefs.clear()
            if (devId != null) prefs[KEY_DEVICE_ID] = devId
            if (fp != null) prefs[KEY_DEVICE_FINGERPRINT] = fp
            prefs[KEY_IS_GUEST] = true
        }
    }

    suspend fun getOrGenerateDeviceId(): String {
        val prefs = context.dataStore.data.first()
        var devId = prefs[KEY_DEVICE_ID]
        if (devId.isNullOrEmpty()) {
            devId = java.util.UUID.randomUUID().toString()
            context.dataStore.edit { it[KEY_DEVICE_ID] = devId }
        }
        return devId
    }

    suspend fun getOrComputeDeviceFingerprint(): String {
        val prefs = context.dataStore.data.first()
        var fp = prefs[KEY_DEVICE_FINGERPRINT]
        if (fp.isNullOrEmpty()) {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown_android_id"
            val raw = "$androidId:${android.os.Build.FINGERPRINT}"
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            fp = digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            context.dataStore.edit { it[KEY_DEVICE_FINGERPRINT] = fp }
        }
        return fp
    }

    companion object {
        val KEY_GUEST_ID = stringPreferencesKey("guest_id")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_DEVICE_FINGERPRINT = stringPreferencesKey("device_fingerprint")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_IS_GUEST = booleanPreferencesKey("is_guest")
        val KEY_SYNC_CURSOR = stringPreferencesKey("sync_cursor")

        val THEME = stringPreferencesKey("theme")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val AUDIO_FOCUS_BEHAVIOR = stringPreferencesKey("audio_focus_behavior")
        val BLUETOOTH_AVRCP = booleanPreferencesKey("bluetooth_avrcp")
        val ACTIVITY_VISIBILITY = stringPreferencesKey("activity_visibility")
        val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications")
        val IN_APP_BADGES = booleanPreferencesKey("in_app_badges")
        val DND_QUIET_HOURS = booleanPreferencesKey("dnd_quiet_hours")
        val DEVICE_HANDOFF = booleanPreferencesKey("device_handoff")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val HIDE_READ_MARKS = booleanPreferencesKey("hide_read_marks")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LOSSLESS_AUDIO = booleanPreferencesKey("lossless_audio")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val PREFER_MUSIC_ONLY = booleanPreferencesKey("prefer_music_only")
        val LOUDNESS_NORM = booleanPreferencesKey("loudness_norm")
        val CROSSFADE = booleanPreferencesKey("crossfade")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val AUTOMIX = booleanPreferencesKey("automix")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val SPATIAL_AUDIO = booleanPreferencesKey("spatial_audio")
        val REDUCE_ANIMATION = booleanPreferencesKey("reduce_animation")
        val REDUCE_DYNAMIC_BLUR = booleanPreferencesKey("reduce_dynamic_blur")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
        val ALLOW_GUESTS_CONTROL = booleanPreferencesKey("allow_guests_control")
        val LISTEN_SYNC_QUALITY_HIGH = booleanPreferencesKey("listen_sync_quality_high")
        val DISAPPEARING_TTL_MS = longPreferencesKey("disappearing_ttl_ms")
        val MEDIA_TTL_24H = booleanPreferencesKey("media_ttl_24h")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val HIGH_PERFORMANCE = booleanPreferencesKey("high_performance")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val SYNCED_LYRICS = booleanPreferencesKey("synced_lyrics")
        val SETTINGS_SYNC_ENABLED = booleanPreferencesKey("settings_sync_enabled")
        val ENTER_TO_SEND = booleanPreferencesKey("chat_enter_to_send")
        val CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")
        val CHAT_BIOMETRIC_LOCK = booleanPreferencesKey("chat_biometric_lock")
        val CHAT_GROUP_LIMIT = intPreferencesKey("chat_group_limit")
        val CHAT_MEDIA_AUTO_DOWNLOAD = booleanPreferencesKey("chat_media_auto_download")
        val CHAT_TYPING_INDICATORS = booleanPreferencesKey("chat_typing_indicators")
        val CHAT_MESSAGE_REQUEST_APPROVAL = booleanPreferencesKey("chat_message_request_approval")
        val CHAT_ADVANCED_PRIVACY = booleanPreferencesKey("chat_advanced_privacy")
        val CHAT_WALLPAPER = stringPreferencesKey("chat_wallpaper")
        val DEFAULT_MAX_LISTENERS = intPreferencesKey("default_max_listeners")
        val DEFAULT_DJ_MODE = stringPreferencesKey("default_dj_mode")
        val DEFAULT_ROOM_PRIVACY = stringPreferencesKey("default_room_privacy")
        val LT_EPHEMERAL_CHAT = booleanPreferencesKey("lt_ephemeral_chat")
        val LT_LIVE_REACTIONS = booleanPreferencesKey("lt_live_reactions")
        val LT_JOIN_VIA_LINK_BEHAVIOR = stringPreferencesKey("lt_join_via_link_behavior")
        val LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val FIRST_INSTALL_TIME = longPreferencesKey("first_install_time")
        val LAST_HOME_REFRESH_TIME = longPreferencesKey("last_home_refresh_time")
        val LAST_PLAYED_TRACK_JSON = stringPreferencesKey("last_played_track_json")
        val LAST_QUEUE_JSON = stringPreferencesKey("last_queue_json")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    }
}
