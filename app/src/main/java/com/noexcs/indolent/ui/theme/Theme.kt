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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.noexcs.indolent.R

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

val AuroraLightScheme = lightColorScheme(
    primary = Color(0xFF5B5FEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E5FF),
    onPrimaryContainer = Color(0xFF11144A),

    secondary = Color(0xFF7A5AF8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE7FF),
    onSecondaryContainer = Color(0xFF22114D),

    tertiary = Color(0xFF00A3FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F1FF),

    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191B2C),

    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF191B2C),

    surfaceVariant = Color(0xFFE7E8F4),
    onSurfaceVariant = Color(0xFF46485C),

    outline = Color(0xFF73768A),

    surfaceContainer = Color(0xFFF1F2FC),
    surfaceContainerHigh = Color(0xFFE8E9F8),
    surfaceContainerHighest = Color(0xFFDDDEF0),

    error = Color(0xFFDC362E)
)

val MatchaLightScheme = lightColorScheme(
    primary = Color(0xFF4F6F52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8D8),

    secondary = Color(0xFF7A8B5A),
    secondaryContainer = Color(0xFFE7EEDB),

    tertiary = Color(0xFFB08968),
    tertiaryContainer = Color(0xFFF3E2D3),

    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1C1B18),

    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF1C1B18),

    surfaceVariant = Color(0xFFE7E3DA),
    onSurfaceVariant = Color(0xFF4A463F),

    outline = Color(0xFF7C776F),

    surfaceContainer = Color(0xFFF0EEE8),
    surfaceContainerHigh = Color(0xFFE7E4DC),

    error = Color(0xFFBA1A1A)
)

val CyberDarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF00363D),

    secondary = Color(0xFFFF4DCA),
    secondaryContainer = Color(0xFF5E1148),

    tertiary = Color(0xFF8BFF7A),
    tertiaryContainer = Color(0xFF1E4D1A),

    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFE2E2E9),

    surface = Color(0xFF101017),
    onSurface = Color(0xFFE2E2E9),

    surfaceVariant = Color(0xFF2A2A35),
    onSurfaceVariant = Color(0xFFC7C6D1),

    outline = Color(0xFF8F909A),

    surfaceContainer = Color(0xFF15151D),
    surfaceContainerHigh = Color(0xFF1B1B24),
    surfaceContainerHighest = Color(0xFF252530),

    error = Color(0xFFFF5449)
)

val NeutralLightScheme = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,

    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF26A69A),

    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1D1F),

    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF5C5C5F),

    outline = Color(0xFFD1D1D6),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF0F0F4),
    surfaceContainerHigh = Color(0xFFE8E8ED),
    surfaceContainerHighest = Color(0xFFE1E1E8),
)

val CrimsonScheme = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),

    secondary = Color(0xFF775651),
    secondaryContainer = Color(0xFFFFDAD4),

    tertiary = Color(0xFF9A4521),
    tertiaryContainer = Color(0xFFFFDBD0),

    background = Color(0xFFFFF8F7),
    surface = Color(0xFFFFF8F7),

    surfaceContainer = Color(0xFFF5E9E7),
    surfaceContainerHigh = Color(0xFFEEDFDB),

    outline = Color(0xFF85736F),
)

data class ThemeDescriptor(
    val key: String,
    val labelRes: Int = 0,
    val label: String = "",
    val isDark: Boolean,
    val supportsDynamicColor: Boolean = false,
    val usesSeedColor: Boolean = false,
    val colorScheme: ColorScheme? = null,
)

val SunsetOrangeScheme = lightColorScheme(
    primary = Color(0xFFE86A17),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),

    secondary = Color(0xFF8A5A44),
    secondaryContainer = Color(0xFFFFDBC8),

    tertiary = Color(0xFF705D00),
    tertiaryContainer = Color(0xFFFDE287),

    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),

    surfaceContainer = Color(0xFFF8ECE5),
    surfaceContainerHigh = Color(0xFFF1E2DA),

    outline = Color(0xFF85736B),
)

val GoldenYellowScheme = lightColorScheme(
    primary = Color(0xFFB78103),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08C),

    secondary = Color(0xFF6F5B40),
    secondaryContainer = Color(0xFFF8DFC1),

    tertiary = Color(0xFF516440),
    tertiaryContainer = Color(0xFFD3EABC),

    background = Color(0xFFFFFAF2),
    surface = Color(0xFFFFFAF2),

    surfaceContainer = Color(0xFFF7EFE0),
    surfaceContainerHigh = Color(0xFFF0E7D4),

    outline = Color(0xFF7A766B),
)

val EmeraldGreenScheme = lightColorScheme(
    primary = Color(0xFF2E7D5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D2),

    secondary = Color(0xFF4F6355),
    secondaryContainer = Color(0xFFD2E8D5),

    tertiary = Color(0xFF39656B),
    tertiaryContainer = Color(0xFFBCEBF2),

    background = Color(0xFFF6FBF7),
    surface = Color(0xFFF6FBF7),

    surfaceContainer = Color(0xFFEAF4ED),
    surfaceContainerHigh = Color(0xFFE1ECE4),

    outline = Color(0xFF6F7971),
)

