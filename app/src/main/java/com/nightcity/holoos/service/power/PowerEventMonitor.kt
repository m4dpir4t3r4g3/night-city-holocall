package com.nightcity.holoos.service.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.nightcity.holoos.data.system.toBatteryVitals
import com.nightcity.holoos.di.ApplicationScope
import com.nightcity.holoos.domain.logic.LowBatteryAlarm
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.usecase.PlayCueUseCase
import com.nightcity.holoos.service.overlay.HudOverlayController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Charger-plugged ("Neural Link Connected") and low-battery ("Relic Malfunction") cues.
 *
 * ACTION_POWER_CONNECTED / ACTION_BATTERY_LOW / ACTION_BATTERY_CHANGED cannot be received by
 * manifest receivers on API 26+, so this registers at runtime. It is started from
 * HoloApp.onCreate and from the notification listener, whose binding keeps the process
 * alive in the background.
 */
@Singleton
class PowerEventMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playCue: PlayCueUseCase,
    private val preferences: PreferencesRepository,
    private val overlay: HudOverlayController,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val lowBattery = LowBatteryAlarm(threshold = 15, rearmAt = 20)
    private var lastLinkAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onChargerConnected()
                Intent.ACTION_BATTERY_LOW -> if (lowBattery.onSystemLowBattery()) onRelicMalfunction()
                Intent.ACTION_BATTERY_CHANGED -> {
                    val vitals = intent.toBatteryVitals()
                    if (lowBattery.onBatteryUpdate(vitals.percent, vitals.charging, isInitialStickyBroadcast)) {
                        onRelicMalfunction()
                    }
                }
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun onChargerConnected() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLinkAt < DEBOUNCE_MS) return // loose cables bounce
        lastLinkAt = now
        scope.launch {
            if (preferences.preferences.first().isEnabled(HoloToggle.POWER_CUES)) playCue(AudioCue.NEURAL_LINK)
        }
    }

    private fun onRelicMalfunction() {
        val percent = lowBattery.percent
        scope.launch {
            val prefs = preferences.preferences.first()
            if (!prefs.isEnabled(HoloToggle.POWER_CUES)) return@launch
            playCue(AudioCue.RELIC_MALFUNCTION)
            if (prefs.isEnabled(HoloToggle.RELIC_BANNER)) {
                withContext(Dispatchers.Main) { overlay.showRelicBanner(percent) }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 4_000L
    }
}
