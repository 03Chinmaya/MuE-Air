package com.mue.feature.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassAlertDialog
import com.mue.core.design.GlassSwitchRow
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import com.mue.core.design.applePressEffect

@Composable
fun GlobalSettingsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
    val isGuest by viewModel.isGuest.collectAsStateWithLifecycle()
    val settingsSyncEnabled by viewModel.settingsSyncEnabled.collectAsStateWithLifecycle()

    var showGuestDialogFor by remember { mutableStateOf<String?>(null) }

    if (showGuestDialogFor != null) {
        GlassAlertDialog(
            onDismissRequest = { showGuestDialogFor = null },
            title = {
                Text("Sign In Required", color = PureWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Sign in to manage $showGuestDialogFor. Would you like to sign in now?",
                    color = MutedGrey
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGuestDialogFor = null
                        onNavigate("auth")
                    }
                ) {
                    Text("Sign In", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDialogFor = null }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
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
                text = "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                )
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Search settings", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                val matchesSync = "settings sync".contains(searchQuery.trim(), ignoreCase = true)
                // Flat filtered results list
                if (filteredItems.isEmpty() && !matchesSync) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No settings matching \"$searchQuery\"",
                                color = MutedGrey,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "SEARCH RESULTS (${filteredItems.size + if (matchesSync) 1 else 0})",
                            color = MutedGrey,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    if (matchesSync) {
                        item(key = "search_settings_sync") {
                            SettingsSection("CLOUD SYNC") {
                                GlassSwitchRow(
                                    title = "Settings Sync",
                                    subtitle = if (isGuest) "Sign in to sync preferences across devices" else "Automatically sync preferences across your devices",
                                    checked = if (isGuest) false else settingsSyncEnabled,
                                    onCheckedChange = {
                                        if (isGuest) {
                                            showGuestDialogFor = "Settings Sync"
                                        } else {
                                            viewModel.setSettingsSyncEnabled(it)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    items(filteredItems, key = { it.route }) { item ->
                        SettingRow(
                            item = item,
                            isGuest = isGuest,
                            onClick = {
                                if (isGuest && item.isOnlineOnly) {
                                    showGuestDialogFor = item.title
                                } else {
                                    onNavigate(item.route)
                                }
                            }
                        )
                    }
                }
            } else {
                // Cloud Sync Section
                item(key = "cloud_sync_section") {
                    SettingsSection("CLOUD SYNC") {
                        GlassSwitchRow(
                            title = "Settings Sync",
                            subtitle = if (isGuest) "Sign in to sync preferences across devices" else "Automatically sync preferences across your devices",
                            checked = if (isGuest) false else settingsSyncEnabled,
                            onCheckedChange = {
                                if (isGuest) {
                                    showGuestDialogFor = "Settings Sync"
                                } else {
                                    viewModel.setSettingsSyncEnabled(it)
                                }
                            }
                        )
                    }
                }

                // Grouped sections list
                val grouped = filteredItems.groupBy { it.section }
                grouped.forEach { (sectionTitle, items) ->
                    item(key = sectionTitle) {
                        SettingsSection(sectionTitle) {
                            items.forEach { item ->
                                SettingRow(
                                    item = item,
                                    isGuest = isGuest,
                                    onClick = {
                                        if (isGuest && item.isOnlineOnly) {
                                            showGuestDialogFor = item.title
                                        } else {
                                            onNavigate(item.route)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    item: SettingItem,
    isGuest: Boolean,
    onClick: () -> Unit
) {
    val isLocked = isGuest && item.isOnlineOnly
    val rowAlpha = if (isLocked) 0.4f else 1.0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (isLocked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = MutedGrey
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MutedGrey
            )
        }
    }
}

