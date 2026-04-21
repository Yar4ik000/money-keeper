package com.moneykeeper.feature.transactions.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.transactions.ui.add.AddTransactionScreen

@Composable
fun EditTransactionRoute(
    viewModel: EditTransactionViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    )
}
