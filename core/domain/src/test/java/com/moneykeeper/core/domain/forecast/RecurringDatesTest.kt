package com.moneykeeper.core.domain.forecast

import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringDatesTest {

    private fun rule(
        frequency: Frequency,
        interval: Int = 1,
        startDate: LocalDate,
        endDate: LocalDate? = null,
    ) = RecurringRule(
        id = 0L,
        frequency = frequency,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
    )

    @Test
    fun `monthly rule starting Jan 31 clips to Feb 28 and then drifts to 28th`() {
        // LocalDate.plusMonths clamps overflow days silently. Once the anchor hits
        // Feb 28, subsequent plusMonths(1) calls stay on the 28th forever — the user
        // who schedules "на 31-е каждого месяца" quietly loses 3 days per 31-day month.
        val dates = rule(Frequency.MONTHLY, startDate = LocalDate.of(2026, 1, 31))
            .expandDates(from = LocalDate.of(2026, 1, 31), to = LocalDate.of(2026, 5, 31))

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 28),
                LocalDate.of(2026, 4, 28),
                LocalDate.of(2026, 5, 28),
            ),
            dates,
        )
    }

    @Test
    fun `biweekly rule advances by 14 days`() {
        val dates = rule(Frequency.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 4, 1))
            .expandDates(from = LocalDate.of(2026, 4, 1), to = LocalDate.of(2026, 5, 1))

        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 29),
            ),
            dates,
        )
    }

    @Test
    fun `expandDates respects endDate and stops producing after it`() {
        val dates = rule(
            Frequency.DAILY,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 3),
        ).expandDates(from = LocalDate.of(2026, 4, 1), to = LocalDate.of(2026, 4, 10))

        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 3),
            ),
            dates,
        )
    }

    @Test
    fun `from range earlier than startDate yields occurrences only at or after startDate`() {
        val dates = rule(Frequency.MONTHLY, startDate = LocalDate.of(2026, 4, 15))
            .expandDates(from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 6, 30))

        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 6, 15),
            ),
            dates,
        )
    }

    @Test
    fun `from range strictly after all occurrences within endDate yields empty`() {
        val dates = rule(
            Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 3, 1),
        ).expandDates(from = LocalDate.of(2026, 4, 1), to = LocalDate.of(2026, 12, 31))

        assertEquals(emptyList<LocalDate>(), dates)
    }
}
