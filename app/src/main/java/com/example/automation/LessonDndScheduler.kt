package com.example.automation

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import com.example.data.AppPreferencesStore
import com.example.model.Appointment
import com.example.model.LessonDndConfig
import com.example.model.LessonDndMode
import java.util.TimeZone

data class LessonSilentWindow(
    val startMs: Long,
    val endMs: Long,
    val hasExam: Boolean,
    val labels: List<String>
)

object LessonDndScheduler {

    const val ACTION_START_LESSON_SILENT = "com.example.automation.ACTION_START_LESSON_SILENT"
    const val ACTION_END_LESSON_SILENT = "com.example.automation.ACTION_END_LESSON_SILENT"
    private const val REQ_CODE_START = 7101
    private const val REQ_CODE_END = 7102
    private const val TIMEZONE_AMSTERDAM = "Europe/Amsterdam"

    fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    fun computeSilentWindows(
        appointments: List<Appointment>,
        config: LessonDndConfig,
        nowMs: Long = System.currentTimeMillis()
    ): List<LessonSilentWindow> {
        val tz = TimeZone.getTimeZone(TIMEZONE_AMSTERDAM)
        val startBufferMs = config.startBufferMinutes.coerceIn(0, 15) * 60_000L
        val endBufferMs = config.endBufferMinutes.coerceIn(0, 15) * 60_000L

        val rawWindows = appointments.mapNotNull { appt ->
            if (appt.isCanceled || appt.dateIso.isBlank()) return@mapNotNull null
            val lowerTitle = "${appt.title} ${appt.subjectName}".lowercase()
            if (lowerTitle.contains("tussenuur") || lowerTitle.contains("vakantie") || lowerTitle.contains("roostervrij")) {
                return@mapNotNull null
            }
            val startMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(appt.dateIso, appt.startTime, tz)
                ?: return@mapNotNull null
            val rawEndMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(appt.dateIso, appt.endTime, tz)
                ?.takeIf { it > startMs }
                ?: (startMs + 45 * 60_000L)

            val windowStart = startMs - startBufferMs
            val windowEnd = rawEndMs + endBufferMs
            if (windowEnd <= nowMs) return@mapNotNull null

            LessonSilentWindow(
                startMs = windowStart,
                endMs = windowEnd,
                hasExam = appt.isTestOrExam,
                labels = listOf(appt.subjectName.ifBlank { appt.title })
            )
        }.sortedBy { it.startMs }

        if (rawWindows.isEmpty()) return emptyList()

        val merged = mutableListOf<LessonSilentWindow>()
        var current = rawWindows.first()
        for (i in 1 until rawWindows.size) {
            val next = rawWindows[i]
            if (next.startMs <= current.endMs) {
                current = current.copy(
                    endMs = maxOf(current.endMs, next.endMs),
                    hasExam = current.hasExam || next.hasExam,
                    labels = (current.labels + next.labels).distinct()
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    fun rescheduleFromPrefs(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val store = AppPreferencesStore(context)
        val config = store.getLessonDndConfig()
        val appointments = store.loadAppointments()
        rescheduleAlarms(context, appointments, config, nowMs)
    }

    fun rescheduleAlarms(
        context: Context,
        appointments: List<Appointment>,
        config: LessonDndConfig,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelPendingAlarms(context, alarmManager)

        if (!config.enabled) {
            restoreAudioModeIfApplied(context)
            return
        }

        val windows = computeSilentWindows(appointments, config, nowMs)
        val activeWindow = windows.firstOrNull { nowMs in it.startMs until it.endMs }
        if (activeWindow != null) {
            applySilentModeNow(context, config, activeWindow.hasExam)
            scheduleAlarm(
                context = context,
                alarmManager = alarmManager,
                action = ACTION_END_LESSON_SILENT,
                requestCode = REQ_CODE_END,
                triggerAtMs = activeWindow.endMs
            )
        } else {
            restoreAudioModeIfApplied(context)
            val nextWindow = windows.firstOrNull { it.startMs > nowMs }
            if (nextWindow != null) {
                scheduleAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    action = ACTION_START_LESSON_SILENT,
                    requestCode = REQ_CODE_START,
                    triggerAtMs = nextWindow.startMs
                )
            }
        }
    }

    fun applySilentModeNow(
        context: Context,
        config: LessonDndConfig,
        hasExam: Boolean
    ) {
        val store = AppPreferencesStore(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (!store.isLessonDndCurrentlyApplied()) {
            val prevRinger = runCatching { audioManager?.ringerMode ?: -1 }.getOrDefault(-1)
            val prevFilter = runCatching {
                notificationManager?.currentInterruptionFilter ?: -1
            }.getOrDefault(-1)
            store.saveLessonDndSavedAudioState(
                applied = true,
                prevRingerMode = prevRinger,
                prevInterruptionFilter = prevFilter
            )
        }

        val useFullDnd = (config.mode == LessonDndMode.FULL_DND) ||
            (hasExam && config.forceDndDuringExams)

        if (useFullDnd && notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
            runCatching {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            }
        } else if (audioManager != null) {
            runCatching {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
        }
    }

    fun restoreAudioModeIfApplied(context: Context) {
        val store = AppPreferencesStore(context)
        if (!store.isLessonDndCurrentlyApplied()) return

        val prevRinger = store.getLessonDndPrevRingerMode()
        val prevFilter = store.getLessonDndPrevInterruptionFilter()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted && prevFilter > 0) {
            runCatching {
                notificationManager.setInterruptionFilter(prevFilter)
            }
        }
        if (audioManager != null && prevRinger in listOf(
                AudioManager.RINGER_MODE_NORMAL,
                AudioManager.RINGER_MODE_VIBRATE,
                AudioManager.RINGER_MODE_SILENT
            )
        ) {
            runCatching {
                audioManager.ringerMode = prevRinger
            }
        }
        store.clearLessonDndAppliedState()
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager != null) {
            cancelPendingAlarms(context, alarmManager)
        }
        restoreAudioModeIfApplied(context)
    }

    private fun cancelPendingAlarms(context: Context, alarmManager: AlarmManager) {
        alarmManager.cancel(buildAlarmPendingIntent(context, ACTION_START_LESSON_SILENT, REQ_CODE_START))
        alarmManager.cancel(buildAlarmPendingIntent(context, ACTION_END_LESSON_SILENT, REQ_CODE_END))
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, LessonDndReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        action: String,
        requestCode: Int,
        triggerAtMs: Long
    ) {
        val pendingIntent = buildAlarmPendingIntent(context, action, requestCode)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }
}
