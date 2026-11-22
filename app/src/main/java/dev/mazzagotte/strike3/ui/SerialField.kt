package dev.mazzagotte.strike3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Serial number as two number boxes with a fixed slash between them: [049] / [199].
 * The number pad has no slash key, so the slash is built in and the value is saved as "049/199".
 * Leading zeros are kept exactly as typed.
 */
@Composable
fun SerialField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val (num, of) = splitSerial(value)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = num,
            onValueChange = { onChange(joinSerial(digits(it), of)) },
            label = { Text("Serial #") },
            placeholder = { Text("049") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        Text("/", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp))
        OutlinedTextField(
            value = of,
            onValueChange = { onChange(joinSerial(num, digits(it))) },
            label = { Text("Out of") },
            placeholder = { Text("199") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun digits(s: String) = s.filter { it.isDigit() }.take(5)

/** "049/199" -> ("049", "199"). Older entries without a slash land in the first box. */
fun splitSerial(s: String): Pair<String, String> {
    val parts = s.split('/', limit = 2)
    return digits(parts[0]) to digits(parts.getOrElse(1) { "" })
}

/** ("049", "199") -> "049/199"; empty when both boxes are empty. */
fun joinSerial(num: String, of: String): String = when {
    num.isEmpty() && of.isEmpty() -> ""
    of.isEmpty() -> num
    else -> "$num/$of"
}
