package com.example

import com.example.model.AgendaStyle
import com.example.model.DisruptionType
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
import kotlinx.coroutines.flow.first
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
    fun test_firebase_cloud_profile_serialization_safe_fallbacks_and_config() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Provisioned named Firestore database ID & Google logo drawable exist
        assertEquals(
            "ai-studio-agendaplussite-6a81a79c-1603-4f73-8a9f-956af5b3139b",
            context.getString(R.string.firestore_database_id)
        )
        assertEquals(
            "361465857069-v8miibh69ffvuni8brea6gtug9thgucm.apps.googleusercontent.com",
            context.getString(R.string.google_web_client_id)
        )
        assertEquals(
            "361465857069-v8miibh69ffvuni8brea6gtug9thgucm.apps.googleusercontent.com",
            com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID
        )
        assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_google_logo))
        assertNotNull(com.example.services.FirebaseService(context))

        // 2. UserCloudSettings round-trip & bounds clamping
        val customSettings = com.example.services.UserCloudSettings(
            appThemeStyle = com.example.model.AppThemeStyle.ONE_UI.name,
            darkModePreference = com.example.model.DarkModePreference.DARK.name,
            agendaStyle = com.example.model.AgendaStyle.SPACIOUS.name,
            uiScale = 1.15,
            eloUrl = "https://elo.example.nl/portaal",
            savedBusLine = "156",
            uitvalNotificationsEnabled = false,
            gradeNotificationsEnabled = true,
            testNotificationsEnabled = true,
            quietHoursEnabled = true,
            quietHoursStartMinutes = 23 * 60,
            quietHoursEndMinutes = 6 * 60 + 45,
            mutedSubjects = listOf("LO", "Rekenen"),
            smartBusAlertsEnabled = false,
            busWalkMinutes = 8,
            busArrivalBufferMinutes = 12,
            gradeUnboxingEnabled = true,
            gradeUnboxingMode = com.example.model.GradeUnboxingMode.PLINKO_DROP.name
        )
        val firestoreSettingsMap = customSettings.toFirestoreMap()
        val parsedSettings = com.example.services.UserCloudSettings.fromMap(firestoreSettingsMap)
        assertEquals(com.example.model.AppThemeStyle.ONE_UI, parsedSettings.resolvedAppThemeStyle())
        assertEquals(com.example.model.DarkModePreference.DARK, parsedSettings.resolvedDarkModePreference())
        assertEquals(com.example.model.AgendaStyle.SPACIOUS, parsedSettings.resolvedAgendaStyle())
        assertEquals(com.example.model.GradeUnboxingMode.PLINKO_DROP, parsedSettings.resolvedGradeUnboxingMode())
        assertEquals("156", parsedSettings.savedBusLine)
        assertEquals(listOf("LO", "Rekenen"), parsedSettings.mutedSubjects)
        assertEquals(8, parsedSettings.busWalkMinutes)

        // 3. Safe enum fallback on unknown/corrupted string values
        val invalidSettings = com.example.services.UserCloudSettings.fromMap(
            mapOf(
                "appThemeStyle" to "UNKNOWN_THEME",
                "darkModePreference" to "INVALID_MODE",
                "agendaStyle" to "NOPE",
                "gradeUnboxingMode" to "BROKEN_MODE",
                "uiScale" to 5.0
            )
        )
        assertEquals(com.example.model.AppThemeStyle.STANDARD, invalidSettings.resolvedAppThemeStyle())
        assertEquals(com.example.model.DarkModePreference.SYSTEM, invalidSettings.resolvedDarkModePreference())
        assertEquals(com.example.model.AgendaStyle.COMPACT, invalidSettings.resolvedAgendaStyle())
        assertEquals(com.example.model.GradeUnboxingMode.CASE_OPENING, invalidSettings.resolvedGradeUnboxingMode())
        assertEquals(1.20, invalidSettings.uiScale, 0.001)

        // 4. UserCloudProfile create/update map invariants (no "id", immutable "uid" & "createdAt" omitted on update)
        val profile = com.example.services.UserCloudProfile(
            uid = "uid_test_123",
            email = "leerling@example.nl",
            displayName = "Jan Jansen",
            studentName = "Jan Jansen",
            studentId = "123456",
            studentClass = "4V2",
            personId = 9876L,
            schoolId = "elde",
            schoolName = "Elde College",
            schoolCity = "Schijndel",
            schoolSubdomain = "elde",
            magisterAccessToken = "token_abc_123",
            magisterRefreshToken = "refresh_xyz_789",
            magisterAuthenticated = true,
            settings = customSettings
        )
        val createMap = profile.toCreateMap()
        assertFalse(createMap.containsKey("id"))
        assertEquals("uid_test_123", createMap["uid"])
        assertTrue(createMap.containsKey("createdAt"))
        assertTrue(createMap.containsKey("updatedAt"))

        val updateMap = profile.toUpdateMap()
        assertFalse(updateMap.containsKey("id"))
        assertFalse(updateMap.containsKey("uid"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertTrue(updateMap.containsKey("updatedAt"))
        assertEquals("Elde College", updateMap["schoolName"])

        // 5. toAuthSessionOrNull() returns null when unauthenticated/blank and valid AuthSession otherwise
        assertNull(profile.copy(magisterAuthenticated = false).toAuthSessionOrNull())
        assertNull(profile.copy(magisterAccessToken = "").toAuthSessionOrNull())

        val restoredSession = profile.toAuthSessionOrNull()
        assertNotNull(restoredSession)
        assertTrue(restoredSession!!.isAuthenticated)
        assertEquals("Jan Jansen", restoredSession.studentName)
        assertEquals("Elde College", restoredSession.school?.name)
        assertEquals("elde", restoredSession.school?.subdomain)
        assertEquals("token_abc_123", restoredSession.accessToken)
        assertEquals("refresh_xyz_789", restoredSession.refreshToken)
        assertEquals(9876L, restoredSession.personId)
    }
}



