package dev.mazzagotte.strike3.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mazzagotte.strike3.render.PhotoMode
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.Placement
import dev.mazzagotte.strike3.render.PostRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the post being made, from photo to finished image. */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val renderer = PostRenderer(app)

    var source by mutableStateOf<Bitmap?>(null)
        private set
    var originalUri: Uri? = null
        private set
    private var cutoutBitmap by mutableStateOf<Bitmap?>(null)

    var mode by mutableStateOf(PhotoMode.BLACK)
        private set
    var tag by mutableStateOf("")
        private set
    var customTag by mutableStateOf("")
    var feather by mutableFloatStateOf(90f)
    var brightness by mutableFloatStateOf(1f)
    var lighten by mutableStateOf(true)
    var reflect by mutableStateOf(false)
    var placement by mutableStateOf(Placement())

    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val tagText: String get() = customTag.trim().ifEmpty { tag }

    private var bgCache: Pair<String, Bitmap>? = null
    private var preparedCache: Pair<List<Any?>, Bitmap>? = null

    private data class Params(
        val photo: Bitmap?, val mode: PhotoMode, val tag: String, val feather: Float,
        val brightness: Float, val lighten: Boolean, val reflect: Boolean, val placement: Placement,
    )

    init {
        viewModelScope.launch {
            snapshotFlow { currentParams() }.collectLatest { p ->
                preview = withContext(Dispatchers.Default) { render(p) }
            }
        }
    }

    private fun currentParams() = Params(
        photo = if (mode == PhotoMode.CUTOUT) cutoutBitmap ?: source else source,
        mode = mode, tag = tagText, feather = feather, brightness = brightness,
        lighten = lighten, reflect = reflect, placement = placement,
    )

    private fun background(tag: String): Bitmap {
        bgCache?.let { if (it.first == tag) return it.second }
        return renderer.background(tag).also { bgCache = tag to it }
    }

    private fun prepared(p: Params): Bitmap? {
        val photo = p.photo ?: return null
        val featherPx = if (p.mode == PhotoMode.BLACK) p.feather * photo.width / 700f else 0f
        val key = listOf(System.identityHashCode(photo), p.mode, featherPx, p.brightness)
        preparedCache?.let { if (it.first == key) return it.second }
        return PhotoTools.prepare(photo, p.brightness, featherPx).also { preparedCache = key to it }
    }

    private fun render(p: Params): Bitmap =
        renderer.compose(background(p.tag), prepared(p), p.mode, p.lighten, p.reflect, p.placement)

    /** Full-quality finished post. */
    suspend fun renderFinal(): Bitmap {
        val p = currentParams()
        return withContext(Dispatchers.Default) { render(p) }
    }

    fun load(uri: Uri) {
        source = null
        cutoutBitmap = null
        originalUri = uri
        placement = Placement()
        mode = PhotoMode.BLACK
        reflect = false
        tag = ""
        customTag = ""
        preparedCache = null
        viewModelScope.launch {
            busy = "Loading photo…"
            try {
                val bmp = withContext(Dispatchers.IO) { PhotoTools.decode(getApplication<Application>(), uri) }
                source = bmp
                if (withContext(Dispatchers.Default) { PhotoTools.hasTransparency(bmp) }) {
                    // Already a cutout (e.g. Samsung "Save as image")
                    cutoutBitmap = withContext(Dispatchers.Default) { PhotoTools.cropToContent(bmp) }
                    mode = PhotoMode.CUTOUT
                    reflect = true
                }
            } catch (e: Exception) {
                message = "Couldn't open that photo: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun selectMode(m: PhotoMode) {
        if (m == mode) return
        if (m == PhotoMode.BLACK) {
            mode = m
            reflect = false
            placement = Placement()
            return
        }
        if (cutoutBitmap != null) {
            mode = m
            reflect = true
            placement = Placement()
            return
        }
        val src = source ?: return
        viewModelScope.launch {
            busy = "Cutting out the card…"
            try {
                cutoutBitmap = PhotoTools.cutout(src)
                mode = PhotoMode.CUTOUT
                reflect = true
                placement = Placement()
            } catch (e: Exception) {
                message = "Cutout isn't ready. Google may still be downloading it after install, so try again in a minute. (${e.message})"
            } finally {
                busy = null
            }
        }
    }

    fun selectTag(t: String) {
        tag = t
        customTag = ""
    }

    fun resetPosition() { placement = Placement() }
}
