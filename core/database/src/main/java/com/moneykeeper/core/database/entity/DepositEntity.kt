package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "deposits",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // При удалении счёта-получателя выплаты просто обнуляем ссылку —
        // DepositCloseUseCase трактует null как «выплата на сам DEPOSIT-счёт».
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["payoutAccountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["accountId"], unique = true), // инвариант 1:1 (один депозит на счёт)
        Index(value = ["payoutAccountId"]),
    ],
)
data class DepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun DepositEntity.toDomain() = Deposit(
    id = id, accountId = accountId, initialAmount = initialAmount,
    interestRate = interestRate, startDate = startDate, endDate = endDate,
    isCapitalized = isCapitalized, capitalizationPeriod = capitalizationPeriod,
    notifyDaysBefore = notifyDaysBefore, autoRenew = autoRenew,
    payoutAccountId = payoutAccountId, isActive = isActive,
)

fun Deposit.toEntity() = DepositEntity(
    id = id, accountId = accountId, initialAmount = initialAmount,
    interestRate = interestRate, startDate = startDate, endDate = endDate,
    isCapitalized = isCapitalized, capitalizationPeriod = capitalizationPeriod,
    notifyDaysBefore = notifyDaysBefore, autoRenew = autoRenew,
    payoutAccountId = payoutAccountId, isActive = isActive,
)
