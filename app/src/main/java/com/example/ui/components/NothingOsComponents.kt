package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.NothingAsh
import com.example.ui.theme.NothingOutline
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import kotlin.math.abs

val isNothingOs: Boolean
    @Composable get() = LocalAppThemeStyle.current == AppThemeStyle.NOTHING_OS

@Composable
fun nothingCardBorder(
    isAlert: Boolean = false
): BorderStroke? {
    return if (isNothingOs) {
        BorderStroke(
            width = 1.dp,
            color = if (isAlert) NothingRed else NothingOutline
        )
    } else {
        null
    }
}

@Composable
fun NothingAwareDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    if (isNothingOs) {
        NothingDottedDivider(modifier = modifier, color = NothingOutline)
    } else {
        HorizontalDivider(modifier = modifier, color = color)
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
