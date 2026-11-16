package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppPreferencesStore
import com.example.hardware.NothingGlyphBridge
import com.example.hardware.WearSyncBridge
import com.example.model.BusWidgetSnapshot
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
import com.example.network.TransitClient
import com.example.util.NetworkStatusHelper
import com.example.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

class ScheduleSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val store = AppPreferencesStore(applicationContext)
        val session = store.loadSession()
        val tenant = session.school
        val initialToken = session.accessToken
        if (!session.isAuthenticated || tenant == null || initialToken.isNullOrBlank()) {
            return Result.success()
        }

        if (store.isOfflineModeEnabled() || !NetworkStatusHelper.isOnline(applicationContext)) {
            return Result.retry()
        }

        return try {
            val previousAppointments = store.loadAppointments()
            val magisterClient = MagisterClient()
            var activeToken: String = initialToken

            if (magisterClient.isAccessTokenExpired(activeToken)) {
                val refresh = session.refreshToken
                if (!refresh.isNullOrBlank()) {
                    val refreshed = magisterClient.refreshAccessToken(refresh)
                    if (refreshed != null) {
                        activeToken = refreshed.first
                        store.saveSession(
                            session.copy(
                                accessToken = refreshed.first,
                                refreshToken = refreshed.second ?: refresh
                            )
                        )
                    }
                }
            }

            val bundle = magisterClient.fetchAllMagisterData(tenant, activeToken, session.personId)
            val cleaned = bundle.appointments.map { appt ->
                appt.copy(
                    title = appt.title.cleanSchoolText() ?: "Les",
                    subjectName = appt.subjectName.cleanSchoolText() ?: "Les",
                    teacherName = appt.teacherName.cleanSchoolText() ?: "",
                    teacherInitials = appt.teacherInitials.cleanSchoolText() ?: "",
                    classroom = appt.classroom.cleanSchoolText() ?: "",
                    originalClassroom = appt.originalClassroom.cleanSchoolText(),
                    homework = appt.homework.cleanSchoolText()
                )
            }

            store.saveAppointments(cleaned)
            store.saveGrades(bundle.grades)
            store.saveTasks(bundle.tasks)
            store.saveMessages(bundle.messages)
            store.saveStudyGuides(bundle.studyGuides)
            store.saveKwtSlots(bundle.kwtSlots)
            store.saveAbsences(bundle.absences)

            if (store.isAnyNotificationEnabled()) {
                UitvalNotificationHelper.evaluateAllNotifications(
                    context = applicationContext,
                    previousAppointments = previousAppointments,
                    newAppointments = cleaned,
                    newGrades = bundle.grades,
                    newTasks = bundle.tasks
                )
            }

            val savedBusLine = store.getSavedBusLine()
            val busTracking = if (savedBusLine.isNotBlank()) {
                val schoolAddr = store.loadSchoolAddress()
                val cityHint = listOfNotNull(
                    tenant.city,
                    tenant.name,
                    schoolAddr?.streetAndCity,
                    schoolAddr?.displayName,
                    store.getWeatherLocation().cityName
                ).joinToString(" ")
                TransitClient(store).fetchLiveBusTracking(savedBusLine, cityHint, null, null)
            } else null

            val advice = SmartBusPlanner.computeAdvice(
                todayAppointments = cleaned,
                previousAppointments = previousAppointments,
                busTrackingState = busTracking,
                walkMinutes = store.getBusWalkMinutes(),
                arrivalBufferMinutes = store.getBusArrivalBufferMinutes(),
                savedBusLine = savedBusLine
            )

            if (busTracking != null) {
                val nearest = busTracking.nearestStop
                store.saveBusWidgetSnapshot(
                    BusWidgetSnapshot(
                        busLine = busTracking.lineNumber,
                        stopName = nearest?.name ?: "",
                        expectedTime = nearest?.expectedArrival ?: advice?.busDepartureTime ?: "",
                        delayMinutes = nearest?.delayMinutes ?: 0,
                        smartAdviceText = advice?.let { "Vertrek ${it.leaveHomeTime} • Lijn ${it.busLine}" } ?: "",
                        updatedLabel = busTracking.lastUpdatedLabel
                    )
                )
            }

            if (advice != null && advice.shouldNotifyPriority && advice.alertKey != store.getLastSmartBusAlertKey()) {
                store.setLastSmartBusAlertKey(advice.alertKey)
                val body = advice.shiftReasonMessage
                    ?: "Vertrek om ${advice.leaveHomeTime} voor lijn ${advice.busLine} (${advice.busDepartureTime})."
                UitvalNotificationHelper.postSmartBusNotification(
                    context = applicationContext,
                    title = "Slimme Busmelding: Lijn ${advice.busLine}",
                    body = body
                )
            }

            NothingGlyphBridge.updateGlyphCountdown(
                context = applicationContext,
                appointments = cleaned,
                busAdvice = advice,
                busTracking = busTracking
            )
            WearSyncBridge.pushStatus(
                context = applicationContext,
                appointments = cleaned,
                busTracking = busTracking,
                smartBusAdvice = advice
            )
            WidgetUpdater.updateAllWidgets(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object WorkManagerScheduler {
    private const val UNIQUE_WORK_NAME = "agenda_plus_background_sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
