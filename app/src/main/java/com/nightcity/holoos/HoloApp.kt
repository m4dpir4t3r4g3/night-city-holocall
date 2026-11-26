package com.nightcity.holoos

import android.app.Application
import com.nightcity.holoos.service.notification.HoloNotifications
import com.nightcity.holoos.service.power.PowerEventMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HoloApp : Application() {

    @Inject lateinit var powerMonitor: PowerEventMonitor

    override fun onCreate() {
        super.onCreate()
        HoloNotifications.ensureChannels(this)
        // Runtime-registered: these broadcasts are not delivered to manifest receivers.
        powerMonitor.start()
    }
}
