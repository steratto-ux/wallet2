package com.example.finanzecasa.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.finanzecasa.MainActivity
import com.example.finanzecasa.R

object Notifier {
    private const val CHANNEL = "avvisi"

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Vero se le notifiche sono consentite (da Android 13 serve anche il permesso concesso dall'utente). */
    fun canPost(context: Context): Boolean = manager(context).areNotificationsEnabled()

    fun post(context: Context, key: String, title: String, text: String) {
        val nm = manager(context)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Avvisi di Il mio wallet", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(key.hashCode(), n)
    }
}
