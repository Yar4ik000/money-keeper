package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import java.math.BigDecimal

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amount: BigDecimal,
    val period: BudgetPeriod,
    val currency: String,
    val accountIds: String? = null, // null = all accounts; "1,2,3" = specific account IDs
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    amount = amount,
    period = period,
    currency = currency,
    accountIds = accountIds?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    amount = amount,
    period = period,
    currency = currency,
    accountIds = accountIds.takeIf { it.isNotEmpty() }?.joinToString(","),
)
