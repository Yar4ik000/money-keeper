package com.moneykeeper.feature.accounts.domain

import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DepositCalculatorTest {

    @Test
    fun simple_interest_100k_at_10pct_for_31_days() {
        // 100_000 * 0.10 * 31/365 = 849.315... → 849.32 (HALF_EVEN)
        val interest = DepositCalculator.simpleInterest(
            principal   = BigDecimal("100000"),
            ratePercent = BigDecimal("10"),
            startDate   = LocalDate.of(2026, 1, 1),
            endDate     = LocalDate.of(2026, 2, 1),
        )
        assertEquals(0, BigDecimal("849.32").compareTo(interest))
    }

    @Test
    fun compound_monthly_100k_at_10pct_for_365_days() {
        // Ежемесячная капитализация, 12 периодов. Результат посчитан симуляцией алгоритма
        // (Python Decimal, prec=50). Допуск ±0.02 покрывает разброс от INTERMEDIATE_SCALE=10.
        val interest = DepositCalculator.compoundInterest(
            principal   = BigDecimal("100000"),
            ratePercent = BigDecimal("10"),
            startDate   = LocalDate.of(2026, 1, 1),
            endDate     = LocalDate.of(2027, 1, 1),
            period      = CapPeriod.MONTHLY,
        )
        val expected = BigDecimal("10471.27")
        val delta = interest.subtract(expected).abs()
        assertTrue("expected ~$expected, got $interest (delta=$delta)", delta < BigDecimal("0.02"))
    }

    @Test
    fun zero_duration_returns_zero() {
        val date = LocalDate.of(2026, 4, 19)
        assertEquals(
            BigDecimal.ZERO,
            DepositCalculator.simpleInterest(BigDecimal("100000"), BigDecimal("10"), date, date),
        )
        assertEquals(
            BigDecimal.ZERO,
            DepositCalculator.compoundInterest(BigDecimal("100000"), BigDecimal("10"), date, date, CapPeriod.MONTHLY),
        )
    }

    @Test
    fun february_non_leap_28_days() {
        // 2026 не високосный: февраль 28 дней.
        // 100_000 * 0.10 * 28/365 = 767.1232... → 767.12 (HALF_EVEN)
        val interest = DepositCalculator.simpleInterest(
            BigDecimal("100000"), BigDecimal("10"),
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1),
        )
        assertEquals(0, BigDecimal("767.12").compareTo(interest))
    }

    @Test
    fun february_leap_year_29_days() {
        // 2028 високосный: февраль 29 дней.
        // 100_000 * 0.10 * 29/365 = 794.5205... → 794.52 (HALF_EVEN)
        val interest = DepositCalculator.simpleInterest(
            BigDecimal("100000"), BigDecimal("10"),
            LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1),
        )
        assertEquals(0, BigDecimal("794.52").compareTo(interest))
    }

    @Test
    fun projected_balance_after_end_date_equals_maturity() {
        val deposit = Deposit(
            id = 0L, accountId = 1L,
            initialAmount = BigDecimal("100000"),
            interestRate = BigDecimal("10"),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2027, 1, 1),
            isCapitalized = false,
            capitalizationPeriod = CapPeriod.MONTHLY,
            notifyDaysBefore = listOf(7), autoRenew = false, payoutAccountId = null, isActive = true,
        )
        val atEnd    = DepositCalculator.projectedBalance(deposit, LocalDate.of(2027, 1, 1))
        val afterEnd = DepositCalculator.projectedBalance(deposit, LocalDate.of(2027, 6, 1))
        assertEquals(0, atEnd.compareTo(afterEnd))
    }

    @Test
    fun quarterly_compound_interest() {
        // Ежеквартальная капитализация: 4 периода по 90-92 дня.
        val interest = DepositCalculator.compoundInterest(
            principal   = BigDecimal("100000"),
            ratePercent = BigDecimal("12"),
            startDate   = LocalDate.of(2026, 1, 1),
            endDate     = LocalDate.of(2027, 1, 1),
            period      = CapPeriod.QUARTERLY,
        )
        // Должно быть больше простых 12%=12000 из-за капитализации
        assertTrue("Expected more than 12000, got $interest", interest > BigDecimal("12000"))
    }
}
