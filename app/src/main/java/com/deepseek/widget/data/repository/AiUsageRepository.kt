package com.deepseek.widget.data.repository

import androidx.room.withTransaction
import com.deepseek.widget.api.BalanceDeltaAggregator
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.BalanceSnapshot
import com.deepseek.widget.data.local.WorkbenchDatabase
import com.deepseek.widget.data.local.entity.AiUsageSyncStateEntity
import com.deepseek.widget.data.local.entity.ProviderUsageFactEntity
import com.deepseek.widget.data.local.entity.ProviderBalanceSnapshotEntity
import com.deepseek.widget.data.local.entity.ProviderBillImportEntity
import com.deepseek.widget.data.provider.MetricProvenance
import com.deepseek.widget.data.provider.ProviderConnectorRegistry
import com.deepseek.widget.data.provider.ProviderId
import com.deepseek.widget.data.provider.ProviderProfileRepository
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.data.provider.ProviderResult
import com.deepseek.widget.data.provider.CustomProviderConnector
import com.deepseek.widget.data.provider.BillImportPreview
import com.deepseek.widget.data.provider.OfficialBillParser
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class UsageDailyRecord(
    val provider: ProviderId,
    val credentialId: String,
    val credentialLabel: String,
    val date: LocalDate,
    val currency: String,
    val cost: BigDecimal,
    val requests: Long,
    val totalTokens: Long,
    val provenance: MetricProvenance,
    val sourceId: String
) {
    val estimated: Boolean get() = provenance == MetricProvenance.BALANCE_DELTA_ESTIMATE
    val exact: Boolean get() = provenance == MetricProvenance.EXACT_API || provenance == MetricProvenance.EXACT_IMPORT
}

data class UsageModelRecord(
    val provider: ProviderId,
    val credentialId: String,
    val credentialLabel: String,
    val model: String,
    val currency: String,
    val cost: BigDecimal,
    val requests: Long,
    val totalTokens: Long,
    val provenance: MetricProvenance,
    val sourceId: String
) {
    val estimated: Boolean get() = provenance == MetricProvenance.BALANCE_DELTA_ESTIMATE
    val exact: Boolean get() = provenance == MetricProvenance.EXACT_API || provenance == MetricProvenance.EXACT_IMPORT
}

data class UsageSyncStatus(
    val provider: ProviderId,
    val credentialId: String,
    val credentialLabel: String,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long,
    val errorMessage: String,
    val status: String,
    val errorType: String
)

data class UsageSnapshot(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daily: List<UsageDailyRecord>,
    val previousDaily: List<UsageDailyRecord>,
    val models: List<UsageModelRecord>,
    val syncStates: List<UsageSyncStatus>
)

