package com.moneykeeper.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val toAccountId: Long?,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: Long?,
    val date: LocalDate,
    val note: String = "",
    val recurringRuleId: Long? = null,
    val createdAt: LocalDateTime,
)

enum class TransactionType { INCOME, EXPENSE, TRANSFER, SAVINGS }
