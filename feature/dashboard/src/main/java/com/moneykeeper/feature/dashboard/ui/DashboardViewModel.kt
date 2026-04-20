package com.moneykeeper.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val depositRepo: DepositRepository,
) : ViewModel() {

    val uiState = combine(
        accountRepo.observeActiveAccounts(),
        accountRepo.observeTotalsByCurrency(),
        transactionRepo.observePeriodSummary(
            from = YearMonth.now().atDay(1),
            to = YearMonth.now().atEndOfMonth(),
        ),
        transactionRepo.observeRecent(limit = 5),
        depositRepo.observeExpiringSoon(daysThreshold = 30),
    ) { accounts, totals, summary, recent, expiring ->
        DashboardUiState(
            isLoading = false,
            totalsByCurrency = totals,
            accounts = accounts,
            monthlySummary = summary,
            recentTransactions = recent,
            expiringDeposits = expiring.map { deposit ->
                DepositWithDaysLeft(
                    deposit = deposit,
                    accountName = accounts.find { it.id == deposit.accountId }?.name ?: "",
                    daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), deposit.endDate).toInt()
                        .coerceAtLeast(0),
                    projectedAmount = DepositCalculator.projectedBalance(deposit, deposit.endDate),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
