package com.moneykeeper.feature.transactions.ui.edit

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.res.stringResource
import com.moneykeeper.feature.transactions.R
import com.moneykeeper.feature.transactions.ui.add.AddTransactionScreen

@Composable
fun EditTransactionRoute(
    viewModel: EditTransactionViewModel = hiltViewModel(),
    navBackStackEntry: NavBackStackEntry,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onAddAccountFromPicker: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showStopSeriesDialog by viewModel.showStopSeriesDialog.collectAsStateWithLifecycle()

    if (showStopSeriesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onRecurringUncheckConfirm(false) },
            title = { Text(stringResource(R.string.recurring_stop_series_title)) },
            text = { Text(stringResource(R.string.recurring_uncheck_warning)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onRecurringUncheckConfirm(true) }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onRecurringUncheckConfirm(false) }) {
                    Text(stringResource(R.string.recurring_go_back))
                }
            },
        )
    }

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
