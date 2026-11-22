package dev.mazzagotte.strike3.util

import android.graphics.Bitmap
import android.graphics.Color
import dev.mazzagotte.strike3.data.Card
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Parallel suggestions. Nothing here fills the field by itself: the details screen shows
 * these as chips and you tap one to use it.
 */
object ParallelHints {

    /**
     * Parallel names you've already used on cards from the same set and print run (the "/199"),
     * best matches first: same year, then other years. Up to [limit] distinct names.
     * If a color was spotted in the photo, names containing that color move to the front.
     */
    fun fromLog(card: Card, all: List<Card>, color: String? = null, limit: Int = 3): List<String> {
        val set = card.setName.norm()
        if (set.isEmpty()) return emptyList()
        val run = printRun(card.serial)
        val year = card.year.trim()

        val matches = all.filter { other ->
            other.id != card.id &&
                other.parallel.isNotBlank() &&
                other.setName.norm() == set &&
                printRun(other.serial) == run
        }
        val ranked = matches.sortedWith(
            compareByDescending<Card> { year.isNotEmpty() && it.year.trim() == year }
                .thenByDescending { color != null && it.parallel.contains(color, ignoreCase = true) }
                .thenByDescending { it.createdAt },
        )
        return ranked.map { it.parallel.trim() }.distinctBy { it.lowercase() }.take(limit)
    }

    /** "049/199" -> "199"; "" when the card isn't numbered (so unnumbered cards match each other). */
    fun printRun(serial: String): String = serial.substringAfter('/', "").trimStart('0')

    private fun String.norm() = trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Main border color of a card photo, or null when it's mostly silver, white or black
     * (base cards, plain refractors) or the photo is too unclear to call.
     *
     * Samples a ring around the edge of the card (where the colored border is), skipping the
     * middle where the player is, plus transparent, very dark and very bright pixels.
     */
    fun borderColor(photo: Bitmap): String? {
        val bmp = scaled(photo, 240)
        val w = bmp.width
        val h = bmp.height
        if (w < 20 || h < 20) return null
        // Ring from 3% to 18% in from each edge: covers the border even with some holder plastic around the card.
        val inX0 = (w * 0.03f).roundToInt(); val inX1 = (w * 0.18f).roundToInt()
        val inY0 = (h * 0.03f).roundToInt(); val inY1 = (h * 0.18f).roundToInt()

        val counts = HashMap<String, Int>()
        var sampled = 0
        var colorful = 0
        val hsv = FloatArray(3)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (y in inY0 until h - inY0) {
            for (x in inX0 until w - inX0) {
                val inRing = x < inX1 || x >= w - inX1 || y < inY1 || y >= h - inY1
                if (!inRing) continue
                val c = px[y * w + x]
                if (Color.alpha(c) < 200) continue
                Color.colorToHSV(c, hsv)
                val (hue, sat, value) = Triple(hsv[0], hsv[1], hsv[2])
                if (value < 0.18f) continue // backdrop / shadows
                sampled++
                if (sat < 0.32f || value > 0.97f && sat < 0.45f) continue // silver, white, glare
                colorful++
                val name = hueName(hue, sat, value)
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        if (sampled < 200) return null
        // Need a real share of colored pixels, and one color clearly ahead of the rest.
        if (colorful < sampled * 0.18f) return null
        val top = counts.maxByOrNull { it.value } ?: return null
        return if (top.value >= colorful * 0.40f) top.key else null
    }

    private fun hueName(h: Float, s: Float, v: Float): String = when {
        h < 15f || h >= 345f -> "Red"
        h < 38f -> if (v < 0.55f) "Bronze" else "Orange"
        h < 55f -> "Gold"
        h < 68f -> "Yellow"
        h < 160f -> "Green"
        h < 195f -> "Aqua"
        h < 255f -> "Blue"
        h < 300f -> "Purple"
        // Card "purple" borders are usually magenta; only a light, bright magenta reads as pink.
        else -> if (v >= 0.7f) "Pink" else "Purple"
    }

    private fun scaled(src: Bitmap, maxSide: Int): Bitmap {
        val s = maxSide.toFloat() / max(src.width, src.height)
        if (s >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true)
    }
}
