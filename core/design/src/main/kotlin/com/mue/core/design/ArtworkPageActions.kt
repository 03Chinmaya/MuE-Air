package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

val TOP_CONTROL_SIZE = 44.dp

/**
 * Floating 44dp frosted action pill for right-hand top bar controls on the Home screen.
 * Displays Update (when available), Settings, Notifications, and Profile.
 */
@Composable
fun ArtworkPageActions(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    isUpdateAvailable: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val pillShape = CircleShape

    Box(
        modifier = modifier
            .height(TOP_CONTROL_SIZE)
            .liquidGlass(hazeState = hazeState, shape = pillShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isUpdateAvailable) {
                ActionPillButton(
                    icon = Icons.Default.SystemUpdate,
                    contentDescription = "Update Available",
                    onClick = onUpdateClick
                )
            }
            ActionPillButton(
                icon = SfIcons.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
            ActionPillButton(
                icon = SfIcons.Notifications,
                contentDescription = "Notifications",
                onClick = onNotificationsClick
            )
            ActionPillButton(
                icon = SfIcons.Person,
                contentDescription = "Profile",
                onClick = onProfileClick
            )
        }
    }
}

/**
 * Floating borderless profile icon for non-home screens (no background pill).
 */
@Composable
fun FloatingProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(TOP_CONTROL_SIZE)
    ) {
        Icon(
            imageVector = SfIcons.Person,
            contentDescription = "Profile",
            tint = PureWhite,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Floating frosted header pill hosting Settings and Profile actions.
 * Order: Settings first, then Profile.
 */
@Composable
fun FloatingHeaderPill(
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionSettings: String = "Settings",
    contentDescriptionProfile: String = "Profile",
) {
    val haptics = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(percent = 50)
    val hazeState = LocalHazeState.current

    Box(
        modifier = modifier
            .height(48.dp)
            .liquidGlass(hazeState = hazeState, shape = pillShape, tintAlpha = 0.08f)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsClick()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = SfIcons.Settings,
                    contentDescription = contentDescriptionSettings,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onProfileClick()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = SfIcons.Person,
                    contentDescription = contentDescriptionProfile,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Floating borderless settings icon for non-home screens.
 */
@Composable
fun FloatingSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Settings"
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(TOP_CONTROL_SIZE)
    ) {
        Icon(
            imageVector = SfIcons.Settings,
            contentDescription = contentDescription,
            tint = PureWhite,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PureWhite,
            modifier = Modifier.size(24.dp)
        )
    }
}
