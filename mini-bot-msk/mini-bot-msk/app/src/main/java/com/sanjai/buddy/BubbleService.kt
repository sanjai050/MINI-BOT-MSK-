package com.sanjai.buddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: FrameLayout? = null
    private var badge: TextView? = null
    private var panel: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var privateTimeout: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addBubble()
        refreshBadge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        instance = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("bubble", "Buddy is running", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "bubble")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Mini Bot MSK is on your screen")
            .setContentText("Tap to open settings")
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------------- Floating bubble ----------------

    private fun mascotRes(): Int {
        val id = resources.getIdentifier("buddy", "drawable", packageName)
        return if (id != 0) id else R.drawable.buddy_default
    }

    private fun addBubble() {
        val size = dp(68)
        val root = FrameLayout(this)

        val img = ImageView(this).apply {
            setImageResource(mascotRes())
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), 0xFFFFB300.toInt())
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dp(6).toFloat()
        }
        root.addView(img, FrameLayout.LayoutParams(size, size))

        val b = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF25D366.toInt())
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        badge = b
        root.addView(b, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(220)
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            lp.x = sx + dx
                            lp.y = sy + dy
                            wm.updateViewLayout(root, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        })

        wm.addView(root, lp)
        bubble = root
    }

    fun refreshBadge() {
        handler.post {
            val b = badge ?: return@post
            val c = BuddyNotificationListener.whatsappCount()
            if (c > 0) {
                b.text = if (c > 99) "99+" else c.toString()
                b.visibility = View.VISIBLE
            } else {
                b.visibility = View.GONE
            }
        }
    }

    // ---------------- Panel ----------------

    private fun togglePanel() {
        if (panel != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF1E1E2E.toInt())
                cornerRadius = dp(22).toFloat()
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(310),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(root, lp)
        panel = root
        render("menu")
    }

    private fun removePanel() {
        privateTimeout?.let { handler.removeCallbacks(it) }
        privateTimeout = null
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private val timeFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    private fun label(text: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(0xFF3B3B58.toInt())
            cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }

    private fun scrollList(items: List<View>) {
        val p = panel ?: return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { box.addView(it) }
        val sv = ScrollView(this).apply { addView(box) }
        p.addView(sv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(260)
        ))
    }

    private fun render(mode: String) {
        val p = panel ?: return
        p.removeAllViews()

        // leaving private mode cancels its 10-minute timer
        if (mode != "private") {
            privateTimeout?.let { handler.removeCallbacks(it) }
            privateTimeout = null
        }

        when (mode) {
            "menu" -> {
                p.addView(label("Hi! I am Mini Bot MSK 👋", 18f, 0xFFFFB300.toInt(), true))
                val wa = BuddyNotificationListener.whatsappCount()
                val waText = when {
                    wa < 0 -> "💬 WhatsApp (turn on notification access)"
                    else -> "💬 WhatsApp: $wa new message${if (wa == 1) "" else "s"}"
                }
                p.addView(button(waText) { render("wa") })
                p.addView(button("🔔 Last notifications") { render("notifs") })
                p.addView(button("🔒 Private notes (10 min)") { render("private") })
                p.addView(button("⏰ Reminders & schedule") { render("reminders") })
                p.addView(button("📝 Open Buddy app") {
                    removePanel()
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                p.addView(button("✖ Hide") { removePanel() })
            }

            "wa" -> {
                p.addView(label("💬 Latest WhatsApp", 17f, 0xFF25D366.toInt(), true))
                val items = Store.notifs(this).filter { it.pkg.startsWith("com.whatsapp") }.take(10)
                if (BuddyNotificationListener.instance == null) {
                    scrollList(listOf(label("Notification access is off. Open the Buddy app and turn it on.", 13f, Color.LTGRAY)))
                } else if (items.isEmpty()) {
                    scrollList(listOf(label("No WhatsApp messages seen yet.", 13f, Color.LTGRAY)))
                } else {
                    scrollList(items.map { notifRow(it) })
                }
                p.addView(button("← Back") { render("menu") })
            }

            "notifs" -> {
                p.addView(label("🔔 Last notifications", 17f, 0xFFFFB300.toInt(), true))
                val items = Store.notifs(this).take(10)
                scrollList(
                    if (items.isEmpty()) listOf(label("Nothing yet.", 13f, Color.LTGRAY))
                    else items.map { notifRow(it) }
                )
                p.addView(button("← Back") { render("menu") })
            }

            "private" -> {
                p.addView(label("🔒 Private notes", 17f, 0xFFFFB300.toInt(), true))
                p.addView(label("Only private notes are shown. This closes itself in 10 minutes.", 12f, Color.LTGRAY))
                val notes = Store.notes(this).filter { it.isPrivate }
                scrollList(
                    if (notes.isEmpty()) listOf(label("No private notes yet. Add some in the app.", 13f, Color.LTGRAY))
                    else notes.map { label("• ${it.text}", 14f) }
                )
                p.addView(button("Lock now") { removePanel() })

                privateTimeout?.let { handler.removeCallbacks(it) }
                val r = Runnable {
                    removePanel()
                    Toast.makeText(this, "Private notes locked", Toast.LENGTH_SHORT).show()
                }
                privateTimeout = r
                handler.postDelayed(r, 10 * 60 * 1000L)
            }

            "reminders" -> {
                p.addView(label("⏰ Coming up", 17f, 0xFFFFB300.toInt(), true))
                val list = Store.reminders(this).take(10)
                scrollList(
                    if (list.isEmpty()) listOf(label("No reminders set.", 13f, Color.LTGRAY))
                    else list.map {
                        label(
                            "${timeFmt.format(Date(it.whenMs))}${if (it.daily) " (daily)" else ""}\n${it.text}",
                            14f
                        )
                    }
                )
                p.addView(button("← Back") { render("menu") })
            }
        }
    }

    private fun notifRow(n: Notif): View = label(
        "${n.app} · ${timeFmt.format(Date(n.time))}\n${n.title}${if (n.text.isNotBlank()) ": ${n.text}" else ""}",
        13f
    )

    companion object {
        var instance: BubbleService? = null
    }
}
