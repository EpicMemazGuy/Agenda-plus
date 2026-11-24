package com.example.model

enum class SchoolProvider(val displayName: String) {
    MAGISTER("Magister")
}

enum class MessageFolder(val labelDutch: String) {
    INBOX("Postvak In"),
    SENT("Verzonden"),
    DRAFT("Concepten"),
    TRASH("Prullenbak"),
    OTHER("Overige mappen")
}

enum class DisruptionType {
    CANCELED,          // Uitval
    ROOM_CHANGE,       // Lokaalwijziging
    SUBSTITUTION       // Docentvervanging
}

enum class TaskCategory(val labelDutch: String) {
    HOMEWORK("Huiswerk"),
    EXAM("Toets"),
    MAJOR_TEST("Grote Toets"),
    PRESENTATION("Presentatie"),
    PRACTICAL_ASSIGNMENT("PO")
}

enum class TaskPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class AbsenceType(val labelDutch: String) {
    SICK("Ziekmelding"),
    TARDY("Te laat"),
    EXCUSED("Geoorloofd verzuim"),
    UNEXCUSED("Ongeoorloofd")
}

enum class AgendaStyle(val labelDutch: String, val subtitleDutch: String) {
    COMPACT("Compact", "Strakke, dicht opeengepakte lesrijen"),
    SPACIOUS("Ruim", "Uitgebreide leskaarten met extra ruimte")
}

enum class UiScalePreset(val labelDutch: String, val scaleFactor: Float) {
    COMPACT("Klein", 0.90f),
    NORMAL("Standaard", 1.0f),
    LARGE("Groot", 1.12f)
}

enum class AppThemeStyle(val labelDutch: String, val subtitleDutch: String) {
    STANDARD("Material You", "Huidige Material You stijl (standaard)"),
    NOTHING_OS("Nothing OS", "OLED zwart, Ndot matrix & Nothing Red"),
    ONE_UI("One UI 8.5", "Samsung 26dp squircles, wallpaper-kleuren & AMOLED"),
    WINDOWS_PHONE("Windows Phone", "0dp Metro Live Tiles & lowercase Panorama titels"),
    IOS_LIQUID_GLASS("iOS Liquid Glass", "22dp squircles, frosted glass & SF typografie"),
    AXOLA_CUSTOM("AXOLA Studio", "Zelf gebouwd thema met eigen kleuren & vorm")
}

enum class MetroAccent(
    val labelDutch: String,
    val colorHex: Long,
    val hexString: String
) {
    COBALT("Cobalt", 0xFF1BA1E2L, "#1BA1E2"),
    EMERALD("Emerald", 0xFF008A00L, "#008A00"),
    CRIMSON("Crimson", 0xFFE51400L, "#E51400"),
    AMBER("Amber", 0xFFF0A30AL, "#F0A30A");

    val argb: Long get() = colorHex
}

enum class DarkModePreference(val labelDutch: String) {
    SYSTEM("Systeem"),
    LIGHT("Licht"),
    DARK("Donker")
}

enum class GradeUnboxingMode(val labelDutch: String, val subtitleDutch: String) {
    CASE_OPENING("Case Opening", "CS:GO-stijl roulette met zeldzaamheidskleuren"),
    TAP_IT_UP("Tap It Up", "Tik razendsnel om de kluis open te breken"),
    PENALTIES("Penalties", "Scoor vanaf de stip voor je cijfer"),
    ULTIMATE_GRADE("Ultimate Grade", "Stadion tunnel & flare walkout reveal"),
    PLINKO_DROP("Plinko Drop", "Laat de bal stuiteren naar je cijfervak")
}

enum class TodayBlockType(val labelDutch: String, val descriptionDutch: String) {
    WEATHER("Weer", "Actuele temperatuur en weersverwachting"),
    NEXT_LESSON("Volgende les", "Live afteller met lokaal en docent"),
    SCHOOL_NAV("Snel naar school", "Directe 1-tik route in Google Maps naar jouw school"),
    BUS_TIME("Bustijd & Kaart", "Live buslijn, haltes, GPS en looproute"),
    QUICK_ACTIONS("Snelkoppelingen", "ELO-portaal en lesuur-teller"),
    HOMEWORK("Huiswerk", "Openstaande taken en toetsen"),
    KWT_ALERT("Keuzelessen (KWT)", "Melding zodra je een keuzeles kunt kiezen")
}

