package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.AppThemeStyle
import com.example.model.GradeItem
import com.example.model.SubjectGradeSummary
import com.example.model.capitalizeSubjectName
import com.example.model.cleanSchoolText
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GradeExporter {

    private fun exportsDir(context: Context): File {
        return File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
    }

    fun renderGradeShareCardPng(
        context: Context,
        selectedGrades: List<GradeItem>,
        overallAverage: Double,
        studentName: String,
        appThemeStyle: AppThemeStyle,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): File {
        val items = selectedGrades.take(8)
        val width = 1080
        val rowHeight = 148
        val headerHeight = 320
        val footerHeight = 250
        val height = (headerHeight + (items.size.coerceAtLeast(1) * rowHeight) + footerHeight).coerceAtLeast(720)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS
        val isOneUi = appThemeStyle == AppThemeStyle.ONE_UI
        val oneUiLight = isOneUi && !isDarkMode
        val resolvedDynamicPrimary = dynamicPrimaryArgb ?: if (oneUiLight) Color.rgb(3, 129, 254) else Color.rgb(62, 145, 255)

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when {
            isNothing -> {
                bgPaint.color = Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // Subtle dot-matrix grid in background
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(32, 255, 255, 255)
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
            isOneUi -> {
                bgPaint.color = if (oneUiLight) Color.rgb(246, 246, 248) else Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            else -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.rgb(18, 25, 48),
                        Color.rgb(28, 39, 72),
                        Color.rgb(19, 26, 46)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isNothing -> Color.rgb(22, 22, 24)
                oneUiLight -> Color.WHITE
                isOneUi -> Color.rgb(23, 23, 27)
                else -> Color.argb(225, 29, 40, 74)
            }
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isNothing) 3f else 2f
            color = when {
                isNothing -> Color.rgb(62, 62, 68)
                oneUiLight -> Color.argb(18, 0, 0, 0)
                isOneUi -> Color.argb(24, 255, 255, 255)
                else -> Color.argb(90, 147, 197, 253)
            }
        }
        val accentColor = when {
            isNothing -> Color.rgb(215, 25, 32)
            isOneUi -> resolvedDynamicPrimary
            else -> Color.rgb(96, 165, 250)
        }
        val primaryTextColor = if (oneUiLight) Color.rgb(16, 19, 24) else Color.WHITE
        val secondaryTextColor = if (oneUiLight) Color.rgb(93, 100, 114) else Color.rgb(180, 188, 205)
        val passingColor = when {
            isNothing -> Color.rgb(245, 245, 247)
            oneUiLight -> resolvedDynamicPrimary
            isOneUi -> resolvedDynamicPrimary
            else -> Color.rgb(110, 231, 183)
        }
        val failingColor = when {
            isNothing -> Color.rgb(215, 25, 32)
            oneUiLight -> Color.rgb(211, 47, 47)
            isOneUi -> Color.rgb(255, 107, 107)
            else -> Color.rgb(248, 113, 113)
        }

        // Outer card frame
        val outerRadius = if (isOneUi) 54f else 48f
        val outerRect = RectF(48f, 48f, width - 48f, height - 48f)
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, cardBgPaint)
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, cardBorderPaint)

        // Accent pill
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        val pillRect = RectF(96f, 96f, 340f, 154f)
        canvas.drawRoundRect(pillRect, 29f, 29f, pillPaint)

        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOneUi && !oneUiLight) Color.BLACK else Color.WHITE
            textSize = 28f
            typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("AGENDA+", 126f, 135f, pillTextPaint)

        val dateLabel = SimpleDateFormat("d MMM yyyy", Locale("nl", "NL")).format(Date())
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateLabel, width - 96f, 135f, metaPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            if (isNothing) "CIJFERKAART" else "Mijn Cijfers",
            96f,
            232f,
            titlePaint
        )

        val cleanStudent = studentName.cleanSchoolText() ?: "Leerling"
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 30f
        }
        canvas.drawText(cleanStudent, 96f, 280f, subTitlePaint)

        // Grade rows
        var curY = headerHeight.toFloat()
        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isNothing -> Color.rgb(34, 34, 38)
                oneUiLight -> Color.rgb(241, 243, 247)
                isOneUi -> Color.rgb(32, 32, 38)
                else -> Color.argb(170, 17, 24, 48)
            }
        }
        val rowSubjectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowDescPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 25f
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.BOLD)
        }

        val rowRadius = if (isOneUi) 42f else 28f
        items.forEach { grade ->
            val rowRect = RectF(88f, curY, width - 88f, curY + rowHeight - 20f)
            canvas.drawRoundRect(rowRect, rowRadius, rowRadius, rowBgPaint)
            if (isNothing || isOneUi) {
                canvas.drawRoundRect(rowRect, rowRadius, rowRadius, cardBorderPaint)
            }

            val subjText = ellipsizeText(grade.subjectName.capitalizeSubjectName(), 22)
            val descText = ellipsizeText("${grade.description} • Weging ${grade.weight}x", 40)
            canvas.drawText(subjText, 120f, curY + 54f, rowSubjectPaint)
            canvas.drawText(descText, 120f, curY + 96f, rowDescPaint)

            val badgeRect = RectF(width - 250f, curY + 20f, width - 114f, curY + rowHeight - 40f)
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    isNothing -> if (grade.isPassing) Color.rgb(48, 48, 54) else Color.rgb(120, 18, 24)
                    isOneUi && grade.isPassing -> Color.argb(
                        38,
                        Color.red(resolvedDynamicPrimary),
                        Color.green(resolvedDynamicPrimary),
                        Color.blue(resolvedDynamicPrimary)
                    )
                    isOneUi -> Color.argb(38, 229, 57, 53)
                    else -> if (grade.isPassing) Color.argb(40, 16, 185, 129) else Color.argb(65, 220, 38, 38)
                }
            }
            val badgeRadius = if (isOneUi) 999f else 22f
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgePaint)

            scorePaint.color = if (grade.isPassing) passingColor else failingColor
            val scoreStr = String.format(Locale.US, "%.1f", grade.score)
            canvas.drawText(scoreStr, badgeRect.centerX(), badgeRect.centerY() + 17f, scorePaint)

            curY += rowHeight
        }

        // Bottom GPA + subtle agendaplus.nl footer
        val footerTop = height - footerHeight + 24f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (oneUiLight) Color.argb(32, 0, 0, 0) else Color.argb(90, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(96f, footerTop, width - 96f, footerTop, dividerPaint)

        val avgLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 30f
        }
        canvas.drawText("Algemeen gemiddelde", 96f, footerTop + 64f, avgLabelPaint)

        val avgValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (overallAverage >= 5.5) passingColor else failingColor
            textSize = 54f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(String.format(Locale.US, "%.1f", overallAverage), width - 96f, footerTop + 72f, avgValuePaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (oneUiLight) Color.argb(145, 16, 19, 24) else Color.argb(155, 255, 255, 255)
            textSize = 26f
            typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("agendaplus.nl", 96f, height - 88f, brandPaint)

        val outFile = File(exportsDir(context), "agendaplus_cijferkaart_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    fun renderSingleGradeUnboxingCardPng(
        context: Context,
        grade: GradeItem,
        studentName: String,
        appThemeStyle: AppThemeStyle,
        isDarkMode: Boolean = true,
        dynamicPrimaryArgb: Int? = null
    ): File {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS
        val isOneUi = appThemeStyle == AppThemeStyle.ONE_UI
        val oneUiLight = isOneUi && !isDarkMode
        val resolvedDynamicPrimary = dynamicPrimaryArgb ?: if (oneUiLight) Color.rgb(3, 129, 254) else Color.rgb(62, 145, 255)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when {
            isNothing -> {
                bgPaint.color = Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(36, 255, 255, 255) }
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
            isOneUi -> {
                bgPaint.color = if (oneUiLight) Color.rgb(246, 246, 248) else Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            else -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.rgb(16, 23, 46), Color.rgb(30, 42, 78), Color.rgb(18, 25, 48)),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }

        val rarityColor = when {
            grade.score >= 8.0 -> Color.rgb(245, 158, 11)
            grade.score >= 5.5 -> if (isOneUi) resolvedDynamicPrimary else Color.rgb(124, 58, 237)
            else -> Color.rgb(215, 25, 32)
        }
        val rarityLabel = when {
            grade.score >= 8.5 -> "LEGENDARISCH"
            grade.score >= 8.0 -> "GOUD"
            grade.score >= 6.8 -> "ZELDZAAM"
            grade.score >= 5.5 -> "VOLDOENDE"
            else -> "ONVOLDOENDE"
        }

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isNothing -> Color.rgb(20, 20, 22)
                oneUiLight -> Color.WHITE
                isOneUi -> Color.rgb(23, 23, 27)
                else -> Color.argb(235, 27, 37, 68)
            }
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = rarityColor
        }
        val outerRadius = if (isOneUi) 64f else 52f
        val outerRect = RectF(64f, 64f, width - 64f, height - 64f)
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, cardBgPaint)
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, cardBorderPaint)

        val primaryText = if (oneUiLight) Color.rgb(16, 19, 24) else Color.WHITE
        val secondaryText = if (oneUiLight) Color.rgb(93, 100, 114) else Color.rgb(185, 193, 210)

        // Rarity badge
        val badgeRect = RectF(112f, 128f, 460f, 196f)
        canvas.drawRoundRect(badgeRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rarityColor })
        canvas.drawText(
            rarityLabel,
            badgeRect.centerX(),
            badgeRect.centerY() + 11f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        val dateLabel = SimpleDateFormat("d MMM yyyy", Locale("nl", "NL")).format(Date())
        canvas.drawText(
            dateLabel,
            width - 112f,
            174f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = 28f
                textAlign = Paint.Align.RIGHT
            }
        )

        // Big score circle
        val centerX = width / 2f
        val centerY = 530f
        canvas.drawCircle(
            centerX,
            centerY,
            210f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(38, Color.red(rarityColor), Color.green(rarityColor), Color.blue(rarityColor))
            }
        )
        canvas.drawCircle(
            centerX,
            centerY,
            210f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                color = rarityColor
            }
        )
        canvas.drawText(
            String.format(Locale.US, "%.1f", grade.score),
            centerX,
            centerY + 62f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rarityColor
                textSize = 180f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(if (isNothing) Typeface.MONOSPACE else Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        // Subject + Description + Metadata
        val subjectTitle = ellipsizeText(grade.subjectName.capitalizeSubjectName(), 24)
        canvas.drawText(
            subjectTitle,
            centerX,
            840f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryText
                textSize = 58f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
        canvas.drawText(
            ellipsizeText(grade.description.ifBlank { "Toetsresultaat" }, 38),
            centerX,
            915f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
        )
        canvas.drawText(
            "Weging ${grade.weight}x • ${grade.date}",
            centerX,
            980f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryText
                textSize = 32f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        val cleanStudent = studentName.cleanSchoolText() ?: "Agenda+ Leerling"
        canvas.drawText(
            "$cleanStudent • agendaplus.nl",
            centerX,
            height - 128f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
        )

        val outFile = File(exportsDir(context), "agendaplus_unboxing_${grade.id.hashCode()}_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    fun generateCsvString(summaries: List<SubjectGradeSummary>): String {
        val sb = StringBuilder()
        sb.appendLine("Vak,Code,Vakgemiddelde,Omschrijving,Cijfer,Weging,Datum,Periode")
        for (summary in summaries) {
            val subjName = summary.subjectName.capitalizeSubjectName()
            if (summary.grades.isEmpty()) {
                sb.appendLine(
                    listOf(
                        csvEscape(subjName),
                        csvEscape(summary.subjectCode),
                        summary.weightedAverage.toString(),
                        "",
                        "",
                        "",
                        "",
                        ""
                    ).joinToString(",")
                )
            } else {
                for (grade in summary.grades) {
                    sb.appendLine(
                        listOf(
                            csvEscape(subjName),
                            csvEscape(summary.subjectCode),
                            summary.weightedAverage.toString(),
                            csvEscape(grade.description),
                            grade.score.toString(),
                            grade.weight.toString(),
                            csvEscape(grade.date),
                            grade.period.toString()
                        ).joinToString(",")
                    )
                }
            }
        }
        return sb.toString()
    }

    fun writeCsvFile(context: Context, summaries: List<SubjectGradeSummary>): File {
        val file = File(exportsDir(context), "agendaplus_cijfers_${System.currentTimeMillis()}.csv")
        file.writeText(generateCsvString(summaries), Charsets.UTF_8)
        return file
    }

    fun writePdfReport(
        outputStream: OutputStream,
        summaries: List<SubjectGradeSummary>,
        studentName: String,
        overallAverage: Double
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 45, 95)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 54f
        canvas.drawText("Agenda+ Cijferoverzicht", margin, y, titlePaint)
        y += 18f
        val dateStr = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("nl", "NL")).format(Date())
        val cleanName = studentName.cleanSchoolText() ?: "Leerling"
        canvas.drawText("$cleanName • Algemeen gemiddelde: $overallAverage • $dateStr", margin, y, subtitlePaint)
        y += 14f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 22f

        fun nextPageIfNeeded(neededHeight: Float) {
            if (y + neededHeight > pageHeight - 48f) {
                canvas.drawText("agendaplus.nl • Pagina $pageNumber", margin, pageHeight - 24f, subtitlePaint)
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
        }

        for (summary in summaries) {
            nextPageIfNeeded(44f)
            canvas.drawText(
                "${summary.subjectName} (${summary.subjectCode.uppercase()}) — Gemiddelde: ${summary.weightedAverage}",
                margin,
                y,
                sectionPaint
            )
            y += 16f

            for (grade in summary.grades) {
                nextPageIfNeeded(18f)
                val desc = ellipsizeText(grade.description, 44)
                canvas.drawText(grade.date, margin, y, bodyPaint)
                canvas.drawText(desc, margin + 76f, y, bodyPaint)
                canvas.drawText("${grade.weight}x", pageWidth - margin - 90f, y, bodyPaint)
                canvas.drawText(String.format(Locale.US, "%.1f", grade.score), pageWidth - margin - 32f, y, bodyPaint)
                y += 15f
            }
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 16f
        }

        canvas.drawText("agendaplus.nl • Pagina $pageNumber", margin, pageHeight - 24f, subtitlePaint)
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    fun writePdfFile(
        context: Context,
        summaries: List<SubjectGradeSummary>,
        studentName: String,
        overallAverage: Double
    ): File {
        val file = File(exportsDir(context), "agendaplus_cijfers_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { fos ->
            writePdfReport(fos, summaries, studentName, overallAverage)
        }
        return file
    }

    fun shareFileViaProvider(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun ellipsizeText(text: String, maxChars: Int): String {
        return if (text.length <= maxChars) text else text.take(maxChars - 1) + "..."
    }
}
