package com.example.model

import org.json.JSONObject
import java.util.Locale

data class SubjectCustomization(
    val code: String,
    val nickname: String = "",
    val colorHex: Long? = null,
    val iconName: String? = null,
    val showOriginalCode: Boolean = true
) {
    fun hasAnyOverride(): Boolean {
        return nickname.isNotBlank() || colorHex != null || !iconName.isNullOrBlank() || !showOriginalCode
    }
}

data class SmartSubjectDefault(
    val code: String,
    val displayName: String,
    val defaultColorHex: Long,
    val defaultIconName: String
)

data class SubjectColorOption(
    val name: String,
    val colorHex: Long,
    val hexString: String
)

data class ResolvedSubjectInfo(
    val normalizedCode: String,
    val rawSubjectText: String,
    val displayName: String,
    val originalCodeLabel: String?,
    val accentColorHex: Long?,
    val iconName: String,
    val isCustomized: Boolean
)

data class SubjectCustomizationState(
    val customizations: Map<String, SubjectCustomization> = emptyMap(),
    val smartDefaultNamesEnabled: Boolean = true,
    val useSubjectColorsForMetroTiles: Boolean = false
)

object SubjectCustomizationCatalog {

    val curatedColors: List<SubjectColorOption> = listOf(
        SubjectColorOption("Kobalt", 0xFF1BA1E2L, "#1BA1E2"),
        SubjectColorOption("Oceaan", 0xFF0A84FFL, "#0A84FF"),
        SubjectColorOption("Indigo", 0xFF5E5CE6L, "#5E5CE6"),
        SubjectColorOption("Paars", 0xFFAF52DEL, "#AF52DE"),
        SubjectColorOption("Magenta", 0xFFE91E63L, "#E91E63"),
        SubjectColorOption("Karmijn", 0xFFE51400L, "#E51400"),
        SubjectColorOption("Koraal", 0xFFFF5A5FL, "#FF5A5F"),
        SubjectColorOption("Oranje", 0xFFFF6D00L, "#FF6D00"),
        SubjectColorOption("Amber", 0xFFF0A30AL, "#F0A30A"),
        SubjectColorOption("Goud", 0xFFD97706L, "#D97706"),
        SubjectColorOption("Limoen", 0xFF65A30DL, "#65A30D"),
        SubjectColorOption("Smaragd", 0xFF008A00L, "#008A00"),
        SubjectColorOption("Mint", 0xFF10B981L, "#10B981"),
        SubjectColorOption("Teal", 0xFF00ABA9L, "#00ABA9"),
        SubjectColorOption("Cyaan", 0xFF06B6D4L, "#06B6D4"),
        SubjectColorOption("Leisteen", 0xFF64748BL, "#64748B")
    )

