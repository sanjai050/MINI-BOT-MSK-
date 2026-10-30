package com.sanjai.buddy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.sanjai.buddy.Store.bubbleOn

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Reminders.rescheduleAll(context)
        if (context.bubbleOn && Settings.canDrawOverlays(context)) {
            context.startForegroundService(Intent(context, BubbleService::class.java))
        }
    }
}
