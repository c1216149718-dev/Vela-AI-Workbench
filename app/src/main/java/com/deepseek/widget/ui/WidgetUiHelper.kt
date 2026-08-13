package com.deepseek.widget.ui

import android.content.Context
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.deepseek.widget.R
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import java.text.DecimalFormat
import java.util.Date

object WidgetUiHelper {

    fun buildViews(
        context: Context,
        deepSeekCache: AccountCache,
        apiKeyFunCache: AccountCache,
        deepSeekConfigured: Boolean,
        apiKeyFunConfigured: Boolean,
        todayRecordedCost: Double? = null
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)
        bindAccount(views, context, AccountProvider.DEEPSEEK, deepSeekCache, deepSeekConfigured)
        bindAccount(views, context, AccountProvider.APIKEY_FUN, apiKeyFunCache, apiKeyFunConfigured)

        val latestUpdate = maxOf(deepSeekCache.lastUpdated, apiKeyFunCache.lastUpdated)
        views.setTextViewText(
            R.id.last_updated,
            if (latestUpdate > 0) context.getString(
                R.string.last_updated,
                DateFormat.getTimeFormat(context).format(Date(latestUpdate))
            ) else ""
        )
        views.setTextViewText(
            R.id.widget_today_cost,
            todayRecordedCost?.let {
                context.getString(R.string.widget_today_cost, DecimalFormat("#,##0.00").format(it))
            } ?: context.getString(R.string.widget_today_cost_empty)
        )

        val (healthText, healthColor) = when {
            !deepSeekConfigured && !apiKeyFunConfigured ->
                R.string.widget_health_configure to R.color.claude_widget_secondary
            deepSeekCache.errorMessage.isNotBlank() || apiKeyFunCache.errorMessage.isNotBlank() ->
                R.string.widget_health_partial_error to R.color.claude_red
            (deepSeekConfigured && deepSeekCache.totalBalance.isNotBlank() && !deepSeekCache.isAvailable) ||
                (apiKeyFunConfigured && apiKeyFunCache.totalBalance.isNotBlank() && !apiKeyFunCache.isAvailable) ->
                R.string.widget_health_attention to R.color.claude_orange
            else -> R.string.widget_health_ok to R.color.claude_green
        }
        views.setTextViewText(R.id.widget_health, context.getString(healthText))
        views.setTextColor(R.id.widget_health, ContextCompat.getColor(context, healthColor))
        return views
    }

    private fun bindAccount(
        views: RemoteViews,
        context: Context,
        provider: AccountProvider,
        cache: AccountCache,
        configured: Boolean
    ) {
        val ids = if (provider == AccountProvider.DEEPSEEK) {
            AccountViewIds(R.id.deepseek_currency_symbol, R.id.deepseek_total_balance, R.id.deepseek_status)
        } else {
            AccountViewIds(R.id.apikey_fun_currency_symbol, R.id.apikey_fun_total_balance, R.id.apikey_fun_status)
        }

        val fallbackCurrency = if (provider == AccountProvider.APIKEY_FUN) "USD" else "CNY"
        views.setTextViewText(ids.currency, getCurrencySymbol(cache.currency.ifBlank { fallbackCurrency }))
        views.setTextViewText(
            ids.total,
            if (!configured || cache.totalBalance.isBlank()) "--" else formatBalance(cache.totalBalance)
        )

        val (status, color) = when {
            !configured -> context.getString(R.string.not_configured) to R.color.claude_widget_secondary
            cache.errorMessage.isNotBlank() -> context.getString(R.string.status_error) to R.color.claude_red
            cache.totalBalance.isBlank() -> context.getString(R.string.loading) to R.color.claude_widget_secondary
            cache.isAvailable -> context.getString(R.string.status_available) to R.color.claude_green
            else -> context.getString(R.string.status_unavailable) to R.color.claude_orange
        }
        views.setTextViewText(ids.status, status)
        views.setTextColor(ids.status, ContextCompat.getColor(context, color))
    }

    fun getCurrencySymbol(currency: String): String = when (currency.uppercase()) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency.ifBlank { "¥" }
    }

    fun formatBalance(balance: String): String = try {
        DecimalFormat("#,##0.00").format(balance.toDouble())
    } catch (_: NumberFormatException) {
        balance.ifBlank { "--" }
    }

    private data class AccountViewIds(val currency: Int, val total: Int, val status: Int)
}
