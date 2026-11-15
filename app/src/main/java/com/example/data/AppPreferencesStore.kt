package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject

data class WeatherLocationPref(
    val cityName: String,
    val latitude: Double,
    val longitude: Double
)

class AppPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
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
        prefs.edit().putString(KEY_SESSION_JSON, json.toString()).apply()
    }

    fun loadSession(): AuthSession {
        val raw = prefs.getString(KEY_SESSION_JSON, null) ?: return AuthSession()
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
                provider = SchoolProvider.MAGISTER
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
        prefs.edit()
            .remove(KEY_SESSION_JSON)
            .remove(KEY_APPOINTMENTS_JSON)
            .remove(KEY_GRADES_JSON)
            .remove(KEY_TASKS_JSON)
            .remove(KEY_MESSAGES_JSON)
            .remove(KEY_STUDY_GUIDES_JSON)
            .remove(KEY_KWT_SLOTS_JSON)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_NOTIFIED_CANCELED_KEYS)
            .remove(KEY_NOTIFIED_CHANGE_KEYS)
            .remove(KEY_KNOWN_GRADE_IDS)
            .remove(KEY_HAS_GRADES_BASELINE)
            .remove(KEY_KNOWN_TEST_KEYS)
            .remove(KEY_HAS_TESTS_BASELINE)
            .remove(KEY_UNBOXED_GRADE_IDS)
            .remove(KEY_HAS_UNBOXED_BASELINE)
            .apply()
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
                                    period = go.optInt("period", 1)
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
                            folderName = o.optString("folderName", folder.labelDutch)
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

    companion object {
        private const val PREFS_NAME = "agenda_plus_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_NAV_PROMPT_DISMISSED = "nav_prompt_dismissed"
        private const val KEY_OFFLINE_MODE_ENABLED = "offline_mode_enabled"
        private const val KEY_AGENDA_STYLE = "agenda_style"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_APP_THEME_STYLE = "app_theme_style"
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
        const val DEFAULT_ELO_URL = "https://www.itslearning.com"

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
