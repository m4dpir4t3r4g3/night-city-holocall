package com.nightcity.holoos.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.DockSlot
import com.nightcity.holoos.domain.repository.AppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Launchable activities for every profile (personal + work), live-updated on installs. */
@Singleton
class LauncherAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AppRepository {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    override fun observeApps(): Flow<List<AppEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        trySend(Unit)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate().map { loadApps() }.flowOn(io)

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                AppEntry(
                    packageName = info.componentName.packageName,
                    activityName = info.componentName.className,
                    label = info.label?.toString().orEmpty(),
                    userSerial = serial,
                    isWorkProfile = user != me,
                )
            }
        }.sortedWith(compareBy({ it.label.lowercase() }, { it.isWorkProfile }))
    }

    override fun launch(entry: AppEntry): Boolean = runCatching {
        launcherApps.startMainActivity(entry.component(), entry.user(), null, null)
    }.onFailure { Log.w(TAG, "launch failed for ${entry.key}", it) }.isSuccess

    override fun openAppInfo(entry: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component(), entry.user(), null, null) }
    }

    override fun uninstall(entry: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, "package:${entry.packageName}".toUri())
                    .putExtra(Intent.EXTRA_USER, entry.user())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun AppEntry.component() = ComponentName(packageName, activityName)
    private fun AppEntry.user(): UserHandle = userManager.getUserForSerialNumber(userSerial) ?: Process.myUserHandle()

    private companion object {
        const val TAG = "HoloApps"
    }
}

/** Default handlers behind the four dock buttons, resolved for icon styling. */
@Singleton
class DockResolver @Inject constructor(@ApplicationContext private val context: Context) {

    fun launchIntent(slot: DockSlot): Intent = when (slot) {
        DockSlot.PHONE -> Intent(Intent.ACTION_DIAL)
        DockSlot.MESSAGES -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        DockSlot.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        DockSlot.BROWSER -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Package of the user's default app for [slot], or null if there is no single default. */
    fun defaultPackage(slot: DockSlot): String? {
        val probe = when (slot) {
            DockSlot.PHONE -> Intent(Intent.ACTION_DIAL)
            DockSlot.MESSAGES -> Intent(Intent.ACTION_SENDTO, "smsto:".toUri())
            DockSlot.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            DockSlot.BROWSER -> Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        }
        val pkg = context.packageManager
            .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        // "android" means the disambiguation chooser: no default set.
        return pkg?.takeUnless { it == "android" }
    }
}
