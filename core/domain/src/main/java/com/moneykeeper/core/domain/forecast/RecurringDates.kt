package com.moneykeeper.core.domain.forecast

import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import java.time.LocalDate

fun RecurringRule.expandDates(from: LocalDate, to: LocalDate): List<LocalDate> {
    val result = mutableListOf<LocalDate>()
    var current = startDate
    while (current < from) {
        current = current.advance(frequency, interval)
    }
    while (current <= to && (endDate == null || current <= endDate)) {
        result.add(current)
        current = current.advance(frequency, interval)
    }
    return result
}

fun LocalDate.advance(frequency: Frequency, interval: Int): LocalDate = when (frequency) {
    Frequency.DAILY   -> plusDays(interval.toLong())
    Frequency.WEEKLY  -> plusWeeks(interval.toLong())
    Frequency.MONTHLY -> plusMonths(interval.toLong())
    Frequency.YEARLY  -> plusYears(interval.toLong())
}
