package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppThemeStyle
import com.example.model.AxolaCustomTheme
import com.example.model.DarkModePreference
import com.example.model.MetroAccent

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.STANDARD }
val LocalDarkThemeActive = staticCompositionLocalOf { false }
val LocalMetroAccent = staticCompositionLocalOf { MetroAccent.COBALT }
val LocalAxolaCustomTheme = staticCompositionLocalOf { AxolaCustomTheme() }

private val DarkColorScheme = darkColorScheme(
    primary = NavyPrimaryDark,
    onPrimary = NavyOnPrimaryDark,
    primaryContainer = NavyPrimaryContainerDark,
    onPrimaryContainer = NavyOnPrimaryContainerDark,
    secondary = NavySecondaryDark,
    onSecondary = NavyOnSecondaryDark,
    secondaryContainer = NavySecondaryContainerDark,
    onSecondaryContainer = NavyOnSecondaryContainerDark,
    tertiary = NavyTertiaryDark,
    onTertiary = NavyOnTertiaryDark,
    tertiaryContainer = NavyTertiaryContainerDark,
    onTertiaryContainer = NavyOnTertiaryContainerDark,
    surface = NavySurfaceDark,
    onSurface = NavyOnSurfaceDark,
    surfaceVariant = NavySurfaceVariantDark,
    onSurfaceVariant = NavyOnSurfaceVariantDark,
    surfaceContainerLowest = NavySurfaceContainerLowestDark,
    surfaceContainerLow = NavySurfaceContainerLowDark,
    surfaceContainer = NavySurfaceContainerDark,
    surfaceContainerHigh = NavySurfaceContainerHighDark,
    surfaceContainerHighest = NavySurfaceContainerHighestDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimaryLight,
    onPrimary = NavyOnPrimaryLight,
    primaryContainer = NavyPrimaryContainerLight,
    onPrimaryContainer = NavyOnPrimaryContainerLight,
    secondary = NavySecondaryLight,
    onSecondary = NavyOnSecondaryLight,
    secondaryContainer = NavySecondaryContainerLight,
    onSecondaryContainer = NavyOnSecondaryContainerLight,
    tertiary = NavyTertiaryLight,
    onTertiary = NavyOnTertiaryLight,
    tertiaryContainer = NavyTertiaryContainerLight,
    onTertiaryContainer = NavyOnTertiaryContainerLight,
    surface = NavySurfaceLight,
    onSurface = NavyOnSurfaceLight,
    surfaceVariant = NavySurfaceVariantLight,
    onSurfaceVariant = NavyOnSurfaceVariantLight,
    surfaceContainerLowest = NavySurfaceContainerLowestLight,
    surfaceContainerLow = NavySurfaceContainerLowLight,
    surfaceContainer = NavySurfaceContainerLight,
    surfaceContainerHigh = NavySurfaceContainerHighLight,
    surfaceContainerHighest = NavySurfaceContainerHighestLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val NothingOsColorScheme = darkColorScheme(
    primary = NothingWhite,
    onPrimary = NothingBlack,
    primaryContainer = NothingMatteElevated,
    onPrimaryContainer = NothingWhite,
    secondary = NothingAsh,
    onSecondary = NothingBlack,
    secondaryContainer = NothingMatteHighest,
    onSecondaryContainer = NothingWhite,
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    tertiaryContainer = NothingRedContainer,
    onTertiaryContainer = NothingOnRedContainer,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingBlack,
    onSurface = NothingWhite,
    surfaceVariant = NothingMatteElevated,
    onSurfaceVariant = NothingAsh,
    outline = NothingOutline,
    outlineVariant = NothingOutline,
    surfaceContainerLowest = NothingBlack,
    surfaceContainerLow = NothingSurfaceLow,
    surfaceContainer = NothingMatteGrey,
    surfaceContainerHigh = NothingMatteElevated,
    surfaceContainerHighest = NothingMatteHighest,
    error = NothingRed,
    onError = NothingWhite,
    errorContainer = NothingRedContainer,
    onErrorContainer = NothingOnRedContainer
)

private fun metroColorScheme(
    accent: MetroAccent,
    isDark: Boolean
): ColorScheme {
    val primaryAccent = Color(accent.colorHex)
    return if (isDark) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent,
            onPrimaryContainer = Color.White,
            secondary = primaryAccent,
            onSecondary = Color.White,
            secondaryContainer = MetroTileElevatedDark,
            onSecondaryContainer = Color.White,
            tertiary = MetroEmerald,
            onTertiary = Color.White,
            tertiaryContainer = MetroTileElevatedDark,
            onTertiaryContainer = Color.White,
            background = MetroBlack,
            onBackground = Color.White,
            surface = MetroBlack,
            onSurface = Color.White,
            surfaceVariant = MetroTileElevatedDark,
            onSurfaceVariant = Color(0xFFB3B3B3),
            outline = Color(0xFF333333),
            outlineVariant = Color(0xFF262626),
            surfaceContainerLowest = MetroBlack,
            surfaceContainerLow = MetroTileDark,
            surfaceContainer = MetroTileDark,
            surfaceContainerHigh = MetroTileElevatedDark,
            surfaceContainerHighest = Color(0xFF333333),
            error = MetroCrimson,
            onError = Color.White,
            errorContainer = MetroCrimson,
            onErrorContainer = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent,
            onPrimaryContainer = Color.White,
            secondary = primaryAccent,
            onSecondary = Color.White,
            secondaryContainer = MetroTileElevatedLight,
            onSecondaryContainer = Color(0xFF111111),
            tertiary = MetroEmerald,
            onTertiary = Color.White,
            tertiaryContainer = MetroTileElevatedLight,
            onTertiaryContainer = Color(0xFF111111),
            background = MetroWhite,
            onBackground = Color(0xFF111111),
            surface = MetroWhite,
            onSurface = Color(0xFF111111),
            surfaceVariant = MetroTileElevatedLight,
            onSurfaceVariant = Color(0xFF555555),
            outline = Color(0xFFCCCCCC),
            outlineVariant = Color(0xFFDADADA),
            surfaceContainerLowest = MetroWhite,
            surfaceContainerLow = MetroTileLight,
            surfaceContainer = MetroTileLight,
            surfaceContainerHigh = MetroTileElevatedLight,
            surfaceContainerHighest = Color(0xFFD6D6D6),
            error = MetroCrimson,
            onError = Color.White,
            errorContainer = MetroCrimson,
            onErrorContainer = Color.White
        )
    }
}

