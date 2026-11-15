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

    private fun parseMessagesArray(bodyStr: String, folder: MessageFolderInfo): List<MagisterMessage> {
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
                val subject = item.optClean("onderwerp", "Onderwerp") ?: "Schoolbericht"
                val senderObj = item.optJSONObject("afzender") ?: item.optJSONObject("Afzender")
                val senderName = senderObj?.optClean("naam", "Naam")
                    ?: item.optClean("afzenderNaam", "AfzenderNaam")
                    ?: "Magister"
                val senderEmail = senderObj?.optClean("email", "Email")
                val rawDate = item.optClean("verzondenOp", "VerzondenOp") ?: ""
                val parsedDate = dateParser(rawDate)
                val formattedDate = when {
                    parsedDate != null -> dutchDateFormat.format(parsedDate)
                    rawDate.length >= 10 -> rawDate.take(10)
                    else -> "Recent"
                }

                val rawBody = item.optClean("inhoud", "Inhoud", "omschrijving", "Omschrijving", "korteInhoud")
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
                        folderName = folder.name
                    )
                )
            }
        }
    }

    fun fetchMessageDetail(tenant: SchoolTenant, token: String, messageId: String): String? {
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
                val raw = json.optClean("inhoud", "Inhoud", "omschrijving", "Omschrijving")
                val clean = stripHtml(raw).cleanSchoolText()
                if (clean != null) return clean
            } catch (_: Exception) {
            }
        }
        return null
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

    fun searchRecipients(tenant: SchoolTenant, token: String, query: String): List<MessageRecipient> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val urls = listOf(
            "${tenant.apiBaseUrl}/contactpersonen?q=$encoded&top=25",
            "${tenant.apiBaseUrl}/berichten/contactpersonen?q=$encoded&top=25",
            "${tenant.apiBaseUrl}/contactpersonen?q=$encoded",
            "${tenant.apiBaseUrl}/berichten/contactpersonen?q=$encoded"
        )
        for (url in urls) {
            try {
                val req = requestBuilder(url, token)
                val resp = client.newCall(req).execute()
                val code = resp.code
                val body = resp.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http("Magister-Berichten", "GET searchRecipients ($url) -> $code (${body.length}b)")
                if (!resp.isSuccessful) {
                    com.example.util.RawConsoleLogger.error("Magister-Berichten", "Search recipients HTTP $code: ${body.take(150)}")
                    continue
                }
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: json.optJSONArray("Items") ?: JSONArray()
                val list = buildList {
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        val id = o.optLong("id", o.optLong("Id", 0L))
                        if (id <= 0L) continue
                        val voornaam = o.optClean("voornaam", "Voornaam", "roepnaam", "Roepnaam") ?: ""
                        val tussenv = o.optClean("tussenvoegsel", "Tussenvoegsel") ?: ""
                        val achternaam = o.optClean("achternaam", "Achternaam") ?: ""
                        val klas = o.optClean("klas", "Klas") ?: ""
                        val fullName = listOf(voornaam, tussenv, achternaam)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { o.optClean("naam", "Naam") ?: "Gebruiker $id" }
                        val rawType = o.optClean("type", "Type") ?: ""
                        val role = if (klas.isNotBlank()) "Klas $klas" else if (rawType.isNotBlank()) rawType else "Magister"
                        add(MessageRecipient(id = id, name = fullName, roleLabel = role))
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                com.example.util.RawConsoleLogger.error("Magister-Berichten", "Search recipients exception: ${e.message}")
            }
        }
        return emptyList()
    }

    fun sendMessage(
        tenant: SchoolTenant,
        token: String,
        recipients: List<MessageRecipient>,
        subject: String,
        bodyText: String
    ): Result<Unit> {
        if (recipients.isEmpty()) return Result.failure(IllegalArgumentException("Kies minimaal één ontvanger."))
        val payload = JSONObject().apply {
            put("onderwerp", subject.trim().ifBlank { "(Geen onderwerp)" })
            put("inhoud", bodyText.trim())
            put("heeftPrioriteit", false)
            put("ontvangers", JSONArray().apply {
                recipients.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("type", "persoon")
                    })
                }
            })
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "${tenant.apiBaseUrl}/berichten/berichten",
            "${tenant.apiBaseUrl}/berichten"
        )
        var lastError = "Bericht kon niet worden verzonden."
        for (url in urls) {
            try {
                val baseReq = requestBuilder(url, token)
                val req = baseReq.newBuilder()
                    .post(payload.toRequestBody(mediaType))
                    .build()
                val resp = client.newCall(req).execute()
                com.example.util.RawConsoleLogger.http("Magister-Berichten", "POST $url -> ${resp.code}")
                if (resp.isSuccessful) {
                    return Result.success(Unit)
                }
                lastError = "Magister gaf foutcode HTTP ${resp.code} terug."
            } catch (e: Exception) {
                lastError = e.message ?: "Netwerkfout bij verzenden"
            }
        }
        return Result.failure(IllegalStateException(lastError))
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
            val candidateUrls = listOf(
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
                        slots.add(
                            KwtSlot(
                                appointmentId = appt.id,
                                title = appt.title.ifBlank { "Keuzewerktijd" },
                                dateIso = appt.dateIso,
                                startTime = appt.startTime,
                                endTime = appt.endTime,
                                periodIndex = appt.periodIndex,
                                options = parsedOpts
                            )
                        )
                        break
                    }
                } catch (_: Exception) {
                }
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
                    val optsArr = o.optJSONArray("keuzes") ?: o.optJSONArray("Keuzes") ?: continue
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

    private fun parseKwtOptions(bodyStr: String): List<KwtOption> {
        val json = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return emptyList()
        val arr = json.optJSONArray("items")
            ?: json.optJSONArray("Items")
            ?: json.optJSONArray("keuzes")
            ?: json.optJSONArray("Keuzes")
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
