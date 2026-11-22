package dev.mazzagotte.strike3.ui

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.util.CardReader
import dev.mazzagotte.strike3.util.Media
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import dev.mazzagotte.strike3.util.ParallelHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(editId: Long?, editor: EditorViewModel, onDone: (Long) -> Unit, onBack: () -> Unit, catalog: Boolean = false) {
    val context = LocalContext.current
    val repo = (context.applicationContext as Strike3App).repo
    val scope = rememberCoroutineScope()
    var card by remember {
        mutableStateOf(
            Card(
                dateAcquired = LocalDate.now().toString(),
                isAuto = "AUTO" in editor.badges,
                isPatch = "RELIC" in editor.badges,
                isRookie = "RC" in editor.badges,
                serial = editor.serialBadge.trim(),
            ),
        )
    }
    var loaded by remember { mutableStateOf(editId == null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var scanning by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var chooseScanSource by remember { mutableStateOf(false) }

    // Catalog mode: front photo from the camera (turned upright), back photo once added
    val frontThumb by produceState<Bitmap?>(initialValue = null, editor.source, editor.rotation) {
        value = editor.source?.let { src -> withContext(Dispatchers.Default) { PhotoTools.rotate(src, editor.rotation) } }
    }
    val backThumb by produceState<Bitmap?>(initialValue = null, card.backPath) {
        value = withContext(Dispatchers.IO) { SourceStore.loadScaled(card.backPath, 800) }
    }

    // For parallel suggestions: your other cards, and the main border color of the front photo.
    val allCards by remember { repo.cards() }.collectAsState(initial = emptyList())
    val borderColor by produceState<String?>(initialValue = null, editId, loaded) {
        if (!loaded) return@produceState
        value = withContext(Dispatchers.Default) {
            val photo = if (editId == null) editor.cutoutBitmap ?: editor.source
            else SourceStore.load(card.cutoutPath ?: card.sourcePath)
            photo?.let { runCatching { ParallelHints.borderColor(it) }.getOrNull() }
        }
    }

    fun readCard(uri: Uri) {
        scope.launch {
            scanning = "Reading the card…"
            try {
                val bmp = withContext(Dispatchers.IO) { PhotoTools.decode(context, uri) }
                val guess = CardReader.read(bmp)
                // Keep the photo as the carousel's back slide
                val back = withContext(Dispatchers.IO) { SourceStore.save(context, bmp, "back-" + Media.stamp()) }
                val old = card.backPath
                card = CardReader.merge(card, guess).copy(backPath = back)
                if (editId == null) SourceStore.delete(old) // a saved card keeps its old file until you save
                scanning = if (guess.found.isEmpty()) "Couldn't read any details. Try a sharper, closer photo of the back."
                else "Filled in: " + guess.found.joinToString(", ") + ". Check them before saving."
            } catch (e: Exception) {
                scanning = "Couldn't read the card: ${e.message}"
            }
        }
    }

    val takeScan = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (uri != null) {
            if (ok) readCard(uri) else Media.delete(context, uri)
        }
    }
    val pickScan = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readCard(uri)
    }

    LaunchedEffect(editId) {
        if (editId != null) {
            repo.get(editId)?.let { card = it }
            loaded = true
        }
    }

    fun save(share: Boolean) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                if (editId != null) {
                    repo.update(card)
                    onDone(editId)
                } else if (catalog) {
                    // Collection only: keep the front photo (and the editor settings, so the post can be made later)
                    val name = "strike3-" + (card.player.filter { it.isLetterOrDigit() }.ifBlank { "card" }) + "-" + Media.stamp()
                    editor.badges = listOfNotNull("AUTO".takeIf { card.isAuto }, "RELIC".takeIf { card.isPatch }, "RC".takeIf { card.isRookie }).toSet()
                    editor.serialBadge = card.serial.trim()
                    val (sourcePath, cutoutPath) = editor.saveSources(name)
                    val saved = card.copy(
                        postUri = null,
                        originalUri = editor.originalUri?.toString(),
                        editParams = editor.currentEditParams().toJson(),
                        sourcePath = sourcePath,
                        cutoutPath = cutoutPath,
                    )
                    onDone(repo.add(saved))
                } else {
                    val post = editor.renderFinal()
                    val name = "strike3-" + (card.player.filter { it.isLetterOrDigit() }.ifBlank { "card" }) + "-" + Media.stamp()
                    val uri: Uri = withContext(Dispatchers.IO) { Media.saveJpeg(context, post, name) }
                    val (sourcePath, cutoutPath) = editor.saveSources(name)
                    val saved = card.copy(
                        postUri = uri.toString(),
                        originalUri = editor.originalUri?.toString(),
                        posted = card.posted || share,
                        editParams = editor.currentEditParams().toJson(),
                        sourcePath = sourcePath,
                        cutoutPath = cutoutPath,
                    )
                    val id = repo.add(saved)
                    if (share) Media.shareToInstagram(context, uri, Media.caption(context, saved))
                    onDone(id)
                }
            } catch (e: Exception) {
                error = "Couldn't save: ${e.message}"
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "Edit card" else if (catalog) "Add card" else "Card details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (catalog && editId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoBox("Front", frontThumb, editor.busy, Modifier.weight(1f)) {}
                    PhotoBox("Back", backThumb, scanning?.takeIf { it.endsWith("…") }, Modifier.weight(1f)) { chooseScanSource = true }
                }
            }
            OutlinedButton(onClick = { chooseScanSource = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Text(if (card.backPath == null) "  Add the back (reads the details)" else "  Retake the back")
            }
            scanning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Field("Player", card.player, words = true) { card = card.copy(player = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Year", card.year, number = true, modifier = Modifier.weight(0.35f)) { card = card.copy(year = it) }
                Field("Set", card.setName, words = true, modifier = Modifier.weight(0.65f)) { card = card.copy(setName = it) }
            }
            Field("Parallel", card.parallel, words = true) { card = card.copy(parallel = it) }
            ParallelSuggestions(card, allCards, borderColor) { card = card.copy(parallel = it) }
            SerialField(card.serial, { card = card.copy(serial = it) }, Modifier.fillMaxWidth())
            Field("Team", card.team, words = true) { card = card.copy(team = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(card.isAuto, { card = card.copy(isAuto = !card.isAuto) }, { Text("Auto") })
                FilterChip(card.isRookie, { card = card.copy(isRookie = !card.isRookie) }, { Text("Rookie") })
                FilterChip(card.isPatch, { card = card.copy(isPatch = !card.isPatch) }, { Text("Relic") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Date acquired", card.dateAcquired, modifier = Modifier.weight(0.55f)) { card = card.copy(dateAcquired = it) }
                Field("Price paid", card.pricePaid, number = true, modifier = Modifier.weight(0.45f)) { card = card.copy(pricePaid = it) }
            }
            Field("Notes", card.notes, singleLine = false) { card = card.copy(notes = it) }
            LabeledSwitch("Posted to Instagram", card.posted) { card = card.copy(posted = it) }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (editId == null && catalog) {
                Button(
                    onClick = { save(share = false) },
                    enabled = !saving && editor.busy == null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) {
                    Text(if (saving) "Saving…" else if (editor.busy != null) "Loading photo…" else "Save to collection")
                }
            } else if (editId == null) {
                Button(onClick = { save(share = true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saving) "Saving…" else "Save & share to Instagram")
                }
                OutlinedButton(onClick = { save(share = false) }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text("Save only")
                }
            } else {
                Button(onClick = { save(share = false) }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(if (saving) "Saving…" else "Save changes")
                }
            }
        }
    }
    if (chooseScanSource) {
        PhotoSourceDialog(
            title = "Photo of the card back",
            onCamera = {
                chooseScanSource = false
                Media.newCameraUri(context)?.let { uri ->
                    pendingCameraUri = uri
                    takeScan.launch(uri)
                }
            },
            onGallery = {
                chooseScanSource = false
                pickScan.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { chooseScanSource = false },
        )
    }
}

/**
 * Tap-to-fill chips under the Parallel field, shown only while it's empty:
 * names you've used before for this set and print run, then the photo's border color as a hint.
 */
@Composable
private fun ParallelSuggestions(card: Card, all: List<Card>, color: String?, onPick: (String) -> Unit) {
    if (card.parallel.isNotBlank()) return
    val fromLog = remember(card.setName, card.year, card.serial, all, color) { ParallelHints.fromLog(card, all, color) }
    val colorHint = color?.takeIf { c -> fromLog.none { it.contains(c, ignoreCase = true) } }
    if (fromLog.isEmpty() && colorHint == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (fromLog.isNotEmpty()) "Suggested from your log" else "Border color in the photo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fromLog.forEach { name -> SuggestionChip(onClick = { onPick(name) }, label = { Text(name) }) }
            colorHint?.let { c -> SuggestionChip(onClick = { onPick(c) }, label = { Text("$c?") }) }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    words: Boolean = false,
    number: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = modifier,
    )
}

/** Photo tile for the Add card screen. Tap to add or retake (when [onClick] does something). */
@Composable
private fun PhotoBox(label: String, bmp: Bitmap?, busy: String?, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.aspectRatio(0.72f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(6.dp)) {
        when {
            busy != null -> CircularProgressIndicator()
            bmp != null -> Image(bmp.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Text("Add $label", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
