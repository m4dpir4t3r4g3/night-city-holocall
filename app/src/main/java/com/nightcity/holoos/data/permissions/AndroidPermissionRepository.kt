package com.nightcity.holoos.data.permissions

import android.Manifest
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nightcity.holoos.domain.model.CalibrationState
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.domain.model.StepStatus
import com.nightcity.holoos.domain.repository.PermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionRepository {

    override fun currentState(): CalibrationState =
        CalibrationState(CalibrationStep.entries.associateWith(::status))

    private fun status(step: CalibrationStep): StepStatus = when (step) {
        CalibrationStep.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) StepStatus.AUTO_GRANTED
            else granted(Manifest.permission.POST_NOTIFICATIONS).toStatus()

        CalibrationStep.NOTIFICATION_LISTENER ->
            (context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)).toStatus()

        CalibrationStep.WRITE_SETTINGS -> Settings.System.canWrite(context).toStatus()

        CalibrationStep.ALARMS ->
            // Normal (install-time) permission: always granted when declared.
            if (granted("com.android.alarm.permission.SET_ALARM")) StepStatus.AUTO_GRANTED else StepStatus.MISSING

        CalibrationStep.OVERLAY -> Settings.canDrawOverlays(context).toStatus()

        CalibrationStep.CONTACTS -> granted(Manifest.permission.READ_CONTACTS).toStatus()

        CalibrationStep.LIVE_WALLPAPER ->
            (WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName).toStatus()

        CalibrationStep.DEFAULT_HOME -> isDefaultHome().toStatus()
    }

    private fun isDefaultHome(): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Boolean.toStatus() = if (this) StepStatus.GRANTED else StepStatus.MISSING
}
