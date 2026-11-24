package com.mue.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Liquid glass modifier with BitChord hairline edge and optimized 0.33x Haze sampling.
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp
): Modifier = liquidGlass(hazeState = null, shape = RoundedCornerShape(cornerRadius))

@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState?,
    cornerRadius: Dp = 24.dp,
    tintAlpha: Float = 0.08f
): Modifier = liquidGlass(
    hazeState = hazeState,
    shape = RoundedCornerShape(cornerRadius),
    tintAlpha = tintAlpha
)

@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState? = null,
    shape: CornerBasedShape = RoundedCornerShape(24.dp),
    tintAlpha: Float = 0.08f
): Modifier {
    val highPerformance = LocalHighPerformanceMode.current
    val isApiBelow31 = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val clipped = this.clip(shape)

    val fallbackColor = if (isLight) Color(0xFFFFFFFF).copy(alpha = 0.90f) else Color(0xFF1C1C1E).copy(alpha = 0.72f)
    val noHazeColor = if (isLight) Color(0xFFFFFFFF).copy(alpha = 0.80f) else Color.Black.copy(alpha = 0.2f)
    val edgeColor = if (isLight) Color(0x1F000000) else GLASS_EDGE_COLOR
    val hazeTintColor = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = tintAlpha)

    val styled = if (highPerformance || isApiBelow31) {
        clipped.background(fallbackColor)
    } else if (hazeState != null) {
        clipped.optimizedHazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = Color.Transparent,
                tints = listOf(HazeTint(hazeTintColor)),
                blurRadius = 24.dp
            )
        )
    } else {
        clipped.background(noHazeColor)
    }

    return styled.border(
        width = GLASS_EDGE_WIDTH,
        color = edgeColor,
        shape = shape
    )
}

/**
 * Standard thumbnail border from BitChord to ensure dark album covers never bleed into pure black backgrounds.
 */
fun Modifier.thumbnailBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier = this.border(
    width = THUMBNAIL_BORDER_WIDTH,
    color = THUMBNAIL_BORDER_COLOR,
    shape = shape
)

/**
 * Content icon & label color sitting on a glass surface.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Selected-tab indicator color for a glass surface: subtle white lift.
 */
@Composable
fun glassIndicatorColor(): Color = Color.White.copy(alpha = 0.14f)
