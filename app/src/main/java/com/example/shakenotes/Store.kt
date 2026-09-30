package com.example.shakenotes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Expense(val id: Long, val title: String, val amount: Double, val category: String, val time: Long)
data class Note(val id: Long, val title: String, val body: String, val time: Long)

val CATEGORIES = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Fun", "Other")

/** Simple on-device storage (SharedPreferences + JSON). Swap for Room later if data grows. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("data", Context.MODE_PRIVATE)

    var shakeEnabled: Boolean
        get() = prefs.getBoolean("shake", true)
        set(v) = prefs.edit().putBoolean("shake", v).apply()

    fun loadExpenses(): List<Expense> = read("expenses") {
        Expense(it.getLong("id"), it.getString("title"), it.getDouble("amount"),
            it.getString("category"), it.getLong("time"))
    }

    fun saveExpenses(list: List<Expense>) = write("expenses", list) {
        JSONObject().put("id", it.id).put("title", it.title).put("amount", it.amount)
            .put("category", it.category).put("time", it.time)
    }

    fun loadNotes(): List<Note> = read("notes") {
        Note(it.getLong("id"), it.getString("title"), it.getString("body"), it.getLong("time"))
    }

    fun saveNotes(list: List<Note>) = write("notes", list) {
        JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("time", it.time)
    }

    private fun <T> read(key: String, map: (JSONObject) -> T): List<T> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
    }

    private fun <T> write(key: String, list: List<T>, map: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(map(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
