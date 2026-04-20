package com.moneykeeper.feature.analytics.ui.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.analytics.R
import com.moneykeeper.feature.analytics.ui.components.CategoryTrendBarChart
import com.moneykeeper.feature.analytics.ui.components.PeriodSelector
import com.moneykeeper.feature.analytics.ui.components.TransactionHistoryItem

@Composable
fun CategoryAnalyticsRoute(
    onTransactionClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryAnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryAnalyticsScreen(
        uiState = uiState,
        onPrevPeriod = viewModel::prevPeriod,
        onNextPeriod = viewModel::nextPeriod,
        onTransactionClick = onTransactionClick,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAnalyticsScreen(
    uiState: CategoryAnalyticsUiState,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.category?.name ?: stringResource(R.string.category_analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                PeriodSelector(
                    period = uiState.period,
                    onPrev = onPrevPeriod,
                    onNext = onNextPeriod,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.category_analytics_total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = uiState.totalAmount.formatAsCurrency("RUB"),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            if (uiState.monthlyTrend.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.category_analytics_monthly_trend),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    CategoryTrendBarChart(
                        data = uiState.monthlyTrend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            if (uiState.transactions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.category_analytics_transactions),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(uiState.transactions, key = { it.transaction.id }) { meta ->
                    TransactionHistoryItem(
                        meta = meta,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onTransactionClick(meta.transaction.id) },
                        onLongClick = {},
                    )
                }
            } else {
                item {
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
        }
    }
}
