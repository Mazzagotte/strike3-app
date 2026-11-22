package dev.mazzagotte.strike3.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.util.Media
import dev.mazzagotte.strike3.util.SortBy
import dev.mazzagotte.strike3.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Header row in a grouped grid. */
private data class Group(val name: String, val cards: List<Card>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    vm: CollectionViewModel,
    onNewPost: () -> Unit,
    onAddCard: () -> Unit,
    onBatch: () -> Unit,
    onStats: () -> Unit,
    /** Opens a card; [ids] is the current results in order, for swiping between cards. */
    onOpen: (id: Long, ids: List<Long>) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val cards by remember { repo.cards() }.collectAsState(initial = emptyList())
    val shown = vm.apply(cards)
    val groups = if (vm.groupByPlayer) {
        shown.groupBy { it.player.trim().ifBlank { "No player" } }
            .map { (name, list) -> Group(name, list) }
            .sortedWith(compareBy<Group> { it.name == "No player" }.thenBy { it.name.lowercase() })
    } else {
        listOf(Group("", shown))
    }
    val order = groups.flatMap { g -> g.cards.map { it.id } }

    var sortMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var chooseAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STRIKE III", fontWeight = FontWeight.Bold, letterSpacing = 3.sp) },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { vm.updateSort(s); sortMenu = false },
                                    leadingIcon = { if (vm.sort == s) Icon(Icons.Filled.Check, null) else Box(Modifier.size(24.dp)) },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { viewMenu = true }) { Icon(Icons.Filled.GridView, "View") }
                        DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                            GridLayout.entries.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l.label) },
                                    onClick = { vm.updateLayout(l); viewMenu = false },
                                    leadingIcon = { if (vm.layout == l) Icon(Icons.Filled.Check, null) else Box(Modifier.size(24.dp)) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Show posts") },
                                onClick = { vm.updateShowPosts(!vm.showPosts) },
                                trailingIcon = { Switch(checked = vm.showPosts, onCheckedChange = { vm.updateShowPosts(it) }) },
                            )
                            DropdownMenuItem(
                                text = { Text("Group by player") },
                                onClick = { vm.updateGroup(!vm.groupByPlayer) },
                                trailingIcon = { Switch(checked = vm.groupByPlayer, onCheckedChange = { vm.updateGroup(it) }) },
                            )
                        }
                    }
                    IconButton(onClick = onStats) { Icon(Icons.Filled.BarChart, "Stats") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Catalog only: front and back photos plus details, make the post later
                ExtendedFloatingActionButton(
                    onClick = { chooseAdd = true },
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
        if (chooseAdd) {
            AlertDialog(
                onDismissRequest = { chooseAdd = false },
                title = { Text("Add cards") },
                text = {
                    Text("One card: front, back, then check the details before saving.\n\nBatch: shoot front, back, next card for a whole stack. Each is read and saved as you go, and you finish the details later from the Needs details filter.")
                },
                confirmButton = { TextButton(onClick = { chooseAdd = false; onBatch() }) { Text("Batch") } },
                dismissButton = { TextButton(onClick = { chooseAdd = false; onAddCard() }) { Text("One card") } },
            )
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("Search: judge 2024 auto /99…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Close, "Clear search") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            FilterRow(vm, cards)
            SummaryLine(shown)

            when {
                cards.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No cards yet. Tap Add card to catalog a card (front and back), or New post to make an Instagram post.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shown.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No cards match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.query = ""; vm.clearFilters() }) { Text("Clear search and filters") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(
                        when (vm.layout) {
                            GridLayout.TWO -> 2
                            GridLayout.THREE -> 3
                            GridLayout.LIST -> 1
                        },
                    ),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 168.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (vm.layout == GridLayout.THREE) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (vm.layout == GridLayout.LIST) 8.dp else 16.dp),
                ) {
                    groups.forEach { g ->
                        if (vm.groupByPlayer) {
                            item(key = "h-" + g.name, span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "${g.name} · ${g.cards.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        items(g.cards, key = { it.id }) { card ->
                            val open = { onOpen(card.id, order) }
                            when (vm.layout) {
                                GridLayout.LIST -> CardRow(card, vm.showPosts, open)
                                GridLayout.THREE -> CardTile(card, vm.showPosts, compact = true, onClick = open)
                                GridLayout.TWO -> CardTile(card, vm.showPosts, compact = false, onClick = open)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Quick filter chips plus Team / Set / Year pick-lists. */
@Composable
private fun FilterRow(vm: CollectionViewModel, cards: List<Card>) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.anyFilter) {
            TextButton(onClick = { vm.clearFilters() }) { Text("Clear") }
        }
        val toReview = cards.count { it.needsReview }
        if (toReview > 0 || vm.needsReview) {
            FilterChip(selected = vm.needsReview, onClick = { vm.needsReview = !vm.needsReview }, label = { Text("Needs details ($toReview)") })
        }
        FilterChip(selected = vm.autos, onClick = { vm.autos = !vm.autos }, label = { Text("Autos") })
        FilterChip(selected = vm.rookies, onClick = { vm.rookies = !vm.rookies }, label = { Text("Rookies") })
        FilterChip(selected = vm.numbered, onClick = { vm.numbered = !vm.numbered }, label = { Text("Numbered") })
        FilterChip(selected = vm.oneOfOnes, onClick = { vm.oneOfOnes = !vm.oneOfOnes }, label = { Text("1/1") })
        PickChip("Team", vm.team, cards.map { it.team }) { vm.team = it }
        PickChip("Set", vm.set, cards.map { it.setName }) { vm.set = it }
        PickChip("Year", vm.year, cards.map { it.year }, newestFirst = true) { vm.year = it }
        FilterChip(selected = vm.posted == true, onClick = { vm.posted = if (vm.posted == true) null else true }, label = { Text("Posted") })
        FilterChip(selected = vm.posted == false, onClick = { vm.posted = if (vm.posted == false) null else false }, label = { Text("Not posted") })
    }
}

/** Chip that opens a list of the values in your collection, with how many cards have each. */
@Composable
private fun PickChip(label: String, selected: String?, values: List<String>, newestFirst: Boolean = false, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val counts = values.map { it.trim() }.filter { it.isNotEmpty() }
        .groupBy { it.lowercase() }
        .map { (_, same) -> same.first() to same.size }
        .let { list -> if (newestFirst) list.sortedByDescending { it.first } else list.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Any ${label.lowercase()}") }, onClick = { onPick(null); open = false })
            if (counts.isEmpty()) {
                DropdownMenuItem(text = { Text("Nothing logged yet") }, onClick = { open = false }, enabled = false)
            }
            counts.forEach { (value, n) ->
                DropdownMenuItem(
                    text = { Text("$value  ($n)") },
                    onClick = { onPick(value); open = false },
                    leadingIcon = { if (selected.equals(value, ignoreCase = true)) Icon(Icons.Filled.Check, null) else Box(Modifier.size(24.dp)) },
                )
            }
        }
    }
}

/** "42 cards · 12 players · 5 autos · 9 numbered" for what's showing. */
@Composable
private fun SummaryLine(shown: List<Card>) {
    val players = shown.map { it.player.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size
    val parts = listOfNotNull(
        "${shown.size} card${if (shown.size == 1) "" else "s"}",
        "$players player${if (players == 1) "" else "s"}".takeIf { players > 0 },
        shown.count { it.isAuto }.takeIf { it > 0 }?.let { "$it auto${if (it == 1) "" else "s"}" },
        shown.count { it.serial.isNotBlank() }.takeIf { it > 0 }?.let { "$it numbered" },
        shown.mapNotNull { it.valueNumber }.takeIf { it.isNotEmpty() }?.let { money(it.sum()) },
    )
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun CardTile(card: Card, showPost: Boolean, compact: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        CardImage(
            card = card,
            maxSide = if (compact) 360 else 500,
            preferPost = showPost,
            modifier = Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(8.dp)),
        )
        Text(
            card.title,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val sub = if (compact) listOf(card.year, card.serial).filter { it.isNotBlank() }.joinToString(" · ")
        else listOf(card.subtitle, card.serial).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** List layout: small picture, name, set line and flags. */
@Composable
private fun CardRow(card: Card, showPost: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardImage(card, 200, Modifier.width(64.dp).aspectRatio(1080f / 1350f).clip(RoundedCornerShape(6.dp)), preferPost = showPost)
        Column(Modifier.weight(1f)) {
            Text(card.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.subtitle.isNotBlank()) {
                Text(card.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val flags = listOfNotNull(
                card.serial.takeIf { it.isNotBlank() },
                "Auto".takeIf { card.isAuto },
                "RC".takeIf { card.isRookie },
                "Relic".takeIf { card.isPatch },
                card.team.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (flags.isNotBlank()) {
                Text(flags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
 * A card's picture: the finished post when [preferPost] is on and there is one, otherwise the
 * front photo (turned the way it was set in the editor), falling back to the post.
 */
@Composable
fun CardImage(card: Card, maxSide: Int, modifier: Modifier = Modifier, preferPost: Boolean = true) {
    val usePost = card.postUri != null && (preferPost || card.sourcePath == null)
    if (usePost || card.sourcePath == null) {
        UriImage(card.postUri, maxSide, modifier)
        return
    }
    val turn = remember(card.editParams) { EditParams.fromJson(card.editParams).rotation }
    PhotoImage(card.sourcePath, turn, maxSide, modifier)
}

/** An app-private photo (front, back), shown whole on a dark background. */
@Composable
fun PhotoImage(path: String?, turn: Int, maxSide: Int, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(initialValue = null, path, turn, maxSide) {
        value = withContext(Dispatchers.IO) {
            SourceStore.loadScaled(path, maxSide)?.let { PhotoTools.rotate(it, turn) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
        } ?: Text("III", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
