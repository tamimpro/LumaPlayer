package com.example.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/**
 * Luma Player v2.0 Ultra-Snappy Subtle Motion system:
 * Crisp 120–210ms transitions and zero-bounce high-stiffness springs tuned for 60/120Hz fluidity.
 */
object LumaMotion {
    const val FAST_MS = 150
    const val STANDARD_MS = 190
    const val THEME_CROSSFADE_MS = 180

    val snappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    val snappyOffsetSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun screenEnter(): EnterTransition =
        fadeIn(tween(STANDARD_MS, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { (it * 0.08f).toInt() }
            )

    fun screenExit(): ExitTransition =
        fadeOut(tween(FAST_MS, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(FAST_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { -(it * 0.05f).toInt() }
            )

    fun screenPopEnter(): EnterTransition =
        fadeIn(tween(STANDARD_MS, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { -(it * 0.06f).toInt() }
            )

    fun screenPopExit(): ExitTransition =
        fadeOut(tween(FAST_MS, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(FAST_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { (it * 0.08f).toInt() }
            )

    fun tabEnter(): EnterTransition =
        fadeIn(tween(FAST_MS, easing = FastOutSlowInEasing))

    fun tabExit(): ExitTransition =
        fadeOut(tween(120, easing = FastOutSlowInEasing))

    fun hudTopEnter(): EnterTransition =
        fadeIn(tween(160, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(170, easing = FastOutSlowInEasing)) { -it / 2 }

    fun hudTopExit(): ExitTransition =
        fadeOut(tween(140, easing = FastOutSlowInEasing)) +
            slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { -it / 2 }

    fun hudBottomEnter(): EnterTransition =
        fadeIn(tween(160, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(170, easing = FastOutSlowInEasing)) { it / 2 }

    fun hudBottomExit(): ExitTransition =
        fadeOut(tween(140, easing = FastOutSlowInEasing)) +
            slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it / 2 }

    fun hudCenterEnter(): EnterTransition =
        fadeIn(tween(150, easing = FastOutSlowInEasing)) +
            scaleIn(tween(170, easing = FastOutSlowInEasing), initialScale = 0.90f)

    fun hudCenterExit(): ExitTransition =
        fadeOut(tween(130, easing = FastOutSlowInEasing)) +
            scaleOut(tween(140, easing = FastOutSlowInEasing), targetScale = 0.92f)

    fun accordionEnter(): EnterTransition =
        fadeIn(tween(160, easing = FastOutSlowInEasing)) +
            expandVertically(tween(190, easing = FastOutSlowInEasing))

    fun accordionExit(): ExitTransition =
        fadeOut(tween(120, easing = FastOutSlowInEasing)) +
            shrinkVertically(tween(160, easing = FastOutSlowInEasing))
}

/**
 * Subtle tactile 0.975x press compression that never blocks clicks or scrolling.
 */
fun Modifier.lumaPressScale(
    pressedScale: Float = 0.975f,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = LumaMotion.snappySpring,
        label = "lumaPressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}
