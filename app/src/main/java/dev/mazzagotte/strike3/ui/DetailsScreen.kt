package dev.mazzagotte.strike3.ui

import android.net.Uri
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
import dev.mazzagotte.strike3.util.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(editId: Long?, editor: EditorViewModel, onDone: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as Strike3App).repo
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf(Card(dateAcquired = LocalDate.now().toString())) }
    var loaded by remember { mutableStateOf(editId == null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                } else {
                    val post = editor.renderFinal()
                    val name = "strike3-" + (card.player.filter { it.isLetterOrDigit() }.ifBlank { "card" }) + "-" + Media.stamp()
                    val uri: Uri = withContext(Dispatchers.IO) { Media.saveJpeg(context, post, name) }
                    val saved = card.copy(
                        postUri = uri.toString(),
                        originalUri = editor.originalUri?.toString(),
                        posted = card.posted || share,
                    )
                    val id = repo.add(saved)
                    if (share) Media.shareToInstagram(context, uri, Media.caption(saved))
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
                title = { Text(if (editId == null) "Card details" else "Edit card") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("Player", card.player, words = true) { card = card.copy(player = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Year", card.year, number = true, modifier = Modifier.weight(0.35f)) { card = card.copy(year = it) }
                Field("Set", card.setName, words = true, modifier = Modifier.weight(0.65f)) { card = card.copy(setName = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Parallel", card.parallel, words = true, modifier = Modifier.weight(0.6f)) { card = card.copy(parallel = it) }
                Field("Serial (25/50)", card.serial, modifier = Modifier.weight(0.4f)) { card = card.copy(serial = it) }
            }
            Field("Team", card.team, words = true) { card = card.copy(team = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(card.isAuto, { card = card.copy(isAuto = !card.isAuto) }, { Text("Auto") })
                FilterChip(card.isRookie, { card = card.copy(isRookie = !card.isRookie) }, { Text("Rookie") })
                FilterChip(card.isPatch, { card = card.copy(isPatch = !card.isPatch) }, { Text("Patch") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Date acquired", card.dateAcquired, modifier = Modifier.weight(0.55f)) { card = card.copy(dateAcquired = it) }
                Field("Price paid", card.pricePaid, number = true, modifier = Modifier.weight(0.45f)) { card = card.copy(pricePaid = it) }
            }
            Field("Notes", card.notes, singleLine = false) { card = card.copy(notes = it) }
            LabeledSwitch("Posted to Instagram", card.posted) { card = card.copy(posted = it) }

            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            if (editId == null) {
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
