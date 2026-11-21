package com.wyutools.app.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One line of today's checklist. Ids only have to be unique inside one day. */
internal data class TodayTodo(
    val id: Long,
    val text: String,
    val done: Boolean,
)

/**
 * Today's checklist.
 *
 * It is deliberately day-scoped: the 今日 page is about *today*, so the list is
 * stored together with its date and comes back empty once the date changes.
 * Carrying yesterday's unchecked items over silently would either bury today's
 * work or nag about things the user already decided to drop.
 *
 * Storage is one JSON blob in SharedPreferences rather than a database: a
 * checklist is a few dozen short strings, and a synchronous read at page entry
 * beats opening a Room instance for it. (JSON, not a custom line format, because
 * SharedPreferences writes XML — control characters as separators would produce
 * a file the parser can reject, which would take every saved todo with it.)
 */
internal object WyuTodos {
    private const val PrefsName = "wyutools_todos"
    private const val KeyDate = "todos_date"
    private const val KeyItems = "todos_items"

    private val _items = MutableStateFlow<List<TodayTodo>>(emptyList())
    val items: StateFlow<List<TodayTodo>> = _items.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var date: LocalDate = LocalDate.now()

    /**
     * Reads today's list, discarding whatever was stored for another day. Safe to
     * call on every entry: the date check is the whole cost once memory is warm.
     */
    fun ensureLoaded(context: Context, today: LocalDate = LocalDate.now()) {
        val store = prefs ?: context.applicationContext
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .also { prefs = it }
        if (store.getString(KeyDate, null) != today.toString()) {
            date = today
            _items.value = emptyList()
            // Writing the new date now (instead of on the first edit) keeps a
            // second entry from reading the old day's blob again.
            store.edit()
                .putString(KeyDate, today.toString())
                .putString(KeyItems, "[]")
                .apply()
            return
        }
        date = today
        _items.value = decode(store.getString(KeyItems, null))
    }

    fun add(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val next = (_items.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _items.value = _items.value + TodayTodo(next, trimmed, done = false)
        persist(context)
    }

    fun toggle(context: Context, id: Long) {
        _items.value = _items.value.map { if (it.id == id) it.copy(done = !it.done) else it }
        persist(context)
    }

    fun remove(context: Context, id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
        persist(context)
    }

    private fun persist(context: Context) {
        val store = prefs ?: context.applicationContext
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .also { prefs = it }
        val array = JSONArray()
        _items.value.forEach { todo ->
            array.put(
                JSONObject()
                    .put("t", todo.text)
                    .put("d", todo.done),
            )
        }
        store.edit()
            .putString(KeyDate, date.toString())
            .putString(KeyItems, array.toString())
            .apply()
    }

    private fun decode(raw: String?): List<TodayTodo> {
        if (raw.isNullOrBlank()) return emptyList()
        // A malformed blob is treated as an empty day rather than crashing the
        // page: the todos are a convenience, not the reason the app exists.
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("t")
                if (text.isBlank()) continue
                add(TodayTodo(index + 1L, text, item.optBoolean("d")))
            }
        }
    }
}
