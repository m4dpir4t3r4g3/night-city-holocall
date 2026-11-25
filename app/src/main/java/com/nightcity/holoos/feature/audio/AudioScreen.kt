package com.nightcity.holoos.feature.audio

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.provider.AlarmClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nightcity.holoos.R
import com.nightcity.holoos.core.designsystem.component.GlitchText
import com.nightcity.holoos.core.designsystem.component.HudButton
import com.nightcity.holoos.core.designsystem.component.HudButtonStyle
import com.nightcity.holoos.core.designsystem.component.HudDivider
import com.nightcity.holoos.core.designsystem.component.HudIndex
import com.nightcity.holoos.core.designsystem.component.HudPanel
import com.nightcity.holoos.core.designsystem.component.HudToggleRow
import com.nightcity.holoos.core.designsystem.component.StatusTag
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import com.nightcity.holoos.core.resources.titleRes
import com.nightcity.holoos.core.resources.triggerRes
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CueSlot
import com.nightcity.holoos.domain.model.CueSource
import com.nightcity.holoos.domain.model.HoloToggle

@Composable
fun AudioRoute(snackbar: SnackbarHostState, viewModel: AudioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImport by rememberSaveable { mutableStateOf<AudioCue?>(null) }
    var showFinanceDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cue = pendingImport
        if (uri != null && cue != null) viewModel.import(cue, uri.toString())
        pendingImport = null
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AudioEvent.Message -> snackbar.showSnackbar(event.message.resolve(context))
                is AudioEvent.LaunchAlarm -> {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, event.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, event.minute)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.cue_alarm_title))
                        .putExtra(AlarmClock.EXTRA_VIBRATE, true)
                        .putExtra(AlarmClock.EXTRA_RINGTONE, event.toneUri)
                        .apply {
                            // Best-effort read grant for clock apps without media permission. The
                            // tone is also the system default alarm once the pack is applied.
                            clipData = ClipData.newRawUri("", event.toneUri.toUri())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(context.getString(R.string.audio_alarm_failed))
                    }
                }
            }
        }
    }

    AudioScreen(
        state = state,
        onApply = viewModel::applyToSystem,
        onPreview = viewModel::togglePreview,
        onImport = { cue ->
            pendingImport = cue
            picker.launch(arrayOf("audio/*"))
        },
        onReset = viewModel::reset,
        onScheduleAlarm = { showAlarmDialog = true },
        onToggle = viewModel::setToggle,
        onEditFinance = { showFinanceDialog = true },
        onSaveKeywords = viewModel::setGigKeywords,
    )

    if (showFinanceDialog) {
        FinanceAppsDialog(state.financeApps, onToggle = viewModel::toggleFinanceApp, onDismiss = { showFinanceDialog = false })
    }
    if (showAlarmDialog) {
        AlarmTimeDialog(
            onConfirm = { h, m ->
                showAlarmDialog = false
                viewModel.scheduleAlarm(h, m)
            },
            onDismiss = { showAlarmDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioScreen(
    state: AudioUiState,
    onApply: () -> Unit,
    onPreview: (AudioCue) -> Unit,
    onImport: (AudioCue) -> Unit,
    onReset: (AudioCue) -> Unit,
    onScheduleAlarm: () -> Unit,
    onToggle: (HoloToggle, Boolean) -> Unit,
    onEditFinance: () -> Unit,
    onSaveKeywords: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                GlitchText(stringResource(R.string.audio_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.audio_subtitle).uppercase(), style = MaterialTheme.typography.labelMedium, color = HoloColors.TextDim)
            }
        }
        item {
            HudPanel(title = stringResource(R.string.audio_apply), accent = HoloColors.CyberYellow) {
                Text(stringResource(R.string.audio_apply_body), style = MaterialTheme.typography.bodyMedium, color = HoloColors.TextDim)
                if (!state.canWriteSettings) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.audio_needs_write), style = MaterialTheme.typography.labelMedium, color = HoloColors.CrimsonRed)
                }
                Spacer(Modifier.height(12.dp))
                HudButton(
                    text = if (state.applying) stringResource(R.string.setup_running) else stringResource(R.string.audio_apply),
                    onClick = onApply,
                    enabled = state.canWriteSettings && !state.applying,
                    accent = HoloColors.CyberYellow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        itemsIndexed(state.slots, key = { _, slot -> slot.cue.name }) { index, slot ->
            CueCard(
                index = index + 1,
                slot = slot,
                previewing = state.previewing == slot.cue,
                onPreview = { onPreview(slot.cue) },
                onImport = { onImport(slot.cue) },
                onReset = { onReset(slot.cue) },
                onScheduleAlarm = onScheduleAlarm.takeIf { slot.cue == AudioCue.APARTMENT_ALARM },
            )
        }
        item {
            HudPanel(title = stringResource(R.string.audio_triggers)) {
                val toggles = listOf(
                    HoloToggle.HAPTICS to R.string.toggle_haptics,
                    HoloToggle.FINANCE_CUE to R.string.toggle_finance,
                    HoloToggle.GIG_CUE to R.string.toggle_gig,
                    HoloToggle.POWER_CUES to R.string.toggle_power,
                    HoloToggle.RELIC_BANNER to R.string.toggle_relic_banner,
                )
                toggles.forEach { (toggle, label) ->
                    HudToggleRow(
                        label = stringResource(label),
                        checked = state.prefs.isEnabled(toggle),
                        onCheckedChange = { onToggle(toggle, it) },
                        enabled = toggle != HoloToggle.RELIC_BANNER || state.prefs.isEnabled(HoloToggle.POWER_CUES),
                    )
                }
            }
        }
        item {
            HudPanel(
                title = stringResource(R.string.audio_finance_apps),
                accent = HoloColors.CyberYellow,
                trailing = { HudButton(stringResource(R.string.audio_finance_edit), onEditFinance, compact = true, style = HudButtonStyle.Outline, accent = HoloColors.CyberYellow) },
            ) {
                Text(stringResource(R.string.audio_finance_apps_body), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.financeApps.filter { it.enabled }.forEach { StatusTag(it.entry.label, HoloColors.CyberYellow) }
                }
            }
        }
        item { GigKeywordsPanel(state.prefs.gigKeywords, onSaveKeywords) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CueCard(
    index: Int,
    slot: CueSlot,
    previewing: Boolean,
    onPreview: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onScheduleAlarm: (() -> Unit)?,
) {
    val (accent, sourceText) = when (val source = slot.source) {
        is CueSource.Custom -> HoloColors.CyberYellow to stringResource(R.string.audio_source_custom, source.displayName)
        is CueSource.Bundled -> HoloColors.NeonCyan to stringResource(R.string.audio_source_bundled, source.fileName)
        CueSource.Synthesized -> HoloColors.CyanDim to stringResource(R.string.audio_source_synth)
    }
    HudPanel(accent = accent, pulse = previewing, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            HudIndex(index, accent)
            Column(Modifier.weight(1f)) {
                Text(stringResource(slot.cue.titleRes).uppercase(), style = MaterialTheme.typography.titleMedium, color = HoloColors.TextPrimary)
                Text(stringResource(slot.cue.triggerRes), style = MaterialTheme.typography.bodySmall, color = HoloColors.TextDim)
            }
            slot.cue.systemSlot?.let { StatusTag(it.name, HoloColors.NeonCyan) }
        }
        Spacer(Modifier.height(6.dp))
        HudDivider()
        Spacer(Modifier.height(6.dp))
        Text("> $sourceText", style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 2)
        Text("  res/raw/${slot.cue.rawResourceName}.ogg", style = MaterialTheme.typography.labelSmall, color = HoloColors.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(
                text = stringResource(if (previewing) R.string.audio_stop else R.string.audio_preview),
                onClick = onPreview,
                compact = true,
                icon = Icons.Filled.PlayArrow,
                accent = accent,
            )
            HudButton(stringResource(R.string.audio_import), onImport, compact = true, style = HudButtonStyle.Outline, icon = Icons.Filled.Add)
            if (slot.source is CueSource.Custom) {
                HudButton(stringResource(R.string.audio_reset), onReset, compact = true, style = HudButtonStyle.Ghost, accent = HoloColors.CrimsonRed, icon = Icons.Filled.Refresh)
            }
        }
        if (onScheduleAlarm != null) {
            Spacer(Modifier.height(8.dp))
            HudButton(
                text = stringResource(R.string.audio_schedule_alarm),
                onClick = onScheduleAlarm,
                compact = true,
                style = HudButtonStyle.Outline,
                accent = HoloColors.CyberYellow,
                icon = Icons.Filled.DateRange,
            )
        }
    }
}

@Composable
private fun GigKeywordsPanel(current: Set<String>, onSave: (String) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(current.joinToString(", ")) }
    HudPanel(title = stringResource(R.string.audio_gig_keywords)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.labelLarge,
            placeholder = { Text(stringResource(R.string.audio_gig_keywords_hint), style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            colors = hudTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            HudButton(stringResource(R.string.done), { onSave(text) }, compact = true, icon = Icons.Filled.Check)
        }
    }
}

@Composable
fun hudTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HoloColors.NeonCyan,
    unfocusedBorderColor = HoloColors.GridLine,
    cursorColor = HoloColors.CyberYellow,
    focusedTextColor = HoloColors.TextPrimary,
    unfocusedTextColor = HoloColors.TextPrimary,
    focusedContainerColor = HoloColors.PitchBlack,
    unfocusedContainerColor = HoloColors.PitchBlack,
    focusedPlaceholderColor = HoloColors.TextDim,
    unfocusedPlaceholderColor = HoloColors.TextDim,
)

@Composable
private fun FinanceAppsDialog(apps: List<FinanceAppUi>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { query.isBlank() || it.entry.label.contains(query, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HoloColors.DarkSlate,
        title = { Text(stringResource(R.string.audio_finance_apps).uppercase(), style = MaterialTheme.typography.headlineSmall, color = HoloColors.CyberYellow) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    textStyle = MaterialTheme.typography.labelLarge,
                    colors = hudTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.entry.packageName }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.entry.packageName) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.enabled,
                                onCheckedChange = { onToggle(app.entry.packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = HoloColors.CyberYellow, checkmarkColor = HoloColors.PitchBlack, uncheckedColor = HoloColors.TextDim),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.entry.label, style = MaterialTheme.typography.titleSmall, color = HoloColors.TextPrimary)
                                Text(app.entry.packageName, style = MaterialTheme.typography.labelSmall, color = HoloColors.TextDim, maxLines = 1)
                            }
                            if (app.detected) {
                                Spacer(Modifier.width(6.dp))
                                StatusTag("auto", HoloColors.NeonCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { HudButton(stringResource(R.string.done), onDismiss, compact = true, accent = HoloColors.CyberYellow) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val time = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HoloColors.DarkSlate,
        title = { Text(stringResource(R.string.audio_schedule_alarm).uppercase(), style = MaterialTheme.typography.headlineSmall, color = HoloColors.CyberYellow) },
        text = {
            TimePicker(
                state = time,
                colors = TimePickerDefaults.colors(
                    clockDialColor = HoloColors.PitchBlack,
                    selectorColor = HoloColors.NeonCyan,
                    containerColor = HoloColors.DarkSlate,
                    timeSelectorSelectedContainerColor = HoloColors.CyanDim,
                    timeSelectorUnselectedContainerColor = HoloColors.PitchBlack,
                    timeSelectorSelectedContentColor = HoloColors.TextPrimary,
                    timeSelectorUnselectedContentColor = HoloColors.TextDim,
                    clockDialSelectedContentColor = HoloColors.PitchBlack,
                    clockDialUnselectedContentColor = HoloColors.TextPrimary,
                ),
            )
        },
        confirmButton = { HudButton(stringResource(R.string.confirm), { onConfirm(time.hour, time.minute) }, compact = true, accent = HoloColors.CyberYellow) },
        dismissButton = { HudButton(stringResource(R.string.cancel), onDismiss, compact = true, style = HudButtonStyle.Ghost, accent = HoloColors.TextDim) },
    )
}

