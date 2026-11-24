package com.mue.core.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import com.mue.core.common.storage.AudioCacheManager
import com.mue.core.database.LibraryDao
import com.mue.core.database.PlaybackHistoryEntity
import com.mue.core.database.SongDao
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.PlaybackState
import com.mue.core.model.PlaybackRepeatMode
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.JioSaavnProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioCacheManager: AudioCacheManager,
    private val downloadManager: DownloadManager,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val songDao: SongDao,
    private val jioSaavnProvider: JioSaavnProvider,
    private val libraryDao: LibraryDao,
    private val settingsDataStore: SettingsDataStore,
    private val queueRecommendationEngine: QueueRecommendationEngine,
    private val streamUrlResolver: StreamUrlResolver
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Low-latency configuration: 500ms initial buffer for fast startup, 2500ms min buffer
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(2500, 15000, 500, 1000)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            false // handleAudioFocus = false so manual AudioFocusRequest controls Duck vs Pause behavior
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val shuffleStrategy = QueueShuffleStrategy()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var preferredAudioDevice: AudioDeviceInfo? = null

    private fun classifyAudioDevice(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val name = device.productName?.toString()?.trim()
                if (!name.isNullOrBlank()) name else "Bluetooth"
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "External Audio"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "This phone"
            else -> {
                val name = device.productName?.toString()?.trim()
                if (!name.isNullOrBlank()) name else "This phone"
            }
        }
    }

    private fun detectAudioRoute(): String {
        val am = audioManager ?: return "This phone"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val preferred = preferredAudioDevice
            if (preferred != null) {
                return classifyAudioDevice(preferred)
            }
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // Priority 1: Bluetooth
            devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            }?.let { return classifyAudioDevice(it) }

            // Priority 2: Wired & USB
            devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }?.let { return classifyAudioDevice(it) }

            // Priority 3: External (HDMI, Line)
            devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_HDMI ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL
            }?.let { return classifyAudioDevice(it) }

            // Priority 4: Built-in speaker
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
                return "This phone"
            }
        }
        return "This phone"
    }

    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = audioManager
            val speaker = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            val target = device ?: speaker
            preferredAudioDevice = target
            exoPlayer.setPreferredAudioDevice(target)
            _audioRoute.value = detectAudioRoute()
        }
    }

    private val _audioRoute = MutableStateFlow(detectAudioRoute())
    val audioRoute: StateFlow<String> = _audioRoute.asStateFlow()

    private val _playingFrom = MutableStateFlow("Unknown")
    val playingFrom: StateFlow<String> = _playingFrom.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()
    private var sleepWhenTrackEnds: Boolean = false

    private var activeStreamUrl: String? = null
    private var currentHistoryId: String? = null
    private var currentTrackStartTime: Long = 0L
    private var isResolvingRadio: Boolean = false
    private var hasTriggered80PercentPrefetch: Boolean = false
    private var audioFocusBehavior: String = "duck"
    private var pausedByTransientFocusLoss = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByTransientFocusLoss = false
                exoPlayer.pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (exoPlayer.isPlaying) {
                    pausedByTransientFocusLoss = true
                    exoPlayer.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (audioFocusBehavior == "pause") {
                    if (exoPlayer.isPlaying) {
                        pausedByTransientFocusLoss = true
                        exoPlayer.pause()
                    }
                } else {
                    exoPlayer.volume = 0.2f
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer.volume = 1.0f
                if (pausedByTransientFocusLoss) {
                    pausedByTransientFocusLoss = false
                    exoPlayer.play()
                }
            }
        }
    }

    private val audioFocusRequest: android.media.AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        } else {
            null
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            am.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun buildMediaItem(track: Track, streamUrl: String): MediaItem {
        val mediaUri = if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            Uri.fromFile(File(streamUrl))
        } else {
            Uri.parse(streamUrl)
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                if (!track.artworkUrl.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(track.artworkUrl))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(mediaUri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private fun prefetchLookaheadAudio() {
        val current = _state.value.currentTrack ?: return
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val lookahead = if (currentIndex >= 0) {
            currentQueue.drop(currentIndex + 1).take(3)
        } else {
            emptyList()
        }
        if (lookahead.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            for (nextTrack in lookahead) {
                try {
                    var alreadyAdded = false
                    withContext(Dispatchers.Main) {
                        for (i in 0 until exoPlayer.mediaItemCount) {
                            if (exoPlayer.getMediaItemAt(i).mediaId == nextTrack.id) {
                                alreadyAdded = true
                                break
                            }
                        }
                    }
                    if (alreadyAdded) continue

                    val nextStreamUrl = streamUrlResolver.resolve(nextTrack)
                    if (!nextStreamUrl.isNullOrBlank()) {
                        val nextMediaItem = buildMediaItem(nextTrack, nextStreamUrl)
                        withContext(Dispatchers.Main) {
                            var recheckAdded = false
                            for (i in 0 until exoPlayer.mediaItemCount) {
                                if (exoPlayer.getMediaItemAt(i).mediaId == nextTrack.id) {
                                    recheckAdded = true
                                    break
                                }
                            }
                            if (!recheckAdded) {
                                exoPlayer.addMediaItem(nextMediaItem)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerController", "Lookahead prefetch failed for ${nextTrack.id}", e)
                }
            }
        }
    }

    private fun checkAndPrefetchQueue() {
        val current = _state.value.currentTrack ?: return
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val remainingCount = if (currentIndex >= 0) {
            currentQueue.size - 1 - currentIndex
        } else {
            currentQueue.size
        }

        if (remainingCount < 3 && !isResolvingRadio) {
            isResolvingRadio = true
            scope.launch(Dispatchers.IO) {
                try {
                    val candidates = queueRecommendationEngine.generateRadioCandidates(
                        seedTrack = current,
                        recentTracks = currentQueue
                    )
                    if (candidates.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val newTracks = candidates.filter { candidate ->
                                _queue.value.none { it.id == candidate.id }
                            }
                            if (newTracks.isNotEmpty()) {
                                _queue.value = _queue.value + newTracks
                                _state.value = _state.value.copy(queue = _queue.value)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerController", "Queue auto-fill failed", e)
                } finally {
                    isResolvingRadio = false
                }
            }
        }
    }

    private var isLoudnessNormEnabled: Boolean = false
    private var isCrossfadeEnabled: Boolean = false
    private var crossfadeSeconds: Int = 0
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var crossfadeJob: kotlinx.coroutines.Job? = null

    /**
     * Applies a gentle volume ramp from 0f -> 1f when transitioning between tracks.
     * Note: ExoPlayer does not natively support true dual-decoder track overlap crossfade;
     * this ramps the incoming track volume over [durationSeconds] as a smooth transition approximation.
     */
    private fun serializeTrack(track: Track): String {
        val obj = JSONObject()
        obj.put("id", track.id)
        obj.put("title", track.title)
        obj.put("artist", track.artist)
        if (track.album != null) obj.put("album", track.album)
        if (track.artworkUrl != null) obj.put("artworkUrl", track.artworkUrl)
        if (track.animatedArtworkUrl != null) obj.put("animatedArtworkUrl", track.animatedArtworkUrl)
        obj.put("durationMs", track.durationMs)
        obj.put("provider", track.provider.name)
        if (track.youtubeVideoId != null) obj.put("youtubeVideoId", track.youtubeVideoId)
        if (track.year != null) obj.put("year", track.year)
        return obj.toString()
    }

    private fun deserializeTrack(jsonStr: String): Track? {
        return try {
            val obj = JSONObject(jsonStr)
            Track(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                album = if (obj.has("album") && !obj.isNull("album")) obj.getString("album") else null,
                artworkUrl = if (obj.has("artworkUrl") && !obj.isNull("artworkUrl")) obj.getString("artworkUrl") else null,
                animatedArtworkUrl = if (obj.has("animatedArtworkUrl") && !obj.isNull("animatedArtworkUrl")) obj.getString("animatedArtworkUrl") else null,
                durationMs = if (obj.has("durationMs")) obj.optLong("durationMs", 0L) else 0L,
                provider = try {
                    ProviderId.valueOf(obj.optString("provider", ProviderId.JIOSAAVN.name))
                } catch (_: Exception) {
                    ProviderId.JIOSAAVN
                },
                youtubeVideoId = if (obj.has("youtubeVideoId") && !obj.isNull("youtubeVideoId")) obj.getString("youtubeVideoId") else null,
                year = if (obj.has("year") && !obj.isNull("year")) obj.getInt("year") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeQueue(queue: List<Track>): String {
        val arr = JSONArray()
        for (track in queue) {
            val obj = JSONObject()
            obj.put("id", track.id)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            if (track.album != null) obj.put("album", track.album)
            if (track.artworkUrl != null) obj.put("artworkUrl", track.artworkUrl)
            if (track.animatedArtworkUrl != null) obj.put("animatedArtworkUrl", track.animatedArtworkUrl)
            obj.put("durationMs", track.durationMs)
            obj.put("provider", track.provider.name)
            if (track.youtubeVideoId != null) obj.put("youtubeVideoId", track.youtubeVideoId)
            if (track.year != null) obj.put("year", track.year)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeQueue(jsonStr: String): List<Track> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                deserializeTrack(arr.getJSONObject(i).toString())?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistPlaybackState() {
        val track = _state.value.currentTrack
        val queue = _queue.value
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        scope.launch(Dispatchers.IO) {
            try {
                val trackJson = track?.let { serializeTrack(it) }
                val queueJson = if (queue.isNotEmpty()) serializeQueue(queue) else null
                settingsDataStore.savePlaybackState(trackJson, queueJson, pos)
            } catch (e: Exception) {
                android.util.Log.w("PlayerController", "Failed to persist playback state", e)
            }
        }
    }

    private fun applyCrossfadeRamp(durationSeconds: Int) {
        if (durationSeconds <= 0) {
            exoPlayer.volume = 1.0f
            return
        }
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val steps = 20
            val stepDelayMs = (durationSeconds * 1000L) / steps
            exoPlayer.volume = 0f
            for (i in 1..steps) {
                kotlinx.coroutines.delay(stepDelayMs)
                exoPlayer.volume = i.toFloat() / steps.toFloat()
            }
            exoPlayer.volume = 1.0f
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.setEnabled(false)
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            android.util.Log.w("PlayerController", "Failed to release LoudnessEnhancer", e)
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun applyLoudnessEnhancer(enabled: Boolean) {
        try {
            val sessionId = exoPlayer.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
                if (enabled) {
                    if (loudnessEnhancer == null) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                            setTargetGain(1000)
                            setEnabled(true)
                        }
                    } else {
                        loudnessEnhancer?.setEnabled(true)
                    }
                } else {
                    releaseLoudnessEnhancer()
                }
            } else {
                releaseLoudnessEnhancer()
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayerController", "LoudnessEnhancer not supported or failed to attach", e)
            releaseLoudnessEnhancer()
        }
    }

    init {
        scope.launch {
            settingsDataStore.audioFocusBehaviorFlow.collect { behavior ->
                audioFocusBehavior = behavior.lowercase()
            }
        }

        scope.launch {
            settingsDataStore.skipSilenceFlow.collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }

        scope.launch {
            settingsDataStore.loudnessNormFlow.collect { enabled ->
                isLoudnessNormEnabled = enabled
                applyLoudnessEnhancer(enabled)
            }
        }

        scope.launch {
            settingsDataStore.crossfadeFlow.collect { enabled ->
                isCrossfadeEnabled = enabled
            }
        }

        scope.launch {
            settingsDataStore.crossfadeSecondsFlow.collect { sec ->
                crossfadeSeconds = sec
            }
        }

        scope.launch {
            try {
                val (trackJson, queueJson, pos) = settingsDataStore.getLastPlaybackState()
                if (!trackJson.isNullOrBlank()) {
                    val restoredTrack = deserializeTrack(trackJson)
                    val restoredQueue = if (!queueJson.isNullOrBlank()) deserializeQueue(queueJson) else emptyList()
                    val restoredPos = pos ?: 0L
                    if (restoredTrack != null && _state.value.currentTrack == null) {
                        _queue.value = if (restoredQueue.isNotEmpty()) restoredQueue else listOf(restoredTrack)
                        _state.value = _state.value.copy(
                            currentTrack = restoredTrack,
                            isPlaying = false,
                            positionMs = restoredPos,
                            durationMs = restoredTrack.durationMs,
                            queue = _queue.value
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PlayerController", "Failed to restore playback state", e)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        _audioRoute.value = detectAudioRoute()
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        if (preferredAudioDevice != null && removedDevices != null) {
                            if (removedDevices.any { it.id == preferredAudioDevice?.id }) {
                                preferredAudioDevice = null
                                exoPlayer.setPreferredAudioDevice(null)
                            }
                        }
                        _audioRoute.value = detectAudioRoute()
                    }
                }, null)
            } catch (_: Exception) {
                // Ignore fallback
            }
        }


        scope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        positionMs = pos,
                        durationMs = dur
                    )
                    if (dur > 0L && (pos.toFloat() / dur.toFloat()) >= 0.80f) {
                        if (!hasTriggered80PercentPrefetch) {
                            hasTriggered80PercentPrefetch = true
                            prefetchLookaheadAudio()
                        }
                    }
                }
                kotlinx.coroutines.delay(250)
            }
        }
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0 && isLoudnessNormEnabled) {
                    applyLoudnessEnhancer(true)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    requestAudioFocus()
                    if (isLoudnessNormEnabled) {
                        applyLoudnessEnhancer(true)
                    }
                } else {
                    if (!pausedByTransientFocusLoss) {
                        abandonAudioFocus()
                    }
                    releaseLoudnessEnhancer()
                    persistPlaybackState()
                }
                _state.value = _state.value.copy(isPlaying = isPlaying)
                notifyPlaybackChanged(_state.value.currentTrack, isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                releaseLoudnessEnhancer()
                if (isLoudnessNormEnabled) {
                    applyLoudnessEnhancer(true)
                }
                if (isCrossfadeEnabled) {
                    val durationSec = if (crossfadeSeconds > 0) crossfadeSeconds else 3
                    applyCrossfadeRamp(durationSec)
                }
                val nextTrackId = mediaItem?.mediaId
                if (!nextTrackId.isNullOrBlank()) {
                    val matchedTrack = _queue.value.find { it.id == nextTrackId }
                    if (matchedTrack != null && _state.value.currentTrack?.id != matchedTrack.id) {
                        val prevHistoryId = currentHistoryId
                        if (prevHistoryId != null) {
                            scope.launch(Dispatchers.IO) {
                                try { libraryDao.updateCompletion(prevHistoryId, 100) } catch (_: Exception) {}
                            }
                        }
                        val newHistoryId = UUID.randomUUID().toString()
                        currentHistoryId = newHistoryId
                        currentTrackStartTime = System.currentTimeMillis()
                        hasTriggered80PercentPrefetch = false

                        _state.value = _state.value.copy(currentTrack = matchedTrack, isPlaying = true)
                        notifyPlaybackChanged(matchedTrack, true)
                        persistPlaybackState()

                        scope.launch(Dispatchers.IO) {
                            try {
                                songDao.incrementPlayCount(matchedTrack.id)
                                libraryDao.insertHistory(
                                    PlaybackHistoryEntity(
                                        id = newHistoryId,
                                        provider = matchedTrack.provider.name,
                                        providerTrackId = matchedTrack.id,
                                        title = matchedTrack.title,
                                        artist = matchedTrack.artist,
                                        album = matchedTrack.album,
                                        artworkUrl = matchedTrack.artworkUrl,
                                        durationMs = matchedTrack.durationMs,
                                        playedAt = System.currentTimeMillis(),
                                        language = matchedTrack.language
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
                checkAndPrefetchQueue()
                prefetchLookaheadAudio()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    releaseLoudnessEnhancer()
                }
                if (playbackState == Player.STATE_ENDED) {
                    val track = _state.value.currentTrack
                    val streamUrl = activeStreamUrl
                    val histId = currentHistoryId
                    if (histId != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                libraryDao.updateCompletion(histId, 100)
                            } catch (_: Exception) {}
                        }
                    }
                    if (track != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                songDao.incrementPlayCount(track.id)
                            } catch (_: Exception) {
                                // Ignore failure in background
                            }
                        }
                    }
                    if (sleepWhenTrackEnds) {
                        sleepWhenTrackEnds = false
                        _sleepTimerMinutes.value = null
                        exoPlayer.pause()
                    } else if (_state.value.repeatMode == PlaybackRepeatMode.ONE) {
                        seekTo(0L)
                        exoPlayer.play()
                    } else if (_state.value.repeatMode == PlaybackRepeatMode.ALL) {
                        skipToNext()
                    } else {
                        val q = _queue.value
                        val current = _state.value.currentTrack
                        val currentIndex = if (current != null) q.indexOfFirst { it.id == current.id } else -1
                        if (currentIndex in 0 until q.size - 1 || exoPlayer.hasNextMediaItem()) {
                            skipToNext()
                        } else {
                            exoPlayer.pause()
                            seekTo(0L)
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PlayerController", "ExoPlayer error [${error.errorCodeName}]: ${error.message}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                ) {
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        })
    }

    fun playTrack(track: Track) {
        // Record fast skip / completion on previous track
        val prevHistoryId = currentHistoryId
        val prevPos = exoPlayer.currentPosition
        val prevDuration = exoPlayer.duration
        if (prevHistoryId != null) {
            val durationPlayed = System.currentTimeMillis() - currentTrackStartTime
            val skippedFast = durationPlayed < 10_000L && prevPos < 10_000L
            val completion = if (prevDuration > 0) ((prevPos * 100) / prevDuration).toInt().coerceIn(0, 100) else 0
            scope.launch(Dispatchers.IO) {
                try {
                    if (skippedFast) {
                        libraryDao.markSkippedFast(prevHistoryId)
                    }
                    if (completion > 0) {
                        libraryDao.updateCompletion(prevHistoryId, completion)
                    }
                } catch (_: Exception) {}
            }
        }

        if (_queue.value.none { it.id == track.id }) {
            _queue.value = _queue.value + track
        }
        shuffleStrategy.recordPlayed(track)
        // Immediate optimistic UI update so MiniPlayer shows instantly on tap
        _state.value = _state.value.copy(currentTrack = track, isPlaying = true, queue = _queue.value)

        scope.launch {
            val streamUrl = streamUrlResolver.resolve(track)

            if (!streamUrl.isNullOrBlank()) {
                activeStreamUrl = streamUrl
                hasTriggered80PercentPrefetch = false
                val mediaItem = buildMediaItem(track, streamUrl)

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                ensurePlaybackServiceStarted()
                _state.value = _state.value.copy(currentTrack = track, isPlaying = true)
                notifyPlaybackChanged(track, true)
                persistPlaybackState()

                val newHistoryId = UUID.randomUUID().toString()
                currentHistoryId = newHistoryId
                currentTrackStartTime = System.currentTimeMillis()

                scope.launch(Dispatchers.IO) {
                    try {
                        libraryDao.insertHistory(
                            PlaybackHistoryEntity(
                                id = newHistoryId,
                                provider = track.provider.name,
                                providerTrackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                artworkUrl = track.artworkUrl,
                                durationMs = track.durationMs,
                                playedAt = System.currentTimeMillis(),
                                language = track.language
                            )
                        )
                    } catch (_: Exception) {
                        // Ignore history failure
                    }
                }
                checkAndPrefetchQueue()
                prefetchLookaheadAudio()
            } else {
                // Playback source resolution failed: update isPlaying back to false
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun playTrack(track: Track, streamUrl: String) {
        // Immediate optimistic UI update
        _state.value = _state.value.copy(currentTrack = track, isPlaying = true)
        activeStreamUrl = streamUrl
        scope.launch {
            val localPath = withContext(Dispatchers.IO) {
                downloadManager.getDownloadedPath(track.id)
                    ?: audioCacheManager.getCachedPath(track.id)
            }

            val mediaUri = if (localPath != null) {
                if (localPath.startsWith("http://") || localPath.startsWith("https://")) {
                    Uri.parse(localPath)
                } else {
                    Uri.fromFile(File(localPath))
                }
            } else {
                Uri.parse(streamUrl)
            }

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        setArtworkUri(Uri.parse(track.artworkUrl))
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(mediaUri)
                .setCustomCacheKey(track.id)
                .setMediaMetadata(mediaMetadata)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            ensurePlaybackServiceStarted()
            _state.value = _state.value.copy(currentTrack = track, isPlaying = true)
            notifyPlaybackChanged(track, true)
            persistPlaybackState()

            scope.launch(Dispatchers.IO) {
                try {
                    libraryDao.insertHistory(
                        PlaybackHistoryEntity(
                            id = UUID.randomUUID().toString(),
                            provider = track.provider.name,
                            providerTrackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            playedAt = System.currentTimeMillis(),
                            language = track.language
                        )
                    )
                } catch (_: Exception) {
                    // Ignore history failure
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.currentMediaItem == null && _state.value.currentTrack != null) {
                val track = _state.value.currentTrack!!
                val pos = _state.value.positionMs
                playTrack(track)
                if (pos > 0) {
                    seekTo(pos)
                }
            } else {
                exoPlayer.play()
            }
        }
        _state.value = _state.value.copy(isPlaying = exoPlayer.isPlaying)
        notifyPlaybackChanged(_state.value.currentTrack, exoPlayer.isPlaying)
    }

    fun setQueue(tracks: List<Track>) {
        _queue.value = tracks
        _state.value = _state.value.copy(queue = tracks)
        checkAndPrefetchQueue()
        prefetchLookaheadAudio()
    }

    fun addToQueue(track: Track) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(track)
        setQueue(currentQueue)
    }

    fun setPlayingFrom(source: String) {
        _playingFrom.value = source
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepWhenTrackEnds = false
        _sleepTimerMinutes.value = minutes
        if (minutes == -1) {
            sleepWhenTrackEnds = true
        } else if (minutes != null && minutes > 0) {
            sleepTimerJob = scope.launch {
                kotlinx.coroutines.delay(minutes * 60 * 1000L)
                exoPlayer.pause()
                _sleepTimerMinutes.value = null
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, source: String = "Unknown") {
        if (tracks.isEmpty()) return
        _playingFrom.value = source
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        setQueue(tracks)
        playTrack(tracks[safeIndex])
    }

    fun removeFromQueue(trackId: String) {
        _queue.value = _queue.value.filter { it.id != trackId }
        _state.value = _state.value.copy(queue = _queue.value)
    }

    fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffleEnabled
        exoPlayer.shuffleModeEnabled = newShuffle
        _state.value = _state.value.copy(isShuffleEnabled = newShuffle)
    }

    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        exoPlayer.repeatMode = when (nextMode) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.value = _state.value.copy(
            repeatMode = nextMode,
            isRepeatEnabled = nextMode != PlaybackRepeatMode.OFF
        )
    }

    fun skipToNext() {
        val q = _queue.value
        val current = _state.value.currentTrack
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (q.isNotEmpty()) {
            val currentIndex = if (current != null) q.indexOfFirst { it.id == current.id } else -1
            val nextIndex = if (_state.value.isShuffleEnabled && q.size > 1) {
                shuffleStrategy.nextIndex(q, currentIndex)
            } else {
                if (currentIndex in 0 until q.size - 1) currentIndex + 1 else 0
            }
            playTrack(q[nextIndex])
        }
        checkAndPrefetchQueue()
        prefetchLookaheadAudio()
    }

    fun skipToPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val q = _queue.value
        val current = _state.value.currentTrack
        if (q.isNotEmpty()) {
            val prevIndex = if (_state.value.isShuffleEnabled) {
                val prevId = shuffleStrategy.getPreviousTrackId()
                val foundIndex = if (prevId != null) q.indexOfFirst { it.id == prevId } else -1
                if (foundIndex != -1) foundIndex else {
                    val currentIndex = if (current != null) q.indexOfFirst { it.id == current.id } else -1
                    if (currentIndex > 0) currentIndex - 1 else q.size - 1
                }
            } else {
                val currentIndex = if (current != null) q.indexOfFirst { it.id == current.id } else -1
                if (currentIndex > 0) currentIndex - 1 else q.size - 1
            }
            playTrack(q[prevIndex])
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    private fun notifyPlaybackChanged(track: Track?, isPlaying: Boolean) {
        onPlaybackChanged?.invoke(
            track?.title ?: "Not Playing",
            track?.artist ?: "Open app to play",
            track?.artworkUrl,
            isPlaying
        )
    }

    private fun ensurePlaybackServiceStarted() {
        try {
            val intent = android.content.Intent(context, MuEPlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.w("PlayerController", "Failed to start playback foreground service", e)
        }
    }

    companion object {
        var onPlaybackChanged: ((title: String, artist: String, artworkUrl: String?, isPlaying: Boolean) -> Unit)? = null
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun applyListenTogetherSync(trackId: String, hostPositionMs: Long, isPlaying: Boolean) {
        scope.launch {
            val current = _state.value.currentTrack
            if (current == null || current.id != trackId) {
                val resolvedTrack = withContext(Dispatchers.IO) {
                    val searchMatches = jioSaavnProvider.queryTracks(trackId)
                    searchMatches.firstOrNull { it.id == trackId } ?: searchMatches.firstOrNull()
                } ?: Track(
                    id = trackId,
                    title = "Synced Track",
                    artist = "Listen Together",
                    album = "",
                    durationMs = 0L,
                    artworkUrl = null,
                    provider = com.mue.core.model.ProviderId.JIOSAAVN
                )
                playTrack(resolvedTrack)
                if (hostPositionMs > 0) {
                    exoPlayer.seekTo(hostPositionMs)
                    _state.value = _state.value.copy(positionMs = hostPositionMs)
                }
                if (isPlaying && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                    _state.value = _state.value.copy(isPlaying = true)
                } else if (!isPlaying && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                }
            } else {
                val currentPos = exoPlayer.currentPosition
                if (kotlin.math.abs(currentPos - hostPositionMs) > 150) {
                    exoPlayer.seekTo(hostPositionMs)
                    _state.value = _state.value.copy(positionMs = hostPositionMs)
                }
                if (isPlaying && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                    _state.value = _state.value.copy(isPlaying = true)
                } else if (!isPlaying && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        }
    }
}
