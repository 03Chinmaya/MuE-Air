package com.mue.feature.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassAlertDialog
import com.mue.core.design.GlassSettingsRow
import com.mue.core.design.GlassSwitchRow
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import com.mue.core.design.settingsRadioButtonColors

@Composable
fun ListenTogetherSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: ListenTogetherSettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val isHost by viewModel.isHost.collectAsStateWithLifecycle()

    val roomMaxListeners by viewModel.roomMaxListeners.collectAsStateWithLifecycle()
    val roomDjMode by viewModel.roomDjMode.collectAsStateWithLifecycle()
    val roomPrivacy by viewModel.roomPrivacy.collectAsStateWithLifecycle()


    val highSyncQuality by viewModel.highSyncQuality.collectAsStateWithLifecycle()
    val allowGuestsControl by viewModel.allowGuestsControl.collectAsStateWithLifecycle()

    val defaultMaxListeners by viewModel.defaultMaxListeners.collectAsStateWithLifecycle()
    val defaultDjMode by viewModel.defaultDjMode.collectAsStateWithLifecycle()
    val defaultRoomPrivacy by viewModel.defaultRoomPrivacy.collectAsStateWithLifecycle()

    val ltJoinViaLinkBehavior by viewModel.ltJoinViaLinkBehavior.collectAsStateWithLifecycle()

    var sessionHistoryCleared by remember { mutableStateOf(false) }
    var showDjDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDefaultDjDialog by remember { mutableStateOf(false) }
    var showDefaultPrivacyDialog by remember { mutableStateOf(false) }
    var showJoinBehaviorDialog by remember { mutableStateOf(false) }
    val joinBehaviorOptions = listOf("Ask first", "Auto-join")

    val djOptions = listOf(
        "DJ" to "DJ Mode (Host controls queue & playback)",
        "Collab" to "Collaborative (Anyone can add & skip)"
    )

    val privacyOptions = listOf(
        "Invite Link" to "Invite Only (Code required)"
    )

    if (showDjDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDjDialog = false },
            title = { Text("Room Control Mode", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    djOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setRoomDjMode(mode)
                                    showDjDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = roomDjMode == mode,
                                onClick = {
                                    viewModel.setRoomDjMode(mode)
                                    showDjDialog = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Text(label, color = PureWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDjDialog = false }) { Text("Done", color = PureWhite) } }
        )
    }

    if (showPrivacyDialog) {
        GlassAlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Room Privacy", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    privacyOptions.forEach { (priv, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setRoomPrivacy(priv)
                                    showPrivacyDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = roomPrivacy == priv,
                                onClick = {
                                    viewModel.setRoomPrivacy(priv)
                                    showPrivacyDialog = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Text(label, color = PureWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Done", color = PureWhite) } }
        )
    }

    if (showDefaultDjDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDefaultDjDialog = false },
            title = { Text("Default Control Mode", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    djOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setDefaultDjMode(mode)
                                    showDefaultDjDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultDjMode == mode,
                                onClick = {
                                    viewModel.setDefaultDjMode(mode)
                                    showDefaultDjDialog = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Text(label, color = PureWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultDjDialog = false }) { Text("Done", color = PureWhite) } }
        )
    }

    if (showDefaultPrivacyDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDefaultPrivacyDialog = false },
            title = { Text("Default Room Privacy", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    privacyOptions.forEach { (priv, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setDefaultRoomPrivacy(priv)
                                    showDefaultPrivacyDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultRoomPrivacy == priv,
                                onClick = {
                                    viewModel.setDefaultRoomPrivacy(priv)
                                    showDefaultPrivacyDialog = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Text(label, color = PureWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultPrivacyDialog = false }) { Text("Done", color = PureWhite) } }
        )
    }

    if (showJoinBehaviorDialog) {
        GlassAlertDialog(
            onDismissRequest = { showJoinBehaviorDialog = false },
            title = { Text("Join-via-link Behavior", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose how Listen Together handles room invite links.",
                        color = MutedGrey,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    joinBehaviorOptions.forEach { behavior ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setLtJoinViaLinkBehavior(behavior)
                                    showJoinBehaviorDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ltJoinViaLinkBehavior == behavior,
                                onClick = {
                                    viewModel.setLtJoinViaLinkBehavior(behavior)
                                    showJoinBehaviorDialog = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Text(behavior, color = PureWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showJoinBehaviorDialog = false }) { Text("Done", color = PureWhite) } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PureWhite
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Listen Together Settings",
                color = PureWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (activeRoom != null) {
            SettingsSection("ACTIVE SESSION: $activeRoom") {
                GlassSettingsRow(
                    icon = Icons.Default.Sync,
                    title = "Session Role",
                    subtitle = if (isHost) "You are hosting this session" else "Connected as guest listener",
                    trailingText = if (isHost) "Host" else "Guest"
                )

                if (isHost) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = PureWhite, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Room Max Listeners", color = PureWhite, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Capacity limit (2–20)", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { if (roomMaxListeners > 2) viewModel.setRoomMaxListeners(roomMaxListeners - 1) },
                                enabled = roomMaxListeners > 2,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = if (roomMaxListeners > 2) PureWhite else MutedGrey.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            }
                            Text("$roomMaxListeners", color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                            IconButton(
                                onClick = { if (roomMaxListeners < 20) viewModel.setRoomMaxListeners(roomMaxListeners + 1) },
                                enabled = roomMaxListeners < 20,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = if (roomMaxListeners < 20) PureWhite else MutedGrey.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    GlassSettingsRow(
                        icon = Icons.Default.MusicNote,
                        title = "DJ Mode",
                        subtitle = "Queue & playback permission",
                        trailingText = if (roomDjMode == "DJ") "DJ Only" else "Collab",
                        onClick = { showDjDialog = true }
                    )

                    GlassSettingsRow(
                        icon = Icons.Default.Lock,
                        title = "Room Privacy",
                        subtitle = "Visibility and access control",
                        trailingText = roomPrivacy,
                        onClick = { showPrivacyDialog = true }
                    )


                }

                GlassSwitchRow(
                    title = "Allow Guests to Control Playback",
                    subtitle = "Let participants skip, pause, and seek tracks in room",
                    checked = allowGuestsControl,
                    onCheckedChange = { viewModel.setAllowGuestsControl(it) }
                )

                GlassSwitchRow(
                    title = "High Sync Quality",
                    subtitle = "Sub-100ms frequent position synchronization",
                    checked = highSyncQuality,
                    onCheckedChange = { viewModel.setHighSyncQuality(it) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (isHost) viewModel.endSession() else viewModel.leaveRoom()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHost) "End Session for All" else "Leave Session",
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SettingsSection("DEFAULT ROOM PREFERENCES") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = PureWhite, modifier = Modifier.size(22.dp))
                    Column {
                        Text("Default Max Listeners", color = PureWhite, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Default capacity for new rooms (2–20)", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { if (defaultMaxListeners > 2) viewModel.setDefaultMaxListeners(defaultMaxListeners - 1) },
                        enabled = defaultMaxListeners > 2,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = if (defaultMaxListeners > 2) PureWhite else MutedGrey.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                    Text("$defaultMaxListeners", color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    IconButton(
                        onClick = { if (defaultMaxListeners < 20) viewModel.setDefaultMaxListeners(defaultMaxListeners + 1) },
                        enabled = defaultMaxListeners < 20,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = if (defaultMaxListeners < 20) PureWhite else MutedGrey.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            GlassSettingsRow(
                icon = Icons.Default.MusicNote,
                title = "Default DJ Mode",
                subtitle = "Initial permission for created rooms",
                trailingText = if (defaultDjMode == "DJ") "DJ Only" else "Collab",
                onClick = { showDefaultDjDialog = true }
            )

            GlassSettingsRow(
                icon = Icons.Default.Lock,
                title = "Default Privacy",
                subtitle = "Initial privacy for created rooms",
                trailingText = defaultRoomPrivacy,
                onClick = { showDefaultPrivacyDialog = true }
            )



            GlassSettingsRow(
                icon = Icons.Default.Share,
                title = "Join-via-link Behavior",
                subtitle = "Action when opening invite links",
                trailingText = ltJoinViaLinkBehavior,
                onClick = { showJoinBehaviorDialog = true }
            )
        }

        SettingsSection("SYNCHRONIZATION") {
            GlassSettingsRow(
                icon = Icons.Default.Sync,
                title = "Sync Protocol",
                subtitle = "Sub-100ms WebSocket clock sync",
                trailingText = "Active"
            )
            GlassSwitchRow(
                title = "Sync Quality: High",
                subtitle = "Syncs seek position and pause states with high frequency",
                checked = highSyncQuality,
                onCheckedChange = { viewModel.setHighSyncQuality(it) }
            )
        }

        SettingsSection("PERMISSIONS & GUESTS") {
            GlassSwitchRow(
                title = "Allow Guests to Control Playback",
                subtitle = "Let participants skip, pause, and seek tracks in room",
                checked = allowGuestsControl,
                onCheckedChange = { viewModel.setAllowGuestsControl(it) }
            )
        }

        SettingsSection("SESSION HISTORY") {
            GlassSettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Session History",
                subtitle = if (sessionHistoryCleared) "Session history cleared" else "Remove past room IDs and participant caches",
                onClick = { sessionHistoryCleared = true }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
