package com.deepseek.widget.feature.insights

import android.animation.ValueAnimator
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
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
    val producer = remember { CartesianChartModelProducer() }
    val points = remember(dates, values) {
        dates.mapIndexed { index, date -> date to values.getOrElse(index) { 0.0 } }
            .ifEmpty { listOf(LocalDate.now() to 0.0) }
    }
    LaunchedEffect(points) {
        producer.runTransaction {
            lineSeries {
                series(
                    x = points.map { (date, _) -> date.toEpochDay().toDouble() },
                    y = points.map { (_, value) -> value }
                )
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
    val spacing = ceil((points.size - 1).coerceAtLeast(1) / 4.0).toInt().coerceAtLeast(1)
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val spoken = points.joinToString("，") { (date, value) ->
        "${date.format(DateTimeFormatter.ofPattern("M月d日"))} ${value.pretty()} $spokenUnit"
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        stroke = LineCartesianLayer.LineStroke.continuous(3.dp)
                    )
                )
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
