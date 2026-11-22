package dev.mazzagotte.strike3.util

import kotlin.math.max
import kotlin.math.min

/**
 * Finds a player you've already logged in the text read off a card.
 *
 * Every name you save in the collection becomes a known name, so the reader gets better the more
 * you catalog, and fixing a wrong name once teaches it for next time. Matching allows for small
 * misreads ("LOMBARD JN" still finds George Lombard Jr.) and for a name split across two lines.
 */
object KnownNames {

    /** Best known name found in [lines], or null. Earlier (higher on the card) lines win ties. */
    fun find(lines: List<String>, known: List<String>): String? {
        val names = known.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { norm(it) }
            .map { it to norm(it).split(' ') }
            .filter { (_, words) -> words.size >= 2 } // a last name alone is too easy to mistake
        if (names.isEmpty() || lines.isEmpty()) return null

        val rows = lines.map { norm(it).split(' ').filter { w -> w.isNotEmpty() } }
        var best: Pair<String, Double>? = null
        for ((i, row) in rows.withIndex()) {
            // This line, and this line joined with the next (name split across two lines)
            val candidates = listOf(row) + listOfNotNull(rows.getOrNull(i + 1)?.let { row + it })
            for ((name, words) in names) {
                val score = candidates.maxOf { bestWindow(it, words) }
                if (score > (best?.second ?: 0.0)) best = name to score
            }
            if (best != null && best.second >= 0.999) break // exact match, stop looking
        }
        return best?.first
    }

    /** Highest similarity between [name] and any run of the same number of words in [row]; 0 if below the bar. */
    private fun bestWindow(row: List<String>, name: List<String>): Double {
        if (row.size < name.size) return 0.0
        val target = name.joinToString(" ")
        var best = 0.0
        for (start in 0..row.size - name.size) {
            val piece = row.subList(start, start + name.size).joinToString(" ")
            val d = distance(piece, target)
            val allowed = when {
                target.length < 9 -> 1
                target.length < 15 -> 2
                else -> 3
            }
            if (d <= allowed) best = max(best, 1.0 - d.toDouble() / max(piece.length, target.length))
        }
        return best
    }

    /** Uppercase letters and single spaces only: "George Lombard Jr." -> "GEORGE LOMBARD JR". */
    fun norm(s: String): String =
        s.uppercase().map { if (it.isLetter()) it else ' ' }.joinToString("").trim().replace(Regex("\\s+"), " ")

    /** Edit distance (letters added, removed or swapped). */
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
