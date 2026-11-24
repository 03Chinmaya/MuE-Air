@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mue.feature.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureWhite
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.liquidGlass

data class AudioOutputItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val device: AudioDeviceInfo?,
    val isActive: Boolean
)

@Composable
fun AudioOutputSheet(
    currentRoute: String,
    onSelectDevice: (AudioDeviceInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioOutputs = remember(currentRoute) {
        val items = mutableListOf<AudioOutputItem>()
        val isPhoneActive = currentRoute.equals("This phone", ignoreCase = true)
        
        items.add(
            AudioOutputItem(
                id = "builtin_speaker",
                name = "This phone",
                description = "Internal speaker",
                icon = Icons.Default.PhoneAndroid,
                device = null,
                isActive = isPhoneActive
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()

            for (device in devices) {
                if (!device.isSink) continue
                when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                    AudioDeviceInfo.TYPE_TELEPHONY -> {
                        // Handled by "This phone"
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                        val name = device.productName?.toString()?.trim().let {
                            if (!it.isNullOrBlank()) it else "Bluetooth"
                        }
                        val isActive = currentRoute.equals(name, ignoreCase = true) || 
                            (!isPhoneActive && currentRoute.contains("Bluetooth", ignoreCase = true))
                        items.add(
                            AudioOutputItem(
                                id = "bt_${device.id}",
                                name = name,
                                description = "Bluetooth Audio",
                                icon = Icons.Default.Bluetooth,
                                device = device,
                                isActive = isActive
                            )
                        )
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                        val name = "Wired Headphones"
                        items.add(
                            AudioOutputItem(
                                id = "wired_${device.id}",
                                name = name,
                                description = "3.5mm Headphone Jack",
                                icon = Icons.Default.Headphones,
                                device = device,
                                isActive = currentRoute.equals(name, ignoreCase = true)
                            )
                        )
                    }
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        val name = device.productName?.toString()?.trim().let {
                            if (!it.isNullOrBlank()) it else "USB Audio"
                        }
                        items.add(
                            AudioOutputItem(
                                id = "usb_${device.id}",
                                name = name,
                                description = "USB Audio Device",
                                icon = Icons.Default.Headphones,
                                device = device,
                                isActive = currentRoute.equals("USB Audio", ignoreCase = true) || currentRoute.equals(name, ignoreCase = true)
                            )
                        )
                    }
                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                        val name = "External Audio"
                        items.add(
                            AudioOutputItem(
                                id = "ext_${device.id}",
                                name = name,
                                description = "Line out / HDMI",
                                icon = Icons.Default.Speaker,
                                device = device,
                                isActive = currentRoute.equals(name, ignoreCase = true)
                            )
                        )
                    }
                }
            }
        }
        items
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .liquidGlass(LocalHazeState.current, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Audio Output",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Text(
                            text = "Choose where to play audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureWhite.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Output Device List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    audioOutputs.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (item.isActive) SystemAccentBlue.copy(alpha = 0.15f)
                                    else Color(0xFF1C1C1E).copy(alpha = 0.5f)
                                )
                                .clickable {
                                    onSelectDevice(item.device)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item.isActive) SystemAccentBlue.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.name,
                                    tint = if (item.isActive) SystemAccentBlue else PureWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (item.isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (item.isActive) PureWhite else PureWhite.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isActive) SystemAccentBlue else MutedGrey,
                                    fontSize = 12.sp
                                )
                            }

                            if (item.isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = SystemAccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pair / Open Bluetooth Settings Tile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Bluetooth Settings",
                        tint = MutedGrey,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Bluetooth Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGrey
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