private fun iosLiquidGlassColorScheme(isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = IosBlue,
            onPrimary = Color.White,
            primaryContainer = IosBlue.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFD8ECFF),
            secondary = IosOrange,
            onSecondary = Color.Black,
            secondaryContainer = IosOrange.copy(alpha = 0.20f),
            onSecondaryContainer = Color(0xFFFFE5BA),
            tertiary = IosGreen,
            onTertiary = Color.Black,
            tertiaryContainer = IosGreen.copy(alpha = 0.20f),
            onTertiaryContainer = Color(0xFFD2F8DC),
            background = IosDarkCanvas,
            onBackground = Color.White,
            surface = IosDarkCanvas,
            onSurface = Color.White,
            surfaceVariant = IosDarkElevated,
            onSurfaceVariant = Color(0xFFAEAEB2),
            outline = IosDarkDivider,
            outlineVariant = IosDarkDivider,
            surfaceContainerLowest = IosDarkCanvas,
            surfaceContainerLow = IosDarkCard,
            surfaceContainer = IosDarkCard,
            surfaceContainerHigh = IosDarkElevated,
            surfaceContainerHighest = IosDarkHighest,
            error = IosRed,
            onError = Color.White,
            errorContainer = IosRed.copy(alpha = 0.22f),
            onErrorContainer = Color(0xFFFFD8D4)
        )
    } else {
        lightColorScheme(
            primary = IosBlueLight,
            onPrimary = Color.White,
            primaryContainer = IosBlueLight.copy(alpha = 0.13f),
            onPrimaryContainer = Color(0xFF003B7A),
            secondary = IosOrangeLight,
            onSecondary = Color.White,
            secondaryContainer = IosOrange.copy(alpha = 0.16f),
            onSecondaryContainer = Color(0xFF5C3400),
            tertiary = IosGreenLight,
            onTertiary = Color.White,
            tertiaryContainer = IosGreen.copy(alpha = 0.16f),
            onTertiaryContainer = Color(0xFF0A471A),
            background = IosLightCanvas,
            onBackground = Color(0xFF000000),
            surface = IosLightCanvas,
            onSurface = Color(0xFF000000),
            surfaceVariant = IosLightElevated,
            onSurfaceVariant = Color(0xFF636366),
            outline = IosLightDivider,
            outlineVariant = IosLightDivider,
            surfaceContainerLowest = IosLightCard,
            surfaceContainerLow = IosLightCard,
            surfaceContainer = IosLightCard,
            surfaceContainerHigh = IosLightElevated,
            surfaceContainerHighest = IosLightHighest,
            error = IosRedLight,
            onError = Color.White,
            errorContainer = IosRed.copy(alpha = 0.14f),
            onErrorContainer = Color(0xFF6E000A)
        )
    }
}

