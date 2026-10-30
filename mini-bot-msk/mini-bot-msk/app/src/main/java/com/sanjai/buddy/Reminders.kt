package com.sanjai.buddy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object Reminders {
    private const val DAY = 24L * 60 * 60 * 1000

    private fun pending(c: Context, r: Reminder): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java).putExtra("id", r.id)
        return PendingIntent.getBroadcast(
            c, (r.id % Int.MAX_VALUE).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(c: Context, r: Reminder) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c, r)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, pi)
        }
    }

    fun cancel(c: Context, r: Reminder) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, r))
    }

    /** Next trigger time for a daily reminder, always in the future. */
    fun nextDaily(whenMs: Long): Long {
        var t = whenMs
        val now = System.currentTimeMillis()
        while (t <= now) t += DAY
        return t
    }

    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        val updated = mutableListOf<Reminder>()
        for (r in Store.reminders(c)) {
            when {
                r.whenMs > now -> { schedule(c, r); updated.add(r) }
                r.daily -> { val n = r.copy(whenMs = nextDaily(r.whenMs)); schedule(c, n); updated.add(n) }
                // one-time reminder that was missed while the phone was off: dropped
            }
        }
        Store.saveReminders(c, updated)
    }
}
