package com.nightcity.holoos.feature.setup

import android.Manifest
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.service.notification.HoloNotificationListener
import com.nightcity.holoos.service.wallpaper.HoloWallpaperService

sealed interface StepAction {
    data class RuntimePermission(val permission: String) : StepAction
    data class SystemIntent(val intent: Intent, val fallback: Intent?) : StepAction
    data object None : StepAction
}

/**
 * Maps each calibration step to the system UI that grants it. [attempt] escalates to a
 * settings screen when a runtime prompt was permanently denied or the role dialog dismissed.
 */
object CalibrationActions {
    fun actionFor(context: Context, step: CalibrationStep, attempt: Int): StepAction {
        val pkgUri = "package:${context.packageName}".toUri()
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        return when (step) {
            CalibrationStep.NOTIFICATIONS -> when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> StepAction.None
                attempt <= 2 -> StepAction.RuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
                else -> StepAction.SystemIntent(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    appDetails,
                )
            }

            CalibrationStep.NOTIFICATION_LISTENER -> {
                val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val component = ComponentName(context, HoloNotificationListener::class.java).flattenToString()
                    StepAction.SystemIntent(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
                        list,
                    )
                } else {
                    StepAction.SystemIntent(list, null)
                }
            }

            CalibrationStep.WRITE_SETTINGS ->
                StepAction.SystemIntent(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri), appDetails)

            CalibrationStep.ALARMS -> StepAction.None

            CalibrationStep.OVERLAY ->
                StepAction.SystemIntent(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri), appDetails)

            CalibrationStep.CONTACTS ->
                if (attempt <= 2) StepAction.RuntimePermission(Manifest.permission.READ_CONTACTS)
                else StepAction.SystemIntent(appDetails, null)

            CalibrationStep.LIVE_WALLPAPER -> StepAction.SystemIntent(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, HoloWallpaperService::class.java),
                ),
                Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
            )

            CalibrationStep.DEFAULT_HOME -> {
                val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (attempt <= 1 && roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                    StepAction.SystemIntent(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), homeSettings)
                } else {
                    StepAction.SystemIntent(homeSettings, Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
    }
}
