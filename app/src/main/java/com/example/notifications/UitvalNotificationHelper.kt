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
import com.example.model.GradeItem
import com.example.model.HomeworkTask
import com.example.model.SubjectGradeSummary
import com.example.model.TaskCategory
import com.example.model.capitalizeSubjectName
import com.example.model.cleanSchoolText
import com.example.model.isTestKeywordMatch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleChangeAlert(
    val appointment: Appointment,
    val changeSummary: String,
    val changeKey: String
)

data class TestNotificationItem(
    val key: String,
    val subject: String,
    val title: String,
    val dateLabel: String
)

object UitvalNotificationHelper {

    const val CHANNEL_ID = "uitval_alerts_channel"
    private const val CHANNEL_NAME = "Uitval & roosterwijzigingen"
    private const val CHANNEL_DESC = "Directe meldingen zodra een lesuur uitvalt, verplaatst of van tijd verandert."

    const val GRADES_CHANNEL_ID = "grades_alerts_channel"
    private const val GRADES_CHANNEL_NAME = "Nieuwe cijfers"
    private const val GRADES_CHANNEL_DESC = "Directe meldingen zodra er een nieuw cijfer in Magister staat."

    const val TESTS_CHANNEL_ID = "tests_alerts_channel"
    private const val TESTS_CHANNEL_NAME = "Nieuwe toetsen"
    private const val TESTS_CHANNEL_DESC = "Meldingen zodra een nieuwe toets, PW, SO of MO wordt ingepland."

    const val SMART_BUS_CHANNEL_ID = "smart_bus_alerts_channel"
    private const val SMART_BUS_CHANNEL_NAME = "Slimme Busmeldingen"
    private const val SMART_BUS_CHANNEL_DESC = "Directe vertrektijd-updates wanneer het 1e uur uitvalt."

    const val ONGOING_MORNING_CHANNEL_ID = "morning_live_status_channel"
    private const val ONGOING_MORNING_CHANNEL_NAME = "Ochtend Les- & Bus-status"
    private const val ONGOING_MORNING_CHANNEL_DESC = "Stille lockscreen afteller tijdens schoolochtenden met lokaal en busvertrek."
    private const val ONGOING_MORNING_NOTIF_ID = 82001

    fun isWithinQuietHours(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val wrappedNow = ((nowMinutes % 1440) + 1440) % 1440
        val start = ((startMinutes % 1440) + 1440) % 1440
        val end = ((endMinutes % 1440) + 1440) % 1440
        if (start == end) return false
        return if (start < end) {
            wrappedNow in start until end
        } else {
            wrappedNow >= start || wrappedNow < end
        }
    }

    fun isSubjectMuted(subjectName: String?, mutedSubjects: Set<String>): Boolean {
        val clean = subjectName?.trim()?.lowercase(Locale.getDefault()) ?: return false
        if (clean.isEmpty() || mutedSubjects.isEmpty()) return false
        return mutedSubjects.any { muted ->
            val m = muted.trim().lowercase(Locale.getDefault())
            m.isNotEmpty() && (clean == m || clean.contains(m))
        }
    }

    fun isQuietHoursActive(store: AppPreferencesStore, nowMinutes: Int = currentClockMinutes()): Boolean {
        val cfg = store.getQuietHoursConfig()
        return cfg.enabled && isWithinQuietHours(nowMinutes, cfg.startMinutes, cfg.endMinutes)
    }

