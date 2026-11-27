package com.mue.core.player.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Custom MediaNotification.Provider wrapping DefaultMediaNotificationProvider
 * to enforce ongoing foreground persistence while playing and silent updates.
 */
@UnstableApi
class MuEMediaNotificationProvider(
    context: Context,
    channelId: String = "mue_playback_channel",
    notificationId: Int = 1001
) : MediaNotification.Provider {

    private val delegate = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(channelId)
        .setNotificationId(notificationId)
        .build()

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )
        val isPlaying = mediaSession.player.isPlaying
        if (isPlaying) {
            base.notification.flags = base.notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_ONLY_ALERT_ONCE
        } else {
            base.notification.flags = (base.notification.flags and Notification.FLAG_ONGOING_EVENT.inv()) or
                Notification.FLAG_ONLY_ALERT_ONCE
        }
        return base
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = delegate.handleCustomCommand(session, action, extras)
}
