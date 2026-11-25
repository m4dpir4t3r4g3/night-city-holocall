package com.nightcity.holoos.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.nightcity.holoos.domain.model.IconTint
import javax.inject.Inject

/**
 * The built-in "icon pack": maps any icon onto a two-colour HUD ramp with a single
 * ColorMatrix pass (luminance -> shadow..highlight). Icons with bright backgrounds are
 * luminance-inverted first so the glyph glows instead of the plate.
 */
class IconStylizer @Inject constructor() {

    fun stylize(drawable: Drawable, sizePx: Int, tint: IconTint): Bitmap {
        val src = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(src))
        return stylize(src, tint, recycleSource = true)
    }

    fun stylize(source: Bitmap, tint: IconTint, recycleSource: Boolean = false): Bitmap {
        val out = createBitmap(source.width, source.height)
        val (shadow, highlight) = ramp(tint)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(duotoneMatrix(shadow, highlight, contrast = 1.3f, invert = isBright(source)))
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        if (recycleSource) source.recycle()
        return out
    }

    /** Mean luminance of opaque pixels, sampled from a 16x16 thumbnail. */
    private fun isBright(bitmap: Bitmap): Boolean {
        val thumb = bitmap.scale(16, 16)
        var sum = 0.0
        var count = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            val c = thumb[x, y]
            if ((c ushr 24) < 128) continue
            sum += 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
            count++
        }
        if (thumb != bitmap) thumb.recycle()
        return count > 0 && sum / count / 255.0 > BRIGHT_THRESHOLD
    }

    companion object {
        private const val BRIGHT_THRESHOLD = 0.62

        fun ramp(tint: IconTint): Pair<Int, Int> = when (tint) {
            IconTint.CYAN -> 0xFF021A1F.toInt() to 0xFF00F0FF.toInt()
            IconTint.YELLOW -> 0xFF1F1D02.toInt() to 0xFFFCEE0A.toInt()
            IconTint.DUOTONE -> 0xFF00363C.toInt() to 0xFFFCEE0A.toInt()
        }

        /**
         * L  = 0.299R + 0.587G + 0.114B           (0..255)
         * L' = a*L + b                              (contrast, optional inversion)
         * C  = shadow_c + (highlight_c - shadow_c) * L' / 255
         */
        fun duotoneMatrix(shadow: Int, highlight: Int, contrast: Float, invert: Boolean): ColorMatrix {
            val a = if (invert) -contrast else contrast
            val b = if (invert) 127f * contrast + 128f else 128f * (1f - contrast)
            fun row(shift: Int): FloatArray {
                val s = ((shadow shr shift) and 0xFF).toFloat()
                val h = ((highlight shr shift) and 0xFF).toFloat()
                val d = (h - s) / 255f
                return floatArrayOf(d * a * 0.299f, d * a * 0.587f, d * a * 0.114f, 0f, s + d * b)
            }
            return ColorMatrix(row(16) + row(8) + row(0) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
        }
    }
}
