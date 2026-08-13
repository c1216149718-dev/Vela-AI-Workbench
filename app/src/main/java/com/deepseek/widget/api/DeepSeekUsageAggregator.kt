package com.deepseek.widget.api

import com.deepseek.widget.data.DeepSeekUsageEntry
import java.time.LocalDate

/**
 * 把 DeepSeek 本地账本聚合成与 APIKEY.FUN 一致的结构，复用 UsageTrendView 与模型对比渲染器。
 *
 * - current: 最近 [days] 天
 * - previous: 再往前等长窗口，用于环比
 */
object DeepSeekUsageAggregator {

    fun dailyPoints(
        entries: List<DeepSeekUsageEntry>,
        startDate: LocalDate,
        days: Int
    ): List<DailyUsagePoint> {
        require(days > 0) { "days must be positive" }
        val byDate = entries.filter { it.date.isNotBlank() }
            .groupBy { it.date }
            .mapValues { (_, list) ->
                DailyUsagePoint(
                    date = list.first().date,
                    requests = list.size.toLong(),
                    input_tokens = list.sumOf { it.inputTokens },
                    output_tokens = list.sumOf { it.outputTokens },
                    total_tokens = list.sumOf { it.resolvedTotalTokens },
                    actual_cost = list.sumOf { it.cost }
                )
            }
        return UsageComparison.normalize(byDate.values.toList(), startDate, days)
    }

    fun modelStats(entries: List<DeepSeekUsageEntry>): List<ModelUsageStat> =
        entries.groupBy { it.model.ifBlank { "未知模型" } }
            .map { (model, list) ->
                ModelUsageStat(
                    model = model,
                    requests = list.size.toLong(),
                    input_tokens = list.sumOf { it.inputTokens },
                    output_tokens = list.sumOf { it.outputTokens },
                    total_tokens = list.sumOf { it.resolvedTotalTokens },
                    actual_cost = list.sumOf { it.cost }
                )
            }
            .sortedByDescending { it.actual_cost }

    fun totalCost(points: List<DailyUsagePoint>): Double = points.sumOf { it.actual_cost }
    fun totalRequests(points: List<DailyUsagePoint>): Long = points.sumOf { it.requests }
    fun totalTokens(points: List<DailyUsagePoint>): Long = points.sumOf { it.total_tokens }
}
