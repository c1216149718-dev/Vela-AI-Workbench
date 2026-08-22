package com.deepseek.widget.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.repository.AiUsageRepository
import com.deepseek.widget.data.repository.UsageDailyRecord
import com.deepseek.widget.data.repository.UsageModelRecord
import com.deepseek.widget.data.provider.ProviderId
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.data.provider.ProviderProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class UsageContentState { LOADING, CONTENT, EMPTY, PARTIAL, STALE, UNCONFIGURED }
enum class UsageMetric { COST, REQUESTS, TOKENS }

data class InsightsUiState(
    val deepSeekAccount: AccountCache = AccountCache(),
    val apiKeyFunAccount: AccountCache = AccountCache(),
    val selectedDays: Int = 7,
    val startDate: LocalDate = LocalDate.now().minusDays(6),
    val endDate: LocalDate = LocalDate.now(),
    val totalsByCurrency: Map<String, BigDecimal> = emptyMap(),
    val estimatedTotalsByCurrency: Map<String, BigDecimal> = emptyMap(),
    val providerTotals: Map<ProviderId, Map<String, BigDecimal>> = emptyMap(),
    val providerEstimatedTotals: Map<ProviderId, Map<String, BigDecimal>> = emptyMap(),
    val daily: List<UsageDailyRecord> = emptyList(),
    val previousDaily: List<UsageDailyRecord> = emptyList(),
    val models: List<UsageModelRecord> = emptyList(),
    val usageEntryCount: Int = 0,
    val totalRequests: Long = 0,
    val totalTokens: Long = 0,
    val lastRefreshAt: Long? = null,
    val failedSources: Int = 0,
    val configuredSources: Int = 0,
    val connectedProviders: Set<ProviderId> = emptySet(),
    val contentState: UsageContentState = UsageContentState.LOADING,
    val isRefreshing: Boolean = false
)

