package com.deepseek.widget.feature.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.widget.ui.components.GlassSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class SchedulePickerTarget { START, END }

private enum class PickerWindowState { COMPACT, STANDARD, EXPANDED, FULLSCREEN }

@Composable
internal fun TaskSchedulePicker(
    state: TaskEditUiState,
    target: SchedulePickerTarget,
    onDismiss: () -> Unit,
    onDateChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initialTimestamp = if (target == SchedulePickerTarget.START) state.startAt else state.dueAt
    val fallbackDate = state.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now()
    val initialDateTime = remember(initialTimestamp, fallbackDate) {
        initialTimestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.of(fallbackDate, roundedNow(target == SchedulePickerTarget.END))
    }
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var hour by remember { mutableIntStateOf(initialDateTime.hour) }
    var minute by remember { mutableIntStateOf(initialDateTime.minute) }
    var windowState by remember { mutableStateOf(PickerWindowState.STANDARD) }
    var selectingMinutes by remember { mutableStateOf(false) }

    fun commit() {
        val result = selectedDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (target == SchedulePickerTarget.START) {
            onDateChange(selectedDate.toString())
            onStartChange(result)
        } else {
            onEndChange(result)
        }
        onDismiss()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (windowState == PickerWindowState.FULLSCREEN) 0.58f else 0.43f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onDismiss)
        )
        val full = windowState == PickerWindowState.FULLSCREEN
        val dark = isSystemInDarkTheme()
        val targetWidth = if (full) maxWidth else minOf(maxWidth - 28.dp, 440.dp)
        val targetHeight = when (windowState) {
            PickerWindowState.COMPACT -> 268.dp
            PickerWindowState.STANDARD -> minOf(maxHeight - 54.dp, 612.dp)
            PickerWindowState.EXPANDED -> minOf(maxHeight - 24.dp, 760.dp)
            PickerWindowState.FULLSCREEN -> maxHeight
        }
        val width by androidx.compose.animation.core.animateDpAsState(
            targetWidth,
            spring(dampingRatio = 0.86f, stiffness = 240f),
            label = "picker-width"
        )
        val height by androidx.compose.animation.core.animateDpAsState(
            targetHeight,
            spring(dampingRatio = 0.84f, stiffness = 210f),
            label = "picker-height"
        )
        val shape = if (full) RoundedCornerShape(0.dp) else RoundedCornerShape(30.dp)

        GlassSurface(
            modifier = Modifier
                .width(width)
                .height(height)
                .shadow(if (full) 0.dp else 28.dp, shape),
            shape = shape,
            blurRadius = 48.dp,
            glassTint = MaterialTheme.colorScheme.surface.copy(
                alpha = when {
                    full -> if (dark) 0.92f else 0.95f
                    dark -> 0.80f
                    else -> 0.76f
                }
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(onClick = {})
                )
                Column(Modifier.fillMaxSize()) {
                    PickerTitleBar(
                        target = target,
                        state = windowState,
                        onClose = onDismiss,
                        onMinimize = {
                            windowState = when (windowState) {
                                PickerWindowState.FULLSCREEN -> PickerWindowState.EXPANDED
                                PickerWindowState.EXPANDED -> PickerWindowState.STANDARD
                                PickerWindowState.STANDARD -> PickerWindowState.COMPACT
                                PickerWindowState.COMPACT -> return@PickerTitleBar onDismiss()
                            }
                        },
                        onExpand = {
                            windowState = when (windowState) {
                                PickerWindowState.COMPACT -> PickerWindowState.STANDARD
                                PickerWindowState.STANDARD -> PickerWindowState.EXPANDED
                                PickerWindowState.EXPANDED, PickerWindowState.FULLSCREEN -> PickerWindowState.FULLSCREEN
                            }
                        },
                        onCommit = ::commit
                    )

                    AnimatedContent(
                        targetState = windowState,
                        transitionSpec = {
                            (fadeIn(tween(240)) togetherWith fadeOut(tween(150)))
                                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> spring(0.86f, 230f) }))
                        },
                        label = "schedule-window-content",
                        modifier = Modifier.fillMaxSize()
                    ) { size ->
                        when (size) {
                            PickerWindowState.COMPACT -> CompactTimePicker(hour, minute, { hour = it }, { minute = it })
                            PickerWindowState.STANDARD -> StandardTimePicker(
                                date = selectedDate,
                                hour = hour,
                                minute = minute,
                                selectingMinutes = selectingMinutes,
                                onSelectingMinutesChange = { selectingMinutes = it },
                                onHourChange = { hour = it },
                                onMinuteChange = { minute = it },
                                onOpenCalendar = { windowState = PickerWindowState.EXPANDED }
                            )
                            PickerWindowState.EXPANDED, PickerWindowState.FULLSCREEN -> CalendarTimePicker(
                                selectedDate = selectedDate,
                                hour = hour,
                                minute = minute,
                                onDateChange = {
                                    selectedDate = it
                                    windowState = PickerWindowState.STANDARD
                                },
                                onHourChange = { hour = it },
                                onMinuteChange = { minute = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTitleBar(
    target: SchedulePickerTarget,
    state: PickerWindowState,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrafficLight(Color(0xFFFF5F57), "关闭", onClose)
        Spacer(Modifier.width(9.dp))
        TrafficLight(Color(0xFFFFBD2E), "缩小", onMinimize)
        Spacer(Modifier.width(9.dp))
        TrafficLight(Color(0xFF28C840), if (state == PickerWindowState.FULLSCREEN) "全屏" else "扩大", onExpand)
        Text(
            text = if (target == SchedulePickerTarget.START) "START · 开始" else "END · 结束",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(shape = RoundedCornerShape(12.dp), color = Color.Transparent, onClick = onCommit) {
            Text("完成", Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrafficLight(color: Color, description: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(14.dp), shape = CircleShape, color = color, onClick = onClick) {
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun CompactTimePicker(hour: Int, minute: Int, onHourChange: (Int) -> Unit, onMinuteChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("24-HOUR WHEEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberWheel(hour, 24, "HOUR", onHourChange)
            Text(":", style = MaterialTheme.typography.headlineSmall)
            NumberWheel(minute, 60, "MIN", onMinuteChange)
        }
    }
}

@Composable
private fun NumberWheel(value: Int, range: Int, label: String, onChange: (Int) -> Unit) {
    val middle = range * 50 + value
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle - 1)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val latestOnChange by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state, range) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val selected = (state.firstVisibleItemIndex + 1).floorMod(range)
                    if (selected != value) {
                        latestOnChange(selected)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
    }

    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Box(
            modifier = Modifier.fillMaxWidth().height(156.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            ) {}
            LazyColumn(
                state = state,
                flingBehavior = fling,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(range * 100) { index ->
                    val selected = index == state.firstVisibleItemIndex + 1
                    Box(Modifier.height(52.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "%02d".format(index.floorMod(range)),
                            style = if (selected) MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
                            else MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardTimePicker(
    date: LocalDate,
    hour: Int,
    minute: Int,
    selectingMinutes: Boolean,
    onSelectingMinutesChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onOpenCalendar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            onClick = onOpenCalendar
        ) {
            Text(formatDate(date), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "%02d:%02d".format(hour, minute),
            fontSize = 49.sp,
            lineHeight = 52.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.width(150.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            DialModeLabel("HOUR", active = !selectingMinutes) { onSelectingMinutesChange(false) }
            DialModeLabel("MIN", active = selectingMinutes) { onSelectingMinutesChange(true) }
        }
        AnimatedContent(
            targetState = selectingMinutes,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(tween(320), initialScale = 0.94f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 1.04f)) using
                    SizeTransform(clip = false)
            },
            label = "clock-mode"
        ) { minuteMode ->
            TimeDial(
                hour = hour,
                minute = minute,
                selectingMinutes = minuteMode,
                onSelectingMinutesChange = onSelectingMinutesChange,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange,
                modifier = Modifier.size(352.dp)
            )
        }
    }
}

@Composable
private fun DialModeLabel(label: String, active: Boolean, onClick: () -> Unit) {
    val activeColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 38.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 1.2.sp
        )
        if (active) {
            Canvas(Modifier.size(4.dp)) {
                drawCircle(activeColor)
            }
        }
    }
}

@Composable
private fun TimeDial(
    hour: Int,
    minute: Int,
    selectingMinutes: Boolean,
    onSelectingMinutesChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestHour by rememberUpdatedState(hour)
    val latestMinute by rememberUpdatedState(minute)
    val latestMinutesMode by rememberUpdatedState(selectingMinutes)
    val latestHourChange by rememberUpdatedState(onHourChange)
    val latestMinuteChange by rememberUpdatedState(onMinuteChange)
    val latestModeChange by rememberUpdatedState(onSelectingMinutesChange)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val lens = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    val highlight = Color.White.copy(alpha = 0.78f)
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startedInMinuteMode = latestMinutesMode
                var lastEmitted = if (startedInMinuteMode) latestMinute else latestHour
                val dialCenter = Offset(size.width / 2f, size.height / 2f)
                var centerTapCandidate = startedInMinuteMode &&
                    (down.position - dialCenter).getDistance() <= 48.dp.toPx()

                fun update(point: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f - 24.dp.toPx()
                    val angle = ((atan2(point.y - center.y, point.x - center.x) * 180f / PI.toFloat()) + 450f) % 360f
                    val next = if (startedInMinuteMode) {
                        ((angle / 30f).roundToInt() * 5).floorMod(60)
                    } else {
                        val index = (angle / 30f).roundToInt().floorMod(12)
                        if ((point - center).getDistance() < radius * 0.69f) index + 12 else index
                    }
                    if (next != lastEmitted) {
                        lastEmitted = next
                        if (startedInMinuteMode) latestMinuteChange(next) else latestHourChange(next)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                if (!centerTapCandidate) update(down.position)
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pressed = change.pressed
                    if (pressed) {
                        if (centerTapCandidate &&
                            (change.position - down.position).getDistance() > 18.dp.toPx()
                        ) {
                            centerTapCandidate = false
                        }
                        if (!centerTapCandidate) update(change.position)
                    }
                    change.consume()
                }
                when {
                    centerTapCandidate -> latestModeChange(false)
                    !startedInMinuteMode -> latestModeChange(true)
                }
            }
        }
    ) {
        val radius = size.minDimension / 2f - 24.dp.toPx()
        val outerRadius = radius * 0.83f
        val innerRadius = radius * 0.56f
        val minuteRadius = radius * 0.79f
        drawCircle(Color.White.copy(alpha = 0.34f), radius, style = Stroke(1.dp.toPx()))
        drawCircle(primary.copy(alpha = 0.12f), innerRadius + 19.dp.toPx(), style = Stroke(0.8.dp.toPx()))

        val selected = if (selectingMinutes) minute / 5f else (hour % 12).toFloat()
        val selectedRadius = when {
            selectingMinutes -> minuteRadius
            hour >= 12 -> innerRadius
            else -> outerRadius
        }
        val angle = selected / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
        val lensCenter = Offset(center.x + cos(angle) * selectedRadius, center.y + sin(angle) * selectedRadius)
        drawCircle(Color.Black.copy(alpha = 0.10f), 25.dp.toPx(), lensCenter + Offset(0f, 5.dp.toPx()))
        drawCircle(primary.copy(alpha = 0.08f), 29.dp.toPx(), lensCenter)
        drawCircle(lens, 24.dp.toPx(), lensCenter)
        drawCircle(highlight, 24.dp.toPx(), lensCenter, style = Stroke(1.15.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.28f), 18.dp.toPx(), lensCenter - Offset(4.dp.toPx(), 5.dp.toPx()))

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        if (selectingMinutes) {
            repeat(12) { index ->
                val value = index * 5
                val itemAngle = index / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
                val position = Offset(center.x + cos(itemAngle) * minuteRadius, center.y + sin(itemAngle) * minuteRadius)
                paint.textSize = with(density) { if (value == minute) 16.sp.toPx() else 14.sp.toPx() }
                paint.color = if (value == minute) textColor.toArgbCompat() else secondaryText.toArgbCompat()
                drawContext.canvas.nativeCanvas.drawText("%02d".format(value), position.x, position.y + paint.textSize * 0.34f, paint)
            }
            paint.textSize = with(density) { 44.sp.toPx() }
            paint.color = textColor.toArgbCompat()
            drawContext.canvas.nativeCanvas.drawText(
                "%02d".format(hour),
                center.x,
                center.y + paint.textSize * 0.34f,
                paint
            )
        } else {
            repeat(12) { index ->
                val itemAngle = index / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
                val outer = Offset(center.x + cos(itemAngle) * outerRadius, center.y + sin(itemAngle) * outerRadius)
                val inner = Offset(center.x + cos(itemAngle) * innerRadius, center.y + sin(itemAngle) * innerRadius)
                paint.textSize = with(density) { if (index == hour) 15.sp.toPx() else 13.sp.toPx() }
                paint.color = if (index == hour) textColor.toArgbCompat() else secondaryText.toArgbCompat()
                drawContext.canvas.nativeCanvas.drawText("%02d".format(index), outer.x, outer.y + paint.textSize * 0.34f, paint)
                paint.textSize = with(density) { if (index + 12 == hour) 15.sp.toPx() else 12.sp.toPx() }
                paint.color = if (index + 12 == hour) textColor.toArgbCompat() else secondaryText.copy(alpha = 0.70f).toArgbCompat()
                drawContext.canvas.nativeCanvas.drawText("%02d".format(index + 12), inner.x, inner.y + paint.textSize * 0.34f, paint)
            }
        }
    }
}

@Composable
private fun CalendarTimePicker(
    selectedDate: LocalDate,
    hour: Int,
    minute: Int,
    onDateChange: (LocalDate) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    var month by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            NumberWheel(hour, 24, "HOUR", onHourChange)
            Spacer(Modifier.width(14.dp))
            NumberWheel(minute, 60, "MIN", onMinuteChange)
        }
        CalendarGrid(month, selectedDate, { month = month.minusMonths(1) }, { month = month.plusMonths(1) }, onDateChange)
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateChange: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, "上个月") }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINESE)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, "下个月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7
        val start = first.minusDays(leading.toLong())
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = start.plusDays((row * 7 + column).toLong())
                    val selected = date == selectedDate
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp).padding(3.dp),
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else if (date.month == month.month) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        onClick = { onDateChange(date) }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(date.dayOfMonth.toString()) }
                    }
                }
            }
        }
    }
}

private fun roundedNow(addHour: Boolean): LocalTime {
    val now = LocalTime.now().withSecond(0).withNano(0).plusHours(if (addHour) 1 else 0)
    return now.plusMinutes((5 - now.minute % 5).toLong() % 5)
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINESE))

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)
