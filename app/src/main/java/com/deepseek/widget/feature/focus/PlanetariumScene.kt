package com.deepseek.widget.feature.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.deepseek.widget.data.FocusTimerStyle
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.Environment
import io.github.sceneview.environment.rememberKTXEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.isActive
import kotlin.math.sin

@Composable
internal fun PlanetariumScene(
    style: FocusTimerStyle,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val emptyEnvironment = remember { Environment() }
    val neutralEnvironment = rememberKTXEnvironment(
        environmentLoader,
        "environments/neutral/neutral_ibl.ktx",
        "environments/neutral/neutral_skybox.ktx"
    )
    LaunchedEffect(neutralEnvironment, style) {
        neutralEnvironment?.indirectLight?.intensity = when (style) {
            FocusTimerStyle.LUNA -> 14_000f
            FocusTimerStyle.ARES -> 6_500f
            FocusTimerStyle.EUROPA -> 8_000f
        }
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 5.2f)
        lookAt(Position())
    }
    val keyLight = rememberMainLightNode(engine) {
        intensity = 68_000f
        rotation = Rotation(x = 20f, y = -58f, z = -10f)
    }
    var activeNode by remember { mutableStateOf<Pair<FocusTimerStyle, ModelNode>?>(null) }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF02040A), Color(0xFF07101C), Color(0xFF02040A))
            )
        )
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            modelLoader = modelLoader,
            isOpaque = false,
            renderQuality = RenderQuality.Cinematic,
            environment = neutralEnvironment ?: emptyEnvironment,
            cameraNode = cameraNode,
            mainLightNode = keyLight,
            fillLightNode = null,
            cameraManipulator = null,
            onGestureListener = null,
            onFrame = { frameTimeNanos ->
                val seconds = frameTimeNanos / 1_000_000_000f
                val speed = if (paused) 0.16f else 0.48f
                val node = activeNode?.takeIf { it.first == style }?.second
                when (style) {
                    FocusTimerStyle.LUNA -> node?.rotation = Rotation(x = -3f, y = seconds * speed, z = -2f)
                    FocusTimerStyle.ARES -> node?.rotation = Rotation(x = 7f, y = seconds * speed * 0.82f, z = 1f)
                    FocusTimerStyle.EUROPA -> node?.rotation = Rotation(x = -5f, y = seconds * speed * 0.58f, z = -7f)
                }
            }
        ) {
            key(style) {
                val modelPath = when (style) {
                    FocusTimerStyle.LUNA -> "models/moon.glb"
                    FocusTimerStyle.ARES -> "models/mars.glb"
                    FocusTimerStyle.EUROPA -> "models/europa.glb"
                }
                rememberModelInstance(modelLoader, modelPath)?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scaleToUnits = when (style) {
                            FocusTimerStyle.LUNA -> 3.42f
                            FocusTimerStyle.ARES -> 3.18f
                            FocusTimerStyle.EUROPA -> 2.72f
                        },
                        position = when (style) {
                            FocusTimerStyle.LUNA -> Position(x = -0.42f, y = -0.08f, z = 0f)
                            FocusTimerStyle.ARES -> Position(x = 0.42f, y = -0.03f, z = 0f)
                            FocusTimerStyle.EUROPA -> Position(x = -0.08f, y = 0.02f, z = 0f)
                        },
                        apply = {
                            activeNode = style to this
                            if (style == FocusTimerStyle.LUNA) {
                                isShadowCaster = false
                                isShadowReceiver = false
                            }
                        }
                    )
                }
            }
        }
        SpaceVignette(style = style, modifier = Modifier.fillMaxSize())
        StarField(style = style, paused = paused, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SpaceVignette(style: FocusTimerStyle, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = when (style) {
            FocusTimerStyle.LUNA -> Offset(size.width * 0.38f, size.height * 0.5f)
            FocusTimerStyle.ARES -> Offset(size.width * 0.62f, size.height * 0.5f)
            FocusTimerStyle.EUROPA -> Offset(size.width * 0.49f, size.height * 0.5f)
        }
        drawRect(Color.Black.copy(alpha = 0.28f))
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.48f to Color.Black.copy(alpha = 0.04f),
                    0.74f to Color.Black.copy(alpha = 0.58f),
                    1f to Color.Black.copy(alpha = 0.92f)
                ),
                center = center,
                radius = size.minDimension * 0.72f
            )
        )
    }
}

@Composable
private fun StarField(
    style: FocusTimerStyle,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    phase += (now - last) / 1_000_000_000f * if (paused) 0.018f else 0.055f
                }
                last = now
            }
        }
    }
    Canvas(modifier) {
        repeat(106) { index ->
            val seedX = hash01(index * 37 + 11)
            val seedY = hash01(index * 53 + 19)
            val near = index % 5 == 0
            val xNorm = (seedX + phase * if (near) 0.012f else 0.004f) % 1f
            val yNorm = (seedY + phase * if (near) 0.004f else 0.0015f) % 1f
            if (isInsidePlanet(style, xNorm, yNorm)) return@repeat
            val center = Offset(xNorm * size.width, yNorm * size.height)
            val radius = if (near) 1.05f + hash01(index * 13) * 1.25f else 0.45f + hash01(index * 17) * 0.65f
            val pulse = 0.38f + sin(phase * 2.1f + index) * 0.16f
            drawCircle(Color.White.copy(alpha = pulse.coerceIn(0.08f, 0.66f)), radius, center)
            if (near && index % 15 == 0) {
                drawCircle(Color(0xFFB8D9EE).copy(alpha = 0.1f), radius * 4f, center)
            }
        }
    }
}

private fun isInsidePlanet(style: FocusTimerStyle, x: Float, y: Float): Boolean {
    val (centerX, radiusX) = when (style) {
        FocusTimerStyle.LUNA -> 0.38f to 0.43f
        FocusTimerStyle.ARES -> 0.62f to 0.4f
        FocusTimerStyle.EUROPA -> 0.49f to 0.34f
    }
    val radiusY = radiusX * 0.92f
    val dx = (x - centerX) / radiusX
    val dy = (y - 0.5f) / radiusY
    return dx * dx + dy * dy < 1f
}

private fun hash01(value: Int): Float {
    val x = value * 1103515245 + 12345
    return ((x ushr 8) and 0xFFFF) / 65535f
}
