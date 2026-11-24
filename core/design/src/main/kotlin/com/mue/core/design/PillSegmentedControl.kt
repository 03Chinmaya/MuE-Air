package com.mue.core.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val TrackShape = RoundedCornerShape(percent = 50)
private val PillShape = RoundedCornerShape(percent = 50)

private val TrackColor = Color(0xFF1C1C1E)
private val PillColor = Color.White.copy(alpha = 0.16f)

private val TrackPadding = 3.dp
private val LabelHorizontalPadding = 16.dp
private val LabelVerticalPadding = 7.dp

@Composable
fun PillSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(TrackShape)
            .background(TrackColor)
    ) {
        val insetPx = with(density) { TrackPadding.toPx() }
        val labelPaddingPx = with(density) { LabelHorizontalPadding.toPx() }
        val viewportWidth = constraints.maxWidth.toFloat()
        val room = viewportWidth - insetPx * 2f

        val naturalWidths = remember(options, textStyle, measurer, density) {
            options.map { label ->
                measurer.measure(label, textStyle, maxLines = 1, softWrap = false).size.width + labelPaddingPx * 2f
            }
        }
        val neededWidth = naturalWidths.sum()
        val scrolls = neededWidth > room

        val widths = when {
            scrolls -> naturalWidths
            naturalWidths.maxOrNull()?.let { it * options.size <= room } == true -> List(options.size) { room / options.size }
            else -> naturalWidths.map { it + (room - neededWidth) / options.size }
        }
        val starts = widths.runningFold(insetPx) { start, width -> start + width }
        val contentWidthPx = if (scrolls) neededWidth + insetPx * 2f else viewportWidth

        val safeSelected = selectedIndex.coerceIn(0, options.lastIndex)
        val targetStart = starts[safeSelected]
        val targetWidth = widths[safeSelected]

        val pillStart by animateFloatAsState(
            targetValue = targetStart,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "pill_start"
        )
        val pillWidth by animateFloatAsState(
            targetValue = targetWidth,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "pill_width"
        )

        val scrollState = rememberScrollState()

        LaunchedEffect(safeSelected, scrolls) {
            if (scrolls) {
                val targetCenter = starts[safeSelected] + widths[safeSelected] / 2f - viewportWidth / 2f
                val clamped = targetCenter.roundToInt().coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(clamped)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrolls) Modifier.horizontalScroll(scrollState) else Modifier)
        ) {
            Box(
                modifier = Modifier
                    .width(with(density) { contentWidthPx.toDp() })
                    .padding(vertical = TrackPadding)
            ) {
                // Sliding indicator
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val w = pillWidth.roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(pillStart.roundToInt(), 0)
                            }
                        }
                        .clip(PillShape)
                        .background(PillColor)
                )

                // Option labels
                options.forEachIndexed { index, label ->
                    val isSelected = index == safeSelected
                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.place(IntOffset(starts[index].roundToInt(), 0))
                                }
                            }
                            .width(with(density) { widths[index].toDp() })
                            .clip(PillShape)
                            .clickable { onSelect(index) }
                            .padding(vertical = LabelVerticalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = textStyle,
                            color = if (isSelected) PureWhite else MutedGrey,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

