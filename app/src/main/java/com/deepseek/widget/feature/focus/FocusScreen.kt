package com.deepseek.widget.feature.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.widget.R
import com.deepseek.widget.data.FocusTimerStyle
import com.deepseek.widget.domain.model.FocusStatus
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.theme.LocalWorkbenchColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class FocusSetupStep { DURATION, THEME }

@Composable
fun FocusScreen(
    state: FocusUiState,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    onStyleChange: (FocusTimerStyle) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit = {}
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDigits by remember { mutableStateOf(true) }
    var controlsGeneration by remember { mutableLongStateOf(0L) }
    var setupStep by rememberSaveable { mutableStateOf(FocusSetupStep.DURATION) }
    val session = state.activeSession
    val active = session != null
    val paused = session?.status == FocusStatus.PAUSED

    LaunchedEffect(session?.id, session?.status) {
        while (session != null) {
            now = System.currentTimeMillis()
            delay(250L)
        }
    }
    LaunchedEffect(active) {
        if (active) {
            delay(620L)
            onImmersiveChange(true)
        } else {
            onImmersiveChange(false)
            showDigits = true
        }
    }
    LaunchedEffect(active, controlsGeneration) {
        if (active) {
            showDigits = true
            delay(3_000L)
            showDigits = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { onImmersiveChange(false) }
    }

    val remaining = session?.remainingMillis(now) ?: state.selectedMinutes * 60_000L
    val total = session?.plannedMinutes?.times(60_000L) ?: state.selectedMinutes * 60_000L
    val progressTarget = if (total <= 0L) 0f else (remaining.toFloat() / total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(progressTarget, tween(520, easing = LinearEasing), label = "focus-progress")

    GlassScreen(modifier = Modifier.testTag("focus_screen")) {
        AnimatedVisibility(
            visible = !active && setupStep == FocusSetupStep.DURATION,
            enter = fadeIn(tween(220)) + slideInHorizontally(tween(360)) { -it / 5 },
            exit = fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { -it / 5 }
        ) {
            FocusDurationPage(
                state = state,
                onBack = onBack,
                onHistory = onHistory,
                onMinutesChange = onMinutesChange,
                onContinue = { setupStep = FocusSetupStep.THEME }
            )
        }
        AnimatedVisibility(
            visible = active || setupStep == FocusSetupStep.THEME,
            enter = fadeIn(tween(240)) + slideInHorizontally(tween(380)) { it / 5 },
            exit = fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { it / 5 }
        ) {
            PlanetariumLayout(
                state = state,
                active = active,
                paused = paused,
                remaining = remaining,
                progress = progress,
                showDigits = showDigits,
                onRevealControls = {
                    showDigits = true
                    controlsGeneration += 1L
                },
                onBack = {
                    if (active) onBack() else setupStep = FocusSetupStep.DURATION
                },
                onHistory = onHistory,
                onStyleChange = onStyleChange,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onComplete = {
                    setupStep = FocusSetupStep.DURATION
                    onComplete()
                },
                onCancel = {
                    setupStep = FocusSetupStep.DURATION
                    onCancel()
                }
            )
        }
    }
}

@Composable
private fun PlanetariumLayout(
    state: FocusUiState,
    active: Boolean,
    paused: Boolean,
    remaining: Long,
    progress: Float,
    showDigits: Boolean,
    onRevealControls: () -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onStyleChange: (FocusTimerStyle) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portalWidth by animateDpAsState(
            if (active) maxWidth else maxWidth - 40.dp,
            spring(dampingRatio = 0.88f, stiffness = 115f),
            label = "planetarium-width"
        )
        val portalHeight by animateDpAsState(
            if (active) maxHeight else min(maxHeight.value * 0.48f, 390f).dp,
            spring(dampingRatio = 0.88f, stiffness = 110f),
            label = "planetarium-height"
        )
        val portalTop by animateDpAsState(
            if (active) 0.dp else 92.dp,
            spring(dampingRatio = 0.88f, stiffness = 110f),
            label = "planetarium-top"
        )
        val corner by animateDpAsState(if (active) 0.dp else 26.dp, tween(if (active) 900 else 620), label = "portal-corner")
        val setupAlpha by animateFloatAsState(if (active) 0f else 1f, tween(if (active) 520 else 320), label = "setup-alpha")

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = portalTop)
                .width(portalWidth)
                .height(portalHeight)
                .clip(RoundedCornerShape(corner))
                .border(
                    width = if (active) 0.dp else 0.8.dp,
                    color = Color.White.copy(alpha = if (active) 0f else 0.72f),
                    shape = RoundedCornerShape(corner)
                )
                .clickable(onClick = onRevealControls)
        ) {
            PlanetariumScene(
                style = state.timerStyle,
                paused = paused,
                modifier = Modifier.fillMaxSize()
            )
            if (!active) {
                PreviewCaption(state.timerStyle, Modifier.align(Alignment.TopStart))
            }
        }

        if (setupAlpha > 0.01f) {
            FocusThemePage(
                state = state,
                sceneHeight = portalTop + portalHeight,
                alpha = setupAlpha,
                onBack = onBack,
                onHistory = onHistory,
                onStyleChange = onStyleChange,
                onStart = onStart
            )
        }

        AnimatedVisibility(
            visible = active,
            enter = fadeIn(tween(440, delayMillis = 760)),
            exit = fadeOut(tween(180))
        ) {
            ActiveFocusOverlay(
                style = state.timerStyle,
                remaining = remaining,
                progress = progress,
                paused = paused,
                busy = state.isBusy,
                showDigits = showDigits,
                onBack = onBack,
                onHistory = onHistory,
                onPause = onPause,
                onResume = onResume,
                onComplete = onComplete,
                onCancel = onCancel,
                onInteraction = onRevealControls
            )
        }
    }
}

