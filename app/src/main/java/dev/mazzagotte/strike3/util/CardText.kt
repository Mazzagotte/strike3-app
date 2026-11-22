package dev.mazzagotte.strike3.util

import java.time.Year

/**
 * Turns the text read off a card into details. Plain Kotlin (no Android), so the rules can be
 * tested on their own. Built from going through a real 165-card collection by eye:
 *
 * 1. The "from 2025 Topps Cosmic Chrome Baseball" line on autograph/insert backs gives the
 *    year and set exactly.
 * 2. Years next to Born / Drafted / Debut are never the card year. The copyright line is good;
 *    otherwise the last season in the stats table + 1.
 * 3. Card codes (BCP-139, PD-77, US325…) name the product and go into Notes.
 * 4. The player's name is the line just above the team / position line.
 * 5. Serials must look like a print run (49/199), never a date (6/30/97); the front's foil
 *    stamp is preferred.
 * 6. Grading labels (PSA, CCG, BGS, SGC) give year, set, number and grade in clean text.
 *
 * Every guess says whether it's sure, so the app can mark the ones to check.
 */
object CardText {

    /** A field the reader filled in but isn't sure of. */
    object F {
        const val PLAYER = "player"; const val YEAR = "year"; const val SET = "set"
        const val PARALLEL = "parallel"; const val SERIAL = "serial"; const val TEAM = "team"
    }

    data class Guess(
        val player: String = "",
        val year: String = "",
        val setName: String = "",
        val parallel: String = "",
        val serial: String = "",
        val team: String = "",
        val isAuto: Boolean = false,
        val isRookie: Boolean = false,
        val isRelic: Boolean = false,
        /** Card code, insert name, grade… for Notes. */
        val notes: List<String> = emptyList(),
        /** Fields above that are guesses to double-check. */
        val unsure: Set<String> = emptySet(),
        /** Print run from the serial ("199"), for naming the parallel. */
        val printRun: String = "",
    ) {
        val found: List<String>
            get() = listOfNotNull(
                "player".takeIf { player.isNotBlank() },
                "year".takeIf { year.isNotBlank() },
                "set".takeIf { setName.isNotBlank() },
                "parallel".takeIf { parallel.isNotBlank() },
                "serial".takeIf { serial.isNotBlank() },
                "team".takeIf { team.isNotBlank() },
                "auto".takeIf { isAuto },
                "rookie".takeIf { isRookie },
                "relic".takeIf { isRelic },
            )
    }

    // ---------- Sets ----------

    /** Longer names first so "Bowman Chrome" wins over "Bowman". */
    val SETS = listOf(
        "Topps Chrome Update", "Topps Chrome Platinum Anniversary", "Topps Chrome Black", "Topps Cosmic Chrome",
        "Topps Stadium Club Chrome", "Topps Pro Debut Chrome", "Topps Chrome", "Topps Update", "Topps Heritage",
        "Topps Finest", "Topps Tribute", "Topps Triple Threads", "Topps Museum Collection", "Topps Sterling", "Topps Now",
        "Topps Gypsy Queen", "Topps Stadium Club", "Topps Inception", "Topps Fire", "Topps Gallery", "Topps Big League",
        "Topps Allen & Ginter", "Topps Pristine", "Topps Dynasty", "Topps Diamond Icons", "Topps Black & White",
        "Topps Brooklyn Collection", "Topps Pro Debut", "Topps Archives", "Topps Turkey Red", "Topps Holiday",
        "Topps Heritage Minors", "Topps Project70", "Topps Series 1", "Topps Series 2",
        "Bowman Chrome", "Bowman's Best", "Bowman Draft", "Bowman Sterling", "Bowman Platinum", "Bowman Heritage", "Bowman",
        "Panini Prizm", "Donruss Optic", "Donruss Preferred", "Donruss", "Score Select", "Select", "Panini Contenders",
        "Contenders", "Panini National Treasures", "National Treasures", "Immaculate", "Diamond Kings",
        "Upper Deck Masterpieces", "Upper Deck", "SP Authentic", "Pacific Paramount", "Pacific Omega", "Pinnacle",
        "Score", "Leaf Metal", "Leaf", "Topps",
    )

