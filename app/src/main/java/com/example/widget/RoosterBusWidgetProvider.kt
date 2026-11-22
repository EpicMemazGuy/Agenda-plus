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
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.cleanSchoolText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RoosterBusWidgetProvider : AppWidgetProvider() {

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
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_ROOSTER_BUS"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RoosterBusWidgetProvider::class.java)
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
            val isNothing = themeStyle == AppThemeStyle.NOTHING_OS
            val isOneUi = themeStyle == AppThemeStyle.ONE_UI
            val isMetro = themeStyle == AppThemeStyle.WINDOWS_PHONE
            val isIos = themeStyle == AppThemeStyle.IOS_LIQUID_GLASS
            val layoutId = when (themeStyle) {
                AppThemeStyle.NOTHING_OS -> R.layout.widget_rooster_bus_nothing
                AppThemeStyle.ONE_UI -> R.layout.widget_rooster_bus_oneui
                AppThemeStyle.WINDOWS_PHONE -> R.layout.widget_rooster_bus_metro
                AppThemeStyle.IOS_LIQUID_GLASS -> R.layout.widget_rooster_bus_ios
                AppThemeStyle.STANDARD -> R.layout.widget_rooster_bus
            }
            val views = RemoteViews(context.packageName, layoutId)
            val session = store.loadSession()
            val appointments = store.loadAppointments()
            val busSnapshot = store.loadBusWidgetSnapshot()
            val savedBusLine = store.getSavedBusLine()

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetUpdater.EXTRA_OPEN_TAB, "TODAY")
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                401,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_rb_root, launchPendingIntent)

            val refreshIntent = Intent(context, RoosterBusWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                402,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_rb_refresh_btn, refreshPendingIntent)

            if (isMetro) {
                val accentInt = metroAccent.colorHex.toInt()
                views.setInt(R.id.widget_rb_badge, "setBackgroundColor", accentInt)
                views.setInt(R.id.widget_rb_room, "setBackgroundColor", accentInt)
                views.setInt(R.id.widget_rb_bus_line, "setBackgroundColor", accentInt)
            }

            if (!session.isAuthenticated) {
                views.setTextViewText(R.id.widget_rb_badge, "AGENDA+")
                views.setTextViewText(R.id.widget_rb_time, "Niet verbonden")
                views.setTextViewText(
                    R.id.widget_rb_lesson,
                    when {
                        isNothing -> "LOG IN BIJ MAGISTER"
                        isMetro -> "log in bij magister"
                        else -> "Log in bij Magister"
                    }
                )
                views.setViewVisibility(R.id.widget_rb_room, View.GONE)
            } else {
                val cal = Calendar.getInstance()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val hhmmFormat = SimpleDateFormat("HH:mm", Locale.US)
                val todayIso = isoFormat.format(cal.time)
                val nowHHmm = hhmmFormat.format(cal.time)

                val activeAppts = appointments.filter { !it.isCanceled }
                val todayRemaining = activeAppts
                    .filter { it.dateIso == todayIso && it.endTime > nowHHmm }
                    .sortedWith(compareBy({ it.startTime }, { it.periodIndex }))

                val nextUpcoming = todayRemaining.firstOrNull() ?: activeAppts
                    .filter { it.dateIso > todayIso }
                    .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.periodIndex }))
                    .firstOrNull()

                if (nextUpcoming == null) {
                    views.setTextViewText(R.id.widget_rb_badge, "ROOSTER + BUS")
                    views.setTextViewText(R.id.widget_rb_time, "Vrij")
                    views.setTextViewText(
                        R.id.widget_rb_lesson,
                        when {
                            isNothing -> "GEEN KOMENDE LESSEN"
                            isMetro -> "geen komende lessen"
                            else -> "Geen komende lessen"
                        }
                    )
                    views.setViewVisibility(R.id.widget_rb_room, View.GONE)
                } else {
                    val isNow = nextUpcoming.dateIso == todayIso &&
                        nowHHmm >= nextUpcoming.startTime && nowHHmm < nextUpcoming.endTime
                    views.setTextViewText(R.id.widget_rb_badge, if (isNow) "NU + BUS" else "ROOSTER + BUS")
                    views.setTextViewText(
                        R.id.widget_rb_time,
                        "${nextUpcoming.startTime}-${nextUpcoming.endTime} (${nextUpcoming.periodIndex}e)"
                    )
                    val resolvedSubject = SubjectCustomizationCatalog.resolveSubject(
                        rawSubject = nextUpcoming.subjectName.cleanSchoolText() ?: nextUpcoming.title,
                        rawTitle = nextUpcoming.title,
                        explicitCode = nextUpcoming.subjectCode,
                        state = customState
                    )
                    val subjTitle = resolvedSubject.displayName
                    views.setTextViewText(
                        R.id.widget_rb_lesson,
                        when {
                            isNothing -> subjTitle.uppercase(Locale("nl", "NL"))
                            isMetro -> subjTitle.lowercase(Locale("nl", "NL"))
                            else -> subjTitle
                        }
                    )
                    val room = nextUpcoming.classroom.cleanSchoolText()
                    if (room != null) {
                        views.setTextViewText(
                            R.id.widget_rb_room,
                            if (isMetro) room.uppercase(Locale("nl", "NL")) else "Lok. $room"
                        )
                        views.setViewVisibility(R.id.widget_rb_room, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_rb_room, View.GONE)
                    }
                }
            }

            val effectiveLine = busSnapshot?.busLine?.takeIf { it.isNotBlank() } ?: savedBusLine
            if (effectiveLine.isBlank()) {
                views.setTextViewText(R.id.widget_rb_bus_line, "BUS")
                views.setTextViewText(R.id.widget_rb_bus_stop, "Stel buslijn in bij Vandaag")
                views.setTextViewText(R.id.widget_rb_bus_eta, "--:--")
            } else {
                views.setTextViewText(R.id.widget_rb_bus_line, "LIJN $effectiveLine")
                val stopText = when {
                    busSnapshot?.smartAdviceText?.isNotBlank() == true -> busSnapshot.smartAdviceText
                    busSnapshot?.stopName?.isNotBlank() == true -> busSnapshot.stopName
                    else -> "Live OVapi halte laden..."
                }
                views.setTextViewText(R.id.widget_rb_bus_stop, stopText)

                val etaText = if (busSnapshot != null && busSnapshot.expectedTime.isNotBlank()) {
                    if (busSnapshot.delayMinutes > 0) {
                        "${busSnapshot.expectedTime} (+${busSnapshot.delayMinutes}m)"
                    } else {
                        busSnapshot.expectedTime
                    }
                } else {
                    "--:--"
                }
                views.setTextViewText(R.id.widget_rb_bus_eta, etaText)
                if (busSnapshot != null && busSnapshot.delayMinutes > 0) {
                    when {
                        isOneUi -> views.setTextColor(R.id.widget_rb_bus_eta, Color.parseColor("#E53935"))
                        isIos -> views.setTextColor(R.id.widget_rb_bus_eta, context.getColor(R.color.ios_widget_error))
                        isMetro -> views.setTextColor(R.id.widget_rb_bus_eta, Color.parseColor("#E51400"))
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
