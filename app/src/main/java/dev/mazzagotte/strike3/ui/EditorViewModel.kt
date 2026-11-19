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
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.render.FrameCrop
import dev.mazzagotte.strike3.render.PhotoMode
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.Placement
import dev.mazzagotte.strike3.render.PostRenderer
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.util.Media
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
    var cutoutBitmap by mutableStateOf<Bitmap?>(null)
        private set

    /** Set when re-editing a saved card's post instead of making a new one. */
    var editingCard by mutableStateOf<Card?>(null)
        private set

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

    /** Badge toggles (AUTO, RELIC, RC, 1ST) and an optional serial number like 25/50 */
    var badges by mutableStateOf(setOf<String>())
    var serialBadge by mutableStateOf("")

    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val tagText: String get() = customTag.trim().ifEmpty { tag }

    /** Badges in display order, serial last. */
    val badgeList: List<String>
        get() = BADGE_ORDER.filter { it in badges } + listOfNotNull(serialBadge.trim().ifEmpty { null })

    fun toggleBadge(b: String) {
        badges = if (b in badges) badges - b else badges + b
    }

    private var bgCache: Pair<String, Bitmap>? = null
    private var preparedCache: Pair<List<Any?>, Bitmap>? = null

    private data class Params(
        val photo: Bitmap?, val mode: PhotoMode, val tag: String, val feather: Float,
        val brightness: Float, val lighten: Boolean, val reflect: Boolean, val placement: Placement,
        val badges: List<String>,
    )

    companion object {
        val BADGE_ORDER = listOf("AUTO", "RELIC", "RC", "1ST")
    }

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
        lighten = lighten, reflect = reflect, placement = placement, badges = badgeList,
    )

    private fun background(tag: String, badges: List<String>): Bitmap {
        val key = tag + "|" + badges.joinToString(",")
        bgCache?.let { if (it.first == key) return it.second }
        return renderer.background(tag, PostRenderer.H, badges).also { bgCache = key to it }
    }

    private fun prepared(p: Params): Bitmap? {
        val photo = p.photo ?: return null
        val featherPx = if (p.mode == PhotoMode.BLACK) p.feather * photo.width / 700f else 0f
        val key = listOf(System.identityHashCode(photo), p.mode, featherPx, p.brightness)
        preparedCache?.let { if (it.first == key) return it.second }
        return PhotoTools.prepare(photo, p.brightness, featherPx).also { preparedCache = key to it }
    }

    private fun render(p: Params): Bitmap =
        renderer.compose(background(p.tag, p.badges), prepared(p), p.mode, p.lighten, p.reflect, p.placement)

    /** Full-quality finished post. */
    suspend fun renderFinal(): Bitmap {
        val p = currentParams()
        return withContext(Dispatchers.Default) { render(p) }
    }

    /** [frame]: set for camera photos (crop to the guide); gallery photos are auto-trimmed. */
    fun load(uri: Uri, frame: FrameCrop? = null) {
        editingCard = null
        badges = emptySet()
        serialBadge = ""
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
                val decoded = withContext(Dispatchers.IO) { PhotoTools.decode(getApplication<Application>(), uri) }
                val transparent = withContext(Dispatchers.Default) { PhotoTools.hasTransparency(decoded) }
                val bmp = withContext(Dispatchers.Default) {
                    when {
                        transparent -> decoded
                        frame != null -> PhotoTools.cropToFrame(decoded, frame)
                        else -> PhotoTools.autoTrim(decoded)
                    }
                }
                source = bmp
                if (transparent) {
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

    /** Opens a saved card's post with the settings it was saved with. */
    fun loadForCard(card: Card) {
        editingCard = card
        source = null
        cutoutBitmap = null
        originalUri = card.originalUri?.let(Uri::parse)
        preparedCache = null
        val p = EditParams.fromJson(card.editParams)
        val preset = listOf("", "NEW PICKUP", "PC FAVORITE")
        if (p.tag in preset) { tag = p.tag; customTag = "" } else { tag = ""; customTag = p.tag }
        feather = p.feather
        brightness = p.brightness
        lighten = p.lighten
        reflect = p.reflect
        placement = p.placement
        badges = p.badges.filter { it in BADGE_ORDER }.toSet()
        serialBadge = p.badges.firstOrNull { it !in BADGE_ORDER }.orEmpty()
        mode = PhotoMode.BLACK
        viewModelScope.launch {
            busy = "Loading photo…"
            try {
                val app = getApplication<Application>()
                val src = withContext(Dispatchers.IO) {
                    SourceStore.load(card.sourcePath)
                        ?: card.originalUri?.let { PhotoTools.decode(app, Uri.parse(it)) }
                } ?: error("the original photo isn't saved in the app")
                val cut = withContext(Dispatchers.IO) { SourceStore.load(card.cutoutPath) }
                source = src
                cutoutBitmap = cut
                if (p.mode == PhotoMode.CUTOUT) {
                    if (cut == null) cutoutBitmap = PhotoTools.cutout(src)
                    mode = PhotoMode.CUTOUT
                }
                reflect = p.reflect
                placement = p.placement
            } catch (e: Exception) {
                message = "Couldn't open this post for editing: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun currentEditParams() = EditParams(
        mode = mode, tag = tagText, feather = feather, brightness = brightness,
        lighten = lighten, reflect = reflect, placement = placement, badges = badgeList,
    )

    /** Saves the source photo (and cutout) privately so the post can be re-edited later. */
    suspend fun saveSources(name: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val src = source?.let { SourceStore.save(app, it, name) }
        val cut = cutoutBitmap?.let { SourceStore.save(app, it, "$name-cutout", png = true) }
        src to cut
    }

    /** Re-edit: renders the new post, replaces the old image, and updates the card. */
    suspend fun saveEdit(): Card {
        val card = editingCard ?: error("Nothing to save")
        val app = getApplication<Application>()
        val post = renderFinal()
        val name = "strike3-" + card.player.filter { it.isLetterOrDigit() }.ifBlank { "card" } + "-" + Media.stamp()
        val uri = withContext(Dispatchers.IO) { Media.saveJpeg(app, post, name) }
        card.postUri?.let { old -> withContext(Dispatchers.IO) { Media.delete(app, Uri.parse(old)) } }
        val sourcePath = card.sourcePath ?: source?.let { withContext(Dispatchers.IO) { SourceStore.save(app, it, name) } }
        val cutoutPath = card.cutoutPath ?: cutoutBitmap?.let { withContext(Dispatchers.IO) { SourceStore.save(app, it, "$name-cutout", png = true) } }
        val updated = card.copy(
            postUri = uri.toString(),
            editParams = currentEditParams().toJson(),
            sourcePath = sourcePath,
            cutoutPath = cutoutPath,
            isAuto = "AUTO" in badges,
            isPatch = "RELIC" in badges,
            isRookie = "RC" in badges,
            serial = serialBadge.trim().ifEmpty { card.serial },
        )
        (app as Strike3App).repo.update(updated)
        editingCard = null
        return updated
    }

    fun selectTag(t: String) {
        tag = t
        customTag = ""
    }

    fun resetPosition() { placement = Placement() }
}
