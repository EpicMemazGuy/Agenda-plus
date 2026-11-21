package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalDarkThemeActive
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// One UI 8.5 Motion Physics (Spring & Smooth Deceleration Curves)
// ============================================================================

val OneUiDecelerationEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

fun <T> oneUiSpring(
    dampingRatio: Float = 0.78f,
    stiffness: Float = Spring.StiffnessMediumLow
): SpringSpec<T> = spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness
)

fun <T> oneUiTween(durationMillis: Int = 340): TweenSpec<T> = tween(
    durationMillis = durationMillis,
    easing = OneUiDecelerationEasing
)

// ============================================================================
// One UI 8.5 Superellipse ("Squircle") Geometry
// ============================================================================

/**
 * Continuous-curvature superellipse ("squircle") corner shape matching Samsung One UI 8.5.
 * Eliminates harsh tangent/curvature jumps between straight edges and curved corners.
 */
@Immutable
class SquircleShape(
    val cornerRadius: Dp,
    private val exponent: Float = 3.4f,
    private val topOnly: Boolean = false
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        }
        val maxHalf = min(w, h) / 2f
        val requestedPx = with(density) { cornerRadius.toPx() }.coerceAtLeast(0f)
        // Superellipse transition extends smoothly ~1.25x past circular radius, clamped safely
        val ext = min(requestedPx * 1.25f, maxHalf)
        val path = buildSquirclePath(w = w, h = h, extTop = ext, extBottom = if (topOnly) 0f else ext, exponent = exponent)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return cornerRadius == other.cornerRadius &&
            exponent == other.exponent &&
            topOnly == other.topOnly
    }

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + exponent.hashCode()
        result = 31 * result + topOnly.hashCode()
        return result
    }
}

private fun buildSquirclePath(
    w: Float,
    h: Float,
    extTop: Float,
    extBottom: Float,
    exponent: Float
): Path {
    val path = Path()
    val power = 2f / exponent
    val steps = 10

    // Start at top edge after top-left corner
    path.moveTo(extTop, 0f)
    path.lineTo(w - extTop, 0f)

    // Top-right corner: theta 0 -> pi/2 from (w - extTop, extTop)
    if (extTop > 0.5f) {
        val cx = w - extTop
        val cy = extTop
        for (i in 1..steps) {
            val t = (Math.PI / 2.0) * (i.toDouble() / steps)
            val dx = sin(t).toFloat().pow(power) * extTop
            val dy = cos(t).toFloat().pow(power) * extTop
            path.lineTo(cx + dx, cy - dy)
        }
    } else {
        path.lineTo(w, 0f)
    }

    // Right edge
    path.lineTo(w, h - extBottom)

    // Bottom-right corner
    if (extBottom > 0.5f) {
        val cx = w - extBottom
        val cy = h - extBottom
        for (i in 1..steps) {
            val t = (Math.PI / 2.0) * (i.toDouble() / steps)
            val dx = cos(t).toFloat().pow(power) * extBottom
            val dy = sin(t).toFloat().pow(power) * extBottom
            path.lineTo(cx + dx, cy + dy)
        }
    } else {
        path.lineTo(w, h)
    }

    // Bottom edge
    path.lineTo(extBottom, h)

    // Bottom-left corner
    if (extBottom > 0.5f) {
        val cx = extBottom
        val cy = h - extBottom
        for (i in 1..steps) {
            val t = (Math.PI / 2.0) * (i.toDouble() / steps)
            val dx = sin(t).toFloat().pow(power) * extBottom
            val dy = cos(t).toFloat().pow(power) * extBottom
            path.lineTo(cx - dx, cy + dy)
        }
    } else {
        path.lineTo(0f, h)
    }

    // Left edge
    path.lineTo(0f, extTop)

    // Top-left corner
    if (extTop > 0.5f) {
        val cx = extTop
        val cy = extTop
        for (i in 1..steps) {
            val t = (Math.PI / 2.0) * (i.toDouble() / steps)
            val dx = cos(t).toFloat().pow(power) * extTop
            val dy = sin(t).toFloat().pow(power) * extTop
            path.lineTo(cx - dx, cy - dy)
        }
    } else {
        path.lineTo(0f, 0f)
    }

    path.close()
    return path
}

// ============================================================================
// Proportional Corner Scaling & 24dp Margin Rhythm Helpers
// ============================================================================

/**
 * Nested inner card shape: 14dp squircle in One UI 8.5 (concentric with 26dp outer card),
 * 14dp RoundedCornerShape otherwise.
 */
