package com.moneykeeper.feature.forecast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.forecast.ForecastResult
import com.moneykeeper.feature.forecast.R
import com.moneykeeper.feature.forecast.ui.components.ForecastCurrencyTotals
import com.moneykeeper.feature.forecast.ui.components.ForecastDatePicker
import com.moneykeeper.feature.forecast.ui.components.ForecastSummaryTable
import com.moneykeeper.feature.forecast.ui.components.eventTimeline
import java.time.LocalDate

@Composable
fun ForecastRoute(viewModel: ForecastViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    ForecastScreen(
        uiState = uiState,
        selectedDate = selectedDate,
        onDateSelected = viewModel::onDateSelected,
    )
}

@Composable
fun ForecastScreen(
    uiState: ForecastUiState,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ForecastDatePicker(
            selectedDate = selectedDate,
            onDateSelected = onDateSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        when (uiState) {
            ForecastUiState.Idle, ForecastUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ForecastUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.forecast_error))
                }
            }
            is ForecastUiState.Success -> ForecastContent(result = uiState.result)
        }
    }
}

@Composable
private fun ForecastContent(result: ForecastResult) {
    if (result.accountForecasts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.forecast_no_accounts))
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Currency totals
        result.totalsByCurrency.forEach { total ->
            item(key = "total_${total.currency}") {
                ForecastCurrencyTotals(
                    currency = total.currency,
                    currentBalance = total.currentBalance,
                    forecastedBalance = total.forecastedBalance,
                    delta = total.delta,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Per-account table
        item {
            ForecastSummaryTable(
                forecasts = result.accountForecasts,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Timeline
        if (result.events.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.forecast_events_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val currency = result.totalsByCurrency.firstOrNull()?.currency ?: "RUB"
            eventTimeline(events = result.events, currency = currency)
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.forecast_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
