package com.noexcs.indolent.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// M3 Expressive spring specs
val ExpressiveSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

val GentleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessVeryLow,
)

val SnappySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

// Contrast levels
enum class ContrastLevel { Standard, Medium, High }

val LocalContrastLevel = staticCompositionLocalOf { ContrastLevel.Standard }

// --- Color utilities ---

private fun Color.boostSaturation(factor: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(this.toArgb(), hsv)
    if (hsv[1] < 0.02f) return this
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    val alphaInt = (this.alpha * 255).toInt()
    return Color(AndroidColor.HSVToColor(alphaInt, hsv))
}

private fun Color.tintWith(tint: Color, ratio: Float): Color = Color(
    red = red + (tint.red - red) * ratio,
    green = green + (tint.green - green) * ratio,
    blue = blue + (tint.blue - blue) * ratio,
    alpha = alpha,
)

private fun Color.adjustContrast(factor: Float): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

// Amplify dynamic colors — boost saturation and tint surfaces with primary
// for the signature M3 Expressive "wallpaper-color-immersed" look
private fun ColorScheme.amplifyDynamicColors(): ColorScheme {
    val boosted = copy(
        primary = primary.boostSaturation(1.5f),
        primaryContainer = primaryContainer.boostSaturation(1.3f),
        secondary = secondary.boostSaturation(1.5f),
        secondaryContainer = secondaryContainer.boostSaturation(1.3f),
        tertiary = tertiary.boostSaturation(1.5f),
        tertiaryContainer = tertiaryContainer.boostSaturation(1.3f),
    )
    val tint = boosted.primary
    return boosted.copy(
        background = boosted.background.tintWith(tint, 0.10f),
        surface = boosted.surface.tintWith(tint, 0.12f),
        surfaceContainerLowest = boosted.surfaceContainerLowest.tintWith(tint, 0.05f),
        surfaceContainerLow = boosted.surfaceContainerLow.tintWith(tint, 0.08f),
        surfaceContainer = boosted.surfaceContainer.tintWith(tint, 0.12f),
        surfaceContainerHigh = boosted.surfaceContainerHigh.tintWith(tint, 0.16f),
        surfaceContainerHighest = boosted.surfaceContainerHighest.tintWith(tint, 0.20f),
        surfaceBright = boosted.surfaceBright.tintWith(tint, 0.04f),
        surfaceDim = boosted.surfaceDim.tintWith(tint, 0.18f),
        surfaceVariant = boosted.surfaceVariant.tintWith(tint, 0.14f),
        surfaceTint = tint,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,

    secondary = TealSecondaryDark,
    onSecondary = TealOnSecondaryDark,
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = TealOnSecondaryContainerDark,

    tertiary = PurpleTertiaryDark,
    onTertiary = PurpleOnTertiaryDark,
    tertiaryContainer = PurpleTertiaryContainerDark,
    onTertiaryContainer = PurpleOnTertiaryContainerDark,

    error = ErrorColorDark,
    onError = ErrorOnColorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,

    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark,
    surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    scrim = ScrimDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,

    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,

    tertiary = PurpleTertiary,
    onTertiary = PurpleOnTertiary,
    tertiaryContainer = PurpleTertiaryContainer,
    onTertiaryContainer = PurpleOnTertiaryContainer,

    error = ErrorColor,
    onError = ErrorOnColor,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorOnContainer,

    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceBright = SurfaceBrightLight,
    surfaceDim = SurfaceDimLight,
    surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
    scrim = ScrimLight,
)

@Composable
fun IndolentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    contrastLevel: ContrastLevel = ContrastLevel.Standard,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            base.amplifyDynamicColors()
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
