package com.mue.feature.listen

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.mue.core.design.BrandDrawables
import com.mue.core.design.MuELogoWhite
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassAlertDialog
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.design.settingsRadioButtonColors
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

@Composable
fun ListenTogetherScreen(
    onOpenSettings: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    viewModel: ListenTogetherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val defaultMaxListeners by viewModel.defaultMaxListeners.collectAsStateWithLifecycle()
    val defaultDjMode by viewModel.defaultDjMode.collectAsStateWithLifecycle()
    val defaultRoomPrivacy by viewModel.defaultRoomPrivacy.collectAsStateWithLifecycle()
    val ltJoinViaLinkBehavior by viewModel.ltJoinViaLinkBehavior.collectAsStateWithLifecycle()

    val hazeState = LocalHazeState.current
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Intercept Android back gesture during Connecting or InRoom
    BackHandler(enabled = uiState is UiState.Connecting || uiState is UiState.InRoom) {
        if (uiState is UiState.Connecting) {
            viewModel.leaveRoom()
        } else if (uiState is UiState.InRoom) {
            showExitConfirmDialog = true
        }
    }

    if (showExitConfirmDialog && uiState is UiState.InRoom) {
        val inRoomState = uiState as UiState.InRoom
        GlassAlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text(
                    text = if (inRoomState.isHost) "End session for everyone?" else "Leave session?",
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (inRoomState.isHost) {
                        "All guests will be disconnected and the room will be closed."
                    } else {
                        "Playback will continue locally on your device."
                    },
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        if (inRoomState.isHost) {
                            viewModel.endSession()
                        } else {
                            viewModel.leaveRoom()
                        }
                    }
                ) {
                    Text(
                        text = if (inRoomState.isHost) "End Session" else "Leave",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }

    when (val state = uiState) {
        is UiState.Lobby -> {
            ListenTogetherLobbyContent(
                defaultMaxListeners = defaultMaxListeners,
                defaultDjMode = defaultDjMode,
                defaultRoomPrivacy = defaultRoomPrivacy,
                ltJoinViaLinkBehavior = ltJoinViaLinkBehavior,
                onCreateRoom = { max, dj, priv ->
                    viewModel.createRoom(
                        maxListeners = max,
                        djMode = dj,
                        roomPrivacy = priv
                    )
                },
                onJoinRoom = { code -> viewModel.joinRoom(code) },
                hazeState = hazeState
            )
        }
        is UiState.Connecting -> {
            ListenTogetherConnectingContent(
                state = state,
                onCancel = { viewModel.leaveRoom() },
                hazeState = hazeState
            )
        }
        is UiState.InRoom -> {
            val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()

            ListenTogetherInRoomContent(
                state = state,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                isMuted = isMuted,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipToNext = { viewModel.skipToNext() },
                onSkipToPrevious = { viewModel.skipToPrevious() },
                onToggleMute = { viewModel.toggleMute() },
                onLeaveRoom = { showExitConfirmDialog = true },
                onEndSession = { showExitConfirmDialog = true },
                onResync = { viewModel.resync() },
                onOpenSettings = onOpenSettings,
                hazeState = hazeState
            )
        }
        is UiState.Error -> {
            ListenTogetherErrorContent(
                message = state.message,
                onDismiss = { viewModel.leaveRoom() },
                hazeState = hazeState
            )
        }
    }
}

@Composable
private fun ListenTogetherLobbyContent(
    defaultMaxListeners: Int,
    defaultDjMode: String,
    defaultRoomPrivacy: String,
    ltJoinViaLinkBehavior: String,
    onCreateRoom: (max: Int, dj: String, priv: String) -> Unit,
    onJoinRoom: (code: String) -> Unit,
    hazeState: HazeState? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var roomCodeInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinConfirmDialog by remember { mutableStateOf(false) }
    var selectedMaxListeners by remember(defaultMaxListeners) { mutableIntStateOf(defaultMaxListeners) }
    var selectedDjMode by remember(defaultDjMode) { mutableStateOf(defaultDjMode) }
    var selectedPrivacy by remember(defaultRoomPrivacy) { mutableStateOf("Invite Link") }

    if (showCreateDialog) {
        GlassAlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text("Host a Session", color = PureWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Configure your Listen Together room parameters.",
                        color = MutedGrey,
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Max Listeners Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Max Listeners", color = PureWhite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Capacity limit (2–20)", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { if (selectedMaxListeners > 2) selectedMaxListeners-- },
                                enabled = selectedMaxListeners > 2,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = if (selectedMaxListeners > 2) PureWhite else MutedGrey.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                            Text(
                                "$selectedMaxListeners",
                                color = PureWhite,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = { if (selectedMaxListeners < 20) selectedMaxListeners++ },
                                enabled = selectedMaxListeners < 20,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = if (selectedMaxListeners < 20) PureWhite else MutedGrey.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // DJ Mode
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("DJ Mode", color = PureWhite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "DJ" to "DJ Mode (Host controls playback)",
                            "Collab" to "Collaborative (Anyone can add & skip)"
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedDjMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDjMode == mode,
                                    onClick = { selectedDjMode = mode },
                                    colors = settingsRadioButtonColors()
                                )
                                Text(label, color = PureWhite, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }

                    // Room Privacy
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Room Privacy", color = PureWhite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "Invite Link" to "Invite Only (Code required)"
                        ).forEach { (priv, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedPrivacy = priv }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPrivacy == priv,
                                    onClick = { selectedPrivacy = priv },
                                    colors = settingsRadioButtonColors()
                                )
                                Text(label, color = PureWhite, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateRoom(selectedMaxListeners, selectedDjMode, selectedPrivacy)
                        showCreateDialog = false
                    }
                ) {
                    Text("Create Room", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }

    if (showJoinConfirmDialog) {
        GlassAlertDialog(
            onDismissRequest = { showJoinConfirmDialog = false },
            title = {
                Text("Join Room", color = PureWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Join session room $roomCodeInput now?",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onJoinRoom(roomCodeInput)
                        roomCodeInput = ""
                        showJoinConfirmDialog = false
                    }
                ) {
                    Text("Join", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinConfirmDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Listen lobby logo (72 dp, 25% alpha)
        Image(
            painter = painterResource(id = BrandDrawables.lt),
            contentDescription = "Listen Together",
            modifier = Modifier
                .size(72.dp)
                .alpha(0.25f)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Listen Together",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                letterSpacing = (-0.5).sp
            ),
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Share audio synchronously with friends with zero latency.",
            color = MutedGrey,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Option 1: Host a Session
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Host a Session",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Generate a room code to stream your queue to peers.",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(18.dp))

                val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .applePressEffect()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isLight) Color(0xFF1C1C1E) else PureWhite)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCreateDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (isLight) PureWhite else PureBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Session",
                            color = if (isLight) PureWhite else PureBlack,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Option 2: Join a Session
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Join a Session",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter a 6-character room code from your friend.",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Liquid Glass Join Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlass(hazeState = hazeState, cornerRadius = 16.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = roomCodeInput,
                            onValueChange = {
                                val allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
                                val filtered = it.uppercase().replace(" ", "").replace("-", "").filter { c -> c in allowedChars }
                                if (filtered.length <= 6) {
                                    roomCodeInput = filtered
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (roomCodeInput.isEmpty()) {
                                    Text(
                                        text = "ENTER 6-CHARACTER CODE",
                                        color = MutedGrey.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Paste Action Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .liquidGlass(hazeState = hazeState, cornerRadius = 8.dp)
                                .applePressEffect()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
                                    val clipboardText = clipboardManager.getText()?.text?.trim()?.uppercase()
                                        ?.replace(" ", "")?.replace("-", "")?.filter { c -> c in allowedChars }?.take(6)
                                    if (!clipboardText.isNullOrBlank()) {
                                        if (ltJoinViaLinkBehavior == "Auto-join" && clipboardText.length == 6) {
                                            onJoinRoom(clipboardText)
                                            roomCodeInput = ""
                                        } else {
                                            roomCodeInput = clipboardText
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "PASTE",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val canJoin = roomCodeInput.length == 6
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .applePressEffect()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canJoin) PureWhite else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = canJoin) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (ltJoinViaLinkBehavior == "Ask first") {
                                showJoinConfirmDialog = true
                            } else {
                                onJoinRoom(roomCodeInput)
                                roomCodeInput = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Join Room",
                        color = if (canJoin) PureBlack else MutedGrey,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun ListenTogetherConnectingContent(
    state: UiState.Connecting,
    onCancel: () -> Unit,
    hazeState: HazeState? = null
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Cancel X button at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clip(CircleShape)
                .liquidGlass(hazeState = hazeState, cornerRadius = 22.dp)
                .applePressEffect()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancel()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = PureWhite,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6).copy(alpha = glowAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .liquidGlass(hazeState = hazeState, cornerRadius = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (state.isHost) "Creating secure room…" else "Connecting to host…",
                color = PureWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.roomCode.isNotBlank()) {
                Text(
                    text = "Room: ${state.roomCode}",
                    color = MutedGrey,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            CircularProgressIndicator(
                color = PureWhite,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .applePressEffect()
                    .clip(RoundedCornerShape(14.dp))
                    .liquidGlass(hazeState = hazeState, cornerRadius = 14.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    color = PureWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ListenTogetherInRoomContent(
    state: UiState.InRoom,
    currentTrack: com.mue.core.model.Track?,
    isPlaying: Boolean,
    isMuted: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onToggleMute: () -> Unit,
    onLeaveRoom: () -> Unit,
    onEndSession: () -> Unit,
    onResync: () -> Unit,
    onOpenSettings: () -> Unit,
    hazeState: HazeState? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    if (showQueueDialog) {
        GlassAlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = {
                Text("Room Queue", color = PureWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (currentTrack != null) "Currently broadcasting: ${currentTrack.title} — ${currentTrack.artist}" else "Queue is empty.",
                        color = MutedGrey,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("Close", color = PureWhite)
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Top Bar: Sync Status Pill + Settings Gear + Leave X Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val syncDotColor = when {
                    state.isHostDisconnected -> Color(0xFFEF4444)
                    state.syncStatus == SyncStatus.RECONNECTING -> Color(0xFFF59E0B)
                    state.syncStatus == SyncStatus.SYNCED -> Color(0xFF3B82F6) // Spec Blue #3B82F6
                    state.syncStatus == SyncStatus.BUFFERING -> Color(0xFFF59E0B) // Spec Yellow #F59E0B
                    state.syncStatus == SyncStatus.DRIFT -> Color(0xFFEF4444) // Spec Red #EF4444
                    else -> MutedGrey
                }

                val syncStatusText = when {
                    state.isHostDisconnected -> "Host disconnected • Ending in ${state.graceSecondsRemaining}s"
                    state.syncStatus == SyncStatus.RECONNECTING -> "Reconnecting…"
                    state.syncStatus == SyncStatus.SYNCED -> "Synced • <150ms"
                    state.syncStatus == SyncStatus.BUFFERING -> "Buffering…"
                    state.syncStatus == SyncStatus.DRIFT -> "Drift (${state.currentDriftMs}ms)"
                    else -> "Connecting…"
                }

                Box(
                    modifier = Modifier
                        .liquidGlass(hazeState = hazeState, cornerRadius = 14.dp)
                        .clickable(enabled = state.syncStatus == SyncStatus.DRIFT) { onResync() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(syncDotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = syncStatusText,
                            color = syncDotColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Room Settings",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .liquidGlass(hazeState = hazeState, cornerRadius = 18.dp)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (state.isHost) onEndSession() else onLeaveRoom()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Leave Session",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Room Code Display with Tap-To-Copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.roomCode,
                    color = PureWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .liquidGlass(hazeState = hazeState, cornerRadius = 12.dp)
                        .applePressEffect()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(state.roomCode))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copied = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = if (copied) Color(0xFF30D158) else PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copied) "Copied!" else "Copy",
                            color = if (copied) Color(0xFF30D158) else PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Participants Avatar Stack Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    val displayAvatars = minOf(state.participants.size, 5)
                    repeat(displayAvatars) { idx ->
                        val p = state.participants.getOrNull(idx)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (p?.isHost == true) Color(0xFF3B82F6) else DarkGrey)
                                .border(1.5.dp, PureBlack, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (state.isHost) "You (Host) • ${state.participants.size} in room" else "Guest • ${state.participants.size} in room",
                    color = MutedGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Artwork Container with Drift Overlay
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!currentTrack?.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = currentTrack?.artworkUrl,
                        contentDescription = "Track Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = PureWhite.copy(alpha = 0.5f),
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }

                // Drift Overlay: When drift > 150ms
                if (state.syncStatus == SyncStatus.DRIFT) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .liquidGlass(hazeState = hazeState, cornerRadius = 16.dp)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResync()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Out of sync? Tap to resync",
                                color = PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Track Title & Artist
            Text(
                text = currentTrack?.title ?: "No Track Playing",
                color = PureWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentTrack?.artist ?: "Host queue is empty",
                color = MutedGrey,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Differentiated Playback Controls (Host vs Guest)
            if (state.isHost) {
                // Host Full Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSkipToPrevious()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = PureWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = PureBlack,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSkipToNext()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = PureWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // End Session button (Host: Red text, borderWidth = 0)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .applePressEffect()
                        .clip(RoundedCornerShape(16.dp))
                        .liquidGlass(hazeState = hazeState, cornerRadius = 16.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndSession()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "End Session for Everyone",
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Guest Controls: Local Mute Toggle + Request Song Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Local Mute Toggle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .liquidGlass(hazeState = hazeState, cornerRadius = 16.dp)
                            .applePressEffect()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleMute()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMuted) "Muted" else "Mute Audio",
                                color = if (isMuted) Color(0xFFEF4444) else PureWhite,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Leave Room Button (Guest: Red text, borderWidth = 0)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .applePressEffect()
                        .clip(RoundedCornerShape(16.dp))
                        .liquidGlass(hazeState = hazeState, cornerRadius = 16.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLeaveRoom()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Leave Room",
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Dock Row (Queue)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glass Dock Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(hazeState = hazeState, cornerRadius = 24.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Queue Trigger
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .applePressEffect()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showQueueDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = PureWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ListenTogetherErrorContent(
    message: String,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .liquidGlass(hazeState = hazeState, cornerRadius = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Connection Failed",
                color = PureWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                color = MutedGrey,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .applePressEffect()
                    .clip(RoundedCornerShape(14.dp))
                    .liquidGlass(hazeState = hazeState, cornerRadius = 14.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Return to Lobby",
                    color = PureWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