@Composable
private fun FocusThemePage(
    state: FocusUiState,
    sceneHeight: androidx.compose.ui.unit.Dp,
    alpha: Float,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onStyleChange: (FocusTimerStyle) -> Unit,
    onStart: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { FocusHeader(onBack, onHistory) }
        item { Spacer(Modifier.height(sceneHeight - 78.dp)) }
        item { CelestialSelector(state.timerStyle, onStyleChange) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                PrimaryRoundButton(enabled = !state.isBusy, onClick = onStart)
            }
        }
        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun FocusDurationPage(
    state: FocusUiState,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { FocusPageHeader("DURATION", "时长", onBack, onHistory) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                SpiralDurationDial(state.selectedMinutes, onMinutesChange)
                PresetControl(state.selectedMinutes, onMinutesChange)
                Text(
                    "满 5 分钟后保存记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NextRoundButton(enabled = !state.isBusy, onClick = onContinue)
            }
        }
        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun FocusHeader(onBack: () -> Unit, onHistory: () -> Unit) {
    FocusPageHeader("FOCUS", "专注", onBack, onHistory)
}

@Composable
private fun FocusPageHeader(
    english: String,
    title: String,
    onBack: () -> Unit,
    onHistory: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(english, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "专注历史") }
    }
}

@Composable
private fun PreviewCaption(style: FocusTimerStyle, modifier: Modifier = Modifier) {
    Column(modifier.padding(18.dp)) {
        Text(styleEnglish(style), color = Color(0xFFB6D5E5), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(styleChinese(style), color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun CelestialSelector(selected: FocusTimerStyle, onSelected: (FocusTimerStyle) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusTimerStyle.entries.forEach { style ->
            val active = style == selected
            GlassSurface(
                modifier = Modifier.weight(1f).height(72.dp),
                shape = RoundedCornerShape(18.dp),
                blurRadius = 18.dp,
                glassTint = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f) else null,
                onClick = { onSelected(style) }
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(styleEnglish(style), style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(styleChinese(style), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SpiralDurationDial(minutes: Int, onMinutesChange: (Int) -> Unit) {
    val currentMinutes by rememberUpdatedState(minutes)
    val currentChange by rememberUpdatedState(onMinutesChange)
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val track = LocalWorkbenchColors.current.border
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dialSize = 320.dp
    val thumbProgress = ((minutes - 5f) / 295f).coerceIn(0f, 1f)
    val thumbOffset = with(density) {
        val point = spiralPoint(dialSize.toPx(), thumbProgress, 27.dp.toPx())
        IntOffset(
            (point.x - 18.dp.toPx()).roundToInt(),
            (point.y - 18.dp.toPx()).roundToInt()
        )
    }
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        }
    }

    Box(Modifier.size(dialSize), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var lastEmitted = currentMinutes
                fun update(point: Offset) {
                    val edgeInset = 27.dp.toPx()
                    var closestProgress = 0f
                    var closestDistance = Float.MAX_VALUE
                    repeat(361) { index ->
                        val progress = index / 360f
                        val candidate = spiralPoint(size.width.toFloat(), progress, edgeInset)
                        val distance = (candidate.x - point.x).pow(2) + (candidate.y - point.y).pow(2)
                        if (distance < closestDistance) {
                            closestDistance = distance
                            closestProgress = progress
                        }
                    }
                    val next = (5 + (closestProgress * 295f / 5f).roundToInt() * 5).coerceIn(5, 300)
                    if (next != lastEmitted) {
                        lastEmitted = next
                        currentChange(next)
                        haptic.performHapticFeedback(
                            if (next % 30 == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                        )
                    }
                }
                detectDragGestures(
                    onDragStart = {
                        lastEmitted = currentMinutes
                        update(it)
                    }
                ) { change, _ ->
                    update(change.position)
                    change.consume()
                }
            }
        ) {
            val basePath = spiralPath(size.minDimension, 1f, 27.dp.toPx())
            val selectedPath = spiralPath(size.minDimension, thumbProgress, 27.dp.toPx())
            drawPath(basePath, track.copy(alpha = 0.35f), style = Stroke(1.15.dp.toPx(), cap = StrokeCap.Round))
            drawPath(selectedPath, primary.copy(alpha = 0.64f), style = Stroke(1.65.dp.toPx(), cap = StrokeCap.Round))

            listOf(30, 60, 90, 120, 180, 300).forEach { value ->
                val progress = ((value - 5f) / 295f).coerceIn(0f, 1f)
                val point = spiralPoint(size.minDimension, progress, 27.dp.toPx())
                val centerPoint = Offset(size.width / 2f, size.height / 2f)
                val vector = point - centerPoint
                val distance = vector.getDistance().coerceAtLeast(1f)
                val label = point + vector / distance * 17.dp.toPx()
                drawCircle(primary.copy(alpha = 0.72f), 2.2.dp.toPx(), point)
                labelPaint.textSize = 13.sp.toPx()
                labelPaint.color = labelColor.toArgbCompat()
                drawContext.canvas.nativeCanvas.drawText(
                    value.toString(),
                    label.x,
                    label.y + labelPaint.textSize * 0.34f,
                    labelPaint
                )
            }

            val thumb = spiralPoint(size.minDimension, thumbProgress, 27.dp.toPx())
            drawCircle(primary.copy(alpha = 0.10f), 26.dp.toPx(), thumb)
            drawCircle(Color.White.copy(alpha = 0.22f), 20.dp.toPx(), thumb)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                minutes.toString(),
                fontSize = 66.sp,
                lineHeight = 68.sp,
                color = textColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal
            )
            Text("MIN", style = MaterialTheme.typography.titleSmall, color = textColor, letterSpacing = 1.4.sp)
            Text("DURATION", style = MaterialTheme.typography.labelSmall, color = labelColor, letterSpacing = 2.sp)
        }
        Image(
            painter = painterResource(R.drawable.moon_surface),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { thumbOffset }
                .size(36.dp)
                .shadow(9.dp, CircleShape)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.64f), CircleShape)
        )
    }
}

private fun spiralPoint(size: Float, progress: Float, edgeInset: Float): Offset {
    val center = Offset(size / 2f, size / 2f)
    val maxRadius = size / 2f - edgeInset
    val minRadius = maxRadius * 0.48f
    val value = progress.coerceIn(0f, 1f)
    val radius = minRadius + (maxRadius - minRadius) * value
    val angle = Math.toRadians((-36f + value * 792f).toDouble()).toFloat()
    return Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
}

private fun DrawScope.spiralPath(size: Float, progress: Float, edgeInset: Float): Path {
    val path = Path()
    val last = (progress.coerceIn(0f, 1f) * 360f).roundToInt().coerceAtLeast(1)
    repeat(last + 1) { index ->
        val point = spiralPoint(size, index / 360f, edgeInset)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    return path
}

@Composable
private fun PresetControl(selected: Int, onSelected: (Int) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(25, 60, 120, 300).forEach { minutes ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected == minutes) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    onClick = { onSelected(minutes) }
                ) {
                    Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) { Text(minutes.toString(), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun NextRoundButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(62.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        enabled = enabled,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "选择主题", Modifier.size(29.dp))
        }
    }
}

@Composable
private fun PrimaryRoundButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(66.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        enabled = enabled,
        onClick = onClick
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "开始专注", Modifier.size(32.dp)) } }
}

