package com.deepseek.widget.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

object TaskScheduleRules {
    val reminderOffsetsMinutes = listOf(300, 180, 120, 60, 30, 15, 10, 5, 0)

    fun validate(startAt: Long?, endAt: Long?, zoneId: ZoneId = ZoneId.systemDefault()) {
        require(startAt != null && endAt != null) { "请选择开始和截止时间" }
        require(endAt > startAt) { "截止时间必须晚于开始时间" }
    }

    fun reminderAt(startAt: Long?, offsetMinutes: Int?): Long? {
        if (startAt == null || offsetMinutes == null) return null
        require(offsetMinutes in reminderOffsetsMinutes) { "不支持的提醒时间" }
        return startAt - offsetMinutes * MINUTE_MILLIS
    }

    fun dateOf(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().toString()

    fun startOfDate(date: String, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(date).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private const val MINUTE_MILLIS = 60_000L
}
