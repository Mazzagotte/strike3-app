package dev.mazzagotte.strike3.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/** Everything needed to redraw a post exactly as it was saved. */
data class EditParams(
    val mode: PhotoMode = PhotoMode.BLACK,
    val tag: String = "",
    val feather: Float = 90f,
    val brightness: Float = 1f,
    val lighten: Boolean = true,
    val reflect: Boolean = false,
    val placement: Placement = Placement(),
) {
    fun toJson(): String = JSONObject().apply {
        put("mode", mode.name)
        put("tag", tag)
        put("feather", feather.toDouble())
        put("brightness", brightness.toDouble())
        put("lighten", lighten)
        put("reflect", reflect)
        put("scale", placement.scale.toDouble())
        put("dx", placement.dx.toDouble())
        put("dy", placement.dy.toDouble())
    }.toString()

    companion object {
        fun fromJson(s: String?): EditParams {
            if (s.isNullOrBlank()) return EditParams()
            return try {
                val o = JSONObject(s)
                EditParams(
                    mode = runCatching { PhotoMode.valueOf(o.optString("mode")) }.getOrDefault(PhotoMode.BLACK),
                    tag = o.optString("tag"),
                    feather = o.optDouble("feather", 90.0).toFloat(),
                    brightness = o.optDouble("brightness", 1.0).toFloat(),
                    lighten = o.optBoolean("lighten", true),
                    reflect = o.optBoolean("reflect", false),
                    placement = Placement(
                        scale = o.optDouble("scale", 1.0).toFloat(),
                        dx = o.optDouble("dx", 0.0).toFloat(),
                        dy = o.optDouble("dy", 0.0).toFloat(),
                    ),
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

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) File(path).delete()
    }
}
