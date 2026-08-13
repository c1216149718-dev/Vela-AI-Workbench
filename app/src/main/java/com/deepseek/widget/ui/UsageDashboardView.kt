package com.deepseek.widget.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.deepseek.widget.R
import com.deepseek.widget.api.DailyUsagePoint
import com.deepseek.widget.api.ModelUsageStat
import com.deepseek.widget.api.UsageComparison
import java.text.NumberFormat
import java.util.Locale

/**
 * 可复用的用量仪表盘：时间范围选择 + 总量指标卡 + 每日趋势图 + 模型对比（费用/请求/Token 切换）。
 * DeepSeek 与 APIKEY.FUN 两页共用，仅通过 [accentColorRes] 与 [currencySymbol] 区分风格。
 */
class UsageDashboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Metric { COST, REQUESTS, TOKENS }

    var onRangeChanged: ((days: Int) -> Unit)? = null
    var onRefresh: (() -> Unit)? = null

    private val view = inflate(context, R.layout.view_usage_dashboard, this)
    private val titleText = view.findViewById<TextView>(R.id.dashboard_title)
    private val rangeSelector = view.findViewById<UsageRangeSelectorView>(R.id.usage_range_selector)
    private val metricGroup = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.model_metric_group)
    private val refreshBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh_usage)
    private val statusText = view.findViewById<TextView>(R.id.usage_status)
    private val content = view.findViewById<LinearLayout>(R.id.usage_content)
    private val periodTitle = view.findViewById<TextView>(R.id.usage_period_title)
    private val deltaText = view.findViewById<TextView>(R.id.usage_delta)
    private val costText = view.findViewById<TextView>(R.id.usage_cost)
    private val requestsText = view.findViewById<TextView>(R.id.usage_requests)
    private val tokensText = view.findViewById<TextView>(R.id.usage_tokens)
    private val trendView = view.findViewById<UsageTrendView>(R.id.usage_trend)
    private val legendCurrent = view.findViewById<TextView>(R.id.legend_current)
    private val modelContainer = view.findViewById<LinearLayout>(R.id.model_usage_container)
    private val sourceLabel = view.findViewById<TextView>(R.id.dashboard_source)

    private var selectedDays = 7
    private var selectedMetric = Metric.COST
    private var currentModels: List<ModelUsageStat> = emptyList()
    private var currencySymbol = "$"
    private var accentColor = R.color.apikey_amber
    private var metricAnimator: ValueAnimator? = null

    init {
        orientation = VERTICAL
        refreshBtn.setOnClickListener { onRefresh?.invoke() }
        rangeSelector.onSelectionChanged = { days ->
            selectedDays = days
            onRangeChanged?.invoke(selectedDays)
        }
        metricGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedMetric = metricForButton(checkedId)
            renderModelRows()
        }
        metricGroup.check(R.id.metric_cost)
    }

    fun configure(accentColorRes: Int, currency: String, sourceText: String, title: String? = null) {
        accentColor = accentColorRes
        currencySymbol = currency
        if (title != null) titleText.text = title
        sourceLabel.text = sourceText
        sourceLabel.setTextColor(ContextCompat.getColor(context, R.color.muted_text))
        refreshBtn.setTextColor(ContextCompat.getColor(context, accentColorRes))
        legendCurrent.setTextColor(ContextCompat.getColor(context, accentColorRes))
        trendView.setAccentColor(accentColorRes)
        rangeSelector.setAccentColor(accentColorRes)
        applyPillTint(accentColorRes)
    }

    /** pill 选中态用主题色填充，未选态用浅灰底。 */
    private fun applyPillTint(accentColorRes: Int) {
        val accent = ContextCompat.getColor(context, accentColorRes)
        val idle = ContextCompat.getColor(context, R.color.widget_card_bg)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val tint = ColorStateList(states, intArrayOf(accent, idle))
        for (i in 0 until metricGroup.childCount) {
            (metricGroup.getChildAt(i) as? com.google.android.material.button.MaterialButton)
                ?.backgroundTintList = tint
        }
    }

    fun setRangeDays(days: Int, silent: Boolean = false) {
        selectedDays = days
        rangeSelector.setSelectedDays(days, animate = !silent, notify = !silent)
    }

    fun showKeyRequired(messageRes: Int) {
        content.visibility = View.GONE
        statusText.setText(messageRes)
        statusText.setTextColor(ContextCompat.getColor(context, R.color.label_text))
        statusText.visibility = View.VISIBLE
    }

    fun showLoading() {
        content.visibility = View.GONE
        statusText.setText(R.string.usage_loading)
        statusText.setTextColor(ContextCompat.getColor(context, R.color.label_text))
        statusText.visibility = View.VISIBLE
    }

    fun showError(message: String) {
        content.visibility = View.GONE
        statusText.text = message
        statusText.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
        statusText.visibility = View.VISIBLE
    }

    fun showEmpty() {
        content.visibility = View.GONE
        statusText.setText(R.string.usage_empty)
        statusText.setTextColor(ContextCompat.getColor(context, R.color.label_text))
        statusText.visibility = View.VISIBLE
    }

    /**
     * 渲染一段时间的用量。currentPoints / previousPoints 已按天数归一化。
     */
    fun showUsage(
        currentPoints: List<DailyUsagePoint>,
        previousPoints: List<DailyUsagePoint>,
        models: List<ModelUsageStat>,
        days: Int
    ) {
        if (currentPoints.isEmpty() && models.isEmpty()) {
            showEmpty()
            return
        }
        val totalCost = currentPoints.sumOf { it.actual_cost }
            .takeIf { it > 0.0 } ?: models.sumOf { it.actual_cost }
        val totalRequests = currentPoints.sumOf { it.requests }
            .takeIf { it > 0 } ?: models.sumOf { it.requests }
        val totalTokens = currentPoints.sumOf { it.total_tokens }
            .takeIf { it > 0 } ?: models.sumOf { it.total_tokens }
        val previousCost = previousPoints.sumOf { it.actual_cost }

        periodTitle.text = context.getString(R.string.usage_period_title, rangeLabel(days))
        deltaText.text = comparisonText(totalCost, previousCost)
        deltaText.setTextColor(
            ContextCompat.getColor(
                context,
                if (totalCost <= previousCost) R.color.accent_green else R.color.accent_red
            )
        )
        animateMetrics(totalCost, totalRequests, totalTokens)
        trendView.submitData(currentPoints, previousPoints)
        currentModels = models
        renderModelRows()
        statusText.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun renderModelRows() {
        modelContainer.removeAllViews()
        val models = currentModels
        if (models.isEmpty()) {
            modelContainer.addView(TextView(context).apply {
                setText(R.string.model_distribution_empty)
                setTextColor(ContextCompat.getColor(context, R.color.muted_text))
                textSize = 12f
            })
            return
        }
        val sorted = models.sortedByDescending { metricValue(it, selectedMetric) }
        val visible = if (sorted.size <= 10) sorted else {
            sorted.take(9) + ModelUsageStat(
                model = context.getString(R.string.other_models),
                requests = sorted.drop(9).sumOf { it.requests },
                total_tokens = sorted.drop(9).sumOf { it.total_tokens },
                actual_cost = sorted.drop(9).sumOf { it.actual_cost }
            )
        }
        val maxValue = visible.maxOfOrNull { metricValue(it, selectedMetric) }?.coerceAtLeast(1.0) ?: 1.0
        visible.forEach { model ->
            modelContainer.addView(createModelRow(model, maxValue))
        }
    }

    private fun createModelRow(model: ModelUsageStat, maxValue: Double): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 彩色圆点
        heading.addView(View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.dot_background)?.mutate()?.apply {
                setTint(ContextCompat.getColor(context, accentColor))
            }
        }, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            marginEnd = dp(8)
        })
        heading.addView(TextView(context).apply {
            text = model.model.ifBlank { context.getString(R.string.other_models) }
            setTextColor(ContextCompat.getColor(context, R.color.balance_text))
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(TextView(context).apply {
            text = context.getString(
                R.string.model_usage_detail_metric,
                formatMetricValue(model, selectedMetric),
                compactNumber(model.total_tokens)
            )
            setTextColor(ContextCompat.getColor(context, R.color.muted_text))
            textSize = 10f
            gravity = Gravity.END
        })
        row.addView(heading)
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(context, accentColor))
            progressBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.widget_card_bg))
        }
        val targetProgress = ((metricValue(model, selectedMetric) / maxValue) * progressBar.max)
            .toInt().coerceIn(0, progressBar.max)
        row.addView(progressBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply {
            marginStart = dp(16)
            topMargin = dp(5)
        })
        if (ValueAnimator.areAnimatorsEnabled()) {
            ObjectAnimator.ofInt(progressBar, "progress", 0, targetProgress).apply {
                duration = 420L
                interpolator = DecelerateInterpolator()
                start()
            }
        } else {
            progressBar.progress = targetProgress
        }
        // 底部细分隔线
        row.addView(View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.widget_border))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(8)
        })
        return row
    }

    private fun metricValue(stat: ModelUsageStat, metric: Metric): Double = when (metric) {
        Metric.COST -> stat.actual_cost
        Metric.REQUESTS -> stat.requests.toDouble()
        Metric.TOKENS -> stat.total_tokens.toDouble()
    }

    private fun formatMetricValue(stat: ModelUsageStat, metric: Metric): String = when (metric) {
        Metric.COST -> String.format(Locale.US, "%s%s", currencySymbol, formatCost(stat.actual_cost))
        Metric.REQUESTS -> context.getString(R.string.requests_unit, compactNumber(stat.requests))
        Metric.TOKENS -> compactNumber(stat.total_tokens)
    }

    private fun animateMetrics(cost: Double, requests: Long, tokens: Long) {
        metricAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            updateMetrics(cost, requests, tokens, 1f)
            return
        }
        metricAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = DecelerateInterpolator()
            addUpdateListener { updateMetrics(cost, requests, tokens, it.animatedValue as Float) }
            start()
        }
    }

    private fun updateMetrics(cost: Double, requests: Long, tokens: Long, progress: Float) {
        costText.text = context.getString(
            R.string.cost_value_with_symbol,
            currencySymbol,
            formatCost(cost * progress)
        )
        requestsText.text = compactNumber((requests * progress).toLong())
        tokensText.text = compactNumber((tokens * progress).toLong())
    }

    private fun comparisonText(current: Double, previous: Double): String {
        val percentage = UsageComparison.percentage(current, previous)
        if (percentage == null) {
            return if (current <= 0.0) context.getString(R.string.comparison_no_change)
            else context.getString(R.string.comparison_new_usage)
        }
        return context.getString(
            R.string.comparison_percentage,
            if (percentage >= 0) "+" else "",
            String.format(Locale.US, "%.1f", percentage)
        )
    }

    private fun compactNumber(value: Long): String {
        if (value < 1_000) return NumberFormat.getIntegerInstance().format(value)
        val (number, suffix) = when {
            value >= 1_000_000_000 -> value / 1_000_000_000.0 to "B"
            value >= 1_000_000 -> value / 1_000_000.0 to "M"
            else -> value / 1_000.0 to "K"
        }
        return if (number >= 100) String.format(Locale.US, "%.0f%s", number, suffix)
        else String.format(Locale.US, "%.1f%s", number, suffix)
    }

    private fun formatCost(value: Double): String =
        if (value < 1.0) String.format(Locale.US, "%.4f", value)
        else String.format(Locale.US, "%.2f", value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun metricForButton(id: Int): Metric = when (id) {
        R.id.metric_requests -> Metric.REQUESTS
        R.id.metric_tokens -> Metric.TOKENS
        else -> Metric.COST
    }

    private fun rangeLabel(days: Int): String = context.getString(
        when (days) {
            1 -> R.string.range_1d
            3 -> R.string.range_3d
            5 -> R.string.range_5d
            14 -> R.string.range_14d
            28 -> R.string.range_28d
            30 -> R.string.range_30d
            else -> R.string.range_7d
        }
    )
}
