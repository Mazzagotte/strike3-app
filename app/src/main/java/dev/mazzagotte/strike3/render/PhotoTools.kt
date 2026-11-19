package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The on-screen guide frame at capture time, in preview-view pixels. */
data class FrameCrop(
    val viewW: Int,
    val viewH: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object PhotoTools {

    /** Decodes a photo (EXIF rotation handled), shrunk so the long side is at most [maxSide]. */
    fun decode(context: Context, uri: Uri, maxSide: Int = 2000): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val s = min(1f, maxSide.toFloat() / max(w, h))
            if (s < 1f) decoder.setTargetSize((w * s).roundToInt(), (h * s).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    }

    /** True if a good share of the image is transparent (a cutout PNG). */
    fun hasTransparency(bmp: Bitmap): Boolean {
        if (!bmp.hasAlpha()) return false
        val step = max(1, max(bmp.width, bmp.height) / 120)
        var clear = 0; var total = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                total++
                if ((bmp.getPixel(x, y) ushr 24) < 20) clear++
                x += step
            }
            y += step
        }
        return total > 0 && clear > total * 0.04
    }

    /**
     * Removes the background with Google's on-device model. The first run after
     * installing may fail while Google Play services downloads the model.
     */
    suspend fun cutout(bmp: Bitmap): Bitmap {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val client = SubjectSegmentation.getClient(options)
        try {
            val result = client.process(InputImage.fromBitmap(bmp, 0)).await()
            val fg = result.foregroundBitmap ?: error("No card found in the photo")
            return cropToContent(fg)
        } finally {
            client.close()
        }
    }

    /** Trims transparent edges so the card fills its box. */
    fun cropToContent(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 16) {
                if (x < l) l = x; if (x > r) r = x
                if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r < l || b < t) return bmp
        return Bitmap.createBitmap(bmp, l, t, r - l + 1, b - t + 1)
    }

    /**
     * Crops a camera photo to the gold guide frame shown on screen (plus a small margin).
     * The preview fills the screen (center-crop), so screen coordinates are mapped back
     * onto the photo the same way.
     */
    fun cropToFrame(bmp: Bitmap, f: FrameCrop): Bitmap {
        if (f.viewW <= 0 || f.viewH <= 0) return bmp
        val scale = max(f.viewW.toFloat() / bmp.width, f.viewH.toFloat() / bmp.height)
        val offX = (f.viewW - bmp.width * scale) / 2f
        val offY = (f.viewH - bmp.height * scale) / 2f
        val mx = (f.right - f.left) * 0.06f
        val my = (f.bottom - f.top) * 0.05f
        val l = (((f.left - mx) - offX) / scale).roundToInt().coerceIn(0, bmp.width - 1)
        val t = (((f.top - my) - offY) / scale).roundToInt().coerceIn(0, bmp.height - 1)
        val r = (((f.right + mx) - offX) / scale).roundToInt().coerceIn(l + 1, bmp.width)
        val b = (((f.bottom + my) - offY) / scale).roundToInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, r - l, b - t)
    }

    /**
     * Trims plain background from around the card (for gallery photos). Looks at the photo's
     * border to learn the background, finds where the card is, and keeps it with a small margin.
     * Returns the photo unchanged if it can't find a clear card.
     */
    fun autoTrim(bmp: Bitmap): Bitmap {
        val k = 256f / max(bmp.width, bmp.height)
        val sw = max(8, (bmp.width * k).roundToInt())
        val sh = max(8, (bmp.height * k).roundToInt())
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)

        fun r(c: Int) = (c shr 16) and 0xFF
        fun g(c: Int) = (c shr 8) and 0xFF
        fun b(c: Int) = c and 0xFF

        // Background color = median of the outer ring
        val ring = max(2, min(sw, sh) / 30)
        val border = ArrayList<Int>()
        for (y in 0 until sh) for (x in 0 until sw) {
            if (x < ring || y < ring || x >= sw - ring || y >= sh - ring) border += px[y * sw + x]
        }
        val br = border.map { r(it) }.sorted()[border.size / 2]
        val bg = border.map { g(it) }.sorted()[border.size / 2]
        val bb = border.map { b(it) }.sorted()[border.size / 2]

        val colCount = IntArray(sw)
        val rowCount = IntArray(sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val c = px[y * sw + x]
            val d = abs(r(c) - br) + abs(g(c) - bg) + abs(b(c) - bb)
            if (d > 75) { colCount[x]++; rowCount[y]++ }
        }
        val colMin = max(2, (sh * 0.06f).roundToInt())
        val rowMin = max(2, (sw * 0.06f).roundToInt())
        val x0 = colCount.indexOfFirst { it >= colMin }
        val x1 = colCount.indexOfLast { it >= colMin }
        val y0 = rowCount.indexOfFirst { it >= rowMin }
        val y1 = rowCount.indexOfLast { it >= rowMin }
        if (x0 < 0 || y0 < 0 || x1 <= x0 || y1 <= y0) return bmp

        val boxArea = (x1 - x0 + 1f) * (y1 - y0 + 1f)
        val area = sw.toFloat() * sh
        if (boxArea > area * 0.9f || boxArea < area * 0.04f) return bmp

        val mx = (x1 - x0) * 0.05f
        val my = (y1 - y0) * 0.04f
        val l = ((x0 - mx) / k).roundToInt().coerceIn(0, bmp.width - 1)
        val t = ((y0 - my) / k).roundToInt().coerceIn(0, bmp.height - 1)
        val rr = ((x1 + 1 + mx) / k).roundToInt().coerceIn(l + 1, bmp.width)
        val bbm = ((y1 + 1 + my) / k).roundToInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, rr - l, bbm - t)
    }

    /**
     * Edge fade in pixels. [amount] is the Edge fade slider (100 = default). At 100 the fade is 4% of the
     * photo's width: about the margin the crop/trim leaves around the holder, so the card itself never fades.
     */
    fun fadePx(amount: Float, width: Int): Float = (amount / 100f) * 0.04f * width

    /**
     * Auto enhance: stretches the darkest and brightest 1% to full black and white (capped so it never
     * overdoes it), then adds a little contrast and color so the card pops on the dark background.
     */
    private fun enhanceMatrix(src: Bitmap): ColorMatrix {
        val k = 200f / max(src.width, src.height)
        val small = Bitmap.createScaledBitmap(src, max(4, (src.width * k).roundToInt()), max(4, (src.height * k).roundToInt()), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val hist = IntArray(256)
        var n = 0
        for (c in px) {
            if ((c ushr 24) < 128) continue // ignore transparent (cutout) pixels
            val l = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)).roundToInt()
            hist[l.coerceIn(0, 255)]++
            n++
        }
        val m = ColorMatrix()
        if (n > 0) {
            var acc = 0; var lo = 0
            while (lo < 255 && acc + hist[lo] < n * 0.01f) { acc += hist[lo]; lo++ }
            acc = 0; var hi = 255
            while (hi > 0 && acc + hist[hi] < n * 0.01f) { acc += hist[hi]; hi-- }
            val low = lo.coerceAtMost(40).toFloat()
            val high = hi.coerceAtLeast(190).toFloat()
            val scale = (255f / max(1f, high - low)).coerceAtMost(1.6f)
            m.set(
                floatArrayOf(
                    scale, 0f, 0f, 0f, -low * scale,
                    0f, scale, 0f, 0f, -low * scale,
                    0f, 0f, scale, 0f, -low * scale,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        // A touch more contrast (around mid-grey) and color
        val contrast = 1.08f
        val t = 128f * (1f - contrast)
        m.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        m.postConcat(ColorMatrix().apply { setSaturation(1.15f) })
        return m
    }

    /** Turns the photo clockwise by 0, 90, 180 or 270 degrees. */
    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return bmp
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /**
     * Light unsharp mask: adds back the difference between the photo and a slightly blurred copy,
     * which crisps up printed text, jersey stripes and card edges. Tiny differences (sensor noise)
     * are left alone so smooth areas and glare don't turn gritty. Transparency is kept.
     */
    fun sharpen(src: Bitmap, amount: Float = 0.55f, radius: Int = 2, threshold: Int = 4): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val blur = boxBlur(boxBlur(px, w, h, radius, horizontal = true), w, h, radius, horizontal = false)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]; val b = blur[i]
            val a = c ushr 24
            fun ch(shift: Int): Int {
                val o = (c shr shift) and 0xFF
                val d = o - ((b shr shift) and 0xFF)
                return if (abs(d) < threshold) o else (o + amount * d).roundToInt().coerceIn(0, 255)
            }
            out[i] = (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** One pass of a box blur (run horizontally, then vertically). */
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun idx(i: Int): Int {
                val j = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + j else j * w + line
            }
            for (i in -r..r) { val c = src[idx(i)]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            val n = 2 * r + 1
            for (i in 0 until len) {
                out[idx(i)] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = src[idx(i + r + 1)]; val rem = src[idx(i - r)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
        return out
    }

    /** Sharpen, auto enhance, brightness and (for photos shot on black) soft faded edges. */
    fun prepare(src0: Bitmap, brightness: Float, featherPx: Float, enhance: Boolean = false, sharpen: Boolean = false): Bitmap {
        val src = if (sharpen) sharpen(src0) else src0
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val matrix = if (enhance) enhanceMatrix(src) else ColorMatrix()
        if (brightness != 1f) matrix.postConcat(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })
        if (enhance || brightness != 1f) p.colorFilter = ColorMatrixColorFilter(matrix)
        c.drawBitmap(src, 0f, 0f, p)

        if (featherPx > 0f) {
            val w = out.width.toFloat(); val h = out.height.toFloat()
            val f = min(featherPx, min(w, h) / 2f)
            val erase = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            fun edge(x0: Float, y0: Float, x1: Float, y1: Float, l: Float, t: Float, r: Float, b: Float) {
                erase.shader = LinearGradient(x0, y0, x1, y1, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                c.drawRect(l, t, r, b, erase)
            }
            edge(0f, 0f, f, 0f, 0f, 0f, f, h)
            edge(w, 0f, w - f, 0f, w - f, 0f, w, h)
            edge(0f, 0f, 0f, f, 0f, 0f, w, f)
            edge(0f, h, 0f, h - f, 0f, h - f, w, h)
        }
        return out
    }
}
