package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.AppThemeStyle
import com.example.model.Appointment
import com.example.model.AxolaBackgroundStyle
import com.example.model.AxolaBorderStyle
import com.example.model.AxolaCustomTheme
import com.example.model.AxolaTypographyStyle
import com.example.model.GradeItem
import com.example.model.HomeworkTask
import com.example.model.MetroAccent
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.TaskCategory
import com.example.model.cleanSchoolText
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class DayStoryMode(val labelDutch: String) {
    TODAY_SCHEDULE("Mijn Dag"),
    EXAM_WEEK("Proefwerkweek")
}

enum class RoosterExportScope(val labelDutch: String, val dayCount: Int) {
    DAY("Vandaag (Dag)", 1),
    THREE_DAYS("3-Dagen", 3),
    WEEK("Volledige Week (Ma - Vr)", 5)
}

enum class RoosterExportAspect(
    val labelDutch: String,
    val widthPx: Int,
    val heightPx: Int,
    val isLockscreen: Boolean
) {
    LOCKSCREEN_9_195("Lockscreen Wallpaper (9:19.5)", 1080, 2340, true),
    COMPACT_4_5("Compacte Overzichtskaart (4:5)", 1080, 1350, false),
    WIDE_16_9("Brede Overzichtskaart (16:9)", 1920, 1080, false)
}

data class ExportThemePalette(
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val cardColor: Int,
    val elevatedCardColor: Int,
    val primaryAccent: Int,
    val secondaryAccent: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val borderColor: Int?,
    val borderWidthPx: Float,
    val cornerRadiusPx: Float,
    val isDark: Boolean,
    val drawDotMatrixGrid: Boolean,
    val lowercaseHeaders: Boolean,
    val uppercaseBadges: Boolean,
    val typefaceFamily: Typeface
)

object StoryAndScheduleExporter {

    private fun exportsDir(context: Context): File {
        return File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
    }

