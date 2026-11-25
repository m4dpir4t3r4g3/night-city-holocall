package com.nightcity.holoos.feature.overrides

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nightcity.holoos.R
import com.nightcity.holoos.core.designsystem.component.GlitchText
import com.nightcity.holoos.core.designsystem.component.HudButton
import com.nightcity.holoos.core.designsystem.component.HudButtonStyle
import com.nightcity.holoos.core.designsystem.component.HudIndex
import com.nightcity.holoos.core.designsystem.component.HudPanel
import com.nightcity.holoos.core.designsystem.component.neonBorder
import com.nightcity.holoos.core.designsystem.theme.ChamferShape
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import com.nightcity.holoos.core.designsystem.theme.PaletteSwatch
import kotlinx.coroutines.launch

@Composable
fun OverridesRoute(snackbar: SnackbarHostState, viewModel: OverridesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }

    OverridesScreen(
        state = state,
        onOpen = { item ->
            val intent = Intent(item.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { context.startActivity(intent) }.isSuccess ||
                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!opened) scope.launch { snackbar.showSnackbar(context.getString(R.string.overrides_unavailable)) }
        },
        onToggleDone = viewModel::setDone,
        onCopy = { swatch ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(swatch.label, swatch.hex))
            scope.launch { snackbar.showSnackbar(context.getString(R.string.overrides_copied, swatch.hex)) }
        },
        onExportBackdrop = viewModel::exportBackdrop,
    )
}

@Composable
fun OverridesScreen(
    state: OverridesUiState,
    onOpen: (ManualOverride) -> Unit,
    onToggleDone: (ManualOverride, Boolean) -> Unit,
    onCopy: (PaletteSwatch) -> Unit,
    onExportBackdrop: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                GlitchText(stringResource(R.string.overrides_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.overrides_subtitle).uppercase(), style = MaterialTheme.typography.labelMedium, color = HoloColors.TextDim)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.completed.size}/${ManualOverride.entries.size} COMPLETE",
                    style = MaterialTheme.typography.labelLarge,
                    color = HoloColors.CyberYellow,
                )
            }
        }
        item { PalettePanel(onCopy) }
        item {
            HudPanel(title = stringResource(R.string.overrides_backdrop), accent = HoloColors.CyberYellow) {
                Text(stringResource(R.string.overrides_backdrop_body), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
                Spacer(Modifier.height(10.dp))
                HudButton(stringResource(R.string.overrides_backdrop_export), onExportBackdrop, accent = HoloColors.CyberYellow, icon = Icons.Filled.Create, compact = true)
            }
        }
        itemsIndexed(ManualOverride.entries, key = { _, it -> it.name }) { index, item ->
            val done = item.name in state.completed
            val accent = if (done) HoloColors.NeonCyan.copy(alpha = 0.5f) else HoloColors.NeonCyan
            HudPanel(accent = accent, contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudIndex(index + 1, accent)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.title).uppercase(), style = MaterialTheme.typography.titleMedium, color = HoloColors.TextPrimary)
                        Text(stringResource(item.body), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
                    }
                    Checkbox(
                        checked = done,
                        onCheckedChange = { onToggleDone(item, it) },
                        colors = CheckboxDefaults.colors(checkedColor = HoloColors.NeonCyan, checkmarkColor = HoloColors.PitchBlack, uncheckedColor = HoloColors.TextDim),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    @Suppress("DEPRECATION")
                    HudButton(stringResource(R.string.overrides_open), { onOpen(item) }, compact = true, style = HudButtonStyle.Outline, icon = Icons.Filled.ArrowForward)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PalettePanel(onCopy: (PaletteSwatch) -> Unit) {
    HudPanel(title = stringResource(R.string.overrides_palette)) {
        Text(stringResource(R.string.overrides_palette_body), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
        Spacer(Modifier.height(10.dp))
        PaletteSwatch.entries.forEach { swatch ->
            val shape = remember { ChamferShape(6.dp) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .neonBorder(HoloColors.NeonCyan.copy(alpha = 0.6f), shape, width = 1.dp, glow = false)
                        .clip(shape)
                        .background(swatch.color),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(swatch.label.uppercase(), style = MaterialTheme.typography.titleSmall, color = HoloColors.TextPrimary)
                    Text(swatch.hex, style = MaterialTheme.typography.labelLarge, color = swatch.color.takeIf { swatch != PaletteSwatch.PITCH_BLACK && swatch != PaletteSwatch.DARK_SLATE } ?: HoloColors.TextDim)
                }
                HudButton("COPY", { onCopy(swatch) }, compact = true, style = HudButtonStyle.Ghost, icon = Icons.Filled.Share)
            }
        }
    }
}

