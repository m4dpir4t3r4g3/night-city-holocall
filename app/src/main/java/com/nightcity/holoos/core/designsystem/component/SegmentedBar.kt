package com.nightcity.holoos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import kotlin.math.roundToInt

/** Skewed, segmented gauge used for battery, neural load and calibration progress. */
@Composable
fun SegmentedBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 20,
    color: Color = HoloColors.NeonCyan,
    emptyColor: Color = HoloColors.GridLine,
) {
    Canvas(modifier.height(10.dp)) {
        val gap = 2.dp.toPx()
        val skew = size.height * 0.45f
        val segW = (size.width - gap * (segments - 1) - skew) / segments
        val filled = (fraction.coerceIn(0f, 1f) * segments).roundToInt()
        for (i in 0 until segments) {
            val x = i * (segW + gap)
            val path = Path().apply {
                moveTo(x + skew, 0f)
                lineTo(x + skew + segW, 0f)
                lineTo(x + segW, size.height)
                lineTo(x, size.height)
                close()
            }
            val isFilled = i < filled
            drawPath(path, if (isFilled) color else emptyColor)
            if (isFilled && i == filled - 1) {
                // bright leading edge
                drawLine(Color.White.copy(alpha = 0.7f), Offset(x + skew + segW, 0f), Offset(x + segW, size.height), 1.dp.toPx())
            }
        }
    }
}
