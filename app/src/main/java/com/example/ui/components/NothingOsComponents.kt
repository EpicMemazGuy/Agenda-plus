package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.NothingAsh
import com.example.ui.theme.NothingOutline
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.OneUiDividerDark
import com.example.ui.theme.OneUiDividerLight
import kotlin.math.abs

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IosDarkDivider
import com.example.ui.theme.IosLightDivider
import com.example.ui.theme.IosRed
import java.util.Locale

val isNothingOs: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.NOTHING_OS

val isOneUi85: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.ONE_UI

val isWindowsPhone: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.WINDOWS_PHONE

val isIosLiquidGlass: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.IOS_LIQUID_GLASS

val isFloatingOverlayTheme: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.ONE_UI ||
        LocalAppThemeStyle.current == AppThemeStyle.IOS_LIQUID_GLASS

@Composable
fun appCardCornerRadius(
    standard: Dp = 18.dp,
    nothing: Dp = 24.dp
): Dp {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> 26.dp
        AppThemeStyle.IOS_LIQUID_GLASS -> 22.dp
        AppThemeStyle.WINDOWS_PHONE -> 0.dp
        AppThemeStyle.NOTHING_OS -> nothing
        AppThemeStyle.STANDARD -> standard
    }
}

@Composable
fun appCardShape(
    standard: Dp = 18.dp,
    nothing: Dp = 24.dp
): Shape {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> SquircleShape(26.dp)
        AppThemeStyle.IOS_LIQUID_GLASS -> SquircleShape(22.dp)
        AppThemeStyle.WINDOWS_PHONE -> RectangleShape
        AppThemeStyle.NOTHING_OS -> RoundedCornerShape(nothing)
        AppThemeStyle.STANDARD -> RoundedCornerShape(standard)
    }
}

@Composable
fun appButtonShape(standard: Dp = 14.dp): Shape {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.WINDOWS_PHONE -> RectangleShape
        AppThemeStyle.IOS_LIQUID_GLASS, AppThemeStyle.ONE_UI -> CircleShape
        AppThemeStyle.NOTHING_OS -> RoundedCornerShape(standard)
        AppThemeStyle.STANDARD -> RoundedCornerShape(standard)
    }
}

@Composable
fun appLiquidGlassContainerColor(
    defaultColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    isUrgent: Boolean = false
): Color {
    val isDark = LocalDarkThemeActive.current
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.IOS_LIQUID_GLASS -> {
            if (isUrgent) {
                if (isDark) Color(0xFF2C1414).copy(alpha = 0.84f) else Color(0xFFFFF2F1).copy(alpha = 0.88f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (isDark) 0.78f else 0.82f)
            }
        }
        else -> defaultColor
    }
}

@Composable
fun nothingCardBorder(
    isAlert: Boolean = false
): BorderStroke? {
    val isDark = LocalDarkThemeActive.current
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.NOTHING_OS -> BorderStroke(
            width = 1.dp,
            color = if (isAlert) NothingRed else NothingOutline
        )
        AppThemeStyle.IOS_LIQUID_GLASS -> BorderStroke(
            width = 1.dp,
            color = when {
                isAlert -> IosRed.copy(alpha = 0.55f)
                isDark -> Color.White.copy(alpha = 0.16f)
                else -> Color.White.copy(alpha = 0.70f)
            }
        )
        AppThemeStyle.WINDOWS_PHONE -> null
        AppThemeStyle.ONE_UI -> if (isAlert) {
            BorderStroke(
                width = onePixelDp(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            )
        } else null
        AppThemeStyle.STANDARD -> null
    }
}

@Composable
fun oneUiPillContainerColor(isError: Boolean = false): Color {
    val isDark = LocalDarkThemeActive.current
    return if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = if (isDark) 0.24f else 0.14f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.13f)
    }
}

@Composable
fun oneUiPillContentColor(isError: Boolean = false): Color {
    return if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
}

