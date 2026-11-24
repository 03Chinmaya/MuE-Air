package com.mue.core.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SearchPlayingBars(
    modifier: Modifier = Modifier,
    barColor: Color = PureWhite,
    maxHeight: Dp = 16.dp,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val h1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.size(width = barWidth * 3 + spacing * 2, height = maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(maxHeight * h1)
                .clip(RoundedCornerShape(percent = 50))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(maxHeight * h2)
                .clip(RoundedCornerShape(percent = 50))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(maxHeight * h3)
                .clip(RoundedCornerShape(percent = 50))
                .background(barColor)
        )
    }
}

