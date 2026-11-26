package com.nightcity.holoos.feature.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.nightcity.holoos.core.designsystem.theme.HoloTheme
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.domain.repository.PermissionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The HUD home screen (CATEGORY_HOME). Transparent window so the live wallpaper shows through. */
@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    @Inject lateinit var permissions: PermissionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.closeDrawer() -> Unit
                // Opened as a preview from the hub (not the default home): let back leave.
                !permissions.currentState().status(CalibrationStep.DEFAULT_HOME).isSatisfied -> finish()
                else -> Unit // Home screens swallow back.
            }
        }
        setContent { HoloTheme { LauncherRoute(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home closes the drawer / resets the HUD.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