data class TodayBlockConfig(
    val type: TodayBlockType,
    val enabled: Boolean
)

data class SchoolAddress(
    val displayName: String,
    val streetAndCity: String,
    val latitude: Double,
    val longitude: Double
)

data class BusDeparture(
    val expectedTime: String,
    val scheduledTime: String? = null,
    val destination: String? = null
)

data class BusStopInfo(
    val timingPointCode: String,
    val name: String,
    val town: String,
    val latitude: Double,
    val longitude: Double,
    val expectedArrival: String? = null,
    val scheduledArrival: String? = null,
    val destination: String? = null,
    val isRealtime: Boolean = false,
    val delayMinutes: Int = 0,
    val upcomingDepartures: List<BusDeparture> = emptyList()
)

data class BusVehiclePoint(
    val id: String,
    val lineNumber: String,
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val stopName: String,
    val expectedTime: String,
    val delayMinutes: Int = 0
)

enum class CommuteModeType(val labelDutch: String) {
    BUS("Bus"),
    BIKE("Fiets")
}

data class CommuteModeConfig(
    val id: String,
    val type: CommuteModeType,
    val label: String,
    val busLine: String = "",
    val bikeMinutes: Int = 20
)

data class RainForecastWindow(
    val maxProbabilityPercent: Int = 0,
    val maxPrecipitationMm: Double = 0.0,
    val isRainExpected: Boolean = false,
    val summaryDutch: String = ""
)

data class BusTrackingState(
    val lineNumber: String = "",
    val routeName: String = "",
    val stops: List<BusStopInfo> = emptyList(),
    val liveVehicles: List<BusVehiclePoint> = emptyList(),
    val nearestStop: BusStopInfo? = null,
    val nearestDistanceMeters: Int? = null,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val lastUpdatedLabel: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusNotice: String? = null
)

data class SchoolTenant(
    val id: String,
    val name: String,
    val city: String,
    val subdomain: String,
    val provider: SchoolProvider = SchoolProvider.MAGISTER
) {
    val apiBaseUrl: String get() = "https://$subdomain.magister.net/api"
}

data class Appointment(
    val id: Long,
    val title: String,
    val subjectCode: String,
    val subjectName: String,
    val teacherName: String,
    val teacherInitials: String,
    val classroom: String,
    val originalClassroom: String? = null,
    val periodIndex: Int,
    val startTime: String,
    val endTime: String,
    val dayOfWeek: Int, // 1 = Monday ... 7 = Sunday
    val isCanceled: Boolean = false,
    val isRelocated: Boolean = false,
    val isSubstitution: Boolean = false,
    val homework: String? = null,
    val dateIso: String = "",
    val isKwt: Boolean = false,
    val appointmentType: Int = 0,
    val infoType: Int = 0
) {
    /**
     * Official Magister test detection (PW, SO, Toets, MO, PO).
     * Official Magister InfoType values:
     *   0 = Geen / standaard
     *   1 = Huiswerk (NEVER a test, even if homework text says "Oefenen SO"!)
     *   2 = Proefwerk (PW)
     *   3 = Toets / Tentamen
     *   4 = Schriftelijke Overhoring (SO)
     *   5 = Mondelinge Overhoring (MO)
     * Magister AppointmentType:
     *   7 = Toets
     * If infoType == 0, we check if the rooster appointment title designates
     * a test (e.g. "PW Wiskunde", "SO Frans", "PO Geschiedenis").
     * CRITICAL: Homework text is NEVER used to mark an appointment as a test.
     */
    val isOfficialMagisterTest: Boolean
        get() = infoType in 2..5 || appointmentType == 7

    val isTestOrExam: Boolean
        get() {
            if (isOfficialMagisterTest) return true
            if (infoType == 1 && appointmentType != 7) {
                // Magister explicitly marked this appointment as Huiswerk
                return false
            }
            return isOfficialTestTitle(title)
        }

    val isPresentation: Boolean
        get() = isPresentationKeywordMatch(title) || isPresentationKeywordMatch(homework)

    val isPracticalAssignment: Boolean
        get() = isPoMatch(title)
}