    /** "TOPPS COSMIC CHROME" / "topps cosmic chrome" -> "Topps Cosmic Chrome" (the known spelling when there is one). */
    fun canonicalSet(raw: String): String {
        val cleaned = raw.replace("®", "").replace("™", "").replace(Regex("""(?<=\p{L})[?’`´](?=s\b)"""), "'")
            .replace(Regex("\\s+"), " ").trim().trim(',', '.')
        SETS.firstOrNull { it.equals(cleaned, ignoreCase = true) }?.let { return it }
        // "Stadium Club" alone is Topps Stadium Club, and so on
        SETS.firstOrNull { it.equals("Topps $cleaned", ignoreCase = true) }?.let { return it }
        // Small misreads: "Traps" -> "Topps", "Bowman Storing" -> "Bowman Sterling"
        val lower = cleaned.lowercase()
        SETS.map { it to editDistance(it.lowercase(), lower) }
            .filter { (name, d) -> d <= maxOf(2, name.length / 4) && d * 2 <= name.length }
            .minByOrNull { it.second }?.let { return it.first }
        return cleaned.split(' ').joinToString(" ") { w ->
            if (w == "&" || w.any { it.isDigit() }) w
            else if (w.length <= 3 && w.all { it.isUpperCase() } && w != "THE") w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }.replace("Bowman's", "Bowman's")
    }

    private fun knownSetIn(text: String): String? {
        val lower = text.lowercase()
        return SETS.firstOrNull { lower.contains(it.lowercase()) }
    }

    // ---------- 1. The "from 2025 Topps … Baseball" line ----------

    // Forgiving of small misreads: "trom 2025 Traps Basehall"
    private val FROM_LINE = Regex(
        """\b(?:from|trom|frorn|fram|frem|fron)\s+(?:the\s+)?((?:19[5-9]|20[0-4])\d)\s+(.{3,60}?)\s+(?:base\s?ball|basehall|basebali|baseba11|basebail|basebal1)\b(\s+update\s+series)?""",
        RegexOption.IGNORE_CASE,
    )

    data class FromLine(val year: String, val set: String)

    fun fromLine(text: String): FromLine? {
        val m = FROM_LINE.find(text) ?: return null
        var set = canonicalSet(m.groupValues[2])
        if (m.groupValues[3].isNotBlank()) set = if (set.contains("Chrome", true)) "Topps Chrome Update" else "Topps Update"
        if (set.length < 3) return null
        return FromLine(m.groupValues[1], set)
    }

    // ---------- 2. Year ----------

    private val YEAR = Regex("""(?<![\d/.-])((?:19[5-9]|20[0-4])\d)(?![\d/])""")
    private val NOT_CARD_YEAR = Regex(
        """\b(born|birth|drafted|draft|debut|acquired|selected|signed|traded|b:|d:|hometown|class of|since)\b[^\n]{0,40}$""",
        RegexOption.IGNORE_CASE,
    )
    private val COPYRIGHT = Regex("""(?:©|\(c\)|copyright)\s*((?:19[5-9]|20[0-4])\d)""", RegexOption.IGNORE_CASE)
    private val COMPANY_YEAR = Regex(
        """((?:19[5-9]|20[0-4])\d)\s+(?:the\s+)?(?:topps|panini|upper deck|leaf|pacific|fleer|score|pinnacle)(?:\s+trading\s+cards?)?\s+(?:company|america|co\b|inc\b|international)""",
        RegexOption.IGNORE_CASE,
    )

    /** Years on the card that aren't birth/draft/debut years. */
    private fun cleanYears(lines: List<String>): List<Int> = lines.flatMap { line ->
        YEAR.findAll(line).filter { m ->
            val before = line.substring(0, m.range.first)
            val after = line.substring(m.range.last + 1)
            // "1990 Topps" / "1989 Topps" is a design being honored, not the card's year
            !Regex("""^\s*(topps|bowman|design|style)\b""", RegexOption.IGNORE_CASE).containsMatchIn(after) &&
            !NOT_CARD_YEAR.containsMatchIn(before) &&
                // month-day dates like "June 26, 1974" or "Aug. 13, 2016"
                !Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+\d{1,2},?\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(before)
        }.map { it.value.toInt() }.toList()
    }

