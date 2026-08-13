package com.deepseek.widget.api

import com.deepseek.widget.data.BalanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BalanceDeltaAggregatorTest {

    @Test
    fun sumsEachBalanceDropEvenWhenARechargeHappensDuringTheDay() {
        val date = LocalDate.of(2026, 8, 3)
        val snapshots = listOf(
            snapshot(date, 8, 100.0),
            snapshot(date, 9, 90.0),
            snapshot(date, 10, 120.0),
            snapshot(date, 11, 110.0)
        )

        val points = BalanceDeltaAggregator.dailyPoints(snapshots, date, 1)

        assertEquals(20.0, points.single().actual_cost, 0.0001)
    }

    @Test
    fun usesTheLastSnapshotBeforeTheRangeAsItsOpeningBaseline() {
        val start = LocalDate.of(2026, 8, 3)
        val snapshots = listOf(
            snapshot(start.minusDays(1), 23, 100.0),
            snapshot(start, 8, 95.0)
        )

        val points = BalanceDeltaAggregator.dailyPoints(snapshots, start, 1)

        assertEquals(5.0, points.single().actual_cost, 0.0001)
    }

    @Test
    fun ignoresBalanceChangesAcrossDifferentCurrencies() {
        val date = LocalDate.of(2026, 8, 3)
        val snapshots = listOf(
            snapshot(date, 8, 100.0, "CNY"),
            snapshot(date, 9, 10.0, "USD")
        )

        val points = BalanceDeltaAggregator.dailyPoints(snapshots, date, 1)

        assertTrue(points.all { it.actual_cost == 0.0 })
        assertEquals(0.0, BalanceDeltaAggregator.totalCost(snapshots), 0.0001)
    }

    private fun snapshot(
        date: LocalDate,
        hour: Int,
        balance: Double,
        currency: String = "CNY"
    ) = BalanceSnapshot(
        timestamp = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        balance = balance,
        currency = currency
    )
}
