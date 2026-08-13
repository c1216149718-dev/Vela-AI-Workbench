package com.deepseek.widget.api

import com.deepseek.widget.data.DeepSeekUsageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeepSeekUsageAggregatorTest {

    private val entries = listOf(
        DeepSeekUsageEntry(id = 1, date = "2026-08-01", model = "deepseek-v4-flash", inputTokens = 100, outputTokens = 50, cost = 0.10),
        DeepSeekUsageEntry(id = 2, date = "2026-08-01", model = "deepseek-v4-pro", inputTokens = 200, outputTokens = 80, cost = 0.30),
        DeepSeekUsageEntry(id = 3, date = "2026-08-02", model = "deepseek-v4-flash", inputTokens = 120, outputTokens = 60, cost = 0.12)
    )

    @Test
    fun dailyPointsAggregateEntriesByDate() {
        val start = LocalDate.of(2026, 8, 1)
        val points = DeepSeekUsageAggregator.dailyPoints(entries, start, 2)

        assertEquals(2, points.size)
        assertEquals(2L, points[0].requests)
        assertEquals(0.40, points[0].actual_cost, 0.0001)
        assertEquals(1L, points[1].requests)
        assertEquals(0.12, points[1].actual_cost, 0.0001)
    }

    @Test
    fun modelStatsGroupByModelAndSortByCost() {
        val stats = DeepSeekUsageAggregator.modelStats(entries)

        assertEquals(2, stats.size)
        assertEquals("deepseek-v4-pro", stats[0].model)
        assertEquals(0.30, stats[0].actual_cost, 0.0001)
        assertEquals(0.22, stats[1].actual_cost, 0.0001)
    }

    @Test
    fun totalsSumAcrossDays() {
        val start = LocalDate.of(2026, 8, 1)
        val points = DeepSeekUsageAggregator.dailyPoints(entries, start, 2)

        assertEquals(0.52, DeepSeekUsageAggregator.totalCost(points), 0.0001)
        assertEquals(3L, DeepSeekUsageAggregator.totalRequests(points))
        assertTrue(DeepSeekUsageAggregator.totalTokens(points) > 0)
    }
}
