package com.deepseek.widget.api

import com.deepseek.widget.data.BalanceSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 将余额快照序列聚合为每日费用估算。
 * 这里只统计同币种相邻快照的余额下降，余额上升视为充值或赠金变化。
 */
object BalanceDeltaAggregator {

    /**
     * 从完整快照列表生成指定区间内的每日费用估算。
     * 保留区间前的最近快照作为首日基线，避免漏掉跨日扣减。
     */
    fun dailyPoints(
        snapshots: List<BalanceSnapshot>,
        startDate: LocalDate,
        days: Int
    ): List<DailyUsagePoint> {
        require(days > 0) { "days must be positive" }
        val sorted = snapshots.sortedBy { it.timestamp }
        val zone = ZoneId.systemDefault()
        val endDate = startDate.plusDays(days.toLong() - 1)
        val spendingByDate = mutableMapOf<LocalDate, Double>()

        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1]
            val current = sorted[index]
            if (!previous.currency.equals(current.currency, ignoreCase = true)) continue

            val currentDate = Instant.ofEpochMilli(current.timestamp).atZone(zone).toLocalDate()
            if (currentDate < startDate || currentDate > endDate) continue

            val spent = previous.balance - current.balance
            if (spent > 0.0) {
                spendingByDate[currentDate] = spendingByDate.getOrDefault(currentDate, 0.0) + spent
            }
        }

        return UsageComparison.normalize(
            spendingByDate.map { (date, spent) ->
                DailyUsagePoint(
                    date = date.toString(),
                    actual_cost = spent,
                    cost = spent
                )
            },
            startDate,
            days
        )
    }

    /**
     * 从快照列表计算总消耗。
     */
    fun totalCost(snapshots: List<BalanceSnapshot>): Double {
        if (snapshots.size < 2) return 0.0
        val sorted = snapshots.sortedBy { it.timestamp }
        var total = 0.0
        for (i in 1 until sorted.size) {
            if (!sorted[i - 1].currency.equals(sorted[i].currency, ignoreCase = true)) continue
            val delta = sorted[i - 1].balance - sorted[i].balance
            if (delta > 0) total += delta
        }
        return total
    }

    /**
     * 余额接口无法提供模型维度，只返回一个明确标注为估算的费用汇总项。
     */
    fun modelStats(total: Double): List<ModelUsageStat> {
        if (total <= 0.0) return emptyList()
        return listOf(
            ModelUsageStat(
                model = "DeepSeek 余额扣减估算",
                requests = 0,
                total_tokens = 0,
                actual_cost = total,
                cost = total
            )
        )
    }
}
