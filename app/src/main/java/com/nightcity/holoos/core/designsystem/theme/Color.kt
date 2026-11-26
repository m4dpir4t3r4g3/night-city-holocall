package com.nightcity.holoos.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Kiroshi / Militech HUD palette. Backgrounds stay near-black for AMOLED efficiency. */
object HoloColors {
    val PitchBlack = Color(0xFF050508)
    val DarkSlate = Color(0xFF0D1117)
    val SlatePanel = Color(0xFF121A26)
    val GridLine = Color(0xFF1B2A3A)

    val NeonCyan = Color(0xFF00F0FF)
    val CyanDim = Color(0xFF0A6F78)
    val CyberYellow = Color(0xFFFCEE0A)
    val YellowDim = Color(0xFF4A4503)
    val CrimsonRed = Color(0xFFFF003C)
    val CrimsonDim = Color(0xFF5C0016)

    val TextPrimary = Color(0xFFE6FBFF)
    val TextDim = Color(0xFF7A8C99)
}

/** Hex values surfaced in the Manual Overrides palette buffer. */
enum class PaletteSwatch(val label: String, val hex: String, val color: Color) {
    NEON_CYAN("Neon Cyan", "#00F0FF", HoloColors.NeonCyan),
    CYBER_YELLOW("Cyber Yellow", "#FCEE0A", HoloColors.CyberYellow),
    PITCH_BLACK("Pitch Black", "#050508", HoloColors.PitchBlack),
    CRIMSON_RED("Crimson Red", "#FF003C", HoloColors.CrimsonRed),
    DARK_SLATE("Dark Slate", "#0D1117", HoloColors.DarkSlate),
}
