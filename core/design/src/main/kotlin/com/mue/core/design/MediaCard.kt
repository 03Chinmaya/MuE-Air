package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Exact BitChord / Apple Music MediaCard layout:
 * - 150dp width
 * - 1:1 aspect ratio with 12dp rounded corners and 1dp 0.15f white hairline border
 * - Optional glass pill badge (e.g., "EP", "SINGLE") in top-end corner
 * - 10dp spacer
 * - titleMedium title and bodyMedium subtitle
 */
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    badge: String? = null
) {
    val cardShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .width(150.dp)
            .applePressEffect()
            .clickable { onClick() }
    ) {
        val sharedModifier = if (sharedKey != null) Modifier.sharedArtworkElement(sharedKey) else Modifier
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .size(300)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .thumbnailBorder(cardShape)
                    .background(DarkGrey)
                    .then(sharedModifier)
            )

            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .liquidGlass(shape = RoundedCornerShape(6.dp))
                        .background(PureBlack.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = PureWhite
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