private fun oneUiColorScheme(
    context: Context,
    isDark: Boolean
): ColorScheme {
    val dynamicBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull()
    } else null

    return if (isDark) {
        val primary = dynamicBase?.primary ?: OneUiBlueDark
        val secondary = dynamicBase?.secondary ?: Color(0xFF7CB4FF)
        val tertiary = dynamicBase?.tertiary ?: Color(0xFF66D1FF)
        val cardSurface = lerp(OneUiDarkCardBase, primary, 0.05f)
        val elevatedSurface = lerp(OneUiDarkElevatedBase, primary, 0.07f)
        val highestSurface = lerp(OneUiDarkHighestBase, primary, 0.09f)
        val onSurfacePrimary = Color(0xFFF5F6FA)

        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = lerp(OneUiDarkCardBase, primary, 0.22f),
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = Color.Black,
            secondaryContainer = lerp(OneUiDarkCardBase, secondary, 0.18f),
            onSecondaryContainer = Color(0xFFF2F4F8),
            tertiary = tertiary,
            onTertiary = Color.Black,
            tertiaryContainer = lerp(OneUiDarkCardBase, tertiary, 0.20f),
            onTertiaryContainer = Color.White,
            background = Color.Black,
            onBackground = onSurfacePrimary,
            surface = Color.Black,
            onSurface = onSurfacePrimary,
            surfaceVariant = elevatedSurface,
            onSurfaceVariant = onSurfacePrimary.copy(alpha = 0.65f),
            outline = OneUiDividerDark,
            outlineVariant = OneUiDividerDark,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = cardSurface,
            surfaceContainer = cardSurface,
            surfaceContainerHigh = elevatedSurface,
            surfaceContainerHighest = highestSurface,
            error = OneUiErrorRedDark,
            onError = Color.Black,
            errorContainer = Color(0xFF3A1214),
            onErrorContainer = Color(0xFFFFD9D6)
        )
    } else {
        val primary = dynamicBase?.primary ?: OneUiBlueLight
        val secondary = dynamicBase?.secondary ?: Color(0xFF3E91FF)
        val tertiary = dynamicBase?.tertiary ?: Color(0xFF0078D4)
        val lightCanvas = lerp(OneUiLightCanvasBase, primary, 0.025f)
        val elevatedTint = lerp(OneUiLightElevatedTintBase, primary, 0.04f)
        val highestTint = lerp(OneUiLightHighestTintBase, primary, 0.06f)
        val onSurfacePrimary = Color(0xFF101318)

        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, primary, 0.13f),
            onPrimaryContainer = Color(0xFF0B1C36),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, secondary, 0.11f),
            onSecondaryContainer = Color(0xFF18202C),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = lerp(Color.White, tertiary, 0.13f),
            onTertiaryContainer = Color(0xFF092238),
            background = lightCanvas,
            onBackground = onSurfacePrimary,
            surface = lightCanvas,
            onSurface = onSurfacePrimary,
            surfaceVariant = elevatedTint,
            onSurfaceVariant = onSurfacePrimary.copy(alpha = 0.65f),
            outline = OneUiDividerLight,
            outlineVariant = OneUiDividerLight,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = elevatedTint,
            surfaceContainerHighest = highestTint,
            error = OneUiErrorRedLight,
            onError = Color.White,
            errorContainer = Color(0xFFFFE5E3),
            onErrorContainer = Color(0xFF690005)
        )
    }
}

private fun contrastColorFor(bg: Color): Color {
    val luminance = (0.299f * bg.red) + (0.587f * bg.green) + (0.114f * bg.blue)
    return if (luminance > 0.60f) Color(0xFF101318) else Color.White
}

