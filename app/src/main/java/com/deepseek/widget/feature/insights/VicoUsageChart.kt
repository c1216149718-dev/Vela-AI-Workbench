package com.deepseek.widget.feature.insights

import android.animation.ValueAnimator
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.dashed
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToLong

@Composable
internal fun VicoUsageChart(
    dates: List<LocalDate>,
    values: List<Double>,
    spokenUnit: String,
    modifier: Modifier = Modifier
) {
    VicoMultiUsageChart(
        dates = dates,
        series = listOf(UsageChartSeries("用量", values, MaterialTheme.colorScheme.primary)),
        spokenUnit = spokenUnit,
        modifier = modifier
    )
}

internal data class UsageChartSeries(
    val label: String,
    val values: List<Double>,
    val color: Color,
    val dashed: Boolean = false
)

@Composable
internal fun VicoMultiUsageChart(
    dates: List<LocalDate>,
    series: List<UsageChartSeries>,
    spokenUnit: String,
    modifier: Modifier = Modifier
) {
    val producer = remember { CartesianChartModelProducer() }
    val safeDates = remember(dates) { dates.ifEmpty { listOf(LocalDate.now()) } }
    val safeSeries = remember(series, safeDates) {
        series.ifEmpty { listOf(UsageChartSeries("暂无数据", List(safeDates.size) { 0.0 }, Color.Gray)) }
    }
    LaunchedEffect(safeDates, safeSeries) {
        producer.runTransaction {
            lineSeries {
                safeSeries.forEach { item ->
                    series(
                        x = safeDates.map { it.toEpochDay().toDouble() },
                        y = safeDates.indices.map { index -> item.values.getOrElse(index) { 0.0 } }
                    )
                }
            }
        }
    }
    val formatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatUsageAxisDate(value)
        }
    }
    val markerFormatter = remember(spokenUnit) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull()
            val point = (target as? LineCartesianLayerMarkerTarget)?.points?.firstOrNull()
            val date = target?.x?.let(::formatUsageMarkerDate) ?: "日期未知"
            "$date  ${point?.entry?.y?.pretty().orEmpty()} $spokenUnit"
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
        valueFormatter = markerFormatter
    )
    val spacing = ceil((safeDates.size - 1).coerceAtLeast(1) / 4.0).toInt().coerceAtLeast(1)
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val spoken = safeSeries.joinToString("；") { item ->
        item.label + "：" + safeDates.mapIndexed { index, date ->
            "${date.format(DateTimeFormatter.ofPattern("M月d日"))} ${item.values.getOrElse(index) { 0.0 }.pretty()} $spokenUnit"
        }.joinToString("，")
    }
    val lines = safeSeries.map { item ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(item.color.toArgb())),
            stroke = if (item.dashed) {
                LineCartesianLayer.LineStroke.dashed(thickness = 3.dp, dashLength = 5.dp, gapLength = 4.dp)
            } else LineCartesianLayer.LineStroke.continuous(3.dp)
        )
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines)
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = formatter,
                itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) }
            ),
            marker = marker
        ),
        modelProducer = producer,
        modifier = modifier.height(188.dp).semantics { contentDescription = "趋势图。$spoken" },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        animationSpec = if (motionEnabled) tween<Float>(320) else null,
        animateIn = motionEnabled
    )
}

internal fun formatUsageAxisDate(value: Double): String = runCatching {
    LocalDate.ofEpochDay(value.roundToLong()).format(DateTimeFormatter.ofPattern("MM-dd"))
}.getOrDefault("--")

private fun formatUsageMarkerDate(value: Double): String = runCatching {
    LocalDate.ofEpochDay(value.roundToLong()).format(DateTimeFormatter.ofPattern("M月d日"))
}.getOrDefault("日期未知")

internal fun Double.pretty(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this)
