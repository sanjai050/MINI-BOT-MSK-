package com.sanjai.buddy

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sanjai.buddy.Store.bubbleOn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var startBtn: Button
    private lateinit var notesBox: LinearLayout
    private lateinit var remBox: LinearLayout
    private lateinit var pickedLabel: TextView
    private var pickedMs: Long = 0L
    private val fmt = SimpleDateFormat("EEE dd MMM yyyy, hh:mm a", Locale.getDefault())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply {
            text = "Mini Bot MSK"
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
        col.addView(status)

        // ---- Setup ----
        col.addView(title("Setup (do these once)"))
        col.addView(btn("1. Allow floating Buddy over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("2. Allow notification access (WhatsApp count)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(btn("3. Allow reminders & alarms") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val am = getSystemService(ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
        })
        startBtn = btn("") { toggleBuddy() }
        col.addView(startBtn)

        // ---- Notes ----
        col.addView(title("Notes"))
        val noteInput = EditText(this).apply { hint = "Write a note" }
        val privBox = CheckBox(this).apply { text = "Private (only shown in Buddy's 10-minute private view)" }
        col.addView(noteInput)
        col.addView(privBox)
        col.addView(btn("Add note") {
            val t = noteInput.text.toString().trim()
            if (t.isNotEmpty()) {
                Store.addNote(this, t, privBox.isChecked)
                noteInput.setText("")
                privBox.isChecked = false
                refreshLists()
            }
        })
        notesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(notesBox)

        // ---- Reminders ----
        col.addView(title("Reminders & schedule"))
        val remInput = EditText(this).apply { hint = "What should I remind you about?" }
        col.addView(remInput)
        pickedLabel = TextView(this).apply { text = "No date chosen"; setPadding(0, dp(6), 0, dp(6)) }
        col.addView(pickedLabel)
        col.addView(btn("Pick date & time") { pickDateTime() })
        val dailyBox = CheckBox(this).apply { text = "Repeat every day (daily schedule)" }
        col.addView(dailyBox)
        col.addView(btn("Save reminder") {
            val t = remInput.text.toString().trim()
            if (t.isEmpty() || pickedMs <= System.currentTimeMillis()) {
                Toast.makeText(this, "Enter text and pick a future date & time", Toast.LENGTH_LONG).show()
            } else {
                val r = Reminder(System.currentTimeMillis(), t, pickedMs, dailyBox.isChecked)
                Store.addReminder(this, r)
                Reminders.schedule(this, r)
                remInput.setText("")
                dailyBox.isChecked = false
                pickedMs = 0L
                pickedLabel.text = "No date chosen"
                refreshLists()
                Toast.makeText(this, "Reminder saved", Toast.LENGTH_SHORT).show()
            }
        })
        remBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(remBox)

        refreshLists()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLists()
    }

    private fun pickDateTime() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            // default to 8:00 AM so the reminder arrives in the morning of that date
            TimePickerDialog(this, { _, h, min ->
                val c = Calendar.getInstance().apply {
                    set(y, m, d, h, min, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                pickedMs = c.timeInMillis
                pickedLabel.text = fmt.format(Date(pickedMs))
            }, 8, 0, false).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun toggleBuddy() {
        if (BubbleService.instance != null) {
            stopService(Intent(this, BubbleService::class.java))
            bubbleOn = false
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "First tap step 1 and allow display over other apps", Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, BubbleService::class.java))
            bubbleOn = true
            Toast.makeText(this, "Buddy is on your screen. Press Home to see him!", Toast.LENGTH_LONG).show()
        }
        startBtn.postDelayed({ refreshStatus() }, 400)
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val listener = BuddyNotificationListener.instance != null
        status.text = "Floating permission: ${if (overlay) "✅" else "❌"}\n" +
            "Notification access: ${if (listener) "✅" else "❌ (turn on Buddy in the list)"}"
        startBtn.text = if (BubbleService.instance != null) "Stop Buddy" else "▶ Start Mini Bot MSK on my screen"
    }

    private fun refreshLists() {
        notesBox.removeAllViews()
        for (n in Store.notes(this)) {
            notesBox.addView(row((if (n.isPrivate) "🔒 " else "") + n.text) {
                Store.deleteNote(this, n.id); refreshLists()
            })
        }
        remBox.removeAllViews()
        for (r in Store.reminders(this)) {
            remBox.addView(row("⏰ ${fmt.format(Date(r.whenMs))}${if (r.daily) " (daily)" else ""}\n${r.text}") {
                Reminders.cancel(this, r); Store.deleteReminder(this, r.id); refreshLists()
            })
        }
    }

    private fun row(text: String, onDelete: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, dp(6))
        addView(TextView(context).apply { this.text = text; textSize = 15f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply {
            this.text = "✕"
            setOnClickListener { onDelete() }
        })
    }
}
