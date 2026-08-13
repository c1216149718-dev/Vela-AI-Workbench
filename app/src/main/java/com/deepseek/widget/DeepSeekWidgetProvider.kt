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
import com.deepseek.widget.ui.WidgetUiHelper
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

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
                val deepSeekConfigured = prefs.deepSeekApiKey.first().isNotBlank()
                val apiKeyFunConfigured = profilesStore.isAnyConfigured()
                val today = LocalDate.now().toString()
                val todayUsage = prefs.deepSeekUsageEntries.first().filter { it.date == today }
                val todayRecordedCost = todayUsage.takeIf { it.isNotEmpty() }?.sumOf { it.cost }
                widgetIds.forEach { widgetId ->
                    val views = WidgetUiHelper.buildViews(
                        context = appContext,
                        deepSeekCache = deepSeekCache,
                        apiKeyFunCache = apiKeyFunCache,
                        deepSeekConfigured = deepSeekConfigured,
                        apiKeyFunConfigured = apiKeyFunConfigured,
                        todayRecordedCost = todayRecordedCost
                    )
                    setClickIntents(appContext, views, widgetId)
                    manager.updateAppWidget(widgetId, views)
                }
            } catch (_: Exception) {
                widgetIds.forEach { widgetId ->
                    val views = WidgetUiHelper.buildViews(
                        context = appContext,
                        deepSeekCache = com.deepseek.widget.data.AccountCache(),
                        apiKeyFunCache = com.deepseek.widget.data.AccountCache(),
                        deepSeekConfigured = false,
                        apiKeyFunConfigured = false
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
            openDestinationIntent(context, widgetId * 10, R.id.workbenchFragment)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deepseek_account,
            openDestinationIntent(context, widgetId * 10 + 1, R.id.deepSeekFragment)
        )
        views.setOnClickPendingIntent(
            R.id.widget_apikey_fun_account,
            openDestinationIntent(context, widgetId * 10 + 2, R.id.apiKeyFunFragment)
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
