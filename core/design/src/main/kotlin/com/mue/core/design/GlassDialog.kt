package com.mue.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState

/**
 * Liquid glass dialog container for custom dialog layouts.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    hazeState: HazeState? = LocalHazeState.current,
    shape: CornerBasedShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(
            modifier = modifier.liquidGlass(hazeState = hazeState, shape = shape)
        ) {
            content()
        }
    }
}

/**
 * Liquid glass Material 3 alert dialog drop-in replacement.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: CornerBasedShape = RoundedCornerShape(24.dp),
    hazeState: HazeState? = LocalHazeState.current,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.liquidGlass(hazeState = hazeState, shape = shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        properties = properties
    )
}
