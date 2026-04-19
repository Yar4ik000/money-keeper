package com.moneykeeper.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Deposit(
    val id: Long = 0,
    val accountId: Long,
    val initialAmount: BigDecimal,
    val interestRate: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isCapitalized: Boolean,
    val capitalizationPeriod: CapPeriod?,
    val notifyDaysBefore: Int = 7,
    val autoRenew: Boolean = false,
    val payoutAccountId: Long?,
    val isActive: Boolean = true,
)

enum class CapPeriod { MONTHLY, QUARTERLY, YEARLY }
