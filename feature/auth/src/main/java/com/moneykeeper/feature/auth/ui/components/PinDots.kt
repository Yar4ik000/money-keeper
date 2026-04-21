package com.moneykeeper.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PinDots(count: Int, maxLength: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxLength) { index ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (index < count) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(16.dp),
            ) {}
        }
    }
}
