package com.example.hardware

import android.content.Context
import android.os.Build
import com.example.model.Appointment
import com.example.model.BusTrackingState
import com.example.model.SmartBusAdvice
import com.example.notifications.SmartBusPlanner
import kotlin.math.roundToInt

/**
 * Optional Nothing Phone (1)/(2)/(2a) Glyph progress indicator bridge.
 * Uses reflection so the app builds cleanly and acts as a safe no-op on non-Nothing devices.
 */
object NothingGlyphBridge {

    fun isNothingDevice(): Boolean =
        Build.MANUFACTURER.equals("Nothing", ignoreCase = true)

    fun computeCountdownProgress(
        nowMinutes: Int,
        targetMinutes: Int,
        windowMinutes: Int = 15
    ): Int? {
        val remaining = targetMinutes - nowMinutes
        if (remaining <= 0 || remaining > windowMinutes) return null
        return ((remaining.toFloat() / windowMinutes.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(1, 100)
    }

    fun selectActiveCountdownProgress(
        appointments: List<Appointment>,
        busAdvice: SmartBusAdvice?,
        busTracking: BusTrackingState?,
        nowMinutes: Int = SmartBusPlanner.currentClockMinutes(),
        todayIso: String = SmartBusPlanner.currentTodayIso()
    ): Int? {
        val todayActive = appointments.filter {
            !it.isCanceled && (todayIso.isBlank() || it.dateIso.isBlank() || it.dateIso == todayIso)
        }
        val activeLessonEnd = todayActive.firstOrNull { appt ->
            val start = SmartBusPlanner.parseTimeMinutes(appt.startTime) ?: return@firstOrNull false
            val end = SmartBusPlanner.parseTimeMinutes(appt.endTime) ?: return@firstOrNull false
            nowMinutes in start..end
        }?.let { SmartBusPlanner.parseTimeMinutes(it.endTime) }

        activeLessonEnd?.let { endMin ->
            computeCountdownProgress(nowMinutes, endMin)?.let { return it }
        }

        val busTargetMin = SmartBusPlanner.parseTimeMinutes(
            busAdvice?.busDepartureTime ?: busTracking?.nearestStop?.expectedArrival
        )
        if (busTargetMin != null) {
            computeCountdownProgress(nowMinutes, busTargetMin)?.let { return it }
        }
        return null
    }

    fun updateGlyphCountdown(
        context: Context,
        appointments: List<Appointment>,
        busAdvice: SmartBusAdvice?,
        busTracking: BusTrackingState?,
        nowMinutes: Int = SmartBusPlanner.currentClockMinutes(),
        todayIso: String = SmartBusPlanner.currentTodayIso()
    ) {
        if (!isNothingDevice()) return
        val progress = selectActiveCountdownProgress(
            appointments = appointments,
            busAdvice = busAdvice,
            busTracking = busTracking,
            nowMinutes = nowMinutes,
            todayIso = todayIso
        )
        runCatching {
            val clazz = Class.forName("com.nothing.ketchum.GlyphManager")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val manager = getInstance.invoke(null, context.applicationContext) ?: return
            if (progress == null) {
                runCatching { clazz.getMethod("turnOff").invoke(manager) }
            } else {
                val frameBuilderClass = Class.forName("com.nothing.ketchum.GlyphFrame\$Builder")
                val builder = frameBuilderClass.getDeclaredConstructor().newInstance()
                val buildMethod = frameBuilderClass.getMethod("build")
                val frame = buildMethod.invoke(builder)
                val displayMethod = clazz.methods.firstOrNull { it.name == "displayProgress" && it.parameterCount == 2 }
                if (displayMethod != null && frame != null) {
                    displayMethod.invoke(manager, frame, progress)
                }
            }
        }
    }
}
