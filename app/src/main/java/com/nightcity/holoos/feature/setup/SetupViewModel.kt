package com.nightcity.holoos.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nightcity.holoos.R
import com.nightcity.holoos.core.ui.UiMessage
import com.nightcity.holoos.domain.model.ApplyReport
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CalibrationState
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.domain.model.CuePlayback
import com.nightcity.holoos.domain.model.StepStatus
import com.nightcity.holoos.domain.repository.PermissionRepository
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.usecase.ApplySoundPackUseCase
import com.nightcity.holoos.domain.usecase.PlayCueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
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

data class SetupUiState(
    val calibration: CalibrationState = CalibrationState(emptyMap()),
    val autoRunning: Boolean = false,
    val applying: Boolean = false,
    val lastReport: ApplyReport? = null,
    val setupCompleted: Boolean = false,
) {
    val linkEstablished: Boolean get() = setupCompleted && calibration.requiredComplete
    val canInject: Boolean get() = calibration.status(CalibrationStep.WRITE_SETTINGS).isSatisfied
}

sealed interface SetupEvent {
    data class Launch(val step: CalibrationStep, val attempt: Int) : SetupEvent
    data class Message(val message: UiMessage) : SetupEvent
    data object SoundPackApplied : SetupEvent
}

/**
 * Drives "Neural Link Calibration". The one-click run walks every missing step in order,
 * advancing each time the user returns from a system screen, then injects the sound pack.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val permissions: PermissionRepository,
    private val applySoundPack: ApplySoundPackUseCase,
    private val preferences: PreferencesRepository,
    private val playCue: PlayCueUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(SetupUiState(calibration = permissions.currentState()))
    private val _events = Channel<SetupEvent>(Channel.BUFFERED)
    val events: Flow<SetupEvent> = _events.receiveAsFlow()

    val state: StateFlow<SetupUiState> = combine(local, preferences.preferences) { s, prefs ->
        s.copy(setupCompleted = prefs.setupCompleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val attempts = mutableMapOf<CalibrationStep, Int>()
    private val autoAttempted = mutableSetOf<CalibrationStep>()

    fun refresh() {
        val calibration = permissions.currentState()
        local.update { it.copy(calibration = calibration) }
        if (calibration.requiredComplete) {
            viewModelScope.launch {
                if (!preferences.preferences.first().setupCompleted) preferences.setSetupCompleted(true)
            }
        }
    }

    fun engage(step: CalibrationStep) {
        val attempt = (attempts[step] ?: 0) + 1
        attempts[step] = attempt
        _events.trySend(SetupEvent.Launch(step, attempt))
    }

    fun startAutoCalibration() {
        autoAttempted.clear()
        local.update { it.copy(autoRunning = true) }
        advance()
    }

    fun cancelAutoCalibration() = local.update { it.copy(autoRunning = false) }

    /** A system screen / permission prompt launched for [step] has returned. */
    fun onStepReturned(@Suppress("UNUSED_PARAMETER") step: CalibrationStep) {
        refresh()
        if (local.value.autoRunning) advance()
    }

    fun injectSoundPack() {
        viewModelScope.launch { inject() }
    }

    private fun advance() {
        refresh()
        val calibration = local.value.calibration
        val next = CalibrationStep.entries.firstOrNull {
            calibration.status(it) == StepStatus.MISSING && it !in autoAttempted
        }
        if (next != null) {
            autoAttempted += next
            engage(next)
        } else {
            local.update { it.copy(autoRunning = false) }
            viewModelScope.launch { finish(calibration) }
        }
    }

    private suspend fun finish(calibration: CalibrationState) {
        if (calibration.status(CalibrationStep.WRITE_SETTINGS).isSatisfied) inject()
        if (calibration.requiredComplete) {
            preferences.setSetupCompleted(true)
            playCue(AudioCue.NEURAL_LINK, CuePlayback.PREVIEW)
        }
    }

    private suspend fun inject() {
        local.update { it.copy(applying = true) }
        val report = applySoundPack()
        local.update { it.copy(applying = false, lastReport = report) }
        val event = when {
            report.missingWriteSettings -> SetupEvent.Message(UiMessage(R.string.audio_needs_write))
            report.success -> SetupEvent.SoundPackApplied
            else -> SetupEvent.Message(
                UiMessage(R.string.audio_apply_failed, listOf(report.failures.entries.joinToString { "${it.key}: ${it.value}" })),
            )
        }
        _events.send(event)
    }
}
