package com.deepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.local.WorkbenchDatabase
import com.deepseek.widget.ui.WidgetUiHelper
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.math.BigDecimal

class DeepSeekWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        private const val ACTION_RENDER = "com.deepseek.widget.ACTION_RENDER"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DeepSeekWidgetProvider::class.java))
            context.sendBroadcast(Intent(context, DeepSeekWidgetProvider::class.java).apply {
                action = ACTION_RENDER
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderCachedAsync(context, manager, ids)
        WidgetUpdateWorker.schedulePeriodic(context)
        WidgetUpdateWorker.enqueueImmediate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_REFRESH -> {
                val ids = manager.getAppWidgetIds(ComponentName(context, DeepSeekWidgetProvider::class.java))
                renderCachedAsync(context, manager, ids)
                WidgetUpdateWorker.enqueueImmediate(context)
            }
            ACTION_RENDER -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: manager.getAppWidgetIds(ComponentName(context, DeepSeekWidgetProvider::class.java))
                renderCachedAsync(context, manager, ids)
            }
        }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateWorker.cancelPeriodic(context)
    }

    private fun renderCachedAsync(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = AppPreferences(appContext)
                val profilesStore = ApiKeyFunProfileStore.create(appContext)
                profilesStore.migrateFromLegacy()
                val deepSeekCache = prefs.accountCache(AccountProvider.DEEPSEEK).first()
                val apiKeyFunCache = prefs.accountCache(AccountProvider.APIKEY_FUN).first()
                val days = prefs.usageRangeDays.first().coerceIn(1, 90)
                val end = LocalDate.now()
                val start = end.minusDays(days.toLong() - 1L)
                val database = WorkbenchDatabase.get(appContext)
                val usage = database.aiUsageDailyDao().getAllRange(start.toString(), end.toString())
                val spendByCurrency = usage.groupBy { it.currency.uppercase().ifBlank { "USD" } }
                    .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { total, row -> total + row.cost.toBigDecimalOrNull().orZero() } }
                val balanceByCurrency = linkedMapOf<String, BigDecimal>()
                fun addBalance(currency: String, value: String) {
                    value.toBigDecimalOrNull()?.let { amount ->
                        val key = currency.uppercase().ifBlank { "USD" }
                        balanceByCurrency[key] = balanceByCurrency.getOrDefault(key, BigDecimal.ZERO) + amount
                    }
                }
                addBalance(deepSeekCache.currency.ifBlank { "CNY" }, deepSeekCache.totalBalance)
                addBalance(apiKeyFunCache.currency.ifBlank { "USD" }, apiKeyFunCache.totalBalance)
                database.providerProfileDao().getLatestBalances().forEach { addBalance(it.currency, it.amount) }
                val latest = listOfNotNull(
                    deepSeekCache.lastUpdated.takeIf { it > 0 },
                    apiKeyFunCache.lastUpdated.takeIf { it > 0 },
                    usage.maxOfOrNull { it.updatedAt }
                ).maxOrNull()
                val partial = deepSeekCache.errorMessage.isNotBlank() || apiKeyFunCache.errorMessage.isNotBlank()
                val stale = latest != null && System.currentTimeMillis() - latest > 24 * 60 * 60 * 1000L
                widgetIds.forEach { widgetId ->
                    val views = WidgetUiHelper.buildSummaryViews(
                        context = appContext,
                        days = days,
                        spendByCurrency = spendByCurrency,
                        balanceByCurrency = balanceByCurrency,
                        lastUpdated = latest,
                        partial = partial,
                        stale = stale
                    )
                    setClickIntents(appContext, views, widgetId)
                    manager.updateAppWidget(widgetId, views)
                }
            } catch (_: Exception) {
                widgetIds.forEach { widgetId ->
                    val views = WidgetUiHelper.buildSummaryViews(
                        context = appContext,
                        days = 7,
                        spendByCurrency = emptyMap(),
                        balanceByCurrency = emptyMap(),
                        lastUpdated = null,
                        partial = true,
                        stale = false
                    )
                    setClickIntents(appContext, views, widgetId)
                    manager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun setClickIntents(context: Context, views: RemoteViews, widgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.widget_root,
            openDestinationIntent(context, widgetId * 10, R.id.insightsFragment)
        )
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getBroadcast(
                context,
                widgetId + 1000,
                Intent(context, DeepSeekWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun openDestinationIntent(context: Context, requestCode: Int, destinationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_DESTINATION, destinationId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

private fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO
