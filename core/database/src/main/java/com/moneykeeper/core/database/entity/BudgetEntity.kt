package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import java.math.BigDecimal

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryIds: String? = null, // null = all categories; "1,2,3" = specific IDs
    val amount: BigDecimal,
    val period: BudgetPeriod,
    val currency: String,
    val accountIds: String? = null, // null = all accounts; "1,2,3" = specific IDs
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryIds = categoryIds?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
    amount = amount,
    period = period,
    currency = currency,
    accountIds = accountIds?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryIds = categoryIds.takeIf { it.isNotEmpty() }?.joinToString(","),
    amount = amount,
    period = period,
    currency = currency,
    accountIds = accountIds.takeIf { it.isNotEmpty() }?.joinToString(","),
)
