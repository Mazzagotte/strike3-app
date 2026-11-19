package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import kotlin.math.min

enum class PhotoMode { BLACK, CUTOUT }

/** Where the card photo sits: size multiplier and drag offset in post pixels. */
data class Placement(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f)

/**
 * Draws the Strike III post: 1080 x 1350, dark spotlight background, faint III,
 * floor, optional tag pill, and the fanned-hand + STRIKE III CARDS name at the bottom.
 */
class PostRenderer(context: Context) {
    companion object {
        const val W = 1080
        const val H = 1350
        const val FLOOR = 1015f

        private val SIL = intArrayOf(0xFFE6E9ED.toInt(), 0xFFBFC4CB.toInt(), 0xFFF3F5F7.toInt(), 0xFFA4AAB3.toInt(), 0xFFCFD3D9.toInt())
        private val GLD = intArrayOf(0xFFF1D58A.toInt(), 0xFFC39434.toInt(), 0xFFF4DC98.toInt(), 0xFFA27522.toInt(), 0xFFCFA64C.toInt())
        private val STOPS = floatArrayOf(0f, .28f, .47f, .7f, 1f)
    }

    // Drop BebasNeue / Oswald .ttf files into res/font as bebas_neue.ttf and oswald_medium.ttf
    // to use the brand fonts; otherwise the phone's condensed font is used.
    private val display: Typeface = loadFont(context, "bebas_neue") ?: Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tagFont: Typeface = loadFont(context, "oswald_medium") ?: Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private fun loadFont(context: Context, name: String): Typeface? {
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        return if (id != 0) ResourcesCompat.getFont(context, id) else null
    }

    private fun grad(x0: Float, y0: Float, x1: Float, y1: Float, gold: Boolean) =
        LinearGradient(x0, y0, x1, y1, if (gold) GLD else SIL, STOPS, Shader.TileMode.CLAMP)

