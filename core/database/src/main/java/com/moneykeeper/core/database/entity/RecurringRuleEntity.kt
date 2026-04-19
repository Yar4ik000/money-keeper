package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import java.time.LocalDate

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lastGeneratedDate: LocalDate? = null,
)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id, frequency = frequency, interval = interval,
    startDate = startDate, endDate = endDate, lastGeneratedDate = lastGeneratedDate,
)

fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id, frequency = frequency, interval = interval,
    startDate = startDate, endDate = endDate, lastGeneratedDate = lastGeneratedDate,
)
