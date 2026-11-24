package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState

const val HERO_CARD_ASPECT = 16f / 9f

/**
 * Large 16:9 full-bleed Hero card matching BitChord / Apple Music Listen Now hero banners.
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .aspectRatio(HERO_CARD_ASPECT)
            .clip(heroShape)
            .thumbnailBorder(heroShape)
            .background(DarkGrey)
            .applePressEffect()
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = artworkUrl ?: "",
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Library Hero Card tile (Liked, Downloads, History).
 * Features liquid glass, large icon, count text, stacked-artwork preview, and zero borders.
 */
@Composable
fun HeroCard(
    title: String,
    count: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkPreviews: List<String> = emptyList(),
    hazeState: HazeState? = null
) {
    val heroShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .liquidGlass(hazeState = hazeState, shape = heroShape)
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = PureWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (artworkPreviews.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-10).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        artworkPreviews.take(3).forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkGrey)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


