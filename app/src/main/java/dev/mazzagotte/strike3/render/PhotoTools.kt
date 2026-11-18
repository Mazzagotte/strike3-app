package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    /** Brightness and (for photos shot on black) soft faded edges. */
    fun prepare(src: Bitmap, brightness: Float, featherPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        if (brightness != 1f) {
            p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })
        }
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
