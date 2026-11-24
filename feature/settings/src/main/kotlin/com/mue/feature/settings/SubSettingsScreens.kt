package com.mue.feature.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.AccentRed
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassSwitchRow
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.settingsRadioButtonColors

@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit
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
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black
            )
        )
    }
}

@Composable
fun GlassOptionRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = settingsRadioButtonColors()
        )
    }
}

@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val streamingQuality by viewModel.streamingQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val audioFocus by viewModel.audioFocusBehavior.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val loudnessNorm by viewModel.loudnessNorm.collectAsStateWithLifecycle()
    val bluetoothAvrcp by viewModel.bluetoothAvrcp.collectAsStateWithLifecycle()

    var sliderSeconds by remember(crossfadeSeconds) { mutableFloatStateOf(crossfadeSeconds.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SubScreenHeader(title = "Playback", onBack = onBack)

        SettingsSection(title = "STREAMING QUALITY") {
            GlassOptionRow(
                title = "Auto",
                subtitle = "Optimizes bit rate based on connection speed",
                selected = streamingQuality == "Auto",
                onClick = { viewModel.setStreamingQuality("Auto") }
            )
            GlassOptionRow(
                title = "High (320 kbps)",
                subtitle = "Crystal clear fidelity for high-speed networks",
                selected = streamingQuality == "High",
                onClick = { viewModel.setStreamingQuality("High") }
            )
            GlassOptionRow(
                title = "Data Saver (128 kbps)",
                subtitle = "Conserves mobile data on metered networks",
                selected = streamingQuality == "Low",
                onClick = { viewModel.setStreamingQuality("Low") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "DOWNLOAD QUALITY") {
            GlassOptionRow(
                title = "High (320 kbps)",
                subtitle = "Maximum fidelity for offline downloads",
                selected = downloadQuality == "320 kbps",
                onClick = { viewModel.setDownloadQuality("320 kbps") }
            )
            GlassOptionRow(
                title = "Standard (160 kbps)",
                subtitle = "Saves device storage space while retaining good audio clarity",
                selected = downloadQuality == "160 kbps",
                onClick = { viewModel.setDownloadQuality("160 kbps") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "CROSSFADE & TRANSITIONS") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Crossfade Duration", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (sliderSeconds.toInt() == 0) "Off" else "${sliderSeconds.toInt()}s",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = sliderSeconds,
                    onValueChange = { sliderSeconds = it },
                    onValueChangeFinished = { viewModel.setCrossfadeSeconds(sliderSeconds.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = AccentRed,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "AUDIO FOCUS BEHAVIOR") {
            GlassOptionRow(
                title = "Duck Volume",
                subtitle = "Temporarily lowers music volume when notification sounds",
                selected = audioFocus == "duck",
                onClick = { viewModel.setAudioFocusBehavior("duck") }
            )
            GlassOptionRow(
                title = "Pause Playback",
                subtitle = "Pauses music during calls and notifications, then resumes",
                selected = audioFocus == "pause",
                onClick = { viewModel.setAudioFocusBehavior("pause") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "AUDIO ENHANCEMENTS") {
            GlassSwitchRow(
                title = "Skip Silence",
                subtitle = "Automatically cuts silent intros and interludes",
                checked = skipSilence,
                onCheckedChange = { viewModel.setSkipSilence(it) }
            )
            GlassSwitchRow(
                title = "Loudness Normalization",
                subtitle = "Maintains consistent listening volume across all tracks",
                checked = loudnessNorm,
                onCheckedChange = { viewModel.setLoudnessNorm(it) }
            )
            GlassSwitchRow(
                title = "Bluetooth AVRCP Metadata",
                subtitle = "Broadcasts song title, artist, and album to car or headphones",
                checked = bluetoothAvrcp,
                onCheckedChange = { viewModel.setBluetoothAvrcp(it) }
            )
            GlassSwitchRow(
                title = "Lossless Audio",
                subtitle = "24-bit 96kHz FLAC studio quality (Coming Soon)",
                checked = false,
                onCheckedChange = {},
                enabled = false
            )
            GlassSwitchRow(
                title = "Automix",
                subtitle = "Seamless beat-matched AI track transitions (Coming Soon)",
                checked = false,
                onCheckedChange = {},
                enabled = false
            )
            GlassSwitchRow(
                title = "Spatial Audio",
                subtitle = "Immersive multi-channel 3D soundstage (Coming Soon)",
                checked = false,
                onCheckedChange = {},
                enabled = false
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val activityVisibility by viewModel.activityVisibility.collectAsStateWithLifecycle()
    val hideReadMarks by viewModel.hideReadMarks.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchBlockedUsers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SubScreenHeader(title = "Privacy", onBack = onBack)

        SettingsSection(title = "LISTENING ACTIVITY") {
            GlassOptionRow(
                title = "Public",
                subtitle = "Anyone in your Listen Together rooms can see active songs",
                selected = activityVisibility == "Public",
                onClick = { viewModel.setActivityVisibility("Public") }
            )
            GlassOptionRow(
                title = "Nobody (Private)",
                subtitle = "Hide your current playback status from all users",
                selected = activityVisibility == "Nobody",
                onClick = { viewModel.setActivityVisibility("Nobody") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "BLOCKED USERS") {
            if (blockedUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "No blocked users. When you block users in Listen Together, they will appear here.",
                        color = MutedGrey,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                blockedUsers.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(user, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "Unblock",
                            color = AccentRed,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { viewModel.unblockUser(user) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val pushNotifications by viewModel.pushNotifications.collectAsStateWithLifecycle()
    val inAppBadges by viewModel.inAppBadges.collectAsStateWithLifecycle()
    val dndQuietHours by viewModel.dndQuietHours.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SubScreenHeader(title = "Notifications", onBack = onBack)

        SettingsSection(title = "PUSH NOTIFICATIONS") {
            GlassSwitchRow(
                title = "Push Notifications",
                subtitle = "Get notified about room invites and artist releases",
                checked = pushNotifications,
                onCheckedChange = { viewModel.setPushNotifications(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "IN-APP BADGES") {
            GlassSwitchRow(
                title = "In-App Badges",
                subtitle = "Display notification badges on navigation tabs",
                checked = inAppBadges,
                onCheckedChange = { viewModel.setInAppBadges(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "DO NOT DISTURB") {
            GlassSwitchRow(
                title = "Quiet Hours (10 PM - 8 AM)",
                subtitle = "Silently receive alerts without sound or vibration during quiet hours",
                checked = dndQuietHours,
                onCheckedChange = { viewModel.setDndQuietHours(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LinkedDevicesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val deviceHandoff by viewModel.deviceHandoff.collectAsStateWithLifecycle()
    val devices by viewModel.linkedDevices.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchLinkedDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SubScreenHeader(title = "Linked Devices", onBack = onBack)

        SettingsSection(title = "CROSS-DEVICE PLAYBACK") {
            GlassSwitchRow(
                title = "Device Handoff",
                subtitle = "Seamlessly hand off active playback queue between phone and desktop",
                checked = deviceHandoff,
                onCheckedChange = { viewModel.setDeviceHandoff(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "ACTIVE SESSIONS") {
            if (devices.isEmpty()) {
                Text(
                    text = "No active linked devices found",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (device.isCurrent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1DB954).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Current", color = Color(0xFF1DB954), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(device.lastActive, color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }

                    if (!device.isCurrent) {
                        Text(
                            text = "Revoke",
                            color = AccentRed,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.revokeDevice(device.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val reduceAnimation by viewModel.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by viewModel.reduceDynamicBlur.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SubScreenHeader(title = "Appearance & Accessibility", onBack = onBack)

        SettingsSection(title = "APP LANGUAGE") {
            GlassOptionRow(
                title = "System Default",
                subtitle = "Follows the device operating system language",
                selected = appLanguage == "System Default",
                onClick = { viewModel.setAppLanguage("System Default") }
            )
            GlassOptionRow(
                title = "English",
                subtitle = "English (United States / International)",
                selected = appLanguage == "English",
                onClick = { viewModel.setAppLanguage("English") }
            )
            GlassOptionRow(
                title = "Hindi (हिन्दी)",
                subtitle = "हिन्दी भाषा",
                selected = appLanguage == "Hindi",
                onClick = { viewModel.setAppLanguage("Hindi") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "APPEARANCE MODE") {
            GlassOptionRow(
                title = "System Default",
                subtitle = "Follows your Android system light or dark appearance",
                selected = theme == "System Default" || theme == "System",
                onClick = { viewModel.setTheme("System Default") }
            )
            GlassOptionRow(
                title = "Dark Mode",
                subtitle = "Always use dark theme with deep contrast",
                selected = theme != "Light" && theme != "Light Mode" && theme != "System Default" && theme != "System",
                onClick = { viewModel.setTheme("Pure Black OLED") }
            )
            GlassOptionRow(
                title = "Light Mode",
                subtitle = "Clean Apple-styled light interface with bright surfaces",
                selected = theme == "Light Mode" || theme == "Light",
                onClick = { viewModel.setTheme("Light Mode") }
            )
        }

        if (theme != "Light Mode" && theme != "Light") {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = "DARK COLOR PALETTE") {
                GlassOptionRow(
                    title = "Pure Black OLED",
                    subtitle = "Absolute pitch black #000000 for maximum battery savings",
                    selected = theme == "Pure Black OLED" || theme == "System Default",
                    onClick = { viewModel.setTheme("Pure Black OLED") }
                )
                GlassOptionRow(
                    title = "Deep Slate",
                    subtitle = "Subtle dark slate accents with softened contrast",
                    selected = theme == "Slate",
                    onClick = { viewModel.setTheme("Slate") }
                )
                GlassOptionRow(
                    title = "Liquid Glass",
                    subtitle = "Dynamic color-reactive glass glow behind player controls",
                    selected = theme == "Liquid Glass",
                    onClick = { viewModel.setTheme("Liquid Glass") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "TEXT & FONT SCALE") {
            GlassOptionRow(
                title = "Small",
                subtitle = "Compact text size for high information density",
                selected = fontSize == "Small",
                onClick = { viewModel.setFontSize("Small") }
            )
            GlassOptionRow(
                title = "Medium (Default)",
                subtitle = "Standard balanced typography scale",
                selected = fontSize == "Medium",
                onClick = { viewModel.setFontSize("Medium") }
            )
            GlassOptionRow(
                title = "Large",
                subtitle = "Enhanced legibility with larger titles and captions",
                selected = fontSize == "Large",
                onClick = { viewModel.setFontSize("Large") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "ACCESSIBILITY & MOTION") {
            GlassSwitchRow(
                title = "Reduce Animations",
                subtitle = "Replaces spring physics and slide animations with instant cuts",
                checked = reduceAnimation,
                onCheckedChange = { viewModel.setReduceAnimation(it) }
            )
            GlassSwitchRow(
                title = "Reduce Dynamic Blur",
                subtitle = "Replaces liquid glass backdrop blurs with solid surfaces",
                checked = reduceDynamicBlur,
                onCheckedChange = { viewModel.setReduceDynamicBlur(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
