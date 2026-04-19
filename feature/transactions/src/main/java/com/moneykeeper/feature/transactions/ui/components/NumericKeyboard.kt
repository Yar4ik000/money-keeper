package com.moneykeeper.feature.transactions.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.feature.transactions.R
import com.moneykeeper.feature.transactions.ui.add.KeyboardKey

@Composable
fun NumericKeyboard(
    onKey: (KeyboardKey) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DigitKey("7", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("7")) }
            DigitKey("8", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("8")) }
            DigitKey("9", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("9")) }
            IconKey(Modifier.weight(1f), onClick = { onKey(KeyboardKey.Backspace) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DigitKey("4", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("4")) }
            DigitKey("5", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("5")) }
            DigitKey("6", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("6")) }
            EmptyKey(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            DigitKey("1", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("1")) }
            DigitKey("2", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("2")) }
            DigitKey("3", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("3")) }
            EmptyKey(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            DigitKey(".", Modifier.weight(1f)) { onKey(KeyboardKey.Dot) }
            DigitKey("0", Modifier.weight(1f)) { onKey(KeyboardKey.Digit("0")) }
            Button(
                onClick = onOk,
                modifier = Modifier
                    .weight(2f)
                    .height(48.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    text = stringResource(R.string.keyboard_ok),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun DigitKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun IconKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun EmptyKey(modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = {},
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        enabled = false,
    ) {}
}
