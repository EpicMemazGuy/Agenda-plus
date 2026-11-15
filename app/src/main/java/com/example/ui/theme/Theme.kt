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
import com.example.model.DarkModePreference

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.STANDARD }
val LocalDarkThemeActive = staticCompositionLocalOf { false }

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
        val cardSurface = lerp(OneUiDarkCardBase, primary, 0.06f)
        val elevatedSurface = lerp(OneUiDarkElevatedBase, primary, 0.08f)
        val highestSurface = lerp(OneUiDarkHighestBase, primary, 0.10f)

        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = lerp(OneUiDarkCardBase, primary, 0.24f),
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = Color.Black,
            secondaryContainer = lerp(OneUiDarkCardBase, secondary, 0.20f),
            onSecondaryContainer = Color(0xFFF2F4F8),
            tertiary = tertiary,
            onTertiary = Color.Black,
            tertiaryContainer = lerp(OneUiDarkCardBase, tertiary, 0.22f),
            onTertiaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color(0xFFF5F6FA),
            surface = Color.Black,
            onSurface = Color(0xFFF5F6FA),
            surfaceVariant = elevatedSurface,
            onSurfaceVariant = Color(0xFF9EA3B0),
            outline = Color.White.copy(alpha = 0.09f),
            outlineVariant = Color.White.copy(alpha = 0.08f),
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
        val lightCanvas = lerp(OneUiLightCanvasBase, primary, 0.03f)
        val elevatedTint = lerp(OneUiLightElevatedTintBase, primary, 0.05f)
        val highestTint = lerp(OneUiLightHighestTintBase, primary, 0.08f)

        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, primary, 0.14f),
            onPrimaryContainer = Color(0xFF0B1C36),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, secondary, 0.12f),
            onSecondaryContainer = Color(0xFF18202C),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = lerp(Color.White, tertiary, 0.14f),
            onTertiaryContainer = Color(0xFF092238),
            background = lightCanvas,
            onBackground = Color(0xFF101318),
            surface = lightCanvas,
            onSurface = Color(0xFF101318),
            surfaceVariant = elevatedTint,
            onSurfaceVariant = Color(0xFF5D6472),
            outline = Color.Black.copy(alpha = 0.06f),
            outlineVariant = Color.Black.copy(alpha = 0.06f),
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

@Composable
fun AgendaPlusTheme(
    appThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS
    val isOneUi = appThemeStyle == AppThemeStyle.ONE_UI

    val effectiveDark = when {
        isNothing -> true
        darkModePreference == DarkModePreference.LIGHT -> false
        darkModePreference == DarkModePreference.DARK -> true
        else -> systemDarkTheme
    }

    val colorScheme = when {
        isNothing -> NothingOsColorScheme
        isOneUi -> oneUiColorScheme(context = context, isDark = effectiveDark)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = when {
        isNothing -> NothingOsTypography
        isOneUi -> OneUiTypography
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
        LocalDarkThemeActive provides effectiveDark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
