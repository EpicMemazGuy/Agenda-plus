package com.example.widget

import android.content.Context

object WidgetUpdater {
    const val EXTRA_OPEN_TAB = "extra_open_tab"

    fun updateAllWidgets(context: Context) {
        NextLessonWidgetProvider.updateAllWidgets(context)
        GradesWidgetProvider.updateAllWidgets(context)
        HomeworkWidgetProvider.updateAllWidgets(context)
        RoosterBusWidgetProvider.updateAllWidgets(context)
    }
}
