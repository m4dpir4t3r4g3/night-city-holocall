package com.nightcity.holoos.domain.model

/** Android system sound slots that RingtoneManager can set as defaults. */
enum class SystemSoundSlot { RINGTONE, NOTIFICATION, ALARM }

/**
 * The seven audio slots. [rawResourceName] is the exact base filename expected in
 * res/raw (see res/raw/audio_slots_todo.txt); any supported extension works.
 */
enum class AudioCue(
    val rawResourceName: String,
    val systemSlot: SystemSoundSlot?,
    val haptic: HapticPattern,
) {
    HOLOCALL_RINGTONE("cue_holocall_ringtone", SystemSoundSlot.RINGTONE, HapticPattern.HOLOCALL),
    NOTIFICATION_CHIRP("cue_notification_chirp", SystemSoundSlot.NOTIFICATION, HapticPattern.CHIRP),
    EDDIE_TRANSFER("cue_eddie_transfer", null, HapticPattern.EDDIES),
    NEW_GIG("cue_new_gig", null, HapticPattern.GIG),
    RELIC_MALFUNCTION("cue_relic_malfunction", null, HapticPattern.MALFUNCTION),
    NEURAL_LINK("cue_neural_link", null, HapticPattern.LINK),
    APARTMENT_ALARM("cue_apartment_alarm", SystemSoundSlot.ALARM, HapticPattern.ALARM),
}

/** Where a cue's audio currently comes from, in priority order. */
sealed interface CueSource {
    /** Imported at runtime through the file picker (copied into app storage). */
    data class Custom(val uri: String, val displayName: String) : CueSource

    /** Dropped into res/raw before building. */
    data class Bundled(val fileName: String) : CueSource

    /** No file supplied: an on-device synthesized placeholder. */
    data object Synthesized : CueSource
}

data class CueSlot(val cue: AudioCue, val source: CueSource)

data class CustomCue(val uri: String, val displayName: String)

/**
 * Staccato mechanical vibration waveforms. Timings alternate off/on (ms) starting with an
 * initial delay; amplitudes are 0..255 and line up with [timings].
 */
enum class HapticPattern(val timings: LongArray, val amplitudes: IntArray) {
    CHIRP(longArrayOf(0, 14, 40, 14), intArrayOf(0, 190, 0, 255)),
    EDDIES(longArrayOf(0, 10, 28, 10, 28, 36), intArrayOf(0, 160, 0, 210, 0, 255)),
    GIG(longArrayOf(0, 40, 30, 12, 20, 12), intArrayOf(0, 255, 0, 180, 0, 180)),
    MALFUNCTION(
        longArrayOf(0, 25, 18, 25, 18, 70, 60, 12, 12, 12, 12, 12, 40, 110),
        intArrayOf(0, 255, 0, 200, 0, 255, 0, 140, 0, 140, 0, 140, 0, 255),
    ),
    LINK(longArrayOf(0, 8, 24, 8, 24, 8, 50, 80), intArrayOf(0, 110, 0, 160, 0, 210, 0, 255)),
    HOLOCALL(longArrayOf(0, 30, 50, 30, 50, 30, 300), intArrayOf(0, 230, 0, 230, 0, 230, 0)),
    ALARM(longArrayOf(0, 60, 60, 60, 60, 60, 60, 200), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)),
}

/** Whether a cue is a user-initiated preview or a real event (which honours ringer/DND). */
enum class CuePlayback { PREVIEW, EVENT }

/** Device ringer state as it affects event cues. */
enum class AlertMode { NORMAL, VIBRATE, SILENT }

data class ApplyReport(
    val applied: List<SystemSoundSlot>,
    val failures: Map<SystemSoundSlot, String>,
    val missingWriteSettings: Boolean,
) {
    val success: Boolean get() = failures.isEmpty() && !missingWriteSettings
}