    /**
     * Everything except the card photo. Cache this per tag text.
     * [height] 1350 = feed post (4:5), 1920 = Story / Reel cover (9:16). The 4:5 layout is centered
     * vertically on taller canvases so it sits clear of Instagram's Story controls.
     */
    fun background(tag: String, height: Int = H, badges: List<String> = emptyList()): Bitmap {
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val offY = (height - H) / 2f
        c.drawColor(0xFF0B0C0E.toInt())

        // Spotlight behind the card, and the big faint III
        c.save()
        c.translate(0f, offY)
        val spot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                540f, 594f, 760f,
                intArrayOf(0x1CFFFFFF, 0x08FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.save()
        c.scale(620f / 760f, 1f, 540f, 594f)
        c.drawRect(-900f, -400f, 2000f, 1800f, spot)
        c.restore()
        drawBars(c, -160f, -150f, 980f, 0.07f)
        c.restore()

        // Floor runs to the bottom of the canvas
        val floorTop = FLOOR + offY
        val floor = Paint().apply {
            shader = LinearGradient(0f, floorTop, 0f, height.toFloat(), 0xFF17191C.toInt(), 0xFF08090A.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, floorTop, W.toFloat(), height.toFloat(), floor)

        c.save()
        c.translate(0f, offY)
        val edge = Paint().apply {
            shader = LinearGradient(
                140f, 0f, 940f, 0f,
                intArrayOf(0x00FFFFFF, 0x29FFFFFF, 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(140f, FLOOR - 1f, 940f, FLOOR + 1f, edge)
        drawTag(c, tag.trim().uppercase())
        drawBadges(c, badges)
        drawWordmark(c)
        c.restore()
        return bmp
    }

    /** Final post: background + card photo (+ reflection). */
    fun compose(
        background: Bitmap,
        photo: Bitmap?,
        mode: PhotoMode,
        lighten: Boolean,
        reflect: Boolean,
        placement: Placement,
    ): Bitmap {
        val out = background.copy(Bitmap.Config.ARGB_8888, true)
        if (photo == null) return out
        val c = Canvas(out)
        val r = photoRect(photo.width, photo.height, mode, placement)
        r.offset(0f, (background.height - H) / 2f)

        if (reflect) {
            val rh = min(r.height() * 0.32f, 260f)
            val bounds = RectF(r.left - 40f, r.bottom, r.right + 40f, r.bottom + rh)
            val layer = c.saveLayer(bounds, null)
            c.save()
            c.scale(1f, -1f, 0f, r.bottom)
            c.drawBitmap(photo, null, r, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 56 })
            c.restore()
            val mask = Paint().apply {
                shader = LinearGradient(0f, r.bottom, 0f, r.bottom + rh, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            c.drawRect(bounds, mask)
            c.restoreToCount(layer)
        }

        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (mode == PhotoMode.BLACK && lighten) p.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        c.drawBitmap(photo, null, r, p)
        return out
    }

    /** Carousel slide 2: card back and serial close-up side by side, shot on black. */
    fun slide2(background: Bitmap, back: Bitmap?, closeUp: Bitmap?): Bitmap {
        val out = background.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val offY = (background.height - H) / 2f
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        }
        val boxes = listOfNotNull(back, closeUp)
        if (boxes.size == 1) {
            c.drawBitmap(boxes[0], null, fit(boxes[0], RectF(160f, 190f + offY, 920f, 1000f + offY)), p)
        } else if (boxes.size == 2) {
            c.drawBitmap(boxes[0], null, fit(boxes[0], RectF(60f, 230f + offY, 525f, 990f + offY)), p)
            c.drawBitmap(boxes[1], null, fit(boxes[1], RectF(555f, 230f + offY, 1020f, 990f + offY)), p)
        }
        return out
    }

    /** Plain white 1600 x 1600 photo for selling or trading listings. */
    fun listing(photo: Bitmap, cutout: Boolean): Bitmap {
        val size = 1600
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFFFFFFFF.toInt())
        val r = fit(photo, RectF(160f, 90f, 1440f, 1450f))
        if (cutout) {
            // Soft contact shadow under the card
            val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x33000000, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
            c.save()
            c.translate(r.centerX(), r.bottom)
            c.scale(r.width() * 0.6f, 34f)
            c.drawCircle(0f, 0f, 1f, sh)
            c.restore()
        }
        c.drawBitmap(photo, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display
            textSize = 30f
            letterSpacing = 0.35f
            color = 0xFFA0A4AA.toInt()
            textAlign = Paint.Align.CENTER
        }
        c.drawText("STRIKE III CARDS", size / 2f, 1545f, tp)
        return out
    }

    /** Largest rect with the bitmap's shape that fits inside [box], centered. */
    private fun fit(bmp: Bitmap, box: RectF): RectF {
        val s = min(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * s; val h = bmp.height * s
        val x = box.centerX() - w / 2f; val y = box.centerY() - h / 2f
        return RectF(x, y, x + w, y + h)
    }

    /** Cutouts stand on the floor; photos shot on black sit centered in the spotlight. */
    fun photoRect(pw: Int, ph: Int, mode: PhotoMode, pl: Placement): RectF {
        val cut = mode == PhotoMode.CUTOUT
        val bw = if (cut) 600f else 780f
        val bh = if (cut) 860f else 1000f
        val s = min(bw / pw, bh / ph) * pl.scale
        val w = pw * s
        val h = ph * s
        val x = 540f - w / 2f + pl.dx
        val y = (if (cut) FLOOR + 5f - h else 580f - h / 2f) + pl.dy
        return RectF(x, y, x + w, y + h)
    }

    // ---- brand pieces ----

    private fun drawBars(c: Canvas, x: Float, y: Float, w: Float, alpha: Float) {
        val k = w / 400f
        c.save()
        c.translate(x, y)
        c.scale(k, k)
        val bw = 72f; val bh = 260f; val sl = 56f; val gap = 30f
        for (i in 0 until 3) {
            val bx = 20f + i * (bw + gap)
            val path = Path().apply {
                moveTo(bx + sl, 20f); lineTo(bx + sl + bw, 20f); lineTo(bx + bw, 20f + bh); lineTo(bx, 20f + bh); close()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = grad(bx, 20f, bx + bw + sl, 20f + bh * .65f, i == 2)
                this.alpha = (alpha * 255).toInt()
            }
            c.drawPath(path, paint)
        }
        c.restore()
    }

    /** Fanned hand of three cards, gold card with a backwards K. Drawn in a size x size box. */
    fun drawFan(c: Canvas, x: Float, y: Float, size: Float) {
        val k = size / 1000f
        c.save()
        c.translate(x, y)
        c.scale(k, k)
        c.translate(0f, -20f)
        val w = 230f; val h = 322f
        listOf(-20f to false, 0f to false, 20f to true).forEach { (deg, gold) ->
            c.save()
            c.rotate(deg, 500f, 880f)
            c.translate(385f, 290f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = grad(0f, 0f, w, h * .65f, gold)
                setShadowLayer(18f, 0f, 8f, 0x8C000000.toInt())
            }
            c.drawRoundRect(0f, 0f, w, h, 9f, 9f, fill)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = if (gold) 0x595A3A08 else 0x381E242E
            }
            c.drawRoundRect(11f, 11f, w - 11f, h - 11f, 4f, 4f, line)
            if (gold) {
                val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tagFont
                    textSize = 200f
                    textAlign = Paint.Align.CENTER
                }
                c.save()
                c.translate(w / 2f, h / 2f + 70f)
                c.scale(-1f, 1f)
                kp.color = 0x99FFF4CD.toInt()
                c.drawText("K", 0f, 1.5f, kp)
                kp.color = 0xFF5E410C.toInt()
                c.drawText("K", 0f, 0f, kp)
                c.restore()
            }
            c.restore()
        }
        c.restore()
    }

    private fun drawWordmark(c: Canvas) {
        val y = 1222f; val mark = 86f; val gap = 18f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display
            textSize = 50f
            letterSpacing = 2f / 50f
        }
        val wS = tp.measureText("STRIKE")
        val wII = tp.measureText("II")
        val wI = tp.measureText("I")
        val total = mark + gap + wS + 12f + wII + wI
        val x0 = (W - total) / 2f
        drawFan(c, x0, y, mark)

        val tx = x0 + mark + gap
        val base = y + 48f
        tp.color = 0xFFE9ECEF.toInt()
        c.drawText("STRIKE", tx, base, tp)
        val ix = tx + wS + 12f
        tp.color = 0xFFFFFFFF.toInt()
        tp.shader = grad(ix, base - 44f, ix + wII, base, false)
        c.drawText("II", ix, base, tp)
        tp.shader = grad(ix + wII, base - 44f, ix + wII + wI, base, true)
        c.drawText("I", ix + wII, base, tp)
        tp.shader = null

        val cards = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display
            textSize = 20f
            letterSpacing = 11f / 20f
            color = 0xFFC9A24A.toInt()
        }
        c.drawText("CARDS", tx, base + 28f, cards)
    }

    /**
     * Card badges along the top-left: AUTO and the serial number in gold, RELIC / RC / 1ST in silver.
     */
    private fun drawBadges(c: Canvas, badges: List<String>) {
        var x = 90f
        val y = 84f
        val ph = 44f
        badges.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.forEach { text ->
            val gold = text == "AUTO" || text.contains('/')
            val accent = if (gold) 0xFFE2C477.toInt() else 0xFFD3D8DE.toInt()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tagFont
                textSize = 22f
                letterSpacing = 3f / 22f
                color = accent
            }
            val pw = tp.measureText(text) + 36f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x990A0B0D.toInt() }
            c.drawRoundRect(x, y, x + pw, y + ph, 8f, 8f, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f
                color = if (gold) 0xFFC9A24A.toInt() else 0xFF9AA1AB.toInt()
            }
            c.drawRoundRect(x, y, x + pw, y + ph, 8f, 8f, stroke)
            val fm = tp.fontMetrics
            c.drawText(text, x + 18f, y + ph / 2f - (fm.ascent + fm.descent) / 2f, tp)
            x += pw + 12f
        }
    }

    private fun drawTag(c: Canvas, text: String) {
        if (text.isEmpty()) return
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tagFont
            textSize = 24f
            letterSpacing = 5f / 24f
            color = 0xFFE2C477.toInt()
        }
        val tw = tp.measureText(text)
        val pw = tw + 44f; val ph = 50f
        val x = W - 90f - pw; val y = 80f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x990A0B0D.toInt() }
        c.drawRoundRect(x, y, x + pw, y + ph, ph / 2, ph / 2, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFC9A24A.toInt()
        }
        c.drawRoundRect(x, y, x + pw, y + ph, ph / 2, ph / 2, stroke)
        val fm = tp.fontMetrics
        c.drawText(text, x + 22f, y + ph / 2f - (fm.ascent + fm.descent) / 2f, tp)
    }
}
