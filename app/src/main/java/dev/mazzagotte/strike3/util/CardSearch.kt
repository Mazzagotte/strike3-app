package dev.mazzagotte.strike3.util

import dev.mazzagotte.strike3.data.Card
import kotlin.math.min

/** How the collection is ordered. */
enum class SortBy(val label: String) {
    NEWEST("Newest added"),
    OLDEST("Oldest added"),
    PLAYER("Player A–Z"),
    YEAR("Year, newest first"),
    PRINT_RUN("Print run, lowest first"),
}

/**
 * Collection search: every word you type has to match something on the card, in any field,
 * so "judge 2024 chrome auto" narrows down the way you'd expect.
 *
 * Special words: auto / rc / rookie / relic / patch / numbered / 1/1 / "/99" (print run).
 * Player and team names allow a small typo once a word is 4+ letters ("jeeter" finds Jeter).
 */
object CardSearch {

    fun matches(card: Card, query: String): Boolean {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.all { matchesWord(card, it) }
    }

    private fun matchesWord(card: Card, w: String): Boolean {
        // Card flags
        when (w) {
            "auto", "autos", "autograph", "autographs", "signed" -> if (card.isAuto) return true
            "rc", "rcs", "rookie", "rookies" -> if (card.isRookie) return true
            "relic", "relics", "patch", "patches", "jersey" -> if (card.isPatch) return true
            "numbered", "serial", "serialized" -> if (card.serial.isNotBlank()) return true
            "1/1", "1of1", "oneofone" -> if (isOneOfOne(card)) return true
            "posted" -> if (card.posted) return true
        }
        // "/99" = print run of 99
        if (w.startsWith("/") && w.length > 1 && w.drop(1).all { it.isDigit() }) {
            return printRun(card) == w.drop(1).trimStart('0')
        }
        // Plain text in any field
        val fields = listOf(card.player, card.team, card.setName, card.parallel, card.year, card.serial, card.notes)
        if (fields.any { it.lowercase().contains(w) }) return true
        // Small typo in a player or team name
        if (w.length >= 4 && w.all { it.isLetter() }) {
            val allowed = if (w.length < 7) 1 else 2
            val names = (card.player + " " + card.team).lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 }
            if (names.any { distance(it, w) <= allowed || (it.length > w.length && distance(it.take(w.length), w) <= 1) }) return true
        }
        return false
    }

    /** "049/199" -> "199"; "" when not numbered. */
    fun printRun(card: Card): String = card.serial.substringAfter('/', "").trimStart('0')

    fun isOneOfOne(card: Card) = printRun(card) == "1"

    fun sort(cards: List<Card>, by: SortBy): List<Card> = when (by) {
        SortBy.NEWEST -> cards.sortedByDescending { it.createdAt }
        SortBy.OLDEST -> cards.sortedBy { it.createdAt }
        SortBy.PLAYER -> cards.sortedWith(
            compareBy<Card>({ it.player.isBlank() }, { it.player.lowercase() }).thenByDescending { it.year }.thenByDescending { it.createdAt },
        )
        SortBy.YEAR -> cards.sortedWith(
            compareBy<Card> { it.year.isBlank() }.thenByDescending { it.year }.thenBy { it.player.lowercase() },
        )
        SortBy.PRINT_RUN -> cards.sortedWith(
            compareBy<Card> { printRun(it).toIntOrNull() ?: Int.MAX_VALUE }.thenByDescending { it.createdAt },
        )
    }

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
