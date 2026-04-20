package com.moneykeeper.feature.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.dashboard.R
import com.moneykeeper.feature.dashboard.ui.components.AccountsCarousel
import com.moneykeeper.feature.dashboard.ui.components.ExpiringDepositsWidget
import com.moneykeeper.feature.dashboard.ui.components.MonthlySummaryCard
import com.moneykeeper.feature.dashboard.ui.components.RecentTransactionsHeader
import com.moneykeeper.feature.dashboard.ui.components.TotalBalanceCard
import com.moneykeeper.feature.dashboard.ui.components.TransactionListItem

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onAddTransaction: (preselectedAccountId: Long?) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onDepositClick: (Long) -> Unit,
    onSettings: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onAccountClick = onAccountClick,
        onAddAccount = onAddAccount,
        onAddTransaction = onAddTransaction,
        onSeeAllTransactions = onSeeAllTransactions,
        onDepositClick = onDepositClick,
        onSettings = onSettings,
        onTransactionClick = onTransactionClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onAddTransaction: (Long?) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onDepositClick: (Long) -> Unit,
    onSettings: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name_dashboard),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddTransaction(null) }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.dashboard_add_transaction),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { TotalBalanceCard(state.totalsByCurrency) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_accounts_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onAddAccount) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(stringResource(R.string.dashboard_add_account))
                    }
                }
            }
            item {
                AccountsCarousel(
                    accounts = state.accounts,
                    onAccountClick = onAccountClick,
                )
            }
            item { MonthlySummaryCard(state.monthlySummary) }
            if (state.expiringDeposits.isNotEmpty()) {
                item {
                    ExpiringDepositsWidget(
                        deposits = state.expiringDeposits,
                        onDepositClick = onDepositClick,
                    )
                }
            }
            item { RecentTransactionsHeader(onSeeAllClick = onSeeAllTransactions) }
            items(state.recentTransactions, key = { it.transaction.id }) { txMeta ->
                TransactionListItem(
                    meta = txMeta,
                    onClick = { onTransactionClick(txMeta.transaction.id) },
                )
            }
        }
    }
}
