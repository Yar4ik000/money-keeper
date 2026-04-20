package com.moneykeeper.core.domain.forecast

import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Deposit
import java.math.BigDecimal
import java.time.LocalDate

data class ForecastResult(
    val targetDate: LocalDate,
    val accountForecasts: List<AccountForecast>,
    val totalsByCurrency: List<ForecastCurrencyTotal>,
    val events: List<TimelineEvent>,
)

data class ForecastCurrencyTotal(
    val currency: String,
    val currentBalance: BigDecimal,
    val forecastedBalance: BigDecimal,
    val delta: BigDecimal,
)

data class AccountForecast(
    val account: Account,
    val currentBalance: BigDecimal,
    val forecastedBalance: BigDecimal,
    val delta: BigDecimal,
)

sealed interface TimelineEvent {
    val date: LocalDate
    val description: String
    val amountDelta: BigDecimal
    val accountName: String
    val accountColorHex: String
    val accountIconName: String

    data class DepositMaturity(
        override val date: LocalDate,
        val deposit: Deposit,
        override val accountName: String,
        override val accountColorHex: String,
        override val accountIconName: String,
        val maturityAmount: BigDecimal,
        override val amountDelta: BigDecimal,
        override val description: String,
    ) : TimelineEvent

    data class RecurringIncome(
        override val date: LocalDate,
        val categoryName: String,
        override val accountName: String,
        override val accountColorHex: String,
        override val accountIconName: String,
        override val amountDelta: BigDecimal,
        override val description: String,
    ) : TimelineEvent

    data class RecurringExpense(
        override val date: LocalDate,
        val categoryName: String,
        override val accountName: String,
        override val accountColorHex: String,
        override val accountIconName: String,
        override val amountDelta: BigDecimal,
        override val description: String,
    ) : TimelineEvent
}
