package com.moneykeeper.feature.forecast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.feature.forecast.domain.ForecastEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val depositRepo: DepositRepository,
    private val recurringRuleRepo: RecurringRuleRepository,
    private val forecastEngine: ForecastEngine,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now().plusMonths(3))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Idle)
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    init { recalculate() }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        recalculate()
    }

    private fun recalculate() = viewModelScope.launch {
        _uiState.value = ForecastUiState.Loading
        try {
            val accounts = accountRepo.observeActiveAccounts().first()
            val deposits = depositRepo.observeAll().first().filter { it.isActive }
            val rules = recurringRuleRepo.observeAllWithTemplates().first()
            val result = forecastEngine.calculate(
                accounts = accounts,
                deposits = deposits,
                recurringRules = rules,
                targetDate = _selectedDate.value,
            )
            _uiState.value = ForecastUiState.Success(result, _selectedDate.value)
        } catch (e: Exception) {
            _uiState.value = ForecastUiState.Error(ForecastErrorCode.CalculationFailed, e)
        }
    }
}
