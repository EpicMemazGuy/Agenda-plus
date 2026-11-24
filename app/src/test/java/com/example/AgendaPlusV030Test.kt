package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.automation.CalendarBridgeManager
import com.example.automation.IcsScope
import com.example.automation.LessonDndScheduler
import com.example.data.AppPreferencesStore
import com.example.model.Appointment
import com.example.model.AppThemeStyle
import com.example.model.AxolaBackgroundStyle
import com.example.model.AxolaBorderStyle
import com.example.model.AxolaCustomTheme
import com.example.model.AxolaTypographyStyle
import com.example.model.CalendarBridgeConfig
import com.example.model.GradeItem
import com.example.model.HomeworkTask
import com.example.model.LessonDndConfig
import com.example.model.LessonDndMode
import com.example.model.MetroAccent
import com.example.model.SubjectCustomization
import com.example.model.SubjectCustomizationState
import com.example.model.TaskCategory
import com.example.ui.screens.computeTodayHeroStatus
import com.example.util.DayStoryMode
import com.example.util.RoosterExportAspect
import com.example.util.RoosterExportScope
import com.example.util.StoryAndScheduleExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgendaPlusV030Test {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // =========================================================================
    // 1. AXOLA Studio Theme Builder & Codec Tests
    // =========================================================================

    @Test
    fun test_axola_custom_theme_codec_round_trip() {
        val original = AxolaCustomTheme(
            basePreset = AppThemeStyle.NOTHING_OS,
            cornerRadiusDp = 18,
            backgroundStyle = AxolaBackgroundStyle.DEEP_SLATE,
            primaryAccentHex = "#00FFA3",
            secondaryAccentHex = "#7928CA",
            borderStyle = AxolaBorderStyle.NEON_GLOW,
            typographyStyle = AxolaTypographyStyle.TERMINAL_MONO
        )

        val code = original.encodeToThemeCode()
        assertTrue("Theme code must start with prefix", code.startsWith("AXOLA-THEME://"))

        val decodedResult = AxolaCustomTheme.decodeFromThemeCode(code)
        assertTrue("Decoding valid theme code must succeed", decodedResult.isSuccess)

        val decoded = decodedResult.getOrThrow()
        assertEquals(original.basePreset, decoded.basePreset)
        assertEquals(original.cornerRadiusDp, decoded.cornerRadiusDp)
        assertEquals(original.backgroundStyle, decoded.backgroundStyle)
        assertEquals(original.primaryAccentHex, decoded.primaryAccentHex)
        assertEquals(original.secondaryAccentHex, decoded.secondaryAccentHex)
        assertEquals(original.borderStyle, decoded.borderStyle)
        assertEquals(original.typographyStyle, decoded.typographyStyle)
    }

    @Test
    fun test_axola_custom_theme_codec_invalid_inputs_rejected() {
        assertFalse(AxolaCustomTheme.decodeFromThemeCode("").isSuccess)
        assertFalse(AxolaCustomTheme.decodeFromThemeCode("HTTP://example.com").isSuccess)
        assertFalse(AxolaCustomTheme.decodeFromThemeCode("AXOLA-THEME://not_valid_json_base64!").isSuccess)
        assertFalse(AxolaCustomTheme.decodeFromThemeCode("AXOLA-THEME://e30=").isSuccess) // empty json "{}"
    }

    // =========================================================================
    // 2. Calendar Bridge & .ICS RFC 5545 Tests
    // =========================================================================

    @Test
    fun test_calendar_ics_content_rfc5545_compliance() {
        val appts = listOf(
            Appointment(
                id = 101L,
                title = "Wi - wfre - emkg2b",
                subjectCode = "wi",
                subjectName = "Wiskunde B",
                teacherName = "W. Frederik",
                teacherInitials = "WFRE",
                classroom = "G0411",
                periodIndex = 2,
                startTime = "09:20",
                endTime = "10:10",
                dayOfWeek = 1,
                dateIso = "2026-11-23",
                homework = "Opgaven 14 t/m 28"
            ),
            Appointment(
                id = 102L,
                title = "Ne - mvdl - emkg2b",
                subjectCode = "ne",
                subjectName = "Nederlands",
                teacherName = "M. van der Laan",
                teacherInitials = "MVDL",
                classroom = "A102",
                periodIndex = 3,
                startTime = "10:30",
                endTime = "11:20",
                dayOfWeek = 1,
                dateIso = "2026-11-23",
                isCanceled = true
            )
        )

        val customState = SubjectCustomizationState(
            customizations = mapOf(
                "wi" to SubjectCustomization("wi", "Wiskunde Turbo", 0xFF1BA1E2L)
            ),
            smartDefaultNamesEnabled = true
        )

        // Test with hideCanceled = true
        val icsOmitCanceled = CalendarBridgeManager.buildIcsContent(
            appointments = appts,
            customItems = emptyList(),
            scope = IcsScope.ACTIVE_WEEK,
            customState = customState,
            hideCanceled = true,
            referenceDateIso = "2026-11-23"
        )

        assertTrue(icsOmitCanceled.contains("BEGIN:VCALENDAR"))
        assertTrue(icsOmitCanceled.contains("VERSION:2.0"))
        assertTrue(icsOmitCanceled.contains("PRODID:-//Agenda+//Schoolrooster//NL"))
        assertTrue(icsOmitCanceled.contains("SUMMARY:Wiskunde Turbo"))
        assertTrue(icsOmitCanceled.contains("LOCATION:Lokaal G0411"))
        assertTrue(icsOmitCanceled.contains("Docent: W. Frederik"))
        assertFalse("Canceled class should be omitted when hideCanceled = true", icsOmitCanceled.contains("Nederlands"))
        assertTrue(icsOmitCanceled.contains("END:VCALENDAR"))

        // Test with hideCanceled = false
        val icsWithCanceled = CalendarBridgeManager.buildIcsContent(
            appointments = appts,
            customItems = emptyList(),
            scope = IcsScope.ACTIVE_WEEK,
            customState = customState,
            hideCanceled = false,
            referenceDateIso = "2026-11-23"
        )
        assertTrue(icsWithCanceled.contains("[UITVAL]"))
        assertTrue(icsWithCanceled.contains("Nederlands"))
    }

    // =========================================================================
    // 3. Lesson DND Scheduler (Silent-in-Class) Tests
    // =========================================================================

    @Test
    fun test_lesson_dnd_window_computation_and_merging() {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val config = LessonDndConfig(
            enabled = true,
            mode = LessonDndMode.FULL_DND,
            startBufferMinutes = 5,
            endBufferMinutes = 2,
            forceDndDuringExams = true
        )

        val appts = listOf(
            // Lesson 1: 08:30 - 09:20
            Appointment(
                id = 201L,
                title = "Wiskunde",
                subjectCode = "wi",
                subjectName = "Wiskunde",
                teacherName = "Docent 1",
                teacherInitials = "D1",
                classroom = "101",
                periodIndex = 1,
                startTime = "08:30",
                endTime = "09:20",
                dayOfWeek = 1,
                dateIso = todayIso
            ),
            // Lesson 2: 09:20 - 10:10 (Contiguous with Lesson 1 -> should merge!)
            Appointment(
                id = 202L,
                title = "Wiskunde",
                subjectCode = "wi",
                subjectName = "Wiskunde",
                teacherName = "Docent 1",
                teacherInitials = "D1",
                classroom = "101",
                periodIndex = 2,
                startTime = "09:20",
                endTime = "10:10",
                dayOfWeek = 1,
                dateIso = todayIso
            ),
            // Lesson 3: 11:20 - 12:10 (Separated by gap -> distinct window)
            Appointment(
                id = 203L,
                title = "Engels",
                subjectCode = "en",
                subjectName = "Engels",
                teacherName = "Docent 2",
                teacherInitials = "D2",
                classroom = "102",
                periodIndex = 4,
                startTime = "11:20",
                endTime = "12:10",
                dayOfWeek = 1,
                dateIso = todayIso
            ),
            // Lesson 4: Canceled -> should be skipped!
            Appointment(
                id = 204L,
                title = "Geschiedenis",
                subjectCode = "gs",
                subjectName = "Geschiedenis",
                teacherName = "Docent 3",
                teacherInitials = "D3",
                classroom = "103",
                periodIndex = 5,
                startTime = "12:30",
                endTime = "13:20",
                dayOfWeek = 1,
                dateIso = todayIso,
                isCanceled = true
            )
        )

        val tz = TimeZone.getTimeZone("Europe/Amsterdam")
        val nowCal = Calendar.getInstance(tz).apply {
            val parts = todayIso.split("-")
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morningEpoch = nowCal.timeInMillis

        val windows = LessonDndScheduler.computeSilentWindows(appts, config, nowMs = morningEpoch)
        assertEquals("Contiguous lessons should merge and canceled lesson skipped, leaving 2 windows", 2, windows.size)

        val firstMerged = windows[0]
        val expectedStartMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(todayIso, "08:30", tz)!! - (5 * 60_000L)
        val expectedEndMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(todayIso, "10:10", tz)!! + (2 * 60_000L)
        assertEquals(expectedStartMs, firstMerged.startMs)
        assertEquals(expectedEndMs, firstMerged.endMs)

        val secondWindow = windows[1]
        val expectedSecondStartMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(todayIso, "11:20", tz)!! - (5 * 60_000L)
        val expectedSecondEndMs = CalendarBridgeManager.parseAmsterdamDateTimeMs(todayIso, "12:10", tz)!! + (2 * 60_000L)
        assertEquals(expectedSecondStartMs, secondWindow.startMs)
        assertEquals(expectedSecondEndMs, secondWindow.endMs)
    }

    // =========================================================================
    // 4. Story & Schedule Exporter Canvas Rendering Tests
    // =========================================================================

    @Test
    fun test_render_day_story_png() {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val appts = listOf(
            Appointment(
                id = 301L,
                title = "Wiskunde",
                subjectCode = "wi",
                subjectName = "Wiskunde B",
                teacherName = "Bakker",
                teacherInitials = "BAK",
                classroom = "204",
                periodIndex = 1,
                startTime = "08:30",
                endTime = "09:20",
                dayOfWeek = 1,
                dateIso = todayIso
            )
        )

        val png = StoryAndScheduleExporter.renderDayOrExamStoryPng(
            context = context,
            appointments = appts,
            tasks = emptyList(),
            targetDateIso = todayIso,
            mode = DayStoryMode.TODAY_SCHEDULE,
            hideTeacherAndRoom = false,
            studentName = "Alex",
            customizationState = SubjectCustomizationState(),
            themeStyle = AppThemeStyle.NOTHING_OS
        )

        assertNotNull(png)
        assertTrue("Day Story PNG must exist", png.exists())
        assertTrue("Day Story PNG must have non-zero size", png.length() > 5000L)
    }

    @Test
    fun test_render_grade_story_png() {
        val grade = GradeItem(
            id = "g_test_story",
            subjectCode = "wi",
            subjectName = "Wiskunde B",
            score = 8.5,
            weight = 2.0,
            description = "Hoofdstuk 4 Analytische Meetkunde",
            date = "2026-11-20",
            period = 1
        )

        val png = StoryAndScheduleExporter.renderGradeStoryPng(
            context = context,
            grade = grade,
            subjectAverage = 8.2,
            overallAverage = 7.9,
            studentName = "Alex",
            customizationState = SubjectCustomizationState(),
            themeStyle = AppThemeStyle.ONE_UI,
            isDarkMode = true
        )

        assertNotNull(png)
        assertTrue("Grade Story PNG must exist", png.exists())
        assertTrue("Grade Story PNG must have non-zero size", png.length() > 5000L)
    }

    @Test
    fun test_render_rooster_png_aspect_ratios_and_scopes() {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val appts = listOf(
            Appointment(
                id = 401L,
                title = "Engels",
                subjectCode = "en",
                subjectName = "Engels",
                teacherName = "De Vries",
                teacherInitials = "DVR",
                classroom = "101",
                periodIndex = 1,
                startTime = "08:30",
                endTime = "09:20",
                dayOfWeek = 1,
                dateIso = todayIso
            )
        )

        // 1. Lockscreen 9:19.5 (1080x2340)
        val lockscreenPng = StoryAndScheduleExporter.renderRoosterPng(
            context = context,
            appointments = appts,
            referenceDateIso = todayIso,
            scope = RoosterExportScope.DAY,
            aspect = RoosterExportAspect.LOCKSCREEN_9_195,
            useSubjectCustomizations = true,
            studentName = "Alex",
            customizationState = SubjectCustomizationState(),
            themeStyle = AppThemeStyle.WINDOWS_PHONE,
            metroAccent = MetroAccent.COBALT
        )
        assertTrue("Lockscreen PNG must exist", lockscreenPng.exists())
        assertTrue(lockscreenPng.length() > 5000L)

        // 2. Compact 4:5 (1080x1350)
        val compactPng = StoryAndScheduleExporter.renderRoosterPng(
            context = context,
            appointments = appts,
            referenceDateIso = todayIso,
            scope = RoosterExportScope.THREE_DAYS,
            aspect = RoosterExportAspect.COMPACT_4_5,
            useSubjectCustomizations = true,
            studentName = "Alex",
            customizationState = SubjectCustomizationState(),
            themeStyle = AppThemeStyle.IOS_LIQUID_GLASS
        )
        assertTrue("Compact PNG must exist", compactPng.exists())
        assertTrue(compactPng.length() > 5000L)

        // 3. Wide 16:9 (1920x1080)
        val widePng = StoryAndScheduleExporter.renderRoosterPng(
            context = context,
            appointments = appts,
            referenceDateIso = todayIso,
            scope = RoosterExportScope.WEEK,
            aspect = RoosterExportAspect.WIDE_16_9,
            useSubjectCustomizations = true,
            studentName = "Alex",
            customizationState = SubjectCustomizationState(),
            themeStyle = AppThemeStyle.STANDARD
        )
        assertTrue("Wide PNG must exist", widePng.exists())
        assertTrue(widePng.length() > 5000L)
    }

    // =========================================================================
    // 5. AppPreferencesStore v0.3.0 Engine Persistence & Backup Export/Import
    // =========================================================================

    @Test
    fun test_v030_preferences_and_backup_roundtrip() {
        val store = AppPreferencesStore(context)

        // 1. Calendar Bridge Config
        val customCal = CalendarBridgeConfig(
            enabled = true,
            hideCanceledLessons = true,
            lastSyncedAtMs = 123456789L,
            lastSyncedEventCount = 14
        )
        store.saveCalendarBridgeConfig(customCal)
        val loadedCal = store.getCalendarBridgeConfig()
        assertEquals(true, loadedCal.enabled)
        assertEquals(true, loadedCal.hideCanceledLessons)
        assertEquals(123456789L, loadedCal.lastSyncedAtMs)
        assertEquals(14, loadedCal.lastSyncedEventCount)

        // 2. Lesson DND Config
        val customDnd = LessonDndConfig(
            enabled = true,
            mode = LessonDndMode.VIBRATE,
            startBufferMinutes = 3,
            endBufferMinutes = 4,
            forceDndDuringExams = false
        )
        store.saveLessonDndConfig(customDnd)
        val loadedDnd = store.getLessonDndConfig()
        assertEquals(true, loadedDnd.enabled)
        assertEquals(LessonDndMode.VIBRATE, loadedDnd.mode)
        assertEquals(3, loadedDnd.startBufferMinutes)
        assertEquals(4, loadedDnd.endBufferMinutes)
        assertEquals(false, loadedDnd.forceDndDuringExams)

        // 3. AXOLA Custom Theme
        val customTheme = AxolaCustomTheme(
            basePreset = AppThemeStyle.NOTHING_OS,
            cornerRadiusDp = 12,
            backgroundStyle = AxolaBackgroundStyle.DEEP_SLATE,
            primaryAccentHex = "#E51400",
            secondaryAccentHex = "#00FFA3",
            borderStyle = AxolaBorderStyle.SUBTLE_1DP,
            typographyStyle = AxolaTypographyStyle.TERMINAL_MONO
        )
        store.saveAxolaCustomTheme(customTheme)
        val loadedTheme = store.getAxolaCustomTheme()
        assertEquals(AppThemeStyle.NOTHING_OS, loadedTheme.basePreset)
        assertEquals(12, loadedTheme.cornerRadiusDp)
        assertEquals(AxolaBackgroundStyle.DEEP_SLATE, loadedTheme.backgroundStyle)
        assertEquals("#E51400", loadedTheme.primaryAccentHex)
        assertEquals(AxolaBorderStyle.SUBTLE_1DP, loadedTheme.borderStyle)

        val backupJson = store.exportChoicesBackupJson()
        assertTrue(backupJson.contains("calendarBridgeEnabled"))
        assertTrue(backupJson.contains("lessonDndConfig"))
        assertTrue(backupJson.contains("axolaCustomTheme"))
        assertTrue(backupJson.contains("E51400"))

        // Reset to defaults
        store.saveCalendarBridgeConfig(CalendarBridgeConfig())
        store.saveLessonDndConfig(LessonDndConfig())
        store.saveAxolaCustomTheme(AxolaCustomTheme())

        // Restore backup and verify roundtrip
        val restoreResult = store.importChoicesBackupJson(backupJson)
        assertTrue("Restoring v0.3.0 backup must succeed", restoreResult.isSuccess)

        assertEquals(true, store.getCalendarBridgeConfig().enabled)
        assertEquals(LessonDndMode.VIBRATE, store.getLessonDndConfig().mode)
        assertEquals("#E51400", store.getAxolaCustomTheme().primaryAccentHex)
    }

    // =========================================================================
    // 6. Magister Official Test System (PW/SO/Toets/PO) & Presentation Checker Tests
    // =========================================================================

    @Test
    fun test_magister_official_test_system_and_presentation_checker() {
        // 1. Regular homework with "Oefenen SO" in homework notes MUST NOT be classified as a test
        val hwWithSoNotes = Appointment(
            id = 101L,
            title = "Wiskunde",
            subjectCode = "wi",
            subjectName = "Wiskunde",
            teacherName = "Bakker",
            teacherInitials = "BAK",
            classroom = "102",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 1, // Official Magister InfoType 1 = Huiswerk
            homework = "Oefenen SO hfst 3 opdrachten 1 t/m 15"
        )
        assertFalse("Huiswerk with 'Oefenen SO' in notes must NOT be a test", hwWithSoNotes.isTestOrExam)
        assertFalse("Huiswerk with 'Oefenen SO' is not a presentation", hwWithSoNotes.isPresentation)

        // 2. Regular homework with "Leren voor toets" in homework notes MUST NOT be a test
        val hwWithToetsNotes = Appointment(
            id = 102L,
            title = "Nederlands",
            subjectCode = "ne",
            subjectName = "Nederlands",
            teacherName = "De Vries",
            teacherInitials = "DVR",
            classroom = "104",
            periodIndex = 2,
            startTime = "09:20",
            endTime = "10:10",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 1, // Official Magister InfoType 1 = Huiswerk
            homework = "Huiswerk: leren voor toets volgende week"
        )
        assertFalse("Huiswerk with 'leren voor toets' must NOT be a test", hwWithToetsNotes.isTestOrExam)

        // 3. Official Magister SO (InfoType 4) MUST be classified as a test
        val officialSo = Appointment(
            id = 103L,
            title = "Frans",
            subjectCode = "fa",
            subjectName = "Frans",
            teacherName = "Dubois",
            teacherInitials = "DUB",
            classroom = "201",
            periodIndex = 3,
            startTime = "10:30",
            endTime = "11:20",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 4, // Official Magister InfoType 4 = SO
            homework = "Woordjes Unité 3"
        )
        assertTrue("InfoType 4 must be recognized as official Magister test", officialSo.isTestOrExam)

        // 4. Official Magister Proefwerk (InfoType 2) MUST be classified as a test
        val officialPw = Appointment(
            id = 104L,
            title = "Geschiedenis",
            subjectCode = "gs",
            subjectName = "Geschiedenis",
            teacherName = "Jansen",
            teacherInitials = "JAN",
            classroom = "205",
            periodIndex = 4,
            startTime = "11:20",
            endTime = "12:10",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 2, // Official Magister InfoType 2 = PW
            homework = "Hfst 1 t/m 4"
        )
        assertTrue("InfoType 2 must be recognized as official Magister test", officialPw.isTestOrExam)

        // 5. Official Magister PO (Praktische Opdracht) on title
        val officialPo = Appointment(
            id = 105L,
            title = "PO Aardrijkskunde Onderzoek",
            subjectCode = "ak",
            subjectName = "Aardrijkskunde",
            teacherName = "Klaassen",
            teacherInitials = "KLA",
            classroom = "301",
            periodIndex = 5,
            startTime = "12:40",
            endTime = "13:30",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 0,
            homework = "Inleveren verslag"
        )
        assertTrue("PO in title must be recognized as test/PO", officialPo.isTestOrExam)
        assertTrue("PO must be recognized as practical assignment", officialPo.isPracticalAssignment)

        // 6. Presentation checker: spreekbeurt / presentatie in homework MUST be recognized as Presentation
        val spreekbeurtAppt = Appointment(
            id = 106L,
            title = "Engels",
            subjectCode = "en",
            subjectName = "Engels",
            teacherName = "Smith",
            teacherInitials = "SMI",
            classroom = "108",
            periodIndex = 6,
            startTime = "13:30",
            endTime = "14:20",
            dayOfWeek = 1,
            dateIso = "2026-11-25",
            infoType = 1,
            homework = "Spreekbeurt voorbereiden over Londen (5 minuten)"
        )
        assertTrue("Presentation checker must detect 'spreekbeurt'", spreekbeurtAppt.isPresentation)

        // 7. Test Upcoming Tests list behavior:
        // hwWithSoNotes and hwWithToetsNotes MUST NOT appear in the upcoming tests list!
        val allAppts = listOf(hwWithSoNotes, hwWithToetsNotes, officialSo, officialPw, officialPo)
        val upcoming = com.example.ui.screens.buildUpcomingTestsList(
            appointments = allAppts,
            tasks = emptyList(),
            todayIsoOverride = "2026-11-25"
        )

        // Only official SO, PW, and PO should be in upcoming tests (size = 3)
        assertEquals(3, upcoming.size)
        val subjectsInUpcoming = upcoming.map { it.subject }
        assertFalse("Wiskunde with 'Oefenen SO' must NOT be in upcoming tests", subjectsInUpcoming.contains("Wiskunde"))
        assertFalse("Nederlands with 'leren voor toets' must NOT be in upcoming tests", subjectsInUpcoming.contains("Nederlands"))

        val badges = upcoming.associate { it.subject to it.typeBadge }
        assertEquals("SO", badges["Frans"])
        assertEquals("PW", badges["Geschiedenis"])
        assertEquals("PO", badges["Aardrijkskunde"])
    }

    // =========================================================================
    // 7. Motion & Polish Preferences + Live Hero Status Tests
    // =========================================================================

    @Test
    fun test_haptics_and_skipped_login_preferences_and_backup() {
        val store = AppPreferencesStore(context)
        // Ensure clean baseline
        store.setHapticsEnabled(true)
        store.setHasSkippedLogin(false)

        assertTrue("Haptics must default to true", store.isHapticsEnabled())
        assertFalse("Skipped login must default to false", store.hasSkippedLogin())

        store.setHapticsEnabled(false)
        store.setHasSkippedLogin(true)
        assertFalse(store.isHapticsEnabled())
        assertTrue(store.hasSkippedLogin())

        val backupJson = store.exportChoicesBackupJson()
        assertTrue(backupJson.contains("hapticsEnabled"))

        store.setHapticsEnabled(true)
        assertTrue(store.isHapticsEnabled())

        val restoreResult = store.importChoicesBackupJson(backupJson)
        assertTrue("Restoring backup with hapticsEnabled must succeed", restoreResult.isSuccess)
        assertFalse("Restored hapticsEnabled should be false", store.isHapticsEnabled())

        store.setHasSkippedLogin(false)
        assertFalse(store.hasSkippedLogin())
    }

    @Test
    fun test_compute_today_hero_status_states() {
        val todayIso = "2026-11-23" // Monday
        fun calAt(hour: Int, minute: Int, second: Int = 0, day: Int = 23): Calendar =
            Calendar.getInstance().apply {
                set(2026, Calendar.NOVEMBER, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }

        val lesson1 = Appointment(
            id = 501L,
            title = "Wi - wfre",
            subjectCode = "wi",
            subjectName = "Wiskunde",
            teacherName = "W. Frederik",
            teacherInitials = "WFRE",
            classroom = "101",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 1,
            dateIso = todayIso
        )
        val lesson2Canceled = Appointment(
            id = 502L,
            title = "Ne - mvdl",
            subjectCode = "ne",
            subjectName = "Nederlands",
            teacherName = "M. van der Laan",
            teacherInitials = "MVDL",
            classroom = "102",
            periodIndex = 2,
            startTime = "09:20",
            endTime = "10:10",
            dayOfWeek = 1,
            dateIso = todayIso,
            isCanceled = true
        )
        val lesson3 = Appointment(
            id = 503L,
            title = "En - dvr",
            subjectCode = "en",
            subjectName = "Engels",
            teacherName = "De Vries",
            teacherInitials = "DVR",
            classroom = "103",
            periodIndex = 3,
            startTime = "10:30",
            endTime = "11:20",
            dayOfWeek = 1,
            dateIso = todayIso
        )
        val lesson4Later = Appointment(
            id = 504L,
            title = "Gs - jan",
            subjectCode = "gs",
            subjectName = "Geschiedenis",
            teacherName = "Jansen",
            teacherInitials = "JAN",
            classroom = "104",
            periodIndex = 5,
            startTime = "12:30",
            endTime = "13:20",
            dayOfWeek = 1,
            dateIso = todayIso
        )
        val tuesdayTest = Appointment(
            id = 505L,
            title = "PW Biologie Hfst 4",
            subjectCode = "bi",
            subjectName = "Biologie",
            teacherName = "Smit",
            teacherInitials = "SMI",
            classroom = "202",
            periodIndex = 1,
            startTime = "08:30",
            endTime = "09:20",
            dayOfWeek = 2,
            dateIso = "2026-11-24",
            infoType = 2,
            homework = "Hfst 4"
        )
        val appts = listOf(lesson1, lesson2Canceled, lesson3, lesson4Later, tuesdayTest)

        // 1. InLesson (at 08:55 -> 25 min elapsed of 50 min = 0.5f, 25 min remaining)
        val inLesson = computeTodayHeroStatus(appts, emptyList(), calAt(8, 55, 0))
        assertTrue(inLesson is com.example.ui.screens.TodayHeroStatus.InLesson)
        val inLessonState = inLesson as com.example.ui.screens.TodayHeroStatus.InLesson
        assertEquals(501L, inLessonState.appointment.id)
        assertEquals(0.5f, inLessonState.progressFraction, 0.02f)
        assertEquals(25, inLessonState.remainingMinutes)

        // 2. GapOrUitval during canceled lesson (at 09:40 -> Nederlands canceled, next Engels)
        val uitval = computeTodayHeroStatus(appts, emptyList(), calAt(9, 40, 0))
        assertTrue(uitval is com.example.ui.screens.TodayHeroStatus.GapOrUitval)
        val uitvalState = uitval as com.example.ui.screens.TodayHeroStatus.GapOrUitval
        assertEquals(502L, uitvalState.canceledOrGapAppointment?.id)
        assertEquals(503L, uitvalState.nextActiveAppointment?.id)

        // 3. Break (at 10:15 -> 15 min until Engels, after earlier lesson1)
        val onBreak = computeTodayHeroStatus(appts, emptyList(), calAt(10, 15, 0))
        assertTrue(onBreak is com.example.ui.screens.TodayHeroStatus.Break)
        val breakState = onBreak as com.example.ui.screens.TodayHeroStatus.Break
        assertEquals(503L, breakState.nextAppointment.id)
        assertEquals(15, breakState.minutesUntil)

        // 4. GapOrUitval for >25 min gap (at 11:30 -> 60 min until Geschiedenis)
        val gap = computeTodayHeroStatus(appts, emptyList(), calAt(11, 30, 0))
        assertTrue(gap is com.example.ui.screens.TodayHeroStatus.GapOrUitval)
        val gapState = gap as com.example.ui.screens.TodayHeroStatus.GapOrUitval
        assertEquals(504L, gapState.nextActiveAppointment?.id)
        assertEquals(60, gapState.minutesUntilNext)

        // 5. WeekendOrEvening (at 19:00 -> no more lessons Monday, next Tuesday PW Biologie)
        val evening = computeTodayHeroStatus(appts, emptyList(), calAt(19, 0, 0))
        assertTrue(evening is com.example.ui.screens.TodayHeroStatus.WeekendOrEvening)
        val eveningState = evening as com.example.ui.screens.TodayHeroStatus.WeekendOrEvening
        assertEquals(505L, eveningState.nextActiveAppointment?.id)
        assertTrue(eveningState.countdownLabel.contains("Volgende les"))
        assertTrue(eveningState.upcomingTestsPreview.any { it.subject == "Biologie" })
    }
}
