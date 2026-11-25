package com.nightcity.holoos.domain.model

enum class HoloToggle(val default: Boolean) {
    HAPTICS(true),
    FINANCE_CUE(true),
    GIG_CUE(true),
    POWER_CUES(true),
    RELIC_BANNER(true),
}

data class HoloPreferences(
    val setupCompleted: Boolean = false,
    val toggles: Map<HoloToggle, Boolean> = emptyMap(),
    /** Packages the user explicitly marked as Eddie triggers. */
    val financePackages: Set<String> = emptySet(),
    /** Heuristically detected finance packages the user opted out of. */
    val blockedFinancePackages: Set<String> = emptySet(),
    /** If non-empty, only emails containing one of these words fire the New Gig sting. */
    val gigKeywords: Set<String> = emptySet(),
    val iconTint: IconTint = IconTint.CYAN,
    val iconFrame: IconFrame = IconFrame.CHAMFER,
    val completedOverrides: Set<String> = emptySet(),
    val customCues: Map<AudioCue, CustomCue> = emptyMap(),
) {
    fun isEnabled(toggle: HoloToggle): Boolean = toggles[toggle] ?: toggle.default
}

/** Notification fields the classifier needs, decoupled from StatusBarNotification. */
data class IncomingNotification(
    val key: String,
    val packageName: String,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val onlyAlertOnce: Boolean,
    val importance: Int,
    val title: String?,
    val text: String?,
)
