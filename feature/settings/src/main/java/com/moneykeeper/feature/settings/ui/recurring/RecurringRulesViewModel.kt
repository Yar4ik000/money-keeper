package com.moneykeeper.feature.settings.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecurringRulesViewModel @Inject constructor(
    private val recurringRuleRepo: RecurringRuleRepository,
) : ViewModel() {

    val rules: StateFlow<List<RecurringRuleWithTemplate>> = recurringRuleRepo
        .observeAllWithTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    val isSelectionMode: Boolean get() = _selectedIds.value.isNotEmpty()

    fun enterSelectionMode(ruleId: Long) {
        _selectedIds.update { it + ruleId }
    }

    fun toggleSelection(ruleId: Long) {
        _selectedIds.update { ids -> if (ruleId in ids) ids - ruleId else ids + ruleId }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun stopSelected() = viewModelScope.launch {
        val ids = _selectedIds.value
        clearSelection()
        ids.forEach { recurringRuleRepo.delete(it) }
    }
}
