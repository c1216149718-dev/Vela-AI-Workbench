@file:OptIn(
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class
)

package com.deepseek.widget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepseek.widget.R
import com.deepseek.widget.ui.theme.LocalWorkbenchColors
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.rememberHazeState

val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun GlassScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = rememberHazeState(blurEnabled = true)
    CompositionLocalProvider(
        LocalGlassHazeState provides hazeState,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            WorkbenchBackdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = 0f)
            )
            content()
        }
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 24.dp,
    hazeStateOverride: HazeState? = null,
    glassTint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val colors = LocalWorkbenchColors.current
    val hazeState = hazeStateOverride ?: LocalGlassHazeState.current
    val containerColor = glassTint ?: colors.glassSurface
    val border = Brush.linearGradient(
        colors = if (dark) {
            listOf(
                Color.White.copy(alpha = 0.42f),
                colors.glassBorder,
                colors.providerApiKeyFun.copy(alpha = 0.10f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.96f),
                colors.glassBorder.copy(alpha = 0.62f),
                colors.providerDeepSeek.copy(alpha = 0.12f)
            )
        },
        start = Offset.Zero,
        end = Offset.Infinite
    )
    val base = Modifier
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = CupertinoMaterials.ultraThin(containerColor = containerColor)
                ) {
                    this.blurRadius = blurRadius
                    inputScale = HazeInputScale.Fixed(0.76f)
                    noiseFactor = 0.028f
                }
            } else {
                Modifier.background(containerColor)
            }
        )
        .border(0.8.dp, border, shape)
    Box(
        modifier = modifier.then(if (onClick != null) base.clickable(onClick = onClick) else base),
        content = content
    )
}

@Composable
fun ProviderIdentity(
    provider: ProviderBrand,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val painter: Painter = painterResource(provider.drawableRes)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.31f))
            .background(Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.92f else 0.76f))
            .border(0.7.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(size * 0.31f))
            .padding(size * 0.16f),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painter, contentDescription = provider.contentDescription, modifier = Modifier.fillMaxSize())
    }
}

enum class ProviderBrand(
    @param:DrawableRes val drawableRes: Int,
    val contentDescription: String
) {
    DEEPSEEK(R.drawable.provider_deepseek, "DeepSeek"),
    APIKEY_FUN(R.drawable.provider_apikey_fun, "APIKEY.FUN")
}

@Composable
private fun WorkbenchBackdrop(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val semantic = LocalWorkbenchColors.current
    val base = MaterialTheme.colorScheme.background
    val cool = semantic.backdropCool
    val warm = semantic.backdropWarm
    Canvas(modifier = modifier.background(base)) {
        drawRect(
            brush = Brush.linearGradient(
                0.0f to cool.copy(alpha = if (dark) 0.72f else 0.76f),
                0.42f to base.copy(alpha = 0.46f),
                0.74f to warm.copy(alpha = if (dark) 0.46f else 0.58f),
                1.0f to base,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (dark) 0.035f else 0.16f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, size.height * 0.24f),
            end = Offset(size.width, size.height * 0.24f),
            strokeWidth = size.height * 0.17f
        )
        drawRect(
            color = Color.White.copy(alpha = if (dark) 0.015f else 0.08f),
            style = Stroke(width = 1f)
        )
    }
}
