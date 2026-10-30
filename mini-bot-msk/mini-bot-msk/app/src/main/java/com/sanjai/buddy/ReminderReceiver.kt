package com.sanjai.buddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val r = Store.reminders(context).firstOrNull { it.id == id } ?: return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Buddy reminder")
            .setContentText(r.text)
            .setStyle(Notification.BigTextStyle().bigText(r.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((id % Int.MAX_VALUE).toInt(), n)

        if (r.daily) {
            val next = r.copy(whenMs = Reminders.nextDaily(r.whenMs))
            Store.saveReminders(context, Store.reminders(context).map { if (it.id == id) next else it })
            Reminders.schedule(context, next)
        } else {
            Store.deleteReminder(context, id)
        }
    }
}
