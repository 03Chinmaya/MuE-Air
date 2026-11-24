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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.MuESymbol
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
private val VerifiedGreen = Color(0xFF1DB954)

@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val isGuest by viewModel.isGuest.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val guestId by viewModel.guestId.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val userBio by viewModel.userBio.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var editDialogTarget by remember { mutableStateOf<String?>(null) }
    var editFieldValue by remember { mutableStateOf("") }

    val effectiveDisplayName = if (isGuest && displayName == "MuE Listener") "Guest User" else displayName
    val effectiveUsername = if (isGuest) {
        "@" + (guestId?.take(10) ?: "guest_session")
    } else {
        "@" + (userId?.take(12) ?: "member")
    }

    // Edit Field Dialog
    if (editDialogTarget != null) {
        val title = when (editDialogTarget) {
            "displayName" -> "Edit Display Name"
            "bio" -> "Edit Bio"
            else -> "Edit Profile"
        }
        GlassAlertDialog(
            onDismissRequest = { editDialogTarget = null },
            title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editFieldValue,
                    onValueChange = { editFieldValue = it },
                    singleLine = editDialogTarget != "bio",
                    maxLines = if (editDialogTarget == "bio") 3 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MutedGrey,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (editDialogTarget) {
                            "displayName" -> viewModel.updateDisplayName(editFieldValue)
                            "bio" -> viewModel.updateUserBio(editFieldValue)
                        }
                        editDialogTarget = null
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        GlassAlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to log out? Your local playback cache and preferences will be preserved, but online library sync will pause.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout {
                            onBack()
                        }
                    }
                ) {
                    Text("Log Out", color = WarningOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Delete Account Dialog
    if (showDeleteDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account (GDPR)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This action is permanent and cannot be undone. All your synced playlists, playback history, and server credentials will be permanently erased in accordance with GDPR regulations.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount {
                            onBack()
                        }
                    }
                ) {
                    Text("Delete Permanently", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Restore Identity Dialog (BIP39)
    if (showRestoreDialog) {
        RestoreIdentityDialog(
            onDismiss = { showRestoreDialog = false },
            onRestore = { mnemonic, callback ->
                viewModel.restoreIdentity(mnemonic, callback)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        // Top Navigation Bar
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
                text = "Account & Profile",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onNavigate("settings/root") }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Global Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // User Identity Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .liquidGlass(cornerRadius = 24.dp)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo Plate Header (64 dp)
                MuESymbol(size = 64.dp)

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = effectiveDisplayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = effectiveUsername,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGrey
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isGuest) Color(0xFF242424) else VerifiedGreen.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isGuest) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VerifiedGreen,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (isGuest) "Guest Session" else "Verified Account",
                            color = if (isGuest) MutedGrey else VerifiedGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile Section
        SettingsSection("EDIT PROFILE") {
            ProfileInteractiveRow(
                icon = Icons.Default.Badge,
                title = "Display Name",
                subtitle = effectiveDisplayName,
                onClick = {
                    editFieldValue = effectiveDisplayName
                    editDialogTarget = "displayName"
                }
            )
            ProfileInteractiveRow(
                icon = Icons.Default.AlternateEmail,
                title = "Username",
                subtitle = effectiveUsername,
                trailingText = if (isGuest) "Auto-assigned" else "Fixed",
                onClick = {}
            )
            ProfileInteractiveRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = "Bio",
                subtitle = userBio,
                onClick = {
                    editFieldValue = userBio
                    editDialogTarget = "bio"
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Account Actions Section
        SettingsSection("ACCOUNT ACTIONS") {
            if (isGuest) {
                ProfileInteractiveRow(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Sign In or Register",
                    subtitle = "Link an account to sync library across devices",
                    trailingText = "Sign In",
                    onClick = { onNavigate("auth") }
                )
            }
            ProfileInteractiveRow(
                icon = Icons.Default.VpnKey,
                title = "Restore Identity (BIP39)",
                subtitle = "Recover profile & library using a 12 or 24-word seed phrase",
                onClick = { showRestoreDialog = true }
            )
            ProfileInteractiveRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Log Out",
                subtitle = "Sign out of your active session on this device",
                accentColor = WarningOrange,
                onClick = { showLogoutDialog = true }
            )
            ProfileInteractiveRow(
                icon = Icons.Default.DeleteForever,
                title = "Delete Account",
                subtitle = "Permanently wipe account and personal data (GDPR)",
                accentColor = ErrorRed,
                onClick = { showDeleteDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun ProfileInteractiveRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = MutedGrey,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MutedGrey
        )
    }
}

@Composable
private fun RestoreIdentityDialog(
    onDismiss: () -> Unit,
    onRestore: (String, (Result<Unit>) -> Unit) -> Unit
) {
    var mnemonicInput by remember { mutableStateOf("") }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = {
            if (!isRestoring) {
                onDismiss()
            }
        },
        title = { Text("Restore Identity (BIP39)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Warning Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(WarningOrange.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Ensure you are in a safe, private environment before entering your seed phrase.",
                            color = WarningOrange,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "Enter your 12 or 24-word recovery phrase separated by single spaces.",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = mnemonicInput,
                    onValueChange = {
                        mnemonicInput = it
                        restoreErrorMessage = null
                    },
                    placeholder = {
                        Text("word1 word2 word3 ...", color = MutedGrey.copy(alpha = 0.5f))
                    },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isRestoring,
                    isError = restoreErrorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MutedGrey,
                        errorBorderColor = ErrorRed,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (restoreErrorMessage != null) {
                    Text(
                        text = restoreErrorMessage.orEmpty(),
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isRestoring && mnemonicInput.isNotBlank(),
                onClick = {
                    val words = mnemonicInput.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (words.size != 12 && words.size != 24) {
                        restoreErrorMessage = "Mnemonic must contain exactly 12 or 24 words (currently ${words.size})"
                        return@TextButton
                    }
                    val hasInvalidChars = words.any { !it.all { char -> char.isLetter() } }
                    if (hasInvalidChars) {
                        restoreErrorMessage = "Mnemonic phrase must only contain alphabetic words"
                        return@TextButton
                    }
                    isRestoring = true
                    restoreErrorMessage = null
                    onRestore(mnemonicInput.trim()) { result ->
                        isRestoring = false
                        result.onSuccess {
                            onDismiss()
                        }.onFailure { err ->
                            restoreErrorMessage = err.message ?: "Failed to restore identity"
                        }
                    }
                }
            ) {
                Text(
                    text = if (isRestoring) "Restoring..." else "Restore",
                    color = if (!isRestoring && mnemonicInput.isNotBlank()) MaterialTheme.colorScheme.primary else MutedGrey,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isRestoring,
                onClick = onDismiss
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
