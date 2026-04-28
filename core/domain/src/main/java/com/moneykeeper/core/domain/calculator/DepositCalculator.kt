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

    fun nextPeriodEnd(from: LocalDate, period: CapPeriod): LocalDate = from.addPeriod(period)

    /**
     * Like [accrueByPeriod] but for DAILY accrual basis — splits the period at each principal
     * change event so interest is computed on the actual balance at each point in time.
     *
     * [balanceAtFrom] — account balance at [from] (future principal events already excluded by caller)
     * [principalChanges] — (date, delta) pairs strictly in (from, to), sorted by date
     *
     * Returns a single slice at [to] with the total interest for the full period.
     */
    fun accrueByPeriodDaily(
        balanceAtFrom: BigDecimal,
        deposit: Deposit,
        from: LocalDate,
        to: LocalDate,
        principalChanges: List<Pair<LocalDate, BigDecimal>>,
    ): List<Pair<LocalDate, BigDecimal>> {
        if (!to.isAfter(from)) return emptyList()
        var balance = balanceAtFrom
        var segStart = from
        var totalInterest = BigDecimal.ZERO

        val sorted = principalChanges.sortedBy { it.first }
        val boundaries = sorted.map { it.first } + to

        for (segEnd in boundaries) {
            totalInterest += simpleInterestRaw(balance, deposit.rateAt(segStart), segStart, segEnd)
            sorted.filter { it.first == segEnd }.forEach { (_, delta) -> balance += delta }
            segStart = segEnd
        }

        val rounded = totalInterest.setScale(RESULT_SCALE, RoundingMode.HALF_EVEN)
        return if (rounded.signum() > 0) listOf(to to rounded) else emptyList()
    }

    private fun LocalDate.addPeriod(period: CapPeriod): LocalDate = when (period) {
        CapPeriod.DAILY     -> plusDays(1)
        CapPeriod.MONTHLY   -> plusMonthsEOM(1)
        CapPeriod.QUARTERLY -> plusMonthsEOM(3)
        CapPeriod.YEARLY    -> plusYears(1)
    }

    // End-of-month roll: Oct31→Nov30, Nov30→Dec31, Feb28→Mar31 (if source is last day of month)
    private fun LocalDate.plusMonthsEOM(months: Long): LocalDate {
        val isEOM = dayOfMonth == lengthOfMonth()
        val next = plusMonths(months)
        return if (isEOM) next.withDayOfMonth(next.lengthOfMonth()) else next
    }

    /**
     * Splits [from]..[to] into per-period slices and computes the interest for each.
     * For capitalized deposits the principal grows after every slice.
     * For non-capitalized the principal stays fixed (interest accrues on the original base).
     *
     * Capitalization period is used as the slice size; non-capitalized deposits use MONTHLY slices
     * so the history is still readable (one line per month rather than one giant lump sum).
     *
     * Returns a list of (periodEndDate, interestAmount) pairs — one entry per slice.
     */
    fun accrueByPeriod(
        currentPrincipal: BigDecimal,
        deposit: Deposit,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, BigDecimal>> {
        if (!to.isAfter(from)) return emptyList()
        val step = deposit.capitalizationPeriod ?: CapPeriod.MONTHLY
        val result = mutableListOf<Pair<LocalDate, BigDecimal>>()
        var principal = currentPrincipal
        var current = from
        while (current.isBefore(to)) {
            val periodEnd = minOf(current.addPeriod(step), to)
            val rate = deposit.rateAt(current)
            val interest = simpleInterestRaw(principal, rate, current, periodEnd)
                .setScale(RESULT_SCALE, java.math.RoundingMode.HALF_EVEN)
            if (interest.signum() > 0) {
                result.add(periodEnd to interest)
                if (deposit.isCapitalized) principal = principal.add(interest)
            }
            current = periodEnd
        }
        return result
    }
}
