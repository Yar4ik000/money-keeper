package com.moneykeeper.feature.analytics.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.analytics.R
import com.moneykeeper.feature.analytics.ui.components.PeriodSelector
import com.moneykeeper.feature.analytics.ui.components.TransactionGroupHeader
import com.moneykeeper.feature.analytics.ui.components.TransactionHistoryItem
import java.time.YearMonth

@Composable
fun HistoryRoute(
    onTransactionClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onTransactionClick = onTransactionClick,
        onBack = onBack,
        onFilterUpdate = viewModel::updateFilter,
        onEnterSelectionMode = viewModel::enterSelectionMode,
        onToggleSelection = viewModel::toggleSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onDeleteSelectedStopSeries = viewModel::deleteSelectedStopSeries,
        onDeleteSingleOnly = viewModel::deleteSingleOnly,
        onDeleteSingleThisAndFuture = viewModel::deleteSingleThisAndFuture,
        onDeleteSingleStopSeries = viewModel::deleteSingleStopSeries,
        onClearSelection = viewModel::clearSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onTransactionClick: (Long) -> Unit,
    onBack: () -> Unit,
    onFilterUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteSelectedStopSeries: () -> Unit,
    onDeleteSingleOnly: (TransactionWithMeta) -> Unit = {},
    onDeleteSingleThisAndFuture: (TransactionWithMeta) -> Unit = {},
    onDeleteSingleStopSeries: (TransactionWithMeta) -> Unit = {},
    onClearSelection: () -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRecurringDeleteDialog by remember { mutableStateOf(false) }
    var showScopeDialog by remember { mutableStateOf(false) }
    val success = uiState as? HistoryUiState.Success

    val allItems = success?.groups?.flatMap { it.items } ?: emptyList()

    val singleRecurringSelected: TransactionWithMeta? = if (success?.selectedIds?.size == 1) {
        allItems.find {
            it.transaction.id == success.selectedIds.first() && it.transaction.recurringRuleId != null
        }
    } else null

    val hasRecurringSelected = success != null && allItems
        .any { it.transaction.id in success.selectedIds && it.transaction.recurringRuleId != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        showSearchBar -> {
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            val query = success?.filter?.query ?: ""
                            BasicTextField(
                                value = query,
                                onValueChange = { q -> onFilterUpdate { it.copy(query = q) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                singleLine = true,
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.history_search_hint),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        success?.isSelectionMode == true ->
                            Text(pluralStringResource(R.plurals.history_selected_count, success.selectedIds.size, success.selectedIds.size))
                        else ->
                            Text(stringResource(R.string.history_title))
                    }
                },
                navigationIcon = {
                    when {
                        showSearchBar -> IconButton(onClick = {
                            showSearchBar = false
                            onFilterUpdate { it.copy(query = "") }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        success?.isSelectionMode == true -> IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                        else -> IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    when {
                        showSearchBar -> {
                            val query = success?.filter?.query ?: ""
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onFilterUpdate { it.copy(query = "") } }) {
                                    Icon(Icons.Filled.Close, contentDescription = null)
                                }
                            }
                        }
                        success?.isSelectionMode != true -> {
                            IconButton(onClick = { showSearchBar = true }) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            }
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = null)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (success?.isSelectionMode == true && success.selectedIds.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                when {
                                    singleRecurringSelected != null -> showScopeDialog = true
                                    hasRecurringSelected -> showRecurringDeleteDialog = true
                                    else -> showDeleteConfirm = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.history_delete_selected))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (uiState) {
            is HistoryUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HistoryUiState.Success -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    item(key = "period_selector") {
                        val ym = YearMonth.of(uiState.filter.from.year, uiState.filter.from.month)
                        PeriodSelector(
                            period = ym,
                            onPrev = {
                                onFilterUpdate { f ->
                                    val prev = ym.minusMonths(1)
                                    f.copy(from = prev.atDay(1), to = prev.atEndOfMonth())
                                }
                            },
                            onNext = {
                                onFilterUpdate { f ->
                                    val next = ym.plusMonths(1)
                                    f.copy(from = next.atDay(1), to = next.atEndOfMonth())
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    if (uiState.totalsByCurrency.isNotEmpty()) {
                        item(key = "totals") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                uiState.totalsByCurrency.forEach { summary ->
                                    Text(
                                        text = "+${summary.income.formatAsCurrency(summary.currency)} / -${summary.expense.formatAsCurrency(summary.currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.groups.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.history_empty))
                            }
                        }
                    }

                    uiState.groups.forEach { group ->
                        stickyHeader(key = "header_${group.date}") {
                            TransactionGroupHeader(group = group)
                        }
                        items(group.items, key = { it.transaction.id }) { meta ->
                            TransactionHistoryItem(
                                meta = meta,
                                isSelected = meta.transaction.id in uiState.selectedIds,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) onToggleSelection(meta.transaction.id)
                                    else onTransactionClick(meta.transaction.id)
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) onEnterSelectionMode(meta.transaction.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet && success != null) {
        FilterBottomSheet(
            filter = success.filter,
            accounts = success.availableAccounts,
            categories = success.availableCategories,
            onApply = { newFilter ->
                onFilterUpdate { newFilter }
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showDeleteConfirm && success != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.history_delete_confirm_message, success.selectedIds.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSelected()
                    },
                ) {
                    Text(
                        stringResource(R.string.history_delete_selected),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRecurringDeleteDialog) {
        RecurringDeleteDialog(
            onDeleteSelected = {
                showRecurringDeleteDialog = false
                onDeleteSelected()
            },
            onStopSeries = {
                showRecurringDeleteDialog = false
                onDeleteSelectedStopSeries()
            },
            onDismiss = { showRecurringDeleteDialog = false },
        )
    }

    if (showScopeDialog && singleRecurringSelected != null) {
        DeleteScopeDialog(
            onlyThis = {
                showScopeDialog = false
                onDeleteSingleOnly(singleRecurringSelected)
            },
            thisAndFuture = {
                showScopeDialog = false
                onDeleteSingleThisAndFuture(singleRecurringSelected)
            },
            stopSeries = {
                showScopeDialog = false
                onDeleteSingleStopSeries(singleRecurringSelected)
            },
            onDismiss = { showScopeDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteScopeDialog(
    onlyThis: () -> Unit,
    thisAndFuture: () -> Unit,
    stopSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.history_delete_scope_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onlyThis,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.history_delete_scope_only_this),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.history_delete_scope_only_this_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = thisAndFuture,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.history_delete_scope_this_and_future),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.history_delete_scope_this_and_future_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = stopSeries,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.history_stop_series),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.history_delete_scope_stop_series_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringDeleteDialog(
    onDeleteSelected: () -> Unit,
    onStopSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.history_delete_recurring_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onDeleteSelected,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.history_delete_only_selected),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.history_delete_only_selected_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onStopSeries,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.history_stop_series),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.history_stop_series_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
