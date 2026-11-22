package com.example.automation

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.db.CustomScheduleItem
import com.example.model.Appointment
import com.example.model.CalendarBridgeConfig
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.cleanSchoolText
import com.example.util.GradeExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarSyncResult(
    val success: Boolean,
    val syncedCount: Int = 0,
    val calendarId: Long? = null,
    val errorMessage: String? = null
)

enum class IcsScope(val labelDutch: String) {
    ACTIVE_WEEK("Actieve week (7 dagen)"),
    FULL_MONTH("Volledige maand (30 dagen)")
}

object CalendarBridgeManager {

    const val ACCOUNT_NAME = "AgendaPlus"
    const val CALENDAR_DISPLAY_NAME = "Agenda+ Rooster"
    private const val TIMEZONE_AMSTERDAM = "Europe/Amsterdam"
    private const val CALENDAR_COLOR_INT = 0xFF1BA1E2.toInt()

    fun hasCalendarPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        return read && write
    }

    private fun syncAdapterCalendarUri(): Uri {
        return CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
    }

    fun findExistingLocalCalendarId(context: Context): Long? {
        if (!hasCalendarPermissions(context)) return null
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val args = arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL)
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    fun findOrCreateLocalCalendarId(context: Context): Long? {
        if (!hasCalendarPermissions(context)) return null
        findExistingLocalCalendarId(context)?.let { return it }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR_INT)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TIMEZONE_AMSTERDAM)
        }
        return runCatching {
            val insertedUri = context.contentResolver.insert(syncAdapterCalendarUri(), values)
            insertedUri?.let { ContentUris.parseId(it) } ?: findExistingLocalCalendarId(context)
        }.getOrNull()
    }

    fun syncUpcomingSchedule(
        context: Context,
        appointments: List<Appointment>,
        config: CalendarBridgeConfig,
        customState: SubjectCustomizationState,
        nowMs: Long = System.currentTimeMillis()
    ): CalendarSyncResult {
        if (!hasCalendarPermissions(context)) {
            return CalendarSyncResult(
                success = false,
                errorMessage = "Agendatoegang ontbreekt. Geef toestemming om het rooster te synchroniseren."
            )
        }
        val calendarId = findOrCreateLocalCalendarId(context)
            ?: return CalendarSyncResult(
                success = false,
                errorMessage = "Kon geen lokaal Agenda+ roosteraccount aanmaken op dit apparaat."
            )

        val tz = TimeZone.getTimeZone(TIMEZONE_AMSTERDAM)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }

        val windowStartCal = Calendar.getInstance(tz).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val windowStartMs = windowStartCal.timeInMillis
        val windowEndCal = (windowStartCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 14)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val windowEndMs = windowEndCal.timeInMillis
        val startDateIso = isoFormat.format(Date(windowStartMs))
        val endDateIso = isoFormat.format(Date(windowEndMs))

        return runCatching {
            // Remove previous mirrored Agenda+ events in this 14-day window to keep idempotent
            val deleteSelection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val deleteArgs = arrayOf(calendarId.toString(), windowStartMs.toString(), windowEndMs.toString())
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, deleteSelection, deleteArgs)

            val filtered = appointments.filter { appt ->
                appt.dateIso.isNotBlank() &&
                    appt.dateIso >= startDateIso &&
                    appt.dateIso <= endDateIso &&
                    !(config.hideCanceledLessons && appt.isCanceled)
            }

            var insertedCount = 0
            for (appt in filtered) {
                val startMs = parseAmsterdamDateTimeMs(appt.dateIso, appt.startTime, tz) ?: continue
                val endMs = parseAmsterdamDateTimeMs(appt.dateIso, appt.endTime, tz)
                    ?.takeIf { it > startMs }
                    ?: (startMs + 45 * 60_000L)

                val resolved = SubjectCustomizationCatalog.resolveSubject(
                    rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                    rawTitle = appt.title,
                    explicitCode = appt.subjectCode,
                    state = customState
                )
                val title = buildString {
                    if (appt.isCanceled) append("[UITVAL] ")
                    else if (appt.isTestOrExam) append("[TOETS] ")
                    append(resolved.displayName)
                }
                val room = appt.classroom.cleanSchoolText()
                val location = if (room != null) "Lokaal $room" else ""
                val description = buildString {
                    append("${appt.periodIndex}e uur (${appt.startTime} - ${appt.endTime})")
                    appt.teacherName.cleanSchoolText()?.let { append("\nDocent: $it") }
                    appt.homework.cleanSchoolText()?.let { append("\nHuiswerk: $it") }
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TIMEZONE_AMSTERDAM)
                    if (appt.isCanceled) {
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                    } else {
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    }
                }
                if (context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) {
                    insertedCount++
                }
            }

            CalendarSyncResult(
                success = true,
                syncedCount = insertedCount,
                calendarId = calendarId
            )
        }.getOrElse { err ->
            CalendarSyncResult(
                success = false,
                errorMessage = err.message ?: "Kon agenda niet bijwerken."
            )
        }
    }

    fun clearMirroredCalendar(context: Context): Boolean {
        if (!hasCalendarPermissions(context)) return false
        val calendarId = findExistingLocalCalendarId(context) ?: return true
        return runCatching {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString())
            )
            context.contentResolver.delete(
                syncAdapterCalendarUri(),
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString())
            )
            true
        }.getOrDefault(false)
    }

    fun buildIcsContent(
        appointments: List<Appointment>,
        customItems: List<CustomScheduleItem>,
        scope: IcsScope,
        customState: SubjectCustomizationState,
        hideCanceled: Boolean = true,
        referenceDateIso: String? = null
    ): String {
        val tz = TimeZone.getTimeZone(TIMEZONE_AMSTERDAM)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val anchorCal = Calendar.getInstance(tz).apply {
            if (!referenceDateIso.isNullOrBlank()) {
                runCatching { isoFormat.parse(referenceDateIso) }.getOrNull()?.let { time = it }
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startCal = (anchorCal.clone() as Calendar).apply {
            if (scope == IcsScope.ACTIVE_WEEK) {
                firstDayOfWeek = Calendar.MONDAY
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            }
        }
        val dayCount = if (scope == IcsScope.ACTIVE_WEEK) 7 else 30
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayCount - 1)
        }
        val startIso = isoFormat.format(startCal.time)
        val endIso = isoFormat.format(endCal.time)
        val dtStamp = utcFormat.format(Date())

        val sb = StringBuilder()
        fun line(text: String) {
            sb.append(text).append("\r\n")
        }

        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Agenda+//Schoolrooster//NL")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        line("X-WR-CALNAME:Agenda+ Schoolrooster")
        line("X-WR-TIMEZONE:$TIMEZONE_AMSTERDAM")

        val relevantAppts = appointments
            .filter { appt ->
                appt.dateIso.isNotBlank() &&
                    appt.dateIso >= startIso &&
                    appt.dateIso <= endIso &&
                    !(hideCanceled && appt.isCanceled)
            }
            .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.periodIndex }))

        for (appt in relevantAppts) {
            val startMs = parseAmsterdamDateTimeMs(appt.dateIso, appt.startTime, tz) ?: continue
            val endMs = parseAmsterdamDateTimeMs(appt.dateIso, appt.endTime, tz)
                ?.takeIf { it > startMs }
                ?: (startMs + 45 * 60_000L)

            val resolved = SubjectCustomizationCatalog.resolveSubject(
                rawSubject = appt.subjectName.cleanSchoolText() ?: appt.title,
                rawTitle = appt.title,
                explicitCode = appt.subjectCode,
                state = customState
            )
            val summary = buildString {
                if (appt.isCanceled) append("[UITVAL] ")
                else if (appt.isTestOrExam) append("[TOETS] ")
                append(resolved.displayName)
            }
            val room = appt.classroom.cleanSchoolText()
            val location = if (room != null) "Lokaal $room" else ""
            val description = buildString {
                append("${appt.periodIndex}e uur (${appt.startTime} - ${appt.endTime})")
                appt.teacherName.cleanSchoolText()?.let { append("\nDocent: $it") }
                appt.homework.cleanSchoolText()?.let { append("\nHuiswerk: $it") }
            }

            line("BEGIN:VEVENT")
            line("UID:agendaplus-${appt.id}-${appt.dateIso}@agendaplus.nl")
            line("DTSTAMP:$dtStamp")
            line("DTSTART:${utcFormat.format(Date(startMs))}")
            line("DTEND:${utcFormat.format(Date(endMs))}")
            line("SUMMARY:${escapeIcsText(summary)}")
            if (location.isNotBlank()) line("LOCATION:${escapeIcsText(location)}")
            line("DESCRIPTION:${escapeIcsText(description)}")
            line(if (appt.isCanceled) "STATUS:CANCELLED" else "STATUS:CONFIRMED")
            line("END:VEVENT")
        }

        // Include custom schedule items across the selected window
        val dayCursor = startCal.clone() as Calendar
        for (d in 0 until dayCount) {
            val dayIso = isoFormat.format(dayCursor.time)
            val dayOfWeek = when (dayCursor.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 7
            }
            customItems.filter { it.appliesToDate(dayIso, dayOfWeek) }.forEach { item ->
                val startMs = parseAmsterdamDateTimeMs(dayIso, item.startTime, tz) ?: return@forEach
                val endMs = parseAmsterdamDateTimeMs(dayIso, item.endTime, tz)
                    ?.takeIf { it > startMs }
                    ?: (startMs + 45 * 60_000L)
                line("BEGIN:VEVENT")
                line("UID:agendaplus-custom-${item.id}-$dayIso@agendaplus.nl")
                line("DTSTAMP:$dtStamp")
                line("DTSTART:${utcFormat.format(Date(startMs))}")
                line("DTEND:${utcFormat.format(Date(endMs))}")
                line("SUMMARY:${escapeIcsText(item.title)}")
                if (item.description.isNotBlank()) {
                    line("DESCRIPTION:${escapeIcsText(item.description)}")
                }
                line("STATUS:CONFIRMED")
                line("END:VEVENT")
            }
            dayCursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        line("END:VCALENDAR")
        return sb.toString()
    }

    fun exportIcsFile(
        context: Context,
        appointments: List<Appointment>,
        customItems: List<CustomScheduleItem>,
        scope: IcsScope,
        customState: SubjectCustomizationState,
        hideCanceled: Boolean = true,
        referenceDateIso: String? = null
    ): File {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val suffix = if (scope == IcsScope.ACTIVE_WEEK) "week" else "maand"
        val outFile = File(dir, "agendaplus_rooster_${suffix}_${System.currentTimeMillis()}.ics")
        val content = buildIcsContent(
            appointments = appointments,
            customItems = customItems,
            scope = scope,
            customState = customState,
            hideCanceled = hideCanceled,
            referenceDateIso = referenceDateIso
        )
        outFile.writeText(content, Charsets.UTF_8)
        GradeExporter.shareFileViaProvider(
            context = context,
            file = outFile,
            mimeType = "text/calendar",
            chooserTitle = "Exporteer rooster (.ics)"
        )
        return outFile
    }

    internal fun parseAmsterdamDateTimeMs(
        dateIso: String,
        hhmm: String,
        tz: TimeZone = TimeZone.getTimeZone(TIMEZONE_AMSTERDAM)
    ): Long? {
        val dateParts = dateIso.trim().split("-")
        val timeParts = hhmm.trim().split(":")
        if (dateParts.size != 3 || timeParts.size < 2) return null
        val y = dateParts[0].toIntOrNull() ?: return null
        val m = dateParts[1].toIntOrNull() ?: return null
        val d = dateParts[2].toIntOrNull() ?: return null
        val h = timeParts[0].toIntOrNull() ?: return null
        val min = timeParts[1].toIntOrNull() ?: return null
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(y, m - 1, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    internal fun escapeIcsText(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }
}
