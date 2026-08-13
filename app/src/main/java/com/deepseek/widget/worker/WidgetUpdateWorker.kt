package com.deepseek.widget.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deepseek.widget.DeepSeekWidgetProvider
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.BalanceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val apiClient = DeepSeekApiClient()
    private val prefs = AppPreferences(context)

    override suspend fun doWork(): Result = try {
        supervisorScope {
            val profilesStore = ApiKeyFunProfileStore.create(applicationContext)
            profilesStore.migrateFromLegacy()
            listOf(
                AccountProvider.DEEPSEEK to prefs.deepSeekApiKey.first(),
                AccountProvider.APIKEY_FUN to profilesStore.getPrimarySecret().orEmpty()
            ).map { (provider, apiKey) ->
                async { refreshAccount(provider, apiKey) }
            }.awaitAll()
            DeepSeekWidgetProvider.requestUpdate(applicationContext)
        }
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Widget refresh failed", e)
        Result.retry()
    }

    private suspend fun refreshAccount(provider: AccountProvider, apiKey: String) {
        if (apiKey.isBlank()) return
        try {
            val result = when (provider) {
                AccountProvider.DEEPSEEK -> apiClient.fetchBalance(apiKey)
                AccountProvider.APIKEY_FUN -> apiClient.fetchApiKeyFunBalance(apiKey)
            }
            result.fold(
                onSuccess = { balance ->
                    val info = balance.balance_infos.firstOrNull()
                    if (info == null) {
                        prefs.setError(provider, "未返回余额数据")
                    } else {
                        prefs.saveBalanceData(provider, AccountCache(
                            totalBalance = info.total_balance,
                            grantedBalance = info.granted_balance,
                            toppedUpBalance = info.topped_up_balance,
                            currency = info.currency,
                            isAvailable = balance.is_available
                        ))
                        if (provider == AccountProvider.DEEPSEEK) {
                            info.total_balance.toDoubleOrNull()?.let { numericBalance ->
                                prefs.addBalanceSnapshot(
                                    BalanceSnapshot(
                                        timestamp = System.currentTimeMillis(),
                                        balance = numericBalance,
                                        currency = info.currency
                                    )
                                )
                            }
                        }
                        Unit
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to refresh $provider: ${error.message}")
                    prefs.setError(provider, error.message ?: "未知错误")
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to persist $provider balance", error)
            runCatching { prefs.setError(provider, error.message ?: "未知错误") }
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val PERIODIC_WORK_NAME = "ai_balance_widget_periodic_update"
        private const val IMMEDIATE_WORK_NAME = "ai_balance_widget_immediate_update"

        fun schedulePeriodic(context: Context) {
            val appContext = context.applicationContext
            // 必须在后台线程执行：避免在主线程 runBlocking 读 DataStore / 初始化 WorkManager，
            // 任一异常都不应阻断 Application/Activity 的启动。
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                runCatching {
                    val prefs = AppPreferences(appContext)
                    val intervalMin = prefs.refreshIntervalMinutes.first().coerceAtLeast(15)
                    val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                        intervalMin.toLong(), TimeUnit.MINUTES, 5, TimeUnit.MINUTES
                    ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build()
                    WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                        PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
                    )
                }
            }
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
