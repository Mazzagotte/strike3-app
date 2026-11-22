package dev.mazzagotte.strike3.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.EditParams
import androidx.compose.material.icons.filled.LibraryAdd
import dev.mazzagotte.strike3.util.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Filter(val label: String) { ALL("All"), AUTOS("Autos"), ROOKIES("Rookies"), NOT_POSTED("Not posted") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(onNewPost: () -> Unit, onAddCard: () -> Unit, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val cardsFlow = remember { repo.cards() }
    val cards by cardsFlow.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    val shown = cards.filter { c ->
        val q = query.trim().lowercase()
        val matches = q.isEmpty() || listOf(c.player, c.team, c.setName, c.parallel, c.year, c.serial)
            .any { it.lowercase().contains(q) }
        matches && when (filter) {
            Filter.ALL -> true
            Filter.AUTOS -> c.isAuto
            Filter.ROOKIES -> c.isRookie
            Filter.NOT_POSTED -> !c.posted
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("STRIKE III", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Catalog only: front and back photos plus details, make the post later
                ExtendedFloatingActionButton(
                    onClick = onAddCard,
                    icon = { Icon(Icons.Filled.LibraryAdd, null) },
                    text = { Text("Add card") },
                )
                ExtendedFloatingActionButton(
                    onClick = onNewPost,
                    icon = { Icon(Icons.Filled.PhotoCamera, null) },
                    text = { Text("New post") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search player, team, set…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            Text(
                "${shown.size} card${if (shown.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No cards yet. Tap Add card to catalog a card (front and back), or New post to make an Instagram post.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 168.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown, key = { it.id }) { card -> CardTile(card) { onOpen(card.id) } }
                }
            }
        }
    }
}

@Composable
private fun CardTile(card: Card, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        CardImage(
            card = card,
            maxSide = 500,
            modifier = Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(8.dp)),
        )
        Text(card.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val sub = listOf(card.subtitle, card.serial).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Loads an image from a content URI off the main thread. */
@Composable
fun UriImage(uri: String?, maxSide: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, uri, maxSide) {
        value = uri?.let { u -> withContext(Dispatchers.IO) { Media.thumbnail(context, Uri.parse(u), maxSide) } }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Text("III", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

/**
 * The post if there is one, otherwise the front photo (for cards cataloged without a post),
 * turned the way it was set in the editor.
 */
@Composable
fun CardImage(card: Card, maxSide: Int, modifier: Modifier = Modifier) {
    if (card.postUri != null || card.sourcePath == null) {
        UriImage(card.postUri, maxSide, modifier)
        return
    }
    val turn = remember(card.editParams) { EditParams.fromJson(card.editParams).rotation }
    val bmp by produceState<Bitmap?>(initialValue = null, card.sourcePath, turn, maxSide) {
        value = withContext(Dispatchers.IO) {
            SourceStore.loadScaled(card.sourcePath, maxSide)?.let { PhotoTools.rotate(it, turn) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
        } ?: Text("III", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
