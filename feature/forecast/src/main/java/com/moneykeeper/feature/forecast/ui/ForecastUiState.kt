package com.moneykeeper.feature.forecast.ui

import com.moneykeeper.core.domain.forecast.ForecastResult
import java.time.LocalDate

sealed interface ForecastUiState {
    data object Idle : ForecastUiState
    data object Loading : ForecastUiState
    data class Success(val result: ForecastResult, val selectedDate: LocalDate) : ForecastUiState
    data class Error(val code: ForecastErrorCode, val cause: Throwable? = null) : ForecastUiState
}

enum class ForecastErrorCode { CalculationFailed, DataUnavailable }
