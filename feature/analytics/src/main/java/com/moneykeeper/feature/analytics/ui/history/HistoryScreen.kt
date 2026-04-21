package com.moneykeeper.feature.analytics.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onClearSelection: () -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val success = uiState as? HistoryUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (success?.isSelectionMode == true) {
                        Text(pluralStringResource(R.plurals.history_selected_count, success.selectedIds.size, success.selectedIds.size))
                    } else {
                        Text(stringResource(R.string.history_title))
                    }
                },
                navigationIcon = {
                    if (success?.isSelectionMode == true) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (success?.isSelectionMode != true) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = null)
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
                            onClick = { showDeleteConfirm = true },
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
                    if (showSearchBar) {
                        item(key = "search") {
                            OutlinedTextField(
                                value = uiState.filter.query,
                                onValueChange = { q -> onFilterUpdate { it.copy(query = q) } },
                                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            )
                        }
                    }

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
}
