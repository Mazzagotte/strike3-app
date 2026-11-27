package dev.mazzagotte.strike3.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.mazzagotte.strike3.util.CardText

/**
 * Numbered cards: only the print run matters ("/199"), not which copy it is.
 * One number box with the slash built in; saved as "/199" ("1/1" for one-of-ones).
 */
@Composable
fun SerialField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val run = CardText.printRunOnly(value).removePrefix("1/").removePrefix("/")
    OutlinedTextField(
        value = run,
        onValueChange = { typed ->
            val d = typed.filter { it.isDigit() }.trimStart('0').take(5)
            onChange(if (d.isEmpty()) "" else CardText.printRunOnly("/$d"))
        },
        label = { Text("Numbered to") },
        placeholder = { Text("199") },
        prefix = { Text("/") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}
