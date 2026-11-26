package com.nightcity.holoos.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserManager
import android.util.LruCache
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.IconTint
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads and caches HUD-styled bitmaps for app icons, dock targets and contact photos. */
@Singleton
class IconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stylizer: IconStylizer,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val density = context.resources.displayMetrics.densityDpi
    private val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun appIcon(entry: AppEntry, tint: IconTint): Bitmap? = cached("app|${entry.key}|$tint") {
        val user = userManager.getUserForSerialNumber(entry.userSerial) ?: return@cached null
        val info = launcherApps.resolveActivity(
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(entry.packageName, entry.activityName)),
            user,
        ) ?: return@cached null
        val icon = info.getIcon(density)
        val badged = if (entry.isWorkProfile) context.packageManager.getUserBadgedIcon(icon, user) else icon
        stylizer.stylize(badged, iconPx, tint)
    }

    suspend fun packageIcon(packageName: String, tint: IconTint): Bitmap? = cached("pkg|$packageName|$tint") {
        val icon = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return@cached null
        }
        stylizer.stylize(icon, iconPx, tint)
    }

    suspend fun contactPhoto(photoUri: String, tint: IconTint): Bitmap? = cached("photo|$photoUri|$tint") {
        val bitmap = context.contentResolver.openInputStream(photoUri.toUri())?.use(BitmapFactory::decodeStream)
            ?: return@cached null
        stylizer.stylize(bitmap, tint, recycleSource = true)
    }

    fun clear() = cache.evictAll()

    private suspend fun cached(key: String, load: () -> Bitmap?): Bitmap? {
        cache.get(key)?.let { return it }
        return withContext(io) { runCatching(load).getOrNull()?.also { cache.put(key, it) } }
    }

    private companion object {
        const val ICON_DP = 56
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
