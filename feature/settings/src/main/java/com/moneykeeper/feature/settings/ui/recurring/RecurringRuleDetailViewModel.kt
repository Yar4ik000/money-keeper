package com.moneykeeper.feature.settings.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecurringRuleDetailViewModel @Inject constructor(
    private val recurringRuleRepo: RecurringRuleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val ruleId: Long = checkNotNull(savedStateHandle["ruleId"])

    private val _item = MutableStateFlow<RecurringRuleWithTemplate?>(null)
    val item: StateFlow<RecurringRuleWithTemplate?> = _item.asStateFlow()

    private val _frequency = MutableStateFlow(Frequency.MONTHLY)
    val frequency: StateFlow<Frequency> = _frequency.asStateFlow()

    private val _intervalInput = MutableStateFlow("1")
    val intervalInput: StateFlow<String> = _intervalInput.asStateFlow()

    private val _endDate = MutableStateFlow<LocalDate?>(null)
    val endDate: StateFlow<LocalDate?> = _endDate.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    init {
        viewModelScope.launch {
            val found = recurringRuleRepo.getByIdWithTemplate(ruleId)
            if (found == null) {
                _navigateBack.value = true
                return@launch
            }
            _item.value = found
            _frequency.value = found.rule.frequency
            _intervalInput.value = found.rule.interval.toString()
            _endDate.value = found.rule.endDate
        }
    }

    fun onFrequencyChange(f: Frequency) { _frequency.value = f }

    fun onIntervalChange(v: String) {
        if (v.all { it.isDigit() }) _intervalInput.value = v
    }

    fun onEndDateChange(date: LocalDate?) { _endDate.value = date }

    fun save() = viewModelScope.launch {
        val rule = _item.value?.rule ?: return@launch
        val interval = _intervalInput.value.toIntOrNull()?.coerceAtLeast(1) ?: 1
        recurringRuleRepo.save(
            rule.copy(
                frequency = _frequency.value,
                interval = interval,
                endDate = _endDate.value,
            )
        )
        _navigateBack.value = true
    }

    fun stop() = viewModelScope.launch {
        recurringRuleRepo.delete(ruleId)
        _navigateBack.value = true
    }
}