    fun resolvePalette(
        themeStyle: AppThemeStyle,
        axolaTheme: AxolaCustomTheme = AxolaCustomTheme(),
        metroAccent: MetroAccent = MetroAccent.COBALT,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): ExportThemePalette {
        return when (themeStyle) {
            AppThemeStyle.NOTHING_OS -> ExportThemePalette(
                backgroundTop = Color.BLACK,
                backgroundBottom = Color.BLACK,
                cardColor = Color.rgb(20, 20, 22),
                elevatedCardColor = Color.rgb(32, 32, 36),
                primaryAccent = Color.rgb(215, 25, 32),
                secondaryAccent = Color.WHITE,
                primaryText = Color.WHITE,
                secondaryText = Color.rgb(168, 168, 176),
                borderColor = Color.rgb(58, 58, 64),
                borderWidthPx = 2.5f,
                cornerRadiusPx = 44f,
                isDark = true,
                drawDotMatrixGrid = true,
                lowercaseHeaders = false,
                uppercaseBadges = true,
                typefaceFamily = Typeface.MONOSPACE
            )
            AppThemeStyle.ONE_UI -> {
                val light = !isDarkMode
                val accent = dynamicPrimaryArgb ?: if (light) Color.rgb(3, 129, 254) else Color.rgb(62, 145, 255)
                ExportThemePalette(
                    backgroundTop = if (light) Color.rgb(245, 246, 250) else Color.BLACK,
                    backgroundBottom = if (light) Color.rgb(238, 241, 248) else Color.rgb(10, 12, 16),
                    cardColor = if (light) Color.WHITE else Color.rgb(23, 24, 28),
                    elevatedCardColor = if (light) Color.rgb(240, 243, 249) else Color.rgb(34, 36, 42),
                    primaryAccent = accent,
                    secondaryAccent = Color.rgb(102, 209, 255),
                    primaryText = if (light) Color.rgb(16, 19, 24) else Color.WHITE,
                    secondaryText = if (light) Color.rgb(92, 99, 114) else Color.rgb(174, 178, 190),
                    borderColor = if (light) Color.argb(20, 0, 0, 0) else Color.argb(28, 255, 255, 255),
                    borderWidthPx = 2f,
                    cornerRadiusPx = 56f,
                    isDark = !light,
                    drawDotMatrixGrid = false,
                    lowercaseHeaders = false,
                    uppercaseBadges = false,
                    typefaceFamily = Typeface.DEFAULT
                )
            }
            AppThemeStyle.WINDOWS_PHONE -> {
                val light = !isDarkMode
                val accent = metroAccent.colorHex.toInt()
                ExportThemePalette(
                    backgroundTop = if (light) Color.WHITE else Color.BLACK,
                    backgroundBottom = if (light) Color.WHITE else Color.BLACK,
                    cardColor = if (light) Color.rgb(240, 240, 240) else Color.rgb(29, 29, 29),
                    elevatedCardColor = accent,
                    primaryAccent = accent,
                    secondaryAccent = Color.rgb(0, 138, 0),
                    primaryText = if (light) Color.rgb(17, 17, 17) else Color.WHITE,
                    secondaryText = if (light) Color.rgb(88, 88, 88) else Color.rgb(185, 185, 185),
                    borderColor = null,
                    borderWidthPx = 0f,
                    cornerRadiusPx = 0f,
                    isDark = !light,
                    drawDotMatrixGrid = false,
                    lowercaseHeaders = true,
                    uppercaseBadges = true,
                    typefaceFamily = Typeface.SANS_SERIF
                )
            }
            AppThemeStyle.IOS_LIQUID_GLASS -> {
                val light = !isDarkMode
                ExportThemePalette(
                    backgroundTop = if (light) Color.rgb(242, 242, 247) else Color.rgb(11, 16, 30),
                    backgroundBottom = if (light) Color.rgb(227, 236, 250) else Color.rgb(18, 26, 46),
                    cardColor = if (light) Color.argb(235, 255, 255, 255) else Color.argb(215, 28, 30, 38),
                    elevatedCardColor = if (light) Color.rgb(235, 242, 255) else Color.rgb(44, 46, 56),
                    primaryAccent = if (light) Color.rgb(0, 122, 255) else Color.rgb(10, 132, 255),
                    secondaryAccent = Color.rgb(255, 159, 10),
                    primaryText = if (light) Color.rgb(16, 18, 24) else Color.WHITE,
                    secondaryText = if (light) Color.rgb(99, 99, 102) else Color.rgb(174, 174, 178),
                    borderColor = if (light) Color.argb(190, 255, 255, 255) else Color.argb(46, 255, 255, 255),
                    borderWidthPx = 2.5f,
                    cornerRadiusPx = 46f,
                    isDark = !light,
                    drawDotMatrixGrid = false,
                    lowercaseHeaders = false,
                    uppercaseBadges = false,
                    typefaceFamily = Typeface.SANS_SERIF
                )
            }
            AppThemeStyle.AXOLA_CUSTOM -> {
                val bg = axolaTheme.backgroundStyle
                val isDark = bg.isDark
                val primary = axolaTheme.primaryColorArgb.toInt()
                val secondary = axolaTheme.secondaryColorArgb.toInt()
                val borderClr = when (axolaTheme.borderStyle) {
                    AxolaBorderStyle.NONE -> null
                    AxolaBorderStyle.SUBTLE_1DP -> if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(45, 0, 0, 0)
                    AxolaBorderStyle.NEON_GLOW -> primary
                }
                val tf = when (axolaTheme.typographyStyle) {
                    AxolaTypographyStyle.NOTHING_DOT_MATRIX, AxolaTypographyStyle.TERMINAL_MONO -> Typeface.MONOSPACE
                    AxolaTypographyStyle.MODERN_SANS, AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE -> Typeface.SANS_SERIF
                }
                ExportThemePalette(
                    backgroundTop = bg.canvasArgb.toInt(),
                    backgroundBottom = if (bg == AxolaBackgroundStyle.DEEP_SLATE) Color.rgb(19, 28, 48) else bg.canvasArgb.toInt(),
                    cardColor = bg.cardArgb.toInt(),
                    elevatedCardColor = bg.elevatedArgb.toInt(),
                    primaryAccent = primary,
                    secondaryAccent = secondary,
                    primaryText = if (isDark) Color.WHITE else Color.rgb(16, 19, 24),
                    secondaryText = if (isDark) Color.rgb(170, 178, 194) else Color.rgb(86, 96, 112),
                    borderColor = borderClr,
                    borderWidthPx = if (axolaTheme.borderStyle == AxolaBorderStyle.NEON_GLOW) 3.5f else 2f,
                    cornerRadiusPx = (axolaTheme.cornerRadiusDp * 2.1f).coerceIn(0f, 60f),
                    isDark = isDark,
                    drawDotMatrixGrid = axolaTheme.typographyStyle == AxolaTypographyStyle.NOTHING_DOT_MATRIX,
                    lowercaseHeaders = axolaTheme.typographyStyle == AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE,
                    uppercaseBadges = axolaTheme.typographyStyle == AxolaTypographyStyle.NOTHING_DOT_MATRIX ||
                        axolaTheme.typographyStyle == AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE,
                    typefaceFamily = tf
                )
            }
            AppThemeStyle.STANDARD -> {
                val light = !isDarkMode
                val accent = dynamicPrimaryArgb ?: if (light) Color.rgb(11, 37, 69) else Color.rgb(96, 165, 250)
                ExportThemePalette(
                    backgroundTop = if (light) Color.rgb(245, 247, 252) else Color.rgb(15, 23, 42),
                    backgroundBottom = if (light) Color.rgb(232, 238, 248) else Color.rgb(24, 34, 62),
                    cardColor = if (light) Color.WHITE else Color.rgb(26, 37, 68),
                    elevatedCardColor = if (light) Color.rgb(238, 242, 250) else Color.rgb(36, 51, 90),
                    primaryAccent = accent,
                    secondaryAccent = Color.rgb(232, 93, 4),
                    primaryText = if (light) Color.rgb(16, 20, 30) else Color.WHITE,
                    secondaryText = if (light) Color.rgb(84, 94, 112) else Color.rgb(180, 192, 214),
                    borderColor = if (light) Color.argb(26, 11, 37, 69) else Color.argb(65, 147, 197, 253),
                    borderWidthPx = 2f,
                    cornerRadiusPx = 38f,
                    isDark = !light,
                    drawDotMatrixGrid = false,
                    lowercaseHeaders = false,
                    uppercaseBadges = false,
                    typefaceFamily = Typeface.DEFAULT
                )
            }
        }
    }

