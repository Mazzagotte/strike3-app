package dev.mazzagotte.strike3.util

import android.content.Context
import dev.mazzagotte.strike3.data.Card
import kotlin.math.abs

/** The caption looks you can pick on a card. */
enum class CaptionStyle(val label: String) {
    CLEAN("Clean"),
    STRIKE("Strike III"),
    SHORT("Short"),
}

/**
 * Template captions built only from what's in the card log, so the parallel and serial
 * are always exactly what you typed. No internet, no AI.
 */
object Captions {
    private const val PREFS = "captions"
    private const val KEY_STYLE = "style"

    /** Style used by every Share button. Remembers whatever you last picked on a card. */
    fun defaultStyle(c: Context): CaptionStyle =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STYLE, null)
            ?.let { runCatching { CaptionStyle.valueOf(it) }.getOrNull() } ?: CaptionStyle.CLEAN

    fun setDefaultStyle(c: Context, style: CaptionStyle) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STYLE, style.name).apply()
    }

    fun build(card: Card, style: CaptionStyle): String {
        val body = when (style) {
            CaptionStyle.CLEAN -> clean(card)
            CaptionStyle.STRIKE -> strike(card)
            CaptionStyle.SHORT -> short(card)
        }
        val tags = if (style == CaptionStyle.SHORT) shortTags(card) else fullTags(card)
        return (body.filter { it.isNotBlank() } + "" + tags.joinToString(" ")).joinToString("\n").trim()
    }

    // ---- Styles ----

    /** Player, set line, serial, then auto/rookie/relic. */
    private fun clean(card: Card): List<String> {
        val name = listOfNotNull(card.player.trim().ifBlank { null }, "RC".takeIf { card.isRookie }).joinToString(" ")
        val setLine = listOf(card.year, card.setName, card.parallel).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(
            name,
            setLine,
            serialPhrase(card)?.let { "$it ⚾" }.orEmpty(),
            flags(card),
        )
    }

    /** Umpire call opener, then the card joining the collection. */
    private fun strike(card: Card): List<String> {
        val calls = listOf(
            "Strike three looking 🔒",
            "Called strike three ⚾",
            "Ring him up 🔔",
            "Strike three. Grab some bench.",
            "Punch-out ✊",
        )
        // Same card always gets the same opener, so the caption doesn't change between taps.
        val opener = calls[abs((card.id.takeIf { it != 0L } ?: card.player.hashCode().toLong()) % calls.size).toInt()]
        val what = listOf(card.player, card.parallel).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .ifBlank { "This one" }
        val serial = shortSerial(card)?.let { " $it" }.orEmpty()
        val extras = listOfNotNull(
            "auto".takeIf { card.isAuto },
            "rookie".takeIf { card.isRookie },
            "relic".takeIf { card.isPatch },
        )
        val extra = if (extras.isEmpty()) "" else " (" + extras.joinToString(", ") + ")"
        val where = teamShort(card.team)?.let { "the $it PC" } ?: "the collection"
        return listOf(opener, "$what$serial$extra joins $where.")
    }

    /** One line. */
    private fun short(card: Card): List<String> {
        val parts = listOf(card.player, card.parallel).map { it.trim() }.filter { it.isNotEmpty() } +
            listOfNotNull(shortSerial(card), "RC".takeIf { card.isRookie }, "Auto".takeIf { card.isAuto })
        return listOf(parts.joinToString(" ").ifBlank { "New pickup" } + " ⚾")
    }

    // ---- Pieces ----

    /** "Numbered 049/199", "One of one", or null when there's no serial. */
    private fun serialPhrase(card: Card): String? {
        val s = card.serial.trim()
        if (s.isEmpty()) return null
        return if (isOneOfOne(s)) "One of one 🔥" else "Numbered $s"
    }

    /** "/199" style for inline use ("1/1" stays as is); a number without "out of" is shown as #049. */
    private fun shortSerial(card: Card): String? {
        val s = card.serial.trim()
        if (s.isEmpty()) return null
        if (isOneOfOne(s)) return "1/1"
        val of = s.substringAfter('/', "")
        return if (of.isNotEmpty()) "/$of" else "#$s"
    }

    private fun isOneOfOne(s: String) = s.substringAfter('/', "").trimStart('0') == "1"

    private fun flags(card: Card): String = listOfNotNull(
        "Auto ✍️".takeIf { card.isAuto },
        "Relic".takeIf { card.isPatch },
    ).joinToString(" • ")

    /** "New York Yankees" -> "Yankees", "Boston Red Sox" -> "Red Sox". */
    private fun teamShort(team: String): String? {
        val words = team.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val two = words.takeLast(2).joinToString(" ")
        return if (two in setOf("Red Sox", "White Sox", "Blue Jays")) two else words.last()
    }

    private fun fullTags(card: Card): List<String> = listOfNotNull(
        hashtag(card.player),
        hashtag(card.team),
        hashtag(card.setName),
        "#RookieCard".takeIf { card.isRookie },
        "#Auto".takeIf { card.isAuto },
        "#Relic".takeIf { card.isPatch },
        "#Numbered".takeIf { card.serial.isNotBlank() },
        "#TheHobby", "#SportsCards", "#BaseballCards", "#StrikeIIICards",
    ).distinct()

    private fun shortTags(card: Card): List<String> = listOfNotNull(
        hashtag(card.player),
        hashtag(teamShort(card.team).orEmpty()),
        "#StrikeIIICards",
    ).distinct()

    private fun hashtag(s: String): String? {
        val t = s.filter { it.isLetterOrDigit() }
        return if (t.isBlank()) null else "#$t"
    }
}
