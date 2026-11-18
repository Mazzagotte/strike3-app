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
import dev.mazzagotte.strike3.data.DriveBackup
import kotlinx.coroutines.Dispatchers
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

    dialog?.let {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }
}
