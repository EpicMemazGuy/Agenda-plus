package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.automation.CalendarBridgeManager
import com.example.automation.LessonDndScheduler
import com.example.data.AppPreferencesStore
import com.example.hardware.NothingGlyphBridge
import com.example.hardware.WearSyncBridge
import com.example.model.BusWidgetSnapshot
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.model.CommuteModeType
import com.example.model.capitalizeSubjectName
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
import com.example.network.MagisterTokenManager
import com.example.network.TokenAcquireResult
import com.example.network.TransitClient
import com.example.network.WeatherClient
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
        if (!session.isAuthenticated || tenant == null) {
            return Result.success()
        }

        if (store.isOfflineModeEnabled() || !NetworkStatusHelper.isOnline(applicationContext)) {
            return Result.retry()
        }

        return try {
            val previousAppointments = store.loadAppointments()
            val magisterClient = MagisterClient()
            val tokenResult = MagisterTokenManager.getValidAccessToken(
                context = applicationContext,
                forceRefresh = false,
                client = magisterClient
            )
            val activeToken = when (tokenResult) {
                is TokenAcquireResult.Valid -> tokenResult.accessToken
                is TokenAcquireResult.TransientFailure -> tokenResult.fallbackToken ?: return Result.retry()
                is TokenAcquireResult.RequiresReauth, TokenAcquireResult.NotAuthenticated -> return Result.success()
            }

            val latestSession = store.loadSession()
            val bundle = magisterClient.fetchAllMagisterData(tenant, activeToken, latestSession.personId)
            val cleaned = bundle.appointments.map { appt ->
                appt.copy(
                    title = appt.title.cleanSchoolText() ?: "Les",
                    subjectName = appt.subjectName.capitalizeSubjectName().ifBlank { "Les" },
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

            val commuteModes = store.getCommuteModes()
            val activeId = store.getActiveCommuteModeId()
            val activeMode = commuteModes.firstOrNull { it.id == activeId } ?: commuteModes.firstOrNull()
            val savedBusLine = when {
                activeMode?.type == CommuteModeType.BUS && activeMode.busLine.isNotBlank() -> activeMode.busLine
                else -> store.getSavedBusLine()
            }

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

            val weatherLoc = store.getWeatherLocation()
            val weatherInfo = runCatching {
                WeatherClient().fetchCurrentWeather(weatherLoc.cityName, weatherLoc.latitude, weatherLoc.longitude)
            }.getOrNull()

            val advice = SmartBusPlanner.computeAdvice(
                todayAppointments = cleaned,
                previousAppointments = previousAppointments,
                busTrackingState = busTracking,
                walkMinutes = store.getBusWalkMinutes(),
                arrivalBufferMinutes = store.getBusArrivalBufferMinutes(),
                savedBusLine = savedBusLine,
                activeCommuteMode = activeMode,
                weatherInfo = weatherInfo
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
                    title = "Slimme Reismelding",
                    body = body
                )
            }

            UitvalNotificationHelper.postOrUpdateMorningOngoingNotification(
                context = applicationContext,
                appointments = cleaned,
                busAdvice = advice
            )

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
            val calConfig = store.getCalendarBridgeConfig()
            if (calConfig.enabled && CalendarBridgeManager.hasCalendarPermissions(applicationContext)) {
                val customState = SubjectCustomizationState(
                    customizations = store.loadSubjectCustomizations(),
                    smartDefaultNamesEnabled = store.isSmartSubjectNamesEnabled(),
                    useSubjectColorsForMetroTiles = store.isUseSubjectColorsForMetroTiles()
                )
                val res = CalendarBridgeManager.syncUpcomingSchedule(
                    context = applicationContext,
                    appointments = cleaned,
                    config = calConfig,
                    customState = customState
                )
                if (res.success) {
                    store.saveCalendarBridgeConfig(
                        calConfig.copy(
                            lastSyncedAtMs = System.currentTimeMillis(),
                            lastSyncedEventCount = res.syncedCount
                        )
                    )
                }
            }
            LessonDndScheduler.rescheduleAlarms(
                context = applicationContext,
                appointments = cleaned,
                config = store.getLessonDndConfig()
            )
            WidgetUpdater.updateAllWidgets(applicationContext)
            com.example.services.SyncManager.pushIfChanged(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/**
 * 12-hour keep-alive worker so rotating Magister OAuth2 refresh tokens stay valid
 * across weekends, school holidays, and periods when notifications are toggled off.
 */
class TokenKeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val store = AppPreferencesStore(applicationContext)
        val session = store.loadSession()
        if (!session.isAuthenticated || session.refreshToken.isNullOrBlank()) {
            return Result.success()
        }
        if (store.isOfflineModeEnabled() || !NetworkStatusHelper.isOnline(applicationContext)) {
            return Result.retry()
        }

        return when (MagisterTokenManager.getValidAccessToken(applicationContext, forceRefresh = true)) {
            is TokenAcquireResult.Valid -> Result.success()
            is TokenAcquireResult.TransientFailure -> Result.retry()
            is TokenAcquireResult.RequiresReauth, TokenAcquireResult.NotAuthenticated -> Result.success()
        }
    }
}

object WorkManagerScheduler {
    private const val UNIQUE_WORK_NAME = "agenda_plus_background_sync"
    private const val UNIQUE_KEEP_ALIVE_WORK_NAME = "agenda_plus_token_keep_alive"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        val keepAliveRequest = PeriodicWorkRequestBuilder<TokenKeepAliveWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        runCatching {
            val wm = WorkManager.getInstance(appContext)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            wm.enqueueUniquePeriodicWork(
                UNIQUE_KEEP_ALIVE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                keepAliveRequest
            )
        }
    }

    fun scheduleKeepAliveOnly(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val keepAliveRequest = PeriodicWorkRequestBuilder<TokenKeepAliveWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_KEEP_ALIVE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                keepAliveRequest
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(UNIQUE_KEEP_ALIVE_WORK_NAME)
        }
    }
}
