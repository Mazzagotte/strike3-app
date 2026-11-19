package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.mazzagotte.strike3.data.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Re-draws a saved card's post in any format, from its saved photo and editor settings. */
class PostComposer(private val context: Context) {
    private val renderer = PostRenderer(context)

    class MissingPhoto : Exception("The original photo for this card isn't saved in the app.")

    /** The card photo the post was made from (saved copy, or the gallery original as a fallback). */
    private fun source(card: Card): Bitmap? =
        SourceStore.load(card.sourcePath)
            ?: card.originalUri?.let { runCatching { PhotoTools.decode(context, Uri.parse(it)) }.getOrNull() }

    /** [height]: PostRenderer.H for a feed post, 1920 for a Story / Reel cover. */
    suspend fun post(card: Card, height: Int): Bitmap = withContext(Dispatchers.Default) {
        val p = EditParams.fromJson(card.editParams)
        val found: Bitmap? = if (p.mode == PhotoMode.CUTOUT) {
            SourceStore.load(card.cutoutPath) ?: source(card)?.let { PhotoTools.cutout(it) }
        } else {
            source(card)
        }
        val photo = PhotoTools.rotate(found ?: throw MissingPhoto(), p.rotation)
        val featherPx = if (p.mode == PhotoMode.BLACK) PhotoTools.fadePx(p.feather, photo.width) else 0f
        val prepared = PhotoTools.prepare(photo, p.brightness, featherPx, p.enhance, p.sharpen)
        renderer.compose(renderer.background(p.tag, height, p.badges), prepared, p.mode, p.lighten, p.reflect, p.placement)
    }

    /** Carousel slide 2 (back + close-up). Null when neither photo has been added. */
    suspend fun slide2(card: Card): Bitmap? = withContext(Dispatchers.Default) {
        val back = SourceStore.load(card.backPath)?.let { blend(it) }
        val close = SourceStore.load(card.closeUpPath)?.let { blend(it) }
        if (back == null && close == null) null
        else renderer.slide2(renderer.background(""), back, close)
    }

    // Back / close-up photos: trim the background, then fade the thin margin and enhance
    private fun blend(b: Bitmap): Bitmap {
        val t = PhotoTools.autoTrim(b)
        return PhotoTools.prepare(t, 1f, PhotoTools.fadePx(100f, t.width), enhance = true, sharpen = true)
    }

    /** White-background listing photo. Uses the cutout, making one if needed. */
    suspend fun listing(card: Card): Bitmap = withContext(Dispatchers.Default) {
        val cut = SourceStore.load(card.cutoutPath)
            ?: source(card)?.let { src -> runCatching { PhotoTools.cutout(src) }.getOrNull() }
        val turn = EditParams.fromJson(card.editParams).rotation
        if (cut != null) {
            renderer.listing(PhotoTools.rotate(cut, turn), cutout = true)
        } else {
            val src = source(card) ?: throw MissingPhoto()
            renderer.listing(PhotoTools.rotate(src, turn), cutout = false)
        }
    }
}
