package com.nightcity.holoos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.ChamferShape
import com.nightcity.holoos.core.designsystem.theme.HoloColors

/**
 * The core HUD container: chamfered slate panel, neon outline and bracket ticks on the two
 * square corners. Optional header strip with a trailing slot.
 */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = HoloColors.NeonCyan,
    pulse: Boolean = false,
    cut: Dp = 14.dp,
    backgroundAlpha: Float = 0.9f,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(cut) { ChamferShape(cut) }
    Column(
        modifier
            .neonBorder(accent, shape, pulse = pulse, width = 1.2.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        HoloColors.DarkSlate.copy(alpha = backgroundAlpha),
                        HoloColors.PitchBlack.copy(alpha = backgroundAlpha),
                    ),
                ),
            )
            .cornerTicks(accent)
            .padding(contentPadding),
    ) {
        if (title != null) {
            HudHeader(title = title, accent = accent, trailing = trailing)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun HudHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = HoloColors.NeonCyan,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 6.dp, height = 12.dp).background(accent))
            Text(
                text = "// " + title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.8f), Color.Transparent))),
        )
    }
}

/** Small L-shaped brackets on the top-end and bottom-start (non-chamfered) corners. */
fun Modifier.cornerTicks(color: Color, length: Dp = 10.dp, inset: Dp = 4.dp): Modifier = drawBehind {
    val l = length.toPx()
    val i = inset.toPx()
    val s = 1.5.dp.toPx()
    val c = color.copy(alpha = 0.85f)
    // top-end
    drawLine(c, Offset(size.width - i - l, i), Offset(size.width - i, i), s)
    drawLine(c, Offset(size.width - i, i), Offset(size.width - i, i + l), s)
    // bottom-start
    drawLine(c, Offset(i, size.height - i), Offset(i + l, size.height - i), s)
    drawLine(c, Offset(i, size.height - i - l), Offset(i, size.height - i), s)
}

/** Compact chamfered status tag, e.g. ONLINE / OFFLINE. */
@Composable
fun StatusTag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    val shape = remember { ChamferShape(5.dp) }
    Box(
        modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .neonBorder(color, shape, width = 1.dp, glow = false)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) HoloColors.PitchBlack else color,
        )
    }
}

/** Horizontal "telemetry" divider: a dashed neon line. */
@Composable
fun HudDivider(modifier: Modifier = Modifier, color: Color = HoloColors.NeonCyan.copy(alpha = 0.35f)) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                val dash = 6.dp.toPx()
                val gap = 4.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(color, Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), size.height)
                    x += dash + gap
                }
            },
    )
}

@Composable
fun HudLabelValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = HoloColors.TextPrimary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = HoloColors.TextDim)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

/** Leading "01"-style index for list rows. */
@Composable
fun HudIndex(index: Int, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = index.toString().padStart(2, '0'),
        style = MaterialTheme.typography.headlineMedium,
        color = color.copy(alpha = 0.85f),
        modifier = modifier.padding(end = 10.dp),
    )
}
