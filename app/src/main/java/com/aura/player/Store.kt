package com.aura.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tiny persistence layer: favourites, recents, playlists, EQ settings, lyrics files. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }

    private fun ids(key: String): List<Long> =
        (sp.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

    fun favs(): Set<Long> = ids("favs").toSet()
    fun saveFavs(s: Set<Long>) = sp.edit().putString("favs", s.joinToString(",")).apply()

    fun recents(): List<Long> = ids("recents")
    fun saveRecents(l: List<Long>) = sp.edit().putString("recents", l.joinToString(",")).apply()

    fun playlists(): Map<String, List<Long>> {
        val o = JSONObject(sp.getString("pl", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { k ->
            val a = o.getJSONArray(k); List(a.length()) { a.getLong(it) }
        }
    }
    fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        sp.edit().putString("pl", o.toString()).apply()
    }

    var preset: Int
        get() = sp.getInt("preset", 0)
        set(v) = sp.edit().putInt("preset", v).apply()
    var bass: Int
        get() = sp.getInt("bass", 0)
        set(v) = sp.edit().putInt("bass", v).apply()

    fun lyrics(id: Long): String? = File(dir, "$id.lrc").takeIf { it.exists() }?.readText()
    fun saveLyrics(id: Long, text: String) = File(dir, "$id.lrc").writeText(text)
}
