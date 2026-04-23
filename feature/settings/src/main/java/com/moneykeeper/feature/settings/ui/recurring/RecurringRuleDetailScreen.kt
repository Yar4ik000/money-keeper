package com.moneykeeper.feature.settings.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.settings.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleDetailScreen(
    onBack: () -> Unit,
    viewModel: RecurringRuleDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val frequency by viewModel.frequency.collectAsStateWithLifecycle()
    val intervalInput by viewModel.intervalInput.collectAsStateWithLifecycle()
    val endDate by viewModel.endDate.collectAsStateWithLifecycle()
    val navigateBack by viewModel.navigateBack.collectAsStateWithLifecycle()

    LaunchedEffect(navigateBack) {
        if (navigateBack) onBack()
    }

    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = item != null,
                    ) {
                        Text(stringResource(R.string.recurring_detail_save))
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Context info (read-only)
            item?.let { ruleWithTemplate ->
                Text(
                    text = ruleWithTemplate.description,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (ruleWithTemplate.categoryName.isNotEmpty()) {
                    Text(
                        text = ruleWithTemplate.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = ruleWithTemplate.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.recurring_detail_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Frequency picker
            Text(
                text = stringResource(R.string.recurring_detail_frequency),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Frequency.entries.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onFrequencyChange(f) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = frequency == f,
                        onClick = { viewModel.onFrequencyChange(f) },
                    )
                    Text(
                        text = frequencyDisplayName(f),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Interval field
            OutlinedTextField(
                value = intervalInput,
                onValueChange = viewModel::onIntervalChange,
                label = { Text(stringResource(R.string.recurring_detail_interval)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    val n = intervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    Text(intervalPreview(frequency, n))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // End date field
            Box {
                OutlinedTextField(
                    value = endDate?.format(dateFormatter)
                        ?: stringResource(R.string.recurring_detail_no_end),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.recurring_detail_end_date)) },
                    trailingIcon = {
                        if (endDate != null) {
                            IconButton(onClick = { viewModel.onEndDateChange(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showEndDatePicker = true })
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Stop series button
            Button(
                onClick = { showStopConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recurring_rules_stop_confirm))
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEndDatePicker) {
        val initial = endDate ?: LocalDate.now().plusMonths(1)
        val epochMilli = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochMilli)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        viewModel.onEndDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.recurring_rules_stop_title)) },
            text = { Text(stringResource(R.string.recurring_rules_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirm = false
                        viewModel.stop()
                    },
                ) {
                    Text(
                        stringResource(R.string.recurring_rules_stop_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun frequencyDisplayName(f: Frequency): String = when (f) {
    Frequency.DAILY   -> stringResource(R.string.recurring_freq_label_daily)
    Frequency.WEEKLY  -> stringResource(R.string.recurring_freq_label_weekly)
    Frequency.MONTHLY -> stringResource(R.string.recurring_freq_label_monthly)
    Frequency.YEARLY  -> stringResource(R.string.recurring_freq_label_yearly)
}

@Composable
private fun intervalPreview(frequency: Frequency, n: Int): String = when (frequency) {
    Frequency.DAILY   -> stringResource(R.string.recurring_freq_daily_n, n)
    Frequency.WEEKLY  -> stringResource(R.string.recurring_freq_weekly_n, n)
    Frequency.MONTHLY -> stringResource(R.string.recurring_freq_monthly_n, n)
    Frequency.YEARLY  -> stringResource(R.string.recurring_freq_yearly_n, n)
}
