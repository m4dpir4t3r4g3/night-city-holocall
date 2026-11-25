package com.nightcity.holoos.feature.hub

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nightcity.holoos.R
import com.nightcity.holoos.core.designsystem.component.GlitchText
import com.nightcity.holoos.core.designsystem.component.GlitchTransitionHost
import com.nightcity.holoos.core.designsystem.component.HoloBackdrop
import com.nightcity.holoos.core.designsystem.component.HudButton
import com.nightcity.holoos.core.designsystem.component.HudButtonStyle
import com.nightcity.holoos.core.designsystem.component.HudSnackbarHost
import com.nightcity.holoos.core.designsystem.component.ScanlineOverlay
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import com.nightcity.holoos.feature.audio.AudioRoute
import com.nightcity.holoos.feature.overrides.OverridesRoute
import com.nightcity.holoos.feature.setup.SetupRoute

enum class HubDestination(val route: String, @StringRes val label: Int, val code: String) {
    Calibrate("calibrate", R.string.tab_calibrate, "01"),
    Audio("audio", R.string.tab_audio, "02"),
    Overrides("overrides", R.string.tab_overrides, "03"),
}

@Composable
fun HoloHubApp(onOpenLauncher: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: HubDestination.Calibrate.route
    val snackbar = remember { SnackbarHostState() }

    HoloBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { HubTopBar(onOpenLauncher) },
            bottomBar = {
                HudTabBar(current = route) { dest ->
                    nav.navigate(dest.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            snackbarHost = { HudSnackbarHost(snackbar) },
        ) { padding ->
            GlitchTransitionHost(key = route, modifier = Modifier.padding(padding).fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = HubDestination.Calibrate.route,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(120)) },
                ) {
                    composable(HubDestination.Calibrate.route) {
                        SetupRoute(
                            snackbar = snackbar,
                            onOpenLauncher = onOpenLauncher,
                            onOpenAudio = { nav.navigate(HubDestination.Audio.route) { launchSingleTop = true } },
                        )
                    }
                    composable(HubDestination.Audio.route) { AudioRoute(snackbar) }
                    composable(HubDestination.Overrides.route) { OverridesRoute(snackbar) }
                }
            }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun HubTopBar(onOpenLauncher: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            GlitchText(
                text = "HOLO-OS",
                style = MaterialTheme.typography.headlineLarge,
                color = HoloColors.NeonCyan,
            )
            Text(
                text = stringResource(R.string.hud_brand).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HoloColors.CyberYellow,
            )
        }
        HudButton(
            text = stringResource(R.string.open_hud),
            onClick = onOpenLauncher,
            style = HudButtonStyle.Outline,
            icon = Icons.Filled.Home,
            compact = true,
        )
    }
}

@Composable
private fun HudTabBar(current: String, onSelect: (HubDestination) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HoloColors.PitchBlack.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HubDestination.entries.forEach { dest ->
            val selected = dest.route == current
            val accent = if (selected) HoloColors.CyberYellow else HoloColors.TextDim
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(dest) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp)
                        .background(if (selected) HoloColors.CyberYellow else Color.Transparent),
                )
                Text(dest.code, style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(dest.label).uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}
