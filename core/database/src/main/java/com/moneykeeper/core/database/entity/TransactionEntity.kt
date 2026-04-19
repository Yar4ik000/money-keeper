package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class,       parentColumns = ["id"], childColumns = ["accountId"],       onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class,       parentColumns = ["id"], childColumns = ["toAccountId"],     onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoryEntity::class,      parentColumns = ["id"], childColumns = ["categoryId"],      onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = RecurringRuleEntity::class, parentColumns = ["id"], childColumns = ["recurringRuleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("date"),
        Index("recurringRuleId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun TransactionEntity.toDomain() = Transaction(
    id = id, accountId = accountId, toAccountId = toAccountId,
    amount = amount, type = type, categoryId = categoryId,
    date = date, note = note, recurringRuleId = recurringRuleId, createdAt = createdAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id, accountId = accountId, toAccountId = toAccountId,
    amount = amount, type = type, categoryId = categoryId,
    date = date, note = note, recurringRuleId = recurringRuleId, createdAt = createdAt,
)
