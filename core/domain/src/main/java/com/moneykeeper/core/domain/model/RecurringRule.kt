package com.moneykeeper.core.domain.model

import java.time.LocalDate

data class RecurringRule(
    val id: Long = 0,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lastGeneratedDate: LocalDate? = null,
)

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
