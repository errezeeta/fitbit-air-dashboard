package com.javier.fitbitair.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/**
 * A tap that answers with its whole body: the surface sinks a little under the
 * finger, the ripple carries on from the same interaction and a light tick
 * confirms it. Compose's default ripple is fine; the missing part is that the
 * card itself never reacted.
 */
@Composable
fun Modifier.clickableScaled(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    haptics: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 780f),
        label = "press-scale",
    )
    val feedback = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = ripple(),
            onClick = {
                if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

/**
 * Content that arrives in order: each item waits a beat more than the previous
 * one, then fades and rises into place. Only the first composition animates, so
 * a data refresh does not make the whole screen dance again.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    stepDelayMs: Long = 55L,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepDelayMs)
        progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 26f
        },
    ) {
        content()
    }
}
