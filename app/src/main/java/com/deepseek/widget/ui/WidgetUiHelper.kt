package com.deepseek.widget.ui

import android.content.Context
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.deepseek.widget.R
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Date

object WidgetUiHelper {
    fun getCurrencySymbol(currency: String): String = when (currency.uppercase()) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency.ifBlank { "¥" }
    }

    fun formatBalance(balance: String): String = runCatching {
        DecimalFormat("#,##0.00").format(balance.toBigDecimal())
    }.getOrDefault(balance.ifBlank { "--" })

    fun buildSummaryViews(
        context: Context,
        days: Int,
        spendByCurrency: Map<String, BigDecimal>,
        balanceByCurrency: Map<String, BigDecimal>,
        lastUpdated: Long?,
        partial: Boolean,
        stale: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)
        views.setTextViewText(R.id.widget_spend_label, "总消耗 · ${rangeLabel(days)}")
        views.setTextViewText(R.id.widget_spend_value, primaryCurrencies(spendByCurrency))
        views.setTextViewText(R.id.widget_balance_value, primaryCurrencies(balanceByCurrency))
        val other = (spendByCurrency.keys + balanceByCurrency.keys).any { it.uppercase() !in setOf("CNY", "RMB", "USD") }
        views.setTextViewText(R.id.widget_currency_note, if (other) "另有其他币种，请在 App 内查看" else "金额按币种分别统计")
        views.setTextViewText(R.id.last_updated, lastUpdated?.let { DateFormat.getTimeFormat(context).format(Date(it)) }.orEmpty())
        val (text, color) = when {
            partial -> "部分失败" to R.color.claude_orange
            stale -> "数据可能已陈旧" to R.color.claude_orange
            spendByCurrency.isEmpty() && balanceByCurrency.isEmpty() -> "等待数据" to R.color.claude_widget_secondary
            else -> "同步正常" to R.color.claude_green
        }
        views.setTextViewText(R.id.widget_health, text)
        views.setTextColor(R.id.widget_health, ContextCompat.getColor(context, color))
        return views
    }

    private fun rangeLabel(days: Int): String = when (days) {
        7 -> "7 天"
        14 -> "两周"
        30 -> "一月"
        90 -> "三月"
        else -> "$days 天"
    }

    private fun primaryCurrencies(values: Map<String, BigDecimal>): String {
        if (values.isEmpty()) return "¥-- · $--"
        val cny = values.entries.filter { it.key.uppercase() in setOf("CNY", "RMB") }.fold(BigDecimal.ZERO) { sum, it -> sum + it.value }
        val usd = values.entries.filter { it.key.uppercase() == "USD" }.fold(BigDecimal.ZERO) { sum, it -> sum + it.value }
        val formatter = DecimalFormat("#,##0.00")
        return "¥${formatter.format(cny)} · $${formatter.format(usd)}"
    }
}
