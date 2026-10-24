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

    fun counts(): Map<Long, Int> {
        val o = JSONObject(sp.getString("counts", "{}") ?: "{}")
        return o.keys().asSequence().associate { it.toLong() to o.getInt(it) }
    }
    fun saveCounts(m: Map<Long, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("counts", o.toString()).apply()
    }
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()
    var accentIdx: Int
        get() = sp.getInt("accent", 0)
        set(v) = sp.edit().putInt("accent", v).apply()
    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    var hideRec: Boolean
        get() = sp.getBoolean("hideRec", true)
        set(v) = sp.edit().putBoolean("hideRec", v).apply()

    /** Last queue so playback can be resumed after the app/service was killed. */
    fun saveSession(ids: List<Long>, cur: Long, pos: Long) =
        sp.edit().putString("sess_ids", ids.joinToString(",")).putLong("sess_cur", cur).putLong("sess_pos", pos).apply()
    fun sessionIds(): List<Long> = ids("sess_ids")
    fun sessionCur(): Long = sp.getLong("sess_cur", -1L)
    fun sessionPos(): Long = sp.getLong("sess_pos", 0L)

    var dyn: Boolean
        get() = sp.getBoolean("dyn", true)
        set(v) = sp.edit().putBoolean("dyn", v).apply()
    fun hidden(): Set<Long> = ids("hidden").toSet()
    fun saveHidden(s: Set<Long>) = sp.edit().putString("hidden", s.joinToString(",")).apply()

    /** Equalizer + loudness remembered per output (speaker, each pair of headphones). */
    fun profile(key: String): Triple<Int, Int, Int>? {
        val a = JSONObject(sp.getString("profiles", "{}") ?: "{}").optJSONArray(key) ?: return null
        return Triple(a.optInt(0), a.optInt(1), a.optInt(2))
    }
    fun saveProfile(key: String, preset: Int, bass: Int, loud: Int) {
        val o = JSONObject(sp.getString("profiles", "{}") ?: "{}")
        o.put(key, JSONArray(listOf(preset, bass, loud)))
        sp.edit().putString("profiles", o.toString()).apply()
    }

    var xfade: Int
        get() = sp.getInt("xfade", 0)
        set(v) = sp.edit().putInt("xfade", v).apply()
    var loud: Int
        get() = sp.getInt("loud", 0)
        set(v) = sp.edit().putInt("loud", v).apply()
    var shake: Boolean
        get() = sp.getBoolean("shake", false)
        set(v) = sp.edit().putBoolean("shake", v).apply()
    var hpResume: Boolean
        get() = sp.getBoolean("hpResume", false)
        set(v) = sp.edit().putBoolean("hpResume", v).apply()

    /** Plays per time-of-day bucket (night / morning / afternoon / evening) for each song. */
    fun tod(): Map<Long, List<Int>> {
        val o = JSONObject(sp.getString("tod", "{}") ?: "{}")
        return o.keys().asSequence().associate { k ->
            val a = o.getJSONArray(k); k.toLong() to List(4) { i -> a.optInt(i, 0) }
        }
    }
    fun saveTod(m: Map<Long, List<Int>>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.toString(), JSONArray(v)) }
        sp.edit().putString("tod", o.toString()).apply()
    }

    fun lyrics(id: Long): String? = File(dir, "$id.lrc").takeIf { it.exists() }?.readText()
    fun saveLyrics(id: Long, text: String) = File(dir, "$id.lrc").writeText(text)
}

/** 0 = late night, 1 = morning, 2 = afternoon, 3 = evening. */
fun timeBucket(): Int = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 0..5 -> 0; in 6..11 -> 1; in 12..17 -> 2; else -> 3
}
val BucketNames = listOf("Late Night", "Morning", "Afternoon", "Evening")
