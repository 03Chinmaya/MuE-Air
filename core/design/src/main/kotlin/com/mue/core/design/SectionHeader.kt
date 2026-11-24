package com.mue.core.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp
            )
        )
        if (onSeeAllClick != null) {
            Text(
                text = "See All",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable { onSeeAllClick() }
            )
        }
    }
}