@Composable
fun appInnerCardShape(
    standard: Dp = 14.dp,
    nothing: Dp = 18.dp
): Shape {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> SquircleShape(14.dp)
        AppThemeStyle.IOS_LIQUID_GLASS -> SquircleShape(14.dp)
        AppThemeStyle.WINDOWS_PHONE -> RectangleShape
        AppThemeStyle.NOTHING_OS -> RoundedCornerShape(nothing)
        AppThemeStyle.STANDARD -> RoundedCornerShape(standard)
    }
}

/**
 * Thumbnail / icon container shape: 9dp squircle in One UI 8.5, 10dp squircle in iOS,
 * 0dp sharp corners in Windows Phone, 10dp RoundedCornerShape otherwise.
 */
@Composable
fun appThumbnailShape(
    standard: Dp = 10.dp,
    nothing: Dp = 10.dp
): Shape {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> SquircleShape(9.dp)
        AppThemeStyle.IOS_LIQUID_GLASS -> SquircleShape(10.dp)
        AppThemeStyle.WINDOWS_PHONE -> RectangleShape
        AppThemeStyle.NOTHING_OS -> RoundedCornerShape(nothing)
        AppThemeStyle.STANDARD -> RoundedCornerShape(standard)
    }
}

/**
 * Bottom sheet top-rounded shape.
 */
@Composable
fun appSheetShape(): Shape {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> SquircleShape(26.dp, topOnly = true)
        AppThemeStyle.IOS_LIQUID_GLASS -> SquircleShape(24.dp, topOnly = true)
        AppThemeStyle.WINDOWS_PHONE -> RectangleShape
        AppThemeStyle.NOTHING_OS -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        AppThemeStyle.STANDARD -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    }
}

@Composable
fun appSheetContainerColor(): Color {
    val isDark = LocalDarkThemeActive.current
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.94f else 0.96f)
        AppThemeStyle.IOS_LIQUID_GLASS -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (isDark) 0.90f else 0.94f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
}

/**
 * Enforces horizontal outer margin per theme.
 */
@Composable
fun appScreenHorizontalPadding(standard: Dp = 16.dp): Dp {
    return when (LocalAppThemeStyle.current) {
        AppThemeStyle.ONE_UI -> 24.dp
        AppThemeStyle.IOS_LIQUID_GLASS -> 18.dp
        else -> standard
    }
}

/**
 * Exact 1px thickness for ultra-thin hairline dividers and frosted inner-edge highlights.
 */
@Composable
fun onePixelDp(): Dp {
    val density = LocalDensity.current.density.coerceAtLeast(1f)
    return (1f / density).dp
}

// ============================================================================
// One UI 8.5 Frosted Glass Backdrop Blur & Inner-Edge Highlight
// ============================================================================

@Immutable
data class OneUiBackdropState(
    val graphicsLayer: GraphicsLayer? = null,
    val contentOriginInRoot: Offset = Offset.Zero
)

val LocalOneUiBackdropState = staticCompositionLocalOf { OneUiBackdropState() }
val LocalOneUiScreenInsets = staticCompositionLocalOf { PaddingValues(0.dp) }

@Composable
fun oneUiInnerHighlightBorder(isAlert: Boolean = false): BorderStroke {
    val isDark = LocalDarkThemeActive.current
    val color = when {
        isAlert -> MaterialTheme.colorScheme.error.copy(alpha = 0.48f)
        isDark -> Color.White.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.65f)
    }
    return BorderStroke(width = onePixelDp(), color = color)
}

/**
 * Frosted glass container for One UI 8.5 floating layers (pinned top bar, floating bottom nav,
 * menus, and floating cards): combines real-time API 31+ Gaussian backdrop blur (when a sibling
 * GraphicsLayer backdrop is active), translucent surface tint, zero drop shadows, and a crisp
 * 1px inner-edge highlight.
 */
@Composable
fun OneUiFrostedSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(
        alpha = if (LocalDarkThemeActive.current) 0.78f else 0.80f
    ),
    border: BorderStroke? = oneUiInnerHighlightBorder(),
    blurRadius: Dp = 24.dp,
    enableBackdropBlur: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalOneUiBackdropState.current
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val canBlurBackdrop = enableBackdropBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        backdrop.graphicsLayer != null

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                overlayOriginInRoot = coords.positionInRoot()
            }
            .clip(shape)
    ) {
        if (canBlurBackdrop && backdrop.graphicsLayer != null) {
            val layer = backdrop.graphicsLayer
            val dx = backdrop.contentOriginInRoot.x - overlayOriginInRoot.x
            val dy = backdrop.contentOriginInRoot.y - overlayOriginInRoot.y
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            ) {
                if (layer.size.width > 0 && layer.size.height > 0) {
                    translate(left = dx, top = dy) {
                        drawLayer(layer)
                    }
                }
            }
        }

        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.matchParentSize()
        ) {}

        Box(modifier = Modifier, content = content)
    }
}