@Composable
fun OneUiPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val resolvedContainer = containerColor ?: oneUiPillContainerColor(isError = isError)
    val resolvedContent = contentColor ?: oneUiPillContentColor(isError = isError)
    Surface(
        shape = CircleShape,
        color = resolvedContainer,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = resolvedContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * One UI 8.5 Reachability Expanding Header placed in the upper viewing area
 * using a 34sp light-to-regular humanist display title and spring physics collapse.
 */
@Composable
fun OneUiExpandingHeader(
    title: String,
    subtitle: String? = null,
    isScrolled: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val collapseFraction by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = oneUiSpring(),
        label = "oneui_header_collapse"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 14.dp)
            .graphicsLayer {
                alpha = (1f - (collapseFraction * 0.90f)).coerceIn(0f, 1f)
                translationY = -collapseFraction * 22f
                val s = 1f - (collapseFraction * 0.06f)
                scaleX = s
                scaleY = s
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

/**
 * Windows Phone 8 / 8.1 lowercase Panorama section header (32sp light weight).
 */
@Composable
fun MetroPanoramaHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.lowercase(Locale("nl", "NL")),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle.uppercase(Locale("nl", "NL")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

/**
 * iOS (Liquid Glass) 28sp Apple Large Title header with smooth collapse on scroll.
 */
@Composable
fun IosLargeTitleHeader(
    title: String,
    subtitle: String? = null,
    isScrolled: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val collapseFraction by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = oneUiSpring(),
        label = "ios_header_collapse"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp)
            .graphicsLayer {
                alpha = (1f - (collapseFraction * 0.92f)).coerceIn(0f, 1f)
                translationY = -collapseFraction * 18f
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun NothingAwareDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    insetStart: Dp = 0.dp
) {
    when (LocalAppThemeStyle.current) {
        AppThemeStyle.NOTHING_OS -> NothingDottedDivider(modifier = modifier, color = NothingOutline)
        AppThemeStyle.ONE_UI -> {
            val hairlineColor = if (LocalDarkThemeActive.current) OneUiDividerDark else OneUiDividerLight
            HorizontalDivider(
                modifier = modifier.padding(start = insetStart),
                thickness = onePixelDp(),
                color = hairlineColor
            )
        }
        AppThemeStyle.IOS_LIQUID_GLASS -> {
            val dividerColor = if (LocalDarkThemeActive.current) IosDarkDivider else IosLightDivider
            HorizontalDivider(
                modifier = modifier.padding(start = insetStart),
                thickness = 0.5.dp,
                color = dividerColor
            )
        }
        AppThemeStyle.WINDOWS_PHONE -> {
            HorizontalDivider(
                modifier = modifier,
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f)
            )
        }
        AppThemeStyle.STANDARD -> HorizontalDivider(modifier = modifier, color = color)
    }
}

/**
 * Replaces solid lines with Nothing OS circular dot-matrix lines (• • • • •)
 */
@Composable
fun NothingDottedDivider(
    modifier: Modifier = Modifier,
    color: Color = NothingOutline,
    dotRadius: Dp = 1.25.dp,
    dotSpacing: Dp = 6.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val radiusPx = dotRadius.toPx()
        val spacingPx = dotSpacing.toPx().coerceAtLeast(radiusPx * 3f)
        val centerY = size.height / 2f
        var x = radiusPx
        while (x <= size.width - radiusPx) {
            drawCircle(
                color = color,
                radius = radiusPx,
                center = Offset(x, centerY)
            )
            x += spacingPx
        }
    }
}

/**
 * Mechanical 5x3 dot-matrix animated LED spinner for refresh and loading states
 */
@Composable
fun NothingDotMatrixSpinner(
    modifier: Modifier = Modifier,
    dotColor: Color = NothingWhite,
    accentColor: Color = NothingRed
) {
    val transition = rememberInfiniteTransition(label = "nothing_dot_spinner")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nothing_dot_phase"
    )

    Canvas(modifier = modifier.size(22.dp)) {
        val cols = 5
        val rows = 3
        val stepX = size.width / cols
        val stepY = size.height / (rows + 1)
        val baseRadius = (stepX.coerceAtMost(stepY) * 0.30f).coerceAtLeast(1.4f)

        for (c in 0 until cols) {
            val dist = abs(phase - c)
            val wrappedDist = minOf(dist, abs(phase - (c + cols)), abs(phase - (c - cols)))
            val isPeak = wrappedDist < 0.7f
            val isNear = wrappedDist < 1.5f
            for (r in 0 until rows) {
                val cx = stepX * (c + 0.5f)
                val cy = stepY * (r + 1f)
                val color = when {
                    isPeak && r == 1 -> accentColor
                    isPeak -> dotColor
                    isNear -> dotColor.copy(alpha = 0.55f)
                    else -> NothingOutline
                }
                val radius = if (isPeak) baseRadius * 1.15f else baseRadius * 0.85f
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

/**
 * Concentric circular hardware gauge for Nothing OS average grade display
 */
@Composable
fun NothingConcentricGradeRing(
    average: Double,
    modifier: Modifier = Modifier
) {
    val progress = (average / 10.0).toFloat().coerceIn(0f, 1f)
    val isPassing = average >= 5.5
    val activeColor = if (isPassing) NothingWhite else NothingRed

    Box(
        modifier = modifier.size(86.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(86.dp)) {
            val outerStroke = 4.dp.toPx()
            val innerStroke = 1.dp.toPx()
            // Outer mechanical track
            drawCircle(
                color = NothingOutline,
                radius = (size.minDimension - outerStroke) / 2f,
                style = Stroke(width = outerStroke)
            )
            // Inner concentric dial ring
            drawCircle(
                color = NothingOutline,
                radius = (size.minDimension - outerStroke) / 2f - 8.dp.toPx(),
                style = Stroke(width = innerStroke)
            )
            // Progress arc
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = String.format(java.util.Locale("nl", "NL"), "%.1f", average),
            fontFamily = DotMatrixFontFamily,
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.titleLarge,
            color = if (isPassing) NothingWhite else NothingRed
        )
    }
}

@Composable
fun NothingStatusDot(
    isUrgent: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(
            color = if (isUrgent) NothingRed else NothingAsh,
            radius = size.minDimension / 2f
        )
    }
}
