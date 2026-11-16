package com.example.hardware

import android.content.Context
import com.example.model.Appointment
import com.example.model.BusTrackingState
import com.example.model.SmartBusAdvice
import com.example.notifications.SmartBusPlanner
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * Best-effort Wear OS Data Layer sync bridge pushing next/current lesson and live bus summary
 * to `/agenda_plus/status`. Fails silently on devices without Wear Play Services.
 */
object WearSyncBridge {

    const val DATA_PATH = "/agenda_plus/status"

    fun buildStatusJson(
        appointments: List<Appointment>,
        busTracking: BusTrackingState?,
        smartBusAdvice: SmartBusAdvice?,
        nowMinutes: Int = SmartBusPlanner.currentClockMinutes(),
        todayIso: String = SmartBusPlanner.currentTodayIso()
    ): String {
        val todayActive = appointments.filter {
            !it.isCanceled && (todayIso.isBlank() || it.dateIso.isBlank() || it.dateIso == todayIso)
        }.sortedBy { SmartBusPlanner.parseTimeMinutes(it.startTime) ?: Int.MAX_VALUE }

        val currentOrNext = todayActive.firstOrNull { appt ->
            val end = SmartBusPlanner.parseTimeMinutes(appt.endTime) ?: return@firstOrNull false
            end >= nowMinutes
        }

        val nearestStop = busTracking?.nearestStop
        return JSONObject().apply {
            put("updatedAt", System.currentTimeMillis())
            put("lessonTitle", currentOrNext?.subjectName ?: "")
            put("lessonTime", currentOrNext?.let { "${it.startTime}-${it.endTime}" } ?: "")
            put("classroom", currentOrNext?.classroom ?: "")
            put("busLine", busTracking?.lineNumber ?: smartBusAdvice?.busLine ?: "")
            put("busStop", nearestStop?.name ?: smartBusAdvice?.stopName ?: "")
            put("busDeparture", smartBusAdvice?.busDepartureTime ?: nearestStop?.expectedArrival ?: "")
            put("leaveHomeTime", smartBusAdvice?.leaveHomeTime ?: "")
        }.toString()
    }

    fun pushStatus(
        context: Context,
        appointments: List<Appointment>,
        busTracking: BusTrackingState?,
        smartBusAdvice: SmartBusAdvice?
    ) {
        val json = buildStatusJson(appointments, busTracking, smartBusAdvice)
        runCatching {
            val req = PutDataMapRequest.create(DATA_PATH).apply {
                dataMap.putString("payload", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(req)
        }
    }
}
