package com.deepseek.widget.feature.insights

import android.animation.ValueAnimator
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.widget.data.repository.UsageDailyRecord
import com.deepseek.widget.data.repository.UsageModelRecord
import com.deepseek.widget.data.repository.UsageProvider
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Date

private const val VisibleModelCount = 5

@Composable
fun UsageDetailScreen(
    state: InsightsUiState,
    onBack: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    var metric by remember { mutableStateOf(UsageMetric.COST) }
    GlassScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("用量详情", style = MaterialTheme.typography.headlineMedium)
                        Text(detailRefreshLabel(state.lastRefreshAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) { Icon(Icons.Rounded.Refresh, "刷新用量") }
                }
            }
            item { DetailRangeSelector(state.selectedDays, onRangeChange) }
            item { TotalDetailCard(state) }
            item { MetricSelector(metric) { metric = it } }
            item { ProviderTrendCard("DeepSeek · 估算", UsageProvider.DEEPSEEK, state, metric) }
            item { ProviderTrendCard("APIKEY.FUN · 实扣", UsageProvider.APIKEY_FUN, state, metric) }
            item { ExactDailyList(state, metric) }
            item { ModelDistribution(state, metric) }
        }
    }
}

@Composable
private fun DetailRangeSelector(selected: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "7 天", 14 to "两周", 30 to "一月", 90 to "三月").forEach { (days, title) ->
            FilterChip(selected == days, { onChange(days) }, { Text(title) })
        }
    }
}

@Composable
private fun TotalDetailCard(state: InsightsUiState) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("全部平台", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(formatCurrencyTotals(state.totalsByCurrency), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
            Text("${formatLong(state.totalRequests)} 次请求 · ${formatLong(state.totalTokens)} Token", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("金额按币种分别统计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricSelector(metric: UsageMetric, onChange: (UsageMetric) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(UsageMetric.COST to "费用", UsageMetric.REQUESTS to "请求", UsageMetric.TOKENS to "Token").forEach { (value, title) ->
            FilterChip(metric == value, { onChange(value) }, { Text(title) })
        }
    }
}

@Composable
private fun ProviderTrendCard(title: String, provider: UsageProvider, state: InsightsUiState, metric: UsageMetric) {
    val dates = remember(state.startDate, state.selectedDays) {
        generateSequence(state.startDate) { it.plusDays(1) }.take(state.selectedDays).toList()
    }
    val rows = state.daily.filter { it.provider == provider }
    val values = dates.map { date -> rows.filter { it.date == date }.sumMetric(metric) }
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(metricHint(provider, metric), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VicoUsageChart(dates, values, metricUnit(metric))
        }
    }
}

