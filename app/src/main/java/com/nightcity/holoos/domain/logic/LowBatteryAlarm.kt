package com.nightcity.holoos.domain.logic

/**
 * Hysteresis state machine for the Relic Malfunction cue: fires once when the battery drops
 * below [threshold] while discharging, and re-arms only after charging or recovering to
 * [rearmAt]. The sticky broadcast delivered at registration never fires the alarm.
 */
class LowBatteryAlarm(
    private val threshold: Int = 15,
    private val rearmAt: Int = 20,
) {
    private var armed = true
    private var lastPercent = 100
    private var charging = false

    val percent: Int get() = lastPercent

    @Synchronized
    fun onBatteryUpdate(percent: Int, charging: Boolean, initialSticky: Boolean): Boolean {
        lastPercent = percent
        this.charging = charging
        if (charging || percent >= rearmAt) {
            armed = true
            return false
        }
        if (percent < threshold && armed) {
            armed = false
            return !initialSticky
        }
        return false
    }

    /** ACTION_BATTERY_LOW uses the OEM threshold (often 15-20%), so re-check ours. */
    @Synchronized
    fun onSystemLowBattery(): Boolean {
        if (charging || lastPercent >= threshold || !armed) return false
        armed = false
        return true
    }
}