    val smartDefaults: Map<String, SmartSubjectDefault> = listOf(
        SmartSubjectDefault("wi", "Wiskunde", 0xFF0A84FFL, "calculate"),
        SmartSubjectDefault("wisa", "Wiskunde A", 0xFF0A84FFL, "calculate"),
        SmartSubjectDefault("wisb", "Wiskunde B", 0xFF1BA1E2L, "functions"),
        SmartSubjectDefault("wisc", "Wiskunde C", 0xFF5E5CE6L, "pie_chart"),
        SmartSubjectDefault("wisd", "Wiskunde D", 0xFF06B6D4L, "functions"),
        SmartSubjectDefault("ne", "Nederlands", 0xFFFF6D00L, "menu_book"),
        SmartSubjectDefault("netl", "Nederlands", 0xFFFF6D00L, "menu_book"),
        SmartSubjectDefault("en", "Engels", 0xFFE51400L, "translate"),
        SmartSubjectDefault("entl", "Engels", 0xFFE51400L, "translate"),
        SmartSubjectDefault("du", "Duits", 0xFFF0A30AL, "language"),
        SmartSubjectDefault("dutl", "Duits", 0xFFF0A30AL, "language"),
        SmartSubjectDefault("fr", "Frans", 0xFF5E5CE6L, "chat"),
        SmartSubjectDefault("fatl", "Frans", 0xFF5E5CE6L, "chat"),
        SmartSubjectDefault("sp", "Spaans", 0xFFE91E63L, "public"),
        SmartSubjectDefault("ak", "Aardrijkskunde", 0xFF00ABA9L, "public"),
        SmartSubjectDefault("aard", "Aardrijkskunde", 0xFF00ABA9L, "public"),
        SmartSubjectDefault("gs", "Geschiedenis", 0xFFD97706L, "history_edu"),
        SmartSubjectDefault("ges", "Geschiedenis", 0xFFD97706L, "history_edu"),
        SmartSubjectDefault("bi", "Biologie", 0xFF10B981L, "biotech"),
        SmartSubjectDefault("biol", "Biologie", 0xFF10B981L, "biotech"),
        SmartSubjectDefault("na", "Natuurkunde", 0xFF1BA1E2L, "bolt"),
        SmartSubjectDefault("nat", "Natuurkunde", 0xFF1BA1E2L, "bolt"),
        SmartSubjectDefault("sk", "Scheikunde", 0xFFAF52DEL, "science"),
        SmartSubjectDefault("schk", "Scheikunde", 0xFFAF52DEL, "science"),
        SmartSubjectDefault("ec", "Economie", 0xFF008A00L, "trending_up"),
        SmartSubjectDefault("econ", "Economie", 0xFF008A00L, "trending_up"),
        SmartSubjectDefault("beco", "Bedrijfseconomie", 0xFF65A30DL, "account_balance"),
        SmartSubjectDefault("lo", "Lichamelijke Opvoeding", 0xFF10B981L, "directions_run"),
        SmartSubjectDefault("los", "Lichamelijke Opvoeding", 0xFF10B981L, "directions_run"),
        SmartSubjectDefault("bv", "Beeldende Vorming", 0xFFE91E63L, "palette"),
        SmartSubjectDefault("te", "Beeldende Vorming", 0xFFE91E63L, "brush"),
        SmartSubjectDefault("ckv", "CKV", 0xFFAF52DEL, "theater_comedy"),
        SmartSubjectDefault("mu", "Muziek", 0xFFFF5A5FL, "music_note"),
        SmartSubjectDefault("in", "Informatica", 0xFF06B6D4L, "code"),
        SmartSubjectDefault("inf", "Informatica", 0xFF06B6D4L, "code"),
        SmartSubjectDefault("ml", "Mentorles", 0xFF64748BL, "groups"),
        SmartSubjectDefault("men", "Mentorles", 0xFF64748BL, "groups"),
        SmartSubjectDefault("ma", "Maatschappijleer", 0xFFD97706L, "gavel"),
        SmartSubjectDefault("maw", "Maatschappijwetenschappen", 0xFFD97706L, "balance"),
        SmartSubjectDefault("nlt", "NLT", 0xFF00ABA9L, "hub"),
        SmartSubjectDefault("la", "Latijn", 0xFF64748BL, "account_balance"),
        SmartSubjectDefault("gr", "Grieks", 0xFF5E5CE6L, "temple_hindu"),
        SmartSubjectDefault("kwt", "Keuzewerktijd", 0xFF1BA1E2L, "tune"),
        SmartSubjectDefault("fi", "Filosofie", 0xFFAF52DEL, "psychology")
    ).associateBy { it.code }

