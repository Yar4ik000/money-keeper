package com.moneykeeper.feature.accounts.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.model.DepositEventType
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.ui.components.DeleteConfirmDialog
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.accounts.ui.edit.LocalDatePickerDialog
import com.moneykeeper.feature.accounts.R
import com.moneykeeper.feature.accounts.ui.list.formatAmount
import com.moneykeeper.feature.accounts.ui.list.parseColor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    viewModel: AccountDetailViewModel = hiltViewModel(),
    onEditClick: () -> Unit,
    onTransferClick: () -> Unit,
    onBack: () -> Unit,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val deposit by viewModel.deposit.collectAsStateWithLifecycle()
    val depositEvents by viewModel.depositEvents.collectAsStateWithLifecycle()
    val selectedEventIds by viewModel.selectedEventIds.collectAsStateWithLifecycle()
    val isEventSelectionMode by viewModel.isEventSelectionMode.collectAsStateWithLifecycle()

    var historyExpanded by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showDeleteEventsConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isEventSelectionMode) { viewModel.clearEventSelection() }

    val isDepositType = account?.type == AccountType.DEPOSIT || account?.type == AccountType.SAVINGS

    if (showTopUpDialog) {
        ManualAdjustDialog(
            title = stringResource(R.string.deposit_manual_topup_title),
            confirmLabel = stringResource(R.string.deposit_manual_topup_confirm),
            onConfirm = { amount, date, note ->
                viewModel.topUp(amount, date, note)
                showTopUpDialog = false
            },
            onDismiss = { showTopUpDialog = false },
        )
    }
    if (showWithdrawDialog) {
        ManualAdjustDialog(
            title = stringResource(R.string.deposit_manual_withdraw_title),
            confirmLabel = stringResource(R.string.deposit_manual_withdraw_confirm),
            onConfirm = { amount, date, note ->
                viewModel.withdraw(amount, date, note)
                showWithdrawDialog = false
            },
            onDismiss = { showWithdrawDialog = false },
        )
    }

    if (showDeleteEventsConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.deposit_event_delete_title),
            body = stringResource(R.string.deposit_event_delete_text),
            confirmLabel = stringResource(R.string.deposit_event_delete_action, selectedEventIds.size),
            cancelLabel = stringResource(R.string.common_cancel),
            onConfirm = { showDeleteEventsConfirm = false; viewModel.deleteSelectedEvents() },
            onDismiss = { showDeleteEventsConfirm = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name ?: stringResource(R.string.account_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onTransferClick) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.account_detail_transfer))
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_detail_edit))
                    }
                },
            )
        },
        bottomBar = {
            if (isEventSelectionMode && selectedEventIds.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { viewModel.clearEventSelection() }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = { showDeleteEventsConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.deposit_event_delete_action, selectedEventIds.size))
                        }
                    }
                }
            }
        },
    ) { padding ->
        val acc = account
        if (acc == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { AccountSummaryCard(account = acc, deposit = deposit) }

            if (isDepositType && deposit != null) {
                item {
                    DepositManualButtons(
                        showWithdraw = true,
                        onTopUp = { showTopUpDialog = true },
                        onWithdraw = { showWithdrawDialog = true },
                    )
                }
                item {
                    DepositHistoryHeader(
                        count = depositEvents.size,
                        expanded = historyExpanded,
                        onToggle = { historyExpanded = !historyExpanded },
                    )
                }
                if (historyExpanded) {
                    val principalEvents = depositEvents.filter {
                        it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW
                    }
                    val interestEvents = depositEvents.filter {
                        it.type == DepositEventType.INTEREST_ACCRUAL || it.type == DepositEventType.CAPITALIZATION
                    }

                    if (principalEvents.isNotEmpty()) {
                        item {
                            DepositEventsSectionHeader(stringResource(R.string.deposit_movements_title))
                        }
                        items(principalEvents, key = { "pe_${it.id}" }) { event ->
                            DepositEventRow(
                                event = event,
                                currency = acc.currency,
                                isSelectionMode = isEventSelectionMode,
                                isSelected = event.id in selectedEventIds,
                                onLongClick = { viewModel.enterEventSelectionMode(event.id) },
                                onToggleSelection = { viewModel.toggleEventSelection(event.id) },
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    if (interestEvents.isNotEmpty()) {
                        item {
                            DepositEventsSectionHeader(stringResource(R.string.deposit_accruals_title))
                        }
                        items(interestEvents, key = { "ie_${it.id}" }) { event ->
                            DepositEventRow(
                                event = event,
                                currency = acc.currency,
                                isSelectionMode = false,
                                isSelected = false,
                                onLongClick = null,
                                onToggleSelection = null,
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.account_detail_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (transactions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.account_detail_no_transactions),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(transactions, key = { "tx_${it.transaction.id}" }) { txMeta ->
                    TransactionRow(txMeta = txMeta, currency = acc.currency)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

}

@Composable
private fun AccountSummaryCard(account: Account, deposit: Deposit?) {
    val today = remember { LocalDate.now() }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = parseColor(account.colorHex).copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                account.balance.formatAmount(account.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (account.balance < BigDecimal.ZERO)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface,
            )

            if ((account.type == AccountType.DEPOSIT || account.type == AccountType.SAVINGS) && deposit != null) {
                Spacer(Modifier.height(8.dp))
                val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
                Text(
                    "${stringResource(R.string.deposit_rate)}: ${deposit.interestRate.toPlainString()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${deposit.startDate.format(dateFormatter)}${deposit.endDate?.let { " – ${it.format(dateFormatter)}" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DepositManualButtons(
    showWithdraw: Boolean,
    onTopUp: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onTopUp, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.deposit_manual_topup))
        }
        if (showWithdraw) {
            OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.deposit_manual_withdraw))
            }
        }
    }
}

@Composable
private fun ManualAdjustDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (BigDecimal, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var amountError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initial = selectedDate,
            onConfirm = { selectedDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(R.string.deposit_manual_adjust_hint),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    label = { Text(stringResource(R.string.deposit_manual_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text(stringResource(R.string.error_deposit_event_amount_invalid)) }
                    } else null,
                    singleLine = true,
                )
                Box {
                    OutlinedTextField(
                        value = selectedDate.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.deposit_manual_date_label)) },
                        singleLine = true,
                    )
                    Box(Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.deposit_manual_note_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.replace(",", ".").toBigDecimalOrNull()
                if (amount == null || amount <= BigDecimal.ZERO) { amountError = true; return@Button }
                onConfirm(amount, selectedDate, noteText.trim())
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun DepositEventsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun DepositHistoryHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.deposit_events_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DepositEventRow(
    event: DepositEvent,
    currency: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: (() -> Unit)?,
    onToggleSelection: (() -> Unit)?,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    val isPositive = event.amount.signum() >= 0
    val amountColor = if (isPositive) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
    val sign = if (isPositive) "+" else ""
    val typeLabel = when (event.type) {
        DepositEventType.PRINCIPAL_ADD -> stringResource(R.string.deposit_event_principal_add)
        DepositEventType.PRINCIPAL_WITHDRAW -> stringResource(R.string.deposit_event_principal_withdraw)
        DepositEventType.INTEREST_ACCRUAL -> stringResource(R.string.deposit_event_interest_accrual)
        DepositEventType.CAPITALIZATION -> stringResource(R.string.deposit_event_capitalization)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode && onToggleSelection != null) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(typeLabel, style = MaterialTheme.typography.bodyMedium)
            if (event.note.isNotBlank()) {
                Text(event.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(event.date.format(dateFormatter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "$sign${event.amount.abs().formatAmount(currency)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = amountColor,
        )
    }
}

@Composable
private fun TransactionRow(txMeta: TransactionWithMeta, currency: String) {
    val tx = txMeta.transaction
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    val isExpense = tx.type == TransactionType.EXPENSE || tx.type == TransactionType.TRANSFER
    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF388E3C)
    val sign = if (isExpense) "−" else "+"

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                txMeta.categoryName.ifBlank { tx.type.name },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tx.note.isNotBlank()) {
                Text(tx.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(tx.date.format(dateFormatter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "$sign${tx.amount.formatAmount(currency)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = amountColor,
        )
    }
}