    /** Last season in a stats table: a line that starts with a year and has several numbers after it. */
    private fun lastStatsSeason(lines: List<String>): Int? = lines.mapNotNull { line ->
        val m = Regex("""^\s*'?((?:19[5-9]|20[0-4])\d|\d{2})\*?\b""").find(line) ?: return@mapNotNull null
        val numbers = Regex("""\d+""").findAll(line).count()
        if (numbers < 5) return@mapNotNull null
        val y = m.groupValues[1]
        if (y.length == 4) y.toInt() else 2000 + y.toInt()
    }.filter { it <= Year.now().value }.maxOrNull()

    private data class YearGuess(val year: String, val sure: Boolean)

    private fun year(lines: List<String>, all: String): YearGuess? {
        COPYRIGHT.find(all)?.let { return YearGuess(it.groupValues[1], true) }
        COMPANY_YEAR.find(all)?.let { m ->
            if (!NOT_CARD_YEAR.containsMatchIn(all.substring(0, m.range.first).takeLast(40))) return YearGuess(m.groupValues[1], false)
        }
        lastStatsSeason(lines)?.let { return YearGuess((it + 1).toString(), false) }
        // "No. 26 in the 2005 Series"
        Regex("""((?:19[5-9]|20[0-4])\d)\s+series\b""", RegexOption.IGNORE_CASE).find(all)?.let { return YearGuess(it.groupValues[1], false) }
        // Otherwise the year mentioned most (latest wins a tie), skipping birth/draft/debut and design years
        val years = cleanYears(lines).filter { it <= Year.now().value + 1 }
        return years.groupingBy { it }.eachCount().entries
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            ?.let { YearGuess(it.key.toString(), false) }
    }

    // ---------- 3. Card codes ----------

    /** Code prefix -> (set, insert name, set is sure). Set null when the code alone doesn't say. */
    private data class Code(val set: String?, val insert: String, val sure: Boolean = true)

    private val CODES = mapOf(
        "BCP" to Code("Bowman Chrome", "Bowman Chrome Prospects"),
        "BP" to Code("Bowman", "Bowman Prospects"),
        "CPA" to Code("Bowman Chrome", "Chrome Prospect Autograph"),
        "BCPA" to Code("Bowman Chrome", "Chrome Prospect Autograph"),
        "BSPA" to Code("Bowman Chrome", "Chrome Prospect Autograph", false),
        "PD" to Code("Topps Pro Debut", "Pro Debut"),
        "PDC" to Code("Topps Pro Debut Chrome", "Pro Debut Chrome"),
        "US" to Code("Topps Update", "Update Series"),
        "USC" to Code("Topps Chrome Update", "Update"),
        "UHC" to Code("Topps Chrome Update", "Update", false),
        "ASGC" to Code("Topps Chrome Update", "All-Star Game Chrome"),
        "HC" to Code("Topps Holiday", "Holiday", false),
        "HE" to Code("Topps Holiday", "Holiday", false),
        "SCBA" to Code("Topps Stadium Club", "Stadium Club Autograph"),
        "CCA" to Code("Topps Cosmic Chrome", "Cosmic Chrome Autograph"),
        "RA" to Code(null, "Rookie Autograph"),
        "CRA" to Code("Topps Chrome", "Chrome Rookie Autograph"),
        "BSA" to Code(null, "Baseball Stars Autograph"),
        "AC" to Code(null, "Autograph"),
        "AA" to Code(null, "Archival Autograph"),
        "SA" to Code(null, "Showpieces Autograph"),
        "RS" to Code(null, "Retrospective Signatures"),
        "90A" to Code("Topps", "1990 Topps Autograph", false),
        "90AS" to Code("Topps", "1990 Topps All-Star", false),
        "75YA" to Code("Topps", "75 Years of Topps Autograph", false),
        "ROYF" to Code(null, "Rookie of the Year Favorites"),
        "ROY" to Code(null, "Rookie of the Year Favorites"),
        "MIB" to Code(null, "My 1st Bowman"),
        "TT" to Code(null, "Transformative Talent"),
        "GG" to Code(null, "Galactic Greatness"),
        "UV" to Code(null, "Ultra Violet All-Stars"),
        "MR" to Code(null, "Meteoric Rise"),
        "ES" to Code(null, "Electric Sluggers"),
        "TM" to Code(null, "Topps Magazine"),
        "RCP" to Code("Topps", "Commemorative Rookie Patch", false),
        "CS" to Code(null, "Clear Shots"),
        "FGRA" to Code(null, "Futures Game Autograph Relic"),
        "PS" to Code(null, "Peerless Signatures"),
        "PA" to Code(null, "Autograph"),
        "TP" to Code(null, "Top Prospects"),
        "NYY" to Code("Topps National Baseball Card Day", "Team set", false),
        "AN" to Code("Leaf", "Anime Nation", false),
        "MI" to Code("Leaf Metal", "Metallic Imaginations", false),
        "PP" to Code(null, "Insert"),
    )

