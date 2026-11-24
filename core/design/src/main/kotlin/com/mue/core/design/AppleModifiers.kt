package com.mue.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

fun Modifier.applePressEffect(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch {
                        launch {
                            scale.animateTo(
                                targetValue = 0.97f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        launch {
                            alpha.animateTo(
                                targetValue = 0.85f,
                                animationSpec = tween(durationMillis = 100)
                            )
                        }
                    }

                    waitForUpOrCancellation()

                    scope.launch {
                        launch {
                            scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        launch {
                            alpha.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 150)
                            )
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
}

