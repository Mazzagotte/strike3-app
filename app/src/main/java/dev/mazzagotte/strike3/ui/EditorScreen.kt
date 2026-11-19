package dev.mazzagotte.strike3.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.mazzagotte.strike3.render.PhotoMode
import dev.mazzagotte.strike3.render.PostRenderer

private val TAGS = listOf("" to "Base", "NEW PICKUP" to "New pickup", "PC FAVORITE" to "PC favorite")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onNext: () -> Unit, onSavedEdit: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val editing = vm.editingCard != null
    val ready = vm.source != null && vm.busy == null && !saving
    val primary: () -> Unit = {
        if (editing) {
            saving = true
            scope.launch {
                try {
                    vm.saveEdit()
                    onSavedEdit()
                } catch (e: Exception) {
                    vm.message = "Couldn't save: ${e.message}"
                } finally {
                    saving = false
                }
            }
        } else {
            onNext()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit post" else "Make the post") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = primary, enabled = ready) { Text(if (editing) "Save" else "Next") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PostPreview(vm)

            Section("Photo type") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = vm.mode == PhotoMode.BLACK,
                        onClick = { vm.selectMode(PhotoMode.BLACK) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Shot on black") }
                    SegmentedButton(
                        selected = vm.mode == PhotoMode.CUTOUT,
                        onClick = { vm.selectMode(PhotoMode.CUTOUT) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Auto cutout") }
                }
            }

            Section("Style") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAGS.forEach { (value, label) ->
                        FilterChip(
                            selected = vm.customTag.isBlank() && vm.tag == value,
                            onClick = { vm.selectTag(value) },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = vm.customTag,
                    onValueChange = { vm.customTag = it.take(18) },
                    label = { Text("Custom tag (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Badges") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO" to "Auto", "RELIC" to "Relic", "RC" to "Rookie", "1ST" to "1st").forEach { (value, label) ->
                        FilterChip(
                            selected = value in vm.badges,
                            onClick = { vm.toggleBadge(value) },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = vm.serialBadge,
                    onValueChange = { vm.serialBadge = it.take(10) },
                    label = { Text("Serial number (e.g. 25/50)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Adjust") {
                LabeledSlider("Size", vm.placement.scale, 0.4f..1.8f) { vm.placement = vm.placement.copy(scale = it) }
                if (vm.mode == PhotoMode.BLACK) {
                    LabeledSlider("Edge fade", vm.feather, 0f..200f) { vm.feather = it }
                }
                LabeledSwitch("Auto enhance", vm.enhance) { vm.enhance = it }
                LabeledSlider("Brightness", vm.brightness, 0.7f..1.4f) { vm.brightness = it }
                if (vm.mode == PhotoMode.BLACK) {
                    LabeledSwitch("Hide black backdrop", vm.lighten) { vm.lighten = it }
                }
                LabeledSwitch("Floor reflection", vm.reflect) { vm.reflect = it }
                OutlinedButton(onClick = vm::resetPosition, modifier = Modifier.fillMaxWidth()) { Text("Reset position") }
            }

            Button(
                onClick = primary,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text(if (saving) "Saving…" else if (editing) "Save post" else "Next: card details") }
        }
    }

    vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun PostPreview(vm: EditorViewModel) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PostRenderer.W / PostRenderer.H.toFloat())
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val k = PostRenderer.W / size.width.toFloat()
                    val p = vm.placement
                    vm.placement = p.copy(
                        scale = (p.scale * zoom).coerceIn(0.4f, 1.8f),
                        dx = p.dx + pan.x * k,
                        dy = p.dy + pan.y * k,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        vm.preview?.let {
            Image(it.asImageBitmap(), contentDescription = "Post preview", modifier = Modifier.fillMaxSize())
        }
        vm.busy?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(it, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    Text(
        "Drag to move the card, pinch to resize.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.3f))
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(0.7f))
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