    /** Hyphen words that look like codes but aren't. */
    private val NOT_CODES = setOf("ALL", "HIGH", "DOUBLE", "TRIPLE", "SINGLE", "T", "NON", "X", "SIX", "FIVE", "FOUR", "THREE", "TWO", "ONE", "WORLD", "MLB", "AL", "NL", "LOW", "MID", "E", "ON", "AT", "CO", "RE", "PRE", "POST", "SELF", "ANTI", "SEMI", "MULTI", "WALK", "GO", "HIT")

    data class FoundCode(val code: String, val set: String?, val insert: String?, val sure: Boolean)

    fun cardCode(text: String): FoundCode? {
        // "BCP-139", "SCBA-AV", "90A-AV", "RS-DM", "T-5B"
        val dashed = Regex("""(?<![A-Za-z0-9-])([A-Z0-9]{1,5}[A-Z])-([A-Z0-9]{1,5})(?![A-Za-z0-9-])""").findAll(text)
            .firstOrNull { m -> m.groupValues[1] !in NOT_CODES && m.groupValues[1].any { it.isLetter() } && (m.groupValues[2].any { it.isDigit() } || m.groupValues[2].length in 2..4) }
        val plain = Regex("""(?<![A-Za-z0-9-])(USC|US|UHC|HC|HE|ASGC|NYY)\s?-?(\d{1,3})(?![\d])""").find(text)
        val m = dashed ?: plain ?: return null
        val prefix = m.groupValues[1]
        val code = if (m == plain) "$prefix${m.groupValues[2]}" else "$prefix-${m.groupValues[2]}"
        val info = CODES[prefix]
        return FoundCode(code, info?.set, info?.insert, info?.sure ?: false)
    }

    // ---------- 4. Player name ----------

    val TEAMS = listOf(
        "New York Yankees", "New York Mets", "Boston Red Sox", "Toronto Blue Jays", "Baltimore Orioles", "Tampa Bay Rays",
        "Cleveland Guardians", "Detroit Tigers", "Kansas City Royals", "Minnesota Twins", "Chicago White Sox",
        "Houston Astros", "Texas Rangers", "Seattle Mariners", "Los Angeles Angels", "Oakland Athletics", "Athletics",
        "Atlanta Braves", "Philadelphia Phillies", "Miami Marlins", "Washington Nationals", "Chicago Cubs",
        "Milwaukee Brewers", "St. Louis Cardinals", "Cincinnati Reds", "Pittsburgh Pirates", "Los Angeles Dodgers",
        "San Diego Padres", "San Francisco Giants", "Arizona Diamondbacks", "Colorado Rockies",
    )

