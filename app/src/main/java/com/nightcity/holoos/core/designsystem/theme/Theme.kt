package com.nightcity.holoos.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HoloColorScheme = darkColorScheme(
    primary = HoloColors.NeonCyan,
    onPrimary = HoloColors.PitchBlack,
    primaryContainer = HoloColors.CyanDim,
    onPrimaryContainer = HoloColors.TextPrimary,
    secondary = HoloColors.CyberYellow,
    onSecondary = HoloColors.PitchBlack,
    secondaryContainer = HoloColors.YellowDim,
    onSecondaryContainer = HoloColors.CyberYellow,
    tertiary = HoloColors.CrimsonRed,
    onTertiary = HoloColors.TextPrimary,
    tertiaryContainer = HoloColors.CrimsonDim,
    onTertiaryContainer = HoloColors.TextPrimary,
    error = HoloColors.CrimsonRed,
    onError = HoloColors.TextPrimary,
    background = HoloColors.PitchBlack,
    onBackground = HoloColors.TextPrimary,
    surface = HoloColors.DarkSlate,
    onSurface = HoloColors.TextPrimary,
    surfaceVariant = HoloColors.SlatePanel,
    onSurfaceVariant = HoloColors.TextDim,
    surfaceContainerLowest = HoloColors.PitchBlack,
    surfaceContainerLow = HoloColors.DarkSlate,
    surfaceContainer = HoloColors.DarkSlate,
    surfaceContainerHigh = HoloColors.SlatePanel,
    surfaceContainerHighest = HoloColors.SlatePanel,
    outline = HoloColors.CyanDim,
    outlineVariant = HoloColors.GridLine,
)

@Composable
fun HoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HoloColorScheme,
        typography = HoloTypography,
        shapes = MaterialHoloShapes,
        content = content,
    )
}
