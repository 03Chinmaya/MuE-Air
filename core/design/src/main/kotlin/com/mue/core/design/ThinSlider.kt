package com.mue.core.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom BitChord ThinSlider with hairline track and smooth scrubbing physics.
 * Replaces heavy Material 3 slider with an ultra-responsive, borderless design.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    activeColor: Color = PureWhite,
    inactiveColor: Color = PureWhite.copy(alpha = 0.22f),
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 6.dp
) {
    var isDragging by remember { mutableStateOf(false) }
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) trackHeight * 1.5f else trackHeight,
        animationSpec = tween(150),
        label = "ThinSliderHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedTrackHeight)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val width = size.width.toFloat()
                            if (width > 0) {
                                val fraction = (offset.x / width).coerceIn(0f, 1f)
                                onValueChange(fraction)
                                isDragging = true
                                val released = tryAwaitRelease()
                                if (released) {
                                    isDragging = false
                                    onValueChangeFinished()
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val width = size.width.toFloat()
                            if (width > 0) {
                                onValueChange((offset.x / width).coerceIn(0f, 1f))
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            onValueChangeFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            onValueChangeFinished()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            if (width > 0) {
                                val fraction = (change.position.x / width).coerceIn(0f, 1f)
                                onValueChange(fraction)
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val activeWidth = width * value.coerceIn(0f, 1f)
            val cornerRadius = CornerRadius(height / 2f, height / 2f)

            // Inactive track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, 0f),
                size = Size(width, height),
                cornerRadius = cornerRadius
            )

            // Active track
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, height),
                    cornerRadius = cornerRadius
                )
            }

            // Scrubber Thumb
            val thumbPx = thumbRadius.toPx() * (if (isDragging) 1.25f else 1.0f)
            drawCircle(
                color = activeColor,
                radius = thumbPx,
                center = Offset(activeWidth.coerceIn(thumbPx, width - thumbPx), height / 2f)
            )
        }
    }
}

