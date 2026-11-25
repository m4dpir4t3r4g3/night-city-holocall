package com.nightcity.holoos.core.resources

import androidx.annotation.StringRes
import com.nightcity.holoos.R
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CalibrationStep
import com.nightcity.holoos.domain.model.DockSlot

// Display strings for domain enums. Kept out of the domain layer so it stays Android-free,
// and in strings.xml so the whole suite can be renamed/rebranded in one place.

@get:StringRes
val AudioCue.titleRes: Int
    get() = when (this) {
        AudioCue.HOLOCALL_RINGTONE -> R.string.cue_holocall_title
        AudioCue.NOTIFICATION_CHIRP -> R.string.cue_chirp_title
        AudioCue.EDDIE_TRANSFER -> R.string.cue_eddie_title
        AudioCue.NEW_GIG -> R.string.cue_gig_title
        AudioCue.RELIC_MALFUNCTION -> R.string.cue_relic_title
        AudioCue.NEURAL_LINK -> R.string.cue_link_title
        AudioCue.APARTMENT_ALARM -> R.string.cue_alarm_title
    }

@get:StringRes
val AudioCue.triggerRes: Int
    get() = when (this) {
        AudioCue.HOLOCALL_RINGTONE -> R.string.cue_holocall_trigger
        AudioCue.NOTIFICATION_CHIRP -> R.string.cue_chirp_trigger
        AudioCue.EDDIE_TRANSFER -> R.string.cue_eddie_trigger
        AudioCue.NEW_GIG -> R.string.cue_gig_trigger
        AudioCue.RELIC_MALFUNCTION -> R.string.cue_relic_trigger
        AudioCue.NEURAL_LINK -> R.string.cue_link_trigger
        AudioCue.APARTMENT_ALARM -> R.string.cue_alarm_trigger
    }

@get:StringRes
val CalibrationStep.titleRes: Int
    get() = when (this) {
        CalibrationStep.NOTIFICATIONS -> R.string.step_notifications_title
        CalibrationStep.NOTIFICATION_LISTENER -> R.string.step_listener_title
        CalibrationStep.WRITE_SETTINGS -> R.string.step_write_settings_title
        CalibrationStep.ALARMS -> R.string.step_alarm_title
        CalibrationStep.OVERLAY -> R.string.step_overlay_title
        CalibrationStep.CONTACTS -> R.string.step_contacts_title
        CalibrationStep.LIVE_WALLPAPER -> R.string.step_wallpaper_title
        CalibrationStep.DEFAULT_HOME -> R.string.step_home_title
    }

@get:StringRes
val CalibrationStep.bodyRes: Int
    get() = when (this) {
        CalibrationStep.NOTIFICATIONS -> R.string.step_notifications_body
        CalibrationStep.NOTIFICATION_LISTENER -> R.string.step_listener_body
        CalibrationStep.WRITE_SETTINGS -> R.string.step_write_settings_body
        CalibrationStep.ALARMS -> R.string.step_alarm_body
        CalibrationStep.OVERLAY -> R.string.step_overlay_body
        CalibrationStep.CONTACTS -> R.string.step_contacts_body
        CalibrationStep.LIVE_WALLPAPER -> R.string.step_wallpaper_body
        CalibrationStep.DEFAULT_HOME -> R.string.step_home_body
    }

@get:StringRes
val DockSlot.labelRes: Int
    get() = when (this) {
        DockSlot.PHONE -> R.string.dock_phone
        DockSlot.MESSAGES -> R.string.dock_messages
        DockSlot.CAMERA -> R.string.dock_camera
        DockSlot.BROWSER -> R.string.dock_browser
    }
