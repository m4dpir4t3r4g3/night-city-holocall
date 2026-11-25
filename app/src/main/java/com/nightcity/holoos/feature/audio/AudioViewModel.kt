package com.nightcity.holoos.feature.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nightcity.holoos.R
import com.nightcity.holoos.core.ui.UiMessage
import com.nightcity.holoos.domain.logic.NotificationCueClassifier
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CuePlayback
import com.nightcity.holoos.domain.model.CueSlot
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.repository.AppRepository
import com.nightcity.holoos.domain.repository.AudioCueRepository
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.repository.SystemSoundRepository
import com.nightcity.holoos.domain.usecase.ApplySoundPackUseCase
import com.nightcity.holoos.domain.usecase.PlayCueUseCase
import com.nightcity.holoos.domain.usecase.PrepareAlarmToneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FinanceAppUi(val entry: AppEntry, val enabled: Boolean, val detected: Boolean)

data class AudioUiState(
    val slots: List<CueSlot> = emptyList(),
    val prefs: HoloPreferences = HoloPreferences(),
    val canWriteSettings: Boolean = false,
    val applying: Boolean = false,
    val previewing: AudioCue? = null,
    val financeApps: List<FinanceAppUi> = emptyList(),
)

sealed interface AudioEvent {
    data class Message(val message: UiMessage) : AudioEvent
    data class LaunchAlarm(val toneUri: String, val hour: Int, val minute: Int) : AudioEvent
}

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val audio: AudioCueRepository,
    private val playCue: PlayCueUseCase,
    private val applySoundPack: ApplySoundPackUseCase,
    private val prepareAlarmTone: PrepareAlarmToneUseCase,
    private val preferences: PreferencesRepository,
    private val systemSounds: SystemSoundRepository,
    private val classifier: NotificationCueClassifier,
    apps: AppRepository,
) : ViewModel() {

    private data class Local(val canWrite: Boolean, val applying: Boolean = false, val previewing: AudioCue? = null)

    private val local = MutableStateFlow(Local(canWrite = systemSounds.canWriteSystemSettings()))
    private val _events = Channel<AudioEvent>(Channel.BUFFERED)
    val events: Flow<AudioEvent> = _events.receiveAsFlow()
    private var previewJob: Job? = null

    val state: StateFlow<AudioUiState> = combine(
        audio.observeSlots(),
        preferences.preferences,
        apps.observeApps(),
        local,
    ) { slots, prefs, appList, l ->
        AudioUiState(
            slots = slots,
            prefs = prefs,
            canWriteSettings = l.canWrite,
            applying = l.applying,
            previewing = l.previewing,
            financeApps = appList.filterNot { it.isWorkProfile }.distinctBy { it.packageName }.map {
                FinanceAppUi(it, classifier.isFinanceApp(it.packageName, prefs), classifier.matchesFinanceHeuristic(it.packageName))
            }.sortedWith(compareByDescending<FinanceAppUi> { it.enabled }.thenBy { it.entry.label.lowercase() }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioUiState())

    fun refresh() = local.update { it.copy(canWrite = systemSounds.canWriteSystemSettings()) }

    fun togglePreview(cue: AudioCue) {
        previewJob?.cancel()
        if (local.value.previewing == cue) {
            playCue.stopPreview()
            local.update { it.copy(previewing = null) }
            return
        }
        local.update { it.copy(previewing = cue) }
        previewJob = viewModelScope.launch {
            playCue(cue, CuePlayback.PREVIEW)
            delay(PREVIEW_INDICATOR_MS)
            local.update { if (it.previewing == cue) it.copy(previewing = null) else it }
        }
    }

    fun import(cue: AudioCue, documentUri: String) = viewModelScope.launch {
        audio.importCustomCue(cue, documentUri)
            .onSuccess { _events.send(AudioEvent.Message(UiMessage(R.string.audio_imported, listOf(it.displayName)))) }
            .onFailure { _events.send(AudioEvent.Message(UiMessage(R.string.audio_import_failed, listOf(it.message ?: "?")))) }
    }

    fun reset(cue: AudioCue) = viewModelScope.launch { audio.clearCustomCue(cue) }

    fun applyToSystem() = viewModelScope.launch {
        refresh()
        local.update { it.copy(applying = true) }
        val report = applySoundPack()
        local.update { it.copy(applying = false) }
        val message = when {
            report.missingWriteSettings -> UiMessage(R.string.audio_needs_write)
            report.success -> UiMessage(R.string.audio_applied, listOf(report.applied.joinToString(" / ")))
            else -> UiMessage(R.string.audio_apply_failed, listOf(report.failures.entries.joinToString { "${it.key}: ${it.value}" }))
        }
        _events.send(AudioEvent.Message(message))
    }

    fun setToggle(toggle: HoloToggle, enabled: Boolean) = viewModelScope.launch { preferences.setToggle(toggle, enabled) }

    fun toggleFinanceApp(packageName: String) = viewModelScope.launch {
        val prefs = preferences.preferences.first()
        val detected = classifier.matchesFinanceHeuristic(packageName)
        var explicit = prefs.financePackages
        var blocked = prefs.blockedFinancePackages
        if (classifier.isFinanceApp(packageName, prefs)) {
            explicit = explicit - packageName
            if (detected) blocked = blocked + packageName
        } else {
            blocked = blocked - packageName
            if (!detected) explicit = explicit + packageName
        }
        preferences.setFinancePackages(explicit, blocked)
    }

    fun setGigKeywords(raw: String) = viewModelScope.launch {
        preferences.setGigKeywords(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    fun scheduleAlarm(hour: Int, minute: Int) = viewModelScope.launch {
        prepareAlarmTone()
            .onSuccess { _events.send(AudioEvent.LaunchAlarm(it, hour, minute)) }
            .onFailure { _events.send(AudioEvent.Message(UiMessage(R.string.audio_alarm_failed))) }
    }

    override fun onCleared() {
        playCue.stopPreview()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_INDICATOR_MS = 6_000L
    }
}
