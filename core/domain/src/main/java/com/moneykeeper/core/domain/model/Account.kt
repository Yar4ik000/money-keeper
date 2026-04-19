package com.moneykeeper.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String,
    val colorHex: String,
    val iconName: String,
    val balance: BigDecimal,
    val isArchived: Boolean = false,
    val createdAt: LocalDate,
    val sortOrder: Int = 0,
)

enum class AccountType { CARD, CASH, DEPOSIT, SAVINGS, INVESTMENT, OTHER }
