package com.deepseek.widget.data.repository

import androidx.room.withTransaction
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.api.BalanceDeltaAggregator
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.BalanceSnapshot
import com.deepseek.widget.data.local.WorkbenchDatabase
import com.deepseek.widget.data.local.entity.AiUsageDailyEntity
import com.deepseek.widget.data.local.entity.AiUsageModelPeriodEntity
import com.deepseek.widget.data.local.entity.AiUsageSyncStateEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class UsageProvider { DEEPSEEK, APIKEY_FUN }

data class UsageDailyRecord(
    val provider: UsageProvider,
    val credentialId: String,
    val date: LocalDate,
    val currency: String,
    val cost: BigDecimal,
    val requests: Long,
    val totalTokens: Long,
    val estimated: Boolean
)

data class UsageModelRecord(
    val provider: UsageProvider,
    val credentialId: String,
    val credentialLabel: String,
    val model: String,
    val currency: String,
    val cost: BigDecimal,
    val requests: Long,
    val totalTokens: Long,
    val estimated: Boolean
)

data class UsageSyncStatus(
    val provider: UsageProvider,
    val credentialId: String,
    val credentialLabel: String,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long,
    val errorMessage: String
)

data class UsageSnapshot(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daily: List<UsageDailyRecord>,
    val models: List<UsageModelRecord>,
    val syncStates: List<UsageSyncStatus>
)

