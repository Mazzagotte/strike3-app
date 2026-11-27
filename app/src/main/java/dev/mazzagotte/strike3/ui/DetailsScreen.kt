package dev.mazzagotte.strike3.ui

import android.net.Uri
import dev.mazzagotte.strike3.util.CardText
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.util.Duplicates
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import dev.mazzagotte.strike3.render.FrameCrop
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
    // What the reader filled in last, so a later reading (the back) can correct it
    var lastAuto by remember { mutableStateOf<CardText.Guess?>(null) }
    var dupOf by remember { mutableStateOf<Card?>(null) }
    var dupConfirmed by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf(false) }
    var capturingBack by remember { mutableStateOf(false) }

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

    /** The front photo, upright, for reading text. */
    suspend fun frontPhoto(): Bitmap? = withContext(Dispatchers.Default) {
        if (editId == null) editor.source?.let { PhotoTools.rotate(it, editor.rotation) }
        else SourceStore.load(card.sourcePath)?.let { PhotoTools.rotate(it, EditParams.fromJson(card.editParams).rotation) }
    }

    fun readCard(uri: Uri, frame: FrameCrop?) {
        scope.launch {
            scanning = "Reading the card…"
            try {
                val bmp = loadCardPhoto(context, uri, frame)
                // Back and front together: the name and serial are often clearer on one side
                val guess = CardReader.readBoth(frontPhoto(), bmp, allCards.map { it.player })
                // Keep the photo as the carousel's back slide
                val back = withContext(Dispatchers.IO) { SourceStore.save(context, bmp, "back-" + Media.stamp()) }
                val old = card.backPath
                card = CardReader.mergeOver(card, lastAuto, guess).copy(backPath = back)
                lastAuto = guess
                if (editId == null) SourceStore.delete(old) // a saved card keeps its old file until you save
                scanning = if (guess.found.isEmpty()) "Couldn't read any details. Try a sharper, closer photo of the back."
                else "Filled in: " + guess.found.joinToString(", ") + "." +
                    (if (card.unsure.isNotEmpty()) " Highlighted ones are guesses, check them." else " Check them before saving.")
            } catch (e: Exception) {
                scanning = "Couldn't read the card: ${e.message}"
            }
        }
    }

    // New card: read the front as soon as the photo is ready, before the back is added
    LaunchedEffect(editId) {
        if (editId != null) return@LaunchedEffect
        snapshotFlow { editor.source != null && editor.busy == null }.first { it }
        if (card.backPath != null || lastAuto != null) return@LaunchedEffect
        try {
            val guess = CardReader.readBoth(frontPhoto(), null, repo.cards().first().map { it.player })
            if (guess.found.isNotEmpty() && card.backPath == null) {
                card = CardReader.mergeOver(card, null, guess)
                lastAuto = guess
                scanning = "From the front: " + guess.found.joinToString(", ") + ". Add the back to fill in more."
            }
        } catch (_: Exception) {
            // Reading the front is a bonus; the back can still be added
        }
    }

    LaunchedEffect(editId) {
        if (editId != null) {
            repo.get(editId)?.let { card = it }
            loaded = true
        }
    }

    fun save(share: Boolean) {
        if (saving) return
        // Already logged? Ask first (once)
        if (!dupConfirmed) {
            Duplicates.find(card, allCards)?.let { dupOf = it; pendingShare = share; return }
        }
        card = card.copy(needsReview = false)
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
                    PhotoBox("Back", backThumb, scanning?.takeIf { it.endsWith("…") }, Modifier.weight(1f)) { capturingBack = true }
                }
            }
            OutlinedButton(onClick = { capturingBack = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Text(if (card.backPath == null) "  Add the back (reads the details)" else "  Retake the back")
            }
            scanning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Fields the reader guessed are highlighted until you change or confirm them
            val unsure = card.unsure
            fun Card.checked(field: String) = withUnsure(unsure - field)
            Field("Player", card.player, words = true, unsure = CardText.F.PLAYER in unsure) { card = card.copy(player = it).checked(CardText.F.PLAYER) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Year", card.year, number = true, modifier = Modifier.weight(0.35f), unsure = CardText.F.YEAR in unsure) { card = card.copy(year = it).checked(CardText.F.YEAR) }
                Field("Card #", card.cardNumber, modifier = Modifier.weight(0.65f), unsure = CardText.F.CARD in unsure) {
                    card = card.copy(cardNumber = it.uppercase()).checked(CardText.F.CARD)
                }
            }
            Field("Set", card.setName, words = true, unsure = CardText.F.SET in unsure) { card = card.copy(setName = it).checked(CardText.F.SET) }
            Field("Parallel", card.parallel, words = true, unsure = CardText.F.PARALLEL in unsure) { card = card.copy(parallel = it).checked(CardText.F.PARALLEL) }
            ParallelSuggestions(card, allCards, borderColor) { card = card.copy(parallel = it).checked(CardText.F.PARALLEL) }
            SerialField(card.serial, { card = card.copy(serial = it).checked(CardText.F.SERIAL) }, Modifier.fillMaxWidth())
            if (CardText.F.SERIAL in unsure) CheckNote("Serial number is a guess, check it")
            Field("Team", card.team, words = true, unsure = CardText.F.TEAM in unsure) { card = card.copy(team = it).checked(CardText.F.TEAM) }
            if (unsure.isNotEmpty()) {
                TextButton(onClick = { card = card.withUnsure(emptySet()) }) { Text("They're all correct") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(card.isAuto, { card = card.copy(isAuto = !card.isAuto) }, { Text("Auto") })
                FilterChip(card.isRookie, { card = card.copy(isRookie = !card.isRookie) }, { Text("Rookie") })
                FilterChip(card.isPatch, { card = card.copy(isPatch = !card.isPatch) }, { Text("Relic") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Date acquired", card.dateAcquired, modifier = Modifier.weight(0.55f)) { card = card.copy(dateAcquired = it) }
                Field("Current value ($)", card.currentValue, number = true, modifier = Modifier.weight(0.45f)) { card = card.copy(currentValue = it) }
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
    dupOf?.let { d ->
        AlertDialog(
            onDismissRequest = { dupOf = null },
            title = { Text("Already in your collection?") },
            text = {
                val what = listOf(d.title, d.subtitle, d.serial).filter { it.isNotBlank() }.joinToString(" ")
                val added = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.createdAt))
                Text("You logged $what on $added. Save this one too?")
            },
            confirmButton = {
                TextButton(onClick = {
                    dupOf = null
                    dupConfirmed = true
                    save(pendingShare)
                }) { Text("Save anyway") }
            },
            dismissButton = { TextButton(onClick = { dupOf = null }) { Text("Cancel") } },
        )
    }

    // Back photo with the same in-app camera and card guide as the front
    if (capturingBack) {
        BackHandler { capturingBack = false }
        // Covers the details screen and keeps taps from reaching it
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            CaptureScreen(
                onPhoto = { uri, frame ->
                    capturingBack = false
                    readCard(uri, frame)
                },
                onBack = { capturingBack = false },
                hint = "Line up the BACK of the card inside the frame. Tap to focus.",
            )
        }
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
    unsure: Boolean = false,
    onChange: (String) -> Unit,
) {
    val check = Color(0xFFFFC107)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (unsure) "$label ?" else label) },
        supportingText = if (unsure) ({ Text("Guessed by the reader, check it") }) else null,
        colors = if (unsure) OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = check, focusedBorderColor = check, unfocusedLabelColor = check,
            unfocusedContainerColor = check.copy(alpha = 0.08f), focusedContainerColor = check.copy(alpha = 0.08f),
        ) else OutlinedTextFieldDefaults.colors(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = modifier,
    )
}

/** Small amber line under a field the reader guessed. */
@Composable
private fun CheckNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFC107), modifier = Modifier.padding(start = 16.dp))
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