data class Disruption(
    val id: String,
    val title: String,
    val subject: String,
    val period: Int,
    val description: String,
    val type: DisruptionType
)

data class HomeworkTask(
    val id: String,
    val subject: String,
    val title: String,
    val description: String,
    val dueDay: String,
    val dueDate: String,
    val category: TaskCategory,
    val isCompleted: Boolean = false,
    val priority: TaskPriority = TaskPriority.MEDIUM
) {
    val isTest: Boolean
        get() = category == TaskCategory.EXAM ||
            category == TaskCategory.MAJOR_TEST ||
            category == TaskCategory.PRACTICAL_ASSIGNMENT

    val isPresentation: Boolean
        get() = category == TaskCategory.PRESENTATION
}

enum class GradeScopeFilter(val labelDutch: String) {
    ALL("Alle"),
    REGULAR("Regulier"),
    PTA("PTA")
}

data class GradeItem(
    val id: String,
    val subjectCode: String,
    val subjectName: String,
    val score: Double,
    val weight: Double,
    val description: String,
    val date: String,
    val period: Int,
    val isPta: Boolean = false
) {
    val isPassing: Boolean get() = score >= 5.5
}

data class SubjectGradeSummary(
    val subjectCode: String,
    val subjectName: String,
    val weightedAverage: Double,
    val grades: List<GradeItem>
) {
    val isPassingAverage: Boolean get() = weightedAverage >= 5.5
}

data class AbsenceRecord(
    val id: String,
    val date: String,
    val period: String,
    val type: AbsenceType,
    val reason: String,
    val isApproved: Boolean,
    val startIso: String = "",
    val endIso: String = "",
    val hour: Int? = null,
    val subject: String = "",
    val code: String = ""
)

data class AnnouncementItem(
    val id: String,
    val title: String,
    val author: String,
    val date: String,
    val content: String,
    val isPinned: Boolean = false
)

data class MessageFolderInfo(
    val id: Long,
    val name: String,
    val unreadCount: Int = 0,
    val folderType: MessageFolder = MessageFolder.OTHER
)

data class MessageRecipient(
    val id: Long,
    val name: String,
    val roleLabel: String = ""
)

data class MagisterMessage(
    val id: String,
    val subject: String,
    val senderName: String,
    val senderEmail: String? = null,
    val dateSent: String,
    val body: String,
    val isRead: Boolean = false,
    val hasAttachments: Boolean = false,
    val folder: MessageFolder = MessageFolder.INBOX,
    val folderId: Long = 1L,
    val folderName: String = "Postvak In",
    val recipients: String = "",
    val rawDateIso: String = ""
)

data class StudyGuideResource(
    val id: String,
    val name: String,
    val url: String,
    val contentType: String = "Link"
)

data class StudyGuidePart(
    val id: String,
    val title: String,
    val description: String,
    val sortOrder: Int = 0,
    val resources: List<StudyGuideResource> = emptyList()
)

data class StudyGuide(
    val id: String,
    val title: String,
    val subjectCode: String,
    val subjectName: String,
    val description: String = "",
    val parts: List<StudyGuidePart> = emptyList()
)

data class KwtOption(
    val id: Long,
    val title: String,
    val subjectName: String,
    val teacherName: String,
    val classroom: String,
    val enrolledCount: Int = 0,
    val maxCount: Int = 0,
    val isEnrolled: Boolean = false
)

data class KwtSlot(
    val appointmentId: Long,
    val title: String,
    val dateIso: String,
    val startTime: String,
    val endTime: String,
    val periodIndex: Int,
    val options: List<KwtOption>
)

data class StudySite(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val isDefault: Boolean = false
)

data class WeatherInfo(
    val cityName: String,
    val temperatureC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val windKmh: Double,
    val conditionDutch: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val latitude: Double,
    val longitude: Double,
    val hourlyTimesIso: List<String> = emptyList(),
    val hourlyPrecipProb: List<Int> = emptyList(),
    val hourlyPrecipMm: List<Double> = emptyList()
)

