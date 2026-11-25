package com.nightcity.holoos.feature.setup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nightcity.holoos.R
import com.nightcity.holoos.core.designsystem.component.GlitchText
import com.nightcity.holoos.core.designsystem.component.HudButton
import com.nightcity.holoos.core.designsystem.component.HudButtonStyle
import com.nightcity.holoos.core.designsystem.component.HudIndex
import com.nightcity.holoos.core.designsystem.component.HudPanel
import com.nightcity.holoos.core.designsystem.component.SegmentedBar
import com.nightcity.holoos.core.designsystem.component.StatusTag
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import com.nightcity.holoos.core.resources.bodyRes
import com.nightcity.holoos.core.resources.titleRes
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.domain.model.StepStatus
import com.nightcity.holoos.service.notification.HoloNotifications

@Composable
fun SetupRoute(
    snackbar: SnackbarHostState,
    onOpenLauncher: () -> Unit,
    onOpenAudio: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingStep by rememberSaveable { mutableStateOf<CalibrationStep?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStep?.let(viewModel::onStepReturned)
    }
    val intentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingStep?.let(viewModel::onStepReturned)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SetupEvent.Launch -> {
                    pendingStep = event.step
                    when (val action = CalibrationActions.actionFor(context, event.step, event.attempt)) {
                        is StepAction.RuntimePermission -> permissionLauncher.launch(action.permission)
                        is StepAction.SystemIntent -> {
                            val launched = listOfNotNull(action.intent, action.fallback).any { intent ->
                                try {
                                    intentLauncher.launch(intent)
                                    true
                                } catch (_: ActivityNotFoundException) {
                                    false
                                }
                            }
                            if (!launched) viewModel.onStepReturned(event.step)
                        }
                        StepAction.None -> viewModel.onStepReturned(event.step)
                    }
                }
                is SetupEvent.Message -> snackbar.showSnackbar(event.message.resolve(context))
                SetupEvent.SoundPackApplied -> {
                    HoloNotifications.postLinkEstablished(context)
                    snackbar.showSnackbar(context.getString(R.string.audio_applied, "RINGTONE / NOTIFICATION / ALARM"))
                }
            }
        }
    }

    SetupScreen(
        state = state,
        onOneClick = viewModel::startAutoCalibration,
        onCancel = viewModel::cancelAutoCalibration,
        onEngage = viewModel::engage,
        onInject = viewModel::injectSoundPack,
        onOpenLauncher = onOpenLauncher,
        onOpenAudio = onOpenAudio,
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onOneClick: () -> Unit,
    onCancel: () -> Unit,
    onEngage: (CalibrationStep) -> Unit,
    onInject: () -> Unit,
    onOpenLauncher: () -> Unit,
    onOpenAudio: () -> Unit,
) {
    val steps = CalibrationStep.entries
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                GlitchText(
                    text = stringResource(R.string.setup_title).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HoloColors.NeonCyan,
                )
                Text(
                    text = stringResource(if (state.linkEstablished) R.string.setup_subtitle_done else R.string.setup_subtitle).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.linkEstablished) HoloColors.CyberYellow else HoloColors.TextDim,
                )
            }
        }

        item { SyncPanel(state, onOneClick, onCancel) }

        if (state.linkEstablished) {
            item {
                HudPanel(title = stringResource(R.string.setup_link_established), accent = HoloColors.CyberYellow, pulse = true) {
                    Text(stringResource(R.string.setup_link_body), style = MaterialTheme.typography.bodyMedium, color = HoloColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudButton(stringResource(R.string.setup_enter_hud), onOpenLauncher, accent = HoloColors.CyberYellow, modifier = Modifier.weight(1f))
                        HudButton(stringResource(R.string.tab_audio), onOpenAudio, style = HudButtonStyle.Outline, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        itemsIndexed(steps, key = { _, step -> step.name }) { index, step ->
            StepCard(index + 1, step, state.calibration.status(step), onEngage)
        }

        item {
            HudPanel(title = stringResource(R.string.setup_audio_payload), accent = HoloColors.CyberYellow) {
                Text(stringResource(R.string.setup_audio_payload_body), style = MaterialTheme.typography.bodyMedium, color = HoloColors.TextDim)
                state.lastReport?.let { report ->
                    Spacer(Modifier.height(8.dp))
                    report.applied.forEach {
                        Text("> ${it.name} :: LINKED", style = MaterialTheme.typography.labelMedium, color = HoloColors.NeonCyan)
                    }
                    report.failures.forEach { (slot, why) ->
                        Text("> ${slot.name} :: FAULT $why", style = MaterialTheme.typography.labelMedium, color = HoloColors.CrimsonRed)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HudButton(
                    text = when {
                        state.applying -> stringResource(R.string.setup_running)
                        state.canInject -> stringResource(R.string.setup_inject)
                        else -> stringResource(R.string.setup_inject_needs_write)
                    },
                    onClick = onInject,
                    enabled = state.canInject && !state.applying,
                    accent = HoloColors.CyberYellow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SyncPanel(state: SetupUiState, onOneClick: () -> Unit, onCancel: () -> Unit) {
    val pct = (state.calibration.progress * 100).toInt()
    val accent = if (state.calibration.requiredComplete) HoloColors.NeonCyan else HoloColors.CyberYellow
    HudPanel(accent = accent, pulse = state.autoRunning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.setup_sync).uppercase(), style = MaterialTheme.typography.labelLarge, color = HoloColors.TextDim)
            Spacer(Modifier.width(8.dp))
            Text("$pct%", style = MaterialTheme.typography.headlineLarge, color = accent)
            Spacer(Modifier.weight(1f))
            Text(
                "${state.calibration.statuses.values.count { it.isSatisfied }}/${state.calibration.statuses.size} LINKS",
                style = MaterialTheme.typography.labelMedium,
                color = HoloColors.TextDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        SegmentedBar(fraction = state.calibration.progress, color = accent, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        if (state.autoRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudButton(stringResource(R.string.setup_running), {}, accent = HoloColors.CyberYellow, enabled = false, modifier = Modifier.weight(1f))
                HudButton(stringResource(R.string.cancel), onCancel, style = HudButtonStyle.Outline, accent = HoloColors.CrimsonRed, icon = Icons.Filled.Close)
            }
        } else {
            HudButton(
                text = stringResource(R.string.setup_one_click),
                onClick = onOneClick,
                accent = HoloColors.CyberYellow,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepCard(index: Int, step: CalibrationStep, status: StepStatus, onEngage: (CalibrationStep) -> Unit) {
    val (tagText, tagColor) = when (status) {
        StepStatus.GRANTED -> stringResource(R.string.status_online) to HoloColors.NeonCyan
        StepStatus.AUTO_GRANTED -> stringResource(R.string.status_auto) to HoloColors.NeonCyan
        StepStatus.MISSING -> if (step.required) {
            stringResource(R.string.status_offline) to HoloColors.CrimsonRed
        } else {
            stringResource(R.string.status_optional) to HoloColors.CyberYellow
        }
    }
    val accent = if (status.isSatisfied) HoloColors.NeonCyan.copy(alpha = 0.55f) else tagColor
    HudPanel(accent = accent, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            HudIndex(index, accent)
            Column(Modifier.weight(1f)) {
                Text(stringResource(step.titleRes).uppercase(), style = MaterialTheme.typography.titleMedium, color = HoloColors.TextPrimary)
                Text(stringResource(step.bodyRes), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
            }
            Spacer(Modifier.width(8.dp))
            StatusTag(tagText, tagColor, filled = status.isSatisfied)
        }
        if (status == StepStatus.MISSING) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HudButton(
                    text = stringResource(R.string.setup_engage),
                    onClick = { onEngage(step) },
                    accent = tagColor,
                    style = HudButtonStyle.Outline,
                    compact = true,
                )
            }
        }
    }
}

