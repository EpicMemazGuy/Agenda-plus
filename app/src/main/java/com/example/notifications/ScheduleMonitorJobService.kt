package com.example.notifications

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.AppPreferencesStore
import com.example.model.capitalizeSubjectName
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
import com.example.util.NetworkStatusHelper
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScheduleMonitorJobService : JobService() {

    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val appContext = applicationContext
        val store = AppPreferencesStore(appContext)
        val session = store.loadSession()
        val tenant = session.school
        val initialToken = session.accessToken
        if (!session.isAuthenticated || tenant == null || initialToken.isNullOrBlank()) {
            return false
        }

        if (!store.isAnyNotificationEnabled() || store.isOfflineModeEnabled() || !NetworkStatusHelper.isOnline(appContext)) {
            return false
        }

        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val previousAppointments = store.loadAppointments()
                val magisterClient = MagisterClient()
                val tokenRes = com.example.network.MagisterTokenManager.getValidAccessToken(
                    context = appContext,
                    forceRefresh = false,
                    client = magisterClient
                )
                val activeToken = when (tokenRes) {
                    is com.example.network.TokenAcquireResult.Valid -> tokenRes.accessToken
                    is com.example.network.TokenAcquireResult.TransientFailure -> tokenRes.fallbackToken ?: initialToken
                    is com.example.network.TokenAcquireResult.RequiresReauth,
                    com.example.network.TokenAcquireResult.NotAuthenticated -> {
                        jobFinished(params, false)
                        return@launch
                    }
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
                UitvalNotificationHelper.evaluateAllNotifications(
                    context = appContext,
                    previousAppointments = previousAppointments,
                    newAppointments = cleaned,
                    newGrades = bundle.grades,
                    newTasks = bundle.tasks
                )
                WidgetUpdater.updateAllWidgets(appContext)
                com.example.services.SyncManager.pushIfChanged(appContext)
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 47006
        private const val INTERVAL_MS = 15L * 60L * 1000L

        fun schedule(context: Context) {
            WorkManagerScheduler.schedule(context)
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return

            val component = ComponentName(context, ScheduleMonitorJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .build()
            scheduler.schedule(jobInfo)
        }

        fun cancel(context: Context) {
            WorkManagerScheduler.cancel(context)
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }
    }
}
