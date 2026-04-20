package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.RateTier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["payoutAccountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["accountId"], unique = true),
        Index(value = ["payoutAccountId"]),
    ],
)
data class DepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val initialAmount: BigDecimal,
    val interestRate: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val isCapitalized: Boolean,
    val capitalizationPeriod: CapPeriod?,
    val notifyDaysBefore: List<Int> = listOf(7),
    val autoRenew: Boolean = false,
    val payoutAccountId: Long?,
    val isActive: Boolean = true,
    val rateTiersJson: String? = null,
)

@kotlinx.serialization.Serializable
private data class RateTierJson(val fromDate: String, val ratePercent: String)

private val json = Json { ignoreUnknownKeys = true }

private fun List<RateTier>.toJson(): String =
    json.encodeToString(map { RateTierJson(it.fromDate.toString(), it.ratePercent.toPlainString()) })

private fun String.toRateTiers(): List<RateTier> =
    json.decodeFromString<List<RateTierJson>>(this)
        .map { RateTier(LocalDate.parse(it.fromDate), BigDecimal(it.ratePercent)) }

fun DepositEntity.toDomain() = Deposit(
    id = id, accountId = accountId, initialAmount = initialAmount,
    interestRate = interestRate, startDate = startDate, endDate = endDate,
    isCapitalized = isCapitalized, capitalizationPeriod = capitalizationPeriod,
    notifyDaysBefore = notifyDaysBefore, autoRenew = autoRenew,
    payoutAccountId = payoutAccountId, isActive = isActive,
    rateTiers = rateTiersJson?.let { runCatching { it.toRateTiers() }.getOrElse { emptyList() } } ?: emptyList(),
)

fun Deposit.toEntity() = DepositEntity(
    id = id, accountId = accountId, initialAmount = initialAmount,
    interestRate = interestRate, startDate = startDate, endDate = endDate,
    isCapitalized = isCapitalized, capitalizationPeriod = capitalizationPeriod,
    notifyDaysBefore = notifyDaysBefore, autoRenew = autoRenew,
    payoutAccountId = payoutAccountId, isActive = isActive,
    rateTiersJson = if (rateTiers.isEmpty()) null else rateTiers.toJson(),
)
