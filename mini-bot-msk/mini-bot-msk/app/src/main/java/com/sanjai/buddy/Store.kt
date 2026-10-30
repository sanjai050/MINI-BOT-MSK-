package com.sanjai.buddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Note(val id: Long, val text: String, val isPrivate: Boolean)
data class Reminder(val id: Long, val text: String, val whenMs: Long, val daily: Boolean)
data class Notif(val app: String, val pkg: String, val title: String, val text: String, val time: Long)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    // ---------- Notes ----------
    fun notes(c: Context): List<Note> {
        val a = JSONArray(sp(c).getString("notes", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Note(o.getLong("id"), o.getString("text"), o.getBoolean("p"))
        }
    }

    private fun saveNotes(c: Context, list: List<Note>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("p", it.isPrivate)) }
        sp(c).edit().putString("notes", a.toString()).apply()
    }

    fun addNote(c: Context, text: String, isPrivate: Boolean) {
        saveNotes(c, notes(c) + Note(System.currentTimeMillis(), text, isPrivate))
    }

    fun deleteNote(c: Context, id: Long) = saveNotes(c, notes(c).filter { it.id != id })

    // ---------- Reminders ----------
    fun reminders(c: Context): List<Reminder> {
        val a = JSONArray(sp(c).getString("reminders", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Reminder(o.getLong("id"), o.getString("text"), o.getLong("t"), o.getBoolean("d"))
        }.sortedBy { it.whenMs }
    }

    fun saveReminders(c: Context, list: List<Reminder>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("text", it.text).put("t", it.whenMs).put("d", it.daily))
        }
        sp(c).edit().putString("reminders", a.toString()).apply()
    }

    fun addReminder(c: Context, r: Reminder) = saveReminders(c, reminders(c) + r)

    fun deleteReminder(c: Context, id: Long) = saveReminders(c, reminders(c).filter { it.id != id })

    // ---------- Notifications seen ----------
    fun notifs(c: Context): List<Notif> {
        val a = JSONArray(sp(c).getString("notifs", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Notif(o.getString("a"), o.getString("p"), o.getString("ti"), o.getString("tx"), o.getLong("t"))
        }
    }

    fun addNotif(c: Context, n: Notif) {
        val list = (listOf(n) + notifs(c)).take(50)
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("a", it.app).put("p", it.pkg).put("ti", it.title)
                    .put("tx", it.text).put("t", it.time)
            )
        }
        sp(c).edit().putString("notifs", a.toString()).apply()
    }

    // ---------- Settings ----------
    var Context.bubbleOn: Boolean
        get() = sp(this).getBoolean("bubble_on", false)
        set(v) = sp(this).edit().putBoolean("bubble_on", v).apply()
}
