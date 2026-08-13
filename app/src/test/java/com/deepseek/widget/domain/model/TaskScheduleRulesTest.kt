package com.deepseek.widget.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskScheduleRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 9)

    @Test
    fun reminderUsesStartTimeAndOffset() {
        val start = date.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(start - 30 * 60_000L, TaskScheduleRules.reminderAt(start, 30))
        assertEquals(start, TaskScheduleRules.reminderAt(start, 0))
        assertNull(TaskScheduleRules.reminderAt(start, null))
    }

    @Test
    fun intervalCanCrossMidnight() {
        val start = date.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        TaskScheduleRules.validate(start, end, zone)
    }

    @Test
    fun intervalCanSpanMultipleDays() {
        val start = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(14).atTime(8, 1).atZone(zone).toInstant().toEpochMilli()
        TaskScheduleRules.validate(start, end, zone)
    }
}
