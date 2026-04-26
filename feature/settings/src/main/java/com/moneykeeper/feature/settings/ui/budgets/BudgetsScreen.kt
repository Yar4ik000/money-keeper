package com.moneykeeper.feature.settings.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.ui.util.categoryIconVector
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.settings.R
import java.math.BigDecimal
import kotlin.math.roundToInt

private val SUPPORTED_CURRENCIES = listOf("RUB", "USD", "EUR", "GBP", "CNY", "BYN", "KZT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    var budgetPendingDelete by remember { mutableStateOf<Budget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.budgets_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingBudget = null; dialogOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.budget.id }) { item ->
                BudgetCard(
                    item = item,
                    globalWarning = state.defaultWarningThreshold,
                    globalCritical = state.defaultCriticalThreshold,
                    onEdit = { editingBudget = item.budget; dialogOpen = true },
                    onDelete = { budgetPendingDelete = item.budget },
                )
            }
        }
    }

    if (dialogOpen) {
        BudgetDialog(
            categories = state.categories,
            accounts = state.accounts,
            existing = editingBudget,
            globalWarning = state.defaultWarningThreshold,
            globalCritical = state.defaultCriticalThreshold,
            onConfirm = { budget ->
                viewModel.save(budget)
                dialogOpen = false
                editingBudget = null
            },
            onDismiss = { dialogOpen = false; editingBudget = null },
        )
    }

    budgetPendingDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetPendingDelete = null },
            title = { Text(stringResource(R.string.budgets_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.budgets_delete_confirm_message, state.items
                    .firstOrNull { it.budget.id == budget.id }
                    ?.categoryNames ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(budget.id)
                    budgetPendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun BudgetCard(
    item: BudgetWithSpent,
    globalWarning: Int,
    globalCritical: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rawRatio = if (item.budget.amount > BigDecimal.ZERO)
        item.spent.toDouble() / item.budget.amount.toDouble()
    else 0.0
    val progress = rawRatio.toFloat().coerceIn(0f, 1f)
    val percent = (rawRatio * 100).roundToInt()

    val warning = item.budget.warningThreshold ?: globalWarning
    val critical = item.budget.criticalThreshold ?: globalCritical
    val barColor = when {
        percent >= critical -> Color(0xFFD32F2F) // red
        percent >= warning  -> Color(0xFFF9A825) // yellow/amber
        else                -> Color(0xFF4CAF50) // green
    }
    val percentColor = when {
        percent >= critical -> Color(0xFFD32F2F)
        percent >= warning  -> Color(0xFFF9A825)
        else                -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.categoryNames, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = item.accountNames,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${item.spent.toPlainString()} / ${item.budget.amount.toPlainString()} ${item.budget.currency}")
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = percentColor,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDialog(
    categories: List<Category>,
    accounts: List<Account>,
    existing: Budget?,
    globalWarning: Int,
    globalCritical: Int,
    onConfirm: (Budget) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable(existing) { mutableStateOf(existing?.amount?.toPlainString() ?: "") }
    var period by remember(existing) { mutableStateOf(existing?.period ?: BudgetPeriod.MONTHLY) }
    var periodExpanded by remember { mutableStateOf(false) }
    var currency by remember(existing) { mutableStateOf(existing?.currency ?: "RUB") }
    var currencyExpanded by remember { mutableStateOf(false) }
    var warningInput by rememberSaveable(existing) {
        mutableStateOf(existing?.warningThreshold?.toString() ?: "")
    }
    var criticalInput by rememberSaveable(existing) {
        mutableStateOf(existing?.criticalThreshold?.toString() ?: "")
    }
    val filteredAccounts = accounts.filter { it.currency == currency }

    // Category multi-select
    val initialAllCategories = remember(existing) { existing?.categoryIds?.isEmpty() ?: true }
    var allCategories by remember(existing) { mutableStateOf(initialAllCategories) }
    val initialSelectedCatIds = remember(existing) { existing?.categoryIds ?: emptySet() }
    var selectedCategoryIds by remember(existing) { mutableStateOf(initialSelectedCatIds) }

    // Account multi-select
    val initialAllAccounts = remember(existing) { existing?.accountIds?.isEmpty() ?: true }
    var allAccounts by remember(existing) { mutableStateOf(initialAllAccounts) }
    val initialSelectedAccIds = remember(existing) { existing?.accountIds ?: emptySet() }
    var selectedAccountIds by remember(existing) { mutableStateOf(initialSelectedAccIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) stringResource(R.string.budgets_add) else stringResource(R.string.budgets_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── Amount ─────────────────────────────────────────────────────
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.budgets_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // ── Period ─────────────────────────────────────────────────────
                ExposedDropdownMenuBox(
                    expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (period == BudgetPeriod.MONTHLY)
                            stringResource(R.string.budgets_period_monthly)
                        else
                            stringResource(R.string.budgets_period_weekly),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.budgets_period)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.budgets_period_monthly)) },
                            onClick = { period = BudgetPeriod.MONTHLY; periodExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.budgets_period_weekly)) },
                            onClick = { period = BudgetPeriod.WEEKLY; periodExpanded = false },
                        )
                    }
                }

                // ── Currency ───────────────────────────────────────────────────
                ExposedDropdownMenuBox(
                    expanded = currencyExpanded,
                    onExpandedChange = { currencyExpanded = it },
                ) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.budgets_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                        SUPPORTED_CURRENCIES.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code) },
                                onClick = {
                                    if (code != currency) {
                                        currency = code
                                        val newAllowed = accounts.filter { it.currency == code }.map { it.id }.toSet()
                                        selectedAccountIds = selectedAccountIds.intersect(newAllowed)
                                    }
                                    currencyExpanded = false
                                },
                            )
                        }
                    }
                }

                // ── Thresholds (optional per-budget override) ─────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = warningInput,
                        onValueChange = { v -> if (v.all { it.isDigit() }) warningInput = v },
                        label = { Text(stringResource(R.string.budgets_warning_threshold)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = criticalInput,
                        onValueChange = { v -> if (v.all { it.isDigit() }) criticalInput = v },
                        label = { Text(stringResource(R.string.budgets_critical_threshold)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.budgets_threshold_global_hint, globalWarning, globalCritical),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── Categories ─────────────────────────────────────────────────
                if (categories.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.budgets_category),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = allCategories,
                            onCheckedChange = { checked ->
                                allCategories = checked
                                if (checked) selectedCategoryIds = emptySet()
                            },
                        )
                        Text(stringResource(R.string.budgets_all_categories))
                    }
                    if (!allCategories) {
                        categories.forEach { category ->
                            val accentColor = parseHexColor(category.colorHex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            ) {
                                Checkbox(
                                    checked = category.id in selectedCategoryIds,
                                    onCheckedChange = { checked ->
                                        selectedCategoryIds = if (checked)
                                            selectedCategoryIds + category.id
                                        else
                                            selectedCategoryIds - category.id
                                    },
                                )
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(accentColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = categoryIconVector(category.iconName),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(category.name)
                            }
                        }
                    }
                }

                // ── Accounts ───────────────────────────────────────────────────
                if (filteredAccounts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.budgets_accounts),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = allAccounts,
                            onCheckedChange = { checked ->
                                allAccounts = checked
                                if (checked) selectedAccountIds = emptySet()
                            },
                        )
                        Text(stringResource(R.string.budgets_all_accounts))
                    }
                    if (!allAccounts) {
                        filteredAccounts.forEach { account ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            ) {
                                Checkbox(
                                    checked = account.id in selectedAccountIds,
                                    onCheckedChange = { checked ->
                                        selectedAccountIds = if (checked)
                                            selectedAccountIds + account.id
                                        else
                                            selectedAccountIds - account.id
                                    },
                                )
                                Text(account.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.toBigDecimalOrNull() ?: return@TextButton
                    val catIds = if (allCategories) emptySet() else selectedCategoryIds
                    val accIds = if (allAccounts) emptySet() else selectedAccountIds
                    onConfirm(
                        Budget(
                            id = existing?.id ?: 0,
                            categoryIds = catIds,
                            amount = amt,
                            period = period,
                            currency = currency,
                            accountIds = accIds,
                            warningThreshold = warningInput.toIntOrNull()?.coerceIn(0, 999),
                            criticalThreshold = criticalInput.toIntOrNull()?.coerceIn(0, 999),
                        )
                    )
                },
                enabled = amount.toBigDecimalOrNull() != null,
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
