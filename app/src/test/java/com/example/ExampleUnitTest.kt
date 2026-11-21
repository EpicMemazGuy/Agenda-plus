package com.example

import com.example.data.AgendaRepository
import com.example.model.AppThemeStyle
import com.example.model.AgendaStyle
import com.example.model.SchoolProvider
import com.example.model.UiScalePreset
import com.example.model.capitalizeSubjectName
import com.example.model.cleanSchoolText
import com.example.network.PkceHelper
import com.example.ui.AppTab
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.round

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun test_pkce_generation() {
        val verifier = PkceHelper.generateVerifier()
        val challenge = PkceHelper.generateChallenge(verifier)
        assertTrue(verifier.isNotBlank())
        assertTrue(challenge.isNotBlank())
        assertTrue(verifier.length >= 43)
    }

    @Test
    fun test_what_if_grade_calculation() {
        val currentSum = 7.8 * 2.0
        val currentWeight = 2.0
        val target = 6.0
        val testWeight = 2.0
        val needed = (target * (currentWeight + testWeight) - currentSum) / testWeight
        val rounded = round(needed * 10) / 10.0
        assertEquals(4.2, rounded, 0.01)
    }

    @Test
    fun test_repository_initial_state_unauthenticated() {
        val repo = AgendaRepository()
        assertFalse("App must start unauthenticated on first download", repo.currentSession.value.isAuthenticated)
        assertTrue(repo.appointments.value.isEmpty())
        assertTrue(repo.gradeSummaries.value.isEmpty())
        assertTrue(repo.tasks.value.isEmpty())
        assertEquals(AgendaStyle.COMPACT, repo.agendaStyle.value)
        assertEquals(AppThemeStyle.STANDARD, repo.appThemeStyle.value)
        assertTrue(repo.studySites.value.isNotEmpty())

        repo.setAppThemeStyle(AppThemeStyle.NOTHING_OS)
        assertEquals(AppThemeStyle.NOTHING_OS, repo.appThemeStyle.value)
        repo.setAppThemeStyle(AppThemeStyle.STANDARD)
        assertEquals(AppThemeStyle.STANDARD, repo.appThemeStyle.value)
    }

    @Test
    fun test_school_providers_available() {
        val repo = AgendaRepository()
        val magisterSchools = repo.getTenantsForProvider(SchoolProvider.MAGISTER)

        assertEquals(1, SchoolProvider.entries.size)
        assertTrue(magisterSchools.isNotEmpty())
        assertTrue(magisterSchools.any { it.subdomain == "ignatius" })
        assertTrue(magisterSchools.any { it.subdomain == "elde" })
    }

    @Test
    fun test_reset_app_and_logout() {
        val repo = AgendaRepository()
        repo.resetAppAndLogout()
        assertFalse(repo.currentSession.value.isAuthenticated)
        assertEquals("", repo.currentSession.value.studentName)
        assertTrue(repo.appointments.value.isEmpty())
        assertTrue(repo.gradeSummaries.value.isEmpty())
        assertTrue(repo.tasks.value.isEmpty())
    }

    @Test
    fun test_clean_school_text_strips_null_strings() {
        assertNull(null.cleanSchoolText())
        assertNull("".cleanSchoolText())
        assertNull("   ".cleanSchoolText())
        assertNull("null".cleanSchoolText())
        assertNull("Null".cleanSchoolText())
        assertNull("  NULL  ".cleanSchoolText())
        assertNull("undefined".cleanSchoolText())
        assertEquals("Paragraaf 3.2 maken", "  Paragraaf 3.2 maken  ".cleanSchoolText())
    }

    @Test
    fun test_meer_tab_and_scale_presets() {
        assertTrue(AppTab.entries.contains(AppTab.MEER))
        assertEquals("Meer", AppTab.MEER.title)
        assertFalse(AppTab.entries.any { it.name == "STUDY" })
        assertEquals(1.0f, UiScalePreset.NORMAL.scaleFactor, 0.001f)
    }

    @Test
    fun test_haversine_distance_reasonable() {
        val meters = com.example.network.TransitClient.haversineMeters(
            52.0907, 5.1214,
            52.0893, 5.1101
        )
        assertTrue("Utrecht center to Utrecht CS should be ~700-900m", meters in 600.0..1000.0)
    }

    @Test
    fun test_direct_magister_auth_url_omits_acr_values() {
        val repo = AgendaRepository()
        val url = repo.getAuthorizationUrl(null, "test_verifier", "test_state")
        assertTrue(url.contains("accounts.magister.net/connect/authorize"))
        assertTrue(url.contains("client_id=M6LOAPP"))
        assertTrue(url.contains("response_type=code%20id_token"))
        assertTrue(url.contains("redirect_uri=m6loapp%3A%2F%2Foauth2redirect%2F"))
        assertFalse("Direct auth URL must omit acr_values so universal school picker loads", url.contains("acr_values"))
    }

    @Test
    fun test_jwt_payload_decode() {
        val testPayload = "eyJ0ZW5hbnQiOiJpZ25hdGl1cyIsInN1YiI6IjEyMyJ9"
        val fakeJwt = "header.$testPayload.signature"
        val tenant = PkceHelper.extractClaim(fakeJwt, "tenant")
        val sub = PkceHelper.extractClaim(fakeJwt, "sub")
        assertEquals("ignatius", tenant)
        assertEquals("123", sub)
    }

    @Test
    fun test_uitval_detection_triggers_once_and_retriggers_if_uncanceled() {
        val today = "2026-11-14"
        val pastAppt = com.example.model.Appointment(
            id = 1L,
            title = "Geschiedenis",
            subjectCode = "GS",
            subjectName = "Geschiedenis",
            teacherName = "Jansen",
            teacherInitials = "JAN",
            classroom = "102",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 5,
            isCanceled = true,
            dateIso = "2026-11-13"
        )
        val todayCanceled = com.example.model.Appointment(
            id = 2L,
            title = "Wiskunde B",
            subjectCode = "WI",
            subjectName = "Wiskunde B",
            teacherName = "Bakker",
            teacherInitials = "BAK",
            classroom = "204",
            periodIndex = 3,
            startTime = "10:15",
            endTime = "11:05",
            dayOfWeek = 6,
            isCanceled = true,
            dateIso = today
        )

        // First sync: only today's canceled lesson should trigger, not yesterday's
        val (firstTriggered, keysAfterFirst) = com.example.notifications.UitvalNotificationHelper.findNewlyCanceledLessons(
            appointments = listOf(pastAppt, todayCanceled),
            previouslyNotifiedKeys = emptySet(),
            todayIso = today
        )
        assertEquals(1, firstTriggered.size)
        assertEquals(2L, firstTriggered.first().id)

        // Second sync with same canceled lesson: should NOT trigger again
        val (secondTriggered, keysAfterSecond) = com.example.notifications.UitvalNotificationHelper.findNewlyCanceledLessons(
            appointments = listOf(pastAppt, todayCanceled),
            previouslyNotifiedKeys = keysAfterFirst,
            todayIso = today
        )
        assertTrue(secondTriggered.isEmpty())

        // Lesson is un-canceled
        val (uncanceledTriggered, keysAfterUncancel) = com.example.notifications.UitvalNotificationHelper.findNewlyCanceledLessons(
            appointments = listOf(todayCanceled.copy(isCanceled = false)),
            previouslyNotifiedKeys = keysAfterSecond,
            todayIso = today
        )
        assertTrue(uncanceledTriggered.isEmpty())
        assertFalse(
            keysAfterUncancel.contains(
                com.example.notifications.UitvalNotificationHelper.cancellationKey(todayCanceled)
            )
        )

        // Lesson is canceled again -> should trigger anew!
        val (reCanceledTriggered, _) = com.example.notifications.UitvalNotificationHelper.findNewlyCanceledLessons(
            appointments = listOf(todayCanceled),
            previouslyNotifiedKeys = keysAfterUncancel,
            todayIso = today
        )
        assertEquals(1, reCanceledTriggered.size)
        assertEquals(2L, reCanceledTriggered.first().id)
    }

    @Test
    fun test_auth_url_includes_offline_access_scope() {
        val repo = AgendaRepository()
        val url = repo.getAuthorizationUrl(null, "test_verifier", "test_state")
        assertTrue(url.contains("offline_access"))
    }

    @Test
    fun test_capitalize_subject_name_normalizes_lowercase() {
        assertEquals("", (null as String?).capitalizeSubjectName())
        assertEquals("", "   ".capitalizeSubjectName())
        assertEquals("Lichamelijke opvoeding", "lichamelijke opvoeding".capitalizeSubjectName())
        assertEquals("Natuur en zorg", "natuur en zorg".capitalizeSubjectName())
        assertEquals("Aardrijkskunde", "aardrijkskunde".capitalizeSubjectName())
        assertEquals("Wiskunde B", "Wiskunde B".capitalizeSubjectName())
    }

    @Test
    fun test_smart_bus_planner_bike_uitval_and_rain_warning() {
        val today = "2026-11-18"
        val prevHour1 = com.example.model.Appointment(
            id = 10L, title = "Engels", subjectCode = "EN", subjectName = "Engels",
            teacherName = "Bakker", teacherInitials = "BAK", classroom = "101",
            periodIndex = 1, startTime = "08:30", endTime = "09:20", dayOfWeek = 3,
            isCanceled = false, dateIso = today
        )
        val hour2 = com.example.model.Appointment(
            id = 11L, title = "Wiskunde", subjectCode = "WI", subjectName = "wiskunde",
            teacherName = "De Vries", teacherInitials = "VRI", classroom = "202",
            periodIndex = 2, startTime = "09:20", endTime = "10:10", dayOfWeek = 3,
            isCanceled = false, dateIso = today
        )
        val todayCanceledHour1 = prevHour1.copy(isCanceled = true)

        val weatherWithRain = com.example.model.WeatherInfo(
            cityName = "Utrecht",
            temperatureC = 11.0,
            minTempC = 8.0,
            maxTempC = 13.0,
            windKmh = 18.0,
            conditionDutch = "Regen",
            weatherCode = 63,
            isDay = true,
            latitude = 52.09,
            longitude = 5.12,
            hourlyTimesIso = listOf("2026-11-18T08:00", "2026-11-18T09:00"),
            hourlyPrecipProb = listOf(80, 20),
            hourlyPrecipMm = listOf(2.4, 0.0)
        )
        val bikeMode = com.example.model.CommuteModeConfig(
            id = "bike_1",
            type = com.example.model.CommuteModeType.BIKE,
            label = "Fiets",
            bikeMinutes = 25
        )

        val advice = com.example.notifications.SmartBusPlanner.computeAdvice(
            todayAppointments = listOf(todayCanceledHour1, hour2),
            previousAppointments = listOf(prevHour1, hour2),
            busTrackingState = null,
            walkMinutes = 5,
            arrivalBufferMinutes = 10,
            nowMinutes = 7 * 60 + 30,
            todayIso = today,
            activeCommuteMode = bikeMode,
            weatherInfo = weatherWithRain
        )

        assertNotNull(advice)
        assertEquals(com.example.model.CommuteModeType.BIKE, advice!!.commuteMode)
        assertEquals("08:45", advice.leaveHomeTime) // 09:20 - 25m bike - 10m buffer = 08:45
        assertEquals(50, advice.longerSleepMinutes)
        assertTrue(advice.shouldNotifyPriority)
        assertNotNull(advice.rainWarning)
        assertTrue(advice.rainWarning!!.contains("Regen"))
    }

    @Test
    fun test_auto_split_study_plan_distribution_and_idempotency() {
        val topics = listOf(
            com.example.model.TestPrepSubtopic("t1", "Hoofdstuk 1 lezen", false),
            com.example.model.TestPrepSubtopic("t2", "Opgaven paragraaf 2", true),
            com.example.model.TestPrepSubtopic("t3", "Formules oefenen", false)
        )
        val split1 = AgendaRepository.buildAutoSplitStudyPlan(
            currentTopics = topics,
            subject = "Natuurkunde",
            targetDateIso = "2026-11-24",
            todayIso = "2026-11-18"
        )
        assertEquals(4, split1.size) // 3 topics + final_review
        assertTrue(split1.all { it.title.startsWith("[") && it.title.contains("]") })
        assertEquals("final_review", split1.last().id)
        assertTrue(split1[1].isChecked)

        // Running again must be idempotent (no double brackets, single final_review)
        val split2 = AgendaRepository.buildAutoSplitStudyPlan(
            currentTopics = split1,
            subject = "Natuurkunde",
            targetDateIso = "2026-11-24",
            todayIso = "2026-11-18"
        )
        assertEquals(4, split2.size)
        assertEquals(1, split2.count { it.id == "final_review" })
        assertFalse(split2.first().title.contains("[["))
        assertEquals(split1.map { it.title }, split2.map { it.title })
    }

    @Test
    fun test_overgangsnorm_status_rules_and_overrides() {
        val summaries = listOf(
            com.example.model.SubjectGradeSummary("NE", "Nederlands", 5.2, emptyList()),
            com.example.model.SubjectGradeSummary("EN", "Engels", 6.2, emptyList()),
            com.example.model.SubjectGradeSummary("WI", "Wiskunde B", 5.1, emptyList()),
            com.example.model.SubjectGradeSummary("GS", "Geschiedenis", 7.0, emptyList())
        )

        // 2 kernvak fives -> Bespreekgeval, kernvakkenValid == false
        val statusTwoKernFives = com.example.ui.dialogs.calculateOvergangsNormStatus(summaries)
        assertEquals(2, statusTwoKernFives.totalTekortenpunten)
        assertEquals(2, statusTwoKernFives.kernvakFives)
        assertFalse(statusTwoKernFives.kernvakkenValid)
        assertEquals("Bespreekgeval", statusTwoKernFives.verdict)

        // What-If override improves Wiskunde B to 6.0 -> Bevorderbaar
        val improved = com.example.ui.dialogs.calculateOvergangsNormStatus(
            summaries = summaries,
            overrides = mapOf("WI_Wiskunde B" to 6.0)
        )
        assertEquals(1, improved.totalTekortenpunten)
        assertTrue(improved.kernvakkenValid)
        assertEquals("Bevorderbaar", improved.verdict)

        // Severe drop on Wiskunde B (3.2 -> 3p), Engels (4.1 -> 2p), Geschiedenis (4.1 -> 2p) + NE (5.2 -> 1p) => 8 tekortpunten -> Zittenblijven risico
        val failing = com.example.ui.dialogs.calculateOvergangsNormStatus(
            summaries = summaries,
            overrides = mapOf("WI_Wiskunde B" to 3.2, "GS_Geschiedenis" to 4.1, "EN_Engels" to 4.1)
        )
        assertEquals(8, failing.totalTekortenpunten)
        assertEquals(2, failing.kernvakFourOrLess)
        assertEquals("Zittenblijven risico", failing.verdict)
    }

    @Test
    fun test_uitval_helper_find_schedule_changes() {
        val today = "2026-11-18"
        val oldAppt = com.example.model.Appointment(
            id = 55L, title = "Biologie", subjectCode = "BI", subjectName = "Biologie",
            teacherName = "Smit", teacherInitials = "SMI", classroom = "105",
            periodIndex = 2, startTime = "09:20", endTime = "10:10", dayOfWeek = 3,
            isCanceled = false, dateIso = today
        )
        val movedAppt = oldAppt.copy(classroom = "304", periodIndex = 4, startTime = "11:20", endTime = "12:10")

        val (alerts, keys) = com.example.notifications.UitvalNotificationHelper.findScheduleChanges(
            previousAppointments = listOf(oldAppt),
            newAppointments = listOf(movedAppt),
            previouslyNotifiedChangeKeys = emptySet(),
            todayIso = today
        )
        assertEquals(1, alerts.size)
        assertTrue(alerts.first().changeSummary.contains("105 -> 304"))
        assertTrue(alerts.first().changeSummary.contains("2e -> 4e uur"))

        // Already notified key should not duplicate
        val (repeatAlerts, _) = com.example.notifications.UitvalNotificationHelper.findScheduleChanges(
            previousAppointments = listOf(oldAppt),
            newAppointments = listOf(movedAppt),
            previouslyNotifiedChangeKeys = keys,
            todayIso = today
        )
        assertTrue(repeatAlerts.isEmpty())
    }
}
