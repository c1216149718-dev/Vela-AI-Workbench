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
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.data.provider.ProviderId
import com.deepseek.widget.data.provider.ProviderCapability
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.VelaEditorialHeader
import com.deepseek.widget.ui.components.VelaMotif
import com.deepseek.widget.ui.components.VelaSectionOrnament
import com.deepseek.widget.ui.components.VelaTitle
import com.deepseek.widget.ui.components.ProviderLogo
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.util.Date

private val InsightShape = RoundedCornerShape(22.dp)

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onUsageClick: () -> Unit,
    onDataSourcesClick: () -> Unit,
    onProviderClick: (String) -> Unit,
    onRangeChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassScreen(modifier, motif = VelaMotif.INSIGHTS) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 132.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VelaEditorialHeader(VelaTitle.INSIGHTS, Modifier.weight(1f))
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = "刷新用量")
                    }
                }
            }
            item { RangeChips(state.selectedDays, onRangeChange) }
            item { UsageSummary(state, onUsageClick) }
            item { ProviderSources(state, onDataSourcesClick, onProviderClick) }
            item {
                Text(
                    "实扣与估算分列 · 金额按币种统计",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { VelaSectionOrnament(VelaMotif.INSIGHTS) }
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
                        state.totalsByCurrency.takeIf { it.isNotEmpty() }?.let(::formatCurrencyTotals) ?: "暂无实扣记录",
                        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
                    )
                    if (state.estimatedTotalsByCurrency.isNotEmpty()) {
                        Text("估算/本地记录 ${formatCurrencyTotals(state.estimatedTotalsByCurrency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        UsageContentState.UNCONFIGURED -> "连接数据源后同步用量"
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
private fun ProviderSources(state: InsightsUiState, onDataSourcesClick: () -> Unit, onProviderClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            VelaEditorialHeader(VelaTitle.DATA_SOURCES, modifier = Modifier.weight(1f))
            Text("管理", modifier = Modifier.clickable(onClick = onDataSourcesClick).padding(12.dp), color = MaterialTheme.colorScheme.primary)
        }
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = InsightShape) {
            Column {
                val visible = (state.connectedProviders + state.providerTotals.keys + state.providerEstimatedTotals.keys)
                    .sortedBy { ProviderRegistry.descriptor(it.value)?.displayName ?: it.value }
                if (visible.isEmpty()) {
                    Text("尚未添加供应商连接", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                visible.forEach { provider ->
                    val descriptor = ProviderRegistry.descriptor(provider.value) ?: return@forEach
                    val exact = state.providerTotals[provider].orEmpty()
                    val estimated = state.providerEstimatedTotals[provider].orEmpty()
                    val value = when {
                        exact.isNotEmpty() -> formatCurrencyTotals(exact)
                        estimated.isNotEmpty() -> formatCurrencyTotals(estimated)
                        else -> "官方接口未提供"
                    }
                    val status = when {
                        exact.isNotEmpty() -> "实际扣费"
                        estimated.isNotEmpty() -> "估算/本地记录"
                        ProviderCapability.BALANCE in descriptor.capabilities -> "余额可同步"
                        else -> descriptor.capabilities.take(3).joinToString(" · ") { it.label }
                    }
                    ProviderRow(provider, descriptor.displayName, value, status) { onProviderClick(provider.value) }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderId, name: String, value: String, status: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProviderLogo(provider, name)
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
    if (values.isEmpty()) return "暂无数据"
    return values.entries.sortedBy { it.key }.joinToString(" · ") { (currency, value) ->
        val symbol = when (currency.uppercase()) { "CNY", "RMB" -> "¥"; "EUR" -> "€"; else -> "$" }
        "$symbol${value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}"
    }
}

internal fun formatLong(value: Long): String = DecimalFormat("#,###").format(value)
