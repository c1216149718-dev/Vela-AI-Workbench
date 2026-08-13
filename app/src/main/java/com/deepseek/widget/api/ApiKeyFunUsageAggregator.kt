package com.deepseek.widget.api

object ApiKeyFunUsageAggregator {

    fun aggregate(reports: List<ApiKeyFunUsageResponse>): ApiKeyFunUsageResponse =
        ApiKeyFunUsageResponse(
            daily_usage = reports.flatMap { it.daily_usage }
                .filter { it.date.isNotBlank() }
                .groupBy { it.date }
                .map { (date, points) ->
                    DailyUsagePoint(
                        date = date,
                        requests = points.sumOf { it.requests },
                        input_tokens = points.sumOf { it.input_tokens },
                        output_tokens = points.sumOf { it.output_tokens },
                        cache_read_tokens = points.sumOf { it.cache_read_tokens },
                        cache_write_tokens = points.sumOf { it.cache_write_tokens },
                        total_tokens = points.sumOf { it.total_tokens },
                        cost = points.sumOf { it.cost },
                        actual_cost = points.sumOf { it.actual_cost }
                    )
                }
                .sortedBy { it.date },
            model_stats = reports.flatMap { it.model_stats }
                .filter { it.model.isNotBlank() }
                .groupBy { it.model.trim() }
                .map { (model, stats) ->
                    ModelUsageStat(
                        model = model,
                        requests = stats.sumOf { it.requests },
                        input_tokens = stats.sumOf { it.input_tokens },
                        output_tokens = stats.sumOf { it.output_tokens },
                        cache_creation_tokens = stats.sumOf { it.cache_creation_tokens },
                        cache_read_tokens = stats.sumOf { it.cache_read_tokens },
                        total_tokens = stats.sumOf { it.total_tokens },
                        cost = stats.sumOf { it.cost },
                        actual_cost = stats.sumOf { it.actual_cost },
                        account_cost = stats.sumOf { it.account_cost }
                    )
                }
                .sortedByDescending { it.actual_cost }
        )
}
