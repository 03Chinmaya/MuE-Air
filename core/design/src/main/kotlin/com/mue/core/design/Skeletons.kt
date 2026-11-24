package com.mue.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SHIMMER_PERIOD_MS = 1400
private val DefaultBlockShape = RoundedCornerShape(8.dp)
private val LineShape = RoundedCornerShape(4.dp)

private val ShimmerBase = Color(0xFF1C1C1E)
private val ShimmerHighlight = Color(0xFF2C2C2E)

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultBlockShape
) {
    val transition = rememberInfiniteTransition(label = "skeleton_sweep")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_PERIOD_MS, easing = LinearEasing)
        ),
        label = "sweep_progress"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val band = size.width * 0.6f
                val startX = -band + sweep.value * (size.width + band * 2)
                val brush = Brush.horizontalGradient(
                    colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
                    startX = startX,
                    endX = startX + band
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
    )
}

@Composable
fun SkeletonLine(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(height),
        shape = LineShape
    )
}

@Composable
fun SongRowSkeleton(
    modifier: Modifier = Modifier,
    circular: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(52.dp),
            shape = if (circular) CircleShape else RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonLine(fraction = 0.68f, height = 14.dp)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonLine(fraction = 0.46f, height = 11.dp)
        }
    }
}

@Composable
fun SongListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
    circular: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) {
            SongRowSkeleton(circular = circular)
        }
    }
}

