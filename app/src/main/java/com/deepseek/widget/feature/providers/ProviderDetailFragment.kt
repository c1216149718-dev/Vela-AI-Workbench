package com.deepseek.widget.feature.providers

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.R
import com.deepseek.widget.data.local.entity.ProviderProfileEntity
import com.deepseek.widget.data.provider.ProviderId
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.feature.insights.InsightsUiState
import com.deepseek.widget.feature.insights.InsightsViewModel
import com.deepseek.widget.feature.insights.UsageMetric
import com.deepseek.widget.feature.insights.formatCurrencyTotals
import com.deepseek.widget.feature.insights.formatLong
import com.deepseek.widget.feature.insights.rankModels
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.ProviderLogo
import com.deepseek.widget.ui.components.VelaMotif
import com.deepseek.widget.ui.components.VelaSectionOrnament
import com.deepseek.widget.ui.theme.WorkbenchTheme
import java.math.BigDecimal
import java.time.LocalDate

class ProviderDetailFragment : Fragment() {
    private val viewModel: InsightsViewModel by activityViewModels {
        val c = (requireActivity().application as DeepSeekWidgetApp).container
        InsightsViewModel.factory(c.aiUsageRepository, c.appPreferences, c.apiKeyFunProfiles, c.providerProfileRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val app = requireActivity().application as DeepSeekWidgetApp
            setContent {
                WorkbenchTheme {
                    val profiles by app.container.providerProfileRepository.observeProfiles().collectAsState(initial = emptyList())
                    ProviderDetailScreen(
                        providerId = arguments?.getString("providerId").orEmpty(),
                        state = viewModel.uiState.collectAsStateWithLifecycle().value,
                        profiles = profiles,
                        onBack = { findNavController().navigateUp() },
                        onManage = { findNavController().navigate(R.id.dataSourceCenterFragment) }
                    )
                }
            }
        }
}

@Composable
private fun ProviderDetailScreen(providerId: String, state: InsightsUiState, profiles: List<ProviderProfileEntity>, onBack: () -> Unit, onManage: () -> Unit) {
    val id = ProviderRegistry.canonicalId(providerId) ?: return
    val descriptor = ProviderRegistry.descriptor(id.value) ?: return
    val rows = state.daily.filter { it.provider == id }
    val models = state.models.filter { it.provider == id }
    val exact = rows.filter { it.exact }.groupBy { it.currency }.mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { total, row -> total + row.cost } }
    val estimate = rows.filterNot { it.exact }.groupBy { it.currency }.mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { total, row -> total + row.cost } }
    val connected = profiles.filter { ProviderRegistry.canonicalId(it.providerId) == id }
    GlassScreen(motif = VelaMotif.USAGE_DETAIL) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                    ProviderLogo(id, descriptor.displayName)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(descriptor.displayName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("供应商聚合详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("实际消耗", color = MaterialTheme.colorScheme.primary)
                        Text(if (exact.isEmpty()) "官方接口未提供" else formatCurrencyTotals(exact), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace))
                        if (estimate.isNotEmpty()) Text("估算/本地记录 ${formatCurrencyTotals(estimate)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatLong(rows.sumOf { it.requests })} 次请求 · ${formatLong(rows.sumOf { it.totalTokens })} Token", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { PeriodComparisonCard(rows, state.previousDaily.filter { it.provider == id }, state.startDate, state.endDate) }
            item {
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("模型消耗量", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        val ranked = rankModels(models, UsageMetric.COST)
                        if (ranked.isEmpty()) Text("官方接口未提供模型历史用量", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ranked.forEach { model ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(model.model, Modifier.weight(1f))
                                Text(formatCurrencyTotals(mapOf(model.currency to model.cost)), fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
            item { Text("连接配置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
            if (connected.isEmpty()) item { GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), onClick = onManage) { Text("尚未配置连接 · 点按添加", Modifier.padding(18.dp)) } }
            items(connected, key = { it.id }) { profile ->
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), onClick = onManage) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.alias, style = MaterialTheme.typography.titleMedium)
                            Text(if (profile.lastError.isBlank()) "已配置 · ${if (profile.backgroundSync) "后台同步" else "手动同步"}" else profile.lastError, color = if (profile.lastError.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                        Icon(Icons.Rounded.ChevronRight, "打开配置")
                    }
                }
            }
            item { VelaSectionOrnament(VelaMotif.USAGE_DETAIL) }
        }
    }
}

@Composable
private fun PeriodComparisonCard(rows: List<com.deepseek.widget.data.repository.UsageDailyRecord>, previousRows: List<com.deepseek.widget.data.repository.UsageDailyRecord>, start: LocalDate, end: LocalDate) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
    val previousStart = start.minusDays(days.toLong())
    val current = rows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.cost.toDouble() }
    val previous = previousRows.filter { !it.date.isBefore(previousStart) && it.date.isBefore(start) }.sumOf { it.cost.toDouble() }
    val max = maxOf(current, previous, 0.01)
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val progress by animateFloatAsState(1f, if (motion) tween(320) else snap(), label = "provider-period-bars")
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("日消耗与上周期", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Canvas(Modifier.fillMaxWidth().height(148.dp).semantics { contentDescription = "本期 $current，上期 $previous" }) {
                val barWidth = size.width * .22f
                val base = size.height - 18.dp.toPx()
                listOf(previous to Color.Gray, current to Color(0xFF7FB3E5)).forEachIndexed { index, (value, color) ->
                    val height = (size.height - 28.dp.toPx()) * (value / max).toFloat() * progress
                    val x = size.width * (.27f + index * .34f)
                    drawRoundRect(color, Offset(x, base - height), Size(barWidth, height.coerceAtLeast(3.dp.toPx())), CornerRadius(12.dp.toPx()))
                }
            }
            Text("上周期 ${"%.2f".format(previous)} · 本期 ${"%.2f".format(current)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
