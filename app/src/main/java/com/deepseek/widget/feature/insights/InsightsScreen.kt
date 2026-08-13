package com.deepseek.widget.feature.insights

import android.animation.ValueAnimator
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.widget.data.repository.UsageProvider
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.ProviderBrand
import com.deepseek.widget.ui.components.ProviderIdentity
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.util.Date

private val InsightShape = RoundedCornerShape(22.dp)

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onUsageClick: () -> Unit,
    onDeepSeekClick: () -> Unit,
    onApiKeyFunClick: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassScreen(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 132.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("INSIGHTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("洞察", style = MaterialTheme.typography.headlineLarge)
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = "刷新用量")
                    }
                }
            }
            item { RangeChips(state.selectedDays, onRangeChange) }
            item { UsageSummary(state, onUsageClick) }
            item { ProviderSources(state, onDeepSeekClick, onApiKeyFunClick) }
            item {
                Text(
                    "DeepSeek 估算 · APIKEY.FUN 实扣 · 金额按币种统计",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RangeChips(selected: Int, onRangeChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "7 天", 14 to "两周", 30 to "一月", 90 to "三月").forEach { (days, label) ->
            FilterChip(selected = selected == days, onClick = { onRangeChange(days) }, label = { Text(label) })
        }
    }
}

@Composable
private fun UsageSummary(state: InsightsUiState, onClick: () -> Unit) {
    val dailyValues = remember(state.daily, state.startDate, state.endDate) {
        generateSequence(state.startDate) { it.plusDays(1) }.take(state.selectedDays).map { date ->
            state.daily.filter { it.date == date }.fold(BigDecimal.ZERO) { total, row -> total + row.cost }
        }.toList()
    }
    GlassSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = InsightShape) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("总用量 · ${state.selectedDays} 天", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatCurrencyTotals(state.totalsByCurrency),
                        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
                    )
                    Text("${formatLong(state.totalRequests)} 次请求 · ${formatLong(state.totalTokens)} Token", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = "打开用量详情")
            }
            UsageStateMessage(state)
            if (dailyValues.any { it > BigDecimal.ZERO }) {
                SmallUsageBars(dailyValues, MaterialTheme.colorScheme.primary)
            } else {
                Surface(Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Box(contentAlignment = Alignment.Center) { Text(emptyMessage(state), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(
                lastRefreshLabel(state.lastRefreshAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsageStateMessage(state: InsightsUiState) {
    val message = when (state.contentState) {
        UsageContentState.PARTIAL -> "部分数据源刷新失败，当前显示已缓存数据"
        UsageContentState.STALE -> "刷新失败，当前显示上次成功数据"
        UsageContentState.UNCONFIGURED -> "绑定 Key 后即可同步平台用量"
        else -> null
    }
    if (message != null) Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun SmallUsageBars(values: List<BigDecimal>, color: Color) {
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (motionEnabled) tween(320) else snap(),
        label = "usage-bars"
    )
    val spoken = values.mapIndexed { index, value -> "第${index + 1}天 ${value.toPlainString()}" }.joinToString("，")
    Canvas(Modifier.fillMaxWidth().height(116.dp).semantics { contentDescription = "每日费用趋势：$spoken" }) {
        val max = values.maxOrNull()?.toFloat()?.coerceAtLeast(0.01f) ?: 0.01f
        val gap = 5.dp.toPx()
        val width = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(2.dp.toPx())
        values.forEachIndexed { index, value ->
            val h = (size.height * 0.9f * value.toFloat() / max * progress).coerceAtLeast(3.dp.toPx())
            drawRoundRect(color.copy(alpha = if (value > BigDecimal.ZERO) .9f else .14f), Offset(index * (width + gap), size.height - h), Size(width, h), CornerRadius(width / 2))
        }
    }
}

@Composable
private fun ProviderSources(state: InsightsUiState, onDeepSeekClick: () -> Unit, onApiKeyFunClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("数据源", style = MaterialTheme.typography.headlineSmall)
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = InsightShape) {
            Column {
                ProviderRow(ProviderBrand.DEEPSEEK, "DeepSeek", formatCurrencyTotals(state.providerTotals[UsageProvider.DEEPSEEK].orEmpty().ifEmpty { mapOf("CNY" to BigDecimal.ZERO) }), "估算", onDeepSeekClick)
                ProviderRow(ProviderBrand.APIKEY_FUN, "APIKEY.FUN", formatCurrencyTotals(state.providerTotals[UsageProvider.APIKEY_FUN].orEmpty().ifEmpty { mapOf("USD" to BigDecimal.ZERO) }), "实扣", onApiKeyFunClick)
            }
        }
    }
}

@Composable
private fun ProviderRow(brand: ProviderBrand, name: String, value: String, status: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProviderIdentity(brand)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun lastRefreshLabel(value: Long?): String {
    if (value == null) return "尚未成功刷新"
    val formatter = DateFormat.getDateFormat(LocalContext.current)
    val time = DateFormat.getTimeFormat(LocalContext.current)
    return "最后刷新 ${formatter.format(Date(value))} ${time.format(Date(value))}"
}

private fun emptyMessage(state: InsightsUiState): String = when (state.contentState) {
    UsageContentState.UNCONFIGURED -> "尚未配置可用的数据源"
    UsageContentState.LOADING -> "正在同步用量…"
    else -> "所选日期范围暂无已记录用量"
}

internal fun formatCurrencyTotals(values: Map<String, BigDecimal>): String {
    if (values.isEmpty()) return "¥0.00 · $0.00"
    return values.entries.sortedBy { it.key }.joinToString(" · ") { (currency, value) ->
        val symbol = when (currency.uppercase()) { "CNY", "RMB" -> "¥"; "EUR" -> "€"; else -> "$" }
        "$symbol${value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}"
    }
}

internal fun formatLong(value: Long): String = DecimalFormat("#,###").format(value)
