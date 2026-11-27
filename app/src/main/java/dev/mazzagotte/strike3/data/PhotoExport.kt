package dev.mazzagotte.strike3.data

import android.graphics.Bitmap
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.SourceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A zip of every card's front and back photos plus their details (cards.json), to hand to Claude
 * for testing and improving the card reader on real photos.
 *
 *   strike3-photos/README.txt
 *   strike3-photos/cards.json          details for each card (what the photos should read as)
 *   strike3-photos/photos/12-front.jpg front, turned upright
 *   strike3-photos/photos/12-back.jpg  back
 *
 * Photos are resized to at most [MAX_PX] on the long side: plenty for reading the small print,
 * without making the zip enormous.
 */
object PhotoExport {
    private const val MAX_PX = 2000
    private const val ROOT = "strike3-photos/"

    data class Result(val cards: Int, val photos: Int)

    fun write(out: OutputStream, cards: List<Card>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        var photos = 0
        val list = JSONArray()
        ZipOutputStream(out.buffered(1 shl 16)).use { zip ->
            zip.setLevel(1) // JPEGs barely compress; keep it quick
            fun putPhoto(name: String, bmp: Bitmap?): String? {
                bmp ?: return null
                zip.putNextEntry(ZipEntry(ROOT + "photos/" + name))
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, zip)
                zip.closeEntry()
                photos++
                return "photos/$name"
            }
            cards.forEachIndexed { i, c ->
                val turn = EditParams.fromJson(c.editParams).rotation
                val front = runCatching { SourceStore.loadScaled(c.sourcePath, MAX_PX)?.let { PhotoTools.rotate(scale(it), turn) } }.getOrNull()
                val back = runCatching { SourceStore.loadScaled(c.backPath, MAX_PX)?.let { scale(it) } }.getOrNull()
                val f = putPhoto("${c.id}-front.jpg", front)
                val b = putPhoto("${c.id}-back.jpg", back)
                list.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("player", c.player); put("year", c.year); put("set", c.setName)
                        put("cardNumber", c.cardNumber); put("parallel", c.parallel); put("serial", c.serial)
                        put("team", c.team); put("auto", c.isAuto); put("rookie", c.isRookie); put("relic", c.isPatch)
                        put("notes", c.notes)
                        put("unsure", JSONArray(c.unsure.sorted()))
                        put("front", f ?: JSONObject.NULL); put("back", b ?: JSONObject.NULL)
                    },
                )
                onProgress(i + 1, cards.size)
            }
            zip.putNextEntry(ZipEntry(ROOT + "cards.json"))
            zip.write(list.toString(1).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ROOT + "README.txt"))
            zip.write(
                ("Strike III card photos for improving the card reader.\n" +
                    "cards.json lists each card's details as saved in the app (\"unsure\" = fields the reader guessed and nobody has checked).\n" +
                    "photos/<id>-front.jpg and <id>-back.jpg are the photos those details belong to.\n").toByteArray(),
            )
            zip.closeEntry()
        }
        return Result(cards.size, photos)
    }

    private fun scale(src: Bitmap): Bitmap {
        val s = MAX_PX.toFloat() / maxOf(src.width, src.height)
        return if (s >= 1f) src else Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
    }
}
