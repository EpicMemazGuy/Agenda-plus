package com.example.notifications

import com.example.model.Appointment
import com.example.model.BusTrackingState
import com.example.model.CommuteModeConfig
import com.example.model.CommuteModeType
import com.example.model.SmartBusAdvice
import com.example.model.WeatherInfo
import com.example.model.capitalizeSubjectName
import com.example.network.WeatherClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SmartBusPlanner {

    private const val ESTIMATED_RIDE_MINUTES = 10

    fun currentClockMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun currentTodayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun parseTimeMinutes(hhmm: String?): Int? {
        val clean = hhmm?.trim()?.take(5) ?: return null
        val parts = clean.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatMinutes(totalMinutes: Int): String {
        val wrapped = ((totalMinutes % 1440) + 1440) % 1440
        val h = wrapped / 60
        val m = wrapped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun computeAdvice(
        todayAppointments: List<Appointment>,
        previousAppointments: List<Appointment>,
        busTrackingState: BusTrackingState?,
        walkMinutes: Int,
        arrivalBufferMinutes: Int,
        nowMinutes: Int = currentClockMinutes(),
        savedBusLine: String = busTrackingState?.lineNumber.orEmpty(),
        todayIso: String = currentTodayIso(),
        activeCommuteMode: CommuteModeConfig? = null,
        weatherInfo: WeatherInfo? = null
    ): SmartBusAdvice? {
        val todayOnly = todayAppointments.filter {
            todayIso.isBlank() || it.dateIso.isBlank() || it.dateIso == todayIso
        }
        val firstActive = todayOnly
            .filter { !it.isCanceled && parseTimeMinutes(it.startTime) != null }
            .minByOrNull { parseTimeMinutes(it.startTime)!! }
            ?: return null

        val firstStartMin = parseTimeMinutes(firstActive.startTime) ?: return null
        if (nowMinutes > firstStartMin) return null

        val earlierCanceledToday = todayOnly
            .filter { it.isCanceled && (parseTimeMinutes(it.startTime) ?: Int.MAX_VALUE) < firstStartMin }
            .minByOrNull { parseTimeMinutes(it.startTime) ?: Int.MAX_VALUE }

        val earliestOriginalStart = earlierCanceledToday?.let { parseTimeMinutes(it.startTime) }
        val longerSleepMinutes = if (earliestOriginalStart != null && firstStartMin > earliestOriginalStart) {
            firstStartMin - earliestOriginalStart
        } else 0

        val hadEarlierActivePreviously = previousAppointments.any { prev ->
            (todayIso.isBlank() || prev.dateIso.isBlank() || prev.dateIso == todayIso) &&
                !prev.isCanceled &&
                (parseTimeMinutes(prev.startTime) ?: Int.MAX_VALUE) < firstStartMin
        }

        // Bike commute mode calculation (doesn't require OVapi stop)
        if (activeCommuteMode?.type == CommuteModeType.BIKE) {
            val bikeDuration = activeCommuteMode.bikeMinutes.coerceIn(5, 90)
            val leaveMin = (firstStartMin - bikeDuration - arrivalBufferMinutes).coerceAtLeast(0)
            val leaveTime = formatMinutes(leaveMin)
            val rainWindow = WeatherClient.evaluateRainForWindow(weatherInfo, leaveMin, bikeDuration)
            val shiftMessage = earlierCanceledToday?.let { canceled ->
                val hourLabel = if (canceled.periodIndex > 0) "${canceled.periodIndex}e uur" else "Eerste les"
                if (longerSleepMinutes > 0) {
                    "$hourLabel uitval — $longerSleepMinutes min langer slapen! Fiets vertrek om $leaveTime."
                } else {
                    "$hourLabel uitgevallen! Fiets om $leaveTime voor ${firstActive.subjectName.capitalizeSubjectName()}."
                }
            }
            val alertKey = "${todayIso}_BIKE_${firstActive.id}_${leaveTime}_${earlierCanceledToday?.periodIndex ?: 0}"
            return SmartBusAdvice(
                busLine = activeCommuteMode.label.ifBlank { "Fiets" },
                stopName = "${bikeDuration} min fietsen",
                busDepartureTime = firstActive.startTime,
                leaveHomeTime = leaveTime,
                firstActiveLesson = firstActive,
                shiftReasonMessage = shiftMessage,
                alertKey = alertKey,
                shouldNotifyPriority = earlierCanceledToday != null && hadEarlierActivePreviously,
                commuteMode = CommuteModeType.BIKE,
                delayMinutes = 0,
                rainWarning = rainWindow.summaryDutch.takeIf { it.isNotBlank() },
                longerSleepMinutes = longerSleepMinutes
            )
        }

        val tracking = busTrackingState ?: return null
        val modeLine = activeCommuteMode?.busLine?.trim().orEmpty()
        val line = modeLine.ifBlank { savedBusLine.trim() }.ifBlank { tracking.lineNumber.trim() }
        if (line.isBlank()) return null

        val stop = tracking.nearestStop ?: tracking.stops.firstOrNull() ?: return null

        val candidates = buildList {
            stop.upcomingDepartures.forEach { dep ->
                val t = dep.expectedTime.trim().take(5)
                parseTimeMinutes(t)?.let { add(t to it) }
            }
            if (isEmpty()) {
                val fallback = stop.expectedArrival?.trim()?.take(5)
                if (!fallback.isNullOrBlank()) {
                    parseTimeMinutes(fallback)?.let { add(fallback to it) }
                }
            }
        }.distinctBy { it.first }.sortedBy { it.second }

        if (candidates.isEmpty()) return null

        val latestAcceptableBusMin = firstStartMin - arrivalBufferMinutes - ESTIMATED_RIDE_MINUTES
        val catchable = candidates.filter { it.second >= nowMinutes + walkMinutes }
        val chosen = catchable.filter { it.second <= latestAcceptableBusMin }.maxByOrNull { it.second }
            ?: catchable.minByOrNull { it.second }
            ?: candidates.filter { it.second <= latestAcceptableBusMin }.maxByOrNull { it.second }
            ?: candidates.first()

        val leaveHomeMin = (chosen.second - walkMinutes).coerceAtLeast(0)
        val leaveHomeTime = formatMinutes(leaveHomeMin)

        val rainWindow = WeatherClient.evaluateRainForWindow(weatherInfo, leaveHomeMin, walkMinutes + 10)
        val delayMin = stop.delayMinutes.coerceAtLeast(0)

        val shiftMessage = when {
            earlierCanceledToday != null -> {
                val hourLabel = if (earlierCanceledToday.periodIndex > 0) "${earlierCanceledToday.periodIndex}e uur" else "Eerste les"
                if (longerSleepMinutes > 0 && activeCommuteMode != null) {
                    "$hourLabel uitval — je kunt $longerSleepMinutes min langer slapen, pak bus $line om ${chosen.first}."
                } else {
                    "$hourLabel uitgevallen! Pak in plaats daarvan de bus van ${chosen.first}."
                }
            }
            delayMin >= 4 -> "Lijn $line heeft +$delayMin min vertraging (${chosen.first})."
            else -> null
        }

        val alertKey = "${todayIso}_${firstActive.id}_${chosen.first}_${earlierCanceledToday?.periodIndex ?: 0}_${delayMin}"

        return SmartBusAdvice(
            busLine = line,
            stopName = stop.name,
            busDepartureTime = chosen.first,
            leaveHomeTime = leaveHomeTime,
            firstActiveLesson = firstActive,
            shiftReasonMessage = shiftMessage,
            alertKey = alertKey,
            shouldNotifyPriority = (earlierCanceledToday != null && hadEarlierActivePreviously) || delayMin >= 5,
            commuteMode = CommuteModeType.BUS,
            delayMinutes = delayMin,
            rainWarning = rainWindow.summaryDutch.takeIf { it.isNotBlank() },
            longerSleepMinutes = longerSleepMinutes
        )
    }
}
