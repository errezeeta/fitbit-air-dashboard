package com.javier.fitbitair.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Counts a number up from zero the first time it is shown.
 *
 * `animateIntAsState(target)` alone does not count: on first composition the
 * value is already at its target, so the number simply appears. Starting the
 * animation one frame later, from zero, is what makes the readout feel like it
 * is measuring something. Later changes (a range switch, a refresh) animate
 * straight from the old value, which is the behaviour you want.
 */
@Composable
fun rememberCountUpInt(target: Int, durationMillis: Int = 650): State<Int> {
    val started = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started.value = true }
    return animateIntAsState(
        targetValue = if (started.value) target else 0,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "count-up-int",
    )
}

@Composable
fun rememberCountUpFloat(target: Float, durationMillis: Int = 650): State<Float> {
    val started = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started.value = true }
    return animateFloatAsState(
        targetValue = if (started.value) target else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "count-up-float",
    )
}
