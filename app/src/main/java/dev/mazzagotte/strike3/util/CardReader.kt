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
 * Reads the text on a card with Google's on-device text recognition; [CardText] turns the
 * text into details. Guesses only fill fields that are still empty (or that it filled itself).
 */
object CardReader {

    /** [known]: player names already in your collection; a match on the card wins over the plain guess. */
    suspend fun read(bmp: Bitmap, known: List<String> = emptyList()): CardText.Guess = readBoth(null, bmp, known)

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
     * Reads the front and/or the back and combines them with the rules in [CardText]: the
     * "from 2025 Topps … Baseball" line, card codes, grading labels, the name above the team line,
     * serials that look like print runs, and the parallel from print run + border color.
     * Each guess says which fields are unsure.
     */
    suspend fun readBoth(front: Bitmap?, back: Bitmap?, known: List<String> = emptyList()): CardText.Guess {
        val frontLines = front?.let { runCatching { lines(it) }.getOrNull() }.orEmpty()
        val backLines = back?.let { runCatching { lines(it) }.getOrNull() }.orEmpty()
        val color = front?.let { runCatching { ParallelHints.borderColor(it) }.getOrNull() }
        return CardText.combine(frontLines, backLines, known, color)
    }

    /**
     * Fills the card from a reading. A field the reader filled in last time ([before]) can be
     * replaced by a newer reading, so the back can correct a guess made from the front alone.
     * Anything you typed yourself is never overwritten. Unsure fields are remembered on the card.
     */
    fun mergeOver(card: Card, before: CardText.Guess?, g: CardText.Guess): Card {
        val unsure = card.unsure.toMutableSet()
        fun pick(field: String, current: String, old: String?, new: String): String {
            if (new.isBlank() || !(current.isBlank() || current == old)) return current
            if (field in g.unsure) unsure += field else unsure -= field
            return new
        }
        val notes = (listOf(card.notes) + g.notes.filter { n -> !card.notes.contains(n, ignoreCase = true) && before?.notes?.contains(n) != true })
            .filter { it.isNotBlank() }.joinToString("; ")
        return card.copy(
            player = pick(CardText.F.PLAYER, card.player, before?.player, g.player),
            year = pick(CardText.F.YEAR, card.year, before?.year, g.year),
            setName = pick(CardText.F.SET, card.setName, before?.setName, g.setName),
            parallel = pick(CardText.F.PARALLEL, card.parallel, before?.parallel, g.parallel),
            serial = pick(CardText.F.SERIAL, card.serial, before?.serial, g.serial),
            team = pick(CardText.F.TEAM, card.team, before?.team, g.team),
            cardNumber = pick(CardText.F.CARD, card.cardNumber, before?.cardNumber, g.cardNumber),
            isAuto = card.isAuto || g.isAuto,
            isRookie = card.isRookie || g.isRookie,
            isPatch = card.isPatch || g.isRelic,
            notes = notes,
        ).withUnsure(unsure)
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
}
