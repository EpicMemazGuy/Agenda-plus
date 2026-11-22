package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppPreferencesStore
import com.example.model.AppThemeStyle
import com.example.model.AxolaTypographyStyle
import com.example.model.GradeItem
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.cleanSchoolText
import java.util.Locale

class GradesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_GRADES"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GradesWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val store = AppPreferencesStore(context)
            val themeStyle = store.getAppThemeStyle()
            val metroAccent = store.getMetroAccent()
            val customState = SubjectCustomizationState(
                customizations = store.loadSubjectCustomizations(),
                smartDefaultNamesEnabled = store.isSmartSubjectNamesEnabled(),
                useSubjectColorsForMetroTiles = store.isUseSubjectColorsForMetroTiles()
            )
            val axolaTheme = store.getAxolaCustomTheme()
            val isAxola = themeStyle == AppThemeStyle.AXOLA_CUSTOM
            val isNothing = themeStyle == AppThemeStyle.NOTHING_OS ||
                (isAxola && axolaTheme.typographyStyle == AxolaTypographyStyle.NOTHING_DOT_MATRIX && axolaTheme.backgroundStyle.isDark)
            val isOneUi = themeStyle == AppThemeStyle.ONE_UI
            val isMetro = themeStyle == AppThemeStyle.WINDOWS_PHONE ||
                (isAxola && axolaTheme.cornerRadiusDp <= 4)
            val isIos = themeStyle == AppThemeStyle.IOS_LIQUID_GLASS
            val layoutId = when (themeStyle) {
                AppThemeStyle.NOTHING_OS -> R.layout.widget_grades_nothing
                AppThemeStyle.ONE_UI -> R.layout.widget_grades_oneui
                AppThemeStyle.WINDOWS_PHONE -> R.layout.widget_grades_metro
                AppThemeStyle.IOS_LIQUID_GLASS -> R.layout.widget_grades_ios
                AppThemeStyle.AXOLA_CUSTOM -> when {
                    isMetro -> R.layout.widget_grades_metro
                    isNothing -> R.layout.widget_grades_nothing
                    else -> R.layout.widget_grades
                }
                AppThemeStyle.STANDARD -> R.layout.widget_grades
            }
            val views = RemoteViews(context.packageName, layoutId)
            val session = store.loadSession()
            val summaries = store.loadGrades()

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetUpdater.EXTRA_OPEN_TAB, "CIJFERS")
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                201,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_grades_root, launchPendingIntent)

            val refreshIntent = Intent(context, GradesWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                202,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_grades_refresh_btn, refreshPendingIntent)

            if (isMetro) {
                views.setInt(R.id.widget_grades_badge, "setBackgroundColor", metroAccent.colorHex.toInt())
            }

            val rowIds = listOf(
                Triple(R.id.widget_grade_row_1, R.id.widget_grade_subject_1, R.id.widget_grade_score_1),
                Triple(R.id.widget_grade_row_2, R.id.widget_grade_subject_2, R.id.widget_grade_score_2),
                Triple(R.id.widget_grade_row_3, R.id.widget_grade_subject_3, R.id.widget_grade_score_3)
            )

            if (!session.isAuthenticated) {
                views.setTextViewText(R.id.widget_grades_avg, if (isNothing) "NIET INGELOGD" else "Log in")
                views.setTextViewText(R.id.widget_grades_empty, "Log in bij Magister om cijfers te zien")
                views.setViewVisibility(R.id.widget_grades_empty, View.VISIBLE)
                rowIds.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val allGrades: List<GradeItem> = summaries
                .flatMap { it.grades }
                .sortedByDescending { it.date }

            val overallAvg = if (summaries.isNotEmpty()) {
                summaries.map { it.weightedAverage }.average()
            } else 0.0

            val avgFormatted = if (summaries.isNotEmpty()) {
                String.format(Locale("nl", "NL"), "%.1f", overallAvg)
            } else "--"

            views.setTextViewText(
                R.id.widget_grades_avg,
                when {
                    isNothing -> "GEM $avgFormatted"
                    isMetro -> "gem: $avgFormatted"
                    else -> "Gem: $avgFormatted"
                }
            )

            if (allGrades.isEmpty() && summaries.isEmpty()) {
                views.setTextViewText(R.id.widget_grades_empty, "Nog geen cijfers gesynchroniseerd")
                views.setViewVisibility(R.id.widget_grades_empty, View.VISIBLE)
                rowIds.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
            } else {
                views.setViewVisibility(R.id.widget_grades_empty, View.GONE)

                data class GradeRowData(val subject: String, val score: Double, val customColorHex: Long?)

                val displayRows: List<GradeRowData> = if (allGrades.isNotEmpty()) {
                    allGrades.take(3).map { g ->
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = g.subjectName.cleanSchoolText() ?: g.subjectCode.ifBlank { "Vak" },
                            explicitCode = g.subjectCode,
                            state = customState
                        )
                        GradeRowData(resolved.displayName, g.score, resolved.accentColorHex)
                    }
                } else {
                    summaries.take(3).map { s ->
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = s.subjectName.cleanSchoolText() ?: s.subjectCode.ifBlank { "Vak" },
                            explicitCode = s.subjectCode,
                            state = customState
                        )
                        GradeRowData(resolved.displayName, s.weightedAverage, resolved.accentColorHex)
                    }
                }

                rowIds.forEachIndexed { index, (rowId, subjectId, scoreId) ->
                    val entry = displayRows.getOrNull(index)
                    if (entry == null) {
                        views.setViewVisibility(rowId, View.GONE)
                    } else {
                        views.setViewVisibility(rowId, View.VISIBLE)
                        views.setTextViewText(subjectId, entry.subject)
                        val scoreStr = String.format(Locale("nl", "NL"), "%.1f", entry.score)
                        views.setTextViewText(scoreId, scoreStr)

                        val isLow = entry.score < 5.5
                        when {
                            isNothing -> {
                                views.setInt(
                                    scoreId,
                                    "setBackgroundResource",
                                    if (isLow) R.drawable.widget_nothing_red_pill_bg else R.drawable.widget_nothing_pill_bg
                                )
                                views.setTextColor(scoreId, Color.WHITE)
                            }
                            isOneUi -> {
                                views.setInt(
                                    scoreId,
                                    "setBackgroundResource",
                                    if (isLow) R.drawable.widget_oneui_error_pill_bg else R.drawable.widget_oneui_pill_bg
                                )
                                views.setTextColor(
                                    scoreId,
                                    if (isLow) Color.parseColor("#E53935") else context.getColor(R.color.oneui_widget_accent)
                                )
                            }
                            isIos -> {
                                views.setInt(
                                    scoreId,
                                    "setBackgroundResource",
                                    if (isLow) R.drawable.widget_ios_error_pill_bg else R.drawable.widget_ios_pill_bg
                                )
                                views.setTextColor(
                                    scoreId,
                                    if (isLow) context.getColor(R.color.ios_widget_error) else context.getColor(R.color.ios_widget_accent)
                                )
                            }
                            isMetro -> {
                                val rowBg = when {
                                    isLow -> Color.parseColor("#E51400")
                                    customState.useSubjectColorsForMetroTiles && entry.customColorHex != null -> entry.customColorHex.toInt()
                                    else -> Color.parseColor("#1D1D1D")
                                }
                                views.setInt(rowId, "setBackgroundColor", rowBg)
                                views.setInt(
                                    scoreId,
                                    "setBackgroundColor",
                                    if (isLow) Color.parseColor("#B00F00") else metroAccent.colorHex.toInt()
                                )
                                views.setTextColor(scoreId, Color.WHITE)
                            }
                            else -> {
                                views.setTextColor(
                                    scoreId,
                                    if (isLow) Color.parseColor("#FCA5A5") else Color.parseColor("#93C5FD")
                                )
                            }
                        }
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
