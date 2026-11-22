package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LessonDndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            LessonDndScheduler.ACTION_START_LESSON_SILENT,
            LessonDndScheduler.ACTION_END_LESSON_SILENT,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                LessonDndScheduler.rescheduleFromPrefs(context.applicationContext)
            }
        }
    }
}
