package com.deepseek.widget.feature.insights

import com.deepseek.widget.data.repository.UsageModelRecord
import com.deepseek.widget.data.repository.UsageProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InsightsAggregationTest {
    @Test fun `model overflow keeps top five and merges the remainder`() {
        val models = (1..8).map { index ->
            UsageModelRecord(
                provider = UsageProvider.APIKEY_FUN,
                credentialId = "key-$index",
                credentialLabel = "Key $index",
                model = "model-$index",
                currency = "USD",
                cost = BigDecimal(index),
                requests = index.toLong(),
                totalTokens = index * 100L,
                estimated = false
            )
        }
        val ranked = rankModels(models, UsageMetric.COST)
        assertEquals(6, ranked.size)
        assertEquals("model-8", ranked.first().model)
        assertEquals("其他", ranked.last().model)
        assertEquals(BigDecimal("6"), ranked.last().cost)
    }

    @Test fun `axis labels are stable epoch dates and never empty`() {
        val date = LocalDate.of(2026, 8, 12)
        assertEquals("08-12", formatUsageAxisDate(date.toEpochDay().toDouble()))
        assertEquals("--", formatUsageAxisDate(Double.NaN))
    }

    @Test fun `cost ranking can be separated by currency before aggregation`() {
        val mixed = listOf(
            UsageModelRecord(UsageProvider.DEEPSEEK, "deepseek", "DeepSeek", "deepseek-chat", "CNY", BigDecimal("3.00"), 1, 100, true),
            UsageModelRecord(UsageProvider.APIKEY_FUN, "key-1", "Key 1", "claude", "USD", BigDecimal("9.00"), 2, 200, false)
        )
        val usd = rankModels(mixed.filter { it.currency == "USD" }, UsageMetric.COST)
        assertEquals(1, usd.size)
        assertEquals("USD", usd.single().currency)
        assertEquals(BigDecimal("9.00"), usd.single().cost)
    }
}
