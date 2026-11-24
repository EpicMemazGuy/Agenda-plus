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
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.TaskCategory
import com.example.model.cleanSchoolText

class HomeworkWidgetProvider : AppWidgetProvider() {

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
        when (intent.action) {
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    val store = AppPreferencesStore(context)
                    val updated = store.loadTasks().map { task ->
                        if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
                    }
                    store.saveTasks(updated)
                    store.touchLocalSyncUpdatedAt()
                    updateAllWidgets(context)
                }
            }
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_HOMEWORK"
        const val ACTION_TOGGLE_TASK = "com.example.widget.ACTION_TOGGLE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HomeworkWidgetProvider::class.java)
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
                AppThemeStyle.NOTHING_OS -> R.layout.widget_homework_nothing
                AppThemeStyle.ONE_UI -> R.layout.widget_homework_oneui
                AppThemeStyle.WINDOWS_PHONE -> R.layout.widget_homework_metro
                AppThemeStyle.IOS_LIQUID_GLASS -> R.layout.widget_homework_ios
                AppThemeStyle.AXOLA_CUSTOM -> when {
                    isMetro -> R.layout.widget_homework_metro
                    isNothing -> R.layout.widget_homework_nothing
                    else -> R.layout.widget_homework
                }
                AppThemeStyle.STANDARD -> R.layout.widget_homework
            }
            val views = RemoteViews(context.packageName, layoutId)
            val session = store.loadSession()
            val tasks = store.loadTasks()

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetUpdater.EXTRA_OPEN_TAB, "HUISWERK")
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                301,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_hw_root, launchPendingIntent)

            val refreshIntent = Intent(context, HomeworkWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                302,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_hw_refresh_btn, refreshPendingIntent)

            if (isMetro) {
                views.setInt(R.id.widget_hw_badge, "setBackgroundColor", metroAccent.colorHex.toInt())
            }

            val rowIds = listOf(
                Triple(R.id.widget_hw_row_1, R.id.widget_hw_title_1, R.id.widget_hw_meta_1),
                Triple(R.id.widget_hw_row_2, R.id.widget_hw_title_2, R.id.widget_hw_meta_2),
                Triple(R.id.widget_hw_row_3, R.id.widget_hw_title_3, R.id.widget_hw_meta_3)
            )

            if (!session.isAuthenticated) {
                views.setTextViewText(R.id.widget_hw_count, if (isNothing) "NIET INGELOGD" else "Log in")
                views.setTextViewText(R.id.widget_hw_empty, "Log in bij Magister voor huiswerk")
                views.setViewVisibility(R.id.widget_hw_empty, View.VISIBLE)
                rowIds.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val openTasks = tasks
                .filter { !it.isCompleted }
                .sortedWith(
                    compareByDescending<com.example.model.HomeworkTask> {
                        it.isTest || it.isPresentation
                    }.thenBy { it.dueDate }
                )

            val openCount = openTasks.size
            when {
                isNothing -> {
                    views.setTextViewText(
                        R.id.widget_hw_count,
                        if (openCount > 0) "$openCount OPEN" else "KLAAR"
                    )
                    views.setInt(
                        R.id.widget_hw_badge,
                        "setBackgroundResource",
                        if (openCount > 0) R.drawable.widget_nothing_red_pill_bg else R.drawable.widget_nothing_pill_bg
                    )
                }
                isMetro -> {
                    views.setTextViewText(
                        R.id.widget_hw_count,
                        if (openCount > 0) "$openCount open" else "klaar"
                    )
                }
                else -> {
                    views.setTextViewText(
                        R.id.widget_hw_count,
                        if (openCount == 1) "1 open taak" else "$openCount open taken"
                    )
                }
            }

            if (openTasks.isEmpty()) {
                views.setTextViewText(R.id.widget_hw_empty, "Geen openstaand huiswerk")
                views.setViewVisibility(R.id.widget_hw_empty, View.VISIBLE)
                rowIds.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
            } else {
                views.setViewVisibility(R.id.widget_hw_empty, View.GONE)
                rowIds.forEachIndexed { index, (rowId, titleId, metaId) ->
                    val task = openTasks.getOrNull(index)
                    if (task == null) {
                        views.setViewVisibility(rowId, View.GONE)
                    } else {
                        views.setViewVisibility(rowId, View.VISIBLE)
                        val title = task.title.cleanSchoolText() ?: "Huiswerk"
                        val resolved = SubjectCustomizationCatalog.resolveSubject(
                            rawSubject = task.subject.cleanSchoolText() ?: "Vak",
                            state = customState
                        )
                        val subj = resolved.displayName
                        val badgeText = when {
                            task.category == TaskCategory.PRESENTATION -> "PRESENTATIE • $subj"
                            task.category == TaskCategory.PRACTICAL_ASSIGNMENT -> "PO • $subj"
                            task.isTest -> "TOETS • $subj"
                            else -> subj.take(12)
                        }
                        val isExam = task.isTest
                        views.setTextViewText(titleId, title)
                        views.setTextViewText(metaId, badgeText)

                        val toggleIntent = Intent(context, HomeworkWidgetProvider::class.java).apply {
                            action = ACTION_TOGGLE_TASK
                            putExtra(EXTRA_TASK_ID, task.id)
                        }
                        val togglePendingIntent = PendingIntent.getBroadcast(
                            context,
                            310 + index,
                            toggleIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(rowId, togglePendingIntent)

                        when {
                            isNothing -> {
                                views.setInt(
                                    metaId,
                                    "setBackgroundResource",
                                    if (isExam) R.drawable.widget_nothing_red_pill_bg else R.drawable.widget_nothing_pill_bg
                                )
                                views.setTextColor(metaId, Color.WHITE)
                            }
                            isOneUi -> {
                                views.setInt(
                                    metaId,
                                    "setBackgroundResource",
                                    if (isExam) R.drawable.widget_oneui_error_pill_bg else R.drawable.widget_oneui_pill_bg
                                )
                                views.setTextColor(
                                    metaId,
                                    if (isExam) Color.parseColor("#E53935") else context.getColor(R.color.oneui_widget_accent)
                                )
                            }
                            isIos -> {
                                views.setInt(
                                    metaId,
                                    "setBackgroundResource",
                                    if (isExam) R.drawable.widget_ios_error_pill_bg else R.drawable.widget_ios_pill_bg
                                )
                                views.setTextColor(
                                    metaId,
                                    if (isExam) context.getColor(R.color.ios_widget_error) else context.getColor(R.color.ios_widget_accent)
                                )
                            }
                            isMetro -> {
                                val rowBg = when {
                                    isExam -> Color.parseColor("#E51400")
                                    customState.useSubjectColorsForMetroTiles && resolved.accentColorHex != null -> resolved.accentColorHex.toInt()
                                    else -> Color.parseColor("#1D1D1D")
                                }
                                views.setInt(rowId, "setBackgroundColor", rowBg)
                                views.setInt(
                                    metaId,
                                    "setBackgroundColor",
                                    if (isExam) Color.parseColor("#B00F00") else metroAccent.colorHex.toInt()
                                )
                                views.setTextColor(metaId, Color.WHITE)
                            }
                            else -> {
                                views.setTextColor(
                                    metaId,
                                    if (isExam) Color.parseColor("#FCA5A5") else Color.parseColor("#CBD5E1")
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
