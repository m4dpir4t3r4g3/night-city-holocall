package com.nightcity.holoos.domain.model

/** Neural Link Calibration steps, in the order the one-click wizard walks them. */
enum class CalibrationStep(val required: Boolean) {
    NOTIFICATIONS(required = true),
    NOTIFICATION_LISTENER(required = true),
    WRITE_SETTINGS(required = true),
    ALARMS(required = true),
    OVERLAY(required = false),
    CONTACTS(required = false),
    LIVE_WALLPAPER(required = false),
    DEFAULT_HOME(required = true),
}

enum class StepStatus {
    GRANTED,
    MISSING,

    /** Install-time permission, or not needed on this Android version. */
    AUTO_GRANTED,
    ;

    val isSatisfied: Boolean get() = this != MISSING
}

data class CalibrationState(val statuses: Map<CalibrationStep, StepStatus>) {
    fun status(step: CalibrationStep): StepStatus = statuses[step] ?: StepStatus.MISSING

    val requiredComplete: Boolean
        get() = CalibrationStep.entries.filter { it.required }.all { status(it).isSatisfied }

    val progress: Float
        get() = if (statuses.isEmpty()) 0f else statuses.values.count { it.isSatisfied }.toFloat() / statuses.size
}
