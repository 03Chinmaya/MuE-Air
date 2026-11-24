package com.mue.core.design

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale

internal fun formatVideoDurationHelper(durationMs: Long, durationSeconds: Int?): String {
    val totalSec = when {
        durationSeconds != null && durationSeconds > 0 -> durationSeconds.toLong()
        durationMs > 0L -> durationMs / 1000L
        else -> return ""
    }
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Horizontal video list item row with 16:9 thumbnail on the left, title/channel in the middle,
 * optional more action on the right, and an indented 0.5dp divider.
 */
@Composable
fun VideoListRow(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    durationSeconds: Int? = null,
    durationMs: Long = 0L,
    onMoreClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    val durationText = formatVideoDurationHelper(durationMs, durationSeconds)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .applePressEffect()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 16:9 Thumbnail Box (96x54 dp)
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkGrey)
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .scale(Scale.FILL)
                            .size(192, 108)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = SfIcons.Play,
                            contentDescription = null,
                            tint = PureWhite.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Duration badge overlay in bottom-right
                if (durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Middle: Title & Channel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channelName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Optional More Menu Button
            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MutedGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Indented Divider: 16dp pad + 96dp thumb + 12dp gap = 124dp
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 124.dp)
            )
        }
    }
}
