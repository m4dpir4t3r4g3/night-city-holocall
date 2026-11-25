package com.nightcity.holoos.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a neon outline that follows [shape], with a soft outer glow. When [pulse] is set the
 * glow breathes; the animated value is only read in the draw phase, so pulsing never
 * triggers recomposition.
 */
fun Modifier.neonBorder(
    color: Color,
    shape: Shape,
    pulse: Boolean = false,
    width: Dp = 1.5.dp,
    glow: Boolean = true,
): Modifier = composed {
    val alpha: State<Float> = if (pulse) {
        rememberInfiniteTransition(label = "neonPulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "neonAlpha",
        )
    } else {
        remember { ConstantState(1f) }
    }
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val stroke = width.toPx()
        onDrawWithContent {
            drawContent()
            val a = alpha.value
            if (glow) {
                drawPath(path, color.copy(alpha = 0.12f * a), style = Stroke(stroke * 6f))
                drawPath(path, color.copy(alpha = 0.28f * a), style = Stroke(stroke * 3f))
            }
            drawPath(path, color.copy(alpha = a.coerceIn(0f, 1f)), style = Stroke(stroke))
        }
    }
}

private class ConstantState<T>(override val value: T) : State<T>
