package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppPreferencesStore
import com.example.model.AppThemeStyle
import com.example.model.cleanSchoolText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NextLessonWidgetProvider : AppWidgetProvider() {

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
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_NEXT_LESSON"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NextLessonWidgetProvider::class.java)
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
            val isNothing = store.getAppThemeStyle() == AppThemeStyle.NOTHING_OS
            val layoutId = if (isNothing) R.layout.widget_next_lesson_nothing else R.layout.widget_next_lesson
            val views = RemoteViews(context.packageName, layoutId)
            val session = store.loadSession()
            val appointments = store.loadAppointments()

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetUpdater.EXTRA_OPEN_TAB, "TODAY")
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPendingIntent)

            val refreshIntent = Intent(context, NextLessonWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

            if (!session.isAuthenticated) {
                views.setTextViewText(R.id.widget_status_badge, "AGENDA+")
                views.setTextViewText(R.id.widget_school, "Niet verbonden")
                views.setTextViewText(R.id.widget_subject, "Log in bij Magister")
                views.setTextViewText(R.id.widget_time, "Tik om je rooster te koppelen")
                views.setViewVisibility(R.id.widget_room, View.GONE)
                if (isNothing) {
                    views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_nothing_pill_bg)
                }
            } else {
                val cal = Calendar.getInstance()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val hhmmFormat = SimpleDateFormat("HH:mm", Locale.US)
                val shortDateFormat = SimpleDateFormat("EEE d MMM", Locale("nl", "NL"))
                val todayIso = isoFormat.format(cal.time)
                val nowHHmm = hhmmFormat.format(cal.time)

                val schoolLabel = session.school?.name?.replace(" (Magister)", "") ?: "Agenda+"
                views.setTextViewText(R.id.widget_school, if (isNothing) schoolLabel.uppercase() else schoolLabel)

                val activeAppts = appointments.filter { !it.isCanceled }
                val todayRemaining = activeAppts
                    .filter { it.dateIso == todayIso && it.endTime > nowHHmm }
                    .sortedWith(compareBy({ it.startTime }, { it.periodIndex }))

                val nextUpcoming = todayRemaining.firstOrNull() ?: activeAppts
                    .filter { it.dateIso > todayIso }
                    .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.periodIndex }))
                    .firstOrNull()

                if (nextUpcoming == null) {
                    views.setTextViewText(R.id.widget_status_badge, "VRIJ")
                    views.setTextViewText(R.id.widget_subject, "Geen komende lessen")
                    views.setTextViewText(R.id.widget_time, "Rooster is bijgewerkt")
                    views.setViewVisibility(R.id.widget_room, View.GONE)
                    if (isNothing) {
                        views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_nothing_pill_bg)
                    }
                } else {
                    val isNow = nextUpcoming.dateIso == todayIso &&
                        nowHHmm >= nextUpcoming.startTime && nowHHmm < nextUpcoming.endTime
                    val isToday = nextUpcoming.dateIso == todayIso

                    val badge = when {
                        isNow -> "NU BEZIG"
                        isToday -> "VOLGENDE LES"
                        else -> {
                            val parsedDay = runCatching { isoFormat.parse(nextUpcoming.dateIso) }.getOrNull()
                            parsedDay?.let { shortDateFormat.format(it).uppercase() } ?: nextUpcoming.dateIso
                        }
                    }

                    val teacherSuffix = nextUpcoming.teacherName.cleanSchoolText()?.let { " • $it" } ?: ""
                    val timeText = "${nextUpcoming.startTime} - ${nextUpcoming.endTime} (${nextUpcoming.periodIndex}e)$teacherSuffix"
                    val room = nextUpcoming.classroom.cleanSchoolText()

                    views.setTextViewText(R.id.widget_status_badge, badge)
                    views.setTextViewText(
                        R.id.widget_subject,
                        if (isNothing) nextUpcoming.subjectName.uppercase() else nextUpcoming.subjectName
                    )
                    views.setTextViewText(R.id.widget_time, timeText)
                    if (isNothing) {
                        views.setInt(
                            R.id.widget_status_badge,
                            "setBackgroundResource",
                            if (isNow || isToday) R.drawable.widget_nothing_red_pill_bg else R.drawable.widget_nothing_pill_bg
                        )
                    }
                    if (room != null) {
                        views.setTextViewText(R.id.widget_room, "Lok. $room")
                        views.setViewVisibility(R.id.widget_room, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_room, View.GONE)
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
