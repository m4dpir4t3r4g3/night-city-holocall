package com.nightcity.holoos.data.overrides

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import com.nightcity.holoos.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders an original HUD-grid keyboard backdrop (Gboard "My image" themes take a picture,
 * not hex codes) and saves it to Pictures/NightCityHoloOS.
 */
@Singleton
class KeyboardBackdropExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun export(): Result<Uri> = withContext(io) {
        runCatching {
            val bitmap = render(1600, 1000)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "holo_keyboard_backdrop_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NightCityHoloOS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("No output stream")
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            } finally {
                bitmap.recycle()
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        }
    }

    private fun render(w: Int, h: Int): Bitmap {
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0D1117.toInt(), 0xFF050508.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Tactical grid
        paint.strokeWidth = 2f
        paint.color = 0x2200F0FF
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h.toFloat(), paint); x += 80f }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w.toFloat(), y, paint); y += 80f }

        // Horizon glow band
        paint.shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.75f, 0x3300F0FF, 0x0000F0FF, Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.55f, w.toFloat(), h * 0.75f, paint)
        paint.shader = null

        // Chamfered frame + accent bars
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFF00F0FF.toInt()
        val cut = 48f
        val inset = 24f
        val frame = Path().apply {
            moveTo(inset + cut, inset)
            lineTo(w - inset, inset)
            lineTo(w - inset, h - inset - cut)
            lineTo(w - inset - cut, h - inset)
            lineTo(inset, h - inset)
            lineTo(inset, inset + cut)
            close()
        }
        c.drawPath(frame, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFCEE0A.toInt()
        c.drawRect(inset + cut + 20f, inset - 6f, inset + cut + 220f, inset + 6f, paint)
        paint.color = 0xFFFF003C.toInt()
        c.drawRect(w - inset - 160f, h - inset - 6f, w - inset - cut - 20f, h - inset + 6f, paint)
        return bmp
    }
}
