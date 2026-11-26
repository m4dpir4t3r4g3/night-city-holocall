package com.nightcity.holoos.domain

import com.nightcity.holoos.domain.logic.CueThrottle
import com.nightcity.holoos.domain.logic.LowBatteryAlarm
import com.nightcity.holoos.domain.model.AudioCue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowBatteryAlarmTest {

    @Test fun firesOnceWhenCrossingThreshold() {
        val alarm = LowBatteryAlarm(threshold = 15, rearmAt = 20)
        assertFalse(alarm.onBatteryUpdate(16, charging = false, initialSticky = false))
        assertTrue(alarm.onBatteryUpdate(14, charging = false, initialSticky = false))
        assertFalse(alarm.onBatteryUpdate(13, charging = false, initialSticky = false))
        assertFalse(alarm.onSystemLowBattery())
    }

    @Test fun rearmsAfterCharging() {
        val alarm = LowBatteryAlarm()
        assertTrue(alarm.onBatteryUpdate(14, charging = false, initialSticky = false))
        assertFalse(alarm.onBatteryUpdate(14, charging = true, initialSticky = false))
        assertTrue(alarm.onBatteryUpdate(14, charging = false, initialSticky = false))
    }

    @Test fun stickyBroadcastNeverFires() {
        val alarm = LowBatteryAlarm()
        assertFalse(alarm.onBatteryUpdate(9, charging = false, initialSticky = true))
        assertFalse(alarm.onBatteryUpdate(8, charging = false, initialSticky = false))
    }

    @Test fun systemLowBatteryRespectsOurThreshold() {
        val alarm = LowBatteryAlarm(threshold = 15)
        alarm.onBatteryUpdate(19, charging = false, initialSticky = false)
        assertFalse("OEM fires BATTERY_LOW at 20%; we wait for <15%", alarm.onSystemLowBattery())
        alarm.onBatteryUpdate(14, charging = false, initialSticky = true)
        assertFalse(alarm.onSystemLowBattery())
    }

    @Test fun throttleSuppressesDuplicates() {
        val throttle = CueThrottle()
        assertTrue(throttle.shouldFire(AudioCue.EDDIE_TRANSFER, "a", false, 0))
        assertFalse(throttle.shouldFire(AudioCue.EDDIE_TRANSFER, "b", false, 1_000))
        assertFalse(throttle.shouldFire(AudioCue.NEW_GIG, "a", false, 5_000))
        assertTrue(throttle.shouldFire(AudioCue.EDDIE_TRANSFER, "c", false, 3_000))
        assertTrue(throttle.shouldFire(AudioCue.NEW_GIG, "d", true, 3_100))
        assertFalse(throttle.shouldFire(AudioCue.NEW_GIG, "d", true, 60_000))
    }
}
