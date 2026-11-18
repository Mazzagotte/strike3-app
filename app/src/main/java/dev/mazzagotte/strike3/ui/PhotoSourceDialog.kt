package dev.mazzagotte.strike3.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Camera or gallery? Uses the phone's own camera app (tap to focus works there too). */
@Composable
fun PhotoSourceDialog(title: String, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("Shoot it on black for the best look.") },
        confirmButton = { TextButton(onClick = onCamera) { Text("Take photo") } },
        dismissButton = { TextButton(onClick = onGallery) { Text("From gallery") } },
    )
}
