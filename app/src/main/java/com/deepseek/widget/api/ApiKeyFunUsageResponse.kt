package com.deepseek.widget.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyFunUsageResponse(
    val daily_usage: List<DailyUsagePoint> = emptyList(),
    val model_stats: List<ModelUsageStat> = emptyList()
)

@Serializable
data class DailyUsagePoint(
    val date: String = "",
    val requests: Long = 0,
    val input_tokens: Long = 0,
    val output_tokens: Long = 0,
    val cache_read_tokens: Long = 0,
    val cache_write_tokens: Long = 0,
    val total_tokens: Long = 0,
    val cost: Double = 0.0,
    val actual_cost: Double = 0.0
)

@Serializable
data class ModelUsageStat(
    val model: String = "",
    val requests: Long = 0,
    val input_tokens: Long = 0,
    val output_tokens: Long = 0,
    val cache_creation_tokens: Long = 0,
    val cache_read_tokens: Long = 0,
    val total_tokens: Long = 0,
    val cost: Double = 0.0,
    val actual_cost: Double = 0.0,
    val account_cost: Double = 0.0
)
