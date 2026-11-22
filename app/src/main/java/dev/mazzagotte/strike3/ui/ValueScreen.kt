package dev.mazzagotte.strike3.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.util.money

/**
 * What the collection is worth: total, how much is priced, value by player / set / team / year,
 * and the most valuable cards. Uses each card's Current value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueScreen(
    onBack: () -> Unit,
    onOpen: (id: Long, ids: List<Long>) -> Unit,
    onFilter: ((CollectionViewModel) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val cards by remember { repo.cards() }.collectAsState(initial = emptyList())
    val valued = cards.filter { it.valueNumber != null }
    val total = valued.sumOf { it.valueNumber!! }
    val unpriced = cards.size - valued.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collection value") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Headline
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TOTAL VALUE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (cards.isEmpty()) "No cards yet."
                        else "${valued.size} of ${cards.size} cards priced" +
                            (if (valued.isNotEmpty()) " · average ${money(total / valued.size)}" else ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (unpriced > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$unpriced card${if (unpriced == 1) " has" else "s have"} no value yet, so the real total is higher.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onFilter { it.noValue = true } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Show the $unpriced unpriced card${if (unpriced == 1) "" else "s"}")
                    }
                    Text(
                        "Pricing a lot at once is quicker in a spreadsheet: Settings › Export Excel with photos, fill in the Current value column, then Import spreadsheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (valued.isEmpty()) return@Column

            // Most valuable
            var showCount by remember { mutableIntStateOf(10) }
            val ranked = valued.sortedByDescending { it.valueNumber }
            Section("Most valuable") {
                ranked.take(showCount).forEachIndexed { i, c -> ValueRow(i + 1, c) { onOpen(c.id, ranked.map { it.id }) } }
                if (ranked.size > showCount) {
                    TextButton(onClick = { showCount += 20 }) { Text("Show more (${ranked.size - showCount} left)") }
                }
            }

            ValueBreakdown("By player", valued, { it.player }) { name -> onFilter { it.showPlayer(name) } }
            ValueBreakdown("By set", valued, { it.setName }) { name -> onFilter { it.set = name } }
            ValueBreakdown("By team", valued, { it.team }) { name -> onFilter { it.team = name } }
            ValueBreakdown("By year", valued, { it.year }) { name -> onFilter { it.year = name } }

            // Where the value sits
            val autos = valued.filter { it.isAuto }.sumOf { it.valueNumber!! }
            val numbered = valued.filter { it.serial.isNotBlank() }.sumOf { it.valueNumber!! }
            val rookies = valued.filter { it.isRookie }.sumOf { it.valueNumber!! }
            Section("Share of total") {
                listOf("Autos" to autos, "Numbered" to numbered, "Rookies" to rookies).forEach { (label, v) ->
                    val pct = if (total > 0) (v / total * 100).toInt() else 0
                    BarRow("$label · $pct%", money(v), if (total > 0) (v / total).toFloat() else 0f, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun ValueRow(rank: Int, c: Card, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$rank", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
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

/** Total value per player / set / team / year, biggest first. Tap a row to see those cards. */
@Composable
private fun ValueBreakdown(title: String, valued: List<Card>, field: (Card) -> String, onPick: (String) -> Unit) {
    val sums = valued.filter { field(it).isNotBlank() }
        .groupBy { field(it).trim().lowercase() }
        .map { (_, same) -> field(same.first()).trim() to same.sumOf { it.valueNumber!! } }
        .sortedByDescending { it.second }
    if (sums.isEmpty()) return
    val max = sums.first().second.takeIf { it > 0 } ?: 1.0
    Section(title) {
        sums.take(8).forEach { (label, v) -> BarRow(label, money(v), (v / max).toFloat()) { onPick(label) } }
        if (sums.size > 8) {
            Text("+ ${sums.size - 8} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
