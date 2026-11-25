package com.nightcity.holoos.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.HoloColors

/**
 * CRT scanline texture with a slow sweeping refresh band. Purely decorative: it draws on
 * top of content and never consumes touches.
 */
@Composable
fun ScanlineOverlay(
    modifier: Modifier = Modifier.fillMaxSize(),
    spacing: Dp = 3.dp,
    lineAlpha: Float = 0.035f,
    sweep: Boolean = true,
) {
    val sweepY = if (sweep) {
        rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = -0.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
            label = "sweep",
        )
    } else {
        null
    }
    Box(
        modifier.drawWithCache {
            val step = spacing.toPx()
            val lines = Path().apply {
                var y = 0f
                while (y < size.height) {
                    moveTo(0f, y)
                    lineTo(size.width, y)
                    y += step
                }
            }
            val bandHeight = size.height * 0.18f
            onDrawBehind {
                drawPath(lines, HoloColors.NeonCyan.copy(alpha = lineAlpha), style = Stroke(1f))
                val y = (sweepY?.value ?: return@onDrawBehind) * size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to HoloColors.NeonCyan.copy(alpha = 0.045f),
                        1f to Color.Transparent,
                        startY = y - bandHeight / 2f,
                        endY = y + bandHeight / 2f,
                    ),
                    topLeft = Offset(0f, y - bandHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
                )
            }
        },
    )
}

/** Hub background: AMOLED black, slate vignette and a faint tactical grid. */
@Composable
fun HoloBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HoloColors.PitchBlack, HoloColors.DarkSlate, HoloColors.PitchBlack)))
            .drawBehind {
                val step = 32.dp.toPx()
                val color = HoloColors.NeonCyan.copy(alpha = 0.035f)
                var x = 0f
                while (x < size.width) {
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            },
        content = content,
    )
}
