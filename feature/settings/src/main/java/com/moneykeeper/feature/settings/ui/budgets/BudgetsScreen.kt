package com.moneykeeper.feature.settings.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.feature.settings.R
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // null = closed, non-null = open (null budget = new, non-null budget = edit)
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
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
                    onEdit = { editingBudget = item.budget; dialogOpen = true },
                    onDelete = { viewModel.delete(item.budget.id) },
                )
            }
        }
    }

    if (dialogOpen) {
        BudgetDialog(
            categories = state.categories,
            existing = editingBudget,
            onConfirm = { budget ->
                viewModel.save(budget)
                dialogOpen = false
                editingBudget = null
            },
            onDismiss = { dialogOpen = false; editingBudget = null },
        )
    }
}

@Composable
private fun BudgetCard(
    item: BudgetWithSpent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = if (item.budget.amount > BigDecimal.ZERO)
        (item.spent / item.budget.amount).toFloat().coerceIn(0f, 1f)
    else 0f
    val overBudget = item.spent > item.budget.amount

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.category?.name ?: "—",
                    modifier = Modifier.weight(1f),
                )
                Text("${item.spent.toPlainString()} / ${item.budget.amount.toPlainString()}")
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
                color = if (overBudget) Color(0xFFD32F2F) else Color(0xFF4CAF50),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDialog(
    categories: List<Category>,
    existing: Budget?,
    onConfirm: (Budget) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialCategory = remember(existing, categories) {
        if (existing != null) categories.find { it.id == existing.categoryId }
        else categories.firstOrNull()
    }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var amount by rememberSaveable(existing) { mutableStateOf(existing?.amount?.toPlainString() ?: "") }
    var period by remember(existing) { mutableStateOf(existing?.period ?: BudgetPeriod.MONTHLY) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) stringResource(R.string.budgets_add) else stringResource(R.string.budgets_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.budgets_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategory = cat; categoryExpanded = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.budgets_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cat = selectedCategory ?: return@TextButton
                    val amt = amount.toBigDecimalOrNull() ?: return@TextButton
                    onConfirm(
                        Budget(
                            id = existing?.id ?: 0,
                            categoryId = cat.id,
                            amount = amt,
                            period = period,
                            currency = existing?.currency ?: "RUB",
                        )
                    )
                },
                enabled = selectedCategory != null && amount.toBigDecimalOrNull() != null,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
