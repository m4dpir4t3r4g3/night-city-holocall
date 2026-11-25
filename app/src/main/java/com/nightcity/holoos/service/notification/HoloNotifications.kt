package com.nightcity.holoos.service.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nightcity.holoos.MainActivity
import com.nightcity.holoos.R

/** The app's own notifications (POST_NOTIFICATIONS). */
object HoloNotifications {
    private const val CHANNEL_STATUS = "holo_status"
    private const val ID_LINK = 2077

    fun ensureChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_DEFAULT, // default sound = the system default chirp
        ).apply { description = context.getString(R.string.channel_status_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posted after the sound pack is injected so the user hears the new default chirp. */
    fun postLinkEstablished(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_holo)
            .setColor(0xFF00F0FF.toInt())
            .setContentTitle(context.getString(R.string.notif_link_title))
            .setContentText(context.getString(R.string.notif_link_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_LINK, notification)
    }
}
