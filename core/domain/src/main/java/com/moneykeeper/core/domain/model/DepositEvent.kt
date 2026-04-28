package com.moneykeeper.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class DepositEvent(
    val id: Long = 0,
    val depositId: Long,
    val date: LocalDate,
    val type: DepositEventType,
    val amount: BigDecimal,
    val note: String = "",
)

enum class DepositEventType {
    PRINCIPAL_ADD,
    PRINCIPAL_WITHDRAW,
    INTEREST_ACCRUAL,
    CAPITALIZATION,
}
