package dev.mazzagotte.strike3.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.CsvImport
import dev.mazzagotte.strike3.data.DriveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as Strike3App).repo }
    val status by repo.backupStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var setUp by remember { mutableStateOf(DriveBackup.isSetUp(context)) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) DriveBackup.setCsvUri(context, uri)
        scope.launch { repo.backupNow() }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            DriveBackup.setJsonUri(context, uri)
            setUp = true
            // Next: the spreadsheet copy, saved next to it.
            createCsv.launch("strike3-collection.csv")
        }
    }
    // One-off spreadsheet export, saved wherever you pick (Downloads, Drive, …)
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            dialog = try {
                val cards = repo.cards().first()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(DriveBackup.toCsv(cards).toByteArray()) }
                        ?: error("couldn't open the file")
                }
                "Exported ${cards.size} card${if (cards.size == 1) "" else "s"}."
            } catch (e: Exception) {
                "Couldn't export: ${e.message}"
            }
        }
    }
    // Bring edits made in Google Sheets / Excel back in
    var importPlan by remember { mutableStateOf<CsvImport.Plan?>(null) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("couldn't open the file")
                }
                val plan = CsvImport.plan(text, repo.cards().first())
                if (plan.updated.isEmpty() && plan.added.isEmpty()) {
                    dialog = "Nothing to change: all ${plan.unchanged} cards already match the file."
                } else {
                    importPlan = plan
                }
            } catch (e: Exception) {
                dialog = "Couldn't read that CSV: ${e.message}"
            }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            dialog = try {
                val cards = withContext(Dispatchers.IO) { DriveBackup.readBackup(context, uri) }
                repo.restore(cards)
                "Restored ${cards.size} cards."
            } catch (e: Exception) {
                "Couldn't restore: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Card log backup (Google Drive)") {
                Text(status, fontWeight = FontWeight.Medium)
                Text(
                    "The log is saved to two files in Google Drive after every change: a backup to restore from, " +
                        "and a spreadsheet you can open in Google Sheets. In the picker, choose Google Drive and a folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { createJson.launch("strike3-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (setUp) "Change Drive files" else "Set up Drive backup")
                }
                OutlinedButton(
                    onClick = { scope.launch { repo.backupNow() } },
                    enabled = setUp,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Back up now") }
                OutlinedButton(
                    onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore from backup file") }
            }

            Section("Export") {
                Text(
                    "Save the whole collection as a spreadsheet (CSV) anywhere: Downloads, Drive, or send it on. " +
                        "Opens in Google Sheets or Excel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { exportCsv.launch("strike3-collection-" + java.time.LocalDate.now() + ".csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export CSV") }
                Text(
                    "Edited the spreadsheet? Download it as CSV (in Google Sheets: File › Download › CSV) and import it here. " +
                        "Rows match their cards by the ID column, so leave that column as is. Rows without an ID become new cards. " +
                        "Photos and posts aren't changed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import CSV") }
            }

            Section("Photos (Google Photos)") {
                Text(
                    "Finished posts are saved to the \"Strike III\" folder and card photos to \"Strike III Originals\". " +
                        "To sync them: open Google Photos › your profile picture › Photos settings › Backup › " +
                        "Back up device folders, and turn on both folders.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    importPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { importPlan = null },
            title = { Text("Import CSV?") },
            text = {
                val parts = listOfNotNull(
                    "${plan.updated.size} card${if (plan.updated.size == 1) "" else "s"} will be updated".takeIf { plan.updated.isNotEmpty() },
                    "${plan.added.size} new card${if (plan.added.size == 1) "" else "s"} added (no photos)".takeIf { plan.added.isNotEmpty() },
                    "${plan.unchanged} unchanged".takeIf { plan.unchanged > 0 },
                    "${plan.skipped} empty row${if (plan.skipped == 1) "" else "s"} skipped".takeIf { plan.skipped > 0 },
                )
                Text(parts.joinToString(", ") + ". Details in the file replace what's in the app.")
            },
            confirmButton = {
                TextButton(onClick = {
                    importPlan = null
                    scope.launch {
                        repo.applyImport(plan)
                        dialog = "Imported: ${plan.updated.size} updated, ${plan.added.size} added."
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importPlan = null }) { Text("Cancel") } },
        )
    }

    dialog?.let {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }
}
