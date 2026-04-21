package com.moneykeeper.feature.auth.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    DigitButton(digit, onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // empty placeholder for alignment
            Spacer(Modifier.size(72.dp))
            DigitButton('0', onClick = { onDigit('0') })
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.width(120.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null)
        }
    }
}

@Composable
private fun DigitButton(digit: Char, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(digit.toString(), fontSize = 22.sp)
    }
}
