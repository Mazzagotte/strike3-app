package dev.mazzagotte.strike3.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import dev.mazzagotte.strike3.render.FrameCrop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.PostComposer
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.util.Media
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import dev.mazzagotte.strike3.util.CaptionStyle
import dev.mazzagotte.strike3.util.Captions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Slot { BACK, CLOSE_UP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(id: Long, onEdit: () -> Unit, onEditPost: (Card) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val composer = remember { PostComposer(context.applicationContext) }
    val card by remember(id) { repo.card(id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var chooseFor by remember { mutableStateOf<Slot?>(null) }
    var pendingSlot by remember { mutableStateOf<Slot?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var capturingBack by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(Captions.defaultStyle(context)) }

    /** Runs an export with a progress overlay and friendly errors. */
    fun doWork(label: String, block: suspend (Card) -> Unit) {
        val c = card ?: return
        if (working != null) return
        scope.launch {
            working = label
            try {
                block(c)
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong."
            } finally {
                working = null
            }
        }
    }

    fun setSlotPhoto(uri: Uri, frame: FrameCrop? = null) {
        val slot = pendingSlot ?: return
        pendingSlot = null
        doWork("Adding photo…") { c ->
            // The back goes through the card guide crop like the front; close-ups stay as shot
            val bmp = if (slot == Slot.BACK) loadCardPhoto(context, uri, frame)
            else withContext(Dispatchers.IO) { PhotoTools.decode(context, uri) }
            val path = withContext(Dispatchers.IO) { SourceStore.save(context, bmp, slot.name.lowercase() + "-" + Media.stamp()) }
            val updated = when (slot) {
                Slot.BACK -> { SourceStore.delete(c.backPath); c.copy(backPath = path) }
                Slot.CLOSE_UP -> { SourceStore.delete(c.closeUpPath); c.copy(closeUpPath = path) }
            }
            repo.update(updated)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (uri != null) {
            if (ok) setSlotPhoto(uri) else { Media.delete(context, uri); pendingSlot = null }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setSlotPhoto(uri) else { pendingSlot = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit details") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        val c = card ?: return@Scaffold
        // Rebuilt when the style or card details change; hand edits last until then.
        val generated = Captions.build(c, style)
        var captionText by remember(generated) { mutableStateOf(generated) }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CardImage(c, 1400, Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(10.dp)))
                working?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(it, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            if (c.postUri == null) {
                // Cataloged without a post: make one from the saved front photo
                Button(onClick = { onEditPost(c) }, enabled = c.sourcePath != null || c.originalUri != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Text("  Make post")
                }
            } else {
                Button(
                    onClick = {
                        val uri = c.postUri ?: return@Button
                        Media.shareToInstagram(context, Uri.parse(uri), captionText)
                        if (!c.posted) scope.launch { repo.update(c.copy(posted = true)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share post") }
            }

            Section("Caption") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CaptionStyle.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = style == s,
                            onClick = {
                                style = s
                                Captions.setDefaultStyle(context, s)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, CaptionStyle.entries.size),
                        ) { Text(s.label) }
                    }
                }
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Built from the card details. Edits here are used by Share and Copy, but aren't saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { Media.copy(context, captionText) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("  Copy caption")
                }
            }

            Section("More") {
                if (c.postUri != null) ActionRow(Icons.Filled.Tune, "Edit post", "Change the tag, position or look", enabled = c.editParams != null) {
                    onEditPost(c)
                }
                if (c.postUri != null) ActionRow(Icons.Filled.AutoAwesomeMotion, "Share as Story or Reel cover", "Same post at 9:16") {
                    doWork("Making Story…") { card ->
                        val bmp = composer.post(card, 1920)
                        val uri = withContext(Dispatchers.IO) { Media.saveJpeg(context, bmp, "strike3-story-" + Media.stamp()) }
                        Media.shareToInstagram(context, uri, captionText)
                    }
                }
                ActionRow(Icons.Filled.Storefront, "Listing photo", "Plain white background for selling or trading") {
                    doWork("Making listing photo…") { card ->
                        val bmp = composer.listing(card)
                        val uri = withContext(Dispatchers.IO) {
                            Media.saveJpeg(context, bmp, "strike3-listing-" + Media.stamp(), Media.LISTINGS_DIR)
                        }
                        Media.shareAnywhere(context, uri, "Share listing photo")
                    }
                }
            }

            Section(if (c.postUri == null) "Back and close-up" else "Carousel") {
                Text(
                    if (c.postUri == null) "Tap to add or change the back and a close-up. Once you make the post, they become a carousel slide."
                    else "Slide 1 is the post. Slide 2 shows the back and a close-up (serial, auto or relic) side by side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlotTile("Back", c.backPath, Modifier.weight(1f)) { capturingBack = true }
                    SlotTile("Close-up", c.closeUpPath, Modifier.weight(1f)) { chooseFor = Slot.CLOSE_UP }
                }
                Button(
                    onClick = {
                        doWork("Making carousel…") { card ->
                            val post = card.postUri?.let(Uri::parse) ?: error("This card has no post yet.")
                            val slide = composer.slide2(card) ?: error("Add a back or close-up photo first.")
                            val slideUri = withContext(Dispatchers.IO) { Media.saveJpeg(context, slide, "strike3-slide2-" + Media.stamp()) }
                            Media.shareCarousel(context, listOf(post, slideUri), captionText)
                            if (!card.posted) repo.update(card.copy(posted = true))
                        }
                    },
                    enabled = c.postUri != null && (c.backPath != null || c.closeUpPath != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ViewCarousel, contentDescription = null)
                    Text("  Share carousel")
                }
            }

            Section("Details") {
                Detail("Player", c.player)
                Detail("Year", c.year)
                Detail("Set", c.setName)
                Detail("Parallel", c.parallel)
                Detail("Serial", c.serial)
                Detail("Team", c.team)
                Detail(
                    "Type",
                    listOfNotNull("Auto".takeIf { c.isAuto }, "Rookie".takeIf { c.isRookie }, "Relic".takeIf { c.isPatch }).joinToString(", "),
                )
                Detail("Date acquired", c.dateAcquired)
                Detail("Price paid", c.pricePaid)
                Detail("Posted", if (c.posted) "Yes" else "Not yet")
                Detail("Notes", c.notes)
            }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    chooseFor?.let { slot ->
        PhotoSourceDialog(
            title = if (slot == Slot.BACK) "Photo of the back" else "Close-up photo",
            onCamera = {
                chooseFor = null
                pendingSlot = slot
                Media.newCameraUri(context)?.let { uri ->
                    pendingCameraUri = uri
                    takePhoto.launch(uri)
                }
            },
            onGallery = {
                chooseFor = null
                pendingSlot = slot
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { chooseFor = null },
        )
    }

    if (capturingBack) {
        BackHandler { capturingBack = false }
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            CaptureScreen(
                onPhoto = { uri, frame ->
                    capturingBack = false
                    pendingSlot = Slot.BACK
                    setSlotPhoto(uri, frame)
                },
                onBack = { capturingBack = false },
                hint = "Line up the BACK of the card inside the frame. Tap to focus.",
            )
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("It's removed from your log. The photos stay in your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    card?.let { c ->
                        scope.launch {
                            listOf(c.sourcePath, c.cutoutPath, c.backPath, c.closeUpPath).forEach { SourceStore.delete(it) }
                            repo.delete(c)
                            onBack()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SlotTile(label: String, path: String?, modifier: Modifier, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { SourceStore.load(path) }
    }
    OutlinedButton(onClick = onClick, modifier = modifier.aspectRatio(0.75f), shape = RoundedCornerShape(10.dp)) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                Text("Add $label", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
    }
}
