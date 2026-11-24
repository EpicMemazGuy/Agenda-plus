package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeatherLocationPref(
    val cityName: String,
    val latitude: Double,
    val longitude: Double
)

class AppPreferencesStore(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretSessionPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECRET_SESSION_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            prefs
        }
    }

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun hasSkippedLogin(): Boolean = prefs.getBoolean(KEY_SKIPPED_LOGIN, false)

    fun setHasSkippedLogin(skipped: Boolean) {
        prefs.edit().putBoolean(KEY_SKIPPED_LOGIN, skipped).apply()
    }

    fun isHapticsEnabled(): Boolean = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    fun hasSeenTutorial(): Boolean = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)

    fun setTutorialSeen(seen: Boolean) {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, seen).apply()
    }

    fun isNavPromptDismissed(): Boolean = prefs.getBoolean(KEY_NAV_PROMPT_DISMISSED, false)

    fun setNavPromptDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_NAV_PROMPT_DISMISSED, dismissed).apply()
    }

    fun isOfflineModeEnabled(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE_ENABLED, false)

    fun setOfflineModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE_ENABLED, enabled).apply()
    }

    fun getAgendaStyle(): AgendaStyle {
        val raw = prefs.getString(KEY_AGENDA_STYLE, AgendaStyle.COMPACT.name)
        return runCatching { AgendaStyle.valueOf(raw ?: AgendaStyle.COMPACT.name) }
            .getOrDefault(AgendaStyle.COMPACT)
    }

    fun setAgendaStyle(style: AgendaStyle) {
        prefs.edit().putString(KEY_AGENDA_STYLE, style.name).apply()
    }

    fun getUiScale(): Float = prefs.getFloat(KEY_UI_SCALE, 1.0f).coerceIn(0.85f, 1.20f)

    fun setUiScale(scale: Float) {
        prefs.edit().putFloat(KEY_UI_SCALE, scale.coerceIn(0.85f, 1.20f)).apply()
    }

    fun getAppThemeStyle(): AppThemeStyle {
        val raw = prefs.getString(KEY_APP_THEME_STYLE, AppThemeStyle.STANDARD.name)
        return runCatching { AppThemeStyle.valueOf(raw ?: AppThemeStyle.STANDARD.name) }
            .getOrDefault(AppThemeStyle.STANDARD)
    }

    fun setAppThemeStyle(style: AppThemeStyle) {
        prefs.edit().putString(KEY_APP_THEME_STYLE, style.name).apply()
    }

    fun getDarkModePreference(): DarkModePreference {
        val raw = prefs.getString(KEY_DARK_MODE_PREF, DarkModePreference.SYSTEM.name)
        return runCatching { DarkModePreference.valueOf(raw ?: DarkModePreference.SYSTEM.name) }
            .getOrDefault(DarkModePreference.SYSTEM)
    }

    fun setDarkModePreference(pref: DarkModePreference) {
        prefs.edit().putString(KEY_DARK_MODE_PREF, pref.name).apply()
    }

    fun getMetroAccent(): MetroAccent {
        val raw = prefs.getString(KEY_METRO_ACCENT, MetroAccent.COBALT.name)
        return runCatching { MetroAccent.valueOf(raw ?: MetroAccent.COBALT.name) }
            .getOrDefault(MetroAccent.COBALT)
    }

    fun setMetroAccent(accent: MetroAccent) {
        prefs.edit().putString(KEY_METRO_ACCENT, accent.name).apply()
    }

    fun isSmartSubjectNamesEnabled(): Boolean =
        prefs.getBoolean(KEY_SMART_SUBJECT_NAMES, true)

    fun setSmartSubjectNamesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_SUBJECT_NAMES, enabled).apply()
    }

    fun isUseSubjectColorsForMetroTiles(): Boolean =
        prefs.getBoolean(KEY_USE_SUBJECT_COLORS_METRO, false)

    fun setUseSubjectColorsForMetroTiles(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SUBJECT_COLORS_METRO, enabled).apply()
    }

    fun getAxolaCustomTheme(): AxolaCustomTheme {
        val raw = prefs.getString(KEY_AXOLA_CUSTOM_THEME_JSON, null) ?: return AxolaCustomTheme()
        return runCatching {
            AxolaCustomTheme.fromJsonObject(JSONObject(raw))
        }.getOrDefault(AxolaCustomTheme())
    }

    fun saveAxolaCustomTheme(theme: AxolaCustomTheme) {
        prefs.edit().putString(KEY_AXOLA_CUSTOM_THEME_JSON, theme.toJsonObject().toString()).apply()
    }

    fun getCalendarBridgeConfig(): CalendarBridgeConfig {
        return CalendarBridgeConfig(
            enabled = prefs.getBoolean(KEY_CALENDAR_BRIDGE_ENABLED, false),
            hideCanceledLessons = prefs.getBoolean(KEY_CALENDAR_HIDE_CANCELED, true),
            lastSyncedAtMs = prefs.getLong(KEY_CALENDAR_LAST_SYNC_MS, 0L),
            lastSyncedEventCount = prefs.getInt(KEY_CALENDAR_LAST_COUNT, 0)
        )
    }

    fun saveCalendarBridgeConfig(config: CalendarBridgeConfig) {
        prefs.edit()
            .putBoolean(KEY_CALENDAR_BRIDGE_ENABLED, config.enabled)
            .putBoolean(KEY_CALENDAR_HIDE_CANCELED, config.hideCanceledLessons)
            .putLong(KEY_CALENDAR_LAST_SYNC_MS, config.lastSyncedAtMs)
            .putInt(KEY_CALENDAR_LAST_COUNT, config.lastSyncedEventCount)
            .apply()
    }

    fun getLessonDndConfig(): LessonDndConfig {
        val rawMode = prefs.getString(KEY_LESSON_DND_MODE, LessonDndMode.VIBRATE.name)
        val mode = runCatching { LessonDndMode.valueOf(rawMode ?: LessonDndMode.VIBRATE.name) }
            .getOrDefault(LessonDndMode.VIBRATE)
        return LessonDndConfig(
            enabled = prefs.getBoolean(KEY_LESSON_DND_ENABLED, false),
            mode = mode,
            startBufferMinutes = prefs.getInt(KEY_LESSON_DND_START_BUFFER, 1).coerceIn(0, 15),
            endBufferMinutes = prefs.getInt(KEY_LESSON_DND_END_BUFFER, 0).coerceIn(0, 15),
            forceDndDuringExams = prefs.getBoolean(KEY_LESSON_DND_FORCE_EXAMS, true)
        )
    }

    fun saveLessonDndConfig(config: LessonDndConfig) {
        prefs.edit()
            .putBoolean(KEY_LESSON_DND_ENABLED, config.enabled)
            .putString(KEY_LESSON_DND_MODE, config.mode.name)
            .putInt(KEY_LESSON_DND_START_BUFFER, config.startBufferMinutes.coerceIn(0, 15))
            .putInt(KEY_LESSON_DND_END_BUFFER, config.endBufferMinutes.coerceIn(0, 15))
            .putBoolean(KEY_LESSON_DND_FORCE_EXAMS, config.forceDndDuringExams)
            .apply()
    }

    fun isLessonDndCurrentlyApplied(): Boolean =
        prefs.getBoolean(KEY_LESSON_DND_APPLIED, false)

    fun saveLessonDndSavedAudioState(applied: Boolean, prevRingerMode: Int, prevInterruptionFilter: Int) {
        prefs.edit()
            .putBoolean(KEY_LESSON_DND_APPLIED, applied)
            .putInt(KEY_LESSON_DND_PREV_RINGER, prevRingerMode)
            .putInt(KEY_LESSON_DND_PREV_FILTER, prevInterruptionFilter)
            .apply()
    }

    fun getLessonDndPrevRingerMode(): Int = prefs.getInt(KEY_LESSON_DND_PREV_RINGER, -1)

    fun getLessonDndPrevInterruptionFilter(): Int = prefs.getInt(KEY_LESSON_DND_PREV_FILTER, -1)

    fun clearLessonDndAppliedState() {
        prefs.edit()
            .putBoolean(KEY_LESSON_DND_APPLIED, false)
            .remove(KEY_LESSON_DND_PREV_RINGER)
            .remove(KEY_LESSON_DND_PREV_FILTER)
            .apply()
    }

    fun loadSubjectCustomizations(): Map<String, SubjectCustomization> {
        val raw = prefs.getString(KEY_SUBJECT_CUSTOMIZATIONS_JSON, null) ?: return emptyMap()
        return runCatching {
            SubjectCustomizationCatalog.fromJsonObject(JSONObject(raw))
        }.getOrDefault(emptyMap())
    }

    fun saveSubjectCustomizations(map: Map<String, SubjectCustomization>) {
        val json = SubjectCustomizationCatalog.toJsonObject(map)
        prefs.edit().putString(KEY_SUBJECT_CUSTOMIZATIONS_JSON, json.toString()).apply()
    }

    fun getEloUrl(): String =
        prefs.getString(KEY_ELO_URL, DEFAULT_ELO_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_ELO_URL

    fun setEloUrl(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
            url.trim()
        } else {
            "https://${url.trim()}"
        }
        prefs.edit().putString(KEY_ELO_URL, normalized).apply()
    }

    fun getWeatherLocation(): WeatherLocationPref {
        val city = prefs.getString(KEY_WEATHER_CITY, "Utrecht") ?: "Utrecht"
        val lat = prefs.getFloat(KEY_WEATHER_LAT, 52.0907f).toDouble()
        val lon = prefs.getFloat(KEY_WEATHER_LON, 5.1214f).toDouble()
        return WeatherLocationPref(city, lat, lon)
    }

    fun setWeatherLocation(city: String, lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_WEATHER_CITY, city)
            .putFloat(KEY_WEATHER_LAT, lat.toFloat())
            .putFloat(KEY_WEATHER_LON, lon.toFloat())
            .apply()
    }

    fun hasCustomWeatherLocation(): Boolean = prefs.contains(KEY_WEATHER_CITY)

    fun saveSchoolAddress(address: SchoolAddress?) {
        if (address == null) {
            prefs.edit().remove(KEY_SCHOOL_ADDRESS_JSON).apply()
            return
        }
        val json = JSONObject().apply {
            put("displayName", address.displayName)
            put("streetAndCity", address.streetAndCity)
            put("latitude", address.latitude)
            put("longitude", address.longitude)
        }
        prefs.edit().putString(KEY_SCHOOL_ADDRESS_JSON, json.toString()).apply()
    }

    fun loadSchoolAddress(): SchoolAddress? {
        val raw = prefs.getString(KEY_SCHOOL_ADDRESS_JSON, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val name = json.optString("displayName").cleanSchoolText() ?: return null
            SchoolAddress(
                displayName = name,
                streetAndCity = json.optString("streetAndCity").cleanSchoolText() ?: name,
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getSavedBusLine(): String = prefs.getString(KEY_SAVED_BUS_LINE, "") ?: ""

    fun setSavedBusLine(line: String) {
        prefs.edit().putString(KEY_SAVED_BUS_LINE, line.trim()).apply()
    }

    fun getOvapiCatalog(): Pair<String?, Long> {
        val json = prefs.getString(KEY_OVAPI_CATALOG_JSON, null)
        val timestamp = prefs.getLong(KEY_OVAPI_CATALOG_TIMESTAMP, 0L)
        return Pair(json, timestamp)
    }

    fun saveOvapiCatalog(json: String, timestamp: Long) {
        prefs.edit()
            .putString(KEY_OVAPI_CATALOG_JSON, json)
            .putLong(KEY_OVAPI_CATALOG_TIMESTAMP, timestamp)
            .apply()
    }

    fun saveTodayBlocksConfig(blocks: List<TodayBlockConfig>) {
        val arr = JSONArray()
        blocks.forEach { block ->
            arr.put(JSONObject().apply {
                put("type", block.type.name)
                put("enabled", block.enabled)
            })
        }
        prefs.edit().putString(KEY_TODAY_BLOCKS_JSON, arr.toString()).apply()
    }

    fun loadTodayBlocksConfig(): List<TodayBlockConfig> {
        val raw = prefs.getString(KEY_TODAY_BLOCKS_JSON, null) ?: return defaultTodayBlocks()
        return try {
            val arr = JSONArray(raw)
            val loaded = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching { TodayBlockType.valueOf(o.optString("type")) }.getOrNull()
                    if (type != null) {
                        add(TodayBlockConfig(type = type, enabled = o.optBoolean("enabled", true)))
                    }
                }
            }
            val missing = TodayBlockType.entries
                .filter { t -> loaded.none { it.type == t } }
                .map { t ->
                    val defaultEnabled = t != TodayBlockType.BUS_TIME && t != TodayBlockType.SCHOOL_NAV
                    TodayBlockConfig(t, defaultEnabled)
                }
            (loaded + missing).ifEmpty { defaultTodayBlocks() }
        } catch (_: Exception) {
            defaultTodayBlocks()
        }
    }

    fun saveSession(session: AuthSession) {
        saveSessionSync(session)
    }

    fun saveSessionSync(session: AuthSession) {
        if (!session.isAuthenticated) {
            clearSession()
            return
        }
        val json = JSONObject().apply {
            put("isAuthenticated", true)
            put("studentName", session.studentName.cleanSchoolText() ?: "")
            put("studentId", session.studentId.cleanSchoolText() ?: "")
            put("studentClass", session.studentClass.cleanSchoolText() ?: "")
            put("accessToken", session.accessToken ?: "")
            put("refreshToken", session.refreshToken ?: "")
            put("personId", session.personId)
            put("expiresAt", session.expiresAt)
            put("provider", SchoolProvider.MAGISTER.name)
            session.school?.let { school ->
                put("school", JSONObject().apply {
                    put("id", school.id)
                    put("name", school.name)
                    put("city", school.city)
                    put("subdomain", school.subdomain)
                    put("provider", SchoolProvider.MAGISTER.name)
                })
            }
        }
        secretSessionPrefs.edit().putString(KEY_SESSION_JSON, json.toString()).commit()
        if (secretSessionPrefs !== prefs && prefs.contains(KEY_SESSION_JSON)) {
            prefs.edit().remove(KEY_SESSION_JSON).commit()
        }
    }

    fun clearAuthCredentialsOnly() {
        secretSessionPrefs.edit().remove(KEY_SESSION_JSON).commit()
        if (secretSessionPrefs !== prefs && prefs.contains(KEY_SESSION_JSON)) {
            prefs.edit().remove(KEY_SESSION_JSON).commit()
        }
    }

    fun loadSession(): AuthSession {
        val raw = secretSessionPrefs.getString(KEY_SESSION_JSON, null)
            ?: prefs.getString(KEY_SESSION_JSON, null)
            ?: return AuthSession()
        return try {
            val json = JSONObject(raw)
            if (!json.optBoolean("isAuthenticated", false)) return AuthSession()
            val schoolObj = json.optJSONObject("school")
            val school = schoolObj?.let {
                SchoolTenant(
                    id = it.optString("id", "auto"),
                    name = it.optString("name", "Magister"),
                    city = it.optString("city", "Nederland"),
                    subdomain = it.optString("subdomain", ""),
                    provider = SchoolProvider.MAGISTER
                )
            }

            AuthSession(
                isAuthenticated = true,
                studentName = json.optString("studentName").cleanSchoolText() ?: "",
                studentId = json.optString("studentId").cleanSchoolText() ?: "",
                studentClass = json.optString("studentClass").cleanSchoolText() ?: "",
                school = school,
                accessToken = json.optString("accessToken").cleanSchoolText(),
                refreshToken = json.optString("refreshToken").cleanSchoolText(),
                personId = json.optLong("personId", 0L),
                provider = SchoolProvider.MAGISTER,
                expiresAt = json.optLong("expiresAt", 0L)
            )
        } catch (_: Exception) {
            AuthSession()
        }
    }

    fun isUitvalNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_UITVAL_NOTIFS_ENABLED, true)

    fun setUitvalNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UITVAL_NOTIFS_ENABLED, enabled).apply()
    }

    fun isGradeNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_GRADE_NOTIFS_ENABLED, true)

    fun setGradeNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRADE_NOTIFS_ENABLED, enabled).apply()
    }

    fun isTestNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_TEST_NOTIFS_ENABLED, true)

    fun setTestNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TEST_NOTIFS_ENABLED, enabled).apply()
    }

    fun isAnyNotificationEnabled(): Boolean =
        isUitvalNotificationsEnabled() || isGradeNotificationsEnabled() || isTestNotificationsEnabled()

    fun getNotifiedCanceledKeys(): Set<String> =
        prefs.getStringSet(KEY_NOTIFIED_CANCELED_KEYS, emptySet())?.toSet() ?: emptySet()

    fun setNotifiedCanceledKeys(keys: Set<String>) {
        val bounded = if (keys.size > 250) keys.toList().takeLast(250).toSet() else keys
        prefs.edit().putStringSet(KEY_NOTIFIED_CANCELED_KEYS, bounded).apply()
    }

    fun getNotifiedChangeKeys(): Set<String> =
        prefs.getStringSet(KEY_NOTIFIED_CHANGE_KEYS, emptySet())?.toSet() ?: emptySet()

    fun setNotifiedChangeKeys(keys: Set<String>) {
        val bounded = if (keys.size > 250) keys.toList().takeLast(250).toSet() else keys
        prefs.edit().putStringSet(KEY_NOTIFIED_CHANGE_KEYS, bounded).apply()
    }

    fun hasGradesBaseline(): Boolean = prefs.getBoolean(KEY_HAS_GRADES_BASELINE, false)

    fun getKnownGradeIds(): Set<String> =
        prefs.getStringSet(KEY_KNOWN_GRADE_IDS, emptySet())?.toSet() ?: emptySet()

    fun setKnownGradeIds(ids: Set<String>) {
        val bounded = if (ids.size > 500) ids.toList().takeLast(500).toSet() else ids
        prefs.edit()
            .putStringSet(KEY_KNOWN_GRADE_IDS, bounded)
            .putBoolean(KEY_HAS_GRADES_BASELINE, true)
            .apply()
    }

    fun hasTestsBaseline(): Boolean = prefs.getBoolean(KEY_HAS_TESTS_BASELINE, false)

    fun getKnownTestKeys(): Set<String> =
        prefs.getStringSet(KEY_KNOWN_TEST_KEYS, emptySet())?.toSet() ?: emptySet()

    fun setKnownTestKeys(keys: Set<String>) {
        val bounded = if (keys.size > 400) keys.toList().takeLast(400).toSet() else keys
        prefs.edit()
            .putStringSet(KEY_KNOWN_TEST_KEYS, bounded)
            .putBoolean(KEY_HAS_TESTS_BASELINE, true)
            .apply()
    }

    fun isGradeUnboxingEnabled(): Boolean =
        prefs.getBoolean(KEY_GRADE_UNBOXING_ENABLED, true)

    fun setGradeUnboxingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRADE_UNBOXING_ENABLED, enabled).apply()
    }

    fun getGradeUnboxingMode(): GradeUnboxingMode {
        val raw = prefs.getString(KEY_GRADE_UNBOXING_MODE, GradeUnboxingMode.CASE_OPENING.name)
        return runCatching { GradeUnboxingMode.valueOf(raw ?: GradeUnboxingMode.CASE_OPENING.name) }
            .getOrDefault(GradeUnboxingMode.CASE_OPENING)
    }

    fun setGradeUnboxingMode(mode: GradeUnboxingMode) {
        prefs.edit().putString(KEY_GRADE_UNBOXING_MODE, mode.name).apply()
    }

    fun hasUnboxedBaseline(): Boolean = prefs.getBoolean(KEY_HAS_UNBOXED_BASELINE, false)

    fun getUnboxedGradeIds(): Set<String> =
        prefs.getStringSet(KEY_UNBOXED_GRADE_IDS, emptySet())?.toSet() ?: emptySet()

    fun setUnboxedGradeIds(ids: Set<String>) {
        val bounded = if (ids.size > 500) ids.toList().takeLast(500).toSet() else ids
        prefs.edit()
            .putStringSet(KEY_UNBOXED_GRADE_IDS, bounded)
            .putBoolean(KEY_HAS_UNBOXED_BASELINE, true)
            .apply()
    }

    fun clearSession() {
        secretSessionPrefs.edit().remove(KEY_SESSION_JSON).commit()
        prefs.edit()
            .remove(KEY_SESSION_JSON)
            .remove(KEY_APPOINTMENTS_JSON)
            .remove(KEY_GRADES_JSON)
            .remove(KEY_TASKS_JSON)
            .remove(KEY_MESSAGES_JSON)
            .remove(KEY_STUDY_GUIDES_JSON)
            .remove(KEY_KWT_SLOTS_JSON)
            .remove(KEY_ABSENCES_JSON)
            .remove(KEY_BUS_SNAPSHOT_JSON)
            .remove(KEY_LAST_SMART_BUS_KEY)
            .remove(KEY_CHANGED_LESSON_DIFFS_JSON)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_NOTIFIED_CANCELED_KEYS)
            .remove(KEY_NOTIFIED_CHANGE_KEYS)
            .remove(KEY_KNOWN_GRADE_IDS)
            .remove(KEY_HAS_GRADES_BASELINE)
            .remove(KEY_KNOWN_TEST_KEYS)
            .remove(KEY_HAS_TESTS_BASELINE)
            .remove(KEY_UNBOXED_GRADE_IDS)
            .remove(KEY_HAS_UNBOXED_BASELINE)
            .commit()
    }

    fun saveAppointments(appointments: List<Appointment>) {
        val arr = JSONArray()
        appointments.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("subjectCode", a.subjectCode)
                put("subjectName", a.subjectName)
                put("teacherName", a.teacherName)
                put("teacherInitials", a.teacherInitials)
                put("classroom", a.classroom)
                a.originalClassroom?.let { put("originalClassroom", it) }
                put("periodIndex", a.periodIndex)
                put("startTime", a.startTime)
                put("endTime", a.endTime)
                put("dayOfWeek", a.dayOfWeek)
                put("isCanceled", a.isCanceled)
                put("isRelocated", a.isRelocated)
                put("isSubstitution", a.isSubstitution)
                a.homework.cleanSchoolText()?.let { put("homework", it) }
                put("dateIso", a.dateIso)
                put("isKwt", a.isKwt)
                put("appointmentType", a.appointmentType)
                put("infoType", a.infoType)
            })
        }
        prefs.edit().putString(KEY_APPOINTMENTS_JSON, arr.toString()).apply()
    }

    fun loadAppointments(): List<Appointment> {
        val raw = prefs.getString(KEY_APPOINTMENTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val hw = if (o.isNull("homework")) null else o.optString("homework").cleanSchoolText()
                    val orig = if (o.isNull("originalClassroom")) null else o.optString("originalClassroom").cleanSchoolText()
                    add(
                        Appointment(
                            id = o.optLong("id", i.toLong()),
                            title = o.optString("title").cleanSchoolText() ?: "Les",
                            subjectCode = o.optString("subjectCode").cleanSchoolText() ?: "",
                            subjectName = o.optString("subjectName").cleanSchoolText() ?: "Les",
                            teacherName = o.optString("teacherName").cleanSchoolText() ?: "",
                            teacherInitials = o.optString("teacherInitials").cleanSchoolText() ?: "",
                            classroom = o.optString("classroom").cleanSchoolText() ?: "",
                            originalClassroom = orig,
                            periodIndex = o.optInt("periodIndex", 1),
                            startTime = o.optString("startTime", "08:30"),
                            endTime = o.optString("endTime", "09:15"),
                            dayOfWeek = o.optInt("dayOfWeek", 1),
                            isCanceled = o.optBoolean("isCanceled", false),
                            isRelocated = o.optBoolean("isRelocated", false),
                            isSubstitution = o.optBoolean("isSubstitution", false),
                            homework = hw,
                            dateIso = o.optString("dateIso").cleanSchoolText() ?: "",
                            isKwt = o.optBoolean("isKwt", false),
                            appointmentType = o.optInt("appointmentType", 0),
                            infoType = o.optInt("infoType", 0)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGrades(grades: List<SubjectGradeSummary>) {
        val arr = JSONArray()
        grades.forEach { s ->
            val itemsArr = JSONArray()
            s.grades.forEach { g ->
                itemsArr.put(JSONObject().apply {
                    put("id", g.id)
                    put("subjectCode", g.subjectCode)
                    put("subjectName", g.subjectName)
                    put("score", g.score)
                    put("weight", g.weight)
                    put("description", g.description)
                    put("date", g.date)
                    put("period", g.period)
                    put("isPta", g.isPta)
                })
            }
            arr.put(JSONObject().apply {
                put("subjectCode", s.subjectCode)
                put("subjectName", s.subjectName)
                put("weightedAverage", s.weightedAverage)
                put("grades", itemsArr)
            })
        }
        prefs.edit().putString(KEY_GRADES_JSON, arr.toString()).apply()
    }

    fun loadGrades(): List<SubjectGradeSummary> {
        val raw = prefs.getString(KEY_GRADES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val gArr = o.optJSONArray("grades") ?: JSONArray()
                    val gradeItems = buildList {
                        for (j in 0 until gArr.length()) {
                            val go = gArr.getJSONObject(j)
                            add(
                                GradeItem(
                                    id = go.optString("id", "g_$j"),
                                    subjectCode = go.optString("subjectCode", ""),
                                    subjectName = go.optString("subjectName", "Vak"),
                                    score = go.optDouble("score", 0.0),
                                    weight = go.optDouble("weight", 1.0),
                                    description = go.optString("description", "Toets"),
                                    date = go.optString("date", ""),
                                    period = go.optInt("period", 1),
                                    isPta = go.optBoolean("isPta", false)
                                )
                            )
                        }
                    }
                    add(
                        SubjectGradeSummary(
                            subjectCode = o.optString("subjectCode", ""),
                            subjectName = o.optString("subjectName", "Vak"),
                            weightedAverage = o.optDouble("weightedAverage", 0.0),
                            grades = gradeItems
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTasks(tasks: List<HomeworkTask>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("subject", t.subject)
                put("title", t.title)
                put("description", t.description)
                put("dueDay", t.dueDay)
                put("dueDate", t.dueDate)
                put("category", t.category.name)
                put("isCompleted", t.isCompleted)
                put("priority", t.priority.name)
            })
        }
        prefs.edit().putString(KEY_TASKS_JSON, arr.toString()).apply()
    }

    fun loadTasks(): List<HomeworkTask> {
        val raw = prefs.getString(KEY_TASKS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cat = runCatching { TaskCategory.valueOf(o.optString("category")) }
                        .getOrDefault(TaskCategory.HOMEWORK)
                    val prio = runCatching { TaskPriority.valueOf(o.optString("priority")) }
                        .getOrDefault(TaskPriority.MEDIUM)
                    add(
                        HomeworkTask(
                            id = o.optString("id", "t_$i"),
                            subject = o.optString("subject", "Algemeen"),
                            title = o.optString("title", "Taak"),
                            description = o.optString("description", ""),
                            dueDay = o.optString("dueDay", ""),
                            dueDate = o.optString("dueDate", ""),
                            category = cat,
                            isCompleted = o.optBoolean("isCompleted", false),
                            priority = prio
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(messages: List<MagisterMessage>) {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("subject", m.subject)
                put("senderName", m.senderName)
                m.senderEmail?.let { put("senderEmail", it) }
                put("dateSent", m.dateSent)
                put("body", m.body)
                put("isRead", m.isRead)
                put("hasAttachments", m.hasAttachments)
                put("folder", m.folder.name)
                put("folderId", m.folderId)
                put("folderName", m.folderName)
                put("recipients", m.recipients)
                put("rawDateIso", m.rawDateIso)
            })
        }
        prefs.edit().putString(KEY_MESSAGES_JSON, arr.toString()).apply()
    }

    fun loadMessages(): List<MagisterMessage> {
        val raw = prefs.getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val folder = runCatching { MessageFolder.valueOf(o.optString("folder")) }
                        .getOrDefault(MessageFolder.INBOX)
                    add(
                        MagisterMessage(
                            id = o.optString("id", "m_$i"),
                            subject = o.optString("subject").cleanSchoolText() ?: "Bericht",
                            senderName = o.optString("senderName").cleanSchoolText() ?: "Afzender",
                            senderEmail = if (o.isNull("senderEmail")) null else o.optString("senderEmail").cleanSchoolText(),
                            dateSent = o.optString("dateSent", "Recent"),
                            body = o.optString("body", ""),
                            isRead = o.optBoolean("isRead", false),
                            hasAttachments = o.optBoolean("hasAttachments", false),
                            folder = folder,
                            folderId = o.optLong("folderId", 1L),
                            folderName = o.optString("folderName", folder.labelDutch),
                            recipients = o.optString("recipients", ""),
                            rawDateIso = o.optString("rawDateIso", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStudyGuides(guides: List<StudyGuide>) {
        val arr = JSONArray()
        guides.forEach { guide ->
            val partsArr = JSONArray()
            guide.parts.forEach { part ->
                val resArr = JSONArray()
                part.resources.forEach { res ->
                    resArr.put(JSONObject().apply {
                        put("id", res.id)
                        put("name", res.name)
                        put("url", res.url)
                        put("contentType", res.contentType)
                    })
                }
                partsArr.put(JSONObject().apply {
                    put("id", part.id)
                    put("title", part.title)
                    put("description", part.description)
                    put("sortOrder", part.sortOrder)
                    put("resources", resArr)
                })
            }
            arr.put(JSONObject().apply {
                put("id", guide.id)
                put("title", guide.title)
                put("subjectCode", guide.subjectCode)
                put("subjectName", guide.subjectName)
                put("description", guide.description)
                put("parts", partsArr)
            })
        }
        prefs.edit().putString(KEY_STUDY_GUIDES_JSON, arr.toString()).apply()
    }

    fun loadStudyGuides(): List<StudyGuide> {
        val raw = prefs.getString(KEY_STUDY_GUIDES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val go = arr.getJSONObject(i)
                    val partsArr = go.optJSONArray("parts") ?: JSONArray()
                    val parts = buildList {
                        for (p in 0 until partsArr.length()) {
                            val po = partsArr.getJSONObject(p)
                            val resArr = po.optJSONArray("resources") ?: JSONArray()
                            val resources = buildList {
                                for (r in 0 until resArr.length()) {
                                    val ro = resArr.getJSONObject(r)
                                    add(
                                        StudyGuideResource(
                                            id = ro.optString("id", "r_$r"),
                                            name = ro.optString("name", "Bron"),
                                            url = ro.optString("url", ""),
                                            contentType = ro.optString("contentType", "Link")
                                        )
                                    )
                                }
                            }
                            add(
                                StudyGuidePart(
                                    id = po.optString("id", "p_$p"),
                                    title = po.optString("title", "Onderdeel"),
                                    description = po.optString("description", ""),
                                    sortOrder = po.optInt("sortOrder", p),
                                    resources = resources
                                )
                            )
                        }
                    }
                    add(
                        StudyGuide(
                            id = go.optString("id", "sg_$i"),
                            title = go.optString("title", "Studiewijzer"),
                            subjectCode = go.optString("subjectCode", ""),
                            subjectName = go.optString("subjectName", "Algemeen"),
                            description = go.optString("description", ""),
                            parts = parts
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveKwtSlots(slots: List<KwtSlot>) {
        val arr = JSONArray()
        slots.forEach { slot ->
            val optsArr = JSONArray()
            slot.options.forEach { opt ->
                optsArr.put(JSONObject().apply {
                    put("id", opt.id)
                    put("title", opt.title)
                    put("subjectName", opt.subjectName)
                    put("teacherName", opt.teacherName)
                    put("classroom", opt.classroom)
                    put("enrolledCount", opt.enrolledCount)
                    put("maxCount", opt.maxCount)
                    put("isEnrolled", opt.isEnrolled)
                })
            }
            arr.put(JSONObject().apply {
                put("appointmentId", slot.appointmentId)
                put("title", slot.title)
                put("dateIso", slot.dateIso)
                put("startTime", slot.startTime)
                put("endTime", slot.endTime)
                put("periodIndex", slot.periodIndex)
                put("options", optsArr)
            })
        }
        prefs.edit().putString(KEY_KWT_SLOTS_JSON, arr.toString()).apply()
    }

    fun loadKwtSlots(): List<KwtSlot> {
        val raw = prefs.getString(KEY_KWT_SLOTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val so = arr.getJSONObject(i)
                    val optsArr = so.optJSONArray("options") ?: JSONArray()
                    val options = buildList {
                        for (j in 0 until optsArr.length()) {
                            val oo = optsArr.getJSONObject(j)
                            add(
                                KwtOption(
                                    id = oo.optLong("id", j.toLong()),
                                    title = oo.optString("title", "Keuzeles"),
                                    subjectName = oo.optString("subjectName", "KWT"),
                                    teacherName = oo.optString("teacherName", ""),
                                    classroom = oo.optString("classroom", ""),
                                    enrolledCount = oo.optInt("enrolledCount", 0),
                                    maxCount = oo.optInt("maxCount", 0),
                                    isEnrolled = oo.optBoolean("isEnrolled", false)
                                )
                            )
                        }
                    }
                    add(
                        KwtSlot(
                            appointmentId = so.optLong("appointmentId", i.toLong()),
                            title = so.optString("title", "Keuzewerktijd"),
                            dateIso = so.optString("dateIso", ""),
                            startTime = so.optString("startTime", ""),
                            endTime = so.optString("endTime", ""),
                            periodIndex = so.optInt("periodIndex", 1),
                            options = options
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLastSyncTime(syncTime: String) {
        prefs.edit().putString(KEY_LAST_SYNC, syncTime).apply()
    }

    fun loadLastSyncTime(): String {
        return prefs.getString(KEY_LAST_SYNC, "Nog niet gesynchroniseerd") ?: "Nog niet gesynchroniseerd"
    }

    fun saveStudySites(sites: List<StudySite>) {
        val arr = JSONArray()
        sites.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("category", s.category)
                put("isDefault", s.isDefault)
            })
        }
        prefs.edit().putString(KEY_STUDY_SITES, arr.toString()).apply()
    }

    fun loadStudySites(): List<StudySite> {
        val raw = prefs.getString(KEY_STUDY_SITES, null)
        if (raw == null) {
            return defaultStudySites()
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        StudySite(
                            id = o.optString("id", "site_$i"),
                            name = o.optString("name", "Studie"),
                            url = o.optString("url", "https://quizlet.com"),
                            category = o.optString("category", "Oefenen"),
                            isDefault = o.optBoolean("isDefault", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            defaultStudySites()
        }
    }

    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true)

    fun setQuietHoursEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, enabled).apply()
    }

    fun getQuietHoursStartMinutes(): Int =
        prefs.getInt(KEY_QUIET_HOURS_START, 22 * 60).coerceIn(0, 23 * 60 + 59)

    fun setQuietHoursStartMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_QUIET_HOURS_START, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun getQuietHoursEndMinutes(): Int =
        prefs.getInt(KEY_QUIET_HOURS_END, 7 * 60).coerceIn(0, 23 * 60 + 59)

    fun setQuietHoursEndMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_QUIET_HOURS_END, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun getQuietHoursConfig(): QuietHoursConfig = QuietHoursConfig(
        enabled = isQuietHoursEnabled(),
        startMinutes = getQuietHoursStartMinutes(),
        endMinutes = getQuietHoursEndMinutes()
    )

    fun getMutedSubjects(): Set<String> =
        prefs.getStringSet(KEY_MUTED_SUBJECTS, emptySet())?.toSet() ?: emptySet()

    fun setMutedSubjects(subjects: Set<String>) {
        prefs.edit().putStringSet(KEY_MUTED_SUBJECTS, subjects).apply()
    }

    fun isSmartBusAlertsEnabled(): Boolean = prefs.getBoolean(KEY_SMART_BUS_ALERTS, true)

    fun setSmartBusAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_BUS_ALERTS, enabled).apply()
    }

    fun getBusWalkMinutes(): Int = prefs.getInt(KEY_BUS_WALK_MINUTES, 5).coerceIn(1, 30)

    fun setBusWalkMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_BUS_WALK_MINUTES, minutes.coerceIn(1, 30)).apply()
    }

    fun getBusArrivalBufferMinutes(): Int = prefs.getInt(KEY_BUS_BUFFER_MINUTES, 10).coerceIn(2, 30)

    fun setBusArrivalBufferMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_BUS_BUFFER_MINUTES, minutes.coerceIn(2, 30)).apply()
    }

    fun getLastSmartBusAlertKey(): String = prefs.getString(KEY_LAST_SMART_BUS_KEY, "") ?: ""

    fun setLastSmartBusAlertKey(key: String) {
        prefs.edit().putString(KEY_LAST_SMART_BUS_KEY, key).apply()
    }

    fun saveAbsences(absences: List<AbsenceRecord>) {
        val arr = JSONArray()
        absences.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("date", a.date)
                put("period", a.period)
                put("type", a.type.name)
                put("reason", a.reason)
                put("isApproved", a.isApproved)
                put("startIso", a.startIso)
                put("endIso", a.endIso)
                a.hour?.let { put("hour", it) }
                put("subject", a.subject)
                put("code", a.code)
            })
        }
        prefs.edit().putString(KEY_ABSENCES_JSON, arr.toString()).apply()
    }

    fun loadAbsences(): List<AbsenceRecord> {
        val raw = prefs.getString(KEY_ABSENCES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching { AbsenceType.valueOf(o.optString("type")) }
                        .getOrDefault(AbsenceType.EXCUSED)
                    val hourVal = if (o.has("hour") && !o.isNull("hour")) o.optInt("hour", -1).takeIf { it > 0 } else null
                    add(
                        AbsenceRecord(
                            id = o.optString("id", "abs_$i"),
                            date = o.optString("date", ""),
                            period = o.optString("period", "Hele dag"),
                            type = type,
                            reason = o.optString("reason", "Verzuim"),
                            isApproved = o.optBoolean("isApproved", true),
                            startIso = o.optString("startIso", ""),
                            endIso = o.optString("endIso", ""),
                            hour = hourVal,
                            subject = o.optString("subject", ""),
                            code = o.optString("code", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveBusWidgetSnapshot(snapshot: BusWidgetSnapshot) {
        val json = JSONObject().apply {
            put("busLine", snapshot.busLine)
            put("stopName", snapshot.stopName)
            put("expectedTime", snapshot.expectedTime)
            put("delayMinutes", snapshot.delayMinutes)
            put("smartAdviceText", snapshot.smartAdviceText)
            put("updatedLabel", snapshot.updatedLabel)
        }
        prefs.edit().putString(KEY_BUS_SNAPSHOT_JSON, json.toString()).apply()
    }

    fun loadBusWidgetSnapshot(): BusWidgetSnapshot? {
        val raw = prefs.getString(KEY_BUS_SNAPSHOT_JSON, null) ?: return null
        return try {
            val o = JSONObject(raw)
            BusWidgetSnapshot(
                busLine = o.optString("busLine", ""),
                stopName = o.optString("stopName", ""),
                expectedTime = o.optString("expectedTime", ""),
                delayMinutes = o.optInt("delayMinutes", 0),
                smartAdviceText = o.optString("smartAdviceText", ""),
                updatedLabel = o.optString("updatedLabel", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadTestPrepChecklists(): Map<String, TestPrepChecklist> {
        val raw = prefs.getString(KEY_TEST_PREP_JSON, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, TestPrepChecklist>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val topicsArr = obj.optJSONArray("topics") ?: JSONArray()
                val topics = buildList {
                    for (i in 0 until topicsArr.length()) {
                        val to = topicsArr.optJSONObject(i) ?: continue
                        add(
                            TestPrepSubtopic(
                                id = to.optString("id", "tp_$i"),
                                title = to.optString("title", "Onderdeel"),
                                isChecked = to.optBoolean("isChecked", false)
                            )
                        )
                    }
                }
                result[key] = TestPrepChecklist(
                    key = key,
                    subject = obj.optString("subject", "Toets"),
                    title = obj.optString("title", "Voorbereiding"),
                    topics = topics
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveTestPrepChecklist(checklist: TestPrepChecklist) {
        val current = loadTestPrepChecklists().toMutableMap()
        current[checklist.key] = checklist
        val root = JSONObject()
        current.forEach { (key, list) ->
            val topicsArr = JSONArray()
            list.topics.forEach { t ->
                topicsArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("isChecked", t.isChecked)
                })
            }
            root.put(key, JSONObject().apply {
                put("subject", list.subject)
                put("title", list.title)
                put("topics", topicsArr)
            })
        }
        prefs.edit().putString(KEY_TEST_PREP_JSON, root.toString()).apply()
    }

    fun saveAllTestPrepChecklists(checklists: Map<String, TestPrepChecklist>) {
        val root = JSONObject()
        checklists.forEach { (key, list) ->
            val topicsArr = JSONArray()
            list.topics.forEach { t ->
                topicsArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("isChecked", t.isChecked)
                })
            }
            root.put(key, JSONObject().apply {
                put("subject", list.subject)
                put("title", list.title)
                put("topics", topicsArr)
            })
        }
        prefs.edit().putString(KEY_TEST_PREP_JSON, root.toString()).apply()
    }

    fun getCommuteModes(): List<CommuteModeConfig> {
        val raw = prefs.getString(KEY_COMMUTE_MODES_JSON, null)
        val parsed = if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val type = runCatching { CommuteModeType.valueOf(o.optString("type")) }
                            .getOrDefault(CommuteModeType.BUS)
                        add(
                            CommuteModeConfig(
                                id = o.optString("id", "mode_$i"),
                                type = type,
                                label = o.optString("label", if (type == CommuteModeType.BIKE) "Fiets" else "Bus"),
                                busLine = o.optString("busLine", ""),
                                bikeMinutes = o.optInt("bikeMinutes", 25).coerceIn(5, 90)
                            )
                        )
                    }
                }.take(3)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        if (parsed.isNotEmpty()) return parsed

        val primaryBus = getSavedBusLine()
        return buildList {
            if (primaryBus.isNotBlank()) {
                add(CommuteModeConfig("bus_1", CommuteModeType.BUS, "Lijn $primaryBus", busLine = primaryBus))
            }
            add(CommuteModeConfig("bike_1", CommuteModeType.BIKE, "Fiets", bikeMinutes = 25))
        }
    }

    fun saveCommuteModes(modes: List<CommuteModeConfig>) {
        val bounded = modes.take(3)
        val arr = JSONArray()
        bounded.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("type", m.type.name)
                put("label", m.label)
                put("busLine", m.busLine)
                put("bikeMinutes", m.bikeMinutes)
            })
        }
        prefs.edit().putString(KEY_COMMUTE_MODES_JSON, arr.toString()).apply()
    }

    fun getActiveCommuteModeId(): String =
        prefs.getString(KEY_ACTIVE_COMMUTE_MODE_ID, "") ?: ""

    fun setActiveCommuteModeId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_COMMUTE_MODE_ID, id).apply()
    }

    fun isMorningLockNotificationEnabled(): Boolean =
        prefs.getBoolean(KEY_MORNING_LOCK_NOTIF_ENABLED, true)

    fun setMorningLockNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MORNING_LOCK_NOTIF_ENABLED, enabled).apply()
    }

    private fun currentIsoWeekKey(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    fun getWeeklyFocusMinutes(): Int {
        val storedWeek = prefs.getString(KEY_FOCUS_WEEK_KEY, "") ?: ""
        val nowWeek = currentIsoWeekKey()
        return if (storedWeek == nowWeek) prefs.getInt(KEY_WEEKLY_FOCUS_MINUTES, 0).coerceAtLeast(0) else 0
    }

    fun addWeeklyFocusMinutes(minutes: Int): Int {
        val added = minutes.coerceAtLeast(1)
        val nowWeek = currentIsoWeekKey()
        val updated = getWeeklyFocusMinutes() + added
        prefs.edit()
            .putString(KEY_FOCUS_WEEK_KEY, nowWeek)
            .putInt(KEY_WEEKLY_FOCUS_MINUTES, updated)
            .apply()
        return updated
    }

    fun getChangedLessonDiffs(): Map<Long, String> {
        val raw = prefs.getString(KEY_CHANGED_LESSON_DIFFS_JSON, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val nowMs = System.currentTimeMillis()
            val map = mutableMapOf<Long, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val entry = obj.optJSONObject(k)
                if (entry != null) {
                    val ts = entry.optLong("ts", 0L)
                    val summary = entry.optString("summary", "")
                    if (summary.isNotBlank() && (ts == 0L || nowMs - ts < 36 * 60 * 60 * 1000L)) {
                        map[id] = summary
                    }
                } else {
                    val summary = obj.optString(k, "")
                    if (summary.isNotBlank()) map[id] = summary
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun mergeChangedLessonDiffs(newDiffs: Map<Long, String>, activeLessonIds: Set<Long>? = null) {
        if (newDiffs.isEmpty() && activeLessonIds == null) return
        val existing = getChangedLessonDiffs().toMutableMap()
        newDiffs.forEach { (id, summary) -> existing[id] = summary }
        val filtered = if (activeLessonIds != null) {
            existing.filterKeys { it in activeLessonIds }
        } else existing
        val nowMs = System.currentTimeMillis()
        val root = JSONObject()
        filtered.entries.take(80).forEach { (id, summary) ->
            root.put(id.toString(), JSONObject().apply {
                put("summary", summary)
                put("ts", nowMs)
            })
        }
        prefs.edit().putString(KEY_CHANGED_LESSON_DIFFS_JSON, root.toString()).apply()
    }

    fun getLocalSyncUpdatedAt(): Long = prefs.getLong(KEY_LOCAL_SYNC_UPDATED_AT, 0L)

    fun setLocalSyncUpdatedAt(timestampMs: Long) {
        prefs.edit().putLong(KEY_LOCAL_SYNC_UPDATED_AT, timestampMs).apply()
    }

    fun touchLocalSyncUpdatedAt(): Long {
        val next = maxOf(System.currentTimeMillis(), getLocalSyncUpdatedAt() + 1L)
        setLocalSyncUpdatedAt(next)
        return next
    }

    fun exportChoicesBackupJson(): String {
        val root = JSONObject().apply {
            put("backupVersion", 2)
            put("appThemeStyle", getAppThemeStyle().name)
            put("darkModePreference", getDarkModePreference().name)
            put("metroAccent", getMetroAccent().name)
            put("smartSubjectNamesEnabled", isSmartSubjectNamesEnabled())
            put("useSubjectColorsForMetroTiles", isUseSubjectColorsForMetroTiles())
            put("axolaCustomTheme", getAxolaCustomTheme().toJsonObject())
            put("calendarBridgeEnabled", getCalendarBridgeConfig().enabled)
            put("calendarHideCanceled", getCalendarBridgeConfig().hideCanceledLessons)
            put("lessonDndConfig", JSONObject().apply {
                val dnd = getLessonDndConfig()
                put("enabled", dnd.enabled)
                put("mode", dnd.mode.name)
                put("startBufferMinutes", dnd.startBufferMinutes)
                put("endBufferMinutes", dnd.endBufferMinutes)
                put("forceDndDuringExams", dnd.forceDndDuringExams)
            })
            put("subjectCustomizations", SubjectCustomizationCatalog.toJsonObject(loadSubjectCustomizations()))
            put("agendaStyle", getAgendaStyle().name)
            put("uiScale", getUiScale().toDouble())
            put("eloUrl", getEloUrl())
            put("hapticsEnabled", isHapticsEnabled())
            put("savedBusLine", getSavedBusLine())
            put("activeCommuteModeId", getActiveCommuteModeId())
            put("morningLockNotificationEnabled", isMorningLockNotificationEnabled())
            put("navPromptDismissed", isNavPromptDismissed())
            put("tutorialSeen", hasSeenTutorial())
            put("offlineModeEnabled", isOfflineModeEnabled())
            put("uitvalNotificationsEnabled", isUitvalNotificationsEnabled())
            put("gradeNotificationsEnabled", isGradeNotificationsEnabled())
            put("testNotificationsEnabled", isTestNotificationsEnabled())
            put("gradeUnboxingEnabled", isGradeUnboxingEnabled())
            put("gradeUnboxingMode", getGradeUnboxingMode().name)
            put("quietHoursEnabled", isQuietHoursEnabled())
            put("quietHoursStartMinutes", getQuietHoursStartMinutes())
            put("quietHoursEndMinutes", getQuietHoursEndMinutes())
            put("mutedSubjects", JSONArray(getMutedSubjects().toList()))
            put("smartBusAlertsEnabled", isSmartBusAlertsEnabled())
            put("busWalkMinutes", getBusWalkMinutes())
            put("busArrivalBufferMinutes", getBusArrivalBufferMinutes())

            val weather = getWeatherLocation()
            put("weatherLocation", JSONObject().apply {
                put("cityName", weather.cityName)
                put("latitude", weather.latitude)
                put("longitude", weather.longitude)
            })

            loadSchoolAddress()?.let { addr ->
                put("schoolAddress", JSONObject().apply {
                    put("displayName", addr.displayName)
                    put("streetAndCity", addr.streetAndCity)
                    put("latitude", addr.latitude)
                    put("longitude", addr.longitude)
                })
            }

            val blocksArr = JSONArray()
            loadTodayBlocksConfig().forEach { block ->
                blocksArr.put(JSONObject().apply {
                    put("type", block.type.name)
                    put("enabled", block.enabled)
                })
            }
            put("todayBlocks", blocksArr)

            val sitesArr = JSONArray()
            loadStudySites().forEach { s ->
                sitesArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("url", s.url)
                    put("category", s.category)
                    put("isDefault", s.isDefault)
                })
            }
            put("studySites", sitesArr)

            val commuteArr = JSONArray()
            getCommuteModes().forEach { mode ->
                commuteArr.put(JSONObject().apply {
                    put("id", mode.id)
                    put("type", mode.type.name)
                    put("label", mode.label)
                    put("busLine", mode.busLine)
                    put("bikeMinutes", mode.bikeMinutes)
                })
            }
            put("commuteModes", commuteArr)
        }
        return root.toString(2)
    }

    fun importChoicesBackupJson(rawJson: String): Result<Int> = runCatching {
        val root = JSONObject(rawJson)
        var restoredCount = 0

        root.optString("appThemeStyle").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { AppThemeStyle.valueOf(raw) }.getOrNull()?.let {
                setAppThemeStyle(it)
                restoredCount++
            }
        }
        root.optString("darkModePreference").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { DarkModePreference.valueOf(raw) }.getOrNull()?.let {
                setDarkModePreference(it)
                restoredCount++
            }
        }
        root.optString("metroAccent").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { MetroAccent.valueOf(raw) }.getOrNull()?.let {
                setMetroAccent(it)
                restoredCount++
            }
        }
        if (root.has("smartSubjectNamesEnabled")) {
            setSmartSubjectNamesEnabled(root.optBoolean("smartSubjectNamesEnabled", true))
            restoredCount++
        }
        if (root.has("useSubjectColorsForMetroTiles")) {
            setUseSubjectColorsForMetroTiles(root.optBoolean("useSubjectColorsForMetroTiles", false))
            restoredCount++
        }
        root.optJSONObject("subjectCustomizations")?.let { subjObj ->
            val parsed = SubjectCustomizationCatalog.fromJsonObject(subjObj)
            saveSubjectCustomizations(parsed)
            restoredCount++
        }
        root.optJSONObject("axolaCustomTheme")?.let { axolaObj ->
            saveAxolaCustomTheme(AxolaCustomTheme.fromJsonObject(axolaObj))
            restoredCount++
        }
        if (root.has("calendarBridgeEnabled") || root.has("calendarHideCanceled")) {
            val cur = getCalendarBridgeConfig()
            saveCalendarBridgeConfig(
                cur.copy(
                    enabled = root.optBoolean("calendarBridgeEnabled", cur.enabled),
                    hideCanceledLessons = root.optBoolean("calendarHideCanceled", cur.hideCanceledLessons)
                )
            )
            restoredCount++
        }
        root.optJSONObject("lessonDndConfig")?.let { dndObj ->
            val mode = runCatching {
                LessonDndMode.valueOf(dndObj.optString("mode", LessonDndMode.VIBRATE.name))
            }.getOrDefault(LessonDndMode.VIBRATE)
            saveLessonDndConfig(
                LessonDndConfig(
                    enabled = dndObj.optBoolean("enabled", false),
                    mode = mode,
                    startBufferMinutes = dndObj.optInt("startBufferMinutes", 1).coerceIn(0, 15),
                    endBufferMinutes = dndObj.optInt("endBufferMinutes", 0).coerceIn(0, 15),
                    forceDndDuringExams = dndObj.optBoolean("forceDndDuringExams", true)
                )
            )
            restoredCount++
        }
        root.optString("agendaStyle").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { AgendaStyle.valueOf(raw) }.getOrNull()?.let {
                setAgendaStyle(it)
                restoredCount++
            }
        }
        if (root.has("uiScale")) {
            setUiScale(root.optDouble("uiScale", 1.0).toFloat())
            restoredCount++
        }
        root.optString("eloUrl").takeIf { it.isNotBlank() }?.let {
            setEloUrl(it)
            restoredCount++
        }
        if (root.has("hapticsEnabled")) {
            setHapticsEnabled(root.optBoolean("hapticsEnabled", true))
            restoredCount++
        }
        if (root.has("savedBusLine")) {
            setSavedBusLine(root.optString("savedBusLine", ""))
            restoredCount++
        }
        if (root.has("activeCommuteModeId")) {
            setActiveCommuteModeId(root.optString("activeCommuteModeId", ""))
            restoredCount++
        }
        if (root.has("morningLockNotificationEnabled")) {
            setMorningLockNotificationEnabled(root.optBoolean("morningLockNotificationEnabled", true))
            restoredCount++
        }
        root.optJSONArray("commuteModes")?.let { arr ->
            val modes = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val type = runCatching { CommuteModeType.valueOf(o.optString("type")) }
                        .getOrDefault(CommuteModeType.BUS)
                    add(
                        CommuteModeConfig(
                            id = o.optString("id", "mode_$i"),
                            type = type,
                            label = o.optString("label", if (type == CommuteModeType.BIKE) "Fiets" else "Bus"),
                            busLine = o.optString("busLine", ""),
                            bikeMinutes = o.optInt("bikeMinutes", 25).coerceIn(5, 90)
                        )
                    )
                }
            }.take(3)
            if (modes.isNotEmpty()) {
                saveCommuteModes(modes)
                restoredCount++
            }
        }
        if (root.has("navPromptDismissed")) {
            setNavPromptDismissed(root.optBoolean("navPromptDismissed", false))
            restoredCount++
        }
        if (root.has("tutorialSeen")) {
            setTutorialSeen(root.optBoolean("tutorialSeen", false))
            restoredCount++
        }
        if (root.has("offlineModeEnabled")) {
            setOfflineModeEnabled(root.optBoolean("offlineModeEnabled", false))
            restoredCount++
        }
        if (root.has("uitvalNotificationsEnabled")) {
            setUitvalNotificationsEnabled(root.optBoolean("uitvalNotificationsEnabled", true))
            restoredCount++
        }
        if (root.has("gradeNotificationsEnabled")) {
            setGradeNotificationsEnabled(root.optBoolean("gradeNotificationsEnabled", true))
            restoredCount++
        }
        if (root.has("testNotificationsEnabled")) {
            setTestNotificationsEnabled(root.optBoolean("testNotificationsEnabled", true))
            restoredCount++
        }
        if (root.has("gradeUnboxingEnabled")) {
            setGradeUnboxingEnabled(root.optBoolean("gradeUnboxingEnabled", true))
            restoredCount++
        }
        root.optString("gradeUnboxingMode").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { GradeUnboxingMode.valueOf(raw) }.getOrNull()?.let {
                setGradeUnboxingMode(it)
                restoredCount++
            }
        }
        if (root.has("quietHoursEnabled")) {
            setQuietHoursEnabled(root.optBoolean("quietHoursEnabled", true))
            restoredCount++
        }
        if (root.has("quietHoursStartMinutes")) {
            setQuietHoursStartMinutes(root.optInt("quietHoursStartMinutes", 22 * 60))
            restoredCount++
        }
        if (root.has("quietHoursEndMinutes")) {
            setQuietHoursEndMinutes(root.optInt("quietHoursEndMinutes", 7 * 60))
            restoredCount++
        }
        root.optJSONArray("mutedSubjects")?.let { arr ->
            val set = buildSet {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank()) add(s)
                }
            }
            setMutedSubjects(set)
            restoredCount++
        }
        if (root.has("smartBusAlertsEnabled")) {
            setSmartBusAlertsEnabled(root.optBoolean("smartBusAlertsEnabled", true))
            restoredCount++
        }
        if (root.has("busWalkMinutes")) {
            setBusWalkMinutes(root.optInt("busWalkMinutes", 5))
            restoredCount++
        }
        if (root.has("busArrivalBufferMinutes")) {
            setBusArrivalBufferMinutes(root.optInt("busArrivalBufferMinutes", 10))
            restoredCount++
        }

        root.optJSONObject("weatherLocation")?.let { w ->
            val city = w.optString("cityName")
            if (city.isNotBlank()) {
                setWeatherLocation(city, w.optDouble("latitude", 52.0907), w.optDouble("longitude", 5.1214))
                restoredCount++
            }
        }

        root.optJSONObject("schoolAddress")?.let { a ->
            val displayName = a.optString("displayName")
            if (displayName.isNotBlank()) {
                saveSchoolAddress(
                    SchoolAddress(
                        displayName = displayName,
                        streetAndCity = a.optString("streetAndCity", displayName),
                        latitude = a.optDouble("latitude", 0.0),
                        longitude = a.optDouble("longitude", 0.0)
                    )
                )
                restoredCount++
            }
        }

        root.optJSONArray("todayBlocks")?.let { arr ->
            val blocks = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = runCatching { TodayBlockType.valueOf(obj.optString("type")) }.getOrNull() ?: continue
                    add(TodayBlockConfig(type = type, enabled = obj.optBoolean("enabled", true)))
                }
            }
            if (blocks.isNotEmpty()) {
                saveTodayBlocksConfig(blocks)
                restoredCount++
            }
        }

        root.optJSONArray("studySites")?.let { arr ->
            val sites = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    if (url.isNotBlank()) {
                        add(
                            StudySite(
                                id = obj.optString("id", "site_$i"),
                                name = obj.optString("name", "Studie"),
                                url = url,
                                category = obj.optString("category", "Oefenen"),
                                isDefault = obj.optBoolean("isDefault", false)
                            )
                        )
                    }
                }
            }
            if (sites.isNotEmpty()) {
                saveStudySites(sites)
                restoredCount++
            }
        }

        if (restoredCount == 0) {
            error("Geen geldige Agenda+-instellingen gevonden in dit bestand.")
        }
        restoredCount
    }

    companion object {
        private const val PREFS_NAME = "agenda_plus_prefs"
        private const val SECRET_SESSION_PREFS_NAME = "agenda_plus_secret_session_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_SKIPPED_LOGIN = "skipped_login"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_NAV_PROMPT_DISMISSED = "nav_prompt_dismissed"
        private const val KEY_OFFLINE_MODE_ENABLED = "offline_mode_enabled"
        private const val KEY_AGENDA_STYLE = "agenda_style"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_APP_THEME_STYLE = "app_theme_style"
        private const val KEY_DARK_MODE_PREF = "dark_mode_pref"
        private const val KEY_ELO_URL = "elo_url"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_SCHOOL_ADDRESS_JSON = "school_address_json"
        private const val KEY_SAVED_BUS_LINE = "saved_bus_line"
        private const val KEY_TODAY_BLOCKS_JSON = "today_blocks_json"
        private const val KEY_SESSION_JSON = "session_json"
        private const val KEY_APPOINTMENTS_JSON = "appointments_json"
        private const val KEY_GRADES_JSON = "grades_json"
        private const val KEY_TASKS_JSON = "tasks_json"
        private const val KEY_MESSAGES_JSON = "messages_json"
        private const val KEY_STUDY_GUIDES_JSON = "study_guides_json"
        private const val KEY_KWT_SLOTS_JSON = "kwt_slots_json"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_STUDY_SITES = "study_sites"
        private const val KEY_UITVAL_NOTIFS_ENABLED = "uitval_notifs_enabled"
        private const val KEY_GRADE_NOTIFS_ENABLED = "grade_notifs_enabled"
        private const val KEY_TEST_NOTIFS_ENABLED = "test_notifs_enabled"
        private const val KEY_NOTIFIED_CANCELED_KEYS = "notified_canceled_keys"
        private const val KEY_NOTIFIED_CHANGE_KEYS = "notified_change_keys"
        private const val KEY_KNOWN_GRADE_IDS = "known_grade_ids"
        private const val KEY_HAS_GRADES_BASELINE = "has_grades_baseline"
        private const val KEY_KNOWN_TEST_KEYS = "known_test_keys"
        private const val KEY_HAS_TESTS_BASELINE = "has_tests_baseline"
        private const val KEY_GRADE_UNBOXING_ENABLED = "grade_unboxing_enabled"
        private const val KEY_GRADE_UNBOXING_MODE = "grade_unboxing_mode"
        private const val KEY_UNBOXED_GRADE_IDS = "unboxed_grade_ids"
        private const val KEY_HAS_UNBOXED_BASELINE = "has_unboxed_baseline"
        private const val KEY_OVAPI_CATALOG_JSON = "ovapi_catalog_json"
        private const val KEY_OVAPI_CATALOG_TIMESTAMP = "ovapi_catalog_timestamp"
        private const val KEY_ABSENCES_JSON = "absences_json"
        private const val KEY_BUS_SNAPSHOT_JSON = "bus_snapshot_json"
        private const val KEY_TEST_PREP_JSON = "test_prep_json"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start_minutes"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end_minutes"
        private const val KEY_MUTED_SUBJECTS = "muted_subjects"
        private const val KEY_SMART_BUS_ALERTS = "smart_bus_alerts_enabled"
        private const val KEY_BUS_WALK_MINUTES = "bus_walk_minutes"
        private const val KEY_BUS_BUFFER_MINUTES = "bus_buffer_minutes"
        private const val KEY_LAST_SMART_BUS_KEY = "last_smart_bus_key"
        private const val KEY_COMMUTE_MODES_JSON = "commute_modes_json"
        private const val KEY_ACTIVE_COMMUTE_MODE_ID = "active_commute_mode_id"
        private const val KEY_MORNING_LOCK_NOTIF_ENABLED = "morning_lock_notif_enabled"
        private const val KEY_WEEKLY_FOCUS_MINUTES = "weekly_focus_minutes"
        private const val KEY_FOCUS_WEEK_KEY = "focus_week_key"
        private const val KEY_CHANGED_LESSON_DIFFS_JSON = "changed_lesson_diffs_json"
        private const val KEY_LOCAL_SYNC_UPDATED_AT = "local_sync_updated_at"
        private const val KEY_METRO_ACCENT = "metro_accent"
        private const val KEY_SMART_SUBJECT_NAMES = "smart_subject_names_enabled"
        private const val KEY_USE_SUBJECT_COLORS_METRO = "use_subject_colors_metro"
        private const val KEY_SUBJECT_CUSTOMIZATIONS_JSON = "subject_customizations_json"
        private const val KEY_AXOLA_CUSTOM_THEME_JSON = "axola_custom_theme_json"
        private const val KEY_CALENDAR_BRIDGE_ENABLED = "calendar_bridge_enabled"
        private const val KEY_CALENDAR_HIDE_CANCELED = "calendar_hide_canceled"
        private const val KEY_CALENDAR_LAST_SYNC_MS = "calendar_last_sync_ms"
        private const val KEY_CALENDAR_LAST_COUNT = "calendar_last_count"
        private const val KEY_LESSON_DND_ENABLED = "lesson_dnd_enabled"
        private const val KEY_LESSON_DND_MODE = "lesson_dnd_mode"
        private const val KEY_LESSON_DND_START_BUFFER = "lesson_dnd_start_buffer"
        private const val KEY_LESSON_DND_END_BUFFER = "lesson_dnd_end_buffer"
        private const val KEY_LESSON_DND_FORCE_EXAMS = "lesson_dnd_force_exams"
        private const val KEY_LESSON_DND_APPLIED = "lesson_dnd_applied"
        private const val KEY_LESSON_DND_PREV_RINGER = "lesson_dnd_prev_ringer"
        private const val KEY_LESSON_DND_PREV_FILTER = "lesson_dnd_prev_filter"
        const val DEFAULT_ELO_URL = "https://www.itslearning.com"

        fun splitToetsstofIntoTopics(rawText: String, subject: String = "", fallbackTitle: String = ""): List<TestPrepSubtopic> {
            val cleaned = rawText
                .replace("\r\n", "\n")
                .trim()
            val rawParts = cleaned
                .split(Regex("[,;\n•]+|\\s+en\\s+"))
                .map { it.trim().trimStart('-', '*', '•', ' ').trim() }
                .filter { it.length >= 2 }
                .distinct()
            val items = if (rawParts.isNotEmpty()) {
                rawParts
            } else {
                listOf(fallbackTitle.ifBlank { "Leerdoelen $subject".trim() })
            }
            return items.mapIndexed { idx, label ->
                TestPrepSubtopic(id = "topic_$idx", title = label, isChecked = false)
            }
        }

        fun defaultTodayBlocks(): List<TodayBlockConfig> = listOf(
            TodayBlockConfig(TodayBlockType.WEATHER, true),
            TodayBlockConfig(TodayBlockType.NEXT_LESSON, true),
            TodayBlockConfig(TodayBlockType.KWT_ALERT, true),
            TodayBlockConfig(TodayBlockType.SCHOOL_NAV, false),
            TodayBlockConfig(TodayBlockType.BUS_TIME, false),
            TodayBlockConfig(TodayBlockType.QUICK_ACTIONS, true),
            TodayBlockConfig(TodayBlockType.HOMEWORK, true)
        )

        fun defaultStudySites(): List<StudySite> = listOf(
            StudySite("study_quizlet", "Quizlet", "https://quizlet.com", "Begrippen & Flitskaarten", isDefault = true),
            StudySite("study_studygo", "StudyGo (WRTS)", "https://studygo.com/nl/", "Woordjes & Oefentoetsen", isDefault = true),
            StudySite("study_khan", "Khan Academy", "https://nl.khanacademy.org", "Wiskunde & Wetenschap", isDefault = true),
            StudySite("study_examenblad", "Examenblad", "https://www.examenblad.nl", "Oude Examens & Syllabi", isDefault = true),
            StudySite("study_cambiumned", "Cambiumned", "https://www.cambiumned.nl", "Nederlands Oefenen", isDefault = true)
        )
    }
}
