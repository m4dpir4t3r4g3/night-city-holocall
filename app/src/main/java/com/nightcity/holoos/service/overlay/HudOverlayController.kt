package com.nightcity.holoos.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.core.content.res.ResourcesCompat
import com.nightcity.holoos.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Transient "Relic Malfunction" glitch banner drawn over other apps (SYSTEM_ALERT_WINDOW).
 * Non-focusable, non-touchable and kept under the 0.8 obscuring-opacity limit so touches
 * pass straight through to the app underneath (Android 12+ untrusted-touch rules).
 */
@Singleton
class HudOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    @MainThread
    fun showRelicBanner(batteryPercent: Int) {
        if (!canShow()) return
        dismiss()
        val view = RelicBannerView(context, batteryPercent)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            alpha = 0.78f
            y = (48 * context.resources.displayMetrics.density).toInt()
        }
        try {
            windowManager.addView(view, params)
            current = view
            handler.postDelayed({ dismiss() }, BANNER_MS)
        } catch (_: Exception) {
            // Permission revoked between the check and addView, or window token issues.
        }
    }

    @MainThread
    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        current?.let { runCatching { windowManager.removeView(it) } }
        current = null
    }

    private companion object {
        const val BANNER_MS = 4_500L
    }
}

@SuppressLint("ViewConstructor")
private class RelicBannerView(context: Context, private val percent: Int) : View(context) {
    private val density = resources.displayMetrics.density
    private val start = SystemClock.uptimeMillis()
    private val crimson = 0xFFFF003C.toInt()
    private val cyan = 0xFF00F0FF.toInt()
    private val title = context.getString(R.string.relic_banner_title).uppercase()
    private val body = context.getString(R.string.relic_banner_body, percent).uppercase()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2050508.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = crimson
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.orbitron_black)
        textSize = 18 * density
        color = crimson
        letterSpacing = 0.08f
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.share_tech_mono_regular)
        textSize = 12 * density
        color = 0xFFE6FBFF.toInt()
        letterSpacing = 0.1f
    }
    private val bar = Paint().apply { color = crimson }
    private val path = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (76 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() - start
        val rnd = Random(t / 60)
        val glitching = rnd.nextFloat() < 0.35f
        val m = 12 * density
        val cut = 14 * density
        val w = width - m
        val h = height.toFloat() - 4 * density
        path.reset()
        path.moveTo(m + cut, 4 * density)
        path.lineTo(w, 4 * density)
        path.lineTo(w, h - cut)
        path.lineTo(w - cut, h)
        path.lineTo(m, h)
        path.lineTo(m, 4 * density + cut)
        path.close()
        canvas.drawPath(path, fill)
        stroke.alpha = if (glitching) 255 else 200
        canvas.drawPath(path, stroke)

        val dx = if (glitching) (rnd.nextFloat() - 0.5f) * 10 * density else 0f
        val tx = m + 20 * density
        val ty = 34 * density
        if (glitching) {
            titlePaint.color = cyan
            canvas.drawText(title, tx + dx + 3 * density, ty, titlePaint)
            titlePaint.color = crimson
        }
        canvas.drawText(title, tx + dx, ty, titlePaint)
        canvas.drawText(body, tx, ty + 22 * density, bodyPaint)

        // Interference bars
        repeat(if (glitching) 3 else 1) {
            val y = 6 * density + rnd.nextFloat() * (h - 10 * density)
            val bw = rnd.nextFloat() * (w - m) * 0.6f
            val bx = m + rnd.nextFloat() * (w - m - bw)
            bar.alpha = 90 + rnd.nextInt(120)
            canvas.drawRect(bx, y, bx + bw, y + 2 * density, bar)
        }
        if (t < 5_000) postInvalidateOnAnimation()
    }
}
