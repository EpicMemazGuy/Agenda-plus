package com.example.network

import android.text.Html
import com.example.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class MagisterExtendedApi(
    private val client: OkHttpClient,
    private val requestBuilder: (url: String, token: String) -> Request,
    private val dateParser: (String) -> Date?
) {

    private fun JSONObject.optClean(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key, "").cleanSchoolText()
            if (value != null) return value
        }
        return null
    }

    private fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        }.getOrDefault(raw.trim())
    }

    fun fetchMessageFolders(tenant: SchoolTenant, token: String): List<MessageFolderInfo> {
        val urls = listOf(
            "${tenant.apiBaseUrl}/berichten/mappen",
            "${tenant.apiBaseUrl}/berichten/mappen/alle"
        )
        for (url in urls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http("Magister-Berichten", "GET mappen -> ${resp.code}")
                if (!resp.isSuccessful) continue

                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: json.optJSONArray("Items") ?: JSONArray()
                val folders = buildList {
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        val id = o.optLong("id", o.optLong("Id", (i + 1).toLong()))
                        val name = o.optClean("naam", "Naam", "omschrijving", "Omschrijving") ?: "Map $id"
                        val unread = o.optInt("aantalOngelezen", o.optInt("AantalOngelezen", 0))
                        val type = when {
                            name.contains("postvak", ignoreCase = true) || id == 1L -> MessageFolder.INBOX
                            name.contains("verzend", ignoreCase = true) || name.contains("verstuur", ignoreCase = true) || id == 2L -> MessageFolder.SENT
                            name.contains("concept", ignoreCase = true) -> MessageFolder.DRAFT
                            name.contains("prullenbak", ignoreCase = true) || name.contains("verwijder", ignoreCase = true) -> MessageFolder.TRASH
                            else -> MessageFolder.OTHER
                        }
                        add(MessageFolderInfo(id = id, name = name, unreadCount = unread, folderType = type))
                    }
                }
                if (folders.isNotEmpty()) return folders
            } catch (_: Exception) {
            }
        }
        return listOf(
            MessageFolderInfo(1L, "Postvak In", 0, MessageFolder.INBOX),
            MessageFolderInfo(2L, "Verzonden", 0, MessageFolder.SENT)
        )
    }

    fun fetchMessages(
        tenant: SchoolTenant,
        token: String,
        folders: List<MessageFolderInfo>
    ): List<MagisterMessage> {
        val allMessages = mutableListOf<MagisterMessage>()
        val targetFolders = folders.filter {
            it.folderType == MessageFolder.INBOX || it.folderType == MessageFolder.SENT || it.folderType == MessageFolder.OTHER
        }.take(4).ifEmpty {
            listOf(
                MessageFolderInfo(1L, "Postvak In", 0, MessageFolder.INBOX),
                MessageFolderInfo(2L, "Verzonden", 0, MessageFolder.SENT)
            )
        }

        // 1. Primary Magister v6 folders endpoint
        for (folder in targetFolders) {
            val urls = listOf(
                "${tenant.apiBaseUrl}/berichten/mappen/${folder.id}/berichten?top=35&skip=0",
                "${tenant.apiBaseUrl}/berichten/mappen/${folder.id}/berichten"
            )
            for (url in urls) {
                try {
                    val resp = client.newCall(requestBuilder(url, token)).execute()
                    val body = resp.body?.string() ?: "{}"
                    com.example.util.RawConsoleLogger.http("Magister-Berichten", "GET map ${folder.id} -> ${resp.code}")
                    if (!resp.isSuccessful) continue
                    val parsed = parseMessagesArray(body, folder)
                    allMessages.addAll(parsed)
                    break
                } catch (_: Exception) {
                }
            }
        }

        if (allMessages.isNotEmpty()) {
            return allMessages.distinctBy { it.id }
        }

        // 2. Legacy / alternative tenant endpoints
        val fallbackEndpoints = listOf(
            "${tenant.apiBaseUrl}/berichten/ontvangen?aantal=40" to MessageFolderInfo(1L, "Postvak In", 0, MessageFolder.INBOX),
            "${tenant.apiBaseUrl}/berichten/verzonden?aantal=25" to MessageFolderInfo(2L, "Verzonden", 0, MessageFolder.SENT),
            "${tenant.apiBaseUrl}/berichten/berichten?top=40" to MessageFolderInfo(1L, "Postvak In", 0, MessageFolder.INBOX)
        )
        for ((url, folderInfo) in fallbackEndpoints) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                if (resp.isSuccessful) {
                    allMessages.addAll(parseMessagesArray(body, folderInfo))
                }
            } catch (_: Exception) {
            }
        }

        return allMessages.distinctBy { it.id }
    }

    private fun extractPersonDisplayName(obj: JSONObject?): String? {
        if (obj == null) return null
        obj.optClean(
            "naam", "Naam",
            "weergavenaam", "Weergavenaam",
            "volledigeNaam", "VolledigeNaam",
            "naamVoorWeergave", "NaamVoorWeergave",
            "roepnaam", "Roepnaam"
        )?.let { return it }
        val first = obj.optClean("voornaam", "Voornaam") ?: ""
        val middle = obj.optClean("tussenvoegsel", "Tussenvoegsel") ?: ""
        val last = obj.optClean("achternaam", "Achternaam") ?: ""
        val combined = listOf(first, middle, last).filter { it.isNotBlank() }.joinToString(" ").trim()
        return combined.ifBlank { null }
    }

    private fun extractSenderName(item: JSONObject): String? {
        val senderObj = item.optJSONObject("afzender")
            ?: item.optJSONObject("Afzender")
            ?: item.optJSONObject("verzender")
            ?: item.optJSONObject("Verzender")
        extractPersonDisplayName(senderObj)?.let { return it }

        for (key in listOf("afzender", "Afzender", "afzenderNaam", "AfzenderNaam", "verzender", "Verzender", "sender", "senderName")) {
            val raw = item.optClean(key) ?: continue
            if (!raw.startsWith("{")) return raw
        }
        return null
    }

    private fun extractRecipients(item: JSONObject): String {
        val arr = item.optJSONArray("ontvangers")
            ?: item.optJSONArray("Ontvangers")
            ?: item.optJSONArray("kopieOntvangers")
            ?: item.optJSONArray("KopieOntvangers")
            ?: item.optJSONArray("recipients")
        if (arr != null && arr.length() > 0) {
            val names = buildList {
                for (j in 0 until arr.length()) {
                    val obj = arr.optJSONObject(j)
                    val name = if (obj != null) {
                        extractPersonDisplayName(obj) ?: obj.optClean("email", "Email")
                    } else {
                        arr.optString(j, "").cleanSchoolText()
                    }
                    if (!name.isNullOrBlank()) add(name)
                }
            }.distinct()
            if (names.isNotEmpty()) return names.joinToString(", ")
        }

        for (key in listOf("ontvangers", "Ontvangers", "ontvangersNamen", "OntvangersNamen", "recipients")) {
            val raw = item.optClean(key) ?: continue
            if (!raw.startsWith("[") && !raw.startsWith("{")) return raw
        }
        return ""
    }

    internal fun parseMessagesArray(bodyStr: String, folder: MessageFolderInfo): List<MagisterMessage> {
        val json = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return emptyList()
        val items = json.optJSONArray("items")
            ?: json.optJSONArray("Items")
            ?: json.optJSONArray("data")
            ?: JSONArray()
        val dutchDateFormat = SimpleDateFormat("d MMM HH:mm", Locale("nl", "NL"))

        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optClean("id", "Id") ?: item.optLong("id", item.optLong("Id", i.toLong())).toString()
                val subject = item.optClean("onderwerp", "Onderwerp", "subject") ?: "Schoolbericht"
                val senderObj = item.optJSONObject("afzender") ?: item.optJSONObject("Afzender")
                val senderName = extractSenderName(item) ?: "Magister"
                val senderEmail = senderObj?.optClean("email", "Email", "emailadres", "Emailadres")
                val recipients = extractRecipients(item)
                val rawDate = item.optClean("verzondenOp", "VerzondenOp", "datum", "Datum") ?: ""
                val parsedDate = dateParser(rawDate)
                val formattedDate = when {
                    parsedDate != null -> dutchDateFormat.format(parsedDate)
                    rawDate.length >= 10 -> rawDate.take(10)
                    else -> "Recent"
                }

                val rawBody = item.optClean("inhoud", "Inhoud", "omschrijving", "Omschrijving", "korteInhoud", "body")
                val cleanBody = stripHtml(rawBody).cleanSchoolText() ?: ""
                val isRead = item.optBoolean("isGelezen", item.optBoolean("IsGelezen", false))
                val hasAttachments = item.optBoolean("heeftBijlagen", item.optBoolean("HeeftBijlagen", false))

                add(
                    MagisterMessage(
                        id = id,
                        subject = subject,
                        senderName = senderName,
                        senderEmail = senderEmail,
                        dateSent = formattedDate,
                        body = cleanBody,
                        isRead = isRead,
                        hasAttachments = hasAttachments,
                        folder = folder.folderType,
                        folderId = folder.id,
                        folderName = folder.name,
                        recipients = recipients,
                        rawDateIso = rawDate
                    )
                )
            }
        }
    }

    internal data class MessageDetailData(
        val body: String? = null,
        val senderName: String? = null,
        val senderEmail: String? = null,
        val recipients: String? = null,
        val rawDateIso: String? = null
    )

    internal fun fetchMessageDetailFull(tenant: SchoolTenant, token: String, messageId: String): MessageDetailData? {
        val urls = listOf(
            "${tenant.apiBaseUrl}/berichten/berichten/$messageId",
            "${tenant.apiBaseUrl}/berichten/$messageId"
        )
        for (url in urls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) continue
                val json = JSONObject(body)
                val raw = json.optClean("inhoud", "Inhoud", "omschrijving", "Omschrijving", "body")
                val cleanBody = stripHtml(raw).cleanSchoolText()
                val senderObj = json.optJSONObject("afzender") ?: json.optJSONObject("Afzender")
                val senderName = extractSenderName(json)
                val senderEmail = senderObj?.optClean("email", "Email", "emailadres", "Emailadres")
                val recipients = extractRecipients(json).ifBlank { null }
                val rawDateIso = json.optClean("verzondenOp", "VerzondenOp", "datum", "Datum")
                if (cleanBody != null || senderName != null || recipients != null) {
                    return MessageDetailData(
                        body = cleanBody,
                        senderName = senderName,
                        senderEmail = senderEmail,
                        recipients = recipients,
                        rawDateIso = rawDateIso
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun fetchMessageDetail(tenant: SchoolTenant, token: String, messageId: String): String? =
        fetchMessageDetailFull(tenant, token, messageId)?.body

    private val genericSenderNames = setOf("", "Magister", "Afzender", "Schooladministratie", "Onbekende afzender")

    fun enrichMessagesWithDetails(
        tenant: SchoolTenant,
        token: String,
        messages: List<MagisterMessage>,
        limit: Int = 25
    ): List<MagisterMessage> {
        if (messages.isEmpty()) return messages
        val dutchDateFormat = SimpleDateFormat("d MMM HH:mm", Locale("nl", "NL"))
        return messages.mapIndexed { idx, msg ->
            val needsBody = msg.body.isBlank()
            val needsSender = msg.senderName.trim() in genericSenderNames
            val needsRecipients = msg.recipients.isBlank()
            val needsIsoDate = msg.rawDateIso.isBlank()
            if (idx >= limit || (!needsBody && !needsSender && !needsRecipients && !needsIsoDate)) {
                msg
            } else {
                val detail = fetchMessageDetailFull(tenant, token, msg.id)
                if (detail == null) {
                    msg
                } else {
                    val updatedDateIso = msg.rawDateIso.ifBlank { detail.rawDateIso.orEmpty() }
                    val updatedDateSent = if (msg.dateSent == "Recent" && updatedDateIso.isNotBlank()) {
                        dateParser(updatedDateIso)?.let { dutchDateFormat.format(it) } ?: updatedDateIso.take(10)
                    } else {
                        msg.dateSent
                    }
                    msg.copy(
                        body = msg.body.ifBlank { detail.body.orEmpty() },
                        senderName = if (needsSender && !detail.senderName.isNullOrBlank()) detail.senderName else msg.senderName,
                        senderEmail = msg.senderEmail ?: detail.senderEmail,
                        recipients = msg.recipients.ifBlank { detail.recipients.orEmpty() },
                        rawDateIso = updatedDateIso,
                        dateSent = updatedDateSent
                    )
                }
            }
        }
    }

    fun markMessageAsRead(tenant: SchoolTenant, token: String, messageId: String): Boolean {
        val payload = JSONObject().apply { put("isGelezen", true) }.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "${tenant.apiBaseUrl}/berichten/berichten/$messageId",
            "${tenant.apiBaseUrl}/berichten/$messageId"
        )
        for (url in urls) {
            try {
                val baseReq = requestBuilder(url, token)
                val req = baseReq.newBuilder()
                    .patch(payload.toRequestBody(mediaType))
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun fetchStudyGuides(tenant: SchoolTenant, token: String, personId: Long): List<StudyGuide> {
        val urls = listOf(
            "${tenant.apiBaseUrl}/personen/$personId/studiewijzers",
            "${tenant.apiBaseUrl}/elo/studiewijzers"
        )
        for (url in urls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http("Magister-ELO", "GET studiewijzers -> ${resp.code}")
                if (!resp.isSuccessful) continue

                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: json.optJSONArray("Items") ?: JSONArray()
                val guides = mutableListOf<StudyGuide>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optClean("id", "Id") ?: item.optLong("id", item.optLong("Id", i.toLong())).toString()
                    val title = item.optClean("titel", "Titel", "omschrijving", "Omschrijving") ?: "Studiewijzer"
                    val subjCodes = item.optJSONArray("vakCodes") ?: item.optJSONArray("VakCodes")
                    val subjCode = subjCodes?.optString(0)?.cleanSchoolText()
                        ?: item.optClean("vakCode", "VakCode")
                        ?: ""
                    val subjObj = item.optJSONObject("vak") ?: item.optJSONObject("Vak")
                    val subjName = subjObj?.optClean("omschrijving", "Omschrijving", "naam", "Naam")
                        ?: item.optClean("vakNaam", "VakNaam")
                        ?: subjCode.ifBlank { title }
                    val desc = stripHtml(item.optClean("omschrijving", "Omschrijving", "opmerking")).cleanSchoolText() ?: ""

                    val parts = if (guides.size < 12) {
                        fetchStudyGuideParts(tenant, token, id)
                    } else emptyList()

                    guides.add(
                        StudyGuide(
                            id = id,
                            title = title,
                            subjectCode = subjCode,
                            subjectName = subjName,
                            description = desc,
                            parts = parts
                        )
                    )
                }
                if (guides.isNotEmpty()) return guides
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun fetchStudyGuideParts(tenant: SchoolTenant, token: String, guideId: String): List<StudyGuidePart> {
        val urls = listOf(
            "${tenant.apiBaseUrl}/studiewijzers/$guideId/onderdelen",
            "${tenant.apiBaseUrl}/elo/studiewijzers/$guideId/onderdelen"
        )
        for (url in urls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) continue

                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: json.optJSONArray("Items") ?: JSONArray()
                val parts = mutableListOf<StudyGuidePart>()
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val id = o.optClean("id", "Id") ?: o.optLong("id", i.toLong()).toString()
                    val title = o.optClean("titel", "Titel") ?: "Onderdeel ${i + 1}"
                    val desc = stripHtml(o.optClean("omschrijving", "Omschrijving")).cleanSchoolText() ?: ""
                    val sortOrder = o.optInt("volgnummer", o.optInt("Volgnummer", i))

                    val resArray = o.optJSONArray("bronnen") ?: o.optJSONArray("Bronnen") ?: JSONArray()
                    val resources = buildList {
                        for (r in 0 until resArray.length()) {
                            val ro = resArray.optJSONObject(r) ?: continue
                            val rName = ro.optClean("naam", "Naam", "titel", "Titel") ?: "Bron ${r + 1}"
                            val rUrl = ro.optClean("uri", "Uri", "url", "Url")
                                ?: ro.optJSONArray("links")?.optJSONObject(0)?.optClean("href")
                                ?: continue
                            val normalizedUrl = if (rUrl.startsWith("http://") || rUrl.startsWith("https://")) {
                                rUrl
                            } else {
                                "https://${tenant.subdomain}.magister.net/${rUrl.trimStart('/')}"
                            }
                            val cType = ro.optClean("contentType", "ContentType") ?: "Link"
                            add(
                                StudyGuideResource(
                                    id = ro.optClean("id", "Id") ?: "res_${i}_$r",
                                    name = rName,
                                    url = normalizedUrl,
                                    contentType = cType
                                )
                            )
                        }
                    }

                    parts.add(
                        StudyGuidePart(
                            id = id,
                            title = title,
                            description = desc,
                            sortOrder = sortOrder,
                            resources = resources
                        )
                    )
                }
                if (parts.isNotEmpty()) return parts.sortedBy { it.sortOrder }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    fun fetchKwtSlots(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appointments: List<Appointment>
    ): List<KwtSlot> {
        val slots = mutableListOf<KwtSlot>()

        // 1. Check appointment-linked KWT choices for appointments flagged as KWT
        val kwtAppointments = appointments.filter { it.isKwt }
        for (appt in kwtAppointments.take(10)) {
            val fetched = fetchKwtSlotForAppointment(tenant, token, personId, appt)
            if (fetched != null) {
                slots.add(fetched)
            }
        }

        if (slots.isNotEmpty()) return slots

        // 2. Fallback: global person-level KWT endpoint
        val globalUrls = listOf(
            "${tenant.apiBaseUrl}/personen/$personId/keuzewerktijd",
            "${tenant.apiBaseUrl}/personen/$personId/keuzelessen"
        )
        for (url in globalUrls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) continue
                val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
                val items = json.optJSONArray("items") ?: json.optJSONArray("Items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val optsArr = o.optJSONArray("keuzes")
                        ?: o.optJSONArray("Keuzes")
                        ?: o.optJSONArray("opties")
                        ?: o.optJSONArray("keuzeMogelijkheden")
                        ?: continue
                    val parsedOpts = parseKwtOptionsFromArray(optsArr)
                    if (parsedOpts.isNotEmpty()) {
                        slots.add(
                            KwtSlot(
                                appointmentId = o.optLong("id", o.optLong("afspraakId", i.toLong())),
                                title = o.optClean("omschrijving", "titel") ?: "Keuzewerktijd",
                                dateIso = o.optClean("datum", "start")?.take(10) ?: "",
                                startTime = o.optClean("start")?.drop(11)?.take(5) ?: "",
                                endTime = o.optClean("einde")?.drop(11)?.take(5) ?: "",
                                periodIndex = o.optInt("lesuur", 1),
                                options = parsedOpts
                            )
                        )
                    }
                }
                if (slots.isNotEmpty()) return slots
            } catch (_: Exception) {
            }
        }

        return emptyList()
    }

    fun fetchKwtSlotForAppointment(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appt: Appointment
    ): KwtSlot? {
        val candidateUrls = listOf(
            "${tenant.apiBaseUrl}/personen/$personId/afspraken/${appt.id}/keuzes",
            "${tenant.apiBaseUrl}/personen/$personId/afspraken/${appt.id}/keuzewerktijd",
            "${tenant.apiBaseUrl}/afspraken/${appt.id}/kwt",
            "${tenant.apiBaseUrl}/personen/$personId/keuzelessen/${appt.id}"
        )
        for (url in candidateUrls) {
            try {
                val resp = client.newCall(requestBuilder(url, token)).execute()
                val body = resp.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http("Magister-KWT", "GET kwt ${appt.id} -> ${resp.code}")
                if (!resp.isSuccessful) continue
                val parsedOpts = parseKwtOptions(body)
                if (parsedOpts.isNotEmpty()) {
                    return KwtSlot(
                        appointmentId = appt.id,
                        title = appt.title.ifBlank { "Keuzewerktijd" },
                        dateIso = appt.dateIso,
                        startTime = appt.startTime,
                        endTime = appt.endTime,
                        periodIndex = appt.periodIndex,
                        options = parsedOpts
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseKwtOptions(bodyStr: String): List<KwtOption> {
        val trimmed = bodyStr.trim()
        if (trimmed.startsWith("[")) {
            return runCatching { parseKwtOptionsFromArray(JSONArray(trimmed)) }.getOrDefault(emptyList())
        }
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val arr = json.optJSONArray("items")
            ?: json.optJSONArray("Items")
            ?: json.optJSONArray("keuzes")
            ?: json.optJSONArray("Keuzes")
            ?: json.optJSONArray("opties")
            ?: json.optJSONArray("Opties")
            ?: json.optJSONArray("keuzeMogelijkheden")
            ?: json.optJSONArray("KeuzeMogelijkheden")
            ?: return emptyList()
        return parseKwtOptionsFromArray(arr)
    }

    private fun parseKwtOptionsFromArray(arr: JSONArray): List<KwtOption> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", o.optLong("Id", i.toLong()))
            val title = o.optClean("omschrijving", "Omschrijving", "titel", "Titel") ?: "Keuzeles"
            val vak = o.optJSONObject("vak")?.optClean("naam", "omschrijving")
                ?: o.optClean("vakNaam", "VakNaam")
                ?: "KWT"
            val docent = o.optJSONArray("docenten")?.optJSONObject(0)?.optClean("naam", "Naam")
                ?: o.optClean("docent", "Docent")
                ?: ""
            val lokaal = o.optJSONArray("lokalen")?.optJSONObject(0)?.optClean("naam", "Naam")
                ?: o.optClean("lokaal", "Lokaal")
                ?: ""
            add(
                KwtOption(
                    id = id,
                    title = title,
                    subjectName = vak,
                    teacherName = docent,
                    classroom = lokaal,
                    enrolledCount = o.optInt("aantalDeelnemers", 0),
                    maxCount = o.optInt("maxAantalDeelnemers", o.optInt("maximum", 0)),
                    isEnrolled = o.optBoolean("isIngeschreven", o.optBoolean("ingeschreven", false))
                )
            )
        }
    }

    fun enrollKwtOption(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appointmentId: Long,
        optionId: Long
    ): Result<Unit> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val payload = JSONObject().apply {
            put("keuzeId", optionId)
            put("id", optionId)
        }.toString()

        val urls = listOf(
            "${tenant.apiBaseUrl}/personen/$personId/afspraken/$appointmentId/keuzes",
            "${tenant.apiBaseUrl}/personen/$personId/afspraken/$appointmentId/keuzewerktijd",
            "${tenant.apiBaseUrl}/personen/$personId/keuzewerktijd/$optionId",
            "${tenant.apiBaseUrl}/personen/$personId/keuzelessen/$appointmentId"
        )
        var lastError = "Inschrijven voor KWT mislukt."
        for (url in urls) {
            try {
                val baseReq = requestBuilder(url, token)
                val req = baseReq.newBuilder()
                    .post(payload.toRequestBody(mediaType))
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) return Result.success(Unit)
                lastError = "Magister gaf HTTP ${resp.code} bij inschrijven."
            } catch (e: Exception) {
                lastError = e.message ?: "Netwerkfout"
            }
        }
        return Result.failure(IllegalStateException(lastError))
    }
}