internal fun axolaCustomColorScheme(custom: AxolaCustomTheme): ColorScheme {
    val bgSpec = custom.backgroundStyle
    val isDark = bgSpec.isDark
    val canvas = Color(bgSpec.canvasArgb)
    val card = Color(bgSpec.cardArgb)
    val elevated = Color(bgSpec.elevatedArgb)
    val primary = Color(custom.primaryColorArgb)
    val secondary = Color(custom.secondaryColorArgb)
    val onBg = if (isDark) Color(0xFFF5F7FA) else Color(0xFF101318)
    val onBgMuted = if (isDark) Color(0xFFA7B0C0) else Color(0xFF566070)
    val primaryContainer = lerp(card, primary, if (isDark) 0.24f else 0.16f)
    val secondaryContainer = lerp(card, secondary, if (isDark) 0.24f else 0.16f)
    val outlineColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFFD0D7E2)

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = contrastColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = contrastColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color.White,
            tertiary = secondary,
            onTertiary = contrastColorFor(secondary),
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = Color.White,
            background = canvas,
            onBackground = onBg,
            surface = canvas,
            onSurface = onBg,
            surfaceVariant = elevated,
            onSurfaceVariant = onBgMuted,
            outline = outlineColor,
            outlineVariant = outlineColor.copy(alpha = 0.7f),
            surfaceContainerLowest = canvas,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = elevated,
            surfaceContainerHighest = lerp(elevated, primary, 0.08f),
            error = Color(0xFFFF5C5C),
            onError = Color.Black,
            errorContainer = Color(0xFF3E1216),
            onErrorContainer = Color(0xFFFFD8D6)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = contrastColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color(0xFF101318),
            secondary = secondary,
            onSecondary = contrastColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFF101318),
            tertiary = secondary,
            onTertiary = contrastColorFor(secondary),
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = Color(0xFF101318),
            background = canvas,
            onBackground = onBg,
            surface = canvas,
            onSurface = onBg,
            surfaceVariant = elevated,
            onSurfaceVariant = onBgMuted,
            outline = outlineColor,
            outlineVariant = outlineColor.copy(alpha = 0.8f),
            surfaceContainerLowest = card,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = elevated,
            surfaceContainerHighest = lerp(elevated, primary, 0.08f),
            error = Color(0xFFD32F2F),
            onError = Color.White,
            errorContainer = Color(0xFFFFE4E2),
            onErrorContainer = Color(0xFF690005)
        )
    }
}

@Composable
fun AgendaPlusTheme(
    appThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    metroAccent: MetroAccent = MetroAccent.COBALT,
    axolaCustomTheme: AxolaCustomTheme = AxolaCustomTheme(),
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS
    val isOneUi = appThemeStyle == AppThemeStyle.ONE_UI
    val isMetro = appThemeStyle == AppThemeStyle.WINDOWS_PHONE
    val isIos = appThemeStyle == AppThemeStyle.IOS_LIQUID_GLASS
    val isAxola = appThemeStyle == AppThemeStyle.AXOLA_CUSTOM

    val effectiveDark = when {
        isNothing -> true
        isAxola -> axolaCustomTheme.backgroundStyle.isDark
        darkModePreference == DarkModePreference.LIGHT -> false
        darkModePreference == DarkModePreference.DARK -> true
        else -> systemDarkTheme
    }

    val colorScheme = when {
        isNothing -> NothingOsColorScheme
        isOneUi -> oneUiColorScheme(context = context, isDark = effectiveDark)
        isMetro -> metroColorScheme(accent = metroAccent, isDark = effectiveDark)
        isIos -> iosLiquidGlassColorScheme(isDark = effectiveDark)
        isAxola -> axolaCustomColorScheme(axolaCustomTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = when {
        isNothing -> NothingOsTypography
        isOneUi -> OneUiTypography
        isMetro -> MetroTypography
        isIos -> IosLiquidGlassTypography
        isAxola -> axolaTypography(axolaCustomTheme.typographyStyle)
        else -> Typography
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val useDarkIcons = !isNothing && !effectiveDark
                controller.isAppearanceLightStatusBars = useDarkIcons
                controller.isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }

    CompositionLocalProvider(
        LocalAppThemeStyle provides appThemeStyle,
        LocalDarkThemeActive provides effectiveDark,
        LocalMetroAccent provides metroAccent,
        LocalAxolaCustomTheme provides axolaCustomTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
