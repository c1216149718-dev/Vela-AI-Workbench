package com.deepseek.widget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.widget.data.DeepSeekUsageLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AccountCache(
    val totalBalance: String = "",
    val grantedBalance: String = "",
    val toppedUpBalance: String = "",
    val currency: String = "",
    val isAvailable: Boolean = false,
    val lastUpdated: Long = 0L,
    val errorMessage: String = ""
)

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val KEY_APIKEY_FUN_API_KEY = stringPreferencesKey("apikey_fun_api_key")
        private val KEY_REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        private val KEY_USAGE_RANGE_DAYS = intPreferencesKey("usage_range_days")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_FOCUS_TIMER_STYLE = stringPreferencesKey("focus_timer_style")
        private val KEY_DEEPSEEK_USAGE_LEDGER = stringPreferencesKey("deepseek_usage_ledger")
        private val KEY_DEEPSEEK_BALANCE_SNAPSHOTS = stringPreferencesKey("deepseek_balance_snapshots")
        private const val DEFAULT_REFRESH_INTERVAL = 30
        private const val DEFAULT_USAGE_RANGE_DAYS = 7

        private fun key(prefix: String, name: String) = stringPreferencesKey("${prefix}_$name")
        private fun boolKey(prefix: String, name: String) = booleanPreferencesKey("${prefix}_$name")
        private fun longKey(prefix: String, name: String) = longPreferencesKey("${prefix}_$name")
    }

    val deepSeekApiKey: Flow<String> = context.dataStore.data.map { it[KEY_DEEPSEEK_API_KEY].orEmpty() }
    val apiKeyFunApiKey: Flow<String> = context.dataStore.data.map { it[KEY_APIKEY_FUN_API_KEY].orEmpty() }
    val refreshIntervalMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_REFRESH_INTERVAL_MINUTES] ?: DEFAULT_REFRESH_INTERVAL
    }
    val usageRangeDays: Flow<Int> = context.dataStore.data.map {
        it[KEY_USAGE_RANGE_DAYS] ?: DEFAULT_USAGE_RANGE_DAYS
    }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.storageValue == prefs[KEY_THEME_MODE] } ?: ThemeMode.SYSTEM
    }
    val focusTimerStyle: Flow<FocusTimerStyle> = context.dataStore.data.map { prefs ->
        when (val saved = prefs[KEY_FOCUS_TIMER_STYLE]) {
            "JOVIAN" -> FocusTimerStyle.ARES
            "RINGWORLD" -> FocusTimerStyle.EUROPA
            "COMET" -> FocusTimerStyle.LUNA
            else -> FocusTimerStyle.entries.firstOrNull { it.name == saved } ?: FocusTimerStyle.LUNA
        }
    }

    val deepSeekUsageEntries: Flow<List<DeepSeekUsageEntry>> = context.dataStore.data.map { prefs ->
        DeepSeekUsageLedger.decode(prefs[KEY_DEEPSEEK_USAGE_LEDGER].orEmpty())
    }

    val deepSeekBalanceSnapshots: Flow<List<BalanceSnapshot>> = context.dataStore.data.map { prefs ->
        BalanceSnapshotLedger.decode(prefs[KEY_DEEPSEEK_BALANCE_SNAPSHOTS].orEmpty())
    }

    fun accountCache(provider: AccountProvider): Flow<AccountCache> = context.dataStore.data.map { prefs ->
        AccountCache(
            totalBalance = prefs[key(provider.prefix, "total_balance")].orEmpty(),
            grantedBalance = prefs[key(provider.prefix, "granted_balance")].orEmpty(),
            toppedUpBalance = prefs[key(provider.prefix, "topped_up_balance")].orEmpty(),
            currency = prefs[key(provider.prefix, "currency")].orEmpty(),
            isAvailable = prefs[boolKey(provider.prefix, "is_available")] ?: false,
            lastUpdated = prefs[longKey(provider.prefix, "last_updated")] ?: 0L,
            errorMessage = prefs[key(provider.prefix, "error_message")].orEmpty()
        )
    }

    suspend fun setDeepSeekApiKey(apiKey: String) = context.dataStore.edit {
        it[KEY_DEEPSEEK_API_KEY] = apiKey
    }

    suspend fun setApiKeyFunApiKey(apiKey: String) = context.dataStore.edit {
        it[KEY_APIKEY_FUN_API_KEY] = apiKey
    }

    suspend fun setRefreshIntervalMinutes(minutes: Int) = context.dataStore.edit {
        it[KEY_REFRESH_INTERVAL_MINUTES] = minutes
    }

    suspend fun setUsageRangeDays(days: Int) = context.dataStore.edit {
        it[KEY_USAGE_RANGE_DAYS] = days
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit {
        it[KEY_THEME_MODE] = mode.storageValue
    }

    suspend fun setFocusTimerStyle(style: FocusTimerStyle) = context.dataStore.edit {
        it[KEY_FOCUS_TIMER_STYLE] = style.name
    }

    suspend fun saveBalanceData(provider: AccountProvider, cache: AccountCache) = context.dataStore.edit { prefs ->
        prefs[key(provider.prefix, "total_balance")] = cache.totalBalance
        prefs[key(provider.prefix, "granted_balance")] = cache.grantedBalance
        prefs[key(provider.prefix, "topped_up_balance")] = cache.toppedUpBalance
        prefs[key(provider.prefix, "currency")] = cache.currency
        prefs[boolKey(provider.prefix, "is_available")] = cache.isAvailable
        prefs[longKey(provider.prefix, "last_updated")] = System.currentTimeMillis()
        prefs[key(provider.prefix, "error_message")] = ""
    }

    suspend fun setError(provider: AccountProvider, message: String) = context.dataStore.edit { prefs ->
        prefs[key(provider.prefix, "error_message")] = message
    }

    suspend fun addDeepSeekUsageEntry(entry: DeepSeekUsageEntry) = context.dataStore.edit { prefs ->
        val current = DeepSeekUsageLedger.decode(prefs[KEY_DEEPSEEK_USAGE_LEDGER].orEmpty()).toMutableList()
        current.add(entry)
        prefs[KEY_DEEPSEEK_USAGE_LEDGER] = DeepSeekUsageLedger.encode(current)
    }

    suspend fun removeDeepSeekUsageEntry(id: Long) = context.dataStore.edit { prefs ->
        val current = DeepSeekUsageLedger.decode(prefs[KEY_DEEPSEEK_USAGE_LEDGER].orEmpty())
        prefs[KEY_DEEPSEEK_USAGE_LEDGER] = DeepSeekUsageLedger.encode(current.filterNot { it.id == id })
    }

    suspend fun clearDeepSeekUsageLedger() = context.dataStore.edit { prefs ->
        prefs[KEY_DEEPSEEK_USAGE_LEDGER] = ""
    }

    suspend fun addBalanceSnapshot(snapshot: BalanceSnapshot) = context.dataStore.edit { prefs ->
        val current = BalanceSnapshotLedger.decode(prefs[KEY_DEEPSEEK_BALANCE_SNAPSHOTS].orEmpty()).toMutableList()
        val latest = current.maxByOrNull { it.timestamp }
        if (latest != null && latest.balance == snapshot.balance && latest.currency == snapshot.currency) {
            return@edit
        }
        current.add(snapshot)
        // 保留最近 90 天的快照
        val cutoff = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val trimmed = current.filter { it.timestamp >= cutoff }
        prefs[KEY_DEEPSEEK_BALANCE_SNAPSHOTS] = BalanceSnapshotLedger.encode(trimmed)
    }

    suspend fun clearBalanceSnapshots() = context.dataStore.edit { prefs ->
        prefs[KEY_DEEPSEEK_BALANCE_SNAPSHOTS] = ""
    }
}

enum class AccountProvider(val prefix: String) {
    DEEPSEEK("deepseek"),
    APIKEY_FUN("apikey_fun")
}

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark")
}

enum class FocusTimerStyle { LUNA, ARES, EUROPA }
