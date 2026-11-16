package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Samsung One UI 8.5 system iconography:
 * - Built on a crisp 24dp x 24dp grid
 * - Uniform 1.75dp stroke weight
 * - Rounded line terminals (StrokeCap.Round) with crisp interior corners (StrokeJoin.Miter)
 * - Flat, front-facing monochromatic silhouettes (no 3D perspective or gradients)
 */
object OneUiIcons {

    private const val STROKE_WIDTH = 1.75f
    private val blackFill = SolidColor(Color.Black)

    val TodayOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiTodayOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                // Front-facing calendar frame with crisp corners
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
                // Header divider & calendar posts
                moveTo(4f, 10f)
                lineTo(20f, 10f)
                moveTo(8f, 3.5f)
                lineTo(8f, 6.5f)
                moveTo(16f, 3.5f)
                lineTo(16f, 6.5f)
                // Focus dot / block for Today
                moveTo(8.5f, 14f)
                lineTo(12.5f, 14f)
                lineTo(12.5f, 17f)
                lineTo(8.5f, 17f)
                close()
            }
        }.build()
    }

    val TodayFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiTodayFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Flat monochromatic calendar silhouette with carved cutout focus square (EvenOdd)
            path(
                fill = blackFill,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(3.5f, 6f)
                lineTo(20.5f, 6f)
                lineTo(20.5f, 20.5f)
                lineTo(3.5f, 20.5f)
                close()
                // Cutout header strip + Today square
                moveTo(5.5f, 8f)
                lineTo(5.5f, 10f)
                lineTo(18.5f, 10f)
                lineTo(18.5f, 8f)
                close()
                moveTo(8f, 13f)
                lineTo(8f, 17.5f)
                lineTo(12.5f, 17.5f)
                lineTo(12.5f, 13f)
                close()
            }
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(8f, 3.5f)
                lineTo(8f, 6f)
                moveTo(16f, 3.5f)
                lineTo(16f, 6f)
            }
        }.build()
    }

    val RoosterOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiRoosterOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(4f, 9.5f)
                lineTo(20f, 9.5f)
                moveTo(9.5f, 9.5f)
                lineTo(9.5f, 20f)
                moveTo(14.5f, 9.5f)
                lineTo(14.5f, 20f)
                moveTo(4f, 14.75f)
                lineTo(20f, 14.75f)
                moveTo(8f, 3f)
                lineTo(8f, 5.5f)
                moveTo(16f, 3f)
                lineTo(16f, 5.5f)
            }
        }.build()
    }

    val RoosterFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiRoosterFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = blackFill,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(3.5f, 5f)
                lineTo(20.5f, 5f)
                lineTo(20.5f, 20.5f)
                lineTo(3.5f, 20.5f)
                close()
                // Clean 3x2 schedule grid cutouts
                moveTo(5.5f, 10.5f)
                lineTo(5.5f, 13.75f)
                lineTo(9f, 13.75f)
                lineTo(9f, 10.5f)
                close()
                moveTo(10.5f, 10.5f)
                lineTo(10.5f, 13.75f)
                lineTo(13.75f, 13.75f)
                lineTo(13.75f, 10.5f)
                close()
                moveTo(15.25f, 10.5f)
                lineTo(15.25f, 13.75f)
                lineTo(18.5f, 13.75f)
                lineTo(18.5f, 10.5f)
                close()
                moveTo(5.5f, 15.25f)
                lineTo(5.5f, 18.5f)
                lineTo(9f, 18.5f)
                lineTo(9f, 15.25f)
                close()
                moveTo(10.5f, 15.25f)
                lineTo(10.5f, 18.5f)
                lineTo(13.75f, 18.5f)
                lineTo(13.75f, 15.25f)
                close()
            }
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(8f, 3f)
                lineTo(8f, 5.5f)
                moveTo(16f, 3f)
                lineTo(16f, 5.5f)
            }
        }.build()
    }

    val HuiswerkOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiHuiswerkOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                lineTo(19f, 20f)
                lineTo(5f, 20f)
                close()
                // Crisp checkmark + lines
                moveTo(8.5f, 10f)
                lineTo(10.25f, 11.75f)
                lineTo(13f, 8.5f)
                moveTo(15f, 10f)
                lineTo(16.5f, 10f)
                moveTo(8.5f, 15f)
                lineTo(16f, 15f)
            }
        }.build()
    }

    val HuiswerkFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiHuiswerkFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = blackFill,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(4.5f, 3.5f)
                lineTo(19.5f, 3.5f)
                lineTo(19.5f, 20.5f)
                lineTo(4.5f, 20.5f)
                close()
                moveTo(7.5f, 8.5f)
                lineTo(7.5f, 10.5f)
                lineTo(16.5f, 10.5f)
                lineTo(16.5f, 8.5f)
                close()
                moveTo(7.5f, 13.5f)
                lineTo(7.5f, 15.5f)
                lineTo(14.5f, 15.5f)
                lineTo(14.5f, 13.5f)
                close()
            }
        }.build()
    }

    val CijfersOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiCijfersOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(4f, 19.5f)
                lineTo(20f, 19.5f)
                moveTo(6.5f, 19.5f)
                lineTo(6.5f, 13f)
                lineTo(9.5f, 13f)
                lineTo(9.5f, 19.5f)
                moveTo(11f, 19.5f)
                lineTo(11f, 6f)
                lineTo(14f, 6f)
                lineTo(14f, 19.5f)
                moveTo(15.5f, 19.5f)
                lineTo(15.5f, 10f)
                lineTo(18.5f, 10f)
                lineTo(18.5f, 19.5f)
            }
        }.build()
    }

    val CijfersFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiCijfersFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = blackFill) {
                moveTo(5.5f, 12.5f)
                lineTo(9f, 12.5f)
                lineTo(9f, 20f)
                lineTo(5.5f, 20f)
                close()
                moveTo(10.25f, 5f)
                lineTo(13.75f, 5f)
                lineTo(13.75f, 20f)
                lineTo(10.25f, 20f)
                close()
                moveTo(15f, 9f)
                lineTo(18.5f, 9f)
                lineTo(18.5f, 20f)
                lineTo(15f, 20f)
                close()
            }
        }.build()
    }

    val MeerOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiMeerOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(4.5f, 4.5f); lineTo(10.5f, 4.5f); lineTo(10.5f, 10.5f); lineTo(4.5f, 10.5f); close()
                moveTo(13.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 10.5f); lineTo(13.5f, 10.5f); close()
                moveTo(4.5f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(13.5f, 13.5f); lineTo(19.5f, 13.5f); lineTo(19.5f, 19.5f); lineTo(13.5f, 19.5f); close()
            }
        }.build()
    }

    val MeerFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiMeerFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = blackFill) {
                moveTo(4f, 4f); lineTo(10.8f, 4f); lineTo(10.8f, 10.8f); lineTo(4f, 10.8f); close()
                moveTo(13.2f, 4f); lineTo(20f, 4f); lineTo(20f, 10.8f); lineTo(13.2f, 10.8f); close()
                moveTo(4f, 13.2f); lineTo(10.8f, 13.2f); lineTo(10.8f, 20f); lineTo(4f, 20f); close()
                moveTo(13.2f, 13.2f); lineTo(20f, 13.2f); lineTo(20f, 20f); lineTo(13.2f, 20f); close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiRefresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                // Smooth open arc + sharp miter arrowhead
                moveTo(19.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.3f, 6.7f)
                moveTo(19.5f, 4.5f)
                lineTo(19.5f, 8.25f)
                lineTo(15.75f, 8.25f)
            }
        }.build()
    }

    val Terminal: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiTerminal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(7.5f, 9.5f); lineTo(10.5f, 12f); lineTo(7.5f, 14.5f)
                moveTo(12.5f, 14.5f); lineTo(16.5f, 14.5f)
            }
        }.build()
    }

    val Account: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiAccount",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = blackFill,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                // Outer circle
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.99f, 3.5f)
                close()
                // Head
                moveTo(12f, 8f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.99f, 8f)
                close()
                // Shoulders
                moveTo(7.5f, 18f)
                quadTo(12f, 14.5f, 16.5f, 18f)
            }
        }.build()
    }

    val AgendaMark: ImageVector by lazy {
        ImageVector.Builder(
            name = "OneUiAgendaMark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Flat monochromatic calendar + crisp '+' mark for brand/app badge
            path(
                stroke = blackFill,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f); lineTo(19.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(4.5f, 10.5f); lineTo(19.5f, 10.5f)
                moveTo(8.5f, 4f); lineTo(8.5f, 6.5f)
                moveTo(15.5f, 4f); lineTo(15.5f, 6.5f)
                moveTo(12f, 12.75f); lineTo(12f, 17.25f)
                moveTo(9.75f, 15f); lineTo(14.25f, 15f)
            }
        }.build()
    }
}
