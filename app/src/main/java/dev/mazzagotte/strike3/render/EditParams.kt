package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything needed to redraw a post exactly as it was saved. */
data class EditParams(
    val mode: PhotoMode = PhotoMode.BLACK,
    val tag: String = "",
    val feather: Float = 100f,
    val enhance: Boolean = true,
    val sharpen: Boolean = true,
    /** Clockwise turn applied to the photo: 0, 90, 180 or 270 */
    val rotation: Int = 0,
    val brightness: Float = 1f,
    val lighten: Boolean = true,
    val reflect: Boolean = false,
    val placement: Placement = Placement(),
    /** Card badges shown on the post, e.g. AUTO, RELIC, RC, 1ST, 25/50 */
    val badges: List<String> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("mode", mode.name)
        put("tag", tag)
        put("feather", feather.toDouble())
        put("enhance", enhance)
        put("sharpen", sharpen)
        put("rotation", rotation)
        put("brightness", brightness.toDouble())
        put("lighten", lighten)
        put("reflect", reflect)
        put("scale", placement.scale.toDouble())
        put("dx", placement.dx.toDouble())
        put("dy", placement.dy.toDouble())
        put("badges", JSONArray(badges))
    }.toString()

    companion object {
        fun fromJson(s: String?): EditParams {
            if (s.isNullOrBlank()) return EditParams()
            return try {
                val o = JSONObject(s)
                EditParams(
                    mode = runCatching { PhotoMode.valueOf(o.optString("mode")) }.getOrDefault(PhotoMode.BLACK),
                    tag = o.optString("tag"),
                    feather = o.optDouble("feather", 100.0).toFloat(),
                    enhance = o.optBoolean("enhance", true),
                    sharpen = o.optBoolean("sharpen", false),
                    rotation = o.optInt("rotation", 0),
                    brightness = o.optDouble("brightness", 1.0).toFloat(),
                    lighten = o.optBoolean("lighten", true),
                    reflect = o.optBoolean("reflect", false),
                    placement = Placement(
                        scale = o.optDouble("scale", 1.0).toFloat(),
                        dx = o.optDouble("dx", 0.0).toFloat(),
                        dy = o.optDouble("dy", 0.0).toFloat(),
                    ),
                    badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                )
            } catch (_: Exception) {
                EditParams()
            }
        }
    }
}

/** App-private photo copies (not in the gallery) used for re-editing and exports. */
object SourceStore {
    private fun dir(context: Context) = File(context.filesDir, "sources").apply { mkdirs() }

    fun save(context: Context, bmp: Bitmap, name: String, png: Boolean = false): String {
        val file = File(dir(context), if (png) "$name.png" else "$name.jpg")
        file.outputStream().use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
        }
        return file.absolutePath
    }

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inMutable = true })
    }

    /** Smaller copy for thumbnails (decodes at roughly [maxSide] instead of full size). */
    fun loadScaled(path: String?, maxSide: Int): Bitmap? {
        if (path.isNullOrBlank() || !File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) File(path).delete()
    }
}
