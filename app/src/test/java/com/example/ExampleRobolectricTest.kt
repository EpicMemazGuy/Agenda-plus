package com.example

import com.example.data.AgendaRepository
import com.example.model.AgendaStyle
import com.example.model.DisruptionType
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun test_domain_model_enums() {
        assertEquals("Huiswerk", TaskCategory.HOMEWORK.labelDutch)
        assertEquals("Toets", TaskCategory.EXAM.labelDutch)
        assertNotNull(DisruptionType.CANCELED)
        assertNotNull(TaskPriority.HIGH)
        assertEquals("Compact", AgendaStyle.COMPACT.labelDutch)
        assertNull("Null".cleanSchoolText())
    }

    @Test
    fun test_parse_magister_v6_grades_waarde_format() {
        val sampleJson = """
            {
              "items": [
                {
                  "kolomId": 9001,
                  "omschrijving": "Proefwerk Hoofdstuk 3",
                  "behaaldOp": "2026-11-10T10:00:00Z",
                  "waarde": "7,8",
                  "weegfactor": 2.0,
                  "vak": { "code": "WI", "omschrijving": "wiskunde B" }
                },
                {
                  "kolomId": 9002,
                  "omschrijving": "SO Woordjes",
                  "ingevoerdOp": "2026-11-12T08:00:00Z",
                  "waarde": "6,4",
                  "weegfactor": 1.0,
                  "vak": { "code": "WI", "omschrijving": "wiskunde B" }
                }
              ]
            }
        """.trimIndent()

        val summaries = MagisterClient().parseGradesJson(sampleJson)
        assertEquals(1, summaries.size)
        val math = summaries.first()
        assertEquals("Wiskunde B", math.subjectName)
        assertEquals(2, math.grades.size)
        assertEquals(7.3, math.weightedAverage, 0.1)
    }

    @Test
    fun test_parse_magister_legacy_cijferstr_format() {
        val legacyJson = """
            {
              "Items": [
                {
                  "CijferId": "101",
                  "CijferStr": "8,2",
                  "Weging": 1.0,
                  "Omschrijving": "Luistertoets",
                  "DatumIngevoerd": "2026-10-05T12:00:00Z",
                  "Vak": { "Afkorting": "EN", "Omschrijving": "Engels" }
                }
              ]
            }
        """.trimIndent()

        val summaries = MagisterClient().parseGradesJson(legacyJson)
        assertEquals(1, summaries.size)
        assertEquals("Engels", summaries.first().subjectName)
        assertEquals(8.2, summaries.first().weightedAverage, 0.01)
    }

    @Test
    fun test_theme_style_persistence_and_launcher_icon_toggle() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)
        assertEquals(com.example.model.AppThemeStyle.STANDARD, store.getAppThemeStyle())

        store.setAppThemeStyle(com.example.model.AppThemeStyle.NOTHING_OS)
        assertEquals(com.example.model.AppThemeStyle.NOTHING_OS, store.getAppThemeStyle())

        com.example.util.LauncherIconManager.applyThemeIcon(context, com.example.model.AppThemeStyle.NOTHING_OS)
        val pm = context.packageManager
        val defaultComp = android.content.ComponentName(context.packageName, "${context.packageName}.MainActivityDefault")
        val nothingComp = android.content.ComponentName(context.packageName, "${context.packageName}.MainActivityNothingOs")
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(defaultComp)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(nothingComp)
        )

        com.example.widget.WidgetUpdater.updateAllWidgets(context)

        store.setAppThemeStyle(com.example.model.AppThemeStyle.STANDARD)
        assertEquals(com.example.model.AppThemeStyle.STANDARD, store.getAppThemeStyle())
        com.example.widget.WidgetUpdater.updateAllWidgets(context)
    }

    @Test
    fun test_preferences_vandaag_blocks_offline_tutorial_and_caches() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // Default flags
        assertFalse(store.hasSeenTutorial())
        assertFalse(store.isNavPromptDismissed())
        assertFalse(store.isOfflineModeEnabled())
        assertTrue(store.loadTodayBlocksConfig().isNotEmpty())

        // Persist tutorial, nav prompt, offline mode
        store.setTutorialSeen(true)
        store.setNavPromptDismissed(true)
        store.setOfflineModeEnabled(true)
        assertTrue(store.hasSeenTutorial())
        assertTrue(store.isNavPromptDismissed())
        assertTrue(store.isOfflineModeEnabled())

        // School address & bus line
        val addr = com.example.model.SchoolAddress(
            displayName = "Elde College",
            streetAndCity = "Putsteeg 4, Schijndel",
            latitude = 51.6208,
            longitude = 5.4368
        )
        store.saveSchoolAddress(addr)
        assertEquals("Elde College", store.loadSchoolAddress()?.displayName)
        store.setSavedBusLine("156")
        assertEquals("156", store.getSavedBusLine())

        // Reorder Today blocks
        val customBlocks = store.loadTodayBlocksConfig().reversed()
        store.saveTodayBlocksConfig(customBlocks)
        assertEquals(customBlocks.first().type, store.loadTodayBlocksConfig().first().type)

        // Messages, StudyGuides & KWT caches
        val msg = com.example.model.MagisterMessage(
            id = "42",
            subject = "Roosterwijziging",
            senderName = "Administratie",
            dateSent = "14 nov 10:00",
            body = "Let op!",
            isRead = false,
            folder = com.example.model.MessageFolder.INBOX,
            folderId = 1L,
            folderName = "Postvak In"
        )
        store.saveMessages(listOf(msg))
        assertEquals(1, store.loadMessages().size)
        assertEquals("Roosterwijziging", store.loadMessages().first().subject)

        val guide = com.example.model.StudyGuide(
            id = "g1",
            title = "Hoofdstuk 4",
            subjectCode = "WI",
            subjectName = "Wiskunde B",
            parts = listOf(
                com.example.model.StudyGuidePart(
                    id = "p1",
                    title = "Week 46",
                    description = "Opgaven 1-10",
                    resources = listOf(com.example.model.StudyGuideResource("r1", "PDF", "https://example.com"))
                )
            )
        )
        store.saveStudyGuides(listOf(guide))
        assertEquals(1, store.loadStudyGuides().size)
        assertEquals(1, store.loadStudyGuides().first().parts.first().resources.size)

        val kwt = com.example.model.KwtSlot(
            appointmentId = 555L,
            title = "KWT Wiskunde",
            dateIso = "2026-11-16",
            startTime = "13:15",
            endTime = "14:05",
            periodIndex = 6,
            options = listOf(
                com.example.model.KwtOption(99L, "Wiskunde bijles", "Wiskunde", "Bakker", "204", 5, 25, true)
            )
        )
        store.saveKwtSlots(listOf(kwt))
        assertEquals(1, store.loadKwtSlots().size)
        assertTrue(store.loadKwtSlots().first().options.first().isEnrolled)

        // Clear session preserves user's schoolAddress & busLine
        store.clearSession()
        assertEquals("Elde College", store.loadSchoolAddress()?.displayName)
        assertEquals("156", store.getSavedBusLine())
        assertTrue(store.loadMessages().isEmpty())
    }

    @Test
    fun test_update_checker_semantic_version_comparison() {
        // Same chosen version should NOT trigger an update prompt
        assertFalse(com.example.util.UpdateChecker.isVersionNewer("0.1.2-pre-alpha", "v0.1.2-pre-alpha"))
        assertFalse(com.example.util.UpdateChecker.isVersionNewer("0.1.4-pre-alpha", "v0.1.4-pre-alpha"))
        assertFalse(com.example.util.UpdateChecker.isVersionNewer(BuildConfig.VERSION_NAME, "v0.1.4-pre-alpha"))
        assertFalse(com.example.util.UpdateChecker.isVersionNewer("0.1.2", "v0.1.2"))

        // Pre-release progression within same patch version
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-pre-alpha", "v0.1.2-pre-alpha.2"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-pre-alpha", "v0.1.2-alpha"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-alpha", "v0.1.2-beta"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-beta", "v0.1.2-rc1"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-rc1", "v0.1.2"))

        // Older remote versions should NOT trigger update
        assertFalse(com.example.util.UpdateChecker.isVersionNewer("0.1.2", "v0.1.2-pre-alpha"))
        assertFalse(com.example.util.UpdateChecker.isVersionNewer("0.1.3-pre-alpha", "v0.1.2"))

        // Newer patch/minor/major versions should trigger update
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2", "v0.1.3-pre-alpha"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.1.2-pre-alpha", "v0.2.0"))
        assertTrue(com.example.util.UpdateChecker.isVersionNewer("0.9.9", "v1.0.0"))
    }

    @Test
    fun test_upcoming_tests_chronological_and_countdowns() {
        val today = "2026-11-15"
        val appts = listOf(
            com.example.model.Appointment(
                id = 10L,
                title = "PW Hoofdstuk 4",
                subjectCode = "WI",
                subjectName = "Wiskunde B",
                teacherName = "Bakker",
                teacherInitials = "BAK",
                classroom = "204",
                periodIndex = 2,
                startTime = "09:20",
                endTime = "10:10",
                dayOfWeek = 1,
                dateIso = "2026-11-18",
                infoType = 2
            ),
            com.example.model.Appointment(
                id = 11L,
                title = "Normale les",
                subjectCode = "NE",
                subjectName = "Nederlands",
                teacherName = "De Vries",
                teacherInitials = "DVR",
                classroom = "101",
                periodIndex = 3,
                startTime = "10:30",
                endTime = "11:20",
                dayOfWeek = 1,
                dateIso = "2026-11-16",
                infoType = 1
            )
        )
        val tasks = listOf(
            com.example.model.HomeworkTask(
                id = "t1",
                subject = "Engels",
                title = "SO Woordjes Unit 5",
                description = "Leer blz. 40-42",
                dueDay = "2026-11-16",
                dueDate = "08:30 (1e uur)",
                category = TaskCategory.EXAM
            )
        )

        val upcoming = com.example.ui.screens.buildUpcomingTestsList(appts, tasks, todayIsoOverride = today)
        assertEquals(2, upcoming.size)
        assertEquals("Engels", upcoming[0].subject)
        assertEquals("SO", upcoming[0].typeBadge)
        assertEquals("Morgen", upcoming[0].countdownLabel)
        assertEquals("Wiskunde B", upcoming[1].subject)
        assertEquals("PW", upcoming[1].typeBadge)
        assertEquals("Over 3 dagen", upcoming[1].countdownLabel)
    }

    @Test
    fun test_notification_diffing_for_schedule_changes_grades_and_tests() {
        val today = "2026-11-15"
        val prevAppt = com.example.model.Appointment(
            id = 100L, title = "Natuurkunde", subjectCode = "NA", subjectName = "Natuurkunde",
            teacherName = "Smit", teacherInitials = "SMI", classroom = "301",
            periodIndex = 4, startTime = "11:20", endTime = "12:10", dayOfWeek = 1, dateIso = "2026-11-16"
        )
        val changedAppt = prevAppt.copy(classroom = "305", isRelocated = true)

        val (changes, keysAfter) = com.example.notifications.UitvalNotificationHelper.findScheduleChanges(
            previousAppointments = listOf(prevAppt),
            newAppointments = listOf(changedAppt),
            previouslyNotifiedChangeKeys = emptySet(),
            todayIso = today
        )
        assertEquals(1, changes.size)
        assertTrue(changes.first().changeSummary.contains("301 -> 305"))

        val (repeatChanges, _) = com.example.notifications.UitvalNotificationHelper.findScheduleChanges(
            previousAppointments = listOf(prevAppt),
            newAppointments = listOf(changedAppt),
            previouslyNotifiedChangeKeys = keysAfter,
            todayIso = today
        )
        assertTrue(repeatChanges.isEmpty())

        // Baseline prevents initial grade flood, subsequent new grade triggers
        val grade1 = com.example.model.GradeItem("g1", "WI", "Wiskunde", 7.5, 2.0, "PW", "2026-11-10", 1)
        val grade2 = com.example.model.GradeItem("g2", "EN", "Engels", 8.6, 1.0, "SO", "2026-11-15", 1)
        val summary1 = com.example.model.SubjectGradeSummary("WI", "Wiskunde", 7.5, listOf(grade1))
        val summary2 = com.example.model.SubjectGradeSummary("EN", "Engels", 8.6, listOf(grade2))

        val (firstGradeAlerts, knownAfterBaseline) = com.example.notifications.UitvalNotificationHelper.findNewGrades(
            newGradeSummaries = listOf(summary1),
            knownGradeIds = emptySet(),
            hasBaseline = false
        )
        assertTrue(firstGradeAlerts.isEmpty())
        assertTrue(knownAfterBaseline.contains("g1"))

        val (newGradeAlerts, _) = com.example.notifications.UitvalNotificationHelper.findNewGrades(
            newGradeSummaries = listOf(summary1, summary2),
            knownGradeIds = knownAfterBaseline,
            hasBaseline = true
        )
        assertEquals(1, newGradeAlerts.size)
        assertEquals("g2", newGradeAlerts.first().id)
    }

    @Test
    fun test_grade_exporter_csv_and_png_and_unboxing_prefs() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)
        assertTrue("Grade unboxing must default ON", store.isGradeUnboxingEnabled())
        assertTrue(store.isUitvalNotificationsEnabled())
        assertTrue(store.isGradeNotificationsEnabled())
        assertTrue(store.isTestNotificationsEnabled())
        assertEquals(com.example.model.GradeUnboxingMode.CASE_OPENING, store.getGradeUnboxingMode())

        store.setGradeUnboxingMode(com.example.model.GradeUnboxingMode.PLINKO_DROP)
        assertEquals(com.example.model.GradeUnboxingMode.PLINKO_DROP, store.getGradeUnboxingMode())

        val grade = com.example.model.GradeItem("g10", "WI", "Wiskunde B", 8.4, 2.0, "Proefwerk H4, Deel 1", "2026-11-14", 1)
        val summary = com.example.model.SubjectGradeSummary("WI", "Wiskunde B", 8.4, listOf(grade))
        val csv = com.example.util.GradeExporter.generateCsvString(listOf(summary))
        assertTrue(csv.contains("Vak,Code,Vakgemiddelde"))
        assertTrue(csv.contains("\"Proefwerk H4, Deel 1\""))

        val png = com.example.util.GradeExporter.renderGradeShareCardPng(
            context = context,
            selectedGrades = listOf(grade),
            overallAverage = 8.4,
            studentName = "Tester",
            appThemeStyle = com.example.model.AppThemeStyle.NOTHING_OS
        )
        assertTrue(png.exists() && png.length() > 1000L)
    }

    @Test
    fun test_release_notes_dialog_extracts_new_features_cleanly() {
        val rawBody = """
            ### Agenda+ v0.1.5-pre-alpha

            - **App-naam:** `Agenda+`
            - **Versie:** `v0.1.5-pre-alpha` (build `10510`)

            ---

            #### Wat is er nieuw in Agenda+?
            - **In-app APK Updater**: Direct installeren vanuit het dialoog
            - Snellere live bustijden

            ---

            #### Installatie & Updaten op Android:
            1. Open het gedownloade bestand.
        """.trimIndent()

        val formatted = com.example.util.UpdateChecker.formatReleaseNotesForDialog(rawBody)
        assertTrue(formatted.contains("In-app APK Updater: Direct installeren vanuit het dialoog"))
        assertTrue(formatted.contains("- Snellere live bustijden"))
        assertFalse(formatted.contains("Installatie & Updaten"))
        assertFalse(formatted.contains("**"))
    }

    @Test
    fun test_choices_backup_export_and_import_round_trip() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        store.setAppThemeStyle(com.example.model.AppThemeStyle.NOTHING_OS)
        store.setSavedBusLine("401")
        store.setGradeUnboxingMode(com.example.model.GradeUnboxingMode.PENALTIES)
        store.setUitvalNotificationsEnabled(false)
        store.saveSchoolAddress(
            com.example.model.SchoolAddress("Stedelijk Gymnasium", "Nassauplein 2, Haarlem", 52.38, 4.63)
        )

        val backupJson = store.exportChoicesBackupJson()
        assertTrue(backupJson.contains("NOTHING_OS"))
        assertTrue(backupJson.contains("401"))

        // Reset values and restore from backup
        store.setAppThemeStyle(com.example.model.AppThemeStyle.STANDARD)
        store.setSavedBusLine("")
        store.setGradeUnboxingMode(com.example.model.GradeUnboxingMode.CASE_OPENING)
        store.setUitvalNotificationsEnabled(true)
        store.saveSchoolAddress(null)

        val restoredResult = store.importChoicesBackupJson(backupJson)
        assertTrue(restoredResult.isSuccess)
        assertEquals(com.example.model.AppThemeStyle.NOTHING_OS, store.getAppThemeStyle())
        assertEquals("401", store.getSavedBusLine())
        assertEquals(com.example.model.GradeUnboxingMode.PENALTIES, store.getGradeUnboxingMode())
        assertFalse(store.isUitvalNotificationsEnabled())
        assertEquals("Stedelijk Gymnasium", store.loadSchoolAddress()?.displayName)

        // Invalid JSON fails cleanly
        assertTrue(store.importChoicesBackupJson("{}").isFailure)
    }

    @Test
    fun test_one_ui_default_theme_lock_dark_mode_and_launcher_icon() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // Strict default lock: Material You (STANDARD) and SYSTEM dark mode on fresh install
        assertEquals(com.example.model.AppThemeStyle.STANDARD, store.getAppThemeStyle())
        assertEquals(com.example.model.DarkModePreference.SYSTEM, store.getDarkModePreference())
        assertEquals("Material You", com.example.model.AppThemeStyle.STANDARD.labelDutch)
        assertEquals("Nothing OS", com.example.model.AppThemeStyle.NOTHING_OS.labelDutch)
        assertEquals("One UI 8.5", com.example.model.AppThemeStyle.ONE_UI.labelDutch)
        assertEquals("Systeem", com.example.model.DarkModePreference.SYSTEM.labelDutch)
        assertEquals("Licht", com.example.model.DarkModePreference.LIGHT.labelDutch)
        assertEquals("Donker", com.example.model.DarkModePreference.DARK.labelDutch)

        // Switching to One UI 8.5 + Light mode + squircle launcher icon
        store.setAppThemeStyle(com.example.model.AppThemeStyle.ONE_UI)
        store.setDarkModePreference(com.example.model.DarkModePreference.LIGHT)
        assertEquals(com.example.model.AppThemeStyle.ONE_UI, store.getAppThemeStyle())
        assertEquals(com.example.model.DarkModePreference.LIGHT, store.getDarkModePreference())

        com.example.util.LauncherIconManager.applyThemeIcon(context, com.example.model.AppThemeStyle.ONE_UI)
        val pm = context.packageManager
        val defaultComp = android.content.ComponentName(context.packageName, "${context.packageName}.MainActivityDefault")
        val nothingComp = android.content.ComponentName(context.packageName, "${context.packageName}.MainActivityNothingOs")
        val oneUiComp = android.content.ComponentName(context.packageName, "${context.packageName}.MainActivityOneUi")
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(defaultComp)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(nothingComp)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(oneUiComp)
        )

        // Backup v2 preserves One UI, dark mode, quiet hours, muted subjects, and bus settings
        store.setQuietHoursEnabled(true)
        store.setQuietHoursStartMinutes(23 * 60)
        store.setQuietHoursEndMinutes(6 * 60 + 30)
        store.setMutedSubjects(setOf("Gym", "Rekenen"))
        store.setSmartBusAlertsEnabled(false)
        store.setBusWalkMinutes(8)
        store.setBusArrivalBufferMinutes(15)

        val backup = store.exportChoicesBackupJson()
        store.setAppThemeStyle(com.example.model.AppThemeStyle.STANDARD)
        store.setDarkModePreference(com.example.model.DarkModePreference.SYSTEM)
        store.setMutedSubjects(emptySet())
        store.setBusWalkMinutes(5)

        val restoreRes = store.importChoicesBackupJson(backup)
        assertTrue(restoreRes.isSuccess)
        assertEquals(com.example.model.AppThemeStyle.ONE_UI, store.getAppThemeStyle())
        assertEquals(com.example.model.DarkModePreference.LIGHT, store.getDarkModePreference())
        assertEquals(23 * 60, store.getQuietHoursStartMinutes())
        assertEquals(6 * 60 + 30, store.getQuietHoursEndMinutes())
        assertEquals(setOf("Gym", "Rekenen"), store.getMutedSubjects())
        assertFalse(store.isSmartBusAlertsEnabled())
        assertEquals(8, store.getBusWalkMinutes())
        assertEquals(15, store.getBusArrivalBufferMinutes())
    }

    @Test
    fun test_quiet_hours_and_subject_muting_keep_baselines_updated() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // Default quiet hours 22:00 - 07:00
        assertTrue(store.isQuietHoursEnabled())
        assertEquals(22 * 60, store.getQuietHoursStartMinutes())
        assertEquals(7 * 60, store.getQuietHoursEndMinutes())

        // Overnight wraparound checks
        assertTrue(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(22 * 60, 22 * 60, 7 * 60))
        assertTrue(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(23 * 60 + 30, 22 * 60, 7 * 60))
        assertTrue(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(2 * 60, 22 * 60, 7 * 60))
        assertTrue(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(6 * 60 + 59, 22 * 60, 7 * 60))
        assertFalse(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(7 * 60, 22 * 60, 7 * 60))
        assertFalse(com.example.notifications.UitvalNotificationHelper.isWithinQuietHours(14 * 60 + 15, 22 * 60, 7 * 60))

        // Subject muting (case-insensitive & partial match)
        val muted = setOf("LO", "Rekenen")
        assertTrue(com.example.notifications.UitvalNotificationHelper.isSubjectMuted("lo", muted))
        assertTrue(com.example.notifications.UitvalNotificationHelper.isSubjectMuted("Rekenen klas 4", muted))
        assertFalse(com.example.notifications.UitvalNotificationHelper.isSubjectMuted("Wiskunde B", muted))

        // During quiet hours, evaluateAllNotifications still updates baselines without buzzing
        val todayIso = com.example.notifications.SmartBusPlanner.currentTodayIso()
        val canceledAppt = com.example.model.Appointment(
            id = 8801L,
            title = "Rekenen",
            subjectCode = "REK",
            subjectName = "Rekenen",
            teacherName = "Jansen",
            teacherInitials = "JAN",
            classroom = "102",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 1,
            isCanceled = true,
            dateIso = todayIso
        )
        val grade = com.example.model.GradeItem("g_quiet_1", "WI", "Wiskunde", 8.0, 1.0, "SO", todayIso, 1)
        val summary = com.example.model.SubjectGradeSummary("WI", "Wiskunde", 8.0, listOf(grade))

        com.example.notifications.UitvalNotificationHelper.evaluateAllNotifications(
            context = context,
            previousAppointments = emptyList(),
            newAppointments = listOf(canceledAppt),
            newGrades = listOf(summary),
            newTasks = emptyList(),
            nowMinutes = 23 * 60 + 15 // inside quiet hours
        )
        assertTrue(
            store.getNotifiedCanceledKeys().contains(
                com.example.notifications.UitvalNotificationHelper.cancellationKey(canceledAppt)
            )
        )
        assertTrue(store.getKnownGradeIds().contains("g_quiet_1"))
    }

    @Test
    fun test_smart_bus_planner_shifts_bus_when_first_hour_canceled() {
        val todayIso = "2026-11-16"
        val firstHourActive = com.example.model.Appointment(
            id = 1L,
            title = "Nederlands",
            subjectCode = "NE",
            subjectName = "Nederlands",
            teacherName = "Bakker",
            teacherInitials = "BAK",
            classroom = "105",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 1,
            isCanceled = false,
            dateIso = todayIso
        )
        val secondHourActive = com.example.model.Appointment(
            id = 2L,
            title = "Wiskunde B",
            subjectCode = "WI",
            subjectName = "Wiskunde B",
            teacherName = "De Vries",
            teacherInitials = "DVR",
            classroom = "204",
            periodIndex = 2,
            startTime = "09:20",
            endTime = "10:10",
            dayOfWeek = 1,
            isCanceled = false,
            dateIso = todayIso
        )

        val busState = com.example.model.BusTrackingState(
            lineNumber = "156",
            nearestStop = com.example.model.BusStopInfo(
                timingPointCode = "STOP1",
                name = "Kerkstraat",
                town = "Schijndel",
                latitude = 51.62,
                longitude = 5.43,
                upcomingDepartures = listOf(
                    com.example.model.BusDeparture("07:55"),
                    com.example.model.BusDeparture("08:45"),
                    com.example.model.BusDeparture("09:15")
                )
            )
        )

        // Now it's 07:30, 1st hour (08:30) was previously active and is now canceled -> start at 09:20
        // Latest bus: 09:20 - 10 (buffer) - 10 (est ride) = 09:00 -> best catchable bus is 08:45; leave home = 08:40
        val advice = com.example.notifications.SmartBusPlanner.computeAdvice(
            todayAppointments = listOf(firstHourActive.copy(isCanceled = true), secondHourActive),
            previousAppointments = listOf(firstHourActive, secondHourActive),
            busTrackingState = busState,
            walkMinutes = 5,
            arrivalBufferMinutes = 10,
            nowMinutes = 7 * 60 + 30,
            savedBusLine = "156",
            todayIso = todayIso
        )

        assertNotNull(advice)
        assertEquals("08:45", advice!!.busDepartureTime)
        assertEquals("08:40", advice.leaveHomeTime)
        assertEquals("1e uur uitgevallen! Pak in plaats daarvan de bus van 08:45.", advice.shiftReasonMessage)
        assertTrue(advice.shouldNotifyPriority)
    }

    @Test
    fun test_pta_parsing_scope_filter_and_weighted_what_if_calculator() {
        val mixedGradesJson = """
            {
              "items": [
                {
                  "kolomId": 5001,
                  "omschrijving": "SO Grammatica",
                  "behaaldOp": "2026-10-01T10:00:00Z",
                  "waarde": "6,0",
                  "weegfactor": 1.0,
                  "isPta": false,
                  "vak": { "code": "EN", "omschrijving": "Engels" }
                },
                {
                  "kolomId": 5002,
                  "omschrijving": "SE Schrijfvaardigheid",
                  "behaaldOp": "2026-11-12T10:00:00Z",
                  "waarde": "8,0",
                  "weegfactor": 3.0,
                  "isPta": true,
                  "vak": { "code": "EN", "omschrijving": "Engels" }
                },
                {
                  "kolomId": 5003,
                  "omschrijving": "Oefentoets",
                  "behaaldOp": "2026-11-05T10:00:00Z",
                  "waarde": "7,0",
                  "weegfactor": 1.0,
                  "vak": { "code": "WI", "omschrijving": "Wiskunde" }
                }
              ]
            }
        """.trimIndent()

        val summaries = MagisterClient().parseGradesJson(mixedGradesJson)
        assertEquals(2, summaries.size)

        val ptaOnly = com.example.ui.screens.filterGradeSummariesByScope(
            summaries,
            com.example.model.GradeScopeFilter.PTA
        )
        assertEquals(1, ptaOnly.size)
        assertEquals("Engels", ptaOnly.first().subjectName)
        assertEquals(8.0, ptaOnly.first().weightedAverage, 0.01)

        val regularOnly = com.example.ui.screens.filterGradeSummariesByScope(
            summaries,
            com.example.model.GradeScopeFilter.REGULAR
        )
        assertEquals(2, regularOnly.size)
        assertEquals(6.0, regularOnly.first { it.subjectName == "Engels" }.weightedAverage, 0.01)

        // Weighted "Wat moet ik halen?": current (6.0 * 1.0 + 8.0 * 3.0) = 30.0 over weight 4.0 -> avg 7.5
        // For target 8.0 with next test weight 2.0: needed = (8.0 * 6.0 - 30.0) / 2.0 = 9.0
        val engelsGrades = summaries.first { it.subjectName == "Engels" }.grades
        val neededFeasible = com.example.ui.screens.calculateRequiredGradeForTarget(
            currentGrades = engelsGrades,
            targetAverage = 8.0,
            nextTestWeight = 2.0
        )
        assertEquals(9.0, neededFeasible, 0.05)

        // Impossible target (> 10.0)
        val neededImpossible = com.example.ui.screens.calculateRequiredGradeForTarget(
            currentGrades = engelsGrades,
            targetAverage = 9.5,
            nextTestWeight = 1.0
        )
        assertTrue(neededImpossible > 10.0)
    }

    @Test
    fun test_room_custom_schedule_db_recurrence_and_crud() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.AgendaDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = db.customScheduleDao()
            val recurringItem = com.example.data.db.CustomScheduleItem(
                title = "Voetbaltraining",
                description = "Veld 2",
                startTime = "18:30",
                endTime = "20:00",
                daysOfWeekCsv = "1,3", // Ma, Wo
                isRecurring = true,
                colorKey = "EMERALD"
            )
            assertTrue(recurringItem.appliesToDate("2026-11-16", 1))
            assertFalse(recurringItem.appliesToDate("2026-11-17", 2))

            val singleItem = com.example.data.db.CustomScheduleItem(
                title = "Bijles Natuurkunde",
                startTime = "15:15",
                endTime = "16:15",
                dateIso = "2026-11-18",
                isRecurring = false,
                colorKey = "BLUE"
            )
            assertTrue(singleItem.appliesToDate("2026-11-18", 3))
            assertFalse(singleItem.appliesToDate("2026-11-19", 4))

            kotlinx.coroutines.runBlocking {
                val id1 = dao.upsert(recurringItem)
                dao.upsert(singleItem)
                val all = dao.observeAll().first()
                assertEquals(2, all.size)
                assertEquals("Bijles Natuurkunde", all.first().title) // sorted by startTime ASC

                dao.updateCompletion(id1, true)
                val updated = dao.observeAll().first().first { it.id == id1 }
                assertTrue(updated.isCompleted)

                dao.deleteById(id1)
                assertEquals(1, dao.observeAll().first().size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun test_prep_checklist_splitter_absences_parser_glyph_and_one_ui_artifacts() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // 1. Toetsstof checklist splitting & persistence
        val topics = com.example.data.AppPreferencesStore.splitToetsstofIntoTopics(
            rawText = "Hoofdstuk 3, Paragraaf 4.2; Woordjes Unit 5 en Samenvatting",
            subject = "Engels",
            fallbackTitle = "Proefwerk"
        )
        assertEquals(4, topics.size)
        assertEquals("Hoofdstuk 3", topics[0].title)
        assertEquals("Samenvatting", topics[3].title)

        val checklist = com.example.model.TestPrepChecklist(
            key = "test_en_1",
            subject = "Engels",
            title = "PW Unit 5",
            topics = topics.mapIndexed { idx, t -> if (idx == 0) t.copy(isChecked = true) else t }
        )
        store.saveTestPrepChecklist(checklist)
        val loaded = store.loadTestPrepChecklists()["test_en_1"]
        assertNotNull(loaded)
        assertEquals(1, loaded!!.completedCount)
        assertEquals(0.25f, loaded.progress, 0.01f)

        // 2. Absences JSON parser
        val absencesJson = """
            {
              "items": [
                { "id": "a1", "start": "2026-11-10T08:30:00Z", "lesuurVan": 1, "omschrijving": "Ziek gemeld", "geoorloofd": true },
                { "id": "a2", "start": "2026-11-12T09:20:00Z", "lesuurVan": 2, "omschrijving": "Te laat in de les", "geoorloofd": false }
              ]
            }
        """.trimIndent()
        val parsedAbsences = MagisterClient().parseAbsencesJson(absencesJson)
        assertEquals(2, parsedAbsences.size)
        assertEquals(com.example.model.AbsenceType.SICK, parsedAbsences[0].type)
        assertEquals("1e uur", parsedAbsences[0].period)
        assertTrue(parsedAbsences[0].isApproved)
        assertEquals(com.example.model.AbsenceType.TARDY, parsedAbsences[1].type)
        assertFalse(parsedAbsences[1].isApproved)

        // 3. Nothing Glyph 15-minute countdown
        assertNull(com.example.hardware.NothingGlyphBridge.computeCountdownProgress(10 * 60, 10 * 60 + 20))
        assertNull(com.example.hardware.NothingGlyphBridge.computeCountdownProgress(10 * 60, 10 * 60))
        val progress = com.example.hardware.NothingGlyphBridge.computeCountdownProgress(10 * 60, 10 * 60 + 7)
        assertNotNull(progress)
        assertTrue(progress!! in 40..55)

        // 4. One UI 8.5 PNG Grade Share Card & all 4 Widgets
        val grade = com.example.model.GradeItem("g_oneui", "NA", "Natuurkunde", 8.9, 2.0, "PTA Mechanica", "2026-11-15", 1, isPta = true)
        val png = com.example.util.GradeExporter.renderGradeShareCardPng(
            context = context,
            selectedGrades = listOf(grade),
            overallAverage = 8.9,
            studentName = "One UI Tester",
            appThemeStyle = com.example.model.AppThemeStyle.ONE_UI
        )
        assertTrue(png.exists() && png.length() > 1000L)

        store.setAppThemeStyle(com.example.model.AppThemeStyle.ONE_UI)
        com.example.widget.WidgetUpdater.updateAllWidgets(context)
    }

    @Test
    fun test_one_ui_design_rules_geometry_tokens_typography_and_motion() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // 1. Default theme lock remains Material You (STANDARD)
        assertEquals(com.example.model.AppThemeStyle.STANDARD, store.getAppThemeStyle())

        // 2. Squircle superellipse geometry produces valid generic outlines & equality semantics
        val density = androidx.compose.ui.unit.Density(3.0f, 1.0f)
        val focusShape = com.example.ui.components.SquircleShape(androidx.compose.ui.unit.Dp(26f))
        val innerShape = com.example.ui.components.SquircleShape(androidx.compose.ui.unit.Dp(14f))
        val thumbShape = com.example.ui.components.SquircleShape(androidx.compose.ui.unit.Dp(9f))
        val sheetShape = com.example.ui.components.SquircleShape(androidx.compose.ui.unit.Dp(26f), topOnly = true)

        assertTrue(
            focusShape.createOutline(
                androidx.compose.ui.geometry.Size(300f, 180f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                density
            ) is androidx.compose.ui.graphics.Outline.Generic
        )
        assertTrue(
            innerShape.createOutline(
                androidx.compose.ui.geometry.Size(160f, 90f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                density
            ) is androidx.compose.ui.graphics.Outline.Generic
        )
        assertTrue(
            thumbShape.createOutline(
                androidx.compose.ui.geometry.Size(48f, 48f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                density
            ) is androidx.compose.ui.graphics.Outline.Generic
        )
        assertEquals(focusShape, com.example.ui.components.SquircleShape(androidx.compose.ui.unit.Dp(26f)))
        assertFalse(focusShape == sheetShape)

        // 3. One UI Color & Surface Architecture tokens
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0381FE), com.example.ui.theme.OneUiBlueLight)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3E91FF), com.example.ui.theme.OneUiBlueDark)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF5F5F7), com.example.ui.theme.OneUiLightCanvasBase)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF19191C), com.example.ui.theme.OneUiDarkCardBase)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE6E6E6), com.example.ui.theme.OneUiDividerLight)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2C2C2E), com.example.ui.theme.OneUiDividerDark)

        // 4. Typography hierarchy: Light-to-Regular humanist display + Roboto dense labels
        assertEquals(androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.displayLarge.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Light, com.example.ui.theme.OneUiTypography.displayLarge.fontWeight)
        assertEquals(com.example.ui.theme.OneUiDisplayFontFamily, com.example.ui.theme.OneUiTypography.displayLarge.fontFamily)

        assertEquals(androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.displayMedium.fontSize)
        assertEquals(androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.titleLarge.fontSize)
        assertEquals(androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.titleMedium.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, com.example.ui.theme.OneUiTypography.titleMedium.fontFamily)
        assertEquals(androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.bodyMedium.fontSize)
        assertEquals(androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp), com.example.ui.theme.OneUiTypography.bodySmall.fontSize)

        // 5. Deceleration curve is non-linear (smooth fast-out, slow-in)
        assertTrue(com.example.ui.components.OneUiDecelerationEasing.transform(0.25f) > 0.5f)
    }

    @Test
    fun test_web_sync_pairing_aes_gcm_protocol_and_merge() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Valid agendaplus://pair URI parsing & 32-byte key validation
        val rawKey = ByteArray(32) { idx -> (idx * 7 + 3).toByte() }
        val urlSafeKey = android.util.Base64.encodeToString(
            rawKey,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        assertEquals(43, urlSafeKey.length)
        val syncId = "550e8400-e29b-41d4-a716-446655440000"
        val pairUri = "agendaplus://pair?id=$syncId&key=$urlSafeKey"

        val parsed = com.example.services.SyncManager.parsePairingUri(pairUri).getOrThrow()
        assertEquals(syncId, parsed.syncId)
        assertArrayEquals(rawKey, parsed.aesKeyBytes)

        assertTrue(com.example.services.SyncManager.parsePairingUri("https://agendaplus.nl/pair?id=$syncId&key=$urlSafeKey").isFailure)
        assertTrue(com.example.services.SyncManager.parsePairingUri("agendaplus://pair?id=bad-id&key=$urlSafeKey").isFailure)
        val shortKey = android.util.Base64.encodeToString(ByteArray(16), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        assertTrue(com.example.services.SyncManager.parsePairingUri("agendaplus://pair?id=$syncId&key=$shortKey").isFailure)

        // Pair and persist, then verify unpair
        val pairState = com.example.services.SyncManager.pairWithUri(context, pairUri).getOrThrow()
        assertTrue(pairState.isPaired)
        assertEquals(syncId, pairState.syncId)

        // 2. AES-256-GCM encryption/decryption byte-for-byte contract (12-byte IV + appended 16-byte tag, Base64.NO_WRAP)
        val samplePlaintext = """{"schemaVersion":1,"source":"android","hello":"Agenda+"}"""
        val blob = com.example.services.SyncManager.encryptPayload(rawKey, samplePlaintext)
        assertFalse(blob.ivBase64.contains("\n"))
        assertFalse(blob.ciphertextBase64.contains("\n"))
        assertEquals(12, android.util.Base64.decode(blob.ivBase64, android.util.Base64.NO_WRAP).size)
        val cipherBytes = android.util.Base64.decode(blob.ciphertextBase64, android.util.Base64.NO_WRAP)
        assertEquals(samplePlaintext.toByteArray(Charsets.UTF_8).size + 16, cipherBytes.size)
        val decrypted = com.example.services.SyncManager.decryptPayload(rawKey, blob.ivBase64, blob.ciphertextBase64).getOrThrow()
        assertEquals(samplePlaintext, decrypted)

        // 3. Exact SyncPayload schema & stable SHA-256 hash across volatile updatedAt timestamps
        val customItem = com.example.data.db.CustomScheduleItem(
            id = 42L,
            title = "Bijles Wiskunde",
            startTime = "15:30",
            endTime = "16:30",
            dateIso = "2026-11-18"
        )
        val task = com.example.model.HomeworkTask(
            id = "hw_1",
            subject = "Nederlands",
            title = "Boekverslag",
            description = "Hoofdstuk 3",
            dueDay = "Morgen",
            dueDate = "2026-11-18",
            category = com.example.model.TaskCategory.HOMEWORK,
            isCompleted = true
        )
        val payloadA = com.example.services.SyncManager.buildSyncPayloadJsonFromData(
            updatedAt = 1731860000000L,
            themeStyle = com.example.model.AppThemeStyle.NOTHING_OS,
            darkMode = com.example.model.DarkModePreference.DARK,
            agendaStyle = com.example.model.AgendaStyle.SPACIOUS,
            uiScale = 1.12f,
            unboxingMode = com.example.model.GradeUnboxingMode.PLINKO_DROP,
            busLine = "306",
            schoolAddress = "Elde College, Schijndel",
            weatherCity = "Schijndel",
            customScheduleItems = listOf(customItem),
            tasks = listOf(task),
            testChecklists = emptyMap(),
            studySites = emptyList(),
            appointments = emptyList(),
            grades = emptyList(),
            messages = emptyList()
        )
        val payloadB = com.example.services.SyncManager.buildSyncPayloadJsonFromData(
            updatedAt = 1731869999999L,
            themeStyle = com.example.model.AppThemeStyle.NOTHING_OS,
            darkMode = com.example.model.DarkModePreference.DARK,
            agendaStyle = com.example.model.AgendaStyle.SPACIOUS,
            uiScale = 1.12f,
            unboxingMode = com.example.model.GradeUnboxingMode.PLINKO_DROP,
            busLine = "306",
            schoolAddress = "Elde College, Schijndel",
            weatherCity = "Schijndel",
            customScheduleItems = listOf(customItem),
            tasks = listOf(task),
            testChecklists = emptyMap(),
            studySites = emptyList(),
            appointments = emptyList(),
            grades = emptyList(),
            messages = emptyList()
        )
        assertEquals(1, payloadA.getInt("schemaVersion"))
        assertEquals("android", payloadA.getString("source"))
        val settingsObj = payloadA.getJSONObject("settings")
        assertEquals("nothing_os", settingsObj.getString("theme"))
        assertEquals("dark", settingsObj.getString("darkMode"))
        assertEquals("spacious", settingsObj.getString("agendaDensity"))
        assertEquals(112, settingsObj.getInt("uiScale"))
        assertEquals("plinko_drop", settingsObj.getString("unboxingMode"))
        assertEquals("306", settingsObj.getString("busLine"))
        assertTrue(payloadA.has("cachedData"))
        assertEquals(
            com.example.services.SyncManager.computeContentHash(payloadA),
            com.example.services.SyncManager.computeContentHash(payloadB)
        )

        // 4. Merging incoming "web" payload updates AgendaRepository settings & ignores older timestamps
        val repo = AgendaRepository(context)
        val incomingWeb = org.json.JSONObject().apply {
            put("schemaVersion", 1)
            put("source", "web")
            put("updatedAt", 2000000000000L)
            put("settings", org.json.JSONObject().apply {
                put("theme", "one_ui")
                put("darkMode", "light")
                put("agendaDensity", "compact")
                put("uiScale", 90)
                put("unboxingMode", "penalties")
                put("busLine", "156")
                put("schoolAddress", "Stadhuisplein 1, Eindhoven")
            })
        }
        assertTrue(repo.mergeWebSyncPayload(incomingWeb))
        assertEquals(com.example.model.AppThemeStyle.ONE_UI, repo.appThemeStyle.value)
        assertEquals(com.example.model.DarkModePreference.LIGHT, repo.darkModePreference.value)
        assertEquals(com.example.model.AgendaStyle.COMPACT, repo.agendaStyle.value)
        assertEquals(0.90f, repo.uiScale.value, 0.001f)
        assertEquals(com.example.model.GradeUnboxingMode.PENALTIES, repo.gradeUnboxingMode.value)
        assertEquals("156", repo.savedBusLine.value)
        assertEquals("Stadhuisplein 1, Eindhoven", repo.schoolAddress.value?.streetAndCity)

        // Stale payload is skipped
        val staleWeb = org.json.JSONObject().apply {
            put("schemaVersion", 1)
            put("source", "web")
            put("updatedAt", 1000000000000L)
            put("settings", org.json.JSONObject().apply { put("busLine", "999") })
        }
        assertFalse(repo.mergeWebSyncPayload(staleWeb))
        assertEquals("156", repo.savedBusLine.value)

        com.example.services.SyncManager.unpair(context)
        assertFalse(com.example.services.SyncManager.isPaired(context))
    }

    @Test
    fun test_web_sync_payload_builder_lessons_window_and_flat_nonzero_grades() {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fun dateOffset(days: Int): String {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, days)
            return dateFormat.format(cal.time)
        }

        val appointments = listOf(
            com.example.model.Appointment(
                id = 1L,
                title = "Te oud",
                subjectCode = "wi",
                subjectName = "Wiskunde",
                teacherName = "Jansen",
                teacherInitials = "JAN",
                classroom = "A101",
                periodIndex = 1,
                startTime = "08:30",
                endTime = "09:15",
                dayOfWeek = 1,
                dateIso = dateOffset(-30)
            ),
            com.example.model.Appointment(
                id = 2L,
                title = "Vorige week les",
                subjectCode = "ne",
                subjectName = "Nederlands",
                teacherName = "De Vries",
                teacherInitials = "VRI",
                classroom = "B204",
                periodIndex = 2,
                startTime = "09:15",
                endTime = "10:00",
                dayOfWeek = 2,
                isCanceled = true,
                dateIso = dateOffset(-10)
            ),
            com.example.model.Appointment(
                id = 3L,
                title = "Volgende maand binnen bereik",
                subjectCode = "en",
                subjectName = "Engels",
                teacherName = "Bakker",
                teacherInitials = "BAK",
                classroom = "C302",
                periodIndex = 3,
                startTime = "10:20",
                endTime = "11:05",
                dayOfWeek = 3,
                dateIso = dateOffset(20)
            ),
            com.example.model.Appointment(
                id = 4L,
                title = "Te ver vooruit",
                subjectCode = "na",
                subjectName = "Natuurkunde",
                teacherName = "Smit",
                teacherInitials = "SMI",
                classroom = "D101",
                periodIndex = 4,
                startTime = "11:05",
                endTime = "11:50",
                dayOfWeek = 4,
                dateIso = dateOffset(45)
            ),
            com.example.model.Appointment(
                id = 5L,
                title = "Vandaag zonder dateIso",
                subjectCode = "bi",
                subjectName = "Biologie",
                teacherName = "Koster",
                teacherInitials = "KOS",
                classroom = "E002",
                periodIndex = 5,
                startTime = "12:15",
                endTime = "13:00",
                dayOfWeek = 5,
                dateIso = ""
            )
        )

        val grades = listOf(
            com.example.model.SubjectGradeSummary(
                subjectCode = "WI",
                subjectName = "Wiskunde B",
                weightedAverage = 7.8,
                grades = listOf(
                    com.example.model.GradeItem(
                        id = "g_78",
                        subjectCode = "WI",
                        subjectName = "Wiskunde B",
                        score = 7.8,
                        weight = 2.0,
                        description = "Proefwerk Calculus",
                        date = "2026-11-12",
                        period = 1,
                        isPta = true
                    ),
                    com.example.model.GradeItem(
                        id = "g_zero",
                        subjectCode = "WI",
                        subjectName = "Wiskunde B",
                        score = 0.0,
                        weight = 1.0,
                        description = "Lege kolom",
                        date = "2026-11-15",
                        period = 1,
                        isPta = false
                    )
                )
            )
        )

        val messages = listOf(
            com.example.model.MagisterMessage(
                id = "msg_101",
                subject = "Excursie informatie",
                senderName = "Mevr. Van Dijk",
                senderEmail = "vandijk@school.nl",
                dateSent = "18 nov 09:30",
                body = "Vergeet je paspoort niet mee te nemen.",
                isRead = false,
                hasAttachments = true,
                recipients = "Klas 5V, Mentoren",
                rawDateIso = "2026-11-18T09:30:00Z"
            )
        )

        val absences = listOf(
            com.example.model.AbsenceRecord(
                id = "abs_1",
                date = "2026-11-12",
                period = "2e uur",
                type = com.example.model.AbsenceType.TARDY,
                reason = "Te laat in de les",
                isApproved = false,
                startIso = "2026-11-12T09:15:00Z",
                endIso = "2026-11-12T10:00:00Z",
                hour = 2,
                subject = "Wiskunde",
                code = "L"
            )
        )

        val payload = com.example.services.SyncManager.buildSyncPayloadJsonFromData(
            updatedAt = 1731860000000L,
            themeStyle = com.example.model.AppThemeStyle.STANDARD,
            darkMode = com.example.model.DarkModePreference.SYSTEM,
            agendaStyle = com.example.model.AgendaStyle.SPACIOUS,
            uiScale = 1.0f,
            unboxingMode = com.example.model.GradeUnboxingMode.CASE_OPENING,
            busLine = "306",
            schoolAddress = "Elde College",
            weatherCity = "Schijndel",
            customScheduleItems = emptyList(),
            tasks = emptyList(),
            testChecklists = emptyMap(),
            studySites = emptyList(),
            appointments = appointments,
            grades = grades,
            messages = messages,
            unboxedGradeIds = setOf("g_78"),
            absences = absences
        )

        assertEquals("material_you", payload.getString("theme"))
        assertTrue(payload.has("schoolInfo"))

        val cached = payload.getJSONObject("cachedData")
        assertFalse(cached.has("rooster"))
        assertFalse(cached.has("cijfers"))
        assertFalse(cached.has("schedule"))
        assertTrue(cached.has("messages"))
        assertTrue(cached.has("absences"))
        assertTrue(cached.has("verzuim"))

        val lessons = cached.getJSONArray("lessons")
        assertEquals(3, lessons.length())
        val exportedIds = (0 until lessons.length()).map { lessons.getJSONObject(it).getLong("id") }.toSet()
        assertEquals(setOf(2L, 3L, 5L), exportedIds)

        val canceledLesson = (0 until lessons.length())
            .map { lessons.getJSONObject(it) }
            .first { it.getLong("id") == 2L }
        assertEquals("Nederlands", canceledLesson.getString("subject"))
        assertEquals("Nederlands", canceledLesson.getString("vak"))
        assertEquals("De Vries", canceledLesson.getString("teacher"))
        assertEquals("De Vries", canceledLesson.getString("docent"))
        assertEquals("B204", canceledLesson.getString("room"))
        assertEquals("B204", canceledLesson.getString("lokaal"))
        assertEquals(2, canceledLesson.getInt("hour"))
        assertEquals(2, canceledLesson.getInt("lesuur"))
        assertTrue(canceledLesson.getBoolean("cancelled"))
        assertTrue(canceledLesson.getBoolean("uitval"))
        assertTrue(canceledLesson.getString("start").contains("T09:15:00"))
        assertTrue(canceledLesson.getString("end").contains("T10:00:00"))

        val exportedGrades = cached.getJSONArray("grades")
        assertEquals(1, exportedGrades.length())
        val gObj = exportedGrades.getJSONObject(0)
        assertEquals("g_78", gObj.getString("id"))
        assertEquals("Wiskunde B", gObj.getString("subject"))
        assertEquals("Wiskunde B", gObj.getString("vak"))
        assertEquals(7.8, gObj.getDouble("grade"), 0.001)
        assertEquals(7.8, gObj.getDouble("value"), 0.001)
        assertEquals(7.8, gObj.getDouble("score"), 0.001)
        assertEquals("7,8", gObj.getString("gradeDisplay"))
        assertEquals("7,8", gObj.getString("cijfer"))
        assertEquals(2.0, gObj.getDouble("weight"), 0.001)
        assertEquals(2.0, gObj.getDouble("weging"), 0.001)
        assertTrue(gObj.getBoolean("unboxed"))
        assertFalse(gObj.getBoolean("isNew"))

        // Verify full message payload contract
        val exportedMessages = cached.getJSONArray("messages")
        assertEquals(1, exportedMessages.length())
        val mObj = exportedMessages.getJSONObject(0)
        assertEquals("msg_101", mObj.getString("id"))
        assertEquals("Excursie informatie", mObj.getString("subject"))
        assertEquals("Excursie informatie", mObj.getString("onderwerp"))
        assertEquals("Mevr. Van Dijk", mObj.getString("sender"))
        assertEquals("Mevr. Van Dijk", mObj.getString("afzender"))
        assertEquals("Klas 5V, Mentoren", mObj.getString("recipients"))
        assertEquals("Klas 5V, Mentoren", mObj.getString("ontvangers"))
        assertEquals("2026-11-18T09:30:00Z", mObj.getString("date"))
        assertEquals("2026-11-18T09:30:00Z", mObj.getString("verzondenOp"))
        assertEquals("Vergeet je paspoort niet mee te nemen.", mObj.getString("body"))
        assertEquals("Vergeet je paspoort niet mee te nemen.", mObj.getString("inhoud"))
        assertFalse(mObj.getBoolean("read"))
        assertFalse(mObj.getBoolean("gelezen"))

        // Verify absences + verzuim payload contract
        val exportedAbsences = cached.getJSONArray("absences")
        val exportedVerzuim = cached.getJSONArray("verzuim")
        assertEquals(1, exportedAbsences.length())
        assertEquals(1, exportedVerzuim.length())
        val aObj = exportedAbsences.getJSONObject(0)
        assertEquals("abs_1", aObj.getString("id"))
        assertEquals("2026-11-12", aObj.getString("date"))
        assertEquals("2026-11-12", aObj.getString("datum"))
        assertEquals("2026-11-12T09:15:00Z", aObj.getString("start"))
        assertEquals("2026-11-12T09:15:00Z", aObj.getString("begin"))
        assertEquals("2026-11-12T10:00:00Z", aObj.getString("end"))
        assertEquals("2026-11-12T10:00:00Z", aObj.getString("einde"))
        assertEquals(2, aObj.getInt("hour"))
        assertEquals(2, aObj.getInt("lesuur"))
        assertEquals("Wiskunde", aObj.getString("subject"))
        assertEquals("Wiskunde", aObj.getString("vak"))
        assertEquals("Te laat", aObj.getString("type"))
        assertEquals("Te laat", aObj.getString("soort"))
        assertEquals("L", aObj.getString("code"))
        assertEquals("Te laat in de les", aObj.getString("description"))
        assertEquals("Te laat in de les", aObj.getString("omschrijving"))
        assertFalse(aObj.getBoolean("permitted"))
        assertFalse(aObj.getBoolean("geoorloofd"))
    }

    @Test
    fun test_parse_magister_extended_messages_and_absences_metadata() {
        val extApi = com.example.network.MagisterExtendedApi(
            client = okhttp3.OkHttpClient(),
            requestBuilder = { url, _ -> okhttp3.Request.Builder().url(url).build() },
            dateParser = { null }
        )
        val folder = com.example.model.MessageFolderInfo(1L, "Postvak In", 1, com.example.model.MessageFolder.INBOX)
        val rawMessagesJson = """
            {
              "items": [
                {
                  "id": 771,
                  "onderwerp": "Roosterwijziging",
                  "afzender": { "naam": "Dhr. Bakker", "email": "bakker@elde.nl" },
                  "ontvangers": [
                    { "naam": "Jan Jansen" },
                    { "naam": "Klas 4A" }
                  ],
                  "verzondenOp": "2026-11-18T11:20:00Z",
                  "inhoud": "<p>Les verplaatst naar lokaal 104.</p>",
                  "isGelezen": true
                },
                {
                  "id": 772,
                  "onderwerp": "Administratie",
                  "afzender": "Conciërge Balie",
                  "ontvangers": "Alle leerlingen",
                  "verzondenOp": "2026-11-17T08:05:00Z",
                  "korteInhoud": "Sleutel gevonden.",
                  "isGelezen": false
                }
              ]
            }
        """.trimIndent()
        val parsedMsgs = extApi.parseMessagesArray(rawMessagesJson, folder)
        assertEquals(2, parsedMsgs.size)
        assertEquals("Dhr. Bakker", parsedMsgs[0].senderName)
        assertEquals("Jan Jansen, Klas 4A", parsedMsgs[0].recipients)
        assertEquals("2026-11-18T11:20:00Z", parsedMsgs[0].rawDateIso)
        assertEquals("Les verplaatst naar lokaal 104.", parsedMsgs[0].body)
        assertEquals("Conciërge Balie", parsedMsgs[1].senderName)
        assertEquals("Alle leerlingen", parsedMsgs[1].recipients)
        assertEquals("2026-11-17T08:05:00Z", parsedMsgs[1].rawDateIso)

        val rawAbsencesJson = """
            {
              "items": [
                {
                  "id": "abs_99",
                  "start": "2026-11-16T08:30:00Z",
                  "einde": "2026-11-16T09:15:00Z",
                  "lesuurVan": 1,
                  "code": "Z",
                  "omschrijving": "Ziek gemeld",
                  "geoorloofd": true,
                  "afspraak": {
                    "vakken": [{ "naam": "Scheikunde" }]
                  }
                }
              ]
            }
        """.trimIndent()
        val parsedAbsences = MagisterClient().parseAbsencesJson(rawAbsencesJson)
        assertEquals(1, parsedAbsences.size)
        assertEquals("2026-11-16T08:30:00Z", parsedAbsences[0].startIso)
        assertEquals("2026-11-16T09:15:00Z", parsedAbsences[0].endIso)
        assertEquals(1, parsedAbsences[0].hour)
        assertEquals("Scheikunde", parsedAbsences[0].subject)
        assertEquals("Z", parsedAbsences[0].code)
    }

    @Test
    fun test_magister_token_manager_single_flight_and_error_handling() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)
        val fixedNow = 1_700_000_000_000L
        val refreshCount = java.util.concurrent.atomic.AtomicInteger(0)

        try {
            com.example.network.MagisterTokenManager.clockMsProvider = { fixedNow }
            store.clearSession()
            store.saveSessionSync(
                com.example.model.AuthSession(
                    isAuthenticated = true,
                    studentName = "Tester",
                    school = com.example.model.SchoolTenant("1", "Elde", "Schijndel", "elde"),
                    accessToken = "expired_tok",
                    refreshToken = "rt_1",
                    personId = 42L,
                    expiresAt = fixedNow - 10_000L
                )
            )

            com.example.network.MagisterTokenManager.refreshExecutorOverride = { token ->
                assertEquals("rt_1", token)
                refreshCount.incrementAndGet()
                kotlinx.coroutines.delay(50)
                com.example.network.TokenRefreshResult.Success(
                    accessToken = "new_access_1",
                    refreshToken = "rt_2",
                    expiresAtMs = fixedNow + 3_600_000L
                )
            }

            // 5 concurrent callers must coalesce into 1 single-flight refresh call
            val results = coroutineScope {
                (1..5).map {
                    async {
                        com.example.network.MagisterTokenManager.getValidAccessToken(context)
                    }
                }.awaitAll()
            }

            assertEquals(1, refreshCount.get())
            assertTrue(results.all { it is com.example.network.TokenAcquireResult.Valid && it.accessToken == "new_access_1" })
            val savedAfterRefresh = store.loadSession()
            assertEquals("rt_2", savedAfterRefresh.refreshToken)
            assertEquals("new_access_1", savedAfterRefresh.accessToken)

            // Transient failure must preserve existing tokens
            com.example.network.MagisterTokenManager.refreshExecutorOverride = {
                com.example.network.TokenRefreshResult.TransientError(503, "Service unavailable")
            }
            val transient = com.example.network.MagisterTokenManager.getValidAccessToken(context, forceRefresh = true)
            assertTrue(transient is com.example.network.TokenAcquireResult.TransientFailure)
            assertTrue(store.loadSession().isAuthenticated)
            assertEquals("rt_2", store.loadSession().refreshToken)

            // InvalidGrant must clear credentials and require reauth
            com.example.network.MagisterTokenManager.refreshExecutorOverride = {
                com.example.network.TokenRefreshResult.InvalidGrant("invalid_grant")
            }
            val invalid = com.example.network.MagisterTokenManager.getValidAccessToken(context, forceRefresh = true)
            assertTrue(invalid is com.example.network.TokenAcquireResult.RequiresReauth)
            assertFalse(store.loadSession().isAuthenticated)
        } finally {
            com.example.network.MagisterTokenManager.refreshExecutorOverride = null
            com.example.network.MagisterTokenManager.clockMsProvider = { System.currentTimeMillis() }
            store.clearSession()
        }
    }

    @Test
    fun test_changed_lesson_diffs_persistence_and_pruning() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)
        store.clearSession()

        store.mergeChangedLessonDiffs(
            newDiffs = mapOf(101L to "Lokaal 10 -> 22", 102L to "Uitval (1e uur)"),
            activeLessonIds = setOf(101L, 102L)
        )
        val loaded = store.getChangedLessonDiffs()
        assertEquals(2, loaded.size)
        assertEquals("Lokaal 10 -> 22", loaded[101L])

        // Lesson 102 drops out of active lessons -> gets pruned
        store.mergeChangedLessonDiffs(
            newDiffs = emptyMap(),
            activeLessonIds = setOf(101L)
        )
        val afterPrune = store.getChangedLessonDiffs()
        assertEquals(1, afterPrune.size)
        assertTrue(afterPrune.containsKey(101L))
        assertFalse(afterPrune.containsKey(102L))
    }

    @Test
    fun test_gap_share_payload_zero_pii_and_overlap_matching() {
        val today = "2026-11-18"
        val myAppts = listOf(
            com.example.model.Appointment(
                id = 1L, title = "Nederlands", subjectCode = "NE", subjectName = "Nederlands",
                teacherName = "Jansen", teacherInitials = "JAN", classroom = "B102",
                periodIndex = 1, startTime = "08:30", endTime = "09:20", dayOfWeek = 3,
                isCanceled = false, dateIso = today
            ),
            com.example.model.Appointment(
                id = 2L, title = "Wiskunde", subjectCode = "WI", subjectName = "Wiskunde",
                teacherName = "Bakker", teacherInitials = "BAK", classroom = "C204",
                periodIndex = 2, startTime = "09:20", endTime = "10:10", dayOfWeek = 3,
                isCanceled = true, dateIso = today
            ),
            // Period 3 omitted intentionally -> becomes "tussenuur"
            com.example.model.Appointment(
                id = 4L, title = "Engels", subjectCode = "EN", subjectName = "Engels",
                teacherName = "De Vries", teacherInitials = "VRI", classroom = "A101",
                periodIndex = 4, startTime = "11:20", endTime = "12:10", dayOfWeek = 3,
                isCanceled = false, dateIso = today
            )
        )

        val json = com.example.services.SyncManager.buildGapSharePayloadJson(
            alias = "Sam",
            appointments = myAppts,
            todayIsoOverride = today
        )
        assertEquals("agendaplus_gaps_v1", json.getString("schema"))
        assertEquals("Sam", json.getString("alias"))

        val slots = json.getJSONArray("freeSlots")
        assertEquals(2, slots.length())
        val raw = json.toString()
        assertFalse(raw.contains("grades"))
        assertFalse(raw.contains("homework"))
        assertFalse(raw.contains("Bakker"))
        assertFalse(raw.contains("C204"))
        assertFalse(raw.contains("Wiskunde"))

        val friend = com.example.services.SyncManager.ClassmateGapSchedule(
            alias = "Kim",
            generatedAt = 1234L,
            freeSlots = listOf(
                com.example.services.SyncManager.GapSlot(
                    dateIso = today, period = 3, startTime = "10:30", endTime = "11:20",
                    kind = "tussenuur", label = "3e uur (tussenuur)"
                )
            )
        )
        val overlaps = com.example.services.SyncManager.findOverlappingGaps(myAppts, friend, todayIsoOverride = today)
        assertEquals(1, overlaps.size)
        assertEquals(3, overlaps.first().period)
        assertEquals("tussenuur", overlaps.first().myKind)
    }

    @Test
    fun test_render_single_grade_unboxing_card_png_across_themes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val grade = com.example.model.GradeItem(
            id = "g_99",
            subjectCode = "WI",
            subjectName = "wiskunde B",
            score = 8.6,
            weight = 3.0,
            description = "SE Kansrekening",
            date = "18-11-2026",
            period = 2,
            isPta = true
        )

        com.example.model.AppThemeStyle.entries.forEach { style ->
            val file = com.example.util.GradeExporter.renderSingleGradeUnboxingCardPng(
                context = context,
                grade = grade,
                studentName = "Sam",
                appThemeStyle = style
            )
            try {
                assertTrue("PNG file must exist for $style", file.exists())
                assertTrue("PNG file must be >1KB for $style", file.length() > 1000L)
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun test_weekly_focus_minutes_and_commute_backup_round_trip() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        val initial = store.getWeeklyFocusMinutes()
        val afterFirst = store.addWeeklyFocusMinutes(25)
        assertEquals(initial + 25, afterFirst)
        val afterSecond = store.addWeeklyFocusMinutes(15)
        assertEquals(initial + 40, afterSecond)

        val customModes = listOf(
            com.example.model.CommuteModeConfig("bus_1", com.example.model.CommuteModeType.BUS, "Lijn 156", busLine = "156"),
            com.example.model.CommuteModeConfig("bus_2", com.example.model.CommuteModeType.BUS, "Lijn 306", busLine = "306"),
            com.example.model.CommuteModeConfig("bike_1", com.example.model.CommuteModeType.BIKE, "Fiets", bikeMinutes = 30)
        )
        store.saveCommuteModes(customModes)
        store.setActiveCommuteModeId("bus_2")
        store.setMorningLockNotificationEnabled(false)

        val backupJson = store.exportChoicesBackupJson()
        // Mutate before restoring
        store.saveCommuteModes(listOf(com.example.model.CommuteModeConfig("bike_only", com.example.model.CommuteModeType.BIKE, "Fiets", bikeMinutes = 10)))
        store.setActiveCommuteModeId("bike_only")
        store.setMorningLockNotificationEnabled(true)

        val restoredResult = store.importChoicesBackupJson(backupJson)
        assertTrue(restoredResult.isSuccess)
        val restoredModes = store.getCommuteModes()
        assertEquals(3, restoredModes.size)
        assertEquals("156", restoredModes[0].busLine)
        assertEquals("306", restoredModes[1].busLine)
        assertEquals(30, restoredModes[2].bikeMinutes)
        assertEquals("bus_2", store.getActiveCommuteModeId())
        assertFalse(store.isMorningLockNotificationEnabled())
    }

    @Test
    fun test_homework_widget_toggle_task_broadcast() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)
        val tasks = listOf(
            com.example.model.HomeworkTask(
                id = "t1",
                subject = "engels",
                title = "Vocabulaire H3",
                description = "Woordjes leren",
                dueDay = "2026-11-20",
                dueDate = "Vr 20 nov",
                category = com.example.model.TaskCategory.HOMEWORK,
                isCompleted = false
            ),
            com.example.model.HomeworkTask(
                id = "t2",
                subject = "Wiskunde",
                title = "Opgave 4",
                description = "Maken",
                dueDay = "2026-11-21",
                dueDate = "Za 21 nov",
                category = com.example.model.TaskCategory.HOMEWORK,
                isCompleted = true
            )
        )
        store.saveTasks(tasks)

        val intent = android.content.Intent(com.example.widget.HomeworkWidgetProvider.ACTION_TOGGLE_TASK).apply {
            putExtra(com.example.widget.HomeworkWidgetProvider.EXTRA_TASK_ID, "t1")
        }
        com.example.widget.HomeworkWidgetProvider().onReceive(context, intent)

        val reloaded = store.loadTasks()
        assertTrue(reloaded.first { it.id == "t1" }.isCompleted)
        assertTrue(reloaded.first { it.id == "t2" }.isCompleted)
        assertTrue(store.getLocalSyncUpdatedAt() > 0L)
    }

    @Test
    fun test_metro_and_ios_themes_subject_customizations_and_web_sync_round_trip() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.example.data.AppPreferencesStore(context)

        // 1. Theme preferences, MetroAccent & Widget rendering under new themes
        store.setAppThemeStyle(com.example.model.AppThemeStyle.WINDOWS_PHONE)
        store.setMetroAccent(com.example.model.MetroAccent.EMERALD)
        store.setUseSubjectColorsForMetroTiles(true)
        assertEquals(com.example.model.AppThemeStyle.WINDOWS_PHONE, store.getAppThemeStyle())
        assertEquals(com.example.model.MetroAccent.EMERALD, store.getMetroAccent())
        assertTrue(store.isUseSubjectColorsForMetroTiles())
        com.example.widget.WidgetUpdater.updateAllWidgets(context)

        store.setAppThemeStyle(com.example.model.AppThemeStyle.IOS_LIQUID_GLASS)
        assertEquals(com.example.model.AppThemeStyle.IOS_LIQUID_GLASS, store.getAppThemeStyle())
        com.example.widget.WidgetUpdater.updateAllWidgets(context)

        // 2. Wire mapping & JSON serialization round-trip for subjectCustomizations
        assertEquals("windows_phone", com.example.services.SyncManager.themeToWire(com.example.model.AppThemeStyle.WINDOWS_PHONE))
        assertEquals("ios_liquid_glass", com.example.services.SyncManager.themeToWire(com.example.model.AppThemeStyle.IOS_LIQUID_GLASS))
        assertEquals(com.example.model.AppThemeStyle.WINDOWS_PHONE, com.example.services.SyncManager.themeFromWire("windows_phone"))
        assertEquals(com.example.model.AppThemeStyle.IOS_LIQUID_GLASS, com.example.services.SyncManager.themeFromWire("ios_liquid_glass"))
        assertEquals(com.example.model.MetroAccent.CRIMSON, com.example.services.SyncManager.metroAccentFromWire("crimson"))

        val customSubjects = mapOf(
            "wi" to com.example.model.SubjectCustomization(
                code = "wi",
                nickname = "Wiskunde B",
                colorHex = 0xFF1BA1E2L,
                iconName = "functions",
                showOriginalCode = false
            )
        )
        val payload = com.example.services.SyncManager.buildSyncPayloadJsonFromData(
            updatedAt = 1732000000000L,
            themeStyle = com.example.model.AppThemeStyle.WINDOWS_PHONE,
            darkMode = com.example.model.DarkModePreference.DARK,
            agendaStyle = com.example.model.AgendaStyle.COMPACT,
            uiScale = 1.0f,
            unboxingMode = com.example.model.GradeUnboxingMode.CASE_OPENING,
            busLine = "",
            schoolAddress = "",
            weatherCity = "",
            customScheduleItems = emptyList(),
            tasks = emptyList(),
            testChecklists = emptyMap(),
            studySites = emptyList(),
            appointments = emptyList(),
            grades = emptyList(),
            messages = emptyList(),
            metroAccent = com.example.model.MetroAccent.AMBER,
            smartSubjectNames = false,
            useSubjectColorsForMetroTiles = true,
            subjectCustomizations = customSubjects
        )
        val settingsObj = payload.getJSONObject("settings")
        assertEquals("windows_phone", settingsObj.getString("theme"))
        assertEquals("amber", settingsObj.getString("metroAccent"))
        assertFalse(settingsObj.getBoolean("smartSubjectNames"))
        assertTrue(settingsObj.getBoolean("useSubjectColorsForMetroTiles"))

        val parsedCustoms = com.example.model.SubjectCustomizationCatalog.fromJsonObject(
            payload.getJSONObject("cachedData").getJSONObject("subjectCustomizations")
        )
        assertEquals(1, parsedCustoms.size)
        assertEquals("Wiskunde B", parsedCustoms["wi"]?.nickname)
        assertEquals(0xFF1BA1E2L, parsedCustoms["wi"]?.colorHex)
        assertEquals("functions", parsedCustoms["wi"]?.iconName)
        assertFalse(parsedCustoms["wi"]?.showOriginalCode ?: true)

        // 3. Incoming Web payload merge updates repository theme, Metro accent, and subject customizations
        val repo = AgendaRepository(context)
        val incomingWeb = org.json.JSONObject().apply {
            put("schemaVersion", 1)
            put("source", "web")
            put("updatedAt", 2100000000000L)
            put("settings", org.json.JSONObject().apply {
                put("theme", "ios_liquid_glass")
                put("metroAccent", "crimson")
                put("smartSubjectNames", true)
                put("useSubjectColorsForMetroTiles", false)
            })
            put("cachedData", org.json.JSONObject().apply {
                put("subjectCustomizations", com.example.model.SubjectCustomizationCatalog.toJsonObject(customSubjects))
            })
        }
        assertTrue(repo.mergeWebSyncPayload(incomingWeb))
        assertEquals(com.example.model.AppThemeStyle.IOS_LIQUID_GLASS, repo.appThemeStyle.value)
        assertEquals(com.example.model.MetroAccent.CRIMSON, repo.metroAccent.value)
        assertTrue(repo.smartDefaultNamesEnabled.value)
        assertFalse(repo.useSubjectColorsForMetroTiles.value)
        assertEquals("Wiskunde B", repo.subjectCustomizations.value["wi"]?.nickname)
    }
}

