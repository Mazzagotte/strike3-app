package dev.mazzagotte.strike3.util

import dev.mazzagotte.strike3.data.Card
import java.text.Normalizer

/**
 * Finding cards you already have, by details (player, card #, year, set, parallel, print run)
 * and, for "Do I have this?", by how the photo looks ([CardPrint]).
 *
 * Details are compared loosely: capitals, accents, "Jr.", dashes and leading zeros don't matter
 * ("Elmer Rodriguez" = "Elmer Rodríguez", "BD118" = "BD-118"), "Topps" fits "Topps Pro Debut",
 * and a field that's blank on either card doesn't count against a match.
 */
object Duplicates {

    enum class Kind(val label: String) {
        SAME("You have this card"),
        MAYBE_SAME("Same card – check the parallel"),
        OTHER_VERSION("Same card, different version"),
        LOOKS_LIKE("Looks like one you have"),
        MIGHT_BE("Might be this one"),
        SAME_PLAYER_SET("Same player and set"),
    }

    data class Match(val card: Card, val kind: Kind, val photo: Double = 0.0) {
        /** Photo agrees strongly too. */
        val photoAgrees: Boolean get() = photo >= CardPrint.FRONT_STRONG
    }

    /** For the "already logged?" question on save: only an exact same card (same parallel and print run). */
    fun find(card: Card, all: List<Card>): Card? {
        if (card.player.isBlank()) return null
        return all.firstOrNull { it.id != card.id && textKind(card, it) == Kind.SAME }
    }

    /**
     * Everything in [all] that could be [probe], best first. [prints]: fingerprints of the photo
     * (upright and upside down); the photo may be a front or a back.
     */
    fun matches(probe: Card, all: List<Card>, prints: List<ByteArray> = emptyList(), max: Int = 12): List<Match> {
        val out = all.filter { it.id != probe.id }.mapNotNull { c ->
            val front = CardPrint.decode(c.frontPrint)
            val back = CardPrint.decode(c.backPrint)
            val f = prints.maxOfOrNull { CardPrint.similarity(it, front) } ?: 0.0
            val b = prints.maxOfOrNull { CardPrint.similarity(it, back) } ?: 0.0
            // A back match needs a higher score; put it on the front's scale so they can be ranked together
            val photo = maxOf(f, if (b >= CardPrint.BACK_MAYBE) b - (CardPrint.BACK_STRONG - CardPrint.FRONT_STRONG) else 0.0)
            val kind = textKind(probe, c)
                ?: when {
                    photo >= CardPrint.FRONT_STRONG -> Kind.LOOKS_LIKE
                    photo >= CardPrint.FRONT_MAYBE -> Kind.MIGHT_BE
                    else -> null
                }
            kind?.let { Match(c, it, photo) }
        }
        // Photo-only matches: keep the few closest, so similar-looking cards don't flood the list
        val photoOnly = out.filter { it.kind == Kind.LOOKS_LIKE || it.kind == Kind.MIGHT_BE }.sortedByDescending { it.photo }.take(3).toSet()
        return out.filter { (it.kind != Kind.LOOKS_LIKE && it.kind != Kind.MIGHT_BE) || it in photoOnly }
            .sortedWith(compareBy<Match> { it.kind.ordinal }.thenByDescending { it.photo })
            .take(max)
    }

    /** How [a] relates to [b] by details alone, or null when they don't look related. */
    fun textKind(a: Card, b: Card): Kind? {
        val pa = name(a.player); val pb = name(b.player)
        val playerKnown = pa.isNotEmpty() && pb.isNotEmpty()
        val samePlayer = playerKnown && (pa == pb || (minOf(pa.length, pb.length) >= 8 && distance(pa, pb) <= 1))
        if (playerKnown && !samePlayer) return null
        val na = number(a.cardNumber); val nb = number(b.cardNumber)
        val ya = a.year.trim(); val yb = b.year.trim()
        val sa = set(a.setName); val sb = set(b.setName)
        val yearsAgree = ya.isEmpty() || yb.isEmpty() || ya == yb
        val setsFit = sa.isEmpty() || sb.isEmpty() || sa.startsWith(sb) || sb.startsWith(sa)
        val bothYears = ya.isNotEmpty() && ya == yb
        val bothSets = sa.isNotEmpty() && sb.isNotEmpty() && setsFit

        val sameCard = when {
            na.isNotEmpty() && nb.isNotEmpty() ->
                na == nb && yearsAgree && setsFit && (samePlayer || bothYears || bothSets)
            // No number on one of them: needs the player plus year and set both agreeing
            else -> samePlayer && bothYears && bothSets
        }
        if (!sameCard) {
            return if (samePlayer && bothYears && bothSets) Kind.SAME_PLAYER_SET else null
        }
        // Same card: which version?
        val ra = run(a.serial); val rb = run(b.serial)
        val qa = parallel(a.parallel); val qb = parallel(b.parallel)
        val runsClash = ra.isNotEmpty() && rb.isNotEmpty() && ra != rb
        val parallelsClash = qa.isNotEmpty() && qb.isNotEmpty() && qa != qb
        return when {
            runsClash || parallelsClash -> Kind.OTHER_VERSION
            ra == rb && qa == qb -> Kind.SAME
            else -> Kind.MAYBE_SAME // one side doesn't say
        }
    }

    // ---------- Normalizing ----------

    private fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    fun name(s: String) = plain(s).replace(Regex("[^a-z ]"), " ")
        .replace(Regex("\\b(jr|sr|ii|iii|iv)\\b"), " ").replace(Regex("\\s+"), " ").trim()

    fun number(s: String) = s.uppercase().replace(Regex("^(NO\\.?|#)\\s*"), "").replace(Regex("[^A-Z0-9]"), "")
        .replace(Regex("(?<![0-9])0+(?=[0-9])"), "")

    private fun set(s: String) = plain(s).replace("'", "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun run(s: String) = s.substringAfter('/', s).trim().trimStart('0')
    private fun parallel(s: String) = plain(s).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        .removeSuffix(" parallel").let { if (it == "base") "" else it }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
