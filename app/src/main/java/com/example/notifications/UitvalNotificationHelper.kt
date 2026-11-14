package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppPreferencesStore
import com.example.model.Appointment
import com.example.model.cleanSchoolText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UitvalNotificationHelper {

    const val CHANNEL_ID = "uitval_alerts_channel"
    private const val CHANNEL_NAME = "Lesuitval meldingen"
    private const val CHANNEL_DESC = "Directe meldingen zodra een lesuur uitvalt of wordt geschrapt."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun cancellationKey(appt: Appointment): String =
        "${appt.id}_${appt.dateIso}_${appt.startTime}"

    internal fun findNewlyCanceledLessons(
        appointments: List<Appointment>,
        previouslyNotifiedKeys: Set<String>,
        todayIso: String
    ): Pair<List<Appointment>, Set<String>> {
        val updatedKeys = previouslyNotifiedKeys.toMutableSet()
        val newlyCanceled = mutableListOf<Appointment>()

        for (appt in appointments) {
            val key = cancellationKey(appt)
            if (!appt.isCanceled) {
                // If lesson was un-canceled, clear key so a future cancellation alerts again
                updatedKeys.remove(key)
                continue
            }

            val isTodayOrUpcoming = appt.dateIso.isBlank() || appt.dateIso >= todayIso
            if (isTodayOrUpcoming && !updatedKeys.contains(key)) {
                newlyCanceled.add(appt)
                updatedKeys.add(key)
            }
        }

        return newlyCanceled to updatedKeys
    }

    fun evaluateAndNotifyCancellations(
        context: Context,
        appointments: List<Appointment>
    ): List<Appointment> {
        val store = AppPreferencesStore(context)
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val previousKeys = store.getNotifiedCanceledKeys()

        val (newlyCanceled, updatedKeys) = findNewlyCanceledLessons(
            appointments = appointments,
            previouslyNotifiedKeys = previousKeys,
            todayIso = todayIso
        )

        store.setNotifiedCanceledKeys(updatedKeys)

        if (newlyCanceled.isEmpty() || !store.isUitvalNotificationsEnabled()) {
            return emptyList()
        }

        if (!hasNotificationPermission(context)) {
            com.example.util.RawConsoleLogger.info(
                "Uitval-Notif",
                "${newlyCanceled.size} uitgevallen les(sen) gevonden, wachtend op meldingen-permissie"
            )
            return newlyCanceled
        }

        ensureChannel(context)

        if (newlyCanceled.size <= 3) {
            newlyCanceled.forEach { appt ->
                postSingleCancellationNotification(context, appt, todayIso)
            }
        } else {
            postSummaryCancellationNotification(context, newlyCanceled, todayIso)
        }

        com.example.util.RawConsoleLogger.info(
            "Uitval-Notif",
            "Uitval-melding verstuurd voor ${newlyCanceled.size} les(sen)"
        )
        return newlyCanceled
    }

    fun sendTestUitvalNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)
        val sample = Appointment(
            id = 999999L,
            title = "Wiskunde B",
            subjectCode = "WI",
            subjectName = "Wiskunde B",
            teacherName = "Docent",
            teacherInitials = "DOC",
            classroom = "204",
            periodIndex = 3,
            startTime = "10:15",
            endTime = "11:05",
            dayOfWeek = 1,
            isCanceled = true,
            dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
        postSingleCancellationNotification(context, sample, sample.dateIso, isTest = true)
        return true
    }

    private fun postSingleCancellationNotification(
        context: Context,
        appt: Appointment,
        todayIso: String,
        isTest: Boolean = false
    ) {
        val subject = appt.subjectName.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: "Les"
        val dayPrefix = when {
            appt.dateIso == todayIso || appt.dateIso.isBlank() -> "Vandaag"
            else -> formatShortDutchDate(appt.dateIso)
        }
        val teacherSuffix = appt.teacherName.cleanSchoolText()?.let { " • $it" } ?: ""
        val roomSuffix = appt.classroom.cleanSchoolText()?.let { " • Lok. $it" } ?: ""

        val title = if (isTest) {
            "Test Uitval: $subject (${appt.periodIndex}e uur)"
        } else {
            "Uitval: $subject (${appt.periodIndex}e uur)"
        }
        val body = "$dayPrefix ${appt.startTime} - ${appt.endTime}$roomSuffix$teacherSuffix is uitgevallen."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appt.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(appt.id.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postSummaryCancellationNotification(
        context: Context,
        canceledList: List<Appointment>,
        todayIso: String
    ) {
        val firstFew = canceledList.take(3).joinToString(", ") {
            val subj = it.subjectName.cleanSchoolText() ?: it.title
            "${it.periodIndex}e $subj"
        }
        val extra = if (canceledList.size > 3) " +${canceledList.size - 3} meer" else ""
        val hasToday = canceledList.any { it.dateIso == todayIso }
        val prefix = if (hasToday) "Vandaag & binnenkort" else "Komende dagen"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            77001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("${canceledList.size} lessen uitgevallen")
            .setContentText("$prefix: $firstFew$extra")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(77001, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun formatShortDutchDate(dateIso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE d MMM", Locale.forLanguageTag("nl-NL"))
            val parsed = parser.parse(dateIso) ?: return dateIso
            formatter.format(parsed)
        } catch (_: Exception) {
            dateIso
        }
    }
}
