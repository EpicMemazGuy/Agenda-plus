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
    PRESENTATION("Presentatie")
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
    ONE_UI("One UI 8.5", "Samsung 26dp squircles, wallpaper-kleuren & AMOLED")
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
    val delayMinutes: Int = 0
)

data class BusVehiclePoint(
    val id: String,
    val lineNumber: String,
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val stopName: String,
    val expectedTime: String
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
    val isTestOrExam: Boolean
        get() = infoType in 2..5 ||
            isTestKeywordMatch(title) ||
            isTestKeywordMatch(homework)
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
)

data class GradeItem(
    val id: String,
    val subjectCode: String,
    val subjectName: String,
    val score: Double,
    val weight: Double,
    val description: String,
    val date: String,
    val period: Int
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
    val isApproved: Boolean
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
    val folderName: String = "Postvak In"
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
    val longitude: Double
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
    val provider: SchoolProvider = SchoolProvider.MAGISTER
)

fun String?.cleanSchoolText(): String? {
    val trimmed = this?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    if (trimmed.equals("null", ignoreCase = true)) return null
    if (trimmed.equals("undefined", ignoreCase = true)) return null
    return trimmed
}

private val TEST_KEYWORD_REGEX = Regex(
    """\b(toets|proefwerk|pw|so|mo|tentamen|repetitie|examen|luistertoets|spreekbeurt|schriftelijke overhoring|mondelinge overhoring)\b""",
    RegexOption.IGNORE_CASE
)

fun isTestKeywordMatch(text: String?): Boolean {
    val clean = text.cleanSchoolText() ?: return false
    return TEST_KEYWORD_REGEX.containsMatchIn(clean)
}
