package dev.mazzagotte.strike3.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.util.Media
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(id: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val card by remember(id) { repo.card(id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        val c = card ?: return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UriImage(c.postUri, 1400, Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(10.dp)))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val uri = c.postUri ?: return@Button
                        Media.shareToInstagram(context, Uri.parse(uri), Media.caption(c))
                        if (!c.posted) scope.launch { repo.update(c.copy(posted = true)) }
                    },
                    enabled = c.postUri != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Share") }
                OutlinedButton(onClick = { Media.copy(context, Media.caption(c)) }, modifier = Modifier.weight(1f)) {
                    Text("Copy caption")
                }
            }

            Detail("Player", c.player)
            Detail("Year", c.year)
            Detail("Set", c.setName)
            Detail("Parallel", c.parallel)
            Detail("Serial", c.serial)
            Detail("Team", c.team)
            Detail(
                "Type",
                listOfNotNull("Auto".takeIf { c.isAuto }, "Rookie".takeIf { c.isRookie }, "Patch".takeIf { c.isPatch }).joinToString(", "),
            )
            Detail("Date acquired", c.dateAcquired)
            Detail("Price paid", c.pricePaid)
            Detail("Posted", if (c.posted) "Yes" else "Not yet")
            Detail("Notes", c.notes)
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("It's removed from your log. The photos stay in your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    card?.let { c -> scope.launch { repo.delete(c); onBack() } }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
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