    /** Lines that mark where the name is: the team line, or a position line. */
    private val ANCHOR = Regex(
        """(yankees|red sox|mets|dodgers|cubs|braves|astros|phillies|giants|cardinals|orioles|blue jays|mariners|padres|""" +
            """somerset patriots|hudson valley|renegades|riverdogs|tampa|scranton|fresno state|team united states|""" +
            """\bshortstop\b|\bpitcher\b|\bcatcher\b|\boutfield(er)?\b|\binfield(er)?\b|\bfirst base(man)?\b|\bsecond base(man)?\b|""" +
            """\bthird base(man)?\b|[•·|,-]\s*(of|ss|p|c|1b|2b|3b|rhp|lhp|dh)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that are on cards but are never a player's name. */
    private val NOT_NAMES = setOf(
        "TOPPS", "BOWMAN", "CHROME", "ROOKIE", "CARD", "CARDS", "CERTIFIED", "AUTOGRAPH", "AUTOGRAPHED", "ISSUE", "AUTHENTIC",
        "MLB", "MAJOR", "LEAGUE", "BASEBALL", "BATS", "THROWS", "HEIGHT", "WEIGHT", "BORN", "DRAFTED", "COMPLETE",
        "SEASON", "PITCHER", "CATCHER", "OUTFIELD", "OUTFIELDER", "INFIELD", "SHORTSTOP", "FIRST", "SECOND", "THIRD",
        "BASE", "PROSPECT", "PROSPECTS", "PANINI", "DONRUSS", "UPPER", "DECK", "COMPANY", "INC", "LICENSED", "PLAYERS",
        "TEAM", "TOTALS", "YEAR", "CLUB", "STATS", "NEW", "YORK", "YANKEES", "THE", "UPDATE", "SERIES", "SELFIE",
        "PICK", "DRAFT", "ALL", "STAR", "STARS", "CONGRATULATIONS", "CHECKLIST", "LEADERS", "GALLERY", "NOTES", "MINOR",
        "RECORD", "BRIEFING", "RESUME", "SKILLS", "CLOSE", "FAVORITES", "DEBUT", "AMERICAN", "NATIONAL", "MINT", "GEM",
        "PSA", "GRADING", "SIGNATURES", "SIGNATURE", "COLLECTION", "MUSEUM", "STADIUM", "SPORTS", "ILLUSTRATED",
        "HOLIDAY", "PERSPECTIVES", "UNLEASHED", "TALENT", "RISE", "PRO", "OF", "SS", "RC", "YOU", "HAVE", "RECEIVED",
        "FROM", "WITH", "BATTING", "PITCHING", "FIELDING", "CAREER", "HITS", "HOME", "RUNS", "ARTWORK", "BY", "REFRACTOR",
        "PRIZM", "SELECT", "SCORE", "LEAF", "PACIFIC", "GOLD", "SILVER", "RED", "BLUE", "GREEN", "ORANGE", "PURPLE",
        "BLACK", "WHITE", "BEST", "MY", "TOP", "FUTURES", "GAME", "CANVAS", "REPRODUCTION", "TRIBUTE", "ICONS",
    )

    private val SUFFIXES = setOf("JR", "JR.", "SR", "SR.", "II", "III", "IV")

    /** "AARON JUDGE • OF" / "Aaron Judge" -> "Aaron Judge"; null if it doesn't look like a person's name. */
    fun nameFrom(line: String): String? {
        val cut = line.split('•', '·', '|', '–').first()
            .replace(Regex("""\s+[-,]\s*(of|ss|p|c|1b|2b|3b|rhp|lhp|dh|inf|outfield(er)?|shortstop|pitcher|catcher)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(no\.?\s*\d+|#\s*\d+|\d+)\s+"""), "")
            .trim().trim(',', ':').let { t -> if (Regex("""\b(jr|sr)\.$""", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else t.trimEnd('.') }
        val words = cut.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size !in 2..4) return null
        val ok = words.all { w ->
            val core = w.replace(".", "").replace("'", "").replace("’", "").replace("-", "")
            core.isNotEmpty() && core.all { it.isLetter() } && (w[0].isUpperCase() || w.uppercase() in SUFFIXES) &&
                (core.length >= 2 || w.endsWith("."))
        } && words.none { it.uppercase().trim('.', ',') in NOT_NAMES } && words.count { it.uppercase() in SUFFIXES } <= 1 &&
            // Misread text tends to make un-pronounceable "words"; real names have vowels and no long consonant runs
            words.filter { it.uppercase().trim('.') !in SUFFIXES && it.length > 2 }.all { w ->
                val l = w.lowercase()
                l.any { it in "aeiouy" } && !Regex("""[^aeiouy'.\-]{4,}""").containsMatchIn(l) && l.length <= 14
            }
        if (!ok) return null
        return titleCase(cut)
    }

    private fun player(lines: List<String>): Pair<String, Boolean>? {
        // The line above the team/position line (or the name part of that same line)
        for ((i, line) in lines.withIndex()) {
            if (!ANCHOR.containsMatchIn(line)) continue
            nameFrom(line)?.let { return it to false }
            for (back in 1..2) lines.getOrNull(i - back)?.let { prev -> nameFrom(prev)?.let { return it to false } }
        }
        // Fallback: first line that looks like a name
        return lines.firstNotNullOfOrNull { nameFrom(it) }?.let { it to false }
    }

    // ---------- 5. Serial ----------

    private val SERIAL = Regex("""(?<![\d/:.$])(\d{1,4})\s?/\s?(\d{1,4})(?![\d/%])""")

    /** "049/199" from a card's text; never a date like 6/30/97 or 9/1/23. */
    fun serial(text: String): String? = SERIAL.findAll(text).map { it.groupValues }.firstOrNull { (_, a, b) ->
        val num = a.toInt(); val den = b.toInt()
        val dateShaped = num in 1..12 && den in 1..31 && den !in setOf(1, 5, 10, 15, 20, 25)
        num in 1..den && den in 1..9999 && !dateShaped
    }?.let { "${it[1]}/${it[2]}" }

    // ---------- 6. Grading labels ----------

    data class Slab(val grader: String, val grade: String, val year: String?, val set: String?, val number: String?)

    private val GRADE = Regex(
        """\b(GEM\s?MT|GEM\s?MINT|PRISTINE|MINT\+?|NM-MT\+?|NM\+?|EX-MT\+?|EX\+?|VG-EX\+?|VG\+?|GOOD|FAIR|POOR|AUTHENTIC)\s*(10|\d(?:\.5)?)?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun slab(lines: List<String>): Slab? {
        val all = lines.joinToString("\n")
        val grader = Regex("""\b(PSA|CCG|CGC|BGS|BECKETT|SGC|HGA|TAG)\b""").find(all)?.value ?: return null
        val g = GRADE.find(all) ?: return null
        val label = lines.take(8)
        val head = label.firstOrNull { Regex("""^\s*(19[5-9]|20[0-4])\d\s+\w""").containsMatchIn(it) }
        val year = head?.let { Regex("""(19[5-9]|20[0-4])\d""").find(it)?.value }
        val set = head?.let { h -> knownSetIn(h.substringAfter(year ?: "").replace(Regex("""#\s*\S+"""), "")) }
        val number = Regex("""#\s*([A-Z0-9-]{1,10})""").find(label.joinToString(" "))?.groupValues?.get(1)
        val grade = listOf(g.groupValues[1].uppercase().replace(Regex("\\s+"), " "), g.groupValues[2]).filter { it.isNotBlank() }.joinToString(" ")
        return Slab(if (grader == "BECKETT") "BGS" else grader, grade, year, set, number)
    }

    // ---------- Putting it together ----------

    fun parse(lines: List<String>): Guess {
        val all = lines.joinToString("\n")
        val flat = lines.joinToString(" ").replace(Regex("""\s+"""), " ")
        val unsure = mutableSetOf<String>()
        val notes = mutableListOf<String>()

        val slab = slab(lines)
        val from = fromLine(flat)
        val code = cardCode(all)

        // Year: grading label or "from …" line (sure), then copyright, stats, other years
        val y = when {
            slab?.year != null -> slab.year to true
            from != null -> from.year to true
            else -> year(lines, all)?.let { it.year to it.sure }
        }
        if (y != null && !y.second) unsure += F.YEAR

        // Set: label / "from …" line (sure), then card code, then a known set name anywhere
        var set = slab?.set ?: from?.set
        if (set == null && code?.set != null) { set = code.set; if (!code.sure) unsure += F.SET }
        if (set == null) knownSetIn(flat)?.let { set = it; unsure += F.SET }

        val serial = serial(all)
        if (serial != null) unsure += F.SERIAL

        val team = TEAMS.firstOrNull { all.contains(it, ignoreCase = true) }.orEmpty()

        val p = player(lines)
        if (p != null) unsure += F.PLAYER

        val isAuto = Regex("""certified\s+autograph|\bautograph(ed)?\b|\bsignatures?\b(?!\s+series)|autographed card|auto\b""", RegexOption.IGNORE_CASE).containsMatchIn(all) ||
            slab?.grade?.contains("AUTHENTIC") == true && Regex("""\bAUTO\b""").containsMatchIn(all)
        val isRookie = Regex("""\brookie card\b|\bRC\b""").containsMatchIn(all)
        val isRelic = Regex("""\brelic\b|\bpatch card\b|\bmemorabilia\b|\bgame-used\b|\bjersey card\b""", RegexOption.IGNORE_CASE).containsMatchIn(all)

        code?.let { notes += listOfNotNull(it.insert, it.code).joinToString(" ") }
        slab?.let { s -> notes += listOfNotNull("${s.grader} ${s.grade}".trim(), s.number?.let { "#$it" }).joinToString(", ") }

        return Guess(
            player = p?.first.orEmpty(),
            year = y?.first.orEmpty(),
            setName = set.orEmpty(),
            serial = serial.orEmpty(),
            team = team,
            isAuto = isAuto,
            isRookie = isRookie,
            isRelic = isRelic,
            notes = notes,
            unsure = unsure,
            printRun = serial?.substringAfter('/')?.trimStart('0').orEmpty(),
        )
    }

    /**
     * Front + back together. Sure readings beat guesses; for the name, one of your logged players
     * found on either side (or a name both sides agree on) is sure. Serial prefers the front's foil
     * stamp, and is only sure when both sides show the same one.
     */
    fun combine(frontLines: List<String>, backLines: List<String>, known: List<String> = emptyList(), borderColor: String? = null): Guess {
        val f = parse(frontLines)
        val b = parse(backLines)
        val unsure = mutableSetOf<String>()

        fun pick(field: String, bv: String, fv: String): String {
            val bSure = bv.isNotBlank() && field !in b.unsure
            val fSure = fv.isNotBlank() && field !in f.unsure
            return when {
                bSure -> bv
                fSure -> fv
                bv.isNotBlank() -> { unsure += field; bv }
                fv.isNotBlank() -> { unsure += field; fv }
                else -> ""
            }
        }

        val knownName = KnownNames.find(backLines, known) ?: KnownNames.find(frontLines, known)
        val agreed = f.player.takeIf { it.isNotBlank() && it.equals(b.player, ignoreCase = true) }
        val player = knownName ?: agreed ?: (b.player.ifBlank { f.player }).also { if (it.isNotBlank()) unsure += F.PLAYER }

        val year = pick(F.YEAR, b.year, f.year)
        var set = pick(F.SET, b.setName, f.setName)
        // "Bowman Briefing" on the back + "Chrome" logo on the front = Bowman Chrome
        if ((set == "Bowman" || set == "Topps") && (frontLines + backLines).any { Regex("""\bchrome\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }) {
            set = "$set Chrome"; unsure += F.SET
        }
        val serial = when {
            f.serial.isNotBlank() && f.serial == b.serial -> f.serial
            f.serial.isNotBlank() -> { unsure += F.SERIAL; f.serial }
            b.serial.isNotBlank() -> { unsure += F.SERIAL; b.serial }
            else -> ""
        }
        val run = serial.substringAfter('/', "").trimStart('0')
        val parallel = parallelFor(set, run, borderColor).orEmpty()
        if (parallel.isNotBlank()) unsure += F.PARALLEL

        return Guess(
            player = player,
            year = year,
            setName = set,
            parallel = parallel,
            serial = serial,
            team = b.team.ifBlank { f.team },
            isAuto = b.isAuto || f.isAuto,
            isRookie = b.isRookie || f.isRookie,
            isRelic = b.isRelic || f.isRelic,
            notes = (b.notes + f.notes).distinct(),
            unsure = unsure,
            printRun = run,
        )
    }

    // ---------- 7. Parallel from print run + color ----------

    /** Print run -> parallel name, by product family. Only used when the border color agrees. */
    private val BOWMAN_CHROME = mapOf(
        "499" to "Refractor", "299" to "Speckle Refractor", "250" to "Purple Refractor", "199" to "Fuchsia Refractor",
        "150" to "Blue Refractor", "125" to "Aqua Refractor", "99" to "Green Refractor", "75" to "Yellow Refractor",
        "50" to "Gold Refractor", "25" to "Orange Refractor", "10" to "Black Refractor", "5" to "Red Refractor", "1" to "Superfractor",
    )
    private val TOPPS_CHROME = mapOf(
        "499" to "Refractor", "299" to "Purple Refractor", "250" to "Purple Refractor", "199" to "Aqua Refractor", "150" to "Blue Refractor",
        "99" to "Green Refractor", "75" to "Yellow Refractor", "50" to "Gold Refractor", "25" to "Orange Refractor",
        "10" to "Black Refractor", "5" to "Red Refractor", "1" to "Superfractor",
    )
    private val PAPER = mapOf(
        "499" to "Purple", "250" to "Purple", "150" to "Blue", "99" to "Green", "75" to "Yellow", "50" to "Gold",
        "25" to "Orange", "10" to "Black", "5" to "Red", "1" to "Platinum 1/1",
    )

    /** Color words that count as a match ("Fuchsia" is pink/purple, "Aqua" is blue/green…). */
    private val COLOR_FAMILY = mapOf(
        "Purple" to setOf("Purple", "Fuchsia", "Pink"), "Pink" to setOf("Pink", "Fuchsia", "Purple"),
        "Blue" to setOf("Blue", "Aqua"), "Aqua" to setOf("Aqua", "Blue", "Green"), "Green" to setOf("Green", "Aqua"),
        "Yellow" to setOf("Yellow", "Gold"), "Gold" to setOf("Gold", "Yellow", "Orange"), "Orange" to setOf("Orange", "Gold"),
        "Red" to setOf("Red"), "Bronze" to setOf("Gold", "Orange"),
    )

    /**
     * Names the parallel when the print run and the border color agree, e.g. Bowman Chrome /150 with a
     * blue border -> "Blue Refractor". Always a guess. Null when they don't line up.
     */
    fun parallelFor(set: String, printRun: String, borderColor: String?): String? {
        if (printRun.isBlank()) return null
        val table = when {
            set.contains("Bowman", true) && (set.contains("Chrome", true) || set.contains("Best", true)) -> BOWMAN_CHROME
            set.contains("Chrome", true) -> TOPPS_CHROME
            else -> PAPER
        }
        val name = table[printRun] ?: return null
        if (printRun == "1") return name
        val nameColor = name.substringBefore(' ').takeIf { it != "Refractor" && it != "Speckle" }
        return when {
            nameColor == null -> name.takeIf { borderColor == null } // plain Refractor: silver border, no color
            borderColor == null -> null
            nameColor in (COLOR_FAMILY[borderColor] ?: setOf(borderColor)) -> name
            else -> null
        }
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w ->
        val upper = w.uppercase().trim('.')
        when {
            upper in setOf("II", "III", "IV") -> upper
            w.startsWith("o'") -> "O'" + w.drop(2).replaceFirstChar { it.uppercase() }
            w.startsWith("mc") && w.length > 2 -> "Mc" + w.drop(2).replaceFirstChar { it.uppercase() }
            else -> w.split("-").joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
        }
    }
}
