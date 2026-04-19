package com.moneykeeper.feature.transactions.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.transactions.R
import com.moneykeeper.feature.transactions.ui.add.LocalDatePickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleSheet(
    rule: RecurringRule?,
    startDate: LocalDate,
    onConfirm: (RecurringRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var frequency by remember { mutableStateOf(rule?.frequency ?: Frequency.MONTHLY) }
    var intervalInput by remember { mutableStateOf(rule?.interval?.toString() ?: "1") }
    var endDate by remember { mutableStateOf(rule?.endDate) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.recurring_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.recurring_frequency),
                style = MaterialTheme.typography.labelLarge,
            )
            Frequency.entries.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { frequency = f }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = frequency == f, onClick = { frequency = f })
                    Text(
                        text = stringResource(frequencyRes(f)),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            OutlinedTextField(
                value = intervalInput,
                onValueChange = { v -> if (v.all { it.isDigit() }) intervalInput = v },
                label = { Text(stringResource(R.string.recurring_interval)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Box(Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = endDate?.format(dateFormatter)
                        ?: stringResource(R.string.recurring_no_end),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.recurring_end_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showEndDatePicker = true })
            }

            Button(
                onClick = {
                    val interval = intervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onConfirm(
                        RecurringRule(
                            id = rule?.id ?: 0L,
                            frequency = frequency,
                            interval = interval,
                            startDate = startDate,
                            endDate = endDate,
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.recurring_confirm))
            }
        }
    }

    if (showEndDatePicker) {
        LocalDatePickerDialog(
            initial = endDate ?: startDate.plusMonths(1),
            onConfirm = { endDate = it; showEndDatePicker = false },
            onDismiss = { showEndDatePicker = false },
        )
    }
}

internal fun frequencyRes(f: Frequency): Int = when (f) {
    Frequency.DAILY -> R.string.recurring_freq_daily
    Frequency.WEEKLY -> R.string.recurring_freq_weekly
    Frequency.MONTHLY -> R.string.recurring_freq_monthly
    Frequency.YEARLY -> R.string.recurring_freq_yearly
}
