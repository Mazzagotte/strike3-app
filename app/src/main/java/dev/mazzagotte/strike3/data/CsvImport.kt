package dev.mazzagotte.strike3.data

import java.time.LocalDate

/**
 * Reads a collection CSV back in (one exported from the app, edited in Google Sheets or Excel).
 *
 * - Rows are matched to cards by the ID column. Leave it alone; it's how a row finds its card.
 * - A row with no ID (or an ID that isn't in the app) becomes a new card, without photos.
 * - Columns are found by their header name, so moving columns around is fine. Columns the app
 *   doesn't know are ignored, and a missing column leaves that detail unchanged.
 * - What's in a cell replaces what's on the card, including a cell you emptied on purpose.
 * - Photos, posts and editor settings are never touched by an import.
 */
object CsvImport {

    data class Plan(val updated: List<Card>, val added: List<Card>, val unchanged: Int, val skipped: Int)

    fun plan(text: String, existing: List<Card>): Plan = planRows(parse(text), existing)

    /** Same as [plan], from rows already split into cells (CSV or Excel). */
    fun planRows(allRows: List<List<String>>, existing: List<Card>): Plan {
        val rows = allRows.filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) error("the file is empty")
        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }

        val cId = col("id")
        val cPlayer = col("player") ?: error("no Player column. Is this a Strike III export?")
        val cYear = col("year")
        val cSet = col("set", "set name")
        val cParallel = col("parallel")
        val cCard = col("card #", "card number", "card no", "card no.", "card num", "number", "#")
        val cSerial = col("serial", "serial number")
        val cTeam = col("team")
        val cAuto = col("auto")
        val cRookie = col("rookie", "rc")
        val cRelic = col("relic", "patch")
        val cValue = col("current value", "value")
        val cDate = col("date acquired")
        val cPosted = col("posted")
        val cNotes = col("notes")
        val cCheck = col("check")

        val byId = existing.associateBy { it.id }
        val updated = mutableListOf<Card>()
        val added = mutableListOf<Card>()
        var unchanged = 0
        var skipped = 0

        for (row in rows.drop(1)) {
            fun text(i: Int?) = i?.let { row.getOrNull(it) }?.let(::unwrap)?.trim()
            fun apply(base: Card): Card {
                var c = base
                text(cPlayer)?.let { c = c.copy(player = it) }
                text(cYear)?.let { c = c.copy(year = it.removeSuffix(".0")) }
                text(cSet)?.let { c = c.copy(setName = it) }
                text(cParallel)?.let { c = c.copy(parallel = it) }
                text(cCard)?.let { c = c.copy(cardNumber = it.uppercase().removePrefix("#").trim()) }
                text(cSerial)?.let { c = c.copy(serial = it.replace(" ", "")) }
                text(cTeam)?.let { c = c.copy(team = it) }
                text(cAuto)?.let { c = c.copy(isAuto = yes(it)) }
                text(cRookie)?.let { c = c.copy(isRookie = yes(it)) }
                text(cRelic)?.let { c = c.copy(isPatch = yes(it)) }
                text(cValue)?.let { c = c.copy(currentValue = it) }
                text(cDate)?.let { c = c.copy(dateAcquired = isoDate(it)) }
                text(cPosted)?.let { c = c.copy(posted = yes(it)) }
                text(cNotes)?.let { c = c.copy(notes = it) }
                // "Check": fields still to double-check; clear the cell once you've checked them
                text(cCheck)?.let { v -> c = c.withUnsure(v.split(',', ';', ' ').map { it.trim().lowercase() }.filter { it in CHECK_FIELDS }.toSet()) }
                return c
            }

            val id = text(cId)?.removeSuffix(".0")?.toLongOrNull()
            val card = id?.let { byId[it] }
            if (card != null) {
                val changed = apply(card)
                if (changed != card) updated += changed else unchanged++
            } else {
                val new = apply(Card())
                if (new.player.isBlank() && new.setName.isBlank()) { skipped++; continue }
                added += new.copy(id = 0)
            }
        }
        return Plan(updated, added, unchanged, skipped)
    }

    private val CHECK_FIELDS = setOf("player", "year", "set", "parallel", "serial", "team", "card")

    private fun yes(s: String) = s.trim().lowercase() in setOf("yes", "y", "true", "x", "1", "✓", "✔")

    /** ="1/1" (how the export keeps Sheets from turning serials into dates) -> 1/1 */
    private fun unwrap(s: String): String {
        val t = s.trim()
        return if (t.startsWith("=\"") && t.endsWith("\"") && t.length >= 3) t.substring(2, t.length - 1) else s
    }

    /** Keeps 2026-11-21 as is; turns 11/21/2026 (how Sheets often writes dates) into 2026-11-21. */
    private fun isoDate(s: String): String {
        // Excel stores an edited date as a day count from 1899-12-30
        s.trim().removeSuffix(".0").toIntOrNull()?.takeIf { it in 20000..80000 }?.let {
            return LocalDate.of(1899, 12, 30).plusDays(it.toLong()).toString()
        }
        val m = Regex("""^(\d{1,2})/(\d{1,2})/(\d{2,4})$""").find(s.trim()) ?: return s
        val (mo, d, y) = m.destructured
        val year = if (y.length == 2) "20$y" else y
        return runCatching { LocalDate.of(year.toInt(), mo.toInt(), d.toInt()).toString() }.getOrDefault(s)
    }

    /** CSV rows, handling quoted cells with commas, quotes and line breaks. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val ch = s[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
}