    private val fullNameToCode: Map<String, String> = mapOf(
        "wiskunde" to "wi",
        "wiskunde a" to "wisa",
        "wiskunde b" to "wisb",
        "wiskunde c" to "wisc",
        "wiskunde d" to "wisd",
        "nederlands" to "ne",
        "engels" to "en",
        "duits" to "du",
        "frans" to "fr",
        "spaans" to "sp",
        "aardrijkskunde" to "ak",
        "geschiedenis" to "gs",
        "biologie" to "bi",
        "natuurkunde" to "na",
        "scheikunde" to "sk",
        "economie" to "ec",
        "bedrijfseconomie" to "beco",
        "lichamelijke opvoeding" to "lo",
        "gym" to "lo",
        "sport" to "los",
        "beeldende vorming" to "bv",
        "tekenen" to "te",
        "muziek" to "mu",
        "informatica" to "in",
        "mentorles" to "ml",
        "mentoruur" to "ml",
        "maatschappijleer" to "ma",
        "maatschappijwetenschappen" to "maw",
        "nlt" to "nlt",
        "latijn" to "la",
        "grieks" to "gr",
        "filosofie" to "fi",
        "ckv" to "ckv",
        "keuzewerktijd" to "kwt"
    )

    private val NON_SUBJECT_WORDS = setOf("", "les", "algemeen", "rooster", "afspraak", "overig")

    fun extractNormalizedSubjectCode(
        rawSubject: String?,
        rawTitle: String? = null
    ): String {
        val candidate = rawSubject.cleanSchoolText() ?: rawTitle.cleanSchoolText() ?: return "les"
        val lowerCandidate = candidate.lowercase(Locale.ROOT)
        fullNameToCode[lowerCandidate]?.let { return it }

        // Parse Magister pattern like "Wi - wfre - emkg2b" or "Los - seng,lsom"
        val sourceForPrefix = rawTitle.cleanSchoolText() ?: candidate
        val beforeDash = sourceForPrefix
            .split(" - ", " – ", ":", "/", "(")
            .firstOrNull()
            ?.trim()
            .orEmpty()

        val firstTokenRaw = beforeDash
            .split(',', ' ')
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")

        val subjClean = candidate
            .split(" - ", " – ", ":", "/", "(")
            .firstOrNull()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: ""

        fullNameToCode[subjClean]?.let { return it }

        // If rawSubject itself looks like a short Magister code (<= 6 chars), prefer it cleanly
        val rawSubjToken = subjClean.replace(Regex("[^a-z0-9]"), "")
        if (rawSubjToken.isNotBlank() && rawSubjToken.length <= 6 && rawSubjToken !in NON_SUBJECT_WORDS) {
            return rawSubjToken
        }
        if (firstTokenRaw.isNotBlank() && firstTokenRaw !in NON_SUBJECT_WORDS) {
            return firstTokenRaw
        }
        return rawSubjToken.ifBlank { "les" }
    }

    fun resolveSubject(
        rawSubject: String?,
        rawTitle: String? = null,
        explicitCode: String? = null,
        state: SubjectCustomizationState = SubjectCustomizationState()
    ): ResolvedSubjectInfo {
        val cleanExplicit = explicitCode.cleanSchoolText()
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("[^a-z0-9]"), "")
            ?.takeIf { it.isNotBlank() && it !in NON_SUBJECT_WORDS }

        val extracted = extractNormalizedSubjectCode(rawSubject = rawSubject, rawTitle = rawTitle)
        val normalizedCode = cleanExplicit ?: extracted

        val directCustom = state.customizations[normalizedCode]
            ?: if (normalizedCode == "los") state.customizations["lo"] else null
            ?: if (normalizedCode == "lo") state.customizations["los"] else null

        val smartDefault = smartDefaults[normalizedCode]
            ?: if (normalizedCode == "los") smartDefaults["lo"] else null

        val rawDisplayFallback = rawSubject.cleanSchoolText()?.capitalizeSubjectName()
            ?: rawTitle.cleanSchoolText()
            ?: normalizedCode.uppercase(Locale.ROOT)

        val displayName = when {
            !directCustom?.nickname.isNullOrBlank() -> directCustom!!.nickname.trim()
            state.smartDefaultNamesEnabled && smartDefault != null -> smartDefault.displayName
            else -> rawDisplayFallback
        }

