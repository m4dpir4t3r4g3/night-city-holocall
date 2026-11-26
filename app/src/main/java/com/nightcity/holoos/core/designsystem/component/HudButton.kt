package com.nightcity.holoos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.ChamferShape
import com.nightcity.holoos.core.designsystem.theme.HoloColors

enum class HudButtonStyle { Filled, Outline, Ghost }

/**
 * Chamfered HUD button. Pressing shifts the face a couple of pixels and flashes the fill,
 * mimicking a mechanical actuator.
 */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = HoloColors.NeonCyan,
    style: HudButtonStyle = HudButtonStyle.Filled,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val shape = remember { ChamferShape(if (compact) 6.dp else 9.dp) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when (style) {
        HudButtonStyle.Filled -> if (pressed) accent.copy(alpha = 0.7f) else accent
        HudButtonStyle.Outline -> accent.copy(alpha = if (pressed) 0.25f else 0.08f)
        HudButtonStyle.Ghost -> if (pressed) accent.copy(alpha = 0.15f) else Color.Transparent
    }
    val content = if (style == HudButtonStyle.Filled) HoloColors.PitchBlack else accent
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .graphicsLayer { translationX = if (pressed) 2.dp.toPx() else 0f }
            .then(if (style == HudButtonStyle.Outline) Modifier.neonBorder(accent, shape, width = 1.dp) else Modifier)
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = if (compact) 10.dp else 16.dp,
                vertical = if (compact) 6.dp else 11.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (compact) 14.dp else 18.dp))
        Text(
            text = text.uppercase(),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Label + switch row styled for the HUD. */
@Composable
fun HudToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = HoloColors.NeonCyan,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) HoloColors.TextPrimary else HoloColors.TextDim,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HoloColors.PitchBlack,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = HoloColors.TextDim,
                uncheckedTrackColor = HoloColors.PitchBlack,
                uncheckedBorderColor = HoloColors.GridLine,
            ),
        )
    }
}