    private fun drawCanvasBackground(canvas: Canvas, width: Int, height: Int, palette: ExportThemePalette) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (palette.backgroundTop == palette.backgroundBottom) {
            bgPaint.color = palette.backgroundTop
        } else {
            bgPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(palette.backgroundTop, palette.backgroundBottom),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (palette.drawDotMatrixGrid) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (palette.isDark) Color.argb(30, 255, 255, 255) else Color.argb(26, 0, 0, 0)
            }
            var gx = 36f
            while (gx < width) {
                var gy = 36f
                while (gy < height) {
                    canvas.drawCircle(gx, gy, 2.2f, dotPaint)
                    gy += 36f
                }
                gx += 36f
            }
        }
    }

    fun renderDayOrExamStoryPng(
        context: Context,
        appointments: List<Appointment>,
        tasks: List<HomeworkTask>,
        targetDateIso: String,
        mode: DayStoryMode,
        hideTeacherAndRoom: Boolean,
        studentName: String,
        customizationState: SubjectCustomizationState,
        themeStyle: AppThemeStyle,
        axolaTheme: AxolaCustomTheme = AxolaCustomTheme(),
        metroAccent: MetroAccent = MetroAccent.COBALT,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = resolvePalette(themeStyle, axolaTheme, metroAccent, isDarkMode, dynamicPrimaryArgb)

        drawCanvasBackground(canvas, width, height, palette)

        // Subtle decorative accent glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.85f, height * 0.12f, 520f,
                intArrayOf(withAlpha(palette.primaryAccent, 52), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.85f, height * 0.12f, 520f, glowPaint)

        val margin = 56f
        val outerCard = RectF(margin, 88f, width - margin, height - 120f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.cardColor }
        canvas.drawRoundRect(outerCard, palette.cornerRadiusPx, palette.cornerRadiusPx, cardPaint)
        palette.borderColor?.let { bColor ->
            canvas.drawRoundRect(
                outerCard,
                palette.cornerRadiusPx,
                palette.cornerRadiusPx,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = palette.borderWidthPx
                    color = bColor
                }
            )
        }

        val dayLessons = appointments
            .filter { it.dateIso == targetDateIso }
            .sortedWith(compareBy({ it.startTime }, { it.periodIndex }))

        val activeLessons = dayLessons.filter { !it.isCanceled }
        val sortedPeriods = activeLessons.map { it.periodIndex }.distinct().sorted()
        val gapCount = if (sortedPeriods.size >= 2) {
            var gaps = 0
            for (i in 0 until sortedPeriods.size - 1) {
                gaps += (sortedPeriods[i + 1] - sortedPeriods[i] - 1).coerceAtLeast(0)
            }
            gaps
        } else 0

        val upcomingExams = buildExamEntries(appointments, tasks, targetDateIso)
        val effectiveExamMode = mode == DayStoryMode.EXAM_WEEK || upcomingExams.size >= 2

        val moodBadgeText = when {
            mode == DayStoryMode.EXAM_WEEK -> "PROEFWERKWEEK OVERLEVEN"
            upcomingExams.size >= 2 -> "PROEFWERKWEEK OVERLEVEN • ${upcomingExams.size} TOETSEN"
            gapCount >= 1 -> "CHILL DAG ($gapCount ${if (gapCount == 1) "TUSSENUUR" else "TUSSENUREN"})"
            activeLessons.isEmpty() -> "VRIJE DAG • OPLADEN"
            else -> "VOLLE FOCUSDAG • ${activeLessons.size} LESSEN"
        }

        val innerPad = 48f
        val leftX = outerCard.left + innerPad
        val rightX = outerCard.right - innerPad

        // Top tag + Date
        val badgeRect = RectF(leftX, outerCard.top + 48f, leftX + 290f, outerCard.top + 106f)
        val badgeRadius = if (palette.cornerRadiusPx == 0f) 0f else 29f
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryAccent
        })
        canvas.drawText(
            "AGENDA+ STORY",
            badgeRect.centerX(),
            badgeRect.centerY() + 10f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = contrastForeground(palette.primaryAccent)
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        val dateLabel = formatDutchDate(targetDateIso)
        canvas.drawText(
            dateLabel,
            rightX,
            outerCard.top + 88f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 28f
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(palette.typefaceFamily, Typeface.NORMAL)
            }
        )

        // Headline
        val rawTitle = if (mode == DayStoryMode.EXAM_WEEK) "Proefwerkweek" else "Mijn Schooldag"
        val displayTitle = if (palette.lowercaseHeaders) rawTitle.lowercase(Locale("nl", "NL")) else rawTitle
        canvas.drawText(
            displayTitle,
            leftX,
            outerCard.top + 196f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = 62f
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        val cleanStudent = studentName.cleanSchoolText() ?: "Leerling"
        canvas.drawText(
            cleanStudent,
            leftX,
            outerCard.top + 250f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 30f
                typeface = Typeface.create(palette.typefaceFamily, Typeface.NORMAL)
            }
        )

        // Mood pill
        val moodRect = RectF(leftX, outerCard.top + 286f, rightX, outerCard.top + 356f)
        val moodBgColor = if (effectiveExamMode) Color.rgb(220, 38, 38) else palette.elevatedCardColor
        canvas.drawRoundRect(
            moodRect,
            if (palette.cornerRadiusPx == 0f) 0f else 24f,
            if (palette.cornerRadiusPx == 0f) 0f else 24f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(moodBgColor, if (effectiveExamMode) 48 else 255) }
        )
        canvas.drawText(
            moodBadgeText,
            moodRect.centerX(),
            moodRect.centerY() + 10f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (effectiveExamMode) Color.rgb(248, 113, 113) else palette.primaryAccent
                textSize = 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        var curY = moodRect.bottom + 36f
        val maxContentBottom = outerCard.bottom - 140f

        if (mode == DayStoryMode.EXAM_WEEK) {
            val examsToDraw = upcomingExams.take(7)
            if (examsToDraw.isEmpty()) {
                drawStoryEmptyCard(
                    canvas = canvas,
                    rect = RectF(leftX, curY, rightX, curY + 260f),
                    title = "Geen aankomende toetsen",
                    subtitle = "Je hebt op dit moment geen toetsen of grote proefwerken gepland.",
                    palette = palette
                )
            } else {
                val rowH = 152f
                examsToDraw.forEach { exam ->
                    if (curY + rowH > maxContentBottom) return@forEach
                    val rowRect = RectF(leftX, curY, rightX, curY + rowH - 18f)
                    val resolved = SubjectCustomizationCatalog.resolveSubject(
                        rawSubject = exam.subject,
                        rawTitle = exam.title,
                        state = customizationState
                    )
                    val stripeColor = resolved.accentColorHex?.toInt() ?: palette.primaryAccent
                    drawStoryRowCard(canvas, rowRect, stripeColor, palette)

                    val countdownText = when {
                        exam.daysUntil <= 0 -> "VANDAAG"
                        exam.daysUntil == 1 -> "MORGEN"
                        else -> "OVER ${exam.daysUntil}D"
                    }
                    canvas.drawText(
                        ellipsizeText(resolved.displayName, 22),
                        rowRect.left + 38f,
                        rowRect.top + 52f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.primaryText
                            textSize = 34f
                            typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                        }
                    )
                    canvas.drawText(
                        ellipsizeText(exam.title, 34),
                        rowRect.left + 38f,
                        rowRect.top + 96f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.secondaryText
                            textSize = 24f
                        }
                    )

                    val cdRect = RectF(rowRect.right - 210f, rowRect.top + 28f, rowRect.right - 24f, rowRect.bottom - 28f)
                    val cdRad = if (palette.cornerRadiusPx == 0f) 0f else 18f
                    canvas.drawRoundRect(cdRect, cdRad, cdRad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = withAlpha(Color.rgb(220, 38, 38), 44)
                    })
                    canvas.drawText(
                        countdownText,
                        cdRect.centerX(),
                        cdRect.centerY() + 9f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(248, 113, 113)
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                        }
                    )

                    curY += rowH
                }
            }
        } else {
            val lessonsToDraw = dayLessons.take(9)
            if (lessonsToDraw.isEmpty()) {
                drawStoryEmptyCard(
                    canvas = canvas,
                    rect = RectF(leftX, curY, rightX, curY + 260f),
                    title = "Vrije dag!",
                    subtitle = "Geen lessen op $dateLabel.",
                    palette = palette
                )
            } else {
                val availableH = maxContentBottom - curY
                val rowStep = (availableH / lessonsToDraw.size.coerceAtLeast(1)).coerceIn(106f, 146f)
                lessonsToDraw.forEach { appt ->
                    if (curY + rowStep > maxContentBottom + 8f) return@forEach
                    val rowRect = RectF(leftX, curY, rightX, curY + rowStep - 14f)
                    val resolved = SubjectCustomizationCatalog.resolveSubject(
                        rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                        rawTitle = appt.title,
                        explicitCode = appt.subjectCode,
                        state = customizationState
                    )
                    val stripeColor = when {
                        appt.isCanceled -> Color.rgb(220, 38, 38)
                        appt.isTestOrExam -> Color.rgb(245, 158, 11)
                        else -> resolved.accentColorHex?.toInt() ?: palette.primaryAccent
                    }
                    drawStoryRowCard(canvas, rowRect, stripeColor, palette)

                    val periodText = "${appt.periodIndex}e"
                    canvas.drawText(
                        periodText,
                        rowRect.left + 44f,
                        rowRect.centerY() + 10f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stripeColor
                            textSize = 32f
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                        }
                    )

                    val subjLabel = if (appt.isCanceled) "[UITVAL] ${resolved.displayName}" else resolved.displayName
                    canvas.drawText(
                        ellipsizeText(subjLabel, 24),
                        rowRect.left + 104f,
                        rowRect.top + 48f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (appt.isCanceled) Color.rgb(248, 113, 113) else palette.primaryText
                            textSize = 32f
                            typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                        }
                    )

                    val metaText = buildString {
                        append("${appt.startTime} - ${appt.endTime}")
                        if (!hideTeacherAndRoom) {
                            appt.classroom.cleanSchoolText()?.let { append(" • Lok. $it") }
                            appt.teacherInitials.cleanSchoolText()?.let { append(" • $it") }
                        }
                    }
                    canvas.drawText(
                        ellipsizeText(metaText, 38),
                        rowRect.left + 104f,
                        rowRect.top + 88f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.secondaryText
                            textSize = 24f
                        }
                    )

                    if (appt.isTestOrExam && !appt.isCanceled) {
                        val badgeW = 112f
                        val bRect = RectF(rowRect.right - badgeW - 18f, rowRect.top + 22f, rowRect.right - 18f, rowRect.top + 64f)
                        canvas.drawRoundRect(
                            bRect,
                            if (palette.cornerRadiusPx == 0f) 0f else 12f,
                            if (palette.cornerRadiusPx == 0f) 0f else 12f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 158, 11) }
                        )
                        canvas.drawText(
                            "TOETS",
                            bRect.centerX(),
                            bRect.centerY() + 8f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                textSize = 20f
                                textAlign = Paint.Align.CENTER
                                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                            }
                        )
                    }

                    curY += rowStep
                }
            }
        }

        // Watermark
        drawWatermarkFooter(canvas, width, outerCard.bottom - 52f, palette)

        val outFile = File(exportsDir(context), "agendaplus_dag_story_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    fun renderGradeStoryPng(
        context: Context,
        grade: GradeItem,
        subjectAverage: Double?,
        overallAverage: Double?,
        studentName: String,
        customizationState: SubjectCustomizationState,
        themeStyle: AppThemeStyle,
        axolaTheme: AxolaCustomTheme = AxolaCustomTheme(),
        metroAccent: MetroAccent = MetroAccent.COBALT,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = resolvePalette(themeStyle, axolaTheme, metroAccent, isDarkMode, dynamicPrimaryArgb)

        drawCanvasBackground(canvas, width, height, palette)

        val resolved = SubjectCustomizationCatalog.resolveSubject(
            rawSubject = grade.subjectName.cleanSchoolText() ?: grade.subjectCode,
            explicitCode = grade.subjectCode,
            state = customizationState
        )
        val subjectColor = resolved.accentColorHex?.toInt() ?: palette.primaryAccent
        val isGold = grade.score >= 8.0
        val scoreAccent = when {
            isGold -> Color.rgb(245, 158, 11)
            grade.score >= 5.5 -> subjectColor
            else -> Color.rgb(220, 38, 38)
        }

        val rankBadge = when {
            grade.score >= 8.0 -> "LEGENDARISCH • OP KOERS"
            grade.score >= 6.5 -> "OP KOERS"
            grade.score >= 5.5 -> "VOLDOENDE"
            else -> "KNOKKEN!"
        }

        // Celebratory glow behind grade circle
        if (isGold || grade.score >= 6.5) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width / 2f, 760f, 440f,
                    intArrayOf(
                        withAlpha(scoreAccent, if (isGold) 115 else 60),
                        Color.TRANSPARENT
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(width / 2f, 760f, 440f, glowPaint)
        }

        val margin = 56f
        val outerCard = RectF(margin, 96f, width - margin, height - 120f)
        canvas.drawRoundRect(
            outerCard,
            palette.cornerRadiusPx,
            palette.cornerRadiusPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(palette.cardColor, 238) }
        )
        canvas.drawRoundRect(
            outerCard,
            palette.cornerRadiusPx,
            palette.cornerRadiusPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = if (isGold) 5f else (palette.borderWidthPx.coerceAtLeast(2f))
                color = if (isGold) Color.rgb(245, 158, 11) else (palette.borderColor ?: scoreAccent)
            }
        )

        val centerX = width / 2f
        val leftX = outerCard.left + 48f
        val rightX = outerCard.right - 48f

        // Header badge + date
        val pillRect = RectF(leftX, outerCard.top + 52f, leftX + 340f, outerCard.top + 114f)
        val pillRad = if (palette.cornerRadiusPx == 0f) 0f else 31f
        canvas.drawRoundRect(pillRect, pillRad, pillRad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scoreAccent
        })
        canvas.drawText(
            rankBadge,
            pillRect.centerX(),
            pillRect.centerY() + 9f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = contrastForeground(scoreAccent)
                textSize = 22f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        canvas.drawText(
            grade.date.ifBlank { "Nieuw cijfer" },
            rightX,
            outerCard.top + 92f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 28f
                textAlign = Paint.Align.RIGHT
            }
        )

        // Subject Pill
        val subjDisplay = if (palette.lowercaseHeaders) {
            resolved.displayName.lowercase(Locale("nl", "NL"))
        } else {
            resolved.displayName
        }
        canvas.drawText(
            ellipsizeText(subjDisplay, 22),
            centerX,
            outerCard.top + 240f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = 60f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
        resolved.originalCodeLabel?.let { codeLabel ->
            canvas.drawText(
                codeLabel,
                centerX,
                outerCard.top + 292f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = subjectColor
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                }
            )
        }

        // Giant Score Ring (with gold outer aura when >= 8.0)
        val circleY = 760f
        if (isGold) {
            canvas.drawCircle(
                centerX,
                circleY,
                275f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = withAlpha(Color.rgb(245, 158, 11), 130)
                }
            )
        }
        canvas.drawCircle(
            centerX,
            circleY,
            240f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(scoreAccent, 42) }
        )
        canvas.drawCircle(
            centerX,
            circleY,
            240f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = scoreAccent
            }
        )
        canvas.drawText(
            String.format(Locale("nl", "NL"), "%.1f", grade.score),
            centerX,
            circleY + 72f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isGold) Color.rgb(252, 211, 77) else scoreAccent
                textSize = 200f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        // Description + Weight
        canvas.drawText(
            ellipsizeText(grade.description.ifBlank { "Toetsresultaat" }, 34),
            centerX,
            1100f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = 40f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
        canvas.drawText(
            "Weging ${grade.weight}x • Periode ${grade.period}",
            centerX,
            1160f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
        )

        // Updated Averages Stats Row
        val statsTop = 1245f
        val statsBottom = 1435f
        val halfGap = 18f
        val leftBox = RectF(leftX, statsTop, centerX - halfGap, statsBottom)
        val rightBox = RectF(centerX + halfGap, statsTop, rightX, statsBottom)
        val boxRad = if (palette.cornerRadiusPx == 0f) 0f else 28f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.elevatedCardColor }
        canvas.drawRoundRect(leftBox, boxRad, boxRad, boxPaint)
        canvas.drawRoundRect(rightBox, boxRad, boxRad, boxPaint)

        val subjAvgStr = subjectAverage?.let { String.format(Locale("nl", "NL"), "%.1f", it) } ?: "--"
        val ovAvgStr = overallAverage?.let { String.format(Locale("nl", "NL"), "%.1f", it) } ?: "--"

        canvas.drawText(
            "Vakgemiddelde",
            leftBox.centerX(),
            leftBox.top + 56f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
        )
        canvas.drawText(
            subjAvgStr,
            leftBox.centerX(),
            leftBox.bottom - 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = 62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        canvas.drawText(
            "Algemeen gem.",
            rightBox.centerX(),
            rightBox.top + 56f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
        )
        canvas.drawText(
            ovAvgStr,
            rightBox.centerX(),
            rightBox.bottom - 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryAccent
                textSize = 62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )

        val cleanStudent = studentName.cleanSchoolText() ?: "Leerling"
        canvas.drawText(
            cleanStudent,
            centerX,
            outerCard.bottom - 120f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
        )
        drawWatermarkFooter(canvas, width, outerCard.bottom - 52f, palette)

        val outFile = File(exportsDir(context), "agendaplus_cijfer_story_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    fun renderRoosterPng(
        context: Context,
        appointments: List<Appointment>,
        referenceDateIso: String,
        scope: RoosterExportScope,
        aspect: RoosterExportAspect,
        useSubjectCustomizations: Boolean,
        studentName: String,
        customizationState: SubjectCustomizationState,
        themeStyle: AppThemeStyle,
        axolaTheme: AxolaCustomTheme = AxolaCustomTheme(),
        metroAccent: MetroAccent = MetroAccent.COBALT,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): File {
        val width = aspect.widthPx
        val height = aspect.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = resolvePalette(themeStyle, axolaTheme, metroAccent, isDarkMode, dynamicPrimaryArgb)

        drawCanvasBackground(canvas, width, height, palette)

        val tz = TimeZone.getTimeZone("Europe/Amsterdam")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val shortDayFormat = SimpleDateFormat("EEE d MMM", Locale("nl", "NL")).apply { timeZone = tz }

        val anchorCal = Calendar.getInstance(tz).apply {
            firstDayOfWeek = Calendar.MONDAY
            runCatching { isoFormat.parse(referenceDateIso) }.getOrNull()?.let { time = it }
            if (scope == RoosterExportScope.WEEK) {
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            }
        }
        val weekNumber = anchorCal.get(Calendar.WEEK_OF_YEAR)
        val dayIsos = (0 until scope.dayCount).map { offset ->
            val c = anchorCal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            val iso = isoFormat.format(c.time)
            val label = shortDayFormat.format(c.time)
                .replace(".", "")
                .replaceFirstChar { it.titlecase(Locale("nl", "NL")) }
            iso to label
        }

        val topPadding = if (aspect.isLockscreen) 310f else 56f
        val sideMargin = 48f
        val bottomMargin = if (aspect.isLockscreen) 110f else 56f

        val headerTitle = if (palette.lowercaseHeaders) "rooster • week $weekNumber" else "Rooster • Week $weekNumber"
        val dateRangeText = if (dayIsos.size == 1) dayIsos.first().second else "${dayIsos.first().second} – ${dayIsos.last().second}"
        val cleanStudent = studentName.cleanSchoolText() ?: "Agenda+"

        canvas.drawText(
            headerTitle,
            sideMargin,
            topPadding + 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = if (aspect == RoosterExportAspect.WIDE_16_9) 46f else 52f
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
        canvas.drawText(
            "$dateRangeText • $cleanStudent",
            sideMargin,
            topPadding + 90f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 26f
            }
        )

        val contentTop = topPadding + 126f
        val contentBottom = height - bottomMargin - 46f
        val effectiveCustomState = if (useSubjectCustomizations) {
            customizationState
        } else {
            SubjectCustomizationState(customizations = emptyMap(), smartDefaultNamesEnabled = false)
        }

        val isMultiCol = (aspect == RoosterExportAspect.WIDE_16_9 && scope.dayCount > 1) ||
            (scope == RoosterExportScope.THREE_DAYS && aspect != RoosterExportAspect.LOCKSCREEN_9_195)

        if (isMultiCol) {
            val colGap = 16f
            val colCount = dayIsos.size.coerceAtLeast(1)
            val colWidth = (width - (sideMargin * 2) - (colGap * (colCount - 1))) / colCount

            dayIsos.forEachIndexed { idx, (iso, label) ->
                val colLeft = sideMargin + idx * (colWidth + colGap)
                val colRight = colLeft + colWidth
                val colRect = RectF(colLeft, contentTop, colRight, contentBottom)
                canvas.drawRoundRect(
                    colRect,
                    palette.cornerRadiusPx.coerceAtMost(32f),
                    palette.cornerRadiusPx.coerceAtMost(32f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.cardColor }
                )
                palette.borderColor?.let { bColor ->
                    canvas.drawRoundRect(
                        colRect,
                        palette.cornerRadiusPx.coerceAtMost(32f),
                        palette.cornerRadiusPx.coerceAtMost(32f),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = palette.borderWidthPx
                            color = bColor
                        }
                    )
                }

                canvas.drawText(
                    label,
                    colLeft + 20f,
                    contentTop + 42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.primaryAccent
                        textSize = 24f
                        typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                    }
                )

                val dayAppts = appointments
                    .filter { it.dateIso == iso }
                    .sortedWith(compareBy({ it.startTime }, { it.periodIndex }))
                    .take(9)

                var rowY = contentTop + 68f
                val availH = (contentBottom - rowY - 16f).coerceAtLeast(120f)
                val rowStep = (availH / dayAppts.size.coerceAtLeast(1)).coerceIn(70f, 118f)

                if (dayAppts.isEmpty()) {
                    canvas.drawText(
                        "Vrij",
                        colRect.centerX(),
                        rowY + 60f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.secondaryText
                            textSize = 22f
                            textAlign = Paint.Align.CENTER
                        }
                    )
                } else {
                    dayAppts.forEach { appt ->
                        if (rowY + rowStep > contentBottom - 8f) return@forEach
                        val rRect = RectF(colLeft + 14f, rowY, colRight - 14f, rowY + rowStep - 10f)
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                            rawTitle = appt.title,
                            explicitCode = appt.subjectCode,
                            state = effectiveCustomState
                        )
                        val accent = when {
                            appt.isCanceled -> Color.rgb(220, 38, 38)
                            appt.isTestOrExam -> Color.rgb(245, 158, 11)
                            useSubjectCustomizations && resolved.accentColorHex != null -> resolved.accentColorHex.toInt()
                            else -> palette.primaryAccent
                        }
                        drawScheduleChip(
                            canvas = canvas,
                            rect = rRect,
                            periodIndex = appt.periodIndex,
                            title = resolved.displayName,
                            time = appt.startTime,
                            room = appt.classroom.cleanSchoolText(),
                            isCanceled = appt.isCanceled,
                            isExam = appt.isTestOrExam,
                            accent = accent,
                            palette = palette,
                            compact = true
                        )
                        rowY += rowStep
                    }
                }
            }
        } else {
            // Vertical day sections (great for 9:19.5 Lockscreen, 1-day, or 5-day stacked card)
            val availH = (contentBottom - contentTop).coerceAtLeast(400f)
            val sectionGap = 16f
            val sectionH = ((availH - sectionGap * (dayIsos.size - 1)) / dayIsos.size.coerceAtLeast(1))

            dayIsos.forEachIndexed { idx, (iso, label) ->
                val secTop = contentTop + idx * (sectionH + sectionGap)
                val secBottom = secTop + sectionH
                val secRect = RectF(sideMargin, secTop, width - sideMargin, secBottom)
                canvas.drawRoundRect(
                    secRect,
                    palette.cornerRadiusPx.coerceAtMost(36f),
                    palette.cornerRadiusPx.coerceAtMost(36f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.cardColor }
                )
                palette.borderColor?.let { bColor ->
                    canvas.drawRoundRect(
                        secRect,
                        palette.cornerRadiusPx.coerceAtMost(36f),
                        palette.cornerRadiusPx.coerceAtMost(36f),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = palette.borderWidthPx
                            color = bColor
                        }
                    )
                }

                canvas.drawText(
                    label,
                    secRect.left + 28f,
                    secRect.top + 42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.primaryAccent
                        textSize = if (scope == RoosterExportScope.WEEK) 24f else 28f
                        typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
                    }
                )

                val dayAppts = appointments
                    .filter { it.dateIso == iso }
                    .sortedWith(compareBy({ it.startTime }, { it.periodIndex }))

                if (dayAppts.isEmpty()) {
                    canvas.drawText(
                        "Geen lessen",
                        secRect.left + 28f,
                        secRect.top + 92f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.secondaryText
                            textSize = 24f
                        }
                    )
                } else if (scope == RoosterExportScope.WEEK) {
                    // Two-column compact grid inside each day card for 5-day lockscreen readability
                    val maxRows = 4
                    val cols = 2
                    val cellGapX = 14f
                    val cellGapY = 10f
                    val innerLeft = secRect.left + 24f
                    val innerTop = secRect.top + 58f
                    val innerW = secRect.width() - 48f
                    val innerH = (secRect.height() - 74f).coerceAtLeast(80f)
                    val cellW = (innerW - cellGapX) / cols
                    val cellH = ((innerH - cellGapY * (maxRows - 1)) / maxRows).coerceIn(48f, 82f)

                    dayAppts.take(maxRows * cols).forEachIndexed { aIdx, appt ->
                        val c = aIdx / maxRows
                        val r = aIdx % maxRows
                        val x = innerLeft + c * (cellW + cellGapX)
                        val y = innerTop + r * (cellH + cellGapY)
                        val cRect = RectF(x, y, x + cellW, y + cellH)
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                            rawTitle = appt.title,
                            explicitCode = appt.subjectCode,
                            state = effectiveCustomState
                        )
                        val accent = when {
                            appt.isCanceled -> Color.rgb(220, 38, 38)
                            appt.isTestOrExam -> Color.rgb(245, 158, 11)
                            useSubjectCustomizations && resolved.accentColorHex != null -> resolved.accentColorHex.toInt()
                            else -> palette.primaryAccent
                        }
                        drawScheduleChip(
                            canvas = canvas,
                            rect = cRect,
                            periodIndex = appt.periodIndex,
                            title = resolved.displayName,
                            time = appt.startTime,
                            room = appt.classroom.cleanSchoolText(),
                            isCanceled = appt.isCanceled,
                            isExam = appt.isTestOrExam,
                            accent = accent,
                            palette = palette,
                            compact = true
                        )
                    }
                } else {
                    var rowY = secRect.top + 62f
                    val availDayH = (secRect.bottom - rowY - 16f).coerceAtLeast(100f)
                    val lessons = dayAppts.take(9)
                    val rowStep = (availDayH / lessons.size.coerceAtLeast(1)).coerceIn(66f, 128f)
                    lessons.forEach { appt ->
                        if (rowY + rowStep > secRect.bottom - 8f) return@forEach
                        val rRect = RectF(secRect.left + 24f, rowY, secRect.right - 24f, rowY + rowStep - 10f)
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                            rawTitle = appt.title,
                            explicitCode = appt.subjectCode,
                            state = effectiveCustomState
                        )
                        val accent = when {
                            appt.isCanceled -> Color.rgb(220, 38, 38)
                            appt.isTestOrExam -> Color.rgb(245, 158, 11)
                            useSubjectCustomizations && resolved.accentColorHex != null -> resolved.accentColorHex.toInt()
                            else -> palette.primaryAccent
                        }
                        drawScheduleChip(
                            canvas = canvas,
                            rect = rRect,
                            periodIndex = appt.periodIndex,
                            title = resolved.displayName,
                            time = "${appt.startTime}-${appt.endTime}",
                            room = appt.classroom.cleanSchoolText(),
                            isCanceled = appt.isCanceled,
                            isExam = appt.isTestOrExam,
                            accent = accent,
                            palette = palette,
                            compact = false
                        )
                        rowY += rowStep
                    }
                }
            }
        }

        drawWatermarkFooter(canvas, width, height - (bottomMargin * 0.45f), palette)

        val outFile = File(exportsDir(context), "agendaplus_rooster_${scope.name.lowercase()}_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    /**
     * Saves PNG to the Android Gallery using scoped MediaStore on API 29+.
     * Returns Result.success(null) on API < 29 so the UI can launch the zero-permission
     * SAF CreateDocument("image/png") picker instead of requesting legacy storage permissions.
     */
    fun savePngToGallery(
        context: Context,
        pngFile: File,
        displayName: String = pngFile.nameWithoutExtension
    ): Result<Uri?> = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@runCatching null
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Agenda+")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Kon geen afbeelding aanmaken in Galerij.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                pngFile.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Kon doelbestand in Galerij niet openen.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun drawScheduleChip(
        canvas: Canvas,
        rect: RectF,
        periodIndex: Int,
        title: String,
        time: String,
        room: String?,
        isCanceled: Boolean,
        isExam: Boolean,
        accent: Int,
        palette: ExportThemePalette,
        compact: Boolean
    ) {
        val rad = if (palette.cornerRadiusPx == 0f) 0f else if (compact) 14f else 20f
        canvas.drawRoundRect(rect, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.elevatedCardColor
        })
        val stripe = RectF(rect.left, rect.top, rect.left + (if (compact) 7f else 10f), rect.bottom)
        canvas.drawRoundRect(stripe, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })

        val textLeft = rect.left + (if (compact) 18f else 24f)
        val mainText = buildString {
            append("${periodIndex}e ")
            if (isCanceled) append("[UITVAL] ")
            else if (isExam) append("[TOETS] ")
            append(title)
        }
        canvas.drawText(
            ellipsizeText(mainText, if (compact) 18 else 26),
            textLeft,
            rect.centerY() - (if (compact) 2f else 4f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isCanceled) Color.rgb(248, 113, 113) else palette.primaryText
                textSize = if (compact) 20f else 26f
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
        val sub = buildString {
            append(time)
            if (!room.isNullOrBlank()) append(" • $room")
        }
        canvas.drawText(
            ellipsizeText(sub, if (compact) 22 else 34),
            textLeft,
            rect.centerY() + (if (compact) 20f else 24f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = if (compact) 16f else 20f
            }
        )
    }

    private fun drawStoryRowCard(canvas: Canvas, rect: RectF, stripeColor: Int, palette: ExportThemePalette) {
        val rad = if (palette.cornerRadiusPx == 0f) 0f else (palette.cornerRadiusPx * 0.65f)
        canvas.drawRoundRect(rect, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.elevatedCardColor
        })
        val stripe = RectF(rect.left, rect.top, rect.left + 14f, rect.bottom)
        canvas.drawRoundRect(stripe, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stripeColor })
    }

    private fun drawStoryEmptyCard(
        canvas: Canvas,
        rect: RectF,
        title: String,
        subtitle: String,
        palette: ExportThemePalette
    ) {
        val rad = if (palette.cornerRadiusPx == 0f) 0f else (palette.cornerRadiusPx * 0.65f)
        canvas.drawRoundRect(rect, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.elevatedCardColor
        })
        canvas.drawText(
            title,
            rect.centerX(),
            rect.centerY() - 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = 36f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
        canvas.drawText(
            ellipsizeText(subtitle, 46),
            rect.centerX(),
            rect.centerY() + 36f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
        )
    }

    private fun drawWatermarkFooter(canvas: Canvas, width: Int, baselineY: Float, palette: ExportThemePalette) {
        canvas.drawText(
            "Agenda+ • agendaplus.nl",
            width / 2f,
            baselineY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.primaryText, 145)
                textSize = 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(palette.typefaceFamily, Typeface.BOLD)
            }
        )
    }

    private data class ExamCountdownItem(
        val subject: String,
        val title: String,
        val dateIso: String,
        val daysUntil: Int
    )

    private fun buildExamEntries(
        appointments: List<Appointment>,
        tasks: List<HomeworkTask>,
        referenceDateIso: String
    ): List<ExamCountdownItem> {
        val tz = TimeZone.getTimeZone("Europe/Amsterdam")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val refMs = runCatching { isoFormat.parse(referenceDateIso)?.time }.getOrNull() ?: System.currentTimeMillis()
        val seen = linkedMapOf<String, ExamCountdownItem>()

        appointments
            .filter { !it.isCanceled && it.isTestOrExam && it.dateIso >= referenceDateIso }
            .sortedBy { it.dateIso }
            .forEach { appt ->
                val dateMs = runCatching { isoFormat.parse(appt.dateIso)?.time }.getOrNull() ?: refMs
                val days = ((dateMs - refMs) / 86_400_000L).toInt().coerceAtLeast(0)
                val key = "${appt.dateIso}|${appt.subjectCode.ifBlank { appt.subjectName }}"
                if (key !in seen) {
                    seen[key] = ExamCountdownItem(
                        subject = appt.subjectName.cleanSchoolText() ?: appt.title,
                        title = appt.homework.cleanSchoolText() ?: appt.title,
                        dateIso = appt.dateIso,
                        daysUntil = days
                    )
                }
            }

        tasks
            .filter {
                !it.isCompleted &&
                    (it.isTest || it.isPresentation) &&
                    it.dueDate >= referenceDateIso
            }
            .sortedBy { it.dueDate }
            .forEach { task ->
                val dateMs = runCatching { isoFormat.parse(task.dueDate)?.time }.getOrNull() ?: refMs
                val days = ((dateMs - refMs) / 86_400_000L).toInt().coerceAtLeast(0)
                val key = "${task.dueDate}|${task.subject}"
                if (key !in seen) {
                    seen[key] = ExamCountdownItem(
                        subject = task.subject,
                        title = task.title,
                        dateIso = task.dueDate,
                        daysUntil = days
                    )
                }
            }

        return seen.values.sortedBy { it.daysUntil }
    }

    private fun formatDutchDate(dateIso: String): String {
        val tz = TimeZone.getTimeZone("Europe/Amsterdam")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val outFormat = SimpleDateFormat("EEE d MMM yyyy", Locale("nl", "NL")).apply { timeZone = tz }
        return runCatching {
            isoFormat.parse(dateIso)?.let { outFormat.format(it) }
        }.getOrNull() ?: dateIso
    }

    private fun contrastForeground(bgColor: Int): Int {
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255.0
        return if (luminance > 0.62) Color.BLACK else Color.WHITE
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun ellipsizeText(text: String, maxChars: Int): String {
        return if (text.length <= maxChars) text else text.take((maxChars - 1).coerceAtLeast(1)) + "..."
    }
}
