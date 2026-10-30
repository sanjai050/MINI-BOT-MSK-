package com.sanjai.buddy

import android.app.Notification
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BuddyNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        BubbleService.instance?.refreshBadge()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        Store.addNotif(this, Notif(friendly(sbn.packageName), sbn.packageName, title, text, sbn.postTime))
        BubbleService.instance?.refreshBadge()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        BubbleService.instance?.refreshBadge()
    }

    companion object {
        var instance: BuddyNotificationListener? = null

        fun friendly(pkg: String): String =
            if (pkg.startsWith("com.whatsapp")) "WhatsApp" else pkg.substringAfterLast('.')

        /** Number of unread WhatsApp messages currently in the notification shade, or -1 if no access. */
        fun whatsappCount(): Int {
            val l = instance ?: return -1
            return try {
                var total = 0
                for (s in l.activeNotifications) {
                    if (!s.packageName.startsWith("com.whatsapp")) continue
                    val n = s.notification
                    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                    val msgs: Array<out Parcelable>? = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    total += if (msgs != null && msgs.isNotEmpty()) msgs.size else 1
                }
                total
            } catch (ex: Exception) {
                -1
            }
        }
    }
}
