package com.nightcity.holoos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nightcity.holoos.core.designsystem.theme.ChamferShape
import com.nightcity.holoos.core.designsystem.theme.HoloColors

@Composable
fun HudSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        val shape = remember { ChamferShape(8.dp) }
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .neonBorder(HoloColors.CyberYellow, shape, width = 1.dp)
                .clip(shape)
                .background(HoloColors.PitchBlack.copy(alpha = 0.95f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("> " + data.visuals.message, style = MaterialTheme.typography.labelLarge, color = HoloColors.CyberYellow)
        }
    }
}
