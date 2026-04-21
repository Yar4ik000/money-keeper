package com.moneykeeper.feature.forecast.domain

import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.forecast.AccountForecast
import com.moneykeeper.core.domain.forecast.ForecastCurrencyTotal
import com.moneykeeper.core.domain.forecast.ForecastResult
import com.moneykeeper.core.domain.forecast.TimelineEvent
import com.moneykeeper.core.domain.forecast.expandDates
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class ForecastEngine @Inject constructor() {

    fun calculate(
        accounts: List<Account>,
        deposits: List<Deposit>,
        recurringRules: List<RecurringRuleWithTemplate>,
        targetDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): ForecastResult {
        val forecasts = accounts.map { AccountForecast(it, it.balance, it.balance, BigDecimal.ZERO) }
        if (!targetDate.isAfter(today)) {
            return ForecastResult(
                targetDate = targetDate,
                accountForecasts = forecasts,
                totalsByCurrency = aggregateByCurrency(forecasts),
                events = emptyList(),
            )
        }

        val balances: MutableMap<Long, BigDecimal> =
            accounts.associate { it.id to it.balance }.toMutableMap()
        val events = mutableListOf<TimelineEvent>()

        val accountMap = accounts.associateBy { it.id }

        // Recurring transactions
        for (rule in recurringRules) {
            val dates = rule.rule.expandDates(from = today.plusDays(1), to = targetDate)
            val ruleAccount = accountMap[rule.templateTransaction.accountId]
            val ruleColorHex = ruleAccount?.colorHex ?: "#607D8B"
            val ruleIconName = ruleAccount?.iconName ?: "bank"
            for (date in dates) {
                val tx = rule.templateTransaction
                when (tx.type) {
                    TransactionType.INCOME -> {
                        balances.merge(tx.accountId, tx.amount, BigDecimal::add)
                        events += TimelineEvent.RecurringIncome(
                            date = date,
                            categoryName = rule.categoryName,
                            accountName = rule.accountName,
                            accountColorHex = ruleColorHex,
                            accountIconName = ruleIconName,
                            amountDelta = tx.amount,
                            description = rule.description,
                        )
                    }
                    TransactionType.EXPENSE, TransactionType.SAVINGS -> {
                        balances.merge(tx.accountId, tx.amount.negate(), BigDecimal::add)
                        events += TimelineEvent.RecurringExpense(
                            date = date,
                            categoryName = rule.categoryName,
                            accountName = rule.accountName,
                            accountColorHex = ruleColorHex,
                            accountIconName = ruleIconName,
                            amountDelta = tx.amount.negate(),
                            description = rule.description,
                        )
                    }
                    TransactionType.TRANSFER -> {
                        balances.merge(tx.accountId, tx.amount.negate(), BigDecimal::add)
                        tx.toAccountId?.let { balances.merge(it, tx.amount, BigDecimal::add) }
                    }
                }
            }
        }

        // Deposits
        for (deposit in deposits) {
            val payoutId = deposit.payoutAccountId ?: deposit.accountId
            val depositAccount = accountMap[deposit.accountId]
            val depositAccountName = depositAccount?.name.orEmpty()
            val endDate = deposit.endDate

            if (endDate != null && !endDate.isAfter(targetDate)) {
                val maturity = DepositCalculator.projectedBalance(deposit, endDate)
                balances.merge(deposit.accountId, deposit.initialAmount.negate(), BigDecimal::add)
                balances.merge(payoutId, maturity, BigDecimal::add)
                events += TimelineEvent.DepositMaturity(
                    date = endDate,
                    deposit = deposit,
                    accountName = depositAccountName,
                    accountColorHex = depositAccount?.colorHex ?: "#607D8B",
                    accountIconName = depositAccount?.iconName ?: "bank",
                    maturityAmount = maturity,
                    amountDelta = maturity - deposit.initialAmount,
                    description = "Окончание вклада: $depositAccountName",
                )
            } else {
                // Deposit does not mature by targetDate. Reflect accrued interest only when
                // the user actually sees it in their available balance:
                //   - Open-ended account (no endDate, e.g. SAVINGS) — interest is credited ongoing
                //   - Capitalized term deposit — interest is added to principal each period
                // For a non-capitalized term deposit with a fixed endDate, interest accumulates
                // on the bank's side but isn't paid out until endDate, so the user's visible
                // balance stays at the principal amount.
                val showAccruedInterest = endDate == null || deposit.isCapitalized
                if (showAccruedInterest) {
                    val interest =
                        DepositCalculator.projectedBalance(deposit, targetDate) - deposit.initialAmount
                    balances.merge(deposit.accountId, interest, BigDecimal::add)
                }
            }
        }

        val accountForecasts = accounts.map { acc ->
            val forecast = balances[acc.id] ?: acc.balance
            AccountForecast(
                account = acc,
                currentBalance = acc.balance,
                forecastedBalance = forecast,
                delta = forecast - acc.balance,
            )
        }

        return ForecastResult(
            targetDate = targetDate,
            accountForecasts = accountForecasts,
            totalsByCurrency = aggregateByCurrency(accountForecasts),
            events = events.sortedBy { it.date },
        )
    }

    private fun aggregateByCurrency(forecasts: List<AccountForecast>): List<ForecastCurrencyTotal> =
        forecasts
            .groupBy { it.account.currency }
            .map { (currency, list) ->
                ForecastCurrencyTotal(
                    currency = currency,
                    currentBalance = list.sumOf { it.currentBalance },
                    forecastedBalance = list.sumOf { it.forecastedBalance },
                    delta = list.sumOf { it.delta },
                )
            }
            .sortedBy { it.currency }
}
