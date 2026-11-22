package dev.mazzagotte.strike3.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.util.CardSearch
import dev.mazzagotte.strike3.util.money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Collection stats. Tapping a player, team, year or set row opens the collection filtered to it;
 * tapping one of the most valuable cards opens that card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onFilter: ((CollectionViewModel) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val cards by remember { repo.cards() }.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (cards.isEmpty()) {
                Text("No cards yet. Stats show up once you've added some.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // Totals
            val players = cards.map { it.player.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size
            Tiles(
                listOf(
                    "Cards" to cards.size.toString(),
                    "Players" to players.toString(),
                    "Autos" to cards.count { it.isAuto }.toString(),
                    "Rookies" to cards.count { it.isRookie }.toString(),
                    "Numbered" to cards.count { it.serial.isNotBlank() }.toString(),
                    "1/1s" to cards.count { CardSearch.isOneOfOne(it) }.toString(),
                    "Relics" to cards.count { it.isPatch }.toString(),
                    "Posted" to cards.count { it.posted }.toString(),
                ),
            )

            // Value
            val valued = cards.filter { it.valueNumber != null }
            Section("Value") {
                if (valued.isEmpty()) {
                    Text(
                        "Add a current value on a card's details to see totals here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val total = valued.sumOf { it.valueNumber!! }
                    Tiles(
                        listOf(
                            "Total value" to money(total),
                            "Average" to money(total / valued.size),
                        ),
                    )
                    Text(
                        "${valued.size} of ${cards.size} cards have a value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Most valuable", fontWeight = FontWeight.SemiBold)
                    valued.sortedByDescending { it.valueNumber }.take(5).forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpen(c.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOf(c.subtitle, c.serial).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(money(c.valueNumber!!), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Breakdown("Top players", cards, { it.player }, limit = 10) { name -> onFilter { it.showPlayer(name) } }
            Breakdown("Teams", cards, { it.team }, limit = 10) { name -> onFilter { it.team = name } }
            Breakdown("Years", cards, { it.year }, byLabel = true) { name -> onFilter { it.year = name } }
            Breakdown("Sets", cards, { it.setName }, limit = 10) { name -> onFilter { it.set = name } }
            AddedByMonth(cards)
        }
    }
}

/** Grid of small number tiles, two per row. */
@Composable
private fun Tiles(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Counts per value with bars, biggest first (or by label, newest first, for years). Tap a row to filter. */
@Composable
private fun Breakdown(
    title: String,
    cards: List<Card>,
    field: (Card) -> String,
    limit: Int = 12,
    byLabel: Boolean = false,
    onPick: (String) -> Unit,
) {
    val counts = cards.map { field(it).trim() }.filter { it.isNotEmpty() }
        .groupBy { it.lowercase() }
        .map { (_, same) -> same.first() to same.size }
        .let { list -> if (byLabel) list.sortedByDescending { it.first } else list.sortedByDescending { it.second } }
    if (counts.isEmpty()) return
    val max = counts.maxOf { it.second }.toFloat()
    Section(title) {
        counts.take(limit).forEach { (label, n) -> BarRow(label, n, n / max) { onPick(label) } }
        if (counts.size > limit) {
            Text("+ ${counts.size - limit} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarRow(label: String, count: Int, fraction: Float, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(130.dp))
        Box(Modifier.weight(1f).height(14.dp)) {
            if (fraction > 0f) Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).height(14.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        Text(count.toString(), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp))
    }
}

/** Cards added in each of the last 6 months. */
@Composable
private fun AddedByMonth(cards: List<Card>) {
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now()
    val months = (5 downTo 0).map { now.minusMonths(it.toLong()) }
    val counts = months.map { m -> m to cards.count { YearMonth.from(Instant.ofEpochMilli(it.createdAt).atZone(zone)) == m } }
    val max = counts.maxOf { it.second }.coerceAtLeast(1).toFloat()
    Section("Added by month") {
        counts.forEach { (m, n) ->
            val label = m.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + m.year
            BarRow(label, n, if (n == 0) 0f else n / max, onClick = null)
        }
    }
}
