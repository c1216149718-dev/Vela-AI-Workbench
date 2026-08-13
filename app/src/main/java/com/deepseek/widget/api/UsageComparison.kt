package com.deepseek.widget.api

import java.time.LocalDate

object UsageComparison {

    fun normalize(
        source: List<DailyUsagePoint>,
        startDate: LocalDate,
        days: Int
    ): List<DailyUsagePoint> {
        require(days > 0) { "days must be positive" }
        val byDate = source.associateBy { it.date }
        return (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong()).toString()
            byDate[date] ?: DailyUsagePoint(date = date)
        }
    }

    fun percentage(current: Double, previous: Double): Double? {
        if (previous <= 0.0) return null
        return (current - previous) / previous * 100.0
    }
}
