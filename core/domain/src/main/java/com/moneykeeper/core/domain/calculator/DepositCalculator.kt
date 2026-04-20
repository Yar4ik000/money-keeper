package com.moneykeeper.core.domain.calculator

import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.rateAt
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

    /**
     * Projects deposit balance at [atDate], respecting rate tiers and optional end date.
     * For open-ended deposits (endDate == null), treats [atDate] as the effective end.
     */
    fun projectedBalance(deposit: Deposit, atDate: LocalDate): BigDecimal {
        val effectiveEnd = when {
            deposit.endDate == null -> atDate
            atDate.isBefore(deposit.endDate) -> atDate
            else -> deposit.endDate
        }

        val interest = if (deposit.isCapitalized && deposit.capitalizationPeriod != null) {
            compoundInterestWithTiers(deposit, effectiveEnd)
        } else {
            simpleInterestWithTiers(deposit, effectiveEnd)
        }
        return deposit.initialAmount.add(interest)
    }

    // ─── Rate-tier aware helpers ───────────────────────────────────────────────

    private fun simpleInterestWithTiers(deposit: Deposit, endDate: LocalDate): BigDecimal {
        if (!endDate.isAfter(deposit.startDate)) return BigDecimal.ZERO
        if (deposit.rateTiers.isEmpty()) {
            return simpleInterest(deposit.initialAmount, deposit.interestRate, deposit.startDate, endDate)
        }
        // Calculate segment by segment between tier boundaries
        val boundaries = tierBoundaries(deposit, endDate)
        return boundaries.fold(BigDecimal.ZERO) { acc, (segStart, segEnd) ->
            val rate = deposit.rateAt(segStart)
            acc + simpleInterest(deposit.initialAmount, rate, segStart, segEnd)
        }.setScale(RESULT_SCALE, RoundingMode.HALF_EVEN)
    }

    private fun compoundInterestWithTiers(deposit: Deposit, endDate: LocalDate): BigDecimal {
        if (!endDate.isAfter(deposit.startDate)) return BigDecimal.ZERO
        val period = deposit.capitalizationPeriod!!
        if (deposit.rateTiers.isEmpty()) {
            return compoundInterest(deposit.initialAmount, deposit.interestRate, deposit.startDate, endDate, period)
        }

        var balance = deposit.initialAmount
        var current = deposit.startDate

        while (current < endDate) {
            val nextPeriodEnd = current.addPeriod(period)
            val segEnd = if (nextPeriodEnd.isAfter(endDate)) endDate else nextPeriodEnd
            val rate = deposit.rateAt(current)
            val periodInterest = simpleInterestRaw(balance, rate, current, segEnd)
            balance = balance.add(periodInterest, MC)
            current = segEnd
            if (current >= endDate) break
        }

        return balance.subtract(deposit.initialAmount, MC).setScale(RESULT_SCALE, RoundingMode.HALF_EVEN)
    }

    /** Returns list of (segmentStart, segmentEnd) pairs covering [startDate, endDate) split at tier boundaries. */
    private fun tierBoundaries(deposit: Deposit, endDate: LocalDate): List<Pair<LocalDate, LocalDate>> {
        val boundaries = (deposit.rateTiers.map { it.fromDate } + endDate).sorted()
        var current = deposit.startDate
        return buildList {
            boundaries.forEach { b ->
                if (b > current && current < endDate) {
                    add(current to minOf(b, endDate))
                    current = b
                }
            }
        }
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
        CapPeriod.DAILY     -> plusDays(1)
        CapPeriod.MONTHLY   -> plusMonths(1)
        CapPeriod.QUARTERLY -> plusMonths(3)
        CapPeriod.YEARLY    -> plusYears(1)
    }
}
