package com.deepseek.widget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val PorcelainLightColors = lightColorScheme(
    primary = Color(0xFF466875),
    onPrimary = Color(0xFFF7F3EC),
    primaryContainer = Color(0xFFD8E1E0),
    onPrimaryContainer = Color(0xFF314F5A),
    secondary = Color(0xFF7B5A4C),
    onSecondary = Color(0xFFF8F1EB),
    secondaryContainer = Color(0xFFE7D9CF),
    onSecondaryContainer = Color(0xFF5D4035),
    background = Color(0xFFE7E2D8),
    onBackground = Color(0xFF252522),
    surface = Color(0xFFECE7DE),
    onSurface = Color(0xFF252522),
    surfaceContainerLowest = Color(0xFFF2EEE7),
    surfaceContainerLow = Color(0xFFECE7DE),
    surfaceContainer = Color(0xFFE7E2D8),
    surfaceContainerHigh = Color(0xFFDFD9CE),
    surfaceContainerHighest = Color(0xFFD6CFC3),
    surfaceVariant = Color(0xFFDDD7CC),
    onSurfaceVariant = Color(0xFF62645F),
    outline = Color(0xFFA8A196),
    outlineVariant = Color(0xFFC8C1B5),
    error = Color(0xFF985954),
    onError = Color(0xFFFFF8F5)
)

private val CarbonDarkColors = darkColorScheme(
    primary = Color(0xFF88A5B1),
    onPrimary = Color(0xFF15272E),
    primaryContainer = Color(0xFF334B55),
    onPrimaryContainer = Color(0xFFDDE8E8),
    secondary = Color(0xFFB2937F),
    onSecondary = Color(0xFF2B211C),
    secondaryContainer = Color(0xFF4D3D34),
    onSecondaryContainer = Color(0xFFF0E0D4),
    background = Color(0xFF111416),
    onBackground = Color(0xFFF2EFE9),
    surface = Color(0xFF1C1F20),
    onSurface = Color(0xFFF2EFE9),
    surfaceContainerLowest = Color(0xFF101314),
    surfaceContainerLow = Color(0xFF171A1B),
    surfaceContainer = Color(0xFF1C1F20),
    surfaceContainerHigh = Color(0xFF252829),
    surfaceContainerHighest = Color(0xFF303333),
    surfaceVariant = Color(0xFF292B2A),
    onSurfaceVariant = Color(0xFFB7B4AE),
    outline = Color(0xFF797A76),
    outlineVariant = Color(0xFF3B3E3D),
    error = Color(0xFFD08A83),
    onError = Color(0xFF32100E)
)

@Immutable
data class WorkbenchSemanticColors(
    val border: Color,
    val borderStrong: Color,
    val elevatedSurface: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val backdropCool: Color,
    val backdropWarm: Color,
    val tertiaryText: Color,
    val success: Color,
    val warning: Color,
    val providerDeepSeek: Color,
    val providerApiKeyFun: Color
)

private val LightSemanticColors = WorkbenchSemanticColors(
    border = Color(0xFFC8C1B5),
    borderStrong = Color(0xFFA8A196),
    elevatedSurface = Color(0xFFEEE9E0),
    glassSurface = Color(0x3DFFFFFF),
    glassBorder = Color(0xCFFFFFFF),
    backdropCool = Color(0xFFD9E0DD),
    backdropWarm = Color(0xFFDDD2C4),
    tertiaryText = Color(0xFF858780),
    success = Color(0xFF51755E),
    warning = Color(0xFF806A48),
    providerDeepSeek = Color(0xFF466875),
    providerApiKeyFun = Color(0xFF7B5A4C)
)

private val DarkSemanticColors = WorkbenchSemanticColors(
    border = Color(0xFF3B3E3D),
    borderStrong = Color(0xFF797A76),
    elevatedSurface = Color(0xFF202324),
    glassSurface = Color(0x661C1F20),
    glassBorder = Color(0x2EFFFFFF),
    backdropCool = Color(0xFF1B2021),
    backdropWarm = Color(0xFF24201D),
    tertiaryText = Color(0xFF7F827E),
    success = Color(0xFF82A88D),
    warning = Color(0xFFB8A06F),
    providerDeepSeek = Color(0xFF7E9EAA),
    providerApiKeyFun = Color(0xFFB88E77)
)

val LocalWorkbenchColors = staticCompositionLocalOf { LightSemanticColors }

private val WorkbenchTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    )
)

@Composable
fun WorkbenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWorkbenchColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) CarbonDarkColors else PorcelainLightColors,
            typography = WorkbenchTypography,
            content = content
        )
    }
}
