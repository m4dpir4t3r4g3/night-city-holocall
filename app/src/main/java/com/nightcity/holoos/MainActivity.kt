package com.nightcity.holoos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nightcity.holoos.core.designsystem.theme.HoloTheme
import com.nightcity.holoos.feature.hub.HoloHubApp
import com.nightcity.holoos.feature.launcher.LauncherActivity
import dagger.hilt.android.AndroidEntryPoint

/** Setup wizard + control hub (Calibrate / Audio / Overrides). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HoloTheme {
                HoloHubApp(onOpenLauncher = { startActivity(Intent(this, LauncherActivity::class.java)) })
            }
        }
    }
}
