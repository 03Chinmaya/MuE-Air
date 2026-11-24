package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

@Composable
fun SettingsSection(
    title: String,
    hazeState: HazeState? = LocalHazeState.current,
    content: @Composable () -> Unit
) {
    val sectionShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, shape = sectionShape)
                .padding(vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun GlassSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .applePressEffect()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trailingText != null) {
            Text(trailingText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PureWhite,
    checkedTrackColor = SystemAccentBlue,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
fun settingsRadioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = SystemAccentBlue,
    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun GlassSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = settingsSwitchColors()
        )
    }
}

