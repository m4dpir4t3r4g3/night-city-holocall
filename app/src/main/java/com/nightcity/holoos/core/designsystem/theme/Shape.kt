package com.nightcity.holoos.core.designsystem.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class Corner { TopStart, TopEnd, BottomEnd, BottomStart }

/**
 * Military-HUD chamfer: 45-degree cuts on the chosen corners. The default is the signature
 * top-start + bottom-end cut. Respects RTL by mirroring start/end.
 */
@Immutable
data class ChamferShape(
    val cut: Dp = 12.dp,
    val corners: Set<Corner> = setOf(Corner.TopStart, Corner.BottomEnd),
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val tl = (if (rtl) Corner.TopEnd else Corner.TopStart) in corners
        val tr = (if (rtl) Corner.TopStart else Corner.TopEnd) in corners
        val br = (if (rtl) Corner.BottomStart else Corner.BottomEnd) in corners
        val bl = (if (rtl) Corner.BottomEnd else Corner.BottomStart) in corners
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(if (tl) c else 0f, 0f)
            lineTo(if (tr) w - c else w, 0f)
            if (tr) lineTo(w, c)
            lineTo(w, if (br) h - c else h)
            if (br) lineTo(w - c, h)
            lineTo(if (bl) c else 0f, h)
            if (bl) lineTo(0f, h - c)
            lineTo(0f, if (tl) c else 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Pointy-top hexagon used for HUD icon frames. */
object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w / 2f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}

object HoloShapes {
    val Small = ChamferShape(6.dp)
    val Medium = ChamferShape(10.dp)
    val Large = ChamferShape(16.dp)
}

/** Material slots mapped to the same chamfer geometry so stock M3 components match. */
val MaterialHoloShapes = Shapes(
    extraSmall = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
    small = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
    medium = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
    large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    extraLarge = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp),
)
