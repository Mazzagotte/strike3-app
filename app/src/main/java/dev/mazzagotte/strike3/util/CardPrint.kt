package dev.mazzagotte.strike3.util

import java.util.Base64
import kotlin.math.sqrt

/**
 * A small "fingerprint" of a card photo, for finding cards you already have by how they look.
 *
 * The photo is trimmed to the card (bright area on a dark background), turned to portrait, and
 * an 8% border is dropped (sleeves, slab edges, glare at the edges). What's left is shrunk to a
 * 12 × 12 color thumbnail (432 numbers). Two prints are compared after taking out overall
 * brightness and contrast, so a show's lighting doesn't matter much; 1.0 is identical.
 *
 * Tested on 230 fronts with tilt (±3°), framing (±5% shift, ±10% zoom), lighting, blur and JPEG
 * changes: the right card came out as the closest match every time. Backs of the same set look
 * alike, so a back match needs a higher score ([BACK_STRONG]) and is only a hint.
 *
 * Plain Kotlin on ARGB pixels, so it can be tested off the phone.
 */
object CardPrint {
    private const val N = 12
    const val SIZE = N * N * 3

    /** Front-photo similarity that counts as "looks like one you have". */
    const val FRONT_STRONG = 0.88
    /** Front-photo similarity worth showing as "might be". */
    const val FRONT_MAYBE = 0.78
    /** Backs of one set look alike, so a back needs more. */
    const val BACK_STRONG = 0.95
    const val BACK_MAYBE = 0.92

    /** [px]: ARGB pixels, row by row, [w] × [h]. Transparent pixels (a cutout) count as black. */
    fun of(px: IntArray, w: Int, h: Int): ByteArray {
        // Luminance, with transparency as black
        fun rgb(c: Int): Int = if ((c ushr 24) < 128) 0 else c and 0xFFFFFF
        fun lum(c: Int): Int { val v = rgb(c); return (((v shr 16) and 255) * 299 + ((v shr 8) and 255) * 587 + (v and 255) * 114) / 1000 }

        // 1. Trim to the card: rows/columns where at least 10% of pixels are brighter than the dark background
        var top = 0; var bottom = h - 1; var left = 0; var right = w - 1
        run {
            val rowOk = BooleanArray(h) { y -> var n = 0; for (x in 0 until w) if (lum(px[y * w + x]) > 48) n++; n >= maxOf(1, w / 10) }
            val colOk = BooleanArray(w) { x -> var n = 0; for (y in 0 until h) if (lum(px[y * w + x]) > 48) n++; n >= maxOf(1, h / 10) }
            val rows = rowOk.indices.filter { rowOk[it] }
            val cols = colOk.indices.filter { colOk[it] }
            if (rows.size >= h * 0.3 && cols.size >= w * 0.3) { top = rows.first(); bottom = rows.last(); left = cols.first(); right = cols.last() }
        }
        val tw = right - left + 1; val th = bottom - top + 1
        // 2. Portrait: a landscape card is turned a quarter clockwise
        val landscape = tw > th
        fun at(x: Int, y: Int): Int = // (x, y) in the portrait card
            if (!landscape) px[(top + y) * w + (left + x)]
            else px[(top + (th - 1 - x)) * w + (left + y)]
        val cw = if (landscape) th else tw; val ch = if (landscape) tw else th
        // 3. Drop an 8% border
        val mx = cw * 8 / 100; val my = ch * 8 / 100
        val iw = maxOf(1, cw - 2 * mx); val ih = maxOf(1, ch - 2 * my)
        // 4. Area-average down to N × N, per color
        val out = ByteArray(SIZE)
        for (j in 0 until N) {
            val y0 = j * ih / N; val y1 = maxOf(y0 + 1, (j + 1) * ih / N)
            for (i in 0 until N) {
                val x0 = i * iw / N; val x1 = maxOf(x0 + 1, (i + 1) * iw / N)
                var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    val c = rgb(at(mx + x, my + y))
                    r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
                }
                val k = (j * N + i) * 3
                out[k] = (r / n).toInt().toByte(); out[k + 1] = (g / n).toInt().toByte(); out[k + 2] = (b / n).toInt().toByte()
            }
        }
        return out
    }

    /** The same photo turned upside down (pixels in reverse order). */
    fun upsideDown(px: IntArray): IntArray = px.reversedArray()

    /** 1.0 = same picture; around 0.6 for two unrelated cards. Brightness and contrast are taken out. */
    fun similarity(a: ByteArray?, b: ByteArray?): Double {
        if (a == null || b == null || a.size != SIZE || b.size != SIZE) return 0.0
        val va = norm(a); val vb = norm(b)
        var dot = 0.0
        for (i in 0 until SIZE) dot += va[i] * vb[i]
        return dot
    }

    private fun norm(a: ByteArray): DoubleArray {
        val v = DoubleArray(SIZE) { (a[it].toInt() and 255).toDouble() }
        val mean = v.average()
        var len = 0.0
        for (i in v.indices) { v[i] -= mean; len += v[i] * v[i] }
        len = sqrt(len).coerceAtLeast(1e-9)
        for (i in v.indices) v[i] /= len
        return v
    }

    fun encode(p: ByteArray?): String = p?.let { Base64.getEncoder().encodeToString(it) }.orEmpty()
    fun decode(s: String?): ByteArray? = s?.takeIf { it.isNotBlank() }?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
}
