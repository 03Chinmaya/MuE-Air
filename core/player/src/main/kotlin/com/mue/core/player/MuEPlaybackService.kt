package com.mue.core.player

import android.app.PendingIntent
import android.content.Intent
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.mue.core.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MuEPlaybackService : MediaSessionService() {
    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var settingsDataStore: SettingsDataStore
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isAvrcpEnabled: Boolean = true

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            settingsDataStore.avrcpEnabledFlow.collect { enabled ->
                isAvrcpEnabled = enabled
            }
        }
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                if (!isAvrcpEnabled && controller.packageName.contains("bluetooth", ignoreCase = true)) {
                    return MediaSession.ConnectionResult.reject()
                }
                val connectionResult = super.onConnect(session, controller)
                val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()

                val shuffleButton = CommandButton.Builder()
                    .setDisplayName("Shuffle")
                    .setIconResId(androidx.media3.session.R.drawable.media3_icon_shuffle_on)
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                    .build()
                val repeatButton = CommandButton.Builder()
                    .setDisplayName("Repeat")
                    .setIconResId(androidx.media3.session.R.drawable.media3_icon_repeat_all)
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                    .build()
                session.setCustomLayout(listOf(shuffleButton, repeatButton))

                return MediaSession.ConnectionResult.accept(
                    connectionResult.availableSessionCommands,
                    availablePlayerCommands
                )
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                when (playerCommand) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        playerController.skipToNext()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        playerController.skipToPrevious()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SET_SHUFFLE_MODE -> {
                        playerController.toggleShuffle()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SET_REPEAT_MODE -> {
                        playerController.toggleRepeat()
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                @Suppress("DEPRECATION")
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            playerController.exoPlayer.play()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            playerController.exoPlayer.pause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                            playerController.togglePlayPause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            playerController.skipToNext()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            playerController.skipToPrevious()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (sessionActivityIntent != null) {
            PendingIntent.getActivity(
                this, 0, sessionActivityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val sessionBuilder = MediaSession.Builder(this, playerController.exoPlayer)
            .setCallback(callback)
        if (pendingIntent != null) {
            sessionBuilder.setSessionActivity(pendingIntent)
        }
        val session = sessionBuilder.build()
        mediaSession = session
        addSession(session)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (!isAvrcpEnabled && controllerInfo.packageName.contains("bluetooth", ignoreCase = true)) {
            return null
        }
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

