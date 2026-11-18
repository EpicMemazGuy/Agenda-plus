package com.example.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.AppPreferencesStore
import com.example.data.db.AgendaDatabase
import com.example.data.db.CustomScheduleItem
import com.example.model.AgendaStyle
import com.example.model.AppThemeStyle
import com.example.model.Appointment
import com.example.model.DarkModePreference
import com.example.model.GradeUnboxingMode
import com.example.model.HomeworkTask
import com.example.model.MagisterMessage
import com.example.model.StudySite
import com.example.model.SubjectGradeSummary
import com.example.model.TestPrepChecklist
import com.example.util.RawConsoleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SyncSourceOverride(
    val appointments: List<Appointment>? = null,
    val grades: List<SubjectGradeSummary>? = null,
    val tasks: List<HomeworkTask>? = null,
    val unboxedGradeIds: Set<String>? = null
)

data class SyncExportCounts(
    val lessons: Int = 0,
    val grades: Int = 0,
    val homework: Int = 0
)

data class WebSyncStatus(
    val isPaired: Boolean = false,
    val syncId: String = "",
    val lastSyncTimestamp: Long = 0L,
    val lastVersion: Long = 0L,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class PairingCredentials(
    val syncId: String,
    val aesKeyBytes: ByteArray
)

data class EncryptedSyncBlob(
    val ivBase64: String,
    val ciphertextBase64: String
)

sealed class PullSyncOutcome {
    data object NotPaired : PullSyncOutcome()
    data object EmptyRemote : PullSyncOutcome()
    data class UpToDate(val version: Long) : PullSyncOutcome()
    data class PulledPayload(
        val version: Long,
        val source: String,
        val updatedAt: Long,
        val payloadJson: JSONObject
    ) : PullSyncOutcome()
}

object SyncManager {
    private const val PREF_FILE = "agendaplus_e2ee_web_sync_prefs"
    private const val KEY_SYNC_ID = "sync_id"
    private const val KEY_AES_KEY_B64 = "aes_key_b64"
    private const val KEY_LAST_SYNC_TS = "last_sync_ts"
    private const val KEY_LAST_VERSION = "last_version"
    private const val KEY_LAST_HASH = "last_uploaded_hash"

    private const val SYNC_BASE_URL = "https://agendaplus.nl/api/sync"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val AES_KEY_BYTES = 32

    private val UUID_V4_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )
    private val UUID_GENERIC_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    private val syncMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val secureRandom = SecureRandom()

    private val _status = MutableStateFlow(WebSyncStatus())
    val status: StateFlow<WebSyncStatus> = _status.asStateFlow()

    @Volatile
    var lastExportCounts: SyncExportCounts = SyncExportCounts()
        private set

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            appContext.getSharedPreferences("${PREF_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun init(context: Context) {
        val prefs = getEncryptedPrefs(context)
        val syncId = prefs.getString(KEY_SYNC_ID, "").orEmpty()
        val keyB64 = prefs.getString(KEY_AES_KEY_B64, "").orEmpty()
        val lastSyncTs = prefs.getLong(KEY_LAST_SYNC_TS, 0L)
        val lastVersion = prefs.getLong(KEY_LAST_VERSION, 0L)
        val valid = isPairedCredentialsValid(syncId, keyB64)
        _status.value = WebSyncStatus(
            isPaired = valid,
            syncId = if (valid) syncId else "",
            lastSyncTimestamp = lastSyncTs,
            lastVersion = lastVersion
        )
    }

    private fun isPairedCredentialsValid(syncId: String, keyB64: String): Boolean {
        if (syncId.length != 36 || keyB64.isBlank()) return false
        return decodeAesKeyBase64(keyB64)?.size == AES_KEY_BYTES
    }

    fun parsePairingUri(uriString: String): Result<PairingCredentials> = runCatching {
        val raw = uriString.trim()
        require(raw.isNotEmpty()) { "Plak of scan een geldige koppel-URI." }

        val uri = Uri.parse(raw)
        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host?.lowercase(Locale.US)
        require(scheme == "agendaplus" && host == "pair") {
            "Ongeldige QR-code: verwacht agendaplus://pair?id=...&key=..."
        }

        val syncIdRaw = uri.getQueryParameter("id")?.trim()?.lowercase(Locale.US).orEmpty()
        require(syncIdRaw.length == 36 && UUID_GENERIC_REGEX.matches(syncIdRaw)) {
            "Ongeldig syncId in QR-code (verwacht lowercase UUIDv4)."
        }
        // Prefer strict UUIDv4 check when possible; reject obviously malformed UUIDs
        if (!UUID_V4_REGEX.matches(syncIdRaw)) {
            RawConsoleLogger.warn("WebSync", "SyncId gebruikt geen strikte v4 versie-nibble: $syncIdRaw")
        }

        val keyParam = uri.getQueryParameter("key")?.trim().orEmpty()
        val keyBytes = decodeAesKeyBase64(keyParam)
            ?: error("Ongeldige AES-sleutel in QR-code.")
        require(keyBytes.size == AES_KEY_BYTES) {
            "Ongeldige sleutellengte: verwacht 32 bytes (256-bit AES), kreeg ${keyBytes.size}."
        }

        PairingCredentials(syncId = syncIdRaw, aesKeyBytes = keyBytes)
    }

    private fun decodeAesKeyBase64(encodedKey: String): ByteArray? {
        if (encodedKey.isBlank()) return null
        return try {
            Base64.decode(encodedKey, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (_: Throwable) {
            try {
                Base64.decode(encodedKey, Base64.DEFAULT)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun pairWithUri(context: Context, uriString: String): Result<WebSyncStatus> {
        val parsed = parsePairingUri(uriString)
        return parsed.map { creds ->
            val encodedKey = Base64.encodeToString(
                creds.aesKeyBytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val prefs = getEncryptedPrefs(context)
            prefs.edit()
                .putString(KEY_SYNC_ID, creds.syncId)
                .putString(KEY_AES_KEY_B64, encodedKey)
                .remove(KEY_LAST_HASH)
                .remove(KEY_LAST_VERSION)
                .remove(KEY_LAST_SYNC_TS)
                .apply()

            val next = WebSyncStatus(
                isPaired = true,
                syncId = creds.syncId,
                lastSyncTimestamp = 0L,
                lastVersion = 0L,
                isBusy = false,
                statusMessage = "Gekoppeld met Web",
                errorMessage = null
            )
            _status.value = next
            RawConsoleLogger.info("WebSync", "Gekoppeld met Agenda+ Web (${creds.syncId.take(8)}...)")
            next
        }.onFailure { err ->
            _status.update {
                it.copy(
                    isBusy = false,
                    errorMessage = err.localizedMessage ?: "Koppelen mislukt"
                )
            }
        }
    }

    fun unpair(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .remove(KEY_SYNC_ID)
            .remove(KEY_AES_KEY_B64)
            .remove(KEY_LAST_SYNC_TS)
            .remove(KEY_LAST_VERSION)
            .remove(KEY_LAST_HASH)
            .apply()
        _status.value = WebSyncStatus(statusMessage = "Koppeling verbroken.")
        RawConsoleLogger.info("WebSync", "Koppeling met Agenda+ Web verbroken.")
    }

    private fun getStoredCredentials(context: Context): PairingCredentials? {
        val prefs = getEncryptedPrefs(context)
        val syncId = prefs.getString(KEY_SYNC_ID, null)?.trim()?.lowercase(Locale.US) ?: return null
        val keyB64 = prefs.getString(KEY_AES_KEY_B64, null) ?: return null
        val keyBytes = decodeAesKeyBase64(keyB64) ?: return null
        if (syncId.length != 36 || keyBytes.size != AES_KEY_BYTES) return null
        return PairingCredentials(syncId = syncId, aesKeyBytes = keyBytes)
    }

    fun isPaired(context: Context): Boolean = getStoredCredentials(context) != null

    fun encryptPayload(
        aesKeyBytes: ByteArray,
        plaintextUtf8: String,
        ivOverride: ByteArray? = null
    ): EncryptedSyncBlob {
        require(aesKeyBytes.size == AES_KEY_BYTES) { "AES-256 key must be 32 bytes" }
        val iv = ivOverride ?: ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        require(iv.size == GCM_IV_BYTES) { "GCM IV must be 12 bytes" }

        val keySpec = SecretKeySpec(aesKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertextWithTag = cipher.doFinal(plaintextUtf8.toByteArray(Charsets.UTF_8))

        return EncryptedSyncBlob(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertextWithTag, Base64.NO_WRAP)
        )
    }

    fun decryptPayload(
        aesKeyBytes: ByteArray,
        ivBase64: String,
        ciphertextBase64: String
    ): Result<String> = runCatching {
        require(aesKeyBytes.size == AES_KEY_BYTES) { "AES-256 key must be 32 bytes" }
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertextWithTag = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        require(iv.size == GCM_IV_BYTES) { "Ongeldige IV-lengte: ${iv.size}" }
        require(ciphertextWithTag.size >= 16) { "Ongeldige ciphertext-lengte" }

        val keySpec = SecretKeySpec(aesKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertextWithTag)
        String(plaintextBytes, Charsets.UTF_8)
    }

    suspend fun buildSyncPayloadJson(
        context: Context,
        updatedAt: Long = System.currentTimeMillis(),
        sourceOverride: SyncSourceOverride? = null
    ): JSONObject {
        val store = AppPreferencesStore(context.applicationContext)
        val customItems = runCatching {
            AgendaDatabase.getInstance(context.applicationContext).customScheduleDao().getAllOnce()
        }.getOrDefault(emptyList())

        val tasks = sourceOverride?.tasks?.takeIf { it.isNotEmpty() } ?: store.loadTasks()
        val appointments = sourceOverride?.appointments?.takeIf { it.isNotEmpty() } ?: store.loadAppointments()
        val grades = sourceOverride?.grades?.takeIf { it.isNotEmpty() } ?: store.loadGrades()
        val messages = store.loadMessages()
        val checklists = store.loadTestPrepChecklists()
        val studySites = store.loadStudySites()
        val unboxedGradeIds = sourceOverride?.unboxedGradeIds?.takeIf { it.isNotEmpty() } ?: store.getUnboxedGradeIds()

        return buildSyncPayloadJsonFromData(
            updatedAt = updatedAt,
            themeStyle = store.getAppThemeStyle(),
            darkMode = store.getDarkModePreference(),
            agendaStyle = store.getAgendaStyle(),
            uiScale = store.getUiScale(),
            unboxingMode = store.getGradeUnboxingMode(),
            busLine = store.getSavedBusLine(),
            schoolAddress = store.loadSchoolAddress()?.let {
                it.streetAndCity.ifBlank { it.displayName }
            }.orEmpty(),
            weatherCity = if (store.hasCustomWeatherLocation()) store.getWeatherLocation().cityName else "",
            customScheduleItems = customItems,
            tasks = tasks,
            testChecklists = checklists,
            studySites = studySites,
            appointments = appointments,
            grades = grades,
            messages = messages,
            unboxedGradeIds = unboxedGradeIds
        )
    }

    fun buildSyncPayloadJsonFromData(
        updatedAt: Long,
        themeStyle: AppThemeStyle,
        darkMode: DarkModePreference,
        agendaStyle: AgendaStyle,
        uiScale: Float,
        unboxingMode: GradeUnboxingMode,
        busLine: String,
        schoolAddress: String,
        weatherCity: String,
        customScheduleItems: List<CustomScheduleItem>,
        tasks: List<HomeworkTask>,
        testChecklists: Map<String, TestPrepChecklist>,
        studySites: List<StudySite>,
        appointments: List<Appointment>,
        grades: List<SubjectGradeSummary>,
        messages: List<MagisterMessage>,
        unboxedGradeIds: Set<String> = emptySet()
    ): JSONObject {
        val settingsObj = JSONObject().apply {
            put("theme", themeToWire(themeStyle))
            put("darkMode", darkModeToWire(darkMode))
            put("agendaDensity", agendaStyleToWire(agendaStyle))
            put("uiScale", kotlin.math.round(uiScale.coerceIn(0.85f, 1.20f) * 100f).toInt())
            put("unboxingMode", unboxingToWire(unboxingMode))
            put("busLine", busLine)
            put("schoolAddress", schoolAddress)
            put("weatherCity", weatherCity)
        }

        val customArr = JSONArray()
        customScheduleItems
            .sortedWith(compareBy<CustomScheduleItem> { it.startTime }.thenBy { it.title }.thenBy { it.id })
            .forEach { item ->
                customArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("description", item.description)
                    put("startTime", item.startTime)
                    put("endTime", item.endTime)
                    put("dateIso", item.dateIso)
                    put("daysOfWeekCsv", item.daysOfWeekCsv)
                    put("isRecurring", item.isRecurring)
                    put("colorKey", item.colorKey)
                    put("isCompleted", item.isCompleted)
                    put("createdAt", item.createdAt)
                })
            }

        val completedHomeworkArr = JSONArray()
        tasks.filter { it.isCompleted }.map { it.id }.sorted().forEach { id ->
            completedHomeworkArr.put(id)
        }

        val checklistsObj = JSONObject()
        testChecklists.keys.sorted().forEach { key ->
            val checklist = testChecklists[key] ?: return@forEach
            val topicsArr = JSONArray()
            checklist.topics.forEach { topic ->
                topicsArr.put(JSONObject().apply {
                    put("id", topic.id)
                    put("title", topic.title)
                    put("isChecked", topic.isChecked)
                })
            }
            checklistsObj.put(key, JSONObject().apply {
                put("key", checklist.key)
                put("subject", checklist.subject)
                put("title", checklist.title)
                put("topics", topicsArr)
            })
        }

        val sitesArr = JSONArray()
        studySites.forEach { site ->
            sitesArr.put(JSONObject().apply {
                put("id", site.id)
                put("name", site.name)
                put("url", site.url)
                put("category", site.category)
                put("isDefault", site.isDefault)
            })
        }

        val now = System.currentTimeMillis()
        val startCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -14)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 28)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val twoWeeksAgo = startCal.timeInMillis
        val fourWeeksAhead = endCal.timeInMillis
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun buildIsoTimestamp(dateIso: String, timeStr: String): String {
            val t = timeStr.trim()
            if (t.contains("T")) return t
            val hhmm = if (t.length == 5 && t[2] == ':') "$t:00" else t
            return if (dateIso.isNotBlank() && hhmm.isNotBlank()) "${dateIso}T${hhmm}" else hhmm
        }

        val lessonsArr = JSONArray()
        appointments
            .map { a ->
                val resolvedDateIso = a.dateIso.trim().take(10).ifBlank {
                    val dayCal = Calendar.getInstance().apply {
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                        add(Calendar.DAY_OF_YEAR, (a.dayOfWeek - 1).coerceIn(0, 6))
                    }
                    dateFormat.format(dayCal.time)
                }
                resolvedDateIso to a
            }
            .sortedWith(compareBy({ it.first }, { it.second.periodIndex }, { it.second.startTime }, { it.second.id }))
            .forEach { (resolvedDateIso, a) ->
                val apptTime = runCatching { dateFormat.parse(resolvedDateIso)?.time }.getOrNull() ?: now
                if (apptTime !in twoWeeksAgo..fourWeeksAhead) return@forEach

                val startIso = buildIsoTimestamp(resolvedDateIso, a.startTime)
                val endIso = buildIsoTimestamp(resolvedDateIso, a.endTime)
                val apptObj = JSONObject().apply {
                    put("id", a.id)
                    put("title", a.title)
                    put("subjectCode", a.subjectCode)
                    put("subjectName", a.subjectName)
                    put("subject", a.subjectName)
                    put("vak", a.subjectName)
                    put("teacherName", a.teacherName)
                    put("teacherInitials", a.teacherInitials)
                    put("teacher", a.teacherName)
                    put("docent", a.teacherName)
                    put("classroom", a.classroom)
                    put("lokaal", a.classroom)
                    put("room", a.classroom)
                    put("originalClassroom", a.originalClassroom ?: "")
                    put("periodIndex", a.periodIndex)
                    put("hour", a.periodIndex)
                    put("lesuur", a.periodIndex)
                    put("startTime", a.startTime)
                    put("endTime", a.endTime)
                    put("start", startIso)
                    put("end", endIso)
                    put("dayOfWeek", a.dayOfWeek)
                    put("isCanceled", a.isCanceled)
                    put("cancelled", a.isCanceled)
                    put("uitval", a.isCanceled)
                    put("isRelocated", a.isRelocated)
                    put("isSubstitution", a.isSubstitution)
                    put("homework", a.homework ?: "")
                    put("dateIso", resolvedDateIso)
                    put("date", resolvedDateIso)
                    put("isKwt", a.isKwt)
                    put("appointmentType", a.appointmentType)
                    put("infoType", a.infoType)
                    put("description", a.title)
                    put("omschrijving", a.title)
                }
                lessonsArr.put(apptObj)
            }

        val homeworkArr = JSONArray()
        tasks.forEach { t ->
            homeworkArr.put(JSONObject().apply {
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

        val gradesArr = JSONArray()
        grades.sortedBy { it.subjectName }.forEach { s ->
            s.grades.forEach { g ->
                if (g.score.isNaN() || g.score <= 0.0) return@forEach
                val subjectName = g.subjectName.ifBlank { s.subjectName }.ifBlank { "Algemeen" }
                val subjectCode = g.subjectCode.ifBlank { s.subjectCode }
                val gradeDisplayStr = String.format(Locale.US, "%.1f", g.score).replace(".", ",")
                val isUnboxed = g.id in unboxedGradeIds
                gradesArr.put(JSONObject().apply {
                    put("id", g.id)
                    put("subjectCode", subjectCode)
                    put("subjectName", subjectName)
                    put("subject", subjectName)
                    put("vak", subjectName)
                    put("title", g.description)
                    put("description", g.description)
                    put("omschrijving", g.description)
                    put("score", g.score)
                    put("grade", g.score)
                    put("value", g.score)
                    put("gradeDisplay", gradeDisplayStr)
                    put("cijfer", gradeDisplayStr)
                    put("weight", g.weight)
                    put("weging", g.weight)
                    put("date", g.date)
                    put("datum", g.date)
                    put("period", g.period)
                    put("isPta", g.isPta)
                    put("weightedAverage", s.weightedAverage)
                    put("unboxed", isUnboxed)
                    put("isNew", !isUnboxed)
                })
            }
        }

        val cachedDataObj = JSONObject().apply {
            put("lessons", lessonsArr)
            put("grades", gradesArr)
            put("homework", homeworkArr)
        }

        val schoolInfoObj = JSONObject().apply {
            put("schoolAddress", schoolAddress)
            put("busLine", busLine)
            put("weatherCity", weatherCity)
        }

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("source", "android")
            put("updatedAt", updatedAt)
            put("theme", themeToWire(themeStyle))
            put("schoolInfo", schoolInfoObj)
            put("settings", settingsObj)
            put("customScheduleItems", customArr)
            put("completedHomeworkIds", completedHomeworkArr)
            put("testChecklists", checklistsObj)
            put("studySites", sitesArr)
            put("cachedData", cachedDataObj)
        }
    }

    /**
     * Computes SHA-256 over the canonical payload content excluding volatile updatedAt
     * so repeated checks with identical state match lastUploadedHash.
     */
    fun computeContentHash(payloadJson: JSONObject): String {
        val canonical = JSONObject().apply {
            put("schemaVersion", payloadJson.optInt("schemaVersion", 1))
            put("settings", payloadJson.optJSONObject("settings") ?: JSONObject())
            put("customScheduleItems", payloadJson.optJSONArray("customScheduleItems") ?: JSONArray())
            put("completedHomeworkIds", payloadJson.optJSONArray("completedHomeworkIds") ?: JSONArray())
            put("testChecklists", payloadJson.optJSONObject("testChecklists") ?: JSONObject())
            put("studySites", payloadJson.optJSONArray("studySites") ?: JSONArray())
            put("cachedData", payloadJson.optJSONObject("cachedData") ?: JSONObject())
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun logOutgoingSyncPayload(payloadStr: String) {
        val tag = "AgendaSync"
        Log.d(tag, payloadStr)
        if (payloadStr.length > 3800) {
            val chunkSize = 3500
            val totalParts = (payloadStr.length + chunkSize - 1) / chunkSize
            for (i in 0 until totalParts) {
                val start = i * chunkSize
                val end = minOf(payloadStr.length, start + chunkSize)
                Log.d(tag, "[part ${i + 1}/$totalParts] ${payloadStr.substring(start, end)}")
            }
        }
    }

    suspend fun pushIfChanged(
        context: Context,
        force: Boolean = false,
        sourceOverride: SyncSourceOverride? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val appContext = context.applicationContext
            val creds = getStoredCredentials(appContext)
                ?: return@withLock Result.success(false)

            val prefs = getEncryptedPrefs(appContext)
            val store = AppPreferencesStore(appContext)
            val localUpdatedAt = store.getLocalSyncUpdatedAt().let { ts ->
                if (ts > 0L) ts else System.currentTimeMillis()
            }
            val payloadJson = buildSyncPayloadJson(
                context = appContext,
                updatedAt = localUpdatedAt,
                sourceOverride = sourceOverride
            )
            val cachedObj = payloadJson.optJSONObject("cachedData")
            lastExportCounts = SyncExportCounts(
                lessons = cachedObj?.optJSONArray("lessons")?.length() ?: 0,
                grades = cachedObj?.optJSONArray("grades")?.length() ?: 0,
                homework = cachedObj?.optJSONArray("homework")?.length() ?: 0
            )
            val contentHash = computeContentHash(payloadJson)
            val lastHash = prefs.getString(KEY_LAST_HASH, null)

            if (!force && contentHash == lastHash) {
                return@withLock Result.success(false)
            }

            _status.update { it.copy(isBusy = true, errorMessage = null) }

            val lastVersion = prefs.getLong(KEY_LAST_VERSION, 0L)
            val version = maxOf(System.currentTimeMillis(), lastVersion + 1L)
            val payloadStr = payloadJson.toString()
            logOutgoingSyncPayload(payloadStr)
            val blob = encryptPayload(creds.aesKeyBytes, payloadStr)

            val postBodyJson = JSONObject().apply {
                put("version", version)
                put("iv", blob.ivBase64)
                put("ciphertext", blob.ciphertextBase64)
            }.toString()

            val request = Request.Builder()
                .url("$SYNC_BASE_URL/${creds.syncId}")
                .post(postBodyJson.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = "Sync upload mislukt (HTTP ${response.code})"
                        _status.update { it.copy(isBusy = false, errorMessage = errMsg) }
                        return@withLock Result.failure(IllegalStateException(errMsg))
                    }
                }
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putString(KEY_LAST_HASH, contentHash)
                    .putLong(KEY_LAST_VERSION, version)
                    .putLong(KEY_LAST_SYNC_TS, now)
                    .apply()
                _status.update {
                    it.copy(
                        isPaired = true,
                        syncId = creds.syncId,
                        lastSyncTimestamp = now,
                        lastVersion = version,
                        isBusy = false,
                        statusMessage = "Gekoppeld met Web",
                        errorMessage = null
                    )
                }
                RawConsoleLogger.info("WebSync", "E2EE payload geüpload (v$version)")
                Result.success(true)
            } catch (t: Throwable) {
                val errMsg = t.localizedMessage ?: "Netwerkfout bij synchroniseren"
                _status.update { it.copy(isBusy = false, errorMessage = errMsg) }
                Result.failure(t)
            }
        }
    }

    fun pushIfChangedAsync(context: Context) {
        val appContext = context.applicationContext
        if (!isPaired(appContext)) return
        backgroundScope.launch {
            runCatching { pushIfChanged(appContext, force = false) }
        }
    }

    suspend fun pullLatest(context: Context): Result<PullSyncOutcome> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val appContext = context.applicationContext
            val creds = getStoredCredentials(appContext)
                ?: return@withLock Result.success(PullSyncOutcome.NotPaired)

            _status.update { it.copy(isBusy = true, errorMessage = null) }

            val request = Request.Builder()
                .url("$SYNC_BASE_URL/${creds.syncId}")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = "Sync ophalen mislukt (HTTP ${response.code})"
                        _status.update { it.copy(isBusy = false, errorMessage = errMsg) }
                        return@withLock Result.failure(IllegalStateException(errMsg))
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        _status.update { it.copy(isBusy = false) }
                        return@withLock Result.success(PullSyncOutcome.EmptyRemote)
                    }

                    val root = JSONObject(body)
                    val remoteVersion = root.optLong("version", 0L)
                    if (remoteVersion == 0L && root.has("payload") && root.isNull("payload")) {
                        _status.update { it.copy(isBusy = false, statusMessage = "Nog geen web-data") }
                        return@withLock Result.success(PullSyncOutcome.EmptyRemote)
                    }

                    val iv = root.optString("iv", "")
                    val ciphertext = root.optString("ciphertext", "")
                    if (iv.isBlank() || ciphertext.isBlank()) {
                        _status.update { it.copy(isBusy = false) }
                        return@withLock Result.success(PullSyncOutcome.EmptyRemote)
                    }

                    val decrypted = decryptPayload(creds.aesKeyBytes, iv, ciphertext).getOrElse { err ->
                        val msg = "Decryptie mislukt: ${err.localizedMessage ?: "ongeldige sleutel"}"
                        _status.update { it.copy(isBusy = false, errorMessage = msg) }
                        return@withLock Result.failure(err)
                    }

                    val payloadJson = JSONObject(decrypted)
                    val source = payloadJson.optString("source", "")
                    val payloadUpdatedAt = payloadJson.optLong(
                        "updatedAt",
                        root.optLong("updatedAt", remoteVersion)
                    )

                    val prefs = getEncryptedPrefs(appContext)
                    val now = System.currentTimeMillis()
                    val mergedVersion = maxOf(remoteVersion, prefs.getLong(KEY_LAST_VERSION, 0L))
                    prefs.edit()
                        .putLong(KEY_LAST_VERSION, mergedVersion)
                        .putLong(KEY_LAST_SYNC_TS, now)
                        .apply()

                    _status.update {
                        it.copy(
                            isPaired = true,
                            syncId = creds.syncId,
                            lastSyncTimestamp = now,
                            lastVersion = mergedVersion,
                            isBusy = false,
                            statusMessage = "Gekoppeld met Web",
                            errorMessage = null
                        )
                    }

                    val localUpdatedAt = AppPreferencesStore(appContext).getLocalSyncUpdatedAt()
                    if (source == "web" && payloadUpdatedAt > localUpdatedAt) {
                        Result.success(
                            PullSyncOutcome.PulledPayload(
                                version = mergedVersion,
                                source = source,
                                updatedAt = payloadUpdatedAt,
                                payloadJson = payloadJson
                            )
                        )
                    } else {
                        Result.success(PullSyncOutcome.UpToDate(mergedVersion))
                    }
                }
            } catch (t: Throwable) {
                val errMsg = t.localizedMessage ?: "Netwerkfout bij ophalen van Web"
                _status.update { it.copy(isBusy = false, errorMessage = errMsg) }
                Result.failure(t)
            }
        }
    }

    suspend fun recordMergedHash(context: Context) {
        val appContext = context.applicationContext
        if (!isPaired(appContext)) return
        val prefs = getEncryptedPrefs(appContext)
        val store = AppPreferencesStore(appContext)
        val payload = buildSyncPayloadJson(appContext, updatedAt = store.getLocalSyncUpdatedAt())
        val hash = computeContentHash(payload)
        prefs.edit().putString(KEY_LAST_HASH, hash).apply()
    }

    fun themeToWire(style: AppThemeStyle): String = when (style) {
        AppThemeStyle.STANDARD -> "material_you"
        AppThemeStyle.NOTHING_OS -> "nothing_os"
        AppThemeStyle.ONE_UI -> "one_ui"
    }

    fun themeFromWire(raw: String?): AppThemeStyle? = when (raw?.trim()?.lowercase(Locale.US)) {
        "material_you", "standard" -> AppThemeStyle.STANDARD
        "nothing_os", "nothing" -> AppThemeStyle.NOTHING_OS
        "one_ui", "oneui", "one_ui_8_5" -> AppThemeStyle.ONE_UI
        else -> null
    }

    fun darkModeToWire(pref: DarkModePreference): String = when (pref) {
        DarkModePreference.SYSTEM -> "system"
        DarkModePreference.LIGHT -> "light"
        DarkModePreference.DARK -> "dark"
    }

    fun darkModeFromWire(raw: String?): DarkModePreference? = when (raw?.trim()?.lowercase(Locale.US)) {
        "system" -> DarkModePreference.SYSTEM
        "light" -> DarkModePreference.LIGHT
        "dark" -> DarkModePreference.DARK
        else -> null
    }

    fun agendaStyleToWire(style: AgendaStyle): String = when (style) {
        AgendaStyle.COMPACT -> "compact"
        AgendaStyle.SPACIOUS -> "spacious"
    }

    fun agendaStyleFromWire(raw: String?): AgendaStyle? = when (raw?.trim()?.lowercase(Locale.US)) {
        "compact" -> AgendaStyle.COMPACT
        "spacious" -> AgendaStyle.SPACIOUS
        else -> null
    }

    fun unboxingToWire(mode: GradeUnboxingMode): String = when (mode) {
        GradeUnboxingMode.CASE_OPENING -> "case_opening"
        GradeUnboxingMode.TAP_IT_UP -> "tap_it_up"
        GradeUnboxingMode.PENALTIES -> "penalties"
        GradeUnboxingMode.ULTIMATE_GRADE -> "ultimate_grade"
        GradeUnboxingMode.PLINKO_DROP -> "plinko_drop"
    }

    fun unboxingFromWire(raw: String?): GradeUnboxingMode? = when (raw?.trim()?.lowercase(Locale.US)) {
        "case_opening" -> GradeUnboxingMode.CASE_OPENING
        "tap_it_up" -> GradeUnboxingMode.TAP_IT_UP
        "penalties" -> GradeUnboxingMode.PENALTIES
        "ultimate_grade" -> GradeUnboxingMode.ULTIMATE_GRADE
        "plinko_drop" -> GradeUnboxingMode.PLINKO_DROP
        else -> null
    }
}
