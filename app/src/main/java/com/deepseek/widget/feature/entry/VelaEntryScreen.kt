package com.deepseek.widget.feature.entry

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepseek.widget.R
import kotlinx.coroutines.delay

enum class EntryThemeVariant { LIGHT, DARK }

internal fun calculateEntryBandHeight(boardWidth: Float, boardHeight: Float): Float {
    val fixedHeight = boardWidth * ((1550f + 560f) / 1080f)
    return (boardHeight - fixedHeight).coerceAtLeast(0f)
}

@Composable
fun VelaEntryScreen(
    themeVariant: EntryThemeVariant,
    progress: Float,
    stage: String,
    ready: Boolean,
    artworkVisible: Boolean,
    minimumDisplayMillis: Long,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var visibleStartedAt by remember { mutableLongStateOf(0L) }
    val motionEnabled = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    val renderedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (motionEnabled) tween(220) else snap(),
        label = "startup-progress"
    )

    LaunchedEffect(artworkVisible) {
        if (artworkVisible && visibleStartedAt == 0L) {
            visibleStartedAt = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(ready, artworkVisible, visibleStartedAt) {
        if (ready && artworkVisible && visibleStartedAt > 0L) {
            val remaining = minimumDisplayMillis -
                (SystemClock.elapsedRealtime() - visibleStartedAt)
            if (remaining > 0) delay(remaining)
            onFinished()
        }
    }

    EntryArtworkLayout(
        themeVariant = themeVariant,
        progress = renderedProgress,
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = "Vela 正在启动，$stage"
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    current = renderedProgress,
                    range = 0f..1f
                )
            }
    )
}

@Composable
internal fun EntryArtworkLayout(
    themeVariant: EntryThemeVariant,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val dark = themeVariant == EntryThemeVariant.DARK
    val surround = if (dark) Color(0xFF121D29) else Color(0xFFF2EBE2)
    BoxWithConstraints(modifier.background(surround)) {
        val expanded = maxWidth >= 600.dp
        val boardWidth = if (expanded) minOf(480.dp, maxWidth - 48.dp) else maxWidth
        val nativeBoardHeight = boardWidth * (2400f / 1080f)
        val boardHeight = if (expanded) minOf(nativeBoardHeight, maxHeight - 48.dp) else maxHeight
        val topHeight = boardWidth * (1550f / 1080f)
        val footerHeight = boardWidth * (560f / 1080f)
        val bandHeight = calculateEntryBandHeight(boardWidth.value, boardHeight.value).dp
        Box(
            modifier = Modifier
                .width(boardWidth)
                .height(boardHeight)
                .align(if (expanded) Alignment.Center else Alignment.TopCenter)
                .background(surround)
        ) {
            Column(Modifier.fillMaxSize()) {
                EntryImage(R.drawable.vela_entry_top, topHeight)
                EntryImage(R.drawable.vela_entry_band, bandHeight, ContentScale.FillBounds)
                EntryImage(R.drawable.vela_entry_footer, footerHeight)
            }
            EntryProgressHighlight(
                progress = progress,
                dark = dark,
                boardWidth = boardWidth,
                footerHeight = footerHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -footerHeight * 0.45f)
            )
        }
    }
}

@Composable
private fun EntryImage(
    drawable: Int,
    height: Dp,
    contentScale: ContentScale = ContentScale.FillWidth
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = contentScale,
        alignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    )
}

@Composable
private fun EntryProgressHighlight(
    progress: Float,
    dark: Boolean,
    boardWidth: Dp,
    footerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val size = minOf(84.dp, boardWidth * 0.22f, footerHeight * 0.36f)
    val quiet = if (dark) Color(0x4DB08B5A) else Color(0x429B672D)
    val active = if (dark) Color(0xFF7FB3E5) else Color(0xFF9B672D)
    Canvas(modifier.width(size).height(size)) {
        val inset = 5.dp.toPx()
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(
            color = quiet,
            startAngle = 204f,
            sweepAngle = 132f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = active,
            startAngle = 204f,
            sweepAngle = 132f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