        val showOrig = directCustom?.showOriginalCode ?: true
        val originalLabel = if (
            showOrig &&
            normalizedCode.isNotBlank() &&
            normalizedCode != "les" &&
            !displayName.equals(normalizedCode, ignoreCase = true) &&
            !rawDisplayFallback.equals(displayName, ignoreCase = true)
        ) {
            normalizedCode.uppercase(Locale.ROOT)
        } else if (
            showOrig &&
            normalizedCode.isNotBlank() &&
            normalizedCode != "les" &&
            !displayName.equals(normalizedCode, ignoreCase = true) &&
            state.smartDefaultNamesEnabled &&
            smartDefault != null
        ) {
            normalizedCode.uppercase(Locale.ROOT)
        } else {
            null
        }

        val resolvedColorHex: Long? = when {
            directCustom != null && directCustom.colorHex != null -> directCustom.colorHex
            directCustom != null && directCustom.colorHex == null && directCustom.hasAnyOverride() -> {
                // User explicitly chose "Volg thema" or edited without custom color -> keep null if overridden, else fallback if untouched
                null
            }
            state.smartDefaultNamesEnabled && smartDefault != null -> smartDefault.defaultColorHex
            else -> null
        }

        val resolvedIconName = directCustom?.iconName?.takeIf { it.isNotBlank() }
            ?: smartDefault?.defaultIconName
            ?: "school"

        return ResolvedSubjectInfo(
            normalizedCode = normalizedCode,
            rawSubjectText = rawDisplayFallback,
            displayName = displayName,
            originalCodeLabel = originalLabel,
            accentColorHex = resolvedColorHex,
            iconName = resolvedIconName,
            isCustomized = directCustom?.hasAnyOverride() == true
        )
    }

    fun parseHexColorOrNull(rawHex: String): Long? {
        val cleaned = rawHex.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return null
        val parsed = cleaned.toLongOrNull(16) ?: return null
        return if (cleaned.length == 6) (0xFF000000L or parsed) else parsed
    }

    fun formatHexColor(colorHex: Long): String {
        val rgb = (colorHex and 0xFFFFFFL).toInt()
        return String.format(Locale.US, "#%06X", rgb)
    }

    fun toJsonObject(map: Map<String, SubjectCustomization>): JSONObject {
        val root = JSONObject()
        map.keys.sorted().forEach { key ->
            val item = map[key] ?: return@forEach
            val code = key.trim().lowercase(Locale.ROOT)
            if (code.isBlank()) return@forEach
            root.put(code, JSONObject().apply {
                put("code", code)
                put("nickname", item.nickname)
                if (item.colorHex != null) {
                    put("colorHex", formatHexColor(item.colorHex))
                } else {
                    put("colorHex", JSONObject.NULL)
                }
                if (!item.iconName.isNullOrBlank()) {
                    put("iconName", item.iconName)
                } else {
                    put("iconName", JSONObject.NULL)
                }
                put("showOriginalCode", item.showOriginalCode)
            })
        }
        return root
    }

    fun fromJsonObject(obj: JSONObject?): Map<String, SubjectCustomization> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, SubjectCustomization>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val code = rawKey.trim().lowercase(Locale.ROOT)
            if (code.isBlank()) continue
            val entry = obj.optJSONObject(rawKey) ?: continue
            val colorHex: Long? = when {
                entry.isNull("colorHex") -> null
                entry.opt("colorHex") is Number -> (entry.optLong("colorHex") or 0xFF000000L)
                else -> parseHexColorOrNull(entry.optString("colorHex", ""))
            }
            val iconName = if (entry.isNull("iconName")) null else entry.optString("iconName").trim().takeIf { it.isNotBlank() }
            val customization = SubjectCustomization(
                code = code,
                nickname = entry.optString("nickname", "").trim(),
                colorHex = colorHex,
                iconName = iconName,
                showOriginalCode = entry.optBoolean("showOriginalCode", true)
            )
            result[code] = customization
        }
        return result
    }
}
