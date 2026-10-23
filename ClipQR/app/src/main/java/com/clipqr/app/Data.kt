package com.clipqr.app

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Category(val label: String) {
    LINK("Links"),
    EMAIL("Emails"),
    PHONE("Phones"),
    CODE("Codes"),
    WIFI("Wi-Fi"),
    CONTACT("Contacts"),
    TEXT("Text"),
}

enum class Source(val label: String) {
    COPY("Copied"),
    SCAN("Scanned"),
    SHARE("Shared"),
    CREATED("Created"),
    MANUAL("Added"),
}

data class ClipItem(
    val id: Long,
    val text: String,
    val category: Category,
    val pinned: Boolean,
    val createdAt: Long,
    val source: Source,
)

/** Holds the library and saves it to a private file on the phone (no internet, no account). */
class ClipViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "clips.json")
    private val prefs = app.getSharedPreferences("clipqr", Context.MODE_PRIVATE)
    private var nextId = 1L

    val items = mutableStateListOf<ClipItem>()
    var tab by mutableIntStateOf(0)
    var autoCapture by mutableStateOf(prefs.getBoolean("auto_capture", true))
        private set

    init {
        load()
    }

    fun updateAutoCapture(on: Boolean) {
        autoCapture = on
        prefs.edit().putBoolean("auto_capture", on).apply()
    }

    /** Reads the clipboard (only works while the app is on screen — an Android privacy rule). */
    fun captureClipboard(force: Boolean = false): ClipItem? {
        val ctx = getApplication<Application>()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = try {
            cm.primaryClip
        } catch (e: Exception) {
            null
        } ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!force && text == prefs.getString("last_clip", null)) return null
        rememberClip(text)
        return add(text, Source.COPY)
    }

    /** Marks text as already seen so it isn't re-added when the app regains focus. */
    fun rememberClip(text: String) {
        prefs.edit().putString("last_clip", text.trim()).apply()
    }

    fun add(raw: String, source: Source): ClipItem? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val now = System.currentTimeMillis()
        val existing = items.indexOfFirst { it.text == text }
        if (existing >= 0) {
            val moved = items.removeAt(existing).copy(createdAt = now)
            items.add(0, moved)
            save()
            return moved
        }
        val item = ClipItem(nextId++, text, Categorizer.categorize(text), false, now, source)
        items.add(0, item)
        while (items.count { !it.pinned } > MAX_UNPINNED) {
            items.removeAt(items.indexOfLast { !it.pinned })
        }
        save()
        return item
    }

    fun find(id: Long): ClipItem? = items.firstOrNull { it.id == id }

    fun togglePin(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(pinned = !items[i].pinned)
        save()
    }

    fun delete(id: Long) {
        items.removeAll { it.id == id }
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val text = o.getString("text")
                val source = runCatching { Source.valueOf(o.optString("source")) }.getOrDefault(Source.MANUAL)
                items.add(
                    ClipItem(
                        id = o.getLong("id"),
                        text = text,
                        category = Categorizer.categorize(text),
                        pinned = o.optBoolean("pinned"),
                        createdAt = o.optLong("time"),
                        source = source,
                    )
                )
            }
            nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        } catch (e: Exception) {
            // Corrupt file: start fresh rather than crash.
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            items.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("text", it.text)
                        .put("pinned", it.pinned)
                        .put("time", it.createdAt)
                        .put("source", it.source.name)
                )
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        } catch (e: Exception) {
            // Ignore write errors; data stays in memory.
        }
    }

    companion object {
        const val MAX_UNPINNED = 500
    }
}
