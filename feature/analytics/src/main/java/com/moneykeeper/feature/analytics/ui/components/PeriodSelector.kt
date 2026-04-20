package com.moneykeeper.feature.analytics.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.analytics.R
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun PeriodSelector(
    period: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: ((YearMonth) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(AppLocale.current())
    var showPicker by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
        }
        Text(
            text = period.format(formatter),
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onJump != null) Modifier.clickable { showPicker = true }
                    else Modifier,
                ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                textDecoration = if (onJump != null) TextDecoration.Underline else TextDecoration.None,
            ),
        )
        IconButton(onClick = onNext, enabled = period < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }

    if (showPicker && onJump != null) {
        YearMonthPickerDialog(
            current = period,
            onDismiss = { showPicker = false },
            onConfirm = { ym ->
                onJump(ym)
                showPicker = false
            },
        )
    }
}

@Composable
private fun YearMonthPickerDialog(
    current: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    val locale = AppLocale.current()
    val now = YearMonth.now()
    var year by remember { mutableIntStateOf(current.year) }
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.period_picker_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { year++ },
                        enabled = year < now.year,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(12) { idx ->
                        val month = Month.of(idx + 1)
                        val ym = YearMonth.of(year, month)
                        val isFuture = ym > now
                        val isSelected = ym == selected
                        val label = month.getDisplayName(TextStyle.SHORT, locale)
                            .replaceFirstChar { it.uppercase(locale) }
                        TextButton(
                            onClick = { if (!isFuture) selected = ym },
                            enabled = !isFuture,
                        ) {
                            Text(
                                text = label,
                                style = if (isSelected)
                                    MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary)
                                else
                                    MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.period_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.period_picker_cancel))
            }
        },
    )
}
