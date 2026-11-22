package dev.mazzagotte.strike3.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.mazzagotte.strike3.data.Card
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads the text on a card (usually the back) with Google's on-device text recognition
 * and guesses the details. Guesses only fill fields that are still empty.
 */
object CardReader {

    data class Guess(
        val player: String = "",
        val year: String = "",
        val setName: String = "",
        val serial: String = "",
        val team: String = "",
        val isAuto: Boolean = false,
        val isRookie: Boolean = false,
    ) {
        val found: List<String>
            get() = listOfNotNull(
                "player".takeIf { player.isNotBlank() },
                "year".takeIf { year.isNotBlank() },
                "set".takeIf { setName.isNotBlank() },
                "serial".takeIf { serial.isNotBlank() },
                "team".takeIf { team.isNotBlank() },
                "auto".takeIf { isAuto },
                "rookie".takeIf { isRookie },
            )
    }

    /** [known]: player names already in your collection; a match on the card wins over the plain guess. */
    suspend fun read(bmp: Bitmap, known: List<String> = emptyList()): Guess = readBoth(null, bmp, known)

    /** Text lines on a photo, top of the card first. */
    suspend fun lines(bmp: Bitmap): List<String> {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = client.process(InputImage.fromBitmap(bmp, 0)).await()
            return text.textBlocks.flatMap { b -> b.lines.map { it.text.trim() } }.filter { it.isNotBlank() }
        } finally {
            client.close()
        }
    }

    /**
     * Reads the front and/or the back and combines them. The back is better for year, set and
     * copyright text; the front often has the serial stamp and the name in big print.
     * Player name, best first: one of your logged players found on either side, a name both
     * sides agree on, the back's guess, then the front's.
     */
    suspend fun readBoth(front: Bitmap?, back: Bitmap?, known: List<String> = emptyList()): Guess {
        val frontLines = front?.let { runCatching { lines(it) }.getOrNull() }.orEmpty()
        val backLines = back?.let { runCatching { lines(it) }.getOrNull() }.orEmpty()
        val f = parse(frontLines)
        val b = parse(backLines)
        val agreed = f.player.takeIf { it.isNotBlank() && it.equals(b.player, ignoreCase = true) }
        val player = KnownNames.find(backLines, known)
            ?: KnownNames.find(frontLines, known)
            ?: agreed
            ?: b.player.ifBlank { f.player }
        return Guess(
            player = player,
            year = b.year.ifBlank { f.year },
            setName = b.setName.ifBlank { f.setName },
            serial = b.serial.ifBlank { f.serial },
            team = b.team.ifBlank { f.team },
            isAuto = b.isAuto || f.isAuto,
            isRookie = b.isRookie || f.isRookie,
        )
    }

    /**
     * Like [merge], but a field the reader filled in last time ([before]) can be replaced by a
     * newer reading, so the back can correct a guess made from the front alone. Anything you
     * typed yourself is never overwritten.
     */
    fun mergeOver(card: Card, before: Guess?, g: Guess): Card {
        fun pick(current: String, old: String?, new: String) =
            if (new.isNotBlank() && (current.isBlank() || current == old)) new else current
        return card.copy(
            player = pick(card.player, before?.player, g.player),
            year = pick(card.year, before?.year, g.year),
            setName = pick(card.setName, before?.setName, g.setName),
            serial = pick(card.serial, before?.serial, g.serial),
            team = pick(card.team, before?.team, g.team),
            isAuto = card.isAuto || g.isAuto,
            isRookie = card.isRookie || g.isRookie,
        )
    }

    /**
     * Auto-rotate: finds which way the printed text on the card runs and returns the clockwise turn
     * (0, 90, 180, 270) that makes most of it read upright. Each line of text votes, weighted by how
     * many letters it has. Returns 0 when there isn't enough readable text to be sure.
     */
    suspend fun detectRotation(bmp: Bitmap): Int {
        val k = min(1f, 1600f / max(bmp.width, bmp.height))
        val img = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = client.process(InputImage.fromBitmap(img, 0)).await()
            val votes = IntArray(4)
            var letters = 0
            for (block in text.textBlocks) for (line in block.lines) {
                val pts = line.cornerPoints ?: continue
                if (pts.size < 2) continue
                val weight = line.text.count { it.isLetter() }
                if (weight < 2) continue
                // Corner 0 → corner 1 runs along the line in reading order
                val angle = Math.toDegrees(atan2((pts[1].y - pts[0].y).toDouble(), (pts[1].x - pts[0].x).toDouble()))
                val quarter = (((angle / 90.0).roundToInt() % 4) + 4) % 4 // 0 = upright, 1 = runs downward, ...
                votes[quarter] += weight
                letters += weight
            }
            if (letters < 6) return 0
            val best = votes.indices.maxByOrNull { votes[it] } ?: 0
            if (votes[best] < letters * 0.5f) return 0 // mixed directions: don't guess
            // Text running at +90° (downward) needs a 270° clockwise turn, and so on
            return (360 - best * 90) % 360
        } finally {
            client.close()
        }
    }

    fun merge(card: Card, g: Guess) = card.copy(
        player = card.player.ifBlank { g.player },
        year = card.year.ifBlank { g.year },
        setName = card.setName.ifBlank { g.setName },
        serial = card.serial.ifBlank { g.serial },
        team = card.team.ifBlank { g.team },
        isAuto = card.isAuto || g.isAuto,
        isRookie = card.isRookie || g.isRookie,
    )

    // Longer names first so "Bowman Chrome" wins over "Bowman".
    private val SETS = listOf(
        "Topps Chrome Update", "Topps Chrome", "Topps Update", "Topps Heritage", "Topps Finest", "Topps Tribute",
        "Topps Triple Threads", "Topps Museum Collection", "Topps Sterling", "Topps Now", "Topps Series 1",
        "Topps Series 2", "Topps Gypsy Queen", "Topps Stadium Club", "Topps Inception", "Topps Fire", "Topps Gallery",
        "Topps Big League", "Topps Allen & Ginter", "Topps Pristine", "Topps Dynasty", "Topps Diamond Icons",
        "Bowman Chrome", "Bowman's Best", "Bowman Draft", "Bowman Sterling", "Bowman Platinum", "Bowman",
        "Stadium Club", "Gypsy Queen", "Allen & Ginter", "Heritage", "Finest",
        "Panini Prizm", "Donruss Optic", "Donruss", "Select", "Contenders", "National Treasures", "Immaculate",
        "Diamond Kings", "Upper Deck", "SP Authentic", "Leaf", "Topps",
    )

    private val TEAMS = listOf(
        "New York Yankees", "New York Mets", "Boston Red Sox", "Toronto Blue Jays", "Baltimore Orioles", "Tampa Bay Rays",
        "Cleveland Guardians", "Detroit Tigers", "Kansas City Royals", "Minnesota Twins", "Chicago White Sox",
        "Houston Astros", "Texas Rangers", "Seattle Mariners", "Los Angeles Angels", "Athletics", "Oakland Athletics",
        "Atlanta Braves", "Philadelphia Phillies", "Miami Marlins", "Washington Nationals", "Chicago Cubs",
        "Milwaukee Brewers", "St. Louis Cardinals", "Cincinnati Reds", "Pittsburgh Pirates", "Los Angeles Dodgers",
        "San Diego Padres", "San Francisco Giants", "Arizona Diamondbacks", "Colorado Rockies",
    )

    // Words that are on cards but are never the player's name.
    private val NOT_NAMES = setOf(
        "TOPPS", "BOWMAN", "CHROME", "ROOKIE", "CARD", "CERTIFIED", "AUTOGRAPH", "ISSUE", "AUTHENTIC", "MLB",
        "MAJOR", "LEAGUE", "BASEBALL", "BATS", "THROWS", "HEIGHT", "WEIGHT", "BORN", "DRAFTED", "COMPLETE", "SEASON",
        "PITCHER", "CATCHER", "OUTFIELD", "OUTFIELDER", "INFIELD", "SHORTSTOP", "FIRST", "SECOND", "THIRD", "BASE",
        "PROSPECT", "PROSPECTS", "PANINI", "DONRUSS", "UPPER", "DECK", "COMPANY", "INC", "LICENSED", "PLAYERS",
        "TEAM", "TOTALS", "YEAR", "CLUB", "STATS", "NEW", "YORK", "YANKEES", "THE",
    )

    fun parse(lines: List<String>): Guess {
        val all = lines.joinToString("\n")
        val lower = all.lowercase()

        val serial = Regex("""\b(\d{1,4})\s*/\s*(\d{1,4})\b""").findAll(all)
            .map { it.groupValues }
            .firstOrNull { (_, a, b) -> a.toInt() in 1..b.toInt() && b.toInt() in 1..9999 && b.length >= 1 && !(b.length == 2 && a.length <= 2 && b.toInt() <= 31 && a.toInt() <= 12) }
            ?.let { "${it[1]}/${it[2]}" } ?: ""

        // Copyright year is the most reliable ("© 2024 The Topps Company")
        val year = Regex("""(?:©|\(c\)|copyright)\s*(19[5-9]\d|20[0-4]\d)""", RegexOption.IGNORE_CASE).find(all)?.groupValues?.get(1)
            ?: Regex("""\b(19[5-9]\d|20[0-4]\d)\b""").findAll(all).map { it.value }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: ""

        val setName = SETS.firstOrNull { lower.contains(it.lowercase()) } ?: ""
        val team = TEAMS.firstOrNull { lower.contains(it.lowercase()) } ?: ""

        val isAuto = Regex("""certified\s+autograph|\bautograph\b""", RegexOption.IGNORE_CASE).containsMatchIn(all)
        val isRookie = Regex("""\brookie\b|\bRC\b""").containsMatchIn(all) || lower.contains("rookie card")

        // Player: first line of 2–3 capitalized words that isn't card boilerplate
        val player = lines.firstOrNull { line ->
            val words = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
            words.size in 2..3 &&
                words.all { w -> w.length >= 2 && w.replace(".", "").replace("'", "").replace("-", "").all { it.isLetter() } && w[0].isUpperCase() } &&
                words.none { it.uppercase().trim('.', ',') in NOT_NAMES } &&
                TEAMS.none { t -> line.equals(t, ignoreCase = true) }
        }?.let { titleCase(it) } ?: ""

        return Guess(player, year, setName, serial, team, isAuto, isRookie)
    }

    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w ->
        w.split("-").joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
    }
}
