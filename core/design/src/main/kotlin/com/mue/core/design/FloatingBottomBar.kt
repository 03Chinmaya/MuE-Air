package com.mue.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlin.math.abs
import kotlin.math.roundToInt

data class BottomBarTab(
    val route: String,
    val icon: ImageVector? = null,
    val iconPainter: Painter? = null,
    @DrawableRes val iconResId: Int? = null,
    val label: String
) {
    constructor(route: String, icon: ImageVector, label: String) : this(
        route = route,
        icon = icon,
        iconPainter = null,
        iconResId = null,
        label = label
    )
}

internal val NAV_BAR_GUTTER = 24.dp
internal val NAV_BAR_LIFT = 4.dp
private val GESTURE_BAR_FLOOR = 12.dp
internal val TAB_VERTICAL_PADDING = 4.5.dp
internal val TAB_ICON_LABEL_GAP = 1.dp
internal val TAB_ICON_SIZE = 22.dp
internal val PILL_INSET = 3.dp

internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
internal const val STRETCH = 0.16f
internal const val SQUASH = 0.5f

@Composable
fun FloatingBottomBar(
    tabs: List<BottomBarTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val glassSpec: AnimationSpec<Float> = GlassSpring

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { 6.dp.toPx() }
    val n = tabs.size

    val tabWidthPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width - gapPx * (n - 1)) / n
    } else 0f
    val tabStepPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width + gapPx) / n
    } else 0f

    val currentTabStepPx by rememberUpdatedState(tabStepPx)
    val currentLastIndex by rememberUpdatedState(tabs.lastIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    val pillTargetPx = if (tabStepPx > 0f) {
        selectedIndex * tabStepPx + dragOffset
    } else 0f

    val animatedPillOffset by animateFloatAsState(
        targetValue = pillTargetPx,
        animationSpec = glassSpec,
        label = "pillOffset",
    )

    val lag = if (tabStepPx > 0f) {
        (abs(pillTargetPx - animatedPillOffset) / tabStepPx).coerceIn(0f, 1f)
    } else 0f

    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
        lastHapticTab = selectedIndex
    }

    val insets = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))
    val highPerformance = LocalHighPerformanceMode.current

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val highPerformance = LocalHighPerformanceMode.current
        val isApiBelow31 = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

        Box(
            modifier = Modifier
                .windowInsetsPadding(insets)
                .padding(horizontal = NAV_BAR_GUTTER)
                .padding(bottom = NAV_BAR_LIFT)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(pillShape)
                .then(
                    if (highPerformance || isApiBelow31) {
                        Modifier.background(if (isLight) Color(0xFFFFFFFF).copy(alpha = 0.92f) else Color(0xFF1C1C1E).copy(alpha = 0.72f))
                    } else if (hazeState != null) {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = Color.Transparent,
                                tints = if (isLight) {
                                    listOf(
                                        HazeTint(Color.White.copy(alpha = 0.6f)),
                                        HazeTint(Color.Black.copy(alpha = 0.05f))
                                    )
                                } else {
                                    listOf(
                                        HazeTint(Color.Black.copy(alpha = 0.3f)),
                                        HazeTint(Color.White.copy(alpha = 0.08f))
                                    )
                                },
                                blurRadius = 24.dp
                            ),
                        )
                    } else {
                        Modifier.background(if (isLight) Color(0xFFFFFFFF).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.5f))
                    }
                )
                .border(GLASS_EDGE_WIDTH, if (isLight) Color(0x1F000000) else GLASS_EDGE_COLOR, pillShape)
                .padding(horizontal = PILL_INSET, vertical = PILL_INSET),
        ) {
        // Traveling Liquid Selection Pill
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(with(density) { rowSize.height.toDp() })
                    .graphicsLayer {
                        translationX = animatedPillOffset
                        scaleX = 1f + lag * STRETCH
                        scaleY = 1f - lag * STRETCH * SQUASH
                    }
                    .clip(pillShape)
                    .background(if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.14f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowSize = it }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    var dragStartIndex = currentSelectedIndex
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            dragStartIndex = currentSelectedIndex
                        },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            val step = currentTabStepPx
                            if (step > 0f) {
                                val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                    .coerceIn(0f, currentLastIndex.toFloat())
                                val newIndex = absoluteIndexFloat.roundToInt()
                                if (newIndex != currentSelectedIndex) {
                                    currentOnTabSelected(newIndex)
                                }
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            val step = currentTabStepPx
                            if (step <= 0f) return@detectHorizontalDragGestures
                            totalDrag += delta
                            val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                .coerceIn(0f, currentLastIndex.toFloat())
                            dragOffset = absoluteIndexFloat * step - currentSelectedIndex * step

                            val approxTab = absoluteIndexFloat.roundToInt()
                            if (approxTab != lastHapticTab) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticTab = approxTab
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = glassSpec,
                    label = "tabScale",
                )
                val tint by animateColorAsState(
                    targetValue = if (isLight) {
                        if (selected) Color(0xFF1C1C1E) else Color(0xFF8E8E93)
                    } else {
                        if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(200),
                    label = "tabTint",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onTabSelected(index)
                        }
                        .padding(vertical = TAB_VERTICAL_PADDING),
                ) {
                    val iconModifier = Modifier
                        .size(TAB_ICON_SIZE)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    when {
                        tab.iconPainter != null -> {
                            Icon(
                                painter = tab.iconPainter,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = iconModifier
                            )
                        }
                        tab.iconResId != null -> {
                            Icon(
                                painter = painterResource(tab.iconResId),
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = iconModifier
                            )
                        }
                        tab.icon != null -> {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = iconModifier
                            )
                        }
                    }
                    Spacer(Modifier.height(TAB_ICON_LABEL_GAP))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            shadow = if (isLight) null else Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                blurRadius = 4f,
                                offset = Offset(0f, 1f)
                            )
                        ),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
}
