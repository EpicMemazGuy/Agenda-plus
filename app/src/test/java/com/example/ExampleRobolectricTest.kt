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
}