val CyanBlueScheme = lightColorScheme(
    primary = Color(0xFF006E90),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F0FF),

    secondary = Color(0xFF4A626C),
    secondaryContainer = Color(0xFFCDE7F2),

    tertiary = Color(0xFF5A5C7A),
    tertiaryContainer = Color(0xFFE0E0FF),

    background = Color(0xFFF5FAFD),
    surface = Color(0xFFF5FAFD),

    surfaceContainer = Color(0xFFEAF2F7),
    surfaceContainerHigh = Color(0xFFE0EAF1),

    outline = Color(0xFF6E7B83),
)

private val materialDynamicColors = MaterialDynamicColors()

fun seedColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val hct = Hct.fromInt(seedColor.toArgb())
    val scheme = SchemeContent(hct, darkTheme, 0.0)
    val c = materialDynamicColors

    return if (darkTheme) darkColorScheme(
        primary = Color(c.primary().getArgb(scheme)),
        onPrimary = Color(c.onPrimary().getArgb(scheme)),
        primaryContainer = Color(c.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(c.onPrimaryContainer().getArgb(scheme)),
        secondary = Color(c.secondary().getArgb(scheme)),
        onSecondary = Color(c.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(c.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(c.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(c.tertiary().getArgb(scheme)),
        onTertiary = Color(c.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(c.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(c.onTertiaryContainer().getArgb(scheme)),
        error = Color(c.error().getArgb(scheme)),
        onError = Color(c.onError().getArgb(scheme)),
        errorContainer = Color(c.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(c.onErrorContainer().getArgb(scheme)),
        background = Color(c.background().getArgb(scheme)),
        onBackground = Color(c.onBackground().getArgb(scheme)),
        surface = Color(c.surface().getArgb(scheme)),
        onSurface = Color(c.onSurface().getArgb(scheme)),
        surfaceVariant = Color(c.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(c.onSurfaceVariant().getArgb(scheme)),
        outline = Color(c.outline().getArgb(scheme)),
        outlineVariant = Color(c.outlineVariant().getArgb(scheme)),
        inverseSurface = Color(c.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(c.inverseOnSurface().getArgb(scheme)),
        inversePrimary = Color(c.inversePrimary().getArgb(scheme)),
        surfaceContainerLowest = Color(c.surfaceContainerLowest().getArgb(scheme)),
        surfaceContainerLow = Color(c.surfaceContainerLow().getArgb(scheme)),
        surfaceContainer = Color(c.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(c.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(c.surfaceContainerHighest().getArgb(scheme)),
    ) else lightColorScheme(
        primary = Color(c.primary().getArgb(scheme)),
        onPrimary = Color(c.onPrimary().getArgb(scheme)),
        primaryContainer = Color(c.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(c.onPrimaryContainer().getArgb(scheme)),
        secondary = Color(c.secondary().getArgb(scheme)),
        onSecondary = Color(c.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(c.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(c.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(c.tertiary().getArgb(scheme)),
        onTertiary = Color(c.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(c.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(c.onTertiaryContainer().getArgb(scheme)),
        error = Color(c.error().getArgb(scheme)),
        onError = Color(c.onError().getArgb(scheme)),
        errorContainer = Color(c.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(c.onErrorContainer().getArgb(scheme)),
        background = Color(c.background().getArgb(scheme)),
        onBackground = Color(c.onBackground().getArgb(scheme)),
        surface = Color(c.surface().getArgb(scheme)),
        onSurface = Color(c.onSurface().getArgb(scheme)),
        surfaceVariant = Color(c.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(c.onSurfaceVariant().getArgb(scheme)),
        outline = Color(c.outline().getArgb(scheme)),
        outlineVariant = Color(c.outlineVariant().getArgb(scheme)),
        inverseSurface = Color(c.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(c.inverseOnSurface().getArgb(scheme)),
        inversePrimary = Color(c.inversePrimary().getArgb(scheme)),
        surfaceContainerLowest = Color(c.surfaceContainerLowest().getArgb(scheme)),
        surfaceContainerLow = Color(c.surfaceContainerLow().getArgb(scheme)),
        surfaceContainer = Color(c.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(c.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(c.surfaceContainerHighest().getArgb(scheme)),
    )
}

object ThemeRegistry {
    private val builtIn = listOf(
        ThemeDescriptor("system", R.string.theme_system, isDark = false, supportsDynamicColor = true),
        ThemeDescriptor("light", R.string.theme_light, isDark = false, colorScheme = LightColorScheme),
        ThemeDescriptor("dark", R.string.theme_dark, isDark = true, colorScheme = DarkColorScheme),
        ThemeDescriptor("aurora", R.string.theme_aurora, isDark = false, colorScheme = AuroraLightScheme),
        ThemeDescriptor("matcha", R.string.theme_matcha, isDark = false, colorScheme = MatchaLightScheme),
        ThemeDescriptor("cyber", R.string.theme_cyber, isDark = true, colorScheme = CyberDarkScheme),
        ThemeDescriptor("neutral", R.string.theme_neutral, isDark = false, colorScheme = NeutralLightScheme),
        ThemeDescriptor("crimson", R.string.theme_crimson, isDark = false, colorScheme = CrimsonScheme),
        ThemeDescriptor("sunset_orange", R.string.theme_sunset_orange, isDark = false, colorScheme = SunsetOrangeScheme),
        ThemeDescriptor("golden_yellow", R.string.theme_golden_yellow, isDark = false, colorScheme = GoldenYellowScheme),
        ThemeDescriptor("emerald_green", R.string.theme_emerald_green, isDark = false, colorScheme = EmeraldGreenScheme),
        ThemeDescriptor("cyan_blue", R.string.theme_cyan_blue, isDark = false, colorScheme = CyanBlueScheme),
        ThemeDescriptor("seed", R.string.theme_seed, isDark = false, usesSeedColor = true),
    )

    private val _dynamicThemes = mutableListOf<ThemeDescriptor>()
    val dynamicThemes: List<ThemeDescriptor> get() = _dynamicThemes.toList()

    val themes: List<ThemeDescriptor> get() = builtIn + _dynamicThemes

    val defaultTheme: ThemeDescriptor get() = themes.first()

    fun findByKey(key: String): ThemeDescriptor = themes.find { it.key == key } ?: defaultTheme

    fun addDynamic(descriptor: ThemeDescriptor) {
        _dynamicThemes.removeAll { it.key == descriptor.key }
        _dynamicThemes.add(descriptor)
    }

    fun removeDynamic(key: String): Boolean = _dynamicThemes.removeAll { it.key == key }

    fun isDynamic(key: String): Boolean = _dynamicThemes.any { it.key == key }

    fun loadDynamic(json: String) {
        _dynamicThemes.clear()
        if (json.isBlank() || json == "[]") return
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val key = obj.getString("key")
                val label = obj.getString("label")
                val seedColor = Color(obj.getInt("seedColor"))
                val isDark = obj.optBoolean("isDark", false)
                val scheme = seedColorScheme(seedColor, isDark)
                _dynamicThemes.add(ThemeDescriptor(
                    key = key, label = label, isDark = isDark,
                    usesSeedColor = false, colorScheme = scheme
                ))
            }
        } catch (_: Exception) { }
    }

    fun toDynamicJson(): String {
        if (_dynamicThemes.isEmpty()) return "[]"
        val arr = org.json.JSONArray()
        _dynamicThemes.forEach { theme ->
            val obj = org.json.JSONObject()
            obj.put("key", theme.key)
            obj.put("label", theme.label)
            // Extract the approximate seed color from the scheme's primary
            val primaryArgb = theme.colorScheme?.primary?.toArgb() ?: 0xFF6750A4.toInt()
            obj.put("seedColor", primaryArgb)
            obj.put("isDark", theme.isDark)
            arr.put(obj)
        }
        return arr.toString()
    }

    val themeKeys: List<String> get() = themes.map { it.key }

    fun nextDynamicKey(): String {
        var i = 0
        while (themes.any { it.key == "dynamic_$i" }) i++
        return "dynamic_$i"
    }
}

object ThemeState {
    var themeKey by mutableStateOf("system")
    var dynamicColor by mutableStateOf(true)
    var seedColor by mutableStateOf(Color.Unspecified)
    var dynamicThemesVersion by mutableStateOf(0)

    fun applyTheme(key: String) {
        themeKey = key
    }

    fun applySeedColor(color: Color) {
        seedColor = color
    }

    fun addDynamicTheme(descriptor: ThemeDescriptor) {
        ThemeRegistry.addDynamic(descriptor)
        dynamicThemesVersion++
    }

    fun removeDynamicTheme(key: String) {
        if (ThemeRegistry.removeDynamic(key)) {
            dynamicThemesVersion++
        }
    }
}

@Composable
fun IndolentTheme(
    themeKey: String = "system",
    dynamicColor: Boolean = true,
    seedColor: Color = Color.Unspecified,
    contrastLevel: ContrastLevel = ContrastLevel.Standard,
    content: @Composable () -> Unit
) {
    val descriptor = remember(themeKey) { ThemeRegistry.findByKey(themeKey) }
    val darkTheme = if (descriptor.supportsDynamicColor || descriptor.usesSeedColor) isSystemInDarkTheme() else descriptor.isDark

    val colorScheme = when {
        descriptor.supportsDynamicColor && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        descriptor.usesSeedColor && seedColor != Color.Unspecified -> seedColorScheme(seedColor, darkTheme)
        descriptor.colorScheme != null -> descriptor.colorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
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