/** Provider-neutral v6 usage cache. */
class AiUsageRepository(
    private val database: WorkbenchDatabase,
    private val preferences: AppPreferences,
    private val profiles: ApiKeyFunProfileStore,
    private val apiClient: DeepSeekApiClient,
    private val providerProfiles: ProviderProfileRepository
) {
    private val dao = database.aiUsageDailyDao()
    private val connectorRegistry = ProviderConnectorRegistry.create()

    suspend fun previewOfficialBill(providerId: String, fileName: String, bytes: ByteArray): BillImportPreview {
        val provider = ProviderRegistry.canonicalId(providerId) ?: error("未知供应商")
        val preview = OfficialBillParser.preview(provider, fileName, bytes)
        return preview.copy(duplicate = dao.hasBillImport(provider.value, OfficialBillParser.sha256(bytes)))
    }

    suspend fun commitOfficialBill(profileId: String, fileName: String, bytes: ByteArray, preview: BillImportPreview) {
        require(preview.records.isNotEmpty()) { "账单中没有可导入记录" }
        val profile = providerProfiles.getProfile(profileId) ?: error("连接不存在")
        val provider = ProviderRegistry.canonicalId(profile.providerId) ?: error("未知供应商")
        require(provider == preview.providerId) { "账单供应商与连接不一致" }
        val hash = OfficialBillParser.sha256(bytes)
        require(!dao.hasBillImport(provider.value, hash)) { "该账单已导入" }
        val now = System.currentTimeMillis()
        val facts = preview.records.map { item ->
            fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.DAY, item.date, item.date, item.date,
                model = item.model, currency = item.currency, cost = item.cost, requests = item.requests,
                inputTokens = item.inputTokens, outputTokens = item.outputTokens, totalTokens = item.totalTokens,
                provenance = MetricProvenance.EXACT_IMPORT, sourceId = hash, now = now)
        }
        database.withTransaction {
            dao.upsertFacts(facts)
            dao.insertBillImport(
                ProviderBillImportEntity(UUID.randomUUID().toString(), provider.value, profile.id, fileName, hash,
                    preview.startDate.toString(), preview.endDate.toString(), facts.size, now)
            )
        }
    }

    fun observe(days: Int): Flow<UsageSnapshot> {
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        val comparisonStart = start.minusDays(days.toLong())
        return combine(
            dao.observeDailyFacts(comparisonStart.toString(), end.toString()),
            dao.observeModelFacts(start.toString(), end.toString()),
            dao.observeSyncStates()
        ) { daily, models, sync ->
            val mappedDaily = daily.mapNotNull { row ->
                    val date = row.bucketDate.toDateOrNull() ?: return@mapNotNull null
                    val provider = row.providerId.toProviderIdOrNull() ?: return@mapNotNull null
                    UsageDailyRecord(
                        provider, row.credentialId,
                        row.credentialLabel, date, row.currency, row.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        row.requests ?: 0, row.totalTokens ?: 0, row.provenance.toProvenance(), row.sourceId
                    )
                }
            // Imported bills are stored as dated facts so one import can serve every selectable
            // date range. Derive their model totals for the active range instead of materialising
            // a PERIOD row for only the range that happened to be selected during import.
            val importedModels = daily
                .asSequence()
                .filter { row ->
                    row.provenance == MetricProvenance.EXACT_IMPORT.name &&
                        row.model != ProviderUsageFactEntity.ALL_MODELS &&
                        row.bucketDate.toDateOrNull()?.let { !it.isBefore(start) && !it.isAfter(end) } == true
                }
                .groupBy { row ->
                    listOf(row.providerId, row.credentialId, row.credentialLabel, row.model, row.currency, row.provenance, row.sourceId)
                }
                .mapNotNull { (key, rows) ->
                    val provider = key[0].toProviderIdOrNull() ?: return@mapNotNull null
                    UsageModelRecord(
                        provider = provider,
                        credentialId = key[1],
                        credentialLabel = key[2],
                        model = key[3],
                        currency = key[4],
                        cost = rows.fold(BigDecimal.ZERO) { total, row -> total + (row.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                        requests = rows.sumOf { it.requests ?: 0L },
                        totalTokens = rows.sumOf { it.totalTokens ?: 0L },
                        provenance = key[5].toProvenance(),
                        sourceId = key[6]
                    )
                }
            UsageSnapshot(
                start,
                end,
                mappedDaily.filter { !it.date.isBefore(start) },
                mappedDaily.filter { it.date.isBefore(start) },
                models.mapNotNull { row ->
                    val provider = row.providerId.toProviderIdOrNull() ?: return@mapNotNull null
                    UsageModelRecord(
                        provider, row.credentialId,
                        row.credentialLabel, row.model, row.currency, row.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        row.requests ?: 0, row.totalTokens ?: 0, row.provenance.toProvenance(), row.sourceId
                    )
                } + importedModels,
                sync.mapNotNull { row ->
                    row.provider.toProviderIdOrNull()?.let { provider ->
                        UsageSyncStatus(provider, row.credentialId, row.credentialLabel, row.lastSuccessAt, row.lastAttemptAt, row.errorMessage, row.status, row.errorType)
                    }
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
        async { refreshConfiguredProviders(start, end, now) }
        profiles.getEnabledSecrets().chunked(3).forEach { batch ->
            batch.map { (profile, secret) ->
                async {
                    val result = apiClient.fetchApiKeyFunUsage(secret, days)
                    val report = result.getOrNull()
                    if (report != null) {
                        val daily = report.daily_usage.mapNotNull { point ->
                            val date = point.date.toDateOrNull() ?: return@mapNotNull null
                            if (date.isBefore(start) || date.isAfter(end)) return@mapNotNull null
                            fact(
                                ProviderRegistry.APIKEY_FUN, profile.id, profile.alias, ProviderUsageFactEntity.DAY,
                                date, date, date, currency = "USD", cost = BigDecimal.valueOf(point.actual_cost),
                                requests = point.requests, inputTokens = point.input_tokens, outputTokens = point.output_tokens,
                                totalTokens = point.total_tokens, provenance = MetricProvenance.EXACT_API,
                                sourceId = "apikey-fun-api", now = now
                            )
                        }
                        val models = report.model_stats.filter { it.model.isNotBlank() }.map { stat ->
                            fact(
                                ProviderRegistry.APIKEY_FUN, profile.id, profile.alias, ProviderUsageFactEntity.PERIOD,
                                start, end, model = stat.model.trim(), currency = "USD", cost = BigDecimal.valueOf(stat.actual_cost),
                                requests = stat.requests, inputTokens = stat.input_tokens, outputTokens = stat.output_tokens,
                                totalTokens = stat.total_tokens, provenance = MetricProvenance.EXACT_API,
                                sourceId = "apikey-fun-api", now = now
                            )
                        }
                        database.withTransaction {
                            dao.deleteFacts(ProviderRegistry.APIKEY_FUN.value, profile.id, ProviderUsageFactEntity.DAY, start.toString(), end.toString(), MetricProvenance.EXACT_API.name)
                            dao.deleteFacts(ProviderRegistry.APIKEY_FUN.value, profile.id, ProviderUsageFactEntity.PERIOD, start.toString(), end.toString(), MetricProvenance.EXACT_API.name)
                            if (daily.isNotEmpty()) dao.upsertFacts(daily)
                            if (models.isNotEmpty()) dao.upsertFacts(models)
                            dao.upsertSyncState(successState(ProviderRegistry.APIKEY_FUN, profile.id, profile.alias, start, end, now))
                        }
                    } else {
                        val old = dao.getSyncState(ProviderRegistry.APIKEY_FUN.value, profile.id)
                        dao.upsertSyncState(failureState(ProviderRegistry.APIKEY_FUN, profile.id, profile.alias, start, end, now, old?.lastSuccessAt, result.exceptionOrNull()?.message.orEmpty()))
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun refreshConfiguredProviders(start: LocalDate, end: LocalDate, now: Long) = coroutineScope {
        providerProfiles.getEnabledProfiles()
            .filterNot { it.providerId == ProviderRegistry.DEEPSEEK.value || it.providerId == ProviderRegistry.APIKEY_FUN.value }
            .chunked(3)
            .forEach { batch ->
                batch.map { profile -> async { refreshConfiguredProfile(profile, start, end, now) } }.awaitAll()
            }
    }

    private suspend fun refreshConfiguredProfile(
        profile: com.deepseek.widget.data.local.entity.ProviderProfileEntity,
        start: LocalDate,
        end: LocalDate,
        now: Long
    ) {
        val provider = ProviderRegistry.canonicalId(profile.providerId) ?: return
        val connector = if (provider == ProviderRegistry.CUSTOM) CustomProviderConnector.from(profile.configJson) else connectorRegistry.connector(provider.value)
        if (connector == null) return
        val values = providerProfiles.credentialsFor(profile)
        var successes = 0
        val errors = mutableListOf<String>()

        when (val balance = connector.syncBalance(values)) {
            is ProviderResult.Supported -> {
                balance.value.forEach { item ->
                    database.providerProfileDao().insertBalance(
                        ProviderBalanceSnapshotEntity(
                            provider.value, profile.id, now, item.currency.uppercase(), item.amount.toPlainString(),
                            item.provenance == MetricProvenance.BALANCE_DELTA_ESTIMATE, item.provenance.name,
                            item.accountFingerprint, item.cloudAccount
                        )
                    )
                }
                successes++
            }
            is ProviderResult.PartialFailure -> { errors += balance.message; successes++ }
            is ProviderResult.Failure -> errors += balance.message
            is ProviderResult.PermissionRequired -> errors += balance.reason
            is ProviderResult.Unsupported -> Unit
        }

        when (val costs = collectPages { cursor -> connector.syncActualCost(start, end, values, cursor) }) {
            is ProviderResult.Supported -> {
                val facts = costs.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.DAY, item.date, item.date, item.date,
                        currency = item.currency, cost = item.amount, provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                dao.deleteFacts(provider.value, profile.id, ProviderUsageFactEntity.DAY, start.toString(), end.toString(), MetricProvenance.EXACT_API.name)
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                successes++
            }
            is ProviderResult.PartialFailure -> {
                val facts = costs.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.DAY, item.date, item.date, item.date,
                        currency = item.currency, cost = item.amount, provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                errors += costs.message
                successes++
            }
            is ProviderResult.Failure -> errors += costs.message
            is ProviderResult.PermissionRequired -> errors += costs.reason
            is ProviderResult.Unsupported -> Unit
        }

        when (val usage = collectPages { cursor -> connector.syncDailyUsage(start, end, values, cursor) }) {
            is ProviderResult.Supported -> {
                val facts = usage.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.DAY, item.date, item.date, item.date,
                        model = item.model, currency = item.currency, cost = item.cost, requests = item.requests,
                        inputTokens = item.inputTokens, outputTokens = item.outputTokens, cachedTokens = item.cachedTokens,
                        totalTokens = item.totalTokens,
                        provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                successes++
            }
            is ProviderResult.PartialFailure -> {
                val facts = usage.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.DAY, item.date, item.date, item.date,
                        model = item.model, currency = item.currency, cost = item.cost, requests = item.requests,
                        inputTokens = item.inputTokens, outputTokens = item.outputTokens, cachedTokens = item.cachedTokens,
                        totalTokens = item.totalTokens,
                        provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                errors += usage.message
                successes++
            }
            is ProviderResult.Failure -> errors += usage.message
            is ProviderResult.PermissionRequired -> errors += usage.reason
            is ProviderResult.Unsupported -> Unit
        }

        when (val modelUsage = collectPages { cursor -> connector.syncModelUsage(start, end, values, cursor) }) {
            is ProviderResult.Supported -> {
                val facts = modelUsage.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.PERIOD, start, end,
                        model = item.model, currency = item.currency, cost = item.cost, requests = item.requests,
                        inputTokens = item.inputTokens, outputTokens = item.outputTokens, cachedTokens = item.cachedTokens,
                        totalTokens = item.totalTokens,
                        provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                successes++
            }
            is ProviderResult.PartialFailure -> {
                val facts = modelUsage.value.map { item ->
                    fact(provider, profile.id, profile.alias, ProviderUsageFactEntity.PERIOD, start, end,
                        model = item.model, currency = item.currency, cost = item.cost, requests = item.requests,
                        inputTokens = item.inputTokens, outputTokens = item.outputTokens, cachedTokens = item.cachedTokens,
                        totalTokens = item.totalTokens,
                        provenance = item.provenance, sourceId = item.sourceId, now = now)
                }
                if (facts.isNotEmpty()) dao.upsertFacts(facts)
                errors += modelUsage.message
                successes++
            }
            is ProviderResult.Failure -> errors += modelUsage.message
            is ProviderResult.PermissionRequired -> errors += modelUsage.reason
            is ProviderResult.Unsupported -> Unit
        }

        val old = dao.getSyncState(provider.value, profile.id)
        dao.upsertSyncState(
            if (errors.isEmpty()) successState(provider, profile.id, profile.alias, start, end, now)
            else AiUsageSyncStateEntity(
                provider.value, profile.id, profile.alias, start.toString(), end.toString(),
                if (successes > 0) now else old?.lastSuccessAt, now, errors.distinct().joinToString("；").take(180),
                if (successes > 0) "PARTIAL" else "FAILURE", "PROVIDER", now
            )
        )
    }

    /** Follow provider cursors without allowing a malformed API to loop forever. */
    private suspend fun <T> collectPages(
        load: suspend (String?) -> ProviderResult<com.deepseek.widget.data.provider.SyncPage<T>>
    ): ProviderResult<List<T>> {
        val items = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        repeat(100) {
            when (val page = load(cursor)) {
                is ProviderResult.Supported -> {
                    items += page.value.items
                    val next = page.value.nextCursor?.takeIf { it.isNotBlank() }
                        ?: return ProviderResult.Supported(items)
                    if (!seen.add(next)) {
                        return ProviderResult.PartialFailure(items, "供应商返回了重复分页游标", com.deepseek.widget.data.provider.SyncErrorType.INVALID_RESPONSE)
                    }
                    cursor = next
                }
                is ProviderResult.PartialFailure -> {
                    items += page.value.items
                    return ProviderResult.PartialFailure(items, page.message, page.errorType)
                }
                is ProviderResult.Failure -> return if (items.isEmpty()) page else ProviderResult.PartialFailure(items, page.message, page.errorType)
                is ProviderResult.PermissionRequired -> return page
                is ProviderResult.Unsupported -> return page
            }
        }
        return ProviderResult.PartialFailure(items, "分页超过安全上限，已保留前 100 页", com.deepseek.widget.data.provider.SyncErrorType.INVALID_RESPONSE)
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
                preferences.saveBalanceData(AccountProvider.DEEPSEEK, AccountCache(info.total_balance, info.granted_balance, info.topped_up_balance, info.currency, balance.is_available))
                info.total_balance.toDoubleOrNull()?.let { value -> preferences.addBalanceSnapshot(BalanceSnapshot(now, value, info.currency.ifBlank { "CNY" })) }
            } else refreshError = result.exceptionOrNull()?.message.orEmpty().take(180)
        }

        val entries = preferences.deepSeekUsageEntries.first().filter { it.date.toDateOrNull()?.let { date -> !date.isBefore(start) && !date.isAfter(end) } == true }
        val snapshots = preferences.deepSeekBalanceSnapshots.first()
        if (!configured && entries.isEmpty() && snapshots.isEmpty()) {
            MetricProvenance.entries.forEach { provenance ->
                dao.deleteFacts(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL, ProviderUsageFactEntity.DAY, start.toString(), end.toString(), provenance.name)
                dao.deleteFacts(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL, ProviderUsageFactEntity.PERIOD, start.toString(), end.toString(), provenance.name)
            }
            dao.deleteSyncState(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL)
            return
        }

        val currency = snapshots.lastOrNull()?.currency?.ifBlank { "CNY" } ?: "CNY"
        val provenance = if (entries.isNotEmpty()) MetricProvenance.LOCAL_CAPTURE else MetricProvenance.BALANCE_DELTA_ESTIMATE
        val daily = if (entries.isNotEmpty()) {
            entries.groupBy { it.date }.map { (dateText, rows) ->
                val date = LocalDate.parse(dateText)
                fact(
                    ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 本地账本", ProviderUsageFactEntity.DAY,
                    date, date, date, currency = currency,
                    cost = rows.fold(BigDecimal.ZERO) { total, row -> total + BigDecimal.valueOf(row.cost) },
                    requests = rows.size.toLong(), inputTokens = rows.sumOf { it.inputTokens }, outputTokens = rows.sumOf { it.outputTokens },
                    totalTokens = rows.sumOf { it.resolvedTotalTokens }, provenance = provenance,
                    sourceId = "deepseek-local-ledger", now = now
                )
            }
        } else {
            BalanceDeltaAggregator.dailyPoints(snapshots, start, ChronoUnit.DAYS.between(start, end).toInt() + 1)
                .filter { it.actual_cost > 0.0 }
                .map { point ->
                    val date = LocalDate.parse(point.date)
                    fact(
                        ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 余额快照", ProviderUsageFactEntity.DAY,
                        date, date, date, currency = currency, cost = BigDecimal.valueOf(point.actual_cost),
                        provenance = provenance, sourceId = "deepseek-balance-delta", now = now
                    )
                }
        }

        val models = if (entries.isNotEmpty()) {
            entries.groupBy { it.model.ifBlank { "未知模型" } }.map { (model, rows) ->
                fact(
                    ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 本地账本", ProviderUsageFactEntity.PERIOD,
                    start, end, model = model, currency = currency,
                    cost = rows.fold(BigDecimal.ZERO) { total, row -> total + BigDecimal.valueOf(row.cost) },
                    requests = rows.size.toLong(), inputTokens = rows.sumOf { it.inputTokens }, outputTokens = rows.sumOf { it.outputTokens },
                    totalTokens = rows.sumOf { it.resolvedTotalTokens }, provenance = provenance,
                    sourceId = "deepseek-local-ledger", now = now
                )
            }
        } else {
            val total = daily.fold(BigDecimal.ZERO) { sum, row -> sum + row.cost.toBigDecimal() }
            if (total > BigDecimal.ZERO) listOf(
                fact(
                    ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 余额快照", ProviderUsageFactEntity.PERIOD,
                    start, end, model = "余额扣减估算", currency = currency, cost = total,
                    provenance = provenance, sourceId = "deepseek-balance-delta", now = now
                )
            ) else emptyList()
        }

        database.withTransaction {
            MetricProvenance.entries.forEach { value ->
                dao.deleteFacts(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL, ProviderUsageFactEntity.DAY, start.toString(), end.toString(), value.name)
                dao.deleteFacts(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL, ProviderUsageFactEntity.PERIOD, start.toString(), end.toString(), value.name)
            }
            if (daily.isNotEmpty()) dao.upsertFacts(daily)
            if (models.isNotEmpty()) dao.upsertFacts(models)
            val old = dao.getSyncState(ProviderRegistry.DEEPSEEK.value, DEEPSEEK_LOCAL)
            dao.upsertSyncState(
                if (refreshError.isBlank()) successState(ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 本地账本", start, end, now)
                else failureState(ProviderRegistry.DEEPSEEK, DEEPSEEK_LOCAL, "DeepSeek 本地账本", start, end, now, old?.lastSuccessAt, refreshError)
            )
        }
    }

    private fun fact(
        provider: ProviderId,
        credentialId: String,
        label: String,
        kind: String,
        start: LocalDate,
        end: LocalDate,
        date: LocalDate? = null,
        model: String = ALL_MODELS,
        currency: String = "",
        cost: BigDecimal = BigDecimal.ZERO,
        requests: Long? = null,
        inputTokens: Long? = null,
        outputTokens: Long? = null,
        cachedTokens: Long? = null,
        totalTokens: Long? = null,
        provenance: MetricProvenance,
        sourceId: String,
        now: Long
    ) = ProviderUsageFactEntity(
        provider.value, credentialId, label, kind, start.toString(), end.toString(), date?.toString().orEmpty(),
        model, currency.uppercase(), cost.toPlainString(), requests, inputTokens, outputTokens, cachedTokens, totalTokens,
        provenance.name, sourceId, now
    )

    private fun successState(provider: ProviderId, id: String, label: String, start: LocalDate, end: LocalDate, now: Long) =
        AiUsageSyncStateEntity(provider.value, id, label, start.toString(), end.toString(), now, now, status = "SUCCESS", lastCompletedAt = now)

    private fun failureState(provider: ProviderId, id: String, label: String, start: LocalDate, end: LocalDate, now: Long, lastSuccess: Long?, message: String) =
        AiUsageSyncStateEntity(provider.value, id, label, start.toString(), end.toString(), lastSuccess, now, message.take(180), "FAILURE", "NETWORK", now)

    companion object {
        const val ALL_MODELS = ProviderUsageFactEntity.ALL_MODELS
        const val DEEPSEEK_LOCAL = "local-ledger"
    }
}

private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
private fun String.toProvenance(): MetricProvenance = runCatching { MetricProvenance.valueOf(this) }.getOrDefault(MetricProvenance.LOCAL_CAPTURE)
private fun String.toProviderIdOrNull(): ProviderId? = ProviderRegistry.canonicalId(this)
    ?: runCatching { ProviderId(lowercase()) }.getOrNull()