@Composable
private fun ActiveFocusOverlay(
    style: FocusTimerStyle,
    remaining: Long,
    progress: Float,
    paused: Boolean,
    busy: Boolean,
    showDigits: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onInteraction: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onInteraction)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(styleEnglish(style), style = MaterialTheme.typography.labelMedium, color = Color(0xFF9BC5D8), fontWeight = FontWeight.Bold)
                Text(styleChinese(style), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
            IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "专注历史", tint = Color.White) }
        }

        AnimatedVisibility(
            visible = showDigits,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(
                when (style) {
                    FocusTimerStyle.LUNA -> Alignment.CenterEnd
                    FocusTimerStyle.ARES -> Alignment.CenterStart
                    FocusTimerStyle.EUROPA -> Alignment.CenterEnd
                }
            )
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    formatMillis(remaining),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color.White
                )
                Text(
                    if (paused) "PAUSED" else "REMAINING",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB8C6CE)
                )
            }
        }

        AnimatedVisibility(
            visible = showDigits,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${((1f - progress) * 100).toInt()}% ELAPSED", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CAAB1))
                    Text("${(progress * 100).toInt()}% LEFT", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CAAB1))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimerControl(Icons.Rounded.Stop, "取消", busy, Color(0xFFE48C85)) {
                        onInteraction()
                        onCancel()
                    }
                    TimerControl(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (paused) "继续" else "暂停",
                        busy,
                        Color(0xFFB9D9E7)
                    ) {
                        onInteraction()
                        if (paused) onResume() else onPause()
                    }
                    TimerControl(Icons.Rounded.Check, "完成", busy, Color(0xFFA9CBAF)) {
                        onInteraction()
                        onComplete()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    busy: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = !busy, modifier = Modifier.size(52.dp)) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(27.dp))
    }
}

private fun styleEnglish(style: FocusTimerStyle): String = when (style) {
    FocusTimerStyle.LUNA -> "LUNA"
    FocusTimerStyle.ARES -> "ARES"
    FocusTimerStyle.EUROPA -> "EUROPA"
}

private fun styleChinese(style: FocusTimerStyle): String = when (style) {
    FocusTimerStyle.LUNA -> "月面静默"
    FocusTimerStyle.ARES -> "火星暮线"
    FocusTimerStyle.EUROPA -> "冰原轨迹"
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun formatMillis(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val hours = seconds / 3600L
    val minutes = seconds % 3600L / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%02d:%02d".format(minutes, remainingSeconds)
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)
