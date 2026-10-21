package com.aura.player

data class LrcLine(val t: Long, val text: String)

object Lrc {
    private val tag = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    /** Parses .lrc text. Timed lines are synced; if there are no timestamps, lines are plain (t = -1). */
    fun parse(raw: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        for (line in raw.lines()) {
            val tags = tag.findAll(line).toList()
            val text = line.replace(tag, "").trim()
            if (tags.isEmpty()) {
                if (text.isNotEmpty() && !text.startsWith("[")) out += LrcLine(-1, text)
                continue
            }
            for (m in tags) {
                val mm = m.groupValues[1].toLong()
                val ss = m.groupValues[2].toLong()
                val fr = m.groupValues[3]
                val f = if (fr.isEmpty()) 0L else fr.padEnd(3, '0').take(3).toLong()
                out += LrcLine(mm * 60000 + ss * 1000 + f, text)
            }
        }
        val timed = out.filter { it.t >= 0 }.sortedBy { it.t }
        return if (timed.isNotEmpty()) timed else out
    }
}