@Composable
private fun ExactDailyList(state: InsightsUiState, metric: UsageMetric) {
    var expanded by remember(state.selectedDays, metric) { mutableStateOf(false) }
    val grouped = state.daily.groupBy { it.date }.toSortedMap(compareByDescending { it })
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("数据明细", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(if (expanded) "收起精确数值" else "查看精确数值", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起" else "展开")
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (grouped.isEmpty()) Text("所选日期暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    grouped.forEach { (date, rows) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), fontWeight = FontWeight.SemiBold)
                            UsageProvider.entries.forEach { provider ->
                                val providerRows = rows.filter { it.provider == provider }
                                if (providerRows.isNotEmpty()) {
                                    val name = if (provider == UsageProvider.DEEPSEEK) "DeepSeek" else "APIKEY.FUN"
                                    Text("$name  ${providerRows.metricText(metric)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDistribution(state: InsightsUiState, metric: UsageMetric) {
    val currencies = remember(state.models) { state.models.map { it.currency.uppercase().ifBlank { "USD" } }.distinct().sorted() }
    var selectedCurrency by remember(metric, currencies) { mutableStateOf(currencies.firstOrNull()) }
    var otherExpanded by remember(metric, selectedCurrency, state.selectedDays) { mutableStateOf(false) }
    var expandedRow by remember(metric, selectedCurrency, state.selectedDays) { mutableStateOf<String?>(null) }
    val source = remember(state.models, metric, selectedCurrency) {
        if (metric == UsageMetric.COST && selectedCurrency != null) {
            state.models.filter { it.currency.equals(selectedCurrency, ignoreCase = true) }
        } else state.models
    }
    val ranked = remember(source, metric) { rankModels(source, metric) }
    val allRows = remember(source, metric) { sortModels(source, metric) }
    val overflow = allRows.drop(VisibleModelCount)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.outline
    )

    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("模型用量分布", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("环形图看占比，点按模型查看详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (metric == UsageMetric.COST && currencies.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { currency ->
                        FilterChip(selectedCurrency == currency, { selectedCurrency = currency }, { Text(currency) })
                    }
                }
            }
            if (ranked.isEmpty()) {
                Text("该周期没有模型明细", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ModelDonut(ranked, metric, colors)
                ranked.forEachIndexed { index, row ->
                    val rowKey = "${row.provider}-${row.credentialLabel}-${row.model}-${row.currency}"
                    ModelCompactRow(
                        row = row,
                        metric = metric,
                        color = colors[index % colors.size],
                        total = ranked.sumOf { it.metricValue(metric) },
                        expanded = if (row.isOther) otherExpanded else expandedRow == rowKey,
                        onClick = {
                            if (row.isOther) otherExpanded = !otherExpanded
                            else expandedRow = if (expandedRow == rowKey) null else rowKey
                        }
                    )
                    AnimatedVisibility(row.isOther && otherExpanded) {
                        Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            overflow.forEach { child ->
                                ModelOverflowRow(child, metric)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDonut(rows: List<RankedModel>, metric: UsageMetric, colors: List<Color>) {
    val total = rows.sumOf { it.metricValue(metric) }.coerceAtLeast(0.0)
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (motionEnabled) tween(420) else snap(),
        label = "model-donut"
    )
    val spoken = rows.joinToString("，") { "${it.model} ${percentage(it.metricValue(metric), total)}" }
    Box(
        Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "模型用量分布。$spoken" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(166.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            rows.forEachIndexed { index, row ->
                val sweep = if (total <= 0.0) 0f else (row.metricValue(metric) / total * 360f * progress).toFloat()
                if (sweep > 0f) {
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = start,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0.8f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("合计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(modelMetricText(rows, metric), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
        }
    }
}

@Composable
private fun ModelCompactRow(
    row: RankedModel,
    metric: UsageMetric,
    color: Color,
    total: Double,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(10.dp), CircleShape, color = color) {}
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.model, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${row.credentialLabel} · ${percentage(row.metricValue(metric), total)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(row.compactMetricText(metric), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
        }
        AnimatedVisibility(expanded && !row.isOther) {
            Text(
                "${row.providerLabel()} · ${row.currency} · ${formatCurrencyTotals(mapOf(row.currency to row.cost))} · ${formatLong(row.requests)} 次 · ${formatLong(row.totalTokens)} Token",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
    }
}

@Composable
private fun ModelOverflowRow(row: RankedModel, metric: UsageMetric) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.model, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text("${row.credentialLabel} · ${row.providerLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(row.compactMetricText(metric), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
    }
}

private fun List<UsageDailyRecord>.sumMetric(metric: UsageMetric): Double = when (metric) {
    UsageMetric.COST -> fold(BigDecimal.ZERO) { total, row -> total + row.cost }.toDouble()
    UsageMetric.REQUESTS -> sumOf { it.requests }.toDouble()
    UsageMetric.TOKENS -> sumOf { it.totalTokens }.toDouble()
}

private fun List<UsageDailyRecord>.metricText(metric: UsageMetric): String = when (metric) {
    UsageMetric.COST -> formatCurrencyTotals(groupBy { it.currency }.mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { total, row -> total + row.cost } })
    UsageMetric.REQUESTS -> "${formatLong(sumOf { it.requests })} 次"
    UsageMetric.TOKENS -> "${formatLong(sumOf { it.totalTokens })} Token"
}

private fun metricHint(provider: UsageProvider, metric: UsageMetric): String = when {
    provider == UsageProvider.DEEPSEEK && metric != UsageMetric.COST -> "仅显示本地账本记录"
    provider == UsageProvider.DEEPSEEK -> "余额差估算"
    else -> "长按或拖动图表查看单日值"
}

private fun metricUnit(metric: UsageMetric): String = when (metric) { UsageMetric.COST -> "金额"; UsageMetric.REQUESTS -> "次"; UsageMetric.TOKENS -> "Token" }

private fun percentage(value: Double, total: Double): String = if (total <= 0.0) "0%" else "%.1f%%".format(value / total * 100.0)

private fun modelMetricText(rows: List<RankedModel>, metric: UsageMetric): String = when (metric) {
    UsageMetric.COST -> formatCurrencyTotals(mapOf(rows.first().currency to rows.fold(BigDecimal.ZERO) { total, row -> total + row.cost }))
    UsageMetric.REQUESTS -> "${formatLong(rows.sumOf { it.requests })} 次"
    UsageMetric.TOKENS -> formatLong(rows.sumOf { it.totalTokens })
}

@Composable
private fun detailRefreshLabel(value: Long?): String {
    if (value == null) return "尚未成功刷新"
    val context = LocalContext.current
    return "最后刷新 ${DateFormat.getDateFormat(context).format(Date(value))} ${DateFormat.getTimeFormat(context).format(Date(value))}"
}

