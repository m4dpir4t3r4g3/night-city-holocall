package com.nightcity.holoos.service.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.random.Random

/**
 * Procedural Night City backdrop: gradient sky, two parallax skyline layers (pre-rendered to
 * bitmaps on resize), flickering neon bars, a perspective grid floor and slanted rain.
 * All per-frame work is a handful of bitmap blits and one batched drawLines call.
 */
class NightCityRenderer(private val density: Float) {

    private var width = 0
    private var height = 0
    private var farLayer: Bitmap? = null
    private var nearLayer: Bitmap? = null
    private val margin get() = (width * 0.12f).toInt()

    private val skyPaint = Paint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f * density }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density; color = Color.argb(70, 0, 240, 255) }
    private val neonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glitchPaint = Paint()
    private val crimsonTint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.argb(110, 255, 0, 60), PorterDuff.Mode.SRC_ATOP)
    }
    private val cyanTint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.argb(110, 0, 240, 255), PorterDuff.Mode.SRC_ATOP)
    }

    private var rain = FloatArray(0) // x, y, len, speed per drop
    private var rainLines = FloatArray(0)
    private val neonSigns = ArrayList<NeonSign>()

    // Parallax: page offset (launcher), device tilt and touch all feed a smoothed target.
    var pageOffset = 0.5f
    private var tiltX = 0f
    private var touchX = 0f
    private var parallax = 0f

    private var gridPhase = 0f
    private var lastFrame = 0L
    private var glitchUntil = 0L
    private var glitchStrength = 1f

    fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF050508.toInt(), 0xFF0D1117.toInt(), 0xFF101A2A.toInt(), 0xFF050508.toInt()),
            floatArrayOf(0f, 0.45f, 0.66f, 1f),
            Shader.TileMode.CLAMP,
        )
        farLayer?.recycle()
        nearLayer?.recycle()
        farLayer = renderSkyline(seed = 11, heightFrac = 0.30f, color = 0xFF0C1420.toInt(), windowAlpha = 70)
        nearLayer = renderSkyline(seed = 23, heightFrac = 0.42f, color = 0xFF070B12.toInt(), windowAlpha = 140)
        val drops = (w * h / (9_000f * density * density)).toInt().coerceIn(60, 220)
        val rnd = Random(5)
        rain = FloatArray(drops * 4) { i ->
            when (i % 4) {
                0 -> rnd.nextFloat() * w
                1 -> rnd.nextFloat() * h
                2 -> (10 + rnd.nextFloat() * 22) * density
                else -> (500 + rnd.nextFloat() * 700) * density
            }
        }
        rainLines = FloatArray(drops * 4)
        neonSigns.clear()
        val srnd = Random(42)
        repeat(6) {
            neonSigns += NeonSign(
                x = srnd.nextFloat() * (w + margin * 2) - margin,
                y = h * (0.38f + srnd.nextFloat() * 0.22f),
                w = (18 + srnd.nextFloat() * 60) * density,
                h = (3 + srnd.nextFloat() * 5) * density,
                color = intArrayOf(0xFF00F0FF.toInt(), 0xFFFCEE0A.toInt(), 0xFFFF003C.toInt())[srnd.nextInt(3)],
                phase = srnd.nextFloat() * 10f,
                depth = 0.4f + srnd.nextFloat() * 0.6f,
            )
        }
    }

    fun tilt(x: Float) { tiltX = (-x / 9.81f).coerceIn(-1f, 1f) }
    fun touch(xFrac: Float) { touchX = (xFrac - 0.5f) * 2f }
    fun triggerGlitch(strength: Float = 1f, durationMs: Long = 650) {
        glitchStrength = strength
        glitchUntil = lastFrame + durationMs
    }

    fun draw(canvas: Canvas, now: Long) {
        if (width == 0) return
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame).coerceAtMost(100)) / 1000f
        lastFrame = now
        val target = (pageOffset - 0.5f) * 1.2f + tiltX * 0.6f + touchX * 0.25f
        parallax += (target - parallax) * (dt * 3f).coerceAtMost(1f)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
        val horizon = height * 0.68f

        farLayer?.let { canvas.drawBitmap(it, -margin - parallax * margin * 0.35f, horizon - it.height, bitmapPaint) }
        drawNeon(canvas, now)
        nearLayer?.let { canvas.drawBitmap(it, -margin - parallax * margin * 0.8f, horizon - it.height + 6 * density, bitmapPaint) }
        drawGrid(canvas, horizon, dt)
        drawRain(canvas, dt)
        if (now < glitchUntil) drawGlitch(canvas, now, horizon)
    }

    private fun drawNeon(canvas: Canvas, now: Long) {
        val t = now / 1000f
        for (s in neonSigns) {
            val flicker = if (((t + s.phase) * 3.1f).toInt() % 11 == 0) 0.25f else 1f
            val x = s.x - parallax * margin * s.depth * 0.6f
            neonPaint.color = s.color
            neonPaint.alpha = (40 * flicker).toInt()
            canvas.drawRect(x - 4 * density, s.y - 4 * density, x + s.w + 4 * density, s.y + s.h + 4 * density, neonPaint)
            neonPaint.alpha = (220 * flicker).toInt()
            canvas.drawRect(x, s.y, x + s.w, s.y + s.h, neonPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, horizon: Float, dt: Float) {
        gridPhase = (gridPhase + dt * 0.35f) % 1f
        val vanishX = width / 2f - parallax * width * 0.15f
        val floorH = height - horizon
        // Horizontal lines with perspective spacing, scrolling toward the viewer.
        for (i in 0 until 14) {
            val p = (i + gridPhase) / 14f
            val y = horizon + floorH * p * p
            gridPaint.color = Color.argb((25 + 120 * p).toInt(), 0, 240, 255)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        // Converging verticals.
        gridPaint.color = Color.argb(70, 0, 240, 255)
        for (i in -10..10) {
            val bottomX = vanishX + i * width * 0.18f
            canvas.drawLine(vanishX + i * 6 * density, horizon, bottomX, height.toFloat(), gridPaint)
        }
        // Horizon glow line.
        gridPaint.color = Color.argb(160, 0, 240, 255)
        canvas.drawLine(0f, horizon, width.toFloat(), horizon, gridPaint)
    }

    private fun drawRain(canvas: Canvas, dt: Float) {
        val wind = 0.18f + parallax * 0.05f
        var j = 0
        var i = 0
        while (i < rain.size) {
            var y = rain[i + 1] + rain[i + 3] * dt
            var x = rain[i] - rain[i + 3] * dt * wind
            if (y > height) { y = -rain[i + 2]; x = Random.nextFloat() * (width + 100 * density) }
            if (x < -20 * density) x += width + 40 * density
            rain[i] = x
            rain[i + 1] = y
            val len = rain[i + 2]
            rainLines[j] = x; rainLines[j + 1] = y
            rainLines[j + 2] = x - len * wind; rainLines[j + 3] = y + len
            i += 4
            j += 4
        }
        canvas.drawLines(rainLines, rainPaint)
    }

    private fun drawGlitch(canvas: Canvas, now: Long, horizon: Float) {
        val rnd = Random(now / 50)
        val k = glitchStrength
        val layer = nearLayer ?: return
        val top = (horizon - layer.height).toInt()
        // RGB split of the near skyline.
        val shift = 8 * density * k
        canvas.drawBitmap(layer, -margin + shift, top.toFloat(), crimsonTint)
        canvas.drawBitmap(layer, -margin - shift, top.toFloat(), cyanTint)
        // Displaced horizontal slices.
        repeat(5) {
            val sy = rnd.nextInt(max(1, layer.height))
            val sh = (4 + rnd.nextInt(24)) * density.toInt().coerceAtLeast(1)
            val dx = ((rnd.nextFloat() - 0.5f) * 60 * density * k).toInt()
            val src = Rect(0, sy, layer.width, (sy + sh).coerceAtMost(layer.height))
            val dst = Rect(-margin + dx, top + sy, -margin + dx + layer.width, top + src.bottom)
            canvas.drawBitmap(layer, src, dst, bitmapPaint)
        }
        // Interference bars.
        repeat(4) {
            glitchPaint.color = intArrayOf(0x5500F0FF, 0x55FF003C, 0x44FCEE0A)[rnd.nextInt(3)]
            val y = rnd.nextFloat() * height
            canvas.drawRect(0f, y, width.toFloat(), y + (2 + rnd.nextInt(10)) * density, glitchPaint)
        }
    }

    private fun renderSkyline(seed: Int, heightFrac: Float, color: Int, windowAlpha: Int): Bitmap {
        val w = width + margin * 2
        val h = (height * heightFrac).toInt().coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        val body = Paint().apply { this.color = color }
        val window = Paint()
        var x = 0f
        while (x < w) {
            val bw = (24 + rnd.nextFloat() * 70) * density
            val bh = h * (0.35f + rnd.nextFloat() * 0.65f)
            c.drawRect(x, h - bh, x + bw, h.toFloat(), body)
            if (rnd.nextFloat() < 0.3f) {
                // antenna
                c.drawRect(x + bw / 2 - density, h - bh - 18 * density, x + bw / 2 + density, h - bh, body)
            }
            val cell = 6 * density
            var wy = h - bh + cell
            while (wy < h - cell) {
                var wx = x + cell * 0.6f
                while (wx < x + bw - cell) {
                    if (rnd.nextFloat() < 0.22f) {
                        window.color = if (rnd.nextFloat() < 0.8f) Color.argb(windowAlpha, 0, 240, 255) else Color.argb(windowAlpha, 252, 238, 10)
                        c.drawRect(wx, wy, wx + cell * 0.45f, wy + cell * 0.35f, window)
                    }
                    wx += cell
                }
                wy += cell
            }
            x += bw + rnd.nextFloat() * 6 * density
        }
        return bmp
    }

    fun release() {
        farLayer?.recycle()
        nearLayer?.recycle()
        farLayer = null
        nearLayer = null
    }

    private data class NeonSign(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val color: Int,
        val phase: Float,
        val depth: Float,
    )
}
