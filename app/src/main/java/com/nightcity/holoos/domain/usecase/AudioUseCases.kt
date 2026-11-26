package com.nightcity.holoos.domain.usecase

import com.nightcity.holoos.domain.logic.CueThrottle
import com.nightcity.holoos.domain.logic.NotificationCueClassifier
import com.nightcity.holoos.domain.model.AlertMode
import com.nightcity.holoos.domain.model.ApplyReport
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CuePlayback
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.model.IncomingNotification
import com.nightcity.holoos.domain.model.SystemSoundSlot
import com.nightcity.holoos.domain.repository.AudioCueRepository
import com.nightcity.holoos.domain.repository.AudioEnvironment
import com.nightcity.holoos.domain.repository.CuePlayer
import com.nightcity.holoos.domain.repository.HapticsEngine
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.repository.SystemSoundRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Plays a cue with its haptic waveform. Event cues honour ringer mode and Do Not Disturb. */
class PlayCueUseCase @Inject constructor(
    private val audio: AudioCueRepository,
    private val player: CuePlayer,
    private val haptics: HapticsEngine,
    private val environment: AudioEnvironment,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(cue: AudioCue, playback: CuePlayback = CuePlayback.EVENT) {
        val hapticsOn = preferences.preferences.first().isEnabled(HoloToggle.HAPTICS)
        val mode = if (playback == CuePlayback.PREVIEW) AlertMode.NORMAL else environment.currentMode()
        if (mode == AlertMode.NORMAL) player.play(audio.playableUri(cue), playback)
        if (hapticsOn && mode != AlertMode.SILENT) haptics.play(cue.haptic)
    }

    fun stopPreview() = player.stopPreview()
}

/**
 * The "inject sound pack" action: exports each system-slot cue into MediaStore and sets it
 * as the Android default ringtone / notification / alarm.
 */
class ApplySoundPackUseCase @Inject constructor(
    private val audio: AudioCueRepository,
    private val systemSounds: SystemSoundRepository,
) {
    suspend operator fun invoke(): ApplyReport {
        if (!systemSounds.canWriteSystemSettings()) {
            return ApplyReport(emptyList(), emptyMap(), missingWriteSettings = true)
        }
        val applied = mutableListOf<SystemSoundSlot>()
        val failures = mutableMapOf<SystemSoundSlot, String>()
        for (cue in AudioCue.entries) {
            val slot = cue.systemSlot ?: continue
            audio.exportToMediaStore(cue)
                .mapCatching { uri -> systemSounds.setDefaultSound(slot, uri).getOrThrow() }
                .onSuccess { applied += slot }
                .onFailure { failures[slot] = it.message ?: it.javaClass.simpleName }
        }
        return ApplyReport(applied, failures, missingWriteSettings = false)
    }
}

/** Exports the alarm cue so it can be handed to the clock app via AlarmClock.EXTRA_RINGTONE. */
class PrepareAlarmToneUseCase @Inject constructor(private val audio: AudioCueRepository) {
    suspend operator fun invoke(): Result<String> = audio.exportToMediaStore(AudioCue.APARTMENT_ALARM)
}

/** Notification listener entry point: classify, throttle, play. */
class HandleNotificationUseCase @Inject constructor(
    private val classifier: NotificationCueClassifier,
    private val throttle: CueThrottle,
    private val preferences: PreferencesRepository,
    private val playCue: PlayCueUseCase,
) {
    suspend operator fun invoke(notification: IncomingNotification, nowMs: Long): AudioCue? {
        val prefs = preferences.preferences.first()
        val cue = classifier.classify(notification, prefs) ?: return null
        if (!throttle.shouldFire(cue, notification.key, notification.onlyAlertOnce, nowMs)) return null
        playCue(cue, CuePlayback.EVENT)
        return cue
    }
}
