package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppThemeStyle

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.STANDARD }

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

@Composable
fun AgendaPlusTheme(
    appThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS

    val colorScheme = when {
        isNothing -> NothingOsColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = if (isNothing) NothingOsTypography else Typography

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val useDarkIcons = !isNothing && !darkTheme
                controller.isAppearanceLightStatusBars = useDarkIcons
                controller.isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }

    CompositionLocalProvider(LocalAppThemeStyle provides appThemeStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
