package com.nightcity.holoos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Mutable glitch parameters. Only read during draw, so bursts cost no recomposition. */
@Stable
class GlitchState {
    var seed by mutableIntStateOf(0)
    var intensity by mutableFloatStateOf(0f)

    suspend fun burst(durationMs: Long = 180L, peak: Float = 1f) {
        val end = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < end) {
            seed = Random.nextInt()
            intensity = peak * (0.45f + Random.nextFloat() * 0.55f)
            delay(40)
        }
        intensity = 0f
    }
}

/** A glitch state that fires short random bursts every few seconds. */
@Composable
fun rememberAmbientGlitch(
    minDelayMs: Long = 2_500,
    maxDelayMs: Long = 7_000,
    burstMs: Long = 180,
    peak: Float = 1f,
): GlitchState {
    val state = remember { GlitchState() }
    LaunchedEffect(state, minDelayMs, maxDelayMs) {
        while (isActive) {
            delay(Random.nextLong(minDelayMs, maxDelayMs))
            state.burst(burstMs, peak)
        }
    }
    return state
}

/**
 * Micro-glitch: chromatic aberration (crimson/cyan ghost copies), horizontally displaced
 * slices and a scan tear line, all derived from [state].
 */
fun Modifier.glitch(
    state: GlitchState,
    maxShift: Dp = 6.dp,
    chroma: Boolean = true,
): Modifier = drawWithContent {
    val k = state.intensity
    if (k <= 0f) {
        drawContent()
        return@drawWithContent
    }
    val rnd = Random(state.seed)
    val shift = maxShift.toPx() * k
    if (chroma) {
        val paint = Paint()
        for ((tint, dir) in arrayOf(HoloColors.CrimsonRed to -1f, HoloColors.NeonCyan to 1f)) {
            paint.colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
            paint.alpha = 0.75f
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
            translate(left = dir * shift * 0.6f) { this@drawWithContent.drawContent() }
            drawIntoCanvas { it.restore() }
        }
    }
    drawContent()
    repeat(3) {
        val top = rnd.nextFloat() * size.height
        val h = (0.05f + rnd.nextFloat() * 0.22f) * size.height
        val dx = (rnd.nextFloat() * 2f - 1f) * shift
        clipRect(top = top, bottom = (top + h).coerceAtMost(size.height)) {
            translate(left = dx) { this@drawWithContent.drawContent() }
        }
    }
    drawRect(
        color = if (rnd.nextBoolean()) HoloColors.CrimsonRed else HoloColors.NeonCyan,
        topLeft = Offset(0f, rnd.nextFloat() * size.height),
        size = Size(size.width, 1.5.dp.toPx()),
        alpha = 0.7f * k,
    )
}

/** Text that periodically tears with a chromatic glitch. */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = HoloColors.NeonCyan,
    maxLines: Int = Int.MAX_VALUE,
    glitch: GlitchState = rememberAmbientGlitch(),
) {
    Text(
        text = text,
        modifier = modifier.glitch(glitch),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Wraps screen content and fires a short glitch burst + interference bars whenever [key]
 * changes. Used for navigation transitions.
 */
@Composable
fun GlitchTransitionHost(
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glitch = remember { GlitchState() }
    val bars = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        seed = Random.nextInt()
        bars.snapTo(0f)
        coroutineScope {
            launch { glitch.burst(durationMs = 260, peak = 1.4f) }
            bars.animateTo(1f, tween(320, easing = LinearEasing))
        }
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().glitch(glitch, maxShift = 10.dp, chroma = false)) { content() }
        Canvas(Modifier.fillMaxSize()) {
            val p = bars.value
            if (p >= 1f) return@Canvas
            val rnd = Random(seed + (p * 9f).toInt())
            val strength = 1f - p
            repeat(7) {
                val y = rnd.nextFloat() * size.height
                val h = 2.dp.toPx() + rnd.nextFloat() * 16.dp.toPx()
                val color = when (rnd.nextInt(3)) {
                    0 -> HoloColors.NeonCyan
                    1 -> HoloColors.CrimsonRed
                    else -> HoloColors.CyberYellow
                }
                val w = size.width * (0.25f + rnd.nextFloat() * 0.75f)
                drawRect(
                    color = color.copy(alpha = 0.32f * strength),
                    topLeft = Offset(rnd.nextFloat() * (size.width - w), y),
                    size = Size(w, h),
                )
            }
        }
    }
}
