package com.moneykeeper.feature.accounts.domain

import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DepositCalculator {

    private val MC = MathContext(32, RoundingMode.HALF_EVEN)
    private const val INTERMEDIATE_SCALE = 10
    private const val RESULT_SCALE = 2
    private val YEAR_DAYS = BigDecimal(365)
    private val HUNDRED = BigDecimal(100)

    /**
     * Простые проценты за [startDate, endDate): A = P * r * days / 365.
     * Возвращает начисленные проценты (не principal + interest).
     */
    fun simpleInterest(
        principal: BigDecimal,
        ratePercent: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
    ): BigDecimal {
        if (!endDate.isAfter(startDate)) return BigDecimal.ZERO
        val days = BigDecimal(ChronoUnit.DAYS.between(startDate, endDate))
        return principal
            .multiply(ratePercent, MC)
            .divide(HUNDRED, MC)
            .multiply(days, MC)
            .divide(YEAR_DAYS, INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN)
            .setScale(RESULT_SCALE, RoundingMode.HALF_EVEN)
    }

    /**
     * Сложные проценты с периодической капитализацией по фактическому календарю.
     * Капитализационные периоды отсчитываются через plusMonths/plusYears, не через
     * фиксированное число дней — это даёт точный банковский результат.
     */
    fun compoundInterest(
        principal: BigDecimal,
        ratePercent: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
        period: CapPeriod,
    ): BigDecimal {
        if (!endDate.isAfter(startDate)) return BigDecimal.ZERO

        var balance = principal
        var current = startDate

        while (true) {
            val nextPeriodEnd = current.addPeriod(period)
            if (nextPeriodEnd.isAfter(endDate)) {
                val tailInterest = simpleInterestRaw(balance, ratePercent, current, endDate)
                balance = balance.add(tailInterest, MC)
                break
            }
            val periodInterest = simpleInterestRaw(balance, ratePercent, current, nextPeriodEnd)
            balance = balance.add(periodInterest, MC)
            current = nextPeriodEnd
            if (current == endDate) break
        }

        return balance.subtract(principal, MC).setScale(RESULT_SCALE, RoundingMode.HALF_EVEN)
    }

    /** Прогноз суммы (principal + начисленные проценты) на произвольную дату. */
    fun projectedBalance(deposit: Deposit, atDate: LocalDate): BigDecimal {
        val effectiveEnd = if (atDate.isBefore(deposit.endDate)) atDate else deposit.endDate
        val interest = if (deposit.isCapitalized)
            compoundInterest(
                deposit.initialAmount, deposit.interestRate,
                deposit.startDate, effectiveEnd,
                deposit.capitalizationPeriod ?: CapPeriod.MONTHLY,
            )
        else
            simpleInterest(deposit.initialAmount, deposit.interestRate, deposit.startDate, effectiveEnd)
        return deposit.initialAmount.add(interest)
    }

    private fun simpleInterestRaw(
        principal: BigDecimal,
        ratePercent: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
    ): BigDecimal {
        val days = BigDecimal(ChronoUnit.DAYS.between(startDate, endDate))
        return principal
            .multiply(ratePercent, MC)
            .divide(HUNDRED, MC)
            .multiply(days, MC)
            .divide(YEAR_DAYS, INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN)
    }

    private fun LocalDate.addPeriod(period: CapPeriod): LocalDate = when (period) {
        CapPeriod.MONTHLY   -> plusMonths(1)
        CapPeriod.QUARTERLY -> plusMonths(3)
        CapPeriod.YEARLY    -> plusYears(1)
    }
}