    private fun currentClockMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channels = listOf(
                Triple(CHANNEL_ID, CHANNEL_NAME, CHANNEL_DESC),
                Triple(GRADES_CHANNEL_ID, GRADES_CHANNEL_NAME, GRADES_CHANNEL_DESC),
                Triple(TESTS_CHANNEL_ID, TESTS_CHANNEL_NAME, TESTS_CHANNEL_DESC),
                Triple(SMART_BUS_CHANNEL_ID, SMART_BUS_CHANNEL_NAME, SMART_BUS_CHANNEL_DESC)
            )
            for ((id, name, desc) in channels) {
                if (manager.getNotificationChannel(id) == null) {
                    val channel = NotificationChannel(
                        id,
                        name,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = desc
                        enableVibration(true)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
            if (manager.getNotificationChannel(ONGOING_MORNING_CHANNEL_ID) == null) {
                val silentChannel = NotificationChannel(
                    ONGOING_MORNING_CHANNEL_ID,
                    ONGOING_MORNING_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = ONGOING_MORNING_CHANNEL_DESC
                    enableVibration(false)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(silentChannel)
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

    fun scheduleChangeKey(appt: Appointment): String =
        "${appt.id}_${appt.dateIso}_${appt.classroom.trim()}_${appt.startTime}_${appt.endTime}"

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

    internal fun findScheduleChanges(
        previousAppointments: List<Appointment>,
        newAppointments: List<Appointment>,
        previouslyNotifiedChangeKeys: Set<String>,
        todayIso: String
    ): Pair<List<ScheduleChangeAlert>, Set<String>> {
        val updatedKeys = previouslyNotifiedChangeKeys.toMutableSet()
        if (previousAppointments.isEmpty()) {
            return emptyList<ScheduleChangeAlert>() to updatedKeys
        }

        val prevById = previousAppointments.associateBy { it.id }
        val alerts = mutableListOf<ScheduleChangeAlert>()

        for (appt in newAppointments) {
            val isTodayOrUpcoming = appt.dateIso.isBlank() || appt.dateIso >= todayIso
            if (!isTodayOrUpcoming) continue

            val prev = prevById[appt.id] ?: continue
            val canceledChanged = prev.isCanceled != appt.isCanceled
            if (appt.isCanceled && !canceledChanged) continue
            val roomChanged = prev.classroom.trim() != appt.classroom.trim() && appt.classroom.isNotBlank()
            val timeChanged = prev.startTime != appt.startTime || prev.endTime != appt.endTime
            val periodChanged = prev.periodIndex != appt.periodIndex && appt.periodIndex > 0
            val teacherChanged = prev.teacherName.trim() != appt.teacherName.trim() && appt.teacherName.isNotBlank()
            if (!canceledChanged && !roomChanged && !timeChanged && !periodChanged && !teacherChanged) continue

            val key = scheduleChangeKey(appt)
            if (updatedKeys.contains(key)) continue

            val parts = mutableListOf<String>()
            if (canceledChanged) {
                parts.add(if (appt.isCanceled) "Uitval" else "Les hervat")
            }
            if (roomChanged) {
                val oldRoom = prev.classroom.cleanSchoolText() ?: "?"
                parts.add("Lokaal: $oldRoom -> ${appt.classroom}")
            }
            if (periodChanged) {
                parts.add("${prev.periodIndex}e -> ${appt.periodIndex}e uur")
            }
            if (timeChanged) {
                parts.add("Tijd gewijzigd (${prev.startTime} -> ${appt.startTime})")
            }
            if (teacherChanged) {
                val oldTeacher = prev.teacherName.cleanSchoolText() ?: "?"
                parts.add("Docent: $oldTeacher -> ${appt.teacherName}")
            }

            updatedKeys.add(key)
            alerts.add(
                ScheduleChangeAlert(
                    appointment = appt,
                    changeSummary = parts.joinToString(" • "),
                    changeKey = key
                )
            )
        }

        return alerts to updatedKeys
    }

    internal fun findNewGrades(
        newGradeSummaries: List<SubjectGradeSummary>,
        knownGradeIds: Set<String>,
        hasBaseline: Boolean
    ): Pair<List<GradeItem>, Set<String>> {
        val allItems = newGradeSummaries.flatMap { it.grades }
        val allIds = allItems.map { it.id }.toSet()
        if (!hasBaseline) {
            return emptyList<GradeItem>() to allIds
        }
        val newItems = allItems.filter { it.id !in knownGradeIds }
        return newItems to (knownGradeIds + allIds)
    }

    internal fun collectUpcomingTests(
        appointments: List<Appointment>,
        tasks: List<HomeworkTask>,
        todayIso: String
    ): List<TestNotificationItem> {
        val items = mutableListOf<TestNotificationItem>()
        val seenKeys = mutableSetOf<String>()

        appointments
            .filter { !it.isCanceled && it.isTestOrExam && (it.dateIso.isBlank() || it.dateIso >= todayIso) }
            .forEach { appt ->
                val key = "appt_${appt.id}_${appt.dateIso}"
                if (seenKeys.add(key)) {
                    val subject = appt.subjectName.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: "Toets"
                    val title = appt.homework.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: subject
                    val dateLabel = if (appt.dateIso.isNotBlank()) formatShortDutchDate(appt.dateIso) else "Binnenkort"
                    items.add(TestNotificationItem(key, subject, title, "$dateLabel • ${appt.startTime}"))
                }
            }

        tasks
            .filter { task ->
                !task.isCompleted && (
                    task.category == TaskCategory.EXAM ||
                        task.category == TaskCategory.MAJOR_TEST ||
                        isTestKeywordMatch(task.title) ||
                        isTestKeywordMatch(task.description)
                    ) && (task.dueDay.length != 10 || task.dueDay >= todayIso)
            }
            .forEach { task ->
                val key = "task_${task.id}_${task.dueDay}"
                if (seenKeys.add(key)) {
                    val subject = task.subject.cleanSchoolText() ?: "Toets"
                    val title = task.title.cleanSchoolText() ?: "Toets"
                    val dateLabel = if (task.dueDay.length == 10) formatShortDutchDate(task.dueDay) else task.dueDay
                    items.add(TestNotificationItem(key, subject, title, "$dateLabel • ${task.dueDate}"))
                }
            }

        return items
    }

    internal fun findNewTests(
        appointments: List<Appointment>,
        tasks: List<HomeworkTask>,
        knownTestKeys: Set<String>,
        hasBaseline: Boolean,
        todayIso: String
    ): Pair<List<TestNotificationItem>, Set<String>> {
        val upcoming = collectUpcomingTests(appointments, tasks, todayIso)
        val allKeys = upcoming.map { it.key }.toSet()
        if (!hasBaseline) {
            return emptyList<TestNotificationItem>() to allKeys
        }
        val newlyAdded = upcoming.filter { it.key !in knownTestKeys }
        return newlyAdded to (knownTestKeys + allKeys)
    }

    fun evaluateAllNotifications(
        context: Context,
        previousAppointments: List<Appointment>,
        newAppointments: List<Appointment>,
        newGrades: List<SubjectGradeSummary>,
        newTasks: List<HomeworkTask>,
        nowMinutes: Int = currentClockMinutes()
    ) {
        val store = AppPreferencesStore(context)
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val mutedSubjects = store.getMutedSubjects()

        // 1. Uitval & Roosterwijzigingen (altijd eerst baselines bijwerken)
        val (newlyCanceledRaw, updatedCancelKeys) = findNewlyCanceledLessons(
            appointments = newAppointments,
            previouslyNotifiedKeys = store.getNotifiedCanceledKeys(),
            todayIso = todayIso
        )
        store.setNotifiedCanceledKeys(updatedCancelKeys)

        val (scheduleChangesRaw, updatedChangeKeys) = findScheduleChanges(
            previousAppointments = previousAppointments,
            newAppointments = newAppointments,
            previouslyNotifiedChangeKeys = store.getNotifiedChangeKeys(),
            todayIso = todayIso
        )
        store.setNotifiedChangeKeys(updatedChangeKeys)
        if (previousAppointments.isNotEmpty() && (scheduleChangesRaw.isNotEmpty() || newlyCanceledRaw.isNotEmpty())) {
            val diffsToStore = mutableMapOf<Long, String>()
            scheduleChangesRaw.forEach { alert ->
                diffsToStore[alert.appointment.id] = alert.changeSummary
            }
            newlyCanceledRaw.forEach { appt ->
                diffsToStore[appt.id] = "Uitval"
            }
            store.mergeChangedLessonDiffs(
                newDiffs = diffsToStore,
                activeLessonIds = newAppointments.map { it.id }.toSet()
            )
        }

        // 2. Nieuwe cijfers
        val (newGradeItemsRaw, updatedGradeIds) = findNewGrades(
            newGradeSummaries = newGradeSummariesOrEmpty(newGrades),
            knownGradeIds = store.getKnownGradeIds(),
            hasBaseline = store.hasGradesBaseline()
        )
        if (updatedGradeIds.isNotEmpty() || store.hasGradesBaseline()) {
            store.setKnownGradeIds(updatedGradeIds)
        }

        // 3. Nieuwe toetsen
        val (newTestItemsRaw, updatedTestKeys) = findNewTests(
            appointments = newAppointments,
            tasks = newTasks,
            knownTestKeys = store.getKnownTestKeys(),
            hasBaseline = store.hasTestsBaseline(),
            todayIso = todayIso
        )
        if (updatedTestKeys.isNotEmpty() || store.hasTestsBaseline()) {
            store.setKnownTestKeys(updatedTestKeys)
        }

        // Stille uren: baselines zijn bijgewerkt, maar we sturen nu geen meldingen uit
        if (isQuietHoursActive(store, nowMinutes)) return
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val newlyCanceled = newlyCanceledRaw.filterNot { appt ->
            isSubjectMuted(appt.subjectName, mutedSubjects) || isSubjectMuted(appt.title, mutedSubjects)
        }
        val scheduleChanges = scheduleChangesRaw.filterNot { alert ->
            isSubjectMuted(alert.appointment.subjectName, mutedSubjects) ||
                isSubjectMuted(alert.appointment.title, mutedSubjects)
        }
        val newGradeItems = newGradeItemsRaw.filterNot { grade ->
            isSubjectMuted(grade.subjectName, mutedSubjects) || isSubjectMuted(grade.subjectCode, mutedSubjects)
        }
        val newTestItems = newTestItemsRaw.filterNot { item ->
            isSubjectMuted(item.subject, mutedSubjects)
        }

        if (store.isUitvalNotificationsEnabled()) {
            if (newlyCanceled.isNotEmpty()) {
                if (newlyCanceled.size <= 3) {
                    newlyCanceled.forEach { postSingleCancellationNotification(context, it, todayIso) }
                } else {
                    postSummaryCancellationNotification(context, newlyCanceled, todayIso)
                }
            }
            if (scheduleChanges.isNotEmpty()) {
                scheduleChanges.take(3).forEachIndexed { idx, alert ->
                    postScheduleChangeNotification(context, alert, todayIso, idx)
                }
            }
        }

        if (store.isGradeNotificationsEnabled() && newGradeItems.isNotEmpty()) {
            if (newGradeItems.size <= 3) {
                newGradeItems.forEachIndexed { idx, grade ->
                    postNewGradeNotification(context, grade, idx)
                }
            } else {
                postSummaryGradeNotification(context, newGradeItems)
            }
        }

        if (store.isTestNotificationsEnabled() && newTestItems.isNotEmpty()) {
            if (newTestItems.size <= 3) {
                newTestItems.forEachIndexed { idx, item ->
                    postNewTestNotification(context, item, idx)
                }
            } else {
                postSummaryTestNotification(context, newTestItems)
            }
        }
    }

    private fun newGradeSummariesOrEmpty(list: List<SubjectGradeSummary>): List<SubjectGradeSummary> = list

    fun evaluateAndNotifyCancellations(
        context: Context,
        appointments: List<Appointment>,
        nowMinutes: Int = currentClockMinutes()
    ): List<Appointment> {
        val store = AppPreferencesStore(context)
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val previousKeys = store.getNotifiedCanceledKeys()

        val (newlyCanceledRaw, updatedKeys) = findNewlyCanceledLessons(
            appointments = appointments,
            previouslyNotifiedKeys = previousKeys,
            todayIso = todayIso
        )

        store.setNotifiedCanceledKeys(updatedKeys)

        if (isQuietHoursActive(store, nowMinutes)) {
            return emptyList()
        }

        val mutedSubjects = store.getMutedSubjects()
        val newlyCanceled = newlyCanceledRaw.filterNot { appt ->
            isSubjectMuted(appt.subjectName, mutedSubjects) || isSubjectMuted(appt.title, mutedSubjects)
        }

        if (newlyCanceled.isEmpty() || !store.isUitvalNotificationsEnabled()) {
            return emptyList()
        }

        if (!hasNotificationPermission(context)) {
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

        return newlyCanceled
    }

    fun postSmartBusNotification(
        context: Context,
        title: String,
        body: String,
        nowMinutes: Int = currentClockMinutes()
    ) {
        val store = AppPreferencesStore(context)
        if (!store.isSmartBusAlertsEnabled() || isQuietHoursActive(store, nowMinutes)) return
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, SMART_BUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, 81001))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(81001, notification)
        } catch (_: SecurityException) {
        }
    }

    fun postOrUpdateMorningOngoingNotification(
        context: Context,
        appointments: List<Appointment>,
        busAdvice: com.example.model.SmartBusAdvice?,
        nowMinutes: Int = currentClockMinutes(),
        todayIso: String = SmartBusPlanner.currentTodayIso()
    ) {
        val store = AppPreferencesStore(context)
        if (!store.isMorningLockNotificationEnabled() || nowMinutes !in (6 * 60)..(12 * 60)) {
            cancelMorningOngoingNotification(context)
            return
        }
        val nextLesson = appointments
            .filter { (it.dateIso.isBlank() || it.dateIso == todayIso) && !it.isCanceled }
            .mapNotNull { appt ->
                val startMin = SmartBusPlanner.parseTimeMinutes(appt.startTime) ?: return@mapNotNull null
                if (startMin + 5 < nowMinutes) null else appt to startMin
            }
            .minByOrNull { it.second }

        if (nextLesson == null) {
            cancelMorningOngoingNotification(context)
            return
        }

        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val (appt, startMin) = nextLesson
        val diffMin = (startMin - nowMinutes).coerceAtLeast(0)
        val subject = appt.subjectName.capitalizeSubjectName().ifBlank { "Les" }
        val roomLabel = appt.classroom.cleanSchoolText()?.let { "Lok. $it" } ?: "Lokaal ?"
        val countdownLabel = if (diffMin == 0) "Nu" else "over $diffMin min"
        val title = "$subject $countdownLabel • $roomLabel"

        val busPart = busAdvice?.let { advice ->
            val busStart = SmartBusPlanner.parseTimeMinutes(advice.busDepartureTime)
            val busWait = if (busStart != null) (busStart - nowMinutes).coerceAtLeast(0) else null
            if (advice.commuteMode == com.example.model.CommuteModeType.BIKE) {
                "Fiets vertrek om ${advice.leaveHomeTime} (${advice.rainWarning ?: "droog weer"})"
            } else {
                val waitTxt = if (busWait != null && busWait <= 90) "over $busWait min" else "om ${advice.busDepartureTime}"
                val delayTxt = if (advice.delayMinutes > 0) " (+${advice.delayMinutes}m)" else ""
                "Lijn ${advice.busLine} $waitTxt$delayTxt • ${advice.stopName}"
            }
        } ?: "${appt.periodIndex}e uur (${appt.startTime} - ${appt.endTime})"

        val notification = NotificationCompat.Builder(context, ONGOING_MORNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle(title)
            .setContentText(busPart)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent(context, ONGOING_MORNING_NOTIF_ID))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ONGOING_MORNING_NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelMorningOngoingNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ONGOING_MORNING_NOTIF_ID)
        } catch (_: Exception) {
        }
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

    private fun mainPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, appt.id.toInt()))
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("${canceledList.size} lessen uitgevallen")
            .setContentText("$prefix: $firstFew$extra")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, 77001))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(77001, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postScheduleChangeNotification(
        context: Context,
        alert: ScheduleChangeAlert,
        todayIso: String,
        offset: Int
    ) {
        val appt = alert.appointment
        val subject = appt.subjectName.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: "Les"
        val dayPrefix = if (appt.dateIso == todayIso || appt.dateIso.isBlank()) "Vandaag" else formatShortDutchDate(appt.dateIso)
        val notifId = 78000 + (appt.id.toInt() and 0x0FFF) + offset
        val body = "$dayPrefix (${appt.periodIndex}e uur): ${alert.changeSummary}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("Roosterwijziging: $subject")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, notifId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postNewGradeNotification(
        context: Context,
        grade: GradeItem,
        offset: Int
    ) {
        val notifId = 79000 + (grade.id.hashCode() and 0x0FFF) + offset
        val body = "${grade.description} • Weging ${grade.weight}x (${grade.date})"

        val notification = NotificationCompat.Builder(context, GRADES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("Nieuw cijfer: ${grade.subjectName} — ${grade.score}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, notifId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postSummaryGradeNotification(
        context: Context,
        grades: List<GradeItem>
    ) {
        val summary = grades.take(3).joinToString(", ") { "${it.subjectName}: ${it.score}" }
        val notification = NotificationCompat.Builder(context, GRADES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("${grades.size} nieuwe cijfers in Magister")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, 79999))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(79999, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postNewTestNotification(
        context: Context,
        item: TestNotificationItem,
        offset: Int
    ) {
        val notifId = 80000 + (item.key.hashCode() and 0x0FFF) + offset
        val body = "${item.title} (${item.dateLabel})"

        val notification = NotificationCompat.Builder(context, TESTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("Nieuwe toets: ${item.subject}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, notifId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postSummaryTestNotification(
        context: Context,
        items: List<TestNotificationItem>
    ) {
        val summary = items.take(3).joinToString(", ") { "${it.subject}: ${it.title}" }
        val notification = NotificationCompat.Builder(context, TESTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_uitval)
            .setContentTitle("${items.size} nieuwe toetsen gepland")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(context, 80999))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(80999, notification)
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
