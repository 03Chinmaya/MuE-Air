package com.mue.feature.settings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.common.ApkDownloader
import com.mue.core.design.AccentRed
import com.mue.core.design.DarkGrey
import com.mue.core.design.MuELogo
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import com.mue.core.design.UpdateDialog
import com.mue.core.design.applePressEffect

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()
    val releaseChannel by viewModel.releaseChannel.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()

    val clientVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    LaunchedEffect(updateCheckState) {
        when (val state = updateCheckState) {
            is SettingsViewModel.UpdateCheckState.UpToDate -> {
                Toast.makeText(context, "You are using the latest version of MuE-Air", Toast.LENGTH_SHORT).show()
            }
            is SettingsViewModel.UpdateCheckState.Error -> {
                Toast.makeText(context, "Update check: ${state.message}", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    // Update Pop-up Dialog
    if (updateCheckState is SettingsViewModel.UpdateCheckState.UpdateAvailable) {
        val info = (updateCheckState as SettingsViewModel.UpdateCheckState.UpdateAvailable).info
        UpdateDialog(
            version = info.version,
            changelog = info.changelog,
            onUpdate = {
                viewModel.dismissUpdateDialog()
                Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_SHORT).show()
                ApkDownloader.downloadAndInstall(context, info.downloadUrl)
            },
            onDoItLater = { viewModel.dismissUpdateDialog() },
            onDismissRequest = { viewModel.dismissUpdateDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Header
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
                text = "About",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // App Branding Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MuELogo(symbolSize = 96.dp)
            }

            // Version Information Section
            SettingsSection("APPLICATION VERSION") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Client Version", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        Text("Android Application Package", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "v$clientVersion",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Server Version", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        Text("Cloud API Gateway", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "v$serverVersion",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .applePressEffect()
                        .clickable {
                            if (updateCheckState !is SettingsViewModel.UpdateCheckState.Checking) {
                                viewModel.checkForUpdates(clientVersion)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for Updates", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        Text("Query latest releases & OTA APK", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    if (updateCheckState is SettingsViewModel.UpdateCheckState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Check Now",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Release Channel Section
            SettingsSection("RELEASE CHANNEL") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .applePressEffect()
                        .clickable { viewModel.setReleaseChannel("Stable") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (releaseChannel == "Stable") Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Stable",
                        tint = if (releaseChannel == "Stable") MaterialTheme.colorScheme.primary else MutedGrey
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stable Channel", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        Text("Production releases tested for stability and performance", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

