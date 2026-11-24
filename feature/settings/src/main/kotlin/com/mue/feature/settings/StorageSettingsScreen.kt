package com.mue.feature.settings

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassSettingsRow
import com.mue.core.design.GlassSwitchRow
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SettingsSection
import java.util.Locale

private val CacheGreen = Color(0xFF1DB954)
private val DownloadsBlue = Color(0xFF29B6F6)
private val OtherGrey = Color(0xFF555555)
private val WarningOrange = Color(0xFFFFA726)
private val ErrorRed = Color(0xFFE53935)

@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val cacheLimitMb by viewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val audioCacheFormatted by viewModel.audioCacheSizeFormatted.collectAsStateWithLifecycle()
    val downloadsFormatted by viewModel.downloadsSizeFormatted.collectAsStateWithLifecycle()
    val audioCacheBytes by viewModel.audioCacheBytes.collectAsStateWithLifecycle()
    val downloadsBytes by viewModel.downloadsBytes.collectAsStateWithLifecycle()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsStateWithLifecycle()
    val preferMusicOnly by viewModel.preferMusicOnly.collectAsStateWithLifecycle()

    // Detect device available storage using StatFs with safety buffer
    val (minLimitMb, maxLimitMb) = remember {
        val stat = StatFs(context.cacheDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableMb = (availableBytes / (1024L * 1024L)).toInt()
        val maxBound = (availableMb - 500).coerceAtLeast(256)
        100 to maxBound
    }

    var sliderValue by remember(cacheLimitMb) {
        mutableFloatStateOf(cacheLimitMb.coerceIn(minLimitMb, maxLimitMb).toFloat())
    }

    var activeDialog by remember { mutableStateOf<String?>(null) }

    when (activeDialog) {
        "clear_cache" -> {
            ConfirmationDialog(
                title = "Clear Song Cache",
                message = "Are you sure you want to clear cached audio tracks ($audioCacheFormatted)? Your offline downloaded tracks will be kept.",
                confirmText = "Clear Cache",
                confirmColor = WarningOrange,
                onConfirm = {
                    activeDialog = null
                    viewModel.clearAudioCache {
                        Toast.makeText(context, "Song cache cleared", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
        "clear_downloads" -> {
            ConfirmationDialog(
                title = "Clear All Downloads",
                message = "Are you sure you want to delete all downloaded songs ($downloadsFormatted)? You will need an active connection to play them again.",
                confirmText = "Delete Downloads",
                confirmColor = ErrorRed,
                onConfirm = {
                    activeDialog = null
                    viewModel.clearDownloads {
                        Toast.makeText(context, "Downloads removed", Toast.LENGTH_SHORT).show()
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
                text = "Storage & Downloads",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Storage Usage Breakdown
        SettingsSection("STORAGE BREAKDOWN") {
            Column(modifier = Modifier.padding(16.dp)) {
                // Segmented visual bar
                val totalBytes = (audioCacheBytes + downloadsBytes).coerceAtLeast(1L)
                val cacheWeight = (audioCacheBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.95f)
                val downloadsWeight = 1f - cacheWeight

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(OtherGrey)
                ) {
                    if (audioCacheBytes > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(cacheWeight)
                                .background(CacheGreen)
                        )
                    }
                    if (downloadsBytes > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(downloadsWeight)
                                .background(DownloadsBlue)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(CacheGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio Cache • $audioCacheFormatted",
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(DownloadsBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Downloads • $downloadsFormatted",
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(OtherGrey)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Other",
                            color = MutedGrey,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Cache Limit Slider
        SettingsSection("AUDIO CACHE LIMIT") {
            Column(modifier = Modifier.padding(16.dp)) {
                val formattedLimit = if (sliderValue >= 1024f) {
                    String.format(Locale.US, "%.1f GB", sliderValue / 1024f)
                } else {
                    "${sliderValue.toInt()} MB"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Maximum Cache Size",
                        color = PureWhite,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formattedLimit,
                        color = PureWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.setCacheLimitMb(sliderValue.toInt())
                    },
                    valueRange = minLimitMb.toFloat()..maxLimitMb.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = PureWhite,
                        activeTrackColor = PureWhite,
                        inactiveTrackColor = DarkGrey
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Automatically purges least recently played tracks when the cached audio exceeds this limit.",
                    color = MutedGrey,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Download Preferences
        SettingsSection("DOWNLOAD PREFERENCES") {
            GlassSwitchRow(
                title = "Download over Wi-Fi only",
                subtitle = "Save mobile data for offline tracks",
                checked = downloadWifiOnly,
                onCheckedChange = { viewModel.setDownloadWifiOnly(it) }
            )
            GlassSwitchRow(
                title = "Prefer music over video",
                subtitle = "Uses audio-only formats when available",
                checked = preferMusicOnly,
                onCheckedChange = { viewModel.setPreferMusicOnly(it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Storage Actions
        SettingsSection("STORAGE ACTIONS") {
            GlassSettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Song Cache",
                subtitle = "Frees space used by cached stream audio",
                trailingText = audioCacheFormatted,
                onClick = { activeDialog = "clear_cache" }
            )
            GlassSettingsRow(
                icon = Icons.Default.DeleteForever,
                title = "Clear All Downloads",
                subtitle = "Deletes all downloaded offline songs",
                trailingText = downloadsFormatted,
                onClick = { activeDialog = "clear_downloads" }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