data class AuthSession(
    val isAuthenticated: Boolean = false,
    val studentName: String = "",
    val studentId: String = "",
    val studentClass: String = "",
    val school: SchoolTenant? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val personId: Long = 0L,
    val provider: SchoolProvider = SchoolProvider.MAGISTER,
    val expiresAt: Long = 0L
)

data class TestPrepSubtopic(
    val id: String,
    val title: String,
    val isChecked: Boolean = false
)

data class TestPrepChecklist(
    val key: String,
    val subject: String,
    val title: String,
    val topics: List<TestPrepSubtopic> = emptyList()
) {
    val completedCount: Int get() = topics.count { it.isChecked }
    val progress: Float get() = if (topics.isEmpty()) 0f else completedCount.toFloat() / topics.size.toFloat()
}

data class QuietHoursConfig(
    val enabled: Boolean = true,
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 7 * 60
)

data class SmartBusAdvice(
    val busLine: String,
    val stopName: String,
    val busDepartureTime: String,
    val leaveHomeTime: String,
    val firstActiveLesson: Appointment,
    val shiftReasonMessage: String? = null,
    val alertKey: String = "",
    val shouldNotifyPriority: Boolean = false,
    val commuteMode: CommuteModeType = CommuteModeType.BUS,
    val delayMinutes: Int = 0,
    val rainWarning: String? = null,
    val longerSleepMinutes: Int = 0
)

data class BusWidgetSnapshot(
    val busLine: String = "",
    val stopName: String = "",
    val expectedTime: String = "",
    val delayMinutes: Int = 0,
    val smartAdviceText: String = "",
    val updatedLabel: String = ""
)

fun String?.cleanSchoolText(): String? {
    val trimmed = this?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    if (trimmed.equals("null", ignoreCase = true)) return null
    if (trimmed.equals("undefined", ignoreCase = true)) return null
    return trimmed
}

fun String?.capitalizeSubjectName(): String {
    val clean = this.cleanSchoolText() ?: return ""
    return clean.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(java.util.Locale.forLanguageTag("nl-NL")) else ch.toString()
    }
}

private val PRESENTATION_KEYWORD_REGEX = Regex(
    """\b(spreekbeurt|presentatie|presenteren|mondelinge presentatie)\b""",
    RegexOption.IGNORE_CASE
)

fun isPresentationKeywordMatch(text: String?): Boolean {
    val clean = text.cleanSchoolText() ?: return false
    return PRESENTATION_KEYWORD_REGEX.containsMatchIn(clean)
}

private val PO_KEYWORD_REGEX = Regex(
    """\b(po|praktische opdracht|inleveropdracht)\b""",
    RegexOption.IGNORE_CASE
)

fun isPoMatch(text: String?): Boolean {
    val clean = text.cleanSchoolText() ?: return false
    return PO_KEYWORD_REGEX.containsMatchIn(clean)
}

private val OFFICIAL_TEST_TITLE_REGEX = Regex(
    """\b(pw|proefwerk|so|schriftelijke overhoring|toets|tentamen|examen|repetitie|mo|mondelinge overhoring|po|praktische opdracht)\b""",
    RegexOption.IGNORE_CASE
)

fun isOfficialTestTitle(title: String?): Boolean {
    val clean = title.cleanSchoolText() ?: return false
    return OFFICIAL_TEST_TITLE_REGEX.containsMatchIn(clean)
}

private val MAJOR_TEST_TITLE_REGEX = Regex(
    """\b(pw|proefwerk|tentamen|examen|repetitie)\b""",
    RegexOption.IGNORE_CASE
)

fun isMajorTestTitle(title: String?): Boolean {
    val clean = title.cleanSchoolText() ?: return false
    return MAJOR_TEST_TITLE_REGEX.containsMatchIn(clean)
}

private val EXAM_TITLE_REGEX = Regex(
    """\b(so|schriftelijke overhoring|toets|mo|mondelinge overhoring|luistertoets)\b""",
    RegexOption.IGNORE_CASE
)

