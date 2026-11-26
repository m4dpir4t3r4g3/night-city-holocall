package com.nightcity.holoos.data.system

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.domain.model.BatteryVitals
import com.nightcity.holoos.domain.model.MemoryVitals
import com.nightcity.holoos.domain.model.SystemVitals
import com.nightcity.holoos.domain.repository.SystemVitalsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Battery telemetry (sticky broadcast) + RAM "neural load" (polled every 2 s). */
@Singleton
class AndroidSystemVitalsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SystemVitalsRepository {

    private val activityManager = context.getSystemService(ActivityManager::class.java)

    override fun observeVitals(): Flow<SystemVitals> =
        combine(batteryFlow(), memoryFlow()) { battery, memory -> SystemVitals(battery, memory) }

    private fun batteryFlow(): Flow<BatteryVitals> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.toBatteryVitals())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { trySend(it.toBatteryVitals()) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun memoryFlow(): Flow<MemoryVitals> = flow {
        val info = ActivityManager.MemoryInfo()
        while (true) {
            activityManager.getMemoryInfo(info)
            emit(MemoryVitals(info.totalMem, info.availMem))
            delay(2_000)
        }
    }.distinctUntilChanged().flowOn(io)
}

fun Intent.toBatteryVitals(): BatteryVitals {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return BatteryVitals(
        percent = if (level < 0) 0 else level * 100 / scale,
        voltageMv = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
        temperatureC = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
        charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
    )
}
