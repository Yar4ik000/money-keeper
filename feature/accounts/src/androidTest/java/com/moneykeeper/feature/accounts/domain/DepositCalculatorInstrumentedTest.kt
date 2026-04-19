package com.moneykeeper.feature.accounts.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DepositCalculatorInstrumentedTest {

    @Test
    fun simpleInterest_oneYear_isCorrect() {
        val result = DepositCalculator.simpleInterest(
            principal = BigDecimal("100000"),
            ratePercent = BigDecimal("10"),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2025, 1, 1),
        )
        assertEquals(0, BigDecimal("10000").compareTo(result))
    }

    @Test
    fun simpleInterest_zeroDuration_isZero() {
        val today = LocalDate.of(2025, 6, 1)
        val result = DepositCalculator.simpleInterest(
            principal = BigDecimal("50000"),
            ratePercent = BigDecimal("12"),
            startDate = today,
            endDate = today,
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun compoundInterest_monthly_greaterThanSimple() {
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2025, 1, 1)
        val principal = BigDecimal("100000")
        val rate = BigDecimal("12")
        val compound = DepositCalculator.compoundInterest(
            principal, rate, start, end, com.moneykeeper.core.domain.model.CapPeriod.MONTHLY,
        )
        val simple = DepositCalculator.simpleInterest(principal, rate, start, end)
        assertTrue(compound > simple)
    }

    @Test
    fun projectedBalance_beforeStart_equalsPrincipal() {
        val deposit = com.moneykeeper.core.domain.model.Deposit(
            id = 0L,
            accountId = 0L,
            initialAmount = BigDecimal("50000"),
            interestRate = BigDecimal("10"),
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2026, 6, 1),
            isCapitalized = false,
            capitalizationPeriod = null,
            notifyDaysBefore = 7,
            autoRenew = false,
            payoutAccountId = null,
            isActive = true,
        )
        val balance = DepositCalculator.projectedBalance(deposit, LocalDate.of(2025, 5, 1))
        assertEquals(0, BigDecimal("50000").compareTo(balance))
    }

    @Test
    fun projectedBalance_afterEnd_returnsFullInterest() {
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2025, 1, 1)
        val deposit = com.moneykeeper.core.domain.model.Deposit(
            id = 0L,
            accountId = 0L,
            initialAmount = BigDecimal("100000"),
            interestRate = BigDecimal("10"),
            startDate = start,
            endDate = end,
            isCapitalized = false,
            capitalizationPeriod = null,
            notifyDaysBefore = 7,
            autoRenew = false,
            payoutAccountId = null,
            isActive = true,
        )
        val balance = DepositCalculator.projectedBalance(deposit, LocalDate.of(2026, 1, 1))
        val expected = BigDecimal("100000") + DepositCalculator.simpleInterest(
            BigDecimal("100000"), BigDecimal("10"), start, end,
        )

        assertEquals(0, expected.compareTo(balance))
    }
}
