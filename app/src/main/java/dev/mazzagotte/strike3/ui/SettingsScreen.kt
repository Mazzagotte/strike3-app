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
import dev.mazzagotte.strike3.data.Xlsx
import androidx.compose.foundation.layout.Row
import dev.mazzagotte.strike3.util.Media
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
                withContext(Dispatchers.IO) { DriveBackup.writeBytes(context, uri, DriveBackup.toCsv(cards).toByteArray()) }
                "Exported ${cards.size} card${if (cards.size == 1) "" else "s"}."
            } catch (e: Throwable) {
                discard(context, uri)
                "Couldn't export: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
    // Excel file with the front and back photos in their cells, saved straight to Downloads
    var exporting by remember { mutableStateOf<String?>(null) }
    var exported by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    fun exportExcel() {
        scope.launch {
            exporting = "Making photos…"
            try {
                val cards = repo.cards().first()
                val name = "strike3-collection-" + Media.stamp() + ".xlsx"
                val uri = withContext(Dispatchers.IO) {
                    // Build the whole file first, so a problem can't leave a half-written file behind
                    val bytes = java.io.ByteArrayOutputStream().also { buf ->
                        Xlsx.write(buf, cards) { done, total ->
                            scope.launch { exporting = "Making photos… $done of $total" }
                        }
                    }.toByteArray()
                    if (!Xlsx.looks(bytes)) error("the file came out empty")
                    Media.saveDownload(context, name, XLSX_MIME, bytes).also {
                        exported = it to "Saved $name to Downloads › Strike III " +
                            "(${cards.size} card${if (cards.size == 1) "" else "s"}, ${"%.1f".format(bytes.size / 1_048_576.0)} MB)."
                    }
                }
            } catch (e: Throwable) {
                dialog = "Couldn't export: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                exporting = null
            }
        }
    }

    // Bring edits made in Google Sheets / Excel back in
    var importPlan by remember { mutableStateOf<CsvImport.Plan?>(null) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("couldn't open the file")
                    // Excel file or CSV, whichever was picked
                    if (Xlsx.looks(bytes)) Xlsx.readRows(bytes.inputStream())
                    else CsvImport.parse(bytes.toString(Charsets.UTF_8))
                }
                val plan = CsvImport.planRows(rows, repo.cards().first())
                if (plan.updated.isEmpty() && plan.added.isEmpty()) {
                    dialog = "Nothing to change: all ${plan.unchanged} cards already match the file."
                } else {
                    importPlan = plan
                }
            } catch (e: Exception) {
                dialog = "Couldn't read that file: ${e.message}"
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
                    "Save the whole collection as a spreadsheet anywhere: Downloads, Drive, or send it on. " +
                        "The Excel file goes straight to Downloads › Strike III, with the front and back photos placed in their cells (shown in Excel for Microsoft 365). Share it from there to Drive, email and so on.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { exportExcel() },
                    enabled = exporting == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(exporting ?: "Export Excel with photos") }
                OutlinedButton(
                    onClick = { exportCsv.launch("strike3-collection-" + java.time.LocalDate.now() + ".csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export CSV (no photos)") }
                Text(
                    "Edited the spreadsheet? Import the Excel file or a CSV here (from Google Sheets: File › Download › Microsoft Excel or CSV). " +
                        "Rows match their cards by the ID column, so leave that column as is. Rows without an ID become new cards. " +
                        "Photos and posts aren't changed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        importCsv.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import spreadsheet (Excel or CSV)") }
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

    exported?.let { (uri, msg) ->
        AlertDialog(
            onDismissRequest = { exported = null },
            title = { Text("Excel file saved") },
            text = { Text(msg + " Photos are placed in their cells, which shows in Excel for Microsoft 365.") },
            confirmButton = {
                TextButton(onClick = { exported = null; Media.shareFile(context, uri, XLSX_MIME, "Send spreadsheet") }) { Text("Share") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { exported = null; Media.openFile(context, uri, XLSX_MIME) }) { Text("Open") }
                    TextButton(onClick = { exported = null }) { Text("Done") }
                }
            },
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

/** Removes an export file that couldn't be written, so an empty file isn't left behind. */
private fun discard(context: android.content.Context, uri: android.net.Uri) {
    runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
