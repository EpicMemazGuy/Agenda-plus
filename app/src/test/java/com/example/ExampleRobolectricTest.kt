package com.example

import com.example.model.AgendaStyle
import com.example.model.DisruptionType
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.cleanSchoolText
import com.example.network.MagisterClient
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
}

