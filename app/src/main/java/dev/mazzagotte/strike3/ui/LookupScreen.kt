package dev.mazzagotte.strike3.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.FrameCrop
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.util.CardPrint
import dev.mazzagotte.strike3.util.CardReader
import dev.mazzagotte.strike3.util.CardText
import dev.mazzagotte.strike3.util.Duplicates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Do I have this?": take a photo of a card (front or back) and see what in the collection it
 * matches, by what's printed on it and by how it looks. Nothing is saved.
 */
class LookupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as Strike3App).repo

    var photoUri by mutableStateOf<Uri?>(null); private set
    var frame by mutableStateOf<FrameCrop?>(null); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var busy by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    /** What the reader got; the player and card # can be corrected by hand. */
    var player by mutableStateOf(""); var cardNumber by mutableStateOf("")
    var year by mutableStateOf(""); private set
    var setName by mutableStateOf(""); private set
    var parallel by mutableStateOf(""); private set
    var serial by mutableStateOf(""); private set
    private var prints: List<ByteArray> = emptyList()

    var matches by mutableStateOf<List<Duplicates.Match>>(emptyList()); private set
    private var all: List<Card> = emptyList()

    fun reset() {
        photoUri = null; frame = null; preview = null; error = null; busy = null
        player = ""; cardNumber = ""; year = ""; setName = ""; parallel = ""; serial = ""
        prints = emptyList(); matches = emptyList()
    }

    fun check(uri: Uri, crop: FrameCrop?) {
        reset()
        photoUri = uri; frame = crop
        viewModelScope.launch {
            busy = "Reading the card…"
            try {
                val app = getApplication<Application>()
                val decoded = withContext(Dispatchers.IO) { PhotoTools.decode(app, uri) }
                val bmp = withContext(Dispatchers.Default) {
                    if (crop != null) PhotoTools.autoTrim(PhotoTools.cropToFrame(decoded, crop)) else PhotoTools.autoTrim(decoded)
                }
                val turn = runCatching { CardReader.detectRotation(bmp) }.getOrDefault(0)
                val upright = if (turn != 0) PhotoTools.rotate(bmp, turn) else bmp
                preview = upright
                all = repo.cards().first()
                val lines = runCatching { CardReader.lines(upright) }.getOrDefault(emptyList())
                // Front or back isn't known; the rules that matter (codes, names, serials) work either way
                val g = CardText.combine(emptyList(), lines, all.map { it.player }.distinct())
                player = g.player; cardNumber = g.cardNumber; year = g.year; setName = g.setName
                parallel = g.parallel; serial = g.serial
                busy = "Comparing with your cards…"
                prints = withContext(Dispatchers.Default) {
                    val small = scaled(upright, 500)
                    val px = IntArray(small.width * small.height)
                    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                    listOf(CardPrint.of(px, small.width, small.height), CardPrint.of(CardPrint.upsideDown(px), small.width, small.height))
                }
                rematch()
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that photo"
            } finally {
                busy = null
            }
        }
    }

    /** Runs the match again (after the player or card # was corrected). */
    fun rematch() {
        val probe = Card(id = -1, player = player.trim(), cardNumber = cardNumber.trim(), year = year, setName = setName, parallel = parallel, serial = serial)
        viewModelScope.launch {
            matches = withContext(Dispatchers.Default) { Duplicates.matches(probe, all, prints) }
        }
    }

    private fun scaled(b: Bitmap, maxSide: Int): Bitmap {
        val k = maxSide.toFloat() / maxOf(b.width, b.height)
        return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }
}

@Composable
fun LookupScreen(
    vm: LookupViewModel,
    onOpen: (Long, List<Long>) -> Unit,
    onAdd: (Uri, FrameCrop?) -> Unit,
    onBack: () -> Unit,
) {
    if (vm.photoUri == null) {
        CaptureScreen(
            onPhoto = { uri, frame -> vm.check(uri, frame) },
            onBack = onBack,
            hint = "Do I have this? Line up the front or the back.",
        )
    } else {
        LookupResults(vm, onOpen, onAdd, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookupResults(
    vm: LookupViewModel,
    onOpen: (Long, List<Long>) -> Unit,
    onAdd: (Uri, FrameCrop?) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Do I have this?") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(96.dp).height(134.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        vm.preview?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(vm.player, { vm.player = it; vm.rematch() }, label = { Text("Player") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(vm.cardNumber, { vm.cardNumber = it.uppercase(); vm.rematch() }, label = { Text("Card #") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val more = listOf(vm.year, vm.setName, vm.parallel, vm.serial).filter { it.isNotBlank() }.joinToString(" · ")
                        if (more.isNotBlank()) Text("Read: $more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.reset() }) { Text("Check another") }
                    vm.photoUri?.let { uri -> OutlinedButton(onClick = { onAdd(uri, vm.frame) }) { Text("Add to collection") } }
                }
            }
            vm.busy?.let { msg ->
                item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(msg) } }
            }
            vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (vm.busy == null && vm.error == null) {
                if (vm.matches.isEmpty()) {
                    item {
                        Text(
                            "Nothing in your collection matches. If the player or card # above is wrong, fix it and the list updates.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    val ids = vm.matches.map { it.card.id }
                    items(vm.matches, key = { it.card.id }) { m -> MatchRow(m) { onOpen(m.card.id, ids) } }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(m: Duplicates.Match, onClick: () -> Unit) {
    val c = m.card
    val color = when (m.kind) {
        Duplicates.Kind.SAME, Duplicates.Kind.LOOKS_LIKE -> MaterialTheme.colorScheme.error
        Duplicates.Kind.MAYBE_SAME -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c, 300, Modifier.width(64.dp).height(90.dp).clip(RoundedCornerShape(6.dp)), preferPost = false)
        Column(Modifier.weight(1f)) {
            Text(m.kind.label + if (m.photoAgrees && m.kind != Duplicates.Kind.LOOKS_LIKE) " · photo matches too" else "",
                color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(c.player.ifBlank { "(no player)" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(c.year, c.setName, c.cardNumber.takeIf { it.isNotBlank() }?.let { "#$it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ver = listOf(c.parallel, c.serial).filter { it.isNotBlank() }.joinToString(" ")
            if (ver.isNotBlank()) Text(ver, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
