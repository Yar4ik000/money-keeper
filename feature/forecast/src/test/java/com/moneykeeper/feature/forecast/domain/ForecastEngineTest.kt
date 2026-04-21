package com.moneykeeper.feature.forecast.domain

import com.moneykeeper.core.domain.forecast.TimelineEvent
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastEngineTest {

    private val engine = ForecastEngine()
    private val today = LocalDate.of(2026, 4, 20)

    private fun account(
        id: Long,
        balance: BigDecimal,
        currency: String = "RUB",
        type: AccountType = AccountType.CARD,
    ) = Account(
        id = id, name = "Account $id", type = type, currency = currency,
        colorHex = "#000", iconName = "CreditCard", balance = balance,
        createdAt = LocalDate.of(2026, 1, 1),
    )

    private fun deposit(
        accountId: Long,
        principal: BigDecimal,
        rate: BigDecimal,
        endDate: LocalDate,
        payoutAccountId: Long? = null,
        capitalized: Boolean = false,
    ) = Deposit(
        accountId = accountId, initialAmount = principal, interestRate = rate,
        startDate = today, endDate = endDate, isCapitalized = capitalized,
        capitalizationPeriod = if (capitalized) CapPeriod.MONTHLY else null,
        payoutAccountId = payoutAccountId, isActive = true,
    )

    private fun rule(
        accountId: Long,
        amount: BigDecimal,
        type: TransactionType,
        frequency: Frequency = Frequency.MONTHLY,
        startDate: LocalDate = today.plusDays(1),
        endDate: LocalDate? = null,
    ): RecurringRuleWithTemplate {
        val r = RecurringRule(
            id = 1L, frequency = frequency, interval = 1,
            startDate = startDate, endDate = endDate,
        )
        val tx = Transaction(
            id = 1L, accountId = accountId, toAccountId = null,
            amount = amount, type = type, categoryId = null,
            date = startDate, createdAt = LocalDateTime.of(2026, 4, 21, 12, 0),
        )
        return RecurringRuleWithTemplate(
            rule = r, templateTransaction = tx,
            accountName = "Account $accountId", categoryName = "Category",
            description = "Rule desc",
        )
    }

    // ─── No change when targetDate == today ─────────────────────────────────

    @Test
    fun returns_current_balances_when_target_is_today() {
        val acc = account(1L, BigDecimal("10000"))
        val result = engine.calculate(listOf(acc), emptyList(), emptyList(), today, today)
        assertEquals(BigDecimal("10000"), result.accountForecasts[0].forecastedBalance)
        assertEquals(BigDecimal.ZERO, result.accountForecasts[0].delta)
        assertTrue(result.events.isEmpty())
    }

    // ─── Recurring income ────────────────────────────────────────────────────

    @Test
    fun monthly_income_adds_to_balance() {
        val acc = account(1L, BigDecimal("5000"))
        // Monthly income of 1000 on the 1st; target = 3 months ahead → 3 occurrences
        val r = rule(
            accountId = 1L, amount = BigDecimal("1000"),
            type = TransactionType.INCOME, frequency = Frequency.MONTHLY,
            startDate = today.withDayOfMonth(1).plusMonths(1),
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(acc), emptyList(), listOf(r), target, today)
        assertEquals(BigDecimal("8000"), result.accountForecasts[0].forecastedBalance)
        assertEquals(3, result.events.filterIsInstance<TimelineEvent.RecurringIncome>().size)
    }

    // ─── Recurring expense ───────────────────────────────────────────────────

    @Test
    fun monthly_expense_subtracts_from_balance() {
        val acc = account(1L, BigDecimal("10000"))
        val r = rule(
            accountId = 1L, amount = BigDecimal("2000"),
            type = TransactionType.EXPENSE, frequency = Frequency.MONTHLY,
            startDate = today.withDayOfMonth(1).plusMonths(1),
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(acc), emptyList(), listOf(r), target, today)
        assertEquals(BigDecimal("4000"), result.accountForecasts[0].forecastedBalance)
        assertEquals(3, result.events.filterIsInstance<TimelineEvent.RecurringExpense>().size)
    }

    // ─── Deposit matures before targetDate ──────────────────────────────────

    @Test
    fun deposit_matures_before_target_adds_interest_to_payout_account() {
        // Deposit account: 100 000 RUB, simple interest 12%, matures in 30 days
        val depositAccountId = 1L
        val payoutAccountId = 2L
        val depositAcc = account(depositAccountId, BigDecimal("100000"), type = AccountType.DEPOSIT)
        val payoutAcc = account(payoutAccountId, BigDecimal("0"))
        val endDate = today.plusDays(30)
        val dep = deposit(
            accountId = depositAccountId,
            principal = BigDecimal("100000"),
            rate = BigDecimal("12"),
            endDate = endDate,
            payoutAccountId = payoutAccountId,
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(depositAcc, payoutAcc), listOf(dep), emptyList(), target, today)

        val depositForecast = result.accountForecasts.first { it.account.id == depositAccountId }
        val payoutForecast = result.accountForecasts.first { it.account.id == payoutAccountId }

        // Deposit account loses its principal (goes to 0)
        assertEquals(0, BigDecimal.ZERO.compareTo(depositForecast.forecastedBalance))
        // Payout gets maturity (principal + ~986 interest for 30 days at 12%)
        assertTrue(payoutForecast.forecastedBalance > BigDecimal("100000"))

        val maturityEvents = result.events.filterIsInstance<TimelineEvent.DepositMaturity>()
        assertEquals(1, maturityEvents.size)
        assertEquals(endDate, maturityEvents[0].date)
    }

    // ─── Deposit still active at targetDate ─────────────────────────────────

    @Test
    fun capitalized_active_deposit_accrues_interest_to_deposit_account() {
        // Capitalized term deposit adds interest to principal each period, so the user's
        // visible balance grows before maturity.
        val depositAcc = account(1L, BigDecimal("50000"), type = AccountType.DEPOSIT)
        val dep = deposit(
            accountId = 1L,
            principal = BigDecimal("50000"),
            rate = BigDecimal("10"),
            endDate = today.plusYears(1),
            capitalized = true,
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(depositAcc), listOf(dep), emptyList(), target, today)

        val forecast = result.accountForecasts[0]
        assertTrue(forecast.forecastedBalance > BigDecimal("50000"))
        assertTrue(result.events.filterIsInstance<TimelineEvent.DepositMaturity>().isEmpty())
    }

    @Test
    fun non_capitalized_term_deposit_balance_stays_flat_before_maturity() {
        // Without capitalization, interest is paid in a lump sum at endDate.
        // Before maturity the user's available balance is still just the principal.
        val depositAcc = account(1L, BigDecimal("50000"), type = AccountType.DEPOSIT)
        val dep = deposit(
            accountId = 1L,
            principal = BigDecimal("50000"),
            rate = BigDecimal("10"),
            endDate = today.plusYears(1),
            capitalized = false,
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(depositAcc), listOf(dep), emptyList(), target, today)

        val forecast = result.accountForecasts[0]
        assertEquals(0, BigDecimal("50000").compareTo(forecast.forecastedBalance))
        assertEquals(0, BigDecimal.ZERO.compareTo(forecast.delta))
        assertTrue(result.events.filterIsInstance<TimelineEvent.DepositMaturity>().isEmpty())
    }

    @Test
    fun open_ended_deposit_accrues_interest_regardless_of_capitalization() {
        // Open-ended (no endDate) deposits behave like a SAVINGS account —
        // interest is always visible in the balance.
        val depositAcc = account(1L, BigDecimal("50000"), type = AccountType.DEPOSIT)
        val dep = Deposit(
            accountId = 1L,
            initialAmount = BigDecimal("50000"),
            interestRate = BigDecimal("10"),
            startDate = today,
            endDate = null,
            isCapitalized = false,
            capitalizationPeriod = null,
            payoutAccountId = null,
            isActive = true,
        )
        val target = today.plusMonths(3)
        val result = engine.calculate(listOf(depositAcc), listOf(dep), emptyList(), target, today)

        val forecast = result.accountForecasts[0]
        assertTrue(forecast.forecastedBalance > BigDecimal("50000"))
    }

    // ─── Currency totals grouping ─────────────────────────────────────────────

    @Test
    fun totals_grouped_by_currency() {
        val rub1 = account(1L, BigDecimal("10000"), "RUB")
        val rub2 = account(2L, BigDecimal("5000"), "RUB")
        val usd = account(3L, BigDecimal("200"), "USD")
        val result = engine.calculate(listOf(rub1, rub2, usd), emptyList(), emptyList(), today, today)

        assertEquals(2, result.totalsByCurrency.size)
        val rub = result.totalsByCurrency.first { it.currency == "RUB" }
        assertEquals(BigDecimal("15000"), rub.currentBalance)
        val usdTotal = result.totalsByCurrency.first { it.currency == "USD" }
        assertEquals(BigDecimal("200"), usdTotal.currentBalance)
    }

    // ─── Events sorted by date ───────────────────────────────────────────────

    @Test
    fun events_are_sorted_by_date() {
        val acc = account(1L, BigDecimal("10000"))
        val weeklyRule = rule(
            accountId = 1L, amount = BigDecimal("100"),
            type = TransactionType.INCOME, frequency = Frequency.WEEKLY,
            startDate = today.plusDays(3),
        )
        val target = today.plusMonths(2)
        val result = engine.calculate(listOf(acc), emptyList(), listOf(weeklyRule), target, today)
        val dates = result.events.map { it.date }
        assertEquals(dates.sorted(), dates)
    }
}
