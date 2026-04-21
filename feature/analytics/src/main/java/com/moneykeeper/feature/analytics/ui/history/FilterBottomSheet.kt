package com.moneykeeper.feature.analytics.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.analytics.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filter: HistoryFilter,
    accounts: List<Account>,
    categories: List<Category>,
    onApply: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.filter_period), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draft.from.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.filter_from)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { showFromPicker = true })
                }
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draft.to.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.filter_to)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { showToPicker = true })
                }
            }

            Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilterChip(
                    label = stringResource(R.string.filter_type_all),
                    selected = draft.type == null,
                    onClick = { draft = draft.copy(type = null) },
                )
                TransactionType.entries.forEach { type ->
                    TypeFilterChip(
                        label = stringResource(type.labelRes),
                        selected = draft.type == type,
                        onClick = { draft = draft.copy(type = type) },
                    )
                }
            }

            if (accounts.isNotEmpty()) {
                Text(stringResource(R.string.filter_account), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeFilterChip(
                        label = stringResource(R.string.filter_any),
                        selected = draft.accountId == null,
                        onClick = { draft = draft.copy(accountId = null) },
                    )
                    accounts.forEach { account ->
                        TypeFilterChip(
                            label = account.name,
                            selected = draft.accountId == account.id,
                            onClick = { draft = draft.copy(accountId = account.id) },
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                Text(stringResource(R.string.filter_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeFilterChip(
                        label = stringResource(R.string.filter_any),
                        selected = draft.categoryId == null,
                        onClick = { draft = draft.copy(categoryId = null) },
                    )
                    categories.forEach { category ->
                        TypeFilterChip(
                            label = category.name,
                            selected = draft.categoryId == category.id,
                            onClick = { draft = draft.copy(categoryId = category.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onApply(HistoryFilter()) }) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(onClick = { onApply(draft) }) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showFromPicker) {
        LocalDatePickerDialog(
            initial = draft.from,
            onConfirm = { date ->
                draft = draft.copy(from = date, to = maxOf(date, draft.to))
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        LocalDatePickerDialog(
            initial = draft.to,
            onConfirm = { date ->
                draft = draft.copy(to = date, from = minOf(date, draft.from))
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
}

@Composable
private fun TypeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private val TransactionType.labelRes: Int
    get() = when (this) {
        TransactionType.INCOME   -> R.string.analytics_tx_income
        TransactionType.EXPENSE  -> R.string.analytics_tx_expense
        TransactionType.TRANSFER -> R.string.analytics_tx_transfer
        TransactionType.SAVINGS  -> R.string.analytics_tx_savings
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val epochMilli = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochMilli)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis ?: return@TextButton
                onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
