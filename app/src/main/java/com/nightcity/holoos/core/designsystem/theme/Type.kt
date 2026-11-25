package com.nightcity.holoos.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nightcity.holoos.R

/** Geometric display face for headlines and the HUD clock. */
val Orbitron = FontFamily(
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

/** Condensed tech face for body copy. */
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** Monospaced face for labels, readouts and telemetry. */
val ShareTechMono = FontFamily(Font(R.font.share_tech_mono_regular, FontWeight.Normal))

val HoloTypography = Typography(
    displayLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = 2.sp),
    displayMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 1.5.sp),
    displaySmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 1.5.sp),
    headlineLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.5.sp),
    headlineMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 1.2.sp),
    headlineSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = ShareTechMono, fontSize = 14.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = ShareTechMono, fontSize = 10.sp, letterSpacing = 1.sp),
)
