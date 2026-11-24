package com.mue.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassAlertDialog
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass

private val WarningOrange = Color(0xFFFFA726)
private val ErrorRed = Color(0xFFE53935)

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    confirmColor: Color = MaterialTheme.colorScheme.primary,
    dismissText: String = "Cancel",
    requireVerificationText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var verificationInput by remember { mutableStateOf("") }
    val isConfirmEnabled = requireVerificationText == null ||
        verificationInput.trim().equals(requireVerificationText, ignoreCase = false)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (requireVerificationText != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Type \"$requireVerificationText\" to confirm:",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = verificationInput,
                        onValueChange = { verificationInput = it },
                        singleLine = true,
                        placeholder = { Text(requireVerificationText, color = MutedGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = confirmColor,
                            unfocusedBorderColor = MutedGrey,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isConfirmEnabled
            ) {
                Text(
                    confirmText,
                    color = if (isConfirmEnabled) confirmColor else MutedGrey,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DangerZoneScreen(
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var activeDialog by remember { mutableStateOf<String?>(null) }

    when (activeDialog) {
        "clear_local" -> {
            ConfirmationDialog(
                title = "Clear Local Data",
                message = "Are you sure you want to wipe all local data? Your offline tracks, play history, and cached songs will be erased, but your cloud sync and account will stay active.",
                confirmText = "Clear All Data",
                confirmColor = WarningOrange,
                onConfirm = {
                    activeDialog = null
                    viewModel.clearLocalData {
                        Toast.makeText(context, "Local data cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
        "reset_settings" -> {
            ConfirmationDialog(
                title = "Reset Settings",
                message = "Are you sure you want to reset all preferences to their default values? Your account, login, and playlists will not be affected.",
                confirmText = "Reset Settings",
                confirmColor = WarningOrange,
                onConfirm = {
                    activeDialog = null
                    viewModel.resetSettings {
                        Toast.makeText(context, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Danger Zone",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Warning Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ErrorRed.copy(alpha = 0.12f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Actions here perform destructive operations on local and cloud data. Please proceed with caution.",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Local Storage & Preferences
        SettingsSection("LOCAL STORAGE & PREFERENCES") {
            DangerActionRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Local Data",
                subtitle = "Wipes offline cache, local history, and room database. You will remain logged in.",
                accentColor = WarningOrange,
                onClick = { activeDialog = "clear_local" }
            )
            DangerActionRow(
                icon = Icons.Default.Refresh,
                title = "Reset Settings to Defaults",
                subtitle = "Restores playback, appearance, and audio preferences to factory defaults.",
                accentColor = WarningOrange,
                onClick = { activeDialog = "reset_settings" }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun DangerActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accentColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MutedGrey,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MutedGrey
        )
    }
}

