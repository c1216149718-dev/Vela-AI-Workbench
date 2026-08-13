package com.deepseek.widget.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageComparisonTest {

    @Test
    fun normalizeFillsMissingDatesAndKeepsExistingUsage() {
        val start = LocalDate.of(2026, 7, 28)
        val result = UsageComparison.normalize(
            source = listOf(
                DailyUsagePoint(date = "2026-07-28", requests = 3, actual_cost = 0.2),
                DailyUsagePoint(date = "2026-07-30", requests = 5, actual_cost = 0.4)
            ),
            startDate = start,
            days = 3
        )

        assertEquals(listOf("2026-07-28", "2026-07-29", "2026-07-30"), result.map { it.date })
        assertEquals(listOf(3L, 0L, 5L), result.map { it.requests })
    }

    @Test
    fun percentageComparesCurrentAgainstPreviousPeriod() {
        assertEquals(25.0, UsageComparison.percentage(10.0, 8.0)!!, 0.001)
        assertEquals(-50.0, UsageComparison.percentage(4.0, 8.0)!!, 0.001)
        assertNull(UsageComparison.percentage(4.0, 0.0))
    }
}
