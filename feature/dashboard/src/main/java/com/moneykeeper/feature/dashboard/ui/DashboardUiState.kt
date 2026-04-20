package com.moneykeeper.feature.dashboard.ui

import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import java.math.BigDecimal

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalsByCurrency: MultiCurrencyTotal = MultiCurrencyTotal(emptyList()),
    val accounts: List<Account> = emptyList(),
    val monthlySummary: List<PeriodSummaryByCurrency> = emptyList(),
    val recentTransactions: List<TransactionWithMeta> = emptyList(),
    val expiringDeposits: List<DepositWithDaysLeft> = emptyList(),
)

data class DepositWithDaysLeft(
    val deposit: Deposit,
    val accountName: String,
    val daysLeft: Int,
    val projectedAmount: BigDecimal,
)
