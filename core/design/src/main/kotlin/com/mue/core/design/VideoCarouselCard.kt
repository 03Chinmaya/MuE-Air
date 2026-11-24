package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale

/**
 * Compact 160dp 16:9 video carousel preview card with duration badge,
 * 2-line title, and optional channel name subtitle.
 */
@Composable
fun VideoCarouselCard(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    durationSeconds: Int? = null,
    durationMs: Long = 0L
) {
    val durationText = formatVideoDurationHelper(durationMs, durationSeconds)

    Column(
        modifier = modifier
            .width(width)
            .applePressEffect()
            .clickable(onClick = onClick)
    ) {
        // Top: 16:9 Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkGrey)
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .scale(Scale.FILL)
                        .size(240, 135)
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Duration badge in bottom-right
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

        // Bottom: 2-line title
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PureWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Subtitle (channel name) only if not blank
        if (channelName.isNotBlank()) {
            Text(
                text = channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
