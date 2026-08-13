package com.deepseek.widget.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyFunUsageAggregatorTest {

    @Test
    fun aggregatesDatesAndAllModelsAcrossKeys() {
        val result = ApiKeyFunUsageAggregator.aggregate(
            listOf(
                ApiKeyFunUsageResponse(
                    daily_usage = listOf(DailyUsagePoint(date = "2026-08-05", requests = 2, actual_cost = 0.2)),
                    model_stats = listOf(ModelUsageStat(model = "claude-sonnet-4", requests = 2, actual_cost = 0.2))
                ),
                ApiKeyFunUsageResponse(
                    daily_usage = listOf(DailyUsagePoint(date = "2026-08-05", requests = 3, actual_cost = 0.3)),
                    model_stats = listOf(
                        ModelUsageStat(model = "gpt-5", requests = 1, actual_cost = 0.1),
                        ModelUsageStat(model = "claude-sonnet-4", requests = 2, actual_cost = 0.2)
                    )
                )
            )
        )

        assertEquals(5, result.daily_usage.single().requests)
        assertEquals(0.5, result.daily_usage.single().actual_cost, 0.0001)
        assertEquals(setOf("claude-sonnet-4", "gpt-5"), result.model_stats.map { it.model }.toSet())
        assertEquals(4, result.model_stats.first { it.model == "claude-sonnet-4" }.requests)
    }
}