class AiUsageRepository(
    private val database: WorkbenchDatabase,
    private val preferences: AppPreferences,
    private val profiles: ApiKeyFunProfileStore,
    private val apiClient: DeepSeekApiClient
) {
    private val dao = database.aiUsageDailyDao()

    fun observe(days: Int): Flow<UsageSnapshot> {
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        return combine(
            dao.observeAllRange(start.toString(), end.toString()),
            dao.observeModelPeriod(start.toString(), end.toString()),
            dao.observeSyncStates()
        ) { daily, models, sync ->
            UsageSnapshot(
                startDate = start,
                endDate = end,
                daily = daily.mapNotNull { entity ->
                    val provider = entity.provider.toProviderOrNull() ?: return@mapNotNull null
                    val date = runCatching { LocalDate.parse(entity.date) }.getOrNull() ?: return@mapNotNull null
                    UsageDailyRecord(
                        provider, entity.credentialId, date, entity.currency,
                        entity.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        entity.requests ?: 0L, entity.totalTokens ?: 0L, entity.isEstimated
                    )
                },
                models = models.mapNotNull { entity ->
                    val provider = entity.provider.toProviderOrNull() ?: return@mapNotNull null
                    UsageModelRecord(
                        provider, entity.credentialId, entity.credentialLabel, entity.model,
                        entity.currency, entity.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        entity.requests ?: 0L, entity.totalTokens ?: 0L, entity.isEstimated
                    )
                },
                syncStates = sync.mapNotNull { entity ->
                    val provider = entity.provider.toProviderOrNull() ?: return@mapNotNull null
                    UsageSyncStatus(
                        provider, entity.credentialId, entity.credentialLabel,
                        entity.lastSuccessAt, entity.lastAttemptAt, entity.errorMessage
                    )
                }
            )
        }
    }

    suspend fun refresh(days: Int) = coroutineScope {
        require(days in 1..90)
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        val now = System.currentTimeMillis()

        async { refreshDeepSeek(start, end, now) }
        val enabled = profiles.getEnabledSecrets()
        enabled.chunked(3).forEach { batch ->
            batch.map { (profile, secret) ->
                async {
                    val result = apiClient.fetchApiKeyFunUsage(secret, days)
                    val report = result.getOrNull()
                    if (report != null) {
                        val daily = report.daily_usage.mapNotNull { point ->
                            val date = runCatching { LocalDate.parse(point.date) }.getOrNull()
                                ?: return@mapNotNull null
                            if (date.isBefore(start) || date.isAfter(end)) return@mapNotNull null
                            AiUsageDailyEntity(
                                provider = UsageProvider.APIKEY_FUN.name,
                                credentialId = profile.id,
                                date = date.toString(),
                                model = ALL_MODELS,
                                currency = "USD",
                                cost = BigDecimal.valueOf(point.actual_cost).toPlainString(),
                                requests = point.requests,
                                inputTokens = point.input_tokens,
                                outputTokens = point.output_tokens,
                                totalTokens = point.total_tokens,
                                updatedAt = now
                            )
                        }
                        val modelRows = report.model_stats.filter { it.model.isNotBlank() }.map { stat ->
                            AiUsageModelPeriodEntity(
                                provider = UsageProvider.APIKEY_FUN.name,
                                credentialId = profile.id,
                                credentialLabel = profile.alias,
                                periodStart = start.toString(),
                                periodEnd = end.toString(),
                                model = stat.model.trim(),
                                currency = "USD",
                                cost = BigDecimal.valueOf(stat.actual_cost).toPlainString(),
                                requests = stat.requests,
                                inputTokens = stat.input_tokens,
                                outputTokens = stat.output_tokens,
                                totalTokens = stat.total_tokens,
                                updatedAt = now
                            )
                        }
                        database.withTransaction {
                            dao.deleteRange(UsageProvider.APIKEY_FUN.name, profile.id, start.toString(), end.toString())
                            dao.deleteModelPeriod(UsageProvider.APIKEY_FUN.name, profile.id, start.toString(), end.toString())
                            if (daily.isNotEmpty()) dao.upsertAll(daily)
                            if (modelRows.isNotEmpty()) dao.upsertModelPeriods(modelRows)
                            dao.upsertSyncState(
                                AiUsageSyncStateEntity(
                                    UsageProvider.APIKEY_FUN.name, profile.id, profile.alias,
                                    start.toString(), end.toString(), now, now
                                )
                            )
                        }
                    } else {
                        val old = dao.getSyncState(UsageProvider.APIKEY_FUN.name, profile.id)
                        dao.upsertSyncState(
                            AiUsageSyncStateEntity(
                                UsageProvider.APIKEY_FUN.name, profile.id, profile.alias,
                                start.toString(), end.toString(), old?.lastSuccessAt, now,
                                result.exceptionOrNull()?.message.orEmpty().take(180)
                            )
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun refreshDeepSeek(start: LocalDate, end: LocalDate, now: Long) {
        val key = preferences.deepSeekApiKey.first()
        val configured = key.isNotBlank()
        var refreshError = ""
        if (configured) {
            val result = apiClient.fetchBalance(key)
            val balance = result.getOrNull()
            val info = balance?.balance_infos?.firstOrNull()
            if (balance != null && info != null) {
                preferences.saveBalanceData(
                    AccountProvider.DEEPSEEK,
                    AccountCache(
                        totalBalance = info.total_balance,
                        grantedBalance = info.granted_balance,
                        toppedUpBalance = info.topped_up_balance,
                        currency = info.currency,
                        isAvailable = balance.is_available
                    )
                )
                info.total_balance.toDoubleOrNull()?.let { value ->
                    preferences.addBalanceSnapshot(BalanceSnapshot(now, value, info.currency.ifBlank { "CNY" }))
                }
            } else {
                refreshError = result.exceptionOrNull()?.message.orEmpty().take(180)
            }
        }
        val entries = preferences.deepSeekUsageEntries.first().filter { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
        val snapshots = preferences.deepSeekBalanceSnapshots.first()
        if (!configured && entries.isEmpty() && snapshots.isEmpty()) {
            database.withTransaction {
                dao.deleteRange(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL, start.toString(), end.toString())
                dao.deleteModelPeriod(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL, start.toString(), end.toString())
                dao.deleteSyncState(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL)
            }
            return
        }
        val currency = snapshots.lastOrNull()?.currency?.ifBlank { "CNY" } ?: "CNY"
        val dailyRows = if (entries.isNotEmpty()) entries.groupBy { it.date }.map { (date, rows) ->
            AiUsageDailyEntity(
                provider = UsageProvider.DEEPSEEK.name,
                credentialId = DEEPSEEK_LOCAL,
                date = date,
                model = ALL_MODELS,
                currency = currency,
                cost = rows.fold(BigDecimal.ZERO) { total, row -> total + BigDecimal.valueOf(row.cost) }.toPlainString(),
                requests = rows.size.toLong(),
                inputTokens = rows.sumOf { it.inputTokens },
                outputTokens = rows.sumOf { it.outputTokens },
                totalTokens = rows.sumOf { it.resolvedTotalTokens },
                isEstimated = true,
                updatedAt = now
            )
        } else BalanceDeltaAggregator.dailyPoints(snapshots, start, ChronoUnit.DAYS.between(start, end).toInt() + 1)
            .filter { it.actual_cost > 0.0 }
            .map { point ->
                AiUsageDailyEntity(
                    provider = UsageProvider.DEEPSEEK.name,
                    credentialId = DEEPSEEK_LOCAL,
                    date = point.date,
                    model = ALL_MODELS,
                    currency = currency,
                    cost = BigDecimal.valueOf(point.actual_cost).toPlainString(),
                    isEstimated = true,
                    updatedAt = now
                )
            }
        val modelRows = if (entries.isNotEmpty()) entries.groupBy { it.model.ifBlank { "未知模型" } }.map { (model, rows) ->
            AiUsageModelPeriodEntity(
                provider = UsageProvider.DEEPSEEK.name,
                credentialId = DEEPSEEK_LOCAL,
                credentialLabel = "DeepSeek 本地账本",
                periodStart = start.toString(),
                periodEnd = end.toString(),
                model = model,
                currency = currency,
                cost = rows.fold(BigDecimal.ZERO) { total, row -> total + BigDecimal.valueOf(row.cost) }.toPlainString(),
                requests = rows.size.toLong(),
                inputTokens = rows.sumOf { it.inputTokens },
                outputTokens = rows.sumOf { it.outputTokens },
                totalTokens = rows.sumOf { it.resolvedTotalTokens },
                isEstimated = true,
                updatedAt = now
            )
        } else {
            val total = dailyRows.fold(BigDecimal.ZERO) { sum, row -> sum + row.cost.toBigDecimal() }
            if (total > BigDecimal.ZERO) listOf(
                AiUsageModelPeriodEntity(
                    provider = UsageProvider.DEEPSEEK.name,
                    credentialId = DEEPSEEK_LOCAL,
                    credentialLabel = "DeepSeek 余额快照",
                    periodStart = start.toString(),
                    periodEnd = end.toString(),
                    model = "余额扣减估算",
                    currency = currency,
                    cost = total.toPlainString(),
                    isEstimated = true,
                    updatedAt = now
                )
            ) else emptyList()
        }
        database.withTransaction {
            dao.deleteRange(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL, start.toString(), end.toString())
            dao.deleteModelPeriod(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL, start.toString(), end.toString())
            if (dailyRows.isNotEmpty()) dao.upsertAll(dailyRows)
            if (modelRows.isNotEmpty()) dao.upsertModelPeriods(modelRows)
            val old = dao.getSyncState(UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL)
            dao.upsertSyncState(
                AiUsageSyncStateEntity(
                    UsageProvider.DEEPSEEK.name, DEEPSEEK_LOCAL, "DeepSeek 本地账本",
                    start.toString(), end.toString(),
                    if (refreshError.isBlank()) now else old?.lastSuccessAt,
                    now,
                    refreshError
                )
            )
        }
    }

    companion object {
        const val ALL_MODELS = "__all__"
        const val DEEPSEEK_LOCAL = "local-ledger"
    }
}

private fun String.toProviderOrNull(): UsageProvider? =
    runCatching { UsageProvider.valueOf(this) }.getOrNull()
