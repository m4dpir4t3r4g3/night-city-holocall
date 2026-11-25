package com.nightcity.holoos.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nightcity.holoos.domain.model.IncomingNotification
import com.nightcity.holoos.domain.usecase.HandleNotificationUseCase
import com.nightcity.holoos.service.power.PowerEventMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Inspects incoming notifications and layers contextual cues on top of them: Eddie Transfer
 * for banking/payment apps and the New Gig sting for (priority) email.
 */
@AndroidEntryPoint
class HoloNotificationListener : NotificationListenerService() {

    @Inject lateinit var handleNotification: HandleNotificationUseCase
    @Inject lateinit var powerMonitor: PowerEventMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        // The system keeps this service bound, so it is a reliable host for runtime receivers.
        powerMonitor.start()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return
        val ranking = Ranking()
        val importance = if (rankingMap.getRanking(sbn.key, ranking)) ranking.importance else NotificationManager.IMPORTANCE_DEFAULT
        val n = sbn.notification
        val incoming = IncomingNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            category = n.category,
            isOngoing = sbn.isOngoing,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            onlyAlertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            importance = importance,
            title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        scope.launch {
            // Let the app's own notification sound start first, then layer the cue over it.
            kotlinx.coroutines.delay(CUE_DELAY_MS)
            handleNotification(incoming, SystemClock.elapsedRealtime())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CUE_DELAY_MS = 350L
    }
}
