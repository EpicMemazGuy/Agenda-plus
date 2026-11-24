package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle
import com.example.ui.theme.LocalAppThemeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Shared spring specification for cohesive motion across Agenda+.
 */
fun <T> agendaSpring(
    dampingRatio: Float = Spring.DampingRatioLowBouncy,
    stiffness: Float = Spring.StiffnessMediumLow
): FiniteAnimationSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * Press-scale tactility across cards and tiles.
 * - Windows Phone Metro: crisp tilt/press detent (0.962f scale + subtle 2.5deg perspective tilt).
 * - Other themes: tactile 0.975f spring compression.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.agendaPressable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val themeStyle = LocalAppThemeStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val targetScale = if (!isPressed) 1f else if (themeStyle == AppThemeStyle.WINDOWS_PHONE) 0.965f else 0.975f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "agenda_press_scale"
    )
    val metroTilt by animateFloatAsState(
        targetValue = if (isPressed && themeStyle == AppThemeStyle.WINDOWS_PHONE) -2.4f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "agenda_metro_tilt"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (metroTilt != 0f) {
                rotationX = metroTilt
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        }
        .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            onClick = onClick,
            onLongClick = onLongClick?.let { cb ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cb()
                }
            }
        )
}

/**
 * Subtle staggered vertical slide + fade entrance without delaying initial cached display.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 12.dp.toPx() }
    val alphaAnim = remember { Animatable(if (index == 0) 0.85f else 0f) }
    val offsetAnim = remember { Animatable(if (index == 0) 0f else initialOffsetPx) }

    LaunchedEffect(Unit) {
        val staggerDelay = (index.coerceIn(0, 7) * 40L)
        if (staggerDelay > 0L) delay(staggerDelay)
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = agendaSpring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    this.graphicsLayer {
        alpha = alphaAnim.value
        translationY = offsetAnim.value
    }
}

/**
 * Smoothly animated numeric score formatter for GPA, averages, and what-if targets.
 */
@Composable
fun rememberAnimatedScoreText(
    targetValue: Double,
    decimals: Int = 1,
    locale: Locale = Locale("nl", "NL")
): String {
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animated_score"
    )
    return String.format(locale, "%.${decimals}f", animated)
}

@Composable
fun rememberAnimatedIntCount(targetValue: Int): Int {
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "animated_int_count"
    )
    return animated.toInt()
}