fun isExamTitle(title: String?): Boolean {
    val clean = title.cleanSchoolText() ?: return false
    return EXAM_TITLE_REGEX.containsMatchIn(clean)
}

/**
 * Backward-compatibility helper: ONLY checks if a text qualifies as an official test title,
 * or if it contains a presentation keyword.
 * NOTE: NEVER call this on homework body text to detect a test, as teacher notes like "Oefenen SO"
 * are regular homework exercises, not tests!
 */
fun isTestKeywordMatch(text: String?): Boolean {
    val clean = text.cleanSchoolText() ?: return false
    return isOfficialTestTitle(clean)
}

enum class AxolaBackgroundStyle(
    val labelDutch: String,
    val canvasArgb: Long,
    val cardArgb: Long,
    val elevatedArgb: Long,
    val isDark: Boolean
) {
    OLED_BLACK("OLED Zwart (#000000)", 0xFF000000L, 0xFF141416L, 0xFF1E1E22L, true),
    DEEP_SLATE("Diep Leisteen (#0F1420)", 0xFF0F1420L, 0xFF182032L, 0xFF222D45L, true),
    LIGHT_MODE("Licht (#F4F6FA)", 0xFFF4F6FAL, 0xFFFFFFFFL, 0xFFE9EEF7L, false)
}

enum class AxolaBorderStyle(val labelDutch: String) {
    NONE("Geen"),
    SUBTLE_1DP("Subtiele 1dp lijn"),
    NEON_GLOW("Neon / Accent gloed")
}

enum class AxolaTypographyStyle(val labelDutch: String) {
    MODERN_SANS("Modern Sans"),
    NOTHING_DOT_MATRIX("Nothing Dot-Matrix"),
    WINDOWS_LIGHT_LOWERCASE("Windows Light Lowercase"),
    TERMINAL_MONO("Terminal Monospace")
}

