package com.moneykeeper.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Deposit(
    val id: Long = 0,
    val accountId: Long,
    val initialAmount: BigDecimal,
    val interestRate: BigDecimal,
    val startDate: LocalDate,
    /** null = open-ended (накопительный счёт without fixed maturity) */
    val endDate: LocalDate?,
    val isCapitalized: Boolean,
    val capitalizationPeriod: CapPeriod?,
    val notifyDaysBefore: List<Int> = listOf(7),
    val autoRenew: Boolean = false,
    val payoutAccountId: Long?,
    val isActive: Boolean = true,
    /** Stepped rates, sorted by fromDate ascending. Empty = use interestRate for full term. */
    val rateTiers: List<RateTier> = emptyList(),
)

/** A rate period: [ratePercent] applies from [fromDate] until the next tier starts (or deposit ends). */
data class RateTier(
    val fromDate: LocalDate,
    val ratePercent: BigDecimal,
)

/** Returns the applicable interest rate on a given date, consulting rate tiers first. */
fun Deposit.rateAt(date: LocalDate): BigDecimal {
    if (rateTiers.isEmpty()) return interestRate
    return rateTiers
        .filter { it.fromDate <= date }
        .maxByOrNull { it.fromDate }
        ?.ratePercent ?: interestRate
}

enum class CapPeriod { DAILY, MONTHLY, QUARTERLY, YEARLY }
