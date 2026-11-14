package com.example

import com.example.data.AgendaRepository
import com.example.model.AppThemeStyle
import com.example.model.AgendaStyle
import com.example.model.SchoolProvider
import com.example.model.UiScalePreset
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
}
