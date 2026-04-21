package com.moneykeeper.core.ui.util

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * OutlinedTextField wrapper for money/amount inputs. Keeps a local TextFieldValue so that
 * the cursor position survives recompositions driven by external ViewModel state updates
 * (the String-only overload resets the selection to the end every time `value` changes,
 * which makes editing the middle of a formatted number impossible under a
 * VisualTransformation like thousands-separator).
 *
 * @param value plain text from upstream state (digits + optional dot, no separators)
 * @param onValueChange callback with the new plain text
 */
@Composable
fun AmountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    maxFractionDigits: Int = 2,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    // Sync external → local only when the plain text actually changes, so we don't
    // stomp on the user's current selection/caret during normal typing.
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { new ->
            val sanitized = sanitizeAmount(new.text, maxFractionDigits)
            val capped = if (sanitized == new.text) new
                         else new.copy(
                             text = sanitized,
                             selection = TextRange(
                                 new.selection.start.coerceAtMost(sanitized.length),
                                 new.selection.end.coerceAtMost(sanitized.length),
                             ),
                         )
            textFieldValue = capped
            if (capped.text != value) onValueChange(capped.text)
        },
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        readOnly = readOnly,
        enabled = enabled,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = ThousandsVisualTransformation,
        modifier = modifier,
    )
}

private fun sanitizeAmount(input: String, maxFractionDigits: Int): String {
    // Treat comma as decimal separator, keep digits and a single dot, cap fractional part.
    val normalized = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dotIdx = normalized.indexOf('.')
    if (dotIdx < 0) return normalized
    val intPart = normalized.substring(0, dotIdx)
    val fracPart = normalized.substring(dotIdx + 1)
        .filter { it.isDigit() }
        .take(maxFractionDigits)
    return if (maxFractionDigits == 0) intPart else "$intPart.$fracPart"
}
