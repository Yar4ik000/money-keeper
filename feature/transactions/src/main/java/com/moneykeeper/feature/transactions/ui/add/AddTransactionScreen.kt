package com.moneykeeper.feature.transactions.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.transactions.R
import com.moneykeeper.core.ui.util.AmountTextField
import com.moneykeeper.feature.transactions.ui.components.AccountPicker
import com.moneykeeper.feature.transactions.ui.components.CategoryPicker
import com.moneykeeper.feature.transactions.ui.components.RecurringRuleSheet
import com.moneykeeper.feature.transactions.ui.components.TransactionTypeSelector
import com.moneykeeper.feature.transactions.ui.components.frequencyRes
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AddTransactionRoute(
    viewModel: AddTransactionViewModel = hiltViewModel(),
    navBackStackEntry: NavBackStackEntry,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onAddAccountFromPicker: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val newCategoryId by navBackStackEntry.savedStateHandle
        .getStateFlow("newCategoryId", -1L)
        .collectAsStateWithLifecycle()
    val newAccountId by navBackStackEntry.savedStateHandle
        .getStateFlow("newAccountId", -1L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(newCategoryId) {
        if (newCategoryId != -1L) {
            viewModel.onCategoryCreated(newCategoryId)
            navBackStackEntry.savedStateHandle.remove<Long>("newCategoryId")
        }
    }
    LaunchedEffect(newAccountId) {
        if (newAccountId != -1L) {
            viewModel.onAccountCreated(newAccountId)
            navBackStackEntry.savedStateHandle.remove<Long>("newAccountId")
        }
    }

    AddTransactionScreen(
        uiState = state,
        onTypeChange = viewModel::onTypeChange,
        onAmountInputChange = viewModel::onAmountInputChange,
        onAccountSelect = viewModel::onAccountSelect,
        onToAccountSelect = viewModel::onToAccountSelect,
        onCategorySelect = viewModel::onCategorySelect,
        onDateChange = viewModel::onDateChange,
        onNoteChange = viewModel::onNoteChange,
        onRecurringToggle = viewModel::onRecurringToggle,
        onRecurringRuleChange = viewModel::onRecurringRuleChange,
        onSave = viewModel::onSave,
        onSaved = onSaved,
        onBack = onBack,
        onNavigateToCategories = onNavigateToCategories,
        onAddAccountFromPicker = onAddAccountFromPicker,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    uiState: AddTransactionUiState,
    onTypeChange: (TransactionType) -> Unit,
    onAmountInputChange: (String) -> Unit,
    onAccountSelect: (Account) -> Unit,
    onToAccountSelect: (Account) -> Unit,
    onCategorySelect: (Category) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onRecurringToggle: (Boolean) -> Unit,
    onRecurringRuleChange: (RecurringRule) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onAddAccountFromPicker: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMsg = when (uiState.error) {
        is AddTxError.AmountRequired    -> stringResource(R.string.error_tx_amount_required)
        is AddTxError.AccountRequired   -> stringResource(R.string.error_tx_account_required)
        is AddTxError.ToAccountRequired -> stringResource(R.string.error_tx_to_account_required)
        is AddTxError.CurrencyMismatch  -> stringResource(R.string.error_tx_currency_mismatch)
        null -> null
    }

    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) errorMsg?.let { snackbarHostState.showSnackbar(it) }
    }

    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRecurringSheet by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current())
    }
    val titleRes = when (uiState.type) {
        TransactionType.INCOME -> R.string.tx_type_income
        TransactionType.EXPENSE -> R.string.tx_type_expense
        TransactionType.TRANSFER -> R.string.tx_type_transfer
        TransactionType.SAVINGS -> R.string.tx_type_savings
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.tx_save),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransactionTypeSelector(
                selected = uiState.type,
                onSelect = onTypeChange,
            )

            AmountTextField(
                value = uiState.amountInput,
                onValueChange = onAmountInputChange,
                label = { Text(stringResource(R.string.tx_amount)) },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
            )

            // Account field
            Box {
                OutlinedTextField(
                    value = uiState.selectedAccount?.let { "${it.name} · ${it.currency}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.tx_account)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showAccountPicker = true })
            }

            // ToAccount — only for TRANSFER
            if (uiState.type == TransactionType.TRANSFER) {
                Box {
                    OutlinedTextField(
                        value = uiState.selectedToAccount?.let { "${it.name} · ${it.currency}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tx_to_account)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { showToAccountPicker = true })
                }
            }

            // Category — not for TRANSFER
            if (uiState.type != TransactionType.TRANSFER) {
                Box {
                    OutlinedTextField(
                        value = uiState.selectedCategory?.name ?: stringResource(R.string.tx_category_none),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tx_category)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { showCategoryPicker = true })
                }
            }

            // Date
            Box {
                OutlinedTextField(
                    value = uiState.date.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.tx_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showDatePicker = true })
            }

            // Note
            OutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.tx_note)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                maxLines = 3,
            )

            // Recurring toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tx_recurring),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.isRecurring,
                    onCheckedChange = onRecurringToggle,
                )
            }
            if (uiState.isRecurring) {
                TextButton(
                    onClick = { showRecurringSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = uiState.recurringRule?.let {
                            "${stringResource(frequencyRes(it.frequency))} × ${it.interval}"
                        } ?: stringResource(R.string.recurring_configure),
                    )
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPicker(
            categories = uiState.availableCategories,
            transactionType = uiState.type,
            selectedId = uiState.selectedCategory?.id,
            onSelect = onCategorySelect,
            onDismiss = { showCategoryPicker = false },
            onAddCategory = { showCategoryPicker = false; onNavigateToCategories() },
        )
    }
    if (showAccountPicker) {
        AccountPicker(
            accounts = uiState.availableAccounts,
            selectedId = uiState.selectedAccount?.id,
            onSelect = onAccountSelect,
            onDismiss = { showAccountPicker = false },
            onAddAccount = { showAccountPicker = false; onAddAccountFromPicker() },
        )
    }
    if (showToAccountPicker) {
        AccountPicker(
            accounts = uiState.availableAccounts.filter { it.id != uiState.selectedAccount?.id },
            selectedId = uiState.selectedToAccount?.id,
            title = stringResource(R.string.tx_to_account),
            onSelect = onToAccountSelect,
            onDismiss = { showToAccountPicker = false },
        )
    }
    if (showDatePicker) {
        LocalDatePickerDialog(
            initial = uiState.date,
            onConfirm = { onDateChange(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
            maxDate = java.time.LocalDate.now(),
        )
    }
    if (showRecurringSheet) {
        RecurringRuleSheet(
            rule = uiState.recurringRule,
            startDate = uiState.date,
            onConfirm = onRecurringRuleChange,
            onDismiss = { showRecurringSheet = false },
        )
    }
}

private fun BigDecimal.formatTxAmount(): String {
    val nf = NumberFormat.getNumberInstance(AppLocale.current()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return nf.format(setScale(2, RoundingMode.HALF_EVEN))
}