class InsightsViewModel(
    private val repository: AiUsageRepository,
    private val appPreferences: AppPreferences,
    profiles: ApiKeyFunProfileStore,
    providerProfiles: ProviderProfileRepository
) : ViewModel() {
    private val selectedDays = MutableStateFlow(7)
    private val refreshing = MutableStateFlow(false)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    private val snapshot = selectedDays.flatMapLatest(repository::observe)
    private val accountPair = combine(
        appPreferences.accountCache(AccountProvider.DEEPSEEK),
        appPreferences.accountCache(AccountProvider.APIKEY_FUN)
    ) { deepSeek, apiKeyFun -> deepSeek to apiKeyFun }
    private val configuration = combine(
        appPreferences.deepSeekApiKey,
        providerProfiles.observeProfiles()
    ) { deepSeekKey, unifiedProfiles ->
        val ids = unifiedProfiles.filter { it.enabled }.mapNotNull { ProviderRegistry.canonicalId(it.providerId) }.toSet()
        val withLegacyDeepSeek = if (deepSeekKey.isNotBlank()) ids + ProviderRegistry.DEEPSEEK else ids
        withLegacyDeepSeek.size to withLegacyDeepSeek
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        snapshot, accountPair, configuration, selectedDays, refreshing
    ) { data, accounts, configurationState, days, isRefreshing ->
        val (configured, connectedProviders) = configurationState
        val totalByCurrency = data.daily.filter { it.exact }.groupBy { it.currency.normalizedCurrency() }
            .mapValues { (_, rows) -> rows.sumMoney() }
        val estimatesByCurrency = data.daily.filterNot { it.exact }.groupBy { it.currency.normalizedCurrency() }
            .mapValues { (_, rows) -> rows.sumMoney() }
        val providerTotals = data.daily.map { it.provider }.distinct().associateWith { provider ->
            data.daily.filter { it.provider == provider && it.exact }
                .groupBy { it.currency.normalizedCurrency() }
                .mapValues { (_, rows) -> rows.sumMoney() }
        }
        val providerEstimatedTotals = data.daily.map { it.provider }.distinct().associateWith { provider ->
            data.daily.filter { it.provider == provider && !it.exact }
                .groupBy { it.currency.normalizedCurrency() }
                .mapValues { (_, rows) -> rows.sumMoney() }
        }
        val relevantSync = data.syncStates.filter { state ->
            state.provider == ProviderRegistry.DEEPSEEK || state.credentialId.isNotBlank()
        }
        val failed = relevantSync.count { it.errorMessage.isNotBlank() }
        val lastSuccess = relevantSync.mapNotNull { it.lastSuccessAt }.maxOrNull()
        val state = when {
            isRefreshing && data.daily.isEmpty() -> UsageContentState.LOADING
            configured == 0 -> UsageContentState.UNCONFIGURED
            data.daily.isEmpty() && lastSuccess == null -> UsageContentState.EMPTY
            failed > 0 && data.daily.isNotEmpty() -> UsageContentState.PARTIAL
            failed > 0 -> UsageContentState.STALE
            data.daily.isEmpty() -> UsageContentState.EMPTY
            else -> UsageContentState.CONTENT
        }
        InsightsUiState(
            deepSeekAccount = accounts.first,
            apiKeyFunAccount = accounts.second,
            selectedDays = days,
            startDate = data.startDate,
            endDate = data.endDate,
            totalsByCurrency = totalByCurrency,
            estimatedTotalsByCurrency = estimatesByCurrency,
            providerTotals = providerTotals,
            providerEstimatedTotals = providerEstimatedTotals,
            daily = data.daily,
            previousDaily = data.previousDaily,
            models = data.models,
            usageEntryCount = data.daily.size,
            totalRequests = data.daily.sumOf { it.requests },
            totalTokens = data.daily.sumOf { it.totalTokens },
            lastRefreshAt = lastSuccess,
            failedSources = failed,
            configuredSources = configured,
            connectedProviders = connectedProviders,
            contentState = state,
            isRefreshing = isRefreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    init {
        viewModelScope.launch {
            val saved = appPreferences.usageRangeDays.first().coerceIn(1, 90)
            selectedDays.value = saved
            refresh()
        }
    }

    fun selectDays(days: Int) {
        if (days !in 1..90 || days == selectedDays.value) return
        selectedDays.value = days
        viewModelScope.launch { appPreferences.setUsageRangeDays(days) }
        launchRefresh(days)
    }

    fun refresh() = launchRefresh(selectedDays.value)

    private fun launchRefresh(days: Int) {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh(days)
            } finally {
                if (generation == refreshGeneration) refreshing.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: AiUsageRepository,
            appPreferences: AppPreferences,
            profiles: ApiKeyFunProfileStore,
            providerProfiles: ProviderProfileRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InsightsViewModel(repository, appPreferences, profiles, providerProfiles) as T
        }
    }
}

private fun String.normalizedCurrency(): String = uppercase().ifBlank { "USD" }
private fun List<UsageDailyRecord>.sumMoney(): BigDecimal =
    fold(BigDecimal.ZERO) { total, row -> total + row.cost }

data class RankedModel(
    val provider: ProviderId?,
    val credentialLabel: String,
    val model: String,
    val currency: String,
    val cost: BigDecimal,
    val requests: Long,
    val totalTokens: Long,
    val isOther: Boolean = false
)

internal fun rankModels(
    models: List<UsageModelRecord>,
    metric: UsageMetric,
    visibleCount: Int = 5
): List<RankedModel> {
    val sorted = sortModels(models, metric)
    if (sorted.size <= visibleCount) return sorted
    val head = sorted.take(visibleCount)
    val tail = sorted.drop(visibleCount)
    val currencies = tail.map { it.currency }.distinct()
    val otherCurrency = currencies.singleOrNull() ?: "MIXED"
    return head + RankedModel(
        provider = tail.mapNotNull { it.provider }.distinct().singleOrNull(),
        credentialLabel = "多个 Key",
        model = "其他",
        currency = otherCurrency,
        cost = tail.fold(BigDecimal.ZERO) { total, row -> total + row.cost },
        requests = tail.sumOf { it.requests },
        totalTokens = tail.sumOf { it.totalTokens },
        isOther = true
    )
}

internal fun sortModels(models: List<UsageModelRecord>, metric: UsageMetric): List<RankedModel> =
    models.map {
        RankedModel(it.provider, it.credentialLabel, it.model, it.currency, it.cost, it.requests, it.totalTokens)
    }.sortedByDescending { it.metricValue(metric) }

internal fun RankedModel.metricValue(metric: UsageMetric): Double = when (metric) {
    UsageMetric.COST -> cost.toDouble()
    UsageMetric.REQUESTS -> requests.toDouble()
    UsageMetric.TOKENS -> totalTokens.toDouble()
}

internal fun RankedModel.compactMetricText(metric: UsageMetric): String = when (metric) {
    UsageMetric.COST -> if (currency == "MIXED") "分币种" else formatCurrencyTotals(mapOf(currency to cost))
    UsageMetric.REQUESTS -> "${formatLong(requests)} 次"
    UsageMetric.TOKENS -> "${formatLong(totalTokens)} Token"
}

internal fun RankedModel.providerLabel(): String = when (provider) {
    ProviderRegistry.DEEPSEEK -> "DeepSeek"
    ProviderRegistry.APIKEY_FUN -> "APIKEY.FUN"
    null -> "多平台"
    else -> ProviderRegistry.descriptor(provider.value)?.displayName ?: provider.value
}
