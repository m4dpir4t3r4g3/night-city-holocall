package com.nightcity.holoos.service.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat

/**
 * Lightweight Canvas live wallpaper (~30 fps while visible, zero work while hidden).
 * Glitch-pulses on unlock (ACTION_USER_PRESENT) and on screen-on.
 */
class HoloWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = NightCityEngine()

    private inner class NightCityEngine : Engine(), SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = NightCityRenderer(resources.displayMetrics.density)
        private val sensorManager = getSystemService(SensorManager::class.java)
        private val gravity: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        private var visible = false
        private val drawRunnable = Runnable { drawFrame() }

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> renderer.triggerGlitch(strength = 1f, durationMs = 700)
                    Intent.ACTION_SCREEN_ON -> renderer.triggerGlitch(strength = 0.5f, durationMs = 300)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(applicationContext, unlockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            runCatching { applicationContext.unregisterReceiver(unlockReceiver) }
            sensorManager?.unregisterListener(this)
            renderer.release()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                gravity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                drawFrame()
            } else {
                sensorManager?.unregisterListener(this)
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.resize(width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            renderer.pageOffset = xOffset
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_DOWN) {
                val w = surfaceHolder.surfaceFrame.width().coerceAtLeast(1)
                renderer.touch(event.x / w)
            }
        }

        override fun onSensorChanged(event: SensorEvent) = renderer.tilt(event.values[0])
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = try {
                    holder.lockHardwareCanvas()
                } catch (_: Exception) {
                    holder.lockCanvas()
                }
                canvas?.let { renderer.draw(it, SystemClock.uptimeMillis()) }
            } catch (_: IllegalStateException) {
                // Surface went away mid-frame.
            } finally {
                canvas?.let { runCatching { holder.unlockCanvasAndPost(it) } }
            }
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.postDelayed(drawRunnable, FRAME_MS)
        }
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
