package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.model.DepositEventType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "deposit_events",
    foreignKeys = [
        ForeignKey(
            entity = DepositEntity::class,
            parentColumns = ["id"],
            childColumns = ["depositId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["depositId", "date"])],
)
data class DepositEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val depositId: Long,
    val date: LocalDate,
    val type: DepositEventType,
    val amount: BigDecimal,
    val note: String = "",
)

fun DepositEventEntity.toDomain() = DepositEvent(
    id = id, depositId = depositId, date = date, type = type, amount = amount, note = note,
)

fun DepositEvent.toEntity() = DepositEventEntity(
    id = id, depositId = depositId, date = date, type = type, amount = amount, note = note,
)