data class AxolaCustomTheme(
    val basePreset: AppThemeStyle = AppThemeStyle.NOTHING_OS,
    val cornerRadiusDp: Int = 20,
    val backgroundStyle: AxolaBackgroundStyle = AxolaBackgroundStyle.OLED_BLACK,
    val primaryAccentHex: String = "#3B82F6",
    val secondaryAccentHex: String = "#EC4899",
    val borderStyle: AxolaBorderStyle = AxolaBorderStyle.SUBTLE_1DP,
    val typographyStyle: AxolaTypographyStyle = AxolaTypographyStyle.MODERN_SANS
) {
    val primaryColorArgb: Long
        get() = parseHexArgb(primaryAccentHex) ?: 0xFF3B82F6L

    val secondaryColorArgb: Long
        get() = parseHexArgb(secondaryAccentHex) ?: 0xFFEC4899L

    fun toJsonObject(): org.json.JSONObject = org.json.JSONObject().apply {
        put("v", 1)
        put("base", basePreset.name)
        put("radius", cornerRadiusDp.coerceIn(0, 28))
        put("bg", backgroundStyle.name)
        put("primary", normalizeHex(primaryAccentHex, "#3B82F6"))
        put("secondary", normalizeHex(secondaryAccentHex, "#EC4899"))
        put("border", borderStyle.name)
        put("font", typographyStyle.name)
    }

    fun encodeToThemeCode(): String {
        val rawBytes = toJsonObject().toString().toByteArray(Charsets.UTF_8)
        return "$THEME_CODE_PREFIX${encodeBase64Url(rawBytes)}"
    }

    companion object {
        const val THEME_CODE_PREFIX = "AXOLA-THEME://"
        private const val B64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        private fun encodeBase64Url(input: ByteArray): String {
            if (input.isEmpty()) return ""
            val out = StringBuilder((input.size * 4 + 2) / 3)
            var i = 0
            while (i < input.size) {
                val b0 = input[i++].toInt() and 0xFF
                val hasB1 = i < input.size
                val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
                val hasB2 = i < input.size
                val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0
                out.append(B64_URL_ALPHABET[(b0 ushr 2) and 0x3F])
                out.append(B64_URL_ALPHABET[((b0 and 0x03) shl 4) or ((b1 ushr 4) and 0x0F)])
                if (hasB1) out.append(B64_URL_ALPHABET[((b1 and 0x0F) shl 2) or ((b2 ushr 6) and 0x03)])
                if (hasB2) out.append(B64_URL_ALPHABET[b2 and 0x3F])
            }
            return out.toString()
        }

        private fun decodeBase64Flexible(encoded: String): ByteArray {
            val normalized = encoded
                .replace('-', '+')
                .replace('_', '/')
                .replace("=", "")
                .trim()
            val out = java.io.ByteArrayOutputStream((normalized.length * 3) / 4)
            var buffer = 0
            var bits = 0
            for (ch in normalized) {
                val val6 = when (ch) {
                    in 'A'..'Z' -> ch - 'A'
                    in 'a'..'z' -> ch - 'a' + 26
                    in '0'..'9' -> ch - '0' + 52
                    '+' -> 62
                    '/' -> 63
                    else -> throw IllegalArgumentException("Ongeldig Base64-teken in themacode")
                }
                buffer = (buffer shl 6) or val6
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            return out.toByteArray()
        }

        val CURATED_SWATCHES = listOf(
            "#3B82F6", // Electric Blue
            "#D71921", // Nothing Red
            "#1BA1E2", // Metro Cobalt
            "#10B981", // Emerald
            "#F59E0B", // Amber Gold
            "#EC4899", // Neon Rose
            "#8B5CF6", // Vivid Violet
            "#06B6D4"  // Cyan
        )

        fun normalizeHex(raw: String, fallback: String = "#3B82F6"): String {
            val cleaned = raw.trim().removePrefix("#").uppercase(java.util.Locale.US)
            return if (Regex("^[0-9A-F]{6}$").matches(cleaned)) "#$cleaned" else fallback
        }

        fun parseHexArgb(raw: String): Long? {
            val cleaned = raw.trim().removePrefix("#").uppercase(java.util.Locale.US)
            if (!Regex("^[0-9A-F]{6}$").matches(cleaned)) return null
            val rgb = cleaned.toLongOrNull(16) ?: return null
            return 0xFF000000L or rgb
        }

        fun fromBasePreset(preset: AppThemeStyle): AxolaCustomTheme = when (preset) {
            AppThemeStyle.NOTHING_OS -> AxolaCustomTheme(
                basePreset = AppThemeStyle.NOTHING_OS,
                cornerRadiusDp = 24,
                backgroundStyle = AxolaBackgroundStyle.OLED_BLACK,
                primaryAccentHex = "#FFFFFF",
                secondaryAccentHex = "#D71921",
                borderStyle = AxolaBorderStyle.SUBTLE_1DP,
                typographyStyle = AxolaTypographyStyle.NOTHING_DOT_MATRIX
            )
            AppThemeStyle.ONE_UI -> AxolaCustomTheme(
                basePreset = AppThemeStyle.ONE_UI,
                cornerRadiusDp = 26,
                backgroundStyle = AxolaBackgroundStyle.OLED_BLACK,
                primaryAccentHex = "#3E91FF",
                secondaryAccentHex = "#66D1FF",
                borderStyle = AxolaBorderStyle.NONE,
                typographyStyle = AxolaTypographyStyle.MODERN_SANS
            )
            AppThemeStyle.WINDOWS_PHONE -> AxolaCustomTheme(
                basePreset = AppThemeStyle.WINDOWS_PHONE,
                cornerRadiusDp = 0,
                backgroundStyle = AxolaBackgroundStyle.OLED_BLACK,
                primaryAccentHex = "#1BA1E2",
                secondaryAccentHex = "#008A00",
                borderStyle = AxolaBorderStyle.NONE,
                typographyStyle = AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE
            )
            AppThemeStyle.IOS_LIQUID_GLASS -> AxolaCustomTheme(
                basePreset = AppThemeStyle.IOS_LIQUID_GLASS,
                cornerRadiusDp = 22,
                backgroundStyle = AxolaBackgroundStyle.DEEP_SLATE,
                primaryAccentHex = "#0A84FF",
                secondaryAccentHex = "#FF9F0A",
                borderStyle = AxolaBorderStyle.SUBTLE_1DP,
                typographyStyle = AxolaTypographyStyle.MODERN_SANS
            )
            AppThemeStyle.STANDARD, AppThemeStyle.AXOLA_CUSTOM -> AxolaCustomTheme(
                basePreset = AppThemeStyle.STANDARD,
                cornerRadiusDp = 18,
                backgroundStyle = AxolaBackgroundStyle.LIGHT_MODE,
                primaryAccentHex = "#0B2545",
                secondaryAccentHex = "#E85D04",
                borderStyle = AxolaBorderStyle.NONE,
                typographyStyle = AxolaTypographyStyle.MODERN_SANS
            )
        }

        fun fromJsonObject(obj: org.json.JSONObject): AxolaCustomTheme {
            val base = runCatching {
                AppThemeStyle.valueOf(obj.optString("base", AppThemeStyle.NOTHING_OS.name))
            }.getOrDefault(AppThemeStyle.NOTHING_OS).let {
                if (it == AppThemeStyle.AXOLA_CUSTOM) AppThemeStyle.NOTHING_OS else it
            }
            val bg = runCatching {
                AxolaBackgroundStyle.valueOf(obj.optString("bg", AxolaBackgroundStyle.OLED_BLACK.name))
            }.getOrDefault(AxolaBackgroundStyle.OLED_BLACK)
            val border = runCatching {
                AxolaBorderStyle.valueOf(obj.optString("border", AxolaBorderStyle.SUBTLE_1DP.name))
            }.getOrDefault(AxolaBorderStyle.SUBTLE_1DP)
            val font = runCatching {
                AxolaTypographyStyle.valueOf(obj.optString("font", AxolaTypographyStyle.MODERN_SANS.name))
            }.getOrDefault(AxolaTypographyStyle.MODERN_SANS)
            val radius = obj.optInt("radius", 20).coerceIn(0, 28)
            val primary = normalizeHex(obj.optString("primary", "#3B82F6"), "#3B82F6")
            val secondary = normalizeHex(obj.optString("secondary", "#EC4899"), "#EC4899")
            return AxolaCustomTheme(
                basePreset = base,
                cornerRadiusDp = radius,
                backgroundStyle = bg,
                primaryAccentHex = primary,
                secondaryAccentHex = secondary,
                borderStyle = border,
                typographyStyle = font
            )
        }

        fun decodeFromThemeCode(rawCode: String): Result<AxolaCustomTheme> = runCatching {
            val trimmed = rawCode.trim()
            require(trimmed.startsWith(THEME_CODE_PREFIX, ignoreCase = true)) {
                "Ongeldige themacode: moet beginnen met $THEME_CODE_PREFIX"
            }
            val payload = trimmed.substring(THEME_CODE_PREFIX.length).trim()
            require(payload.isNotEmpty()) { "Lege themacode." }
            val jsonBytes = decodeBase64Flexible(payload)
            val obj = org.json.JSONObject(String(jsonBytes, Charsets.UTF_8))
            require(obj.has("primary") || obj.has("bg") || obj.has("radius")) {
                "Themacode bevat geen geldige AXOLA-stijlvelden."
            }
            fromJsonObject(obj)
        }
    }
}

data class CalendarBridgeConfig(
    val enabled: Boolean = false,
    val hideCanceledLessons: Boolean = true,
    val lastSyncedAtMs: Long = 0L,
    val lastSyncedEventCount: Int = 0
)

enum class LessonDndMode(val labelDutch: String, val descriptionDutch: String) {
    VIBRATE("Alleen trillen", "Zet beltoon op trillen tijdens actieve lessen"),
    FULL_DND("Volledig stil (Niet Storen)", "Blokkeer meldingen en geluid tijdens lessen")
}

data class LessonDndConfig(
    val enabled: Boolean = false,
    val mode: LessonDndMode = LessonDndMode.VIBRATE,
    val startBufferMinutes: Int = 1,
    val endBufferMinutes: Int = 0,
    val forceDndDuringExams: Boolean = true
)
