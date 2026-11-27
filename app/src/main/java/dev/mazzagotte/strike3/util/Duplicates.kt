package dev.mazzagotte.strike3.util

import dev.mazzagotte.strike3.data.Card

/**
 * Same card already logged: same player, year, set, parallel and print run
 * (ignoring capitals and extra spaces). Needs at least a player to compare.
 */
object Duplicates {
    fun find(card: Card, all: List<Card>): Card? {
        if (card.player.isBlank()) return null
        val key = key(card)
        return all.firstOrNull { it.id != card.id && key(it) == key }
    }

    private fun key(c: Card) = listOf(c.player, c.year, c.setName, c.parallel, c.serial)
        .joinToString("|") { it.trim().lowercase().replace(Regex("\\s+"), " ") }
}
