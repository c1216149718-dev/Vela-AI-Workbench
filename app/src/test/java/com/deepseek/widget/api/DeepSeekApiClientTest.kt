package com.deepseek.widget.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekApiClientTest {

    private val client = DeepSeekApiClient()

    @Test
    fun parsesApiKeyFunUsageResponse() {
        val result = client.parseApiKeyFunUsage(
            """{"balance":12.345,"unit":"USD","usage":{"today":{"cost":1.2}}}"""
        )

        assertTrue(result.is_available)
        assertEquals("12.345", result.balance_infos.single().total_balance)
        assertEquals("USD", result.balance_infos.single().currency)
    }

    @Test
    fun parsesWrappedCompatibleUsageResponse() {
        val result = client.parseApiKeyFunUsage(
            """{"data":{"remaining":"8.50","currency":"CNY"}}"""
        )

        assertTrue(result.is_available)
        assertEquals("8.50", result.balance_infos.single().total_balance)
        assertEquals("CNY", result.balance_infos.single().currency)
    }

    @Test
    fun parsesDailyAndModelUsageWithoutBalanceFields() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "daily_usage": [
                {
                  "date": "2026-07-30",
                  "requests": 12,
                  "input_tokens": 1200,
                  "output_tokens": 300,
                  "total_tokens": 1500,
                  "actual_cost": 0.42
                }
              ],
              "model_stats": [
                {
                  "model": "deepseek-chat",
                  "requests": 12,
                  "total_tokens": 1500,
                  "actual_cost": 0.42
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.daily_usage.size)
        assertEquals(1500, result.daily_usage.single().total_tokens)
        assertEquals("deepseek-chat", result.model_stats.single().model)
        assertEquals(0.42, result.model_stats.single().actual_cost, 0.0001)
    }

    @Test
    fun acceptsEmptyUsagePayloadWithUnknownFields() {
        val result = client.parseApiKeyFunUsageDetails(
            """{"mode":"unrestricted","isValid":true,"balance":"8.50","unit":"USD"}"""
        )

        assertTrue(result.daily_usage.isEmpty())
        assertTrue(result.model_stats.isEmpty())
    }

    @Test
    fun parsesMultipleModelsAndSumsAllConsumption() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "daily_usage": [
                {"date":"2026-08-01","requests":10,"total_tokens":1000,"actual_cost":0.30},
                {"date":"2026-08-02","requests":5,"total_tokens":500,"actual_cost":0.15}
              ],
              "model_stats": [
                {"model":"deepseek-v4-flash","requests":12,"total_tokens":1200,"actual_cost":0.35},
                {"model":"deepseek-v4-pro","requests":3,"total_tokens":300,"actual_cost":0.10}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, result.model_stats.size)
        assertEquals(0.45, result.model_stats.sumOf { it.actual_cost }, 0.0001)
        assertEquals(0.45, result.daily_usage.sumOf { it.actual_cost }, 0.0001)
        assertEquals("deepseek-v4-flash", result.model_stats[0].model)
    }

    @Test
    fun parsesModelStatsAsMap() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "model_stats": {
                "deepseek-v4-flash": {"requests":7,"total_tokens":700,"actual_cost":0.21},
                "deepseek-v4-pro": {"requests":2,"total_tokens":200,"actual_cost":0.08}
              }
            }
            """.trimIndent()
        )

        assertEquals(2, result.model_stats.size)
        val total = result.model_stats.sumOf { it.actual_cost }
        assertEquals(0.29, total, 0.0001)
        assertTrue(result.model_stats.any { it.model == "deepseek-v4-flash" && it.requests == 7L })
    }

    @Test
    fun fallsBackToCostWhenActualCostMissing() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "model_stats": [
                {"model":"deepseek-v4-flash","requests":4,"total_tokens":400,"cost":0.18}
              ]
            }
            """.trimIndent()
        )

        assertEquals(0.18, result.model_stats.single().actual_cost, 0.0001)
    }

    @Test
    fun unwrapsUsageWrapperAndParsesModels() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "usage": {
                "model_stats": [
                  {"model":"deepseek-v4-flash","requests":9,"total_tokens":900,"actual_cost":0.27},
                  {"model":"deepseek-v4-pro","requests":1,"total_tokens":100,"actual_cost":0.05}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(2, result.model_stats.size)
        assertEquals(0.32, result.model_stats.sumOf { it.actual_cost }, 0.0001)
    }

    @Test
    fun keepsTopLevelDailyAndAllModelsWhenUsageSummaryExists() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "mode": "unrestricted",
              "balance": 25.50,
              "unit": "USD",
              "usage": {
                "today": {"requests": 6, "total_tokens": 600, "actual_cost": 0.12},
                "total": {"requests": 60, "total_tokens": 6000, "actual_cost": 1.20}
              },
              "daily_usage": [
                {"date":"2026-08-03","requests":6,"total_tokens":600,"actual_cost":0.12}
              ],
              "model_stats": [
                {"model":"claude-sonnet-4","requests":2,"total_tokens":200,"actual_cost":0.05},
                {"model":"gpt-5","requests":2,"total_tokens":220,"actual_cost":0.04},
                {"model":"gemini-2.5-pro","requests":1,"total_tokens":100,"actual_cost":0.02},
                {"model":"deepseek-v4-flash","requests":1,"total_tokens":80,"actual_cost":0.01}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.daily_usage.size)
        assertEquals(4, result.model_stats.size)
        assertEquals(
            setOf("claude-sonnet-4", "gpt-5", "gemini-2.5-pro", "deepseek-v4-flash"),
            result.model_stats.map { it.model }.toSet()
        )
        assertEquals(0.12, result.model_stats.sumOf { it.actual_cost }, 0.0001)
    }

    @Test
    fun prefersCompleteModelListOverEarlierSummary() {
        val result = client.parseApiKeyFunUsageDetails(
            """
            {
              "models": [{"model":"claude-sonnet-4","requests":2}],
              "data": {
                "model_stats": [
                  {"model":"claude-sonnet-4","requests":2},
                  {"model":"gpt-5","requests":3},
                  {"model":"gemini-2.5-pro","requests":4}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(3, result.model_stats.size)
        assertEquals(setOf("claude-sonnet-4", "gpt-5", "gemini-2.5-pro"), result.model_stats.map { it.model }.toSet())
    }
}
