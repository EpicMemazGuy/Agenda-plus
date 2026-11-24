package com.example.network

import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.round

sealed class AuthResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val studentName: String,
        val studentId: String,
        val studentClass: String,
        val personId: Long,
        val resolvedTenant: SchoolTenant? = null,
        val expiresInSeconds: Long = 3600L
    ) : AuthResult()

    data class Error(
        val errorCode: Int,
        val errorMessage: String,
        val technicalDetails: String
    ) : AuthResult()
}

sealed class TokenRefreshResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMs: Long
    ) : TokenRefreshResult()

    data class InvalidGrant(val errorMessage: String) : TokenRefreshResult()

    data class TransientError(val code: Int, val errorMessage: String) : TokenRefreshResult()
}

data class MagisterDataBundle(
    val appointments: List<Appointment>,
    val disruptions: List<Disruption>,
    val grades: List<SubjectGradeSummary>,
    val tasks: List<HomeworkTask>,
    val absences: List<AbsenceRecord>,
    val announcements: List<AnnouncementItem>,
    val messages: List<MagisterMessage> = emptyList(),
    val messageFolders: List<MessageFolderInfo> = emptyList(),
    val studyGuides: List<StudyGuide> = emptyList(),
    val kwtSlots: List<KwtSlot> = emptyList()
)

object PkceHelper {
    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun base64UrlEncode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b1 = bytes[i++].toInt() and 0xFF
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b3 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c1 = b1 ushr 2
            val c2 = ((b1 and 0x03) shl 4) or (if (b2 >= 0) b2 ushr 4 else 0)
            val c3 = if (b2 >= 0) ((b2 and 0x0F) shl 2) or (if (b3 >= 0) b3 ushr 6 else 0) else -1
            val c4 = if (b3 >= 0) b3 and 0x3F else -1

            sb.append(BASE64_URL_ALPHABET[c1])
            sb.append(BASE64_URL_ALPHABET[c2])
            if (c3 >= 0) sb.append(BASE64_URL_ALPHABET[c3])
            if (c4 >= 0) sb.append(BASE64_URL_ALPHABET[c4])
        }
        return sb.toString()
    }

    fun base64UrlDecodeToString(encoded: String): String {
        val clean = encoded.replace("\n", "").replace("\r", "")
        val decodedBytes = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            if (ch == '=') break
            val index = BASE64_URL_ALPHABET.indexOf(ch).takeIf { it >= 0 }
                ?: (if (ch == '+') 62 else if (ch == '/') 63 else -1)
            if (index >= 0) {
                buffer = (buffer shl 6) or index
                bitsLeft += 6
                if (bitsLeft >= 8) {
                    bitsLeft -= 8
                    decodedBytes.add(((buffer ushr bitsLeft) and 0xFF).toByte())
                }
            }
        }
        return String(decodedBytes.toByteArray(), Charsets.UTF_8)
    }

    fun extractClaim(jwt: String, claimKey: String): String? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val jsonStr = base64UrlDecodeToString(parts[1])
            val regex = Regex("\"$claimKey\"\\s*:\\s*\"([^\"]+)\"")
            regex.find(jsonStr)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeJwtPayload(jwt: String): JSONObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val jsonStr = base64UrlDecodeToString(parts[1])
            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun generateVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    fun generateChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return base64UrlEncode(digest)
    }

    fun generateRandomState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }
}

class MagisterClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val clientId = "M6LOAPP"
    val redirectUri = "m6loapp://oauth2redirect/"
    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private fun newApiRequest(url: String, token: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("X-API-Client-ID", "12D8")
            .header("User-Agent", browserUserAgent)
            .build()
    }

    private val extendedApi by lazy {
        MagisterExtendedApi(
            client = client,
            requestBuilder = { url, token -> newApiRequest(url, token) },
            dateParser = { raw -> parseMagisterDateTime(raw) }
        )
    }

    /**
     * Builds OAuth authorization URL.
     * Magister M6LOAPP requires response_type=code id_token and redirect_uri=m6loapp://oauth2redirect/.
     */
    fun buildAuthorizationUrl(tenant: SchoolTenant?, verifier: String, state: String): String {
        val challenge = PkceHelper.generateChallenge(verifier)
        val scope = URLEncoder.encode("openid profile offline_access magister.mobile magister.ecs", "UTF-8").replace("+", "%20")
        val encRedirect = URLEncoder.encode(redirectUri, "UTF-8")

        val base = "https://accounts.magister.net/connect/authorize?" +
                "client_id=$clientId" +
                "&redirect_uri=$encRedirect" +
                "&response_type=code%20id_token" +
                "&scope=$scope" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256" +
                "&state=$state" +
                "&nonce=$state"

        return if (tenant != null && tenant.subdomain.isNotBlank() && tenant.subdomain != "accounts") {
            val acr = URLEncoder.encode("tenant:${tenant.subdomain}", "UTF-8").replace("+", "%20")
            "$base&acr_values=$acr"
        } else {
            base
        }
    }

    fun buildDirectAuthorizationUrl(verifier: String, state: String): String {
        return buildAuthorizationUrl(null, verifier, state)
    }

    /**
     * Exchanges real OAuth2 authorization code with Magister Identity Services.
     * Resolves the real school tenant and fetches the authenticated student's account profile.
     */
    suspend fun exchangeAuthorizationCode(
        tenant: SchoolTenant?,
        authCode: String,
        verifier: String,
        idTokenHint: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanCode = authCode.trim()
        com.example.util.RawConsoleLogger.http(
            "Auth",
            "POST https://accounts.magister.net/connect/token (codeLen=${cleanCode.length}, redirect_uri=$redirectUri)"
        )

        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", cleanCode)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.magister.net/connect/token")
            .post(formBody)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                com.example.util.RawConsoleLogger.error(
                    "Auth",
                    "Token endpoint HTTP ${response.code}: ${bodyString.take(300)}"
                )
                val parsedJson = try { JSONObject(bodyString) } catch (e: Exception) { null }
                val errorMsg = parsedJson?.optString("error_description")?.ifBlank { null }
                    ?: parsedJson?.optString("error")?.ifBlank { null }
                    ?: "HTTP ${response.code} van accounts.magister.net/connect/token"

                return@withContext AuthResult.Error(
                    errorCode = response.code,
                    errorMessage = "Magister OAuth2 Fout (${response.code})",
                    technicalDetails = "$errorMsg\nEndpoint: accounts.magister.net/connect/token"
                )
            }

            val json = JSONObject(bodyString)
            val accessToken = json.optString("access_token")
            if (accessToken.isBlank()) {
                com.example.util.RawConsoleLogger.error("Auth", "connect/token 200 OK maar geen access_token ontvangen!")
                return@withContext AuthResult.Error(
                    errorCode = 500,
                    errorMessage = "Geen access_token ontvangen",
                    technicalDetails = "Server antwoord mist 'access_token': ${bodyString.take(200)}"
                )
            }

            val refreshToken = json.optString("refresh_token").ifBlank { null }
            val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
            val idToken = json.optString("id_token").ifBlank { idTokenHint }
            com.example.util.RawConsoleLogger.http(
                "Auth",
                "Token exchange OK (200). accessTokenLen=${accessToken.length}, hasRefresh=${refreshToken != null}, expiresIn=${expiresIn}s"
            )

            // Deduce real tenant subdomain from host-meta, JWT tokens, or userinfo
            val resolvedTenant = deduceTenant(tenant, accessToken, idToken)
            com.example.util.RawConsoleLogger.info(
                "Tenant",
                "Gekozen school domein: ${resolvedTenant.subdomain}.magister.net"
            )

            // Fetch real student account from school API
            val profile = fetchStudentProfileInternal(resolvedTenant, accessToken, idToken)
            com.example.util.RawConsoleLogger.info(
                "Profile",
                "Profiel opgehaald: ${profile.name} (personId=${profile.personId})"
            )

            AuthResult.Success(
                accessToken = accessToken,
                refreshToken = refreshToken,
                studentName = profile.name,
                studentId = profile.id,
                studentClass = profile.className,
                personId = profile.personId,
                resolvedTenant = resolvedTenant,
                expiresInSeconds = expiresIn
            )
        } catch (e: IOException) {
            AuthResult.Error(
                errorCode = 0,
                errorMessage = "Verbinding of account ophalen mislukt",
                technicalDetails = "${e.localizedMessage ?: "Netwerkfout"}\nServer: accounts.magister.net"
            )
        } catch (e: Exception) {
            AuthResult.Error(
                errorCode = 500,
                errorMessage = "Onverwachte verificatiefout",
                technicalDetails = e.message ?: "Onbekende fout tijdens token-uitwisseling"
            )
        }
    }

    fun isAccessTokenExpired(token: String, bufferSeconds: Long = 300L): Boolean {
        val payload = PkceHelper.decodeJwtPayload(token) ?: return false
        val exp = payload.optLong("exp", 0L)
        if (exp <= 0L) return false
        val nowSeconds = System.currentTimeMillis() / 1000L
        return nowSeconds >= (exp - bufferSeconds)
    }

    suspend fun refreshAccessTokenDetailed(refreshToken: String): TokenRefreshResult = withContext(Dispatchers.IO) {
        val cleanRefresh = refreshToken.trim()
        if (cleanRefresh.isBlank()) {
            return@withContext TokenRefreshResult.InvalidGrant("Geen refresh token aanwezig")
        }

        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", cleanRefresh)
            .build()

        val request = Request.Builder()
            .url("https://accounts.magister.net/connect/token")
            .post(formBody)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "application/json")
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                com.example.util.RawConsoleLogger.http("Auth", "Refresh token -> ${response.code}")
                if (!response.isSuccessful) {
                    val errorJson = runCatching { JSONObject(bodyString) }.getOrNull()
                    val errorCode = errorJson?.optString("error")?.trim()?.lowercase(Locale.US) ?: ""
                    val errorDesc = errorJson?.optString("error_description")?.cleanSchoolText()
                        ?: errorJson?.optString("error")?.cleanSchoolText()
                        ?: "HTTP ${response.code}"
                    if (errorCode == "invalid_grant" || errorCode == "invalid_token") {
                        return@use TokenRefreshResult.InvalidGrant(errorDesc)
                    }
                    return@use TokenRefreshResult.TransientError(response.code, errorDesc)
                }

                val json = JSONObject(bodyString)
                val newAccess = json.optString("access_token").cleanSchoolText()
                    ?: return@use TokenRefreshResult.TransientError(500, "Leeg access_token ontvangen")
                val newRefresh = json.optString("refresh_token").cleanSchoolText() ?: cleanRefresh
                val expiresInSec = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
                val expiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)
                TokenRefreshResult.Success(
                    accessToken = newAccess,
                    refreshToken = newRefresh,
                    expiresAtMs = expiresAtMs
                )
            }
        } catch (e: IOException) {
            com.example.util.RawConsoleLogger.warn("Auth", "Token vernieuwen tijdelijk mislukt (netwerk): ${e.message}")
            TokenRefreshResult.TransientError(0, e.message ?: "Netwerkfout")
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.warn("Auth", "Token vernieuwen fout: ${e.message}")
            TokenRefreshResult.TransientError(500, e.message ?: "Onbekende fout")
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): Pair<String, String?>? {
        return when (val res = refreshAccessTokenDetailed(refreshToken)) {
            is TokenRefreshResult.Success -> res.accessToken to res.refreshToken
            else -> null
        }
    }

    private fun normalizeSubdomain(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim().lowercase()
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore("/")
        if (s.endsWith(".magister.net")) {
            s = s.removeSuffix(".magister.net")
        }
        return s.takeIf { it.isNotBlank() && it != "accounts" && it != "magister" && it != "www" }
    }

    private fun deduceTenant(
        initialTenant: SchoolTenant?,
        accessToken: String,
        idToken: String?
    ): SchoolTenant {
        if (initialTenant != null && initialTenant.subdomain.isNotBlank() && initialTenant.subdomain != "accounts") {
            return initialTenant
        }

        var foundSubdomain: String? = null

        // 1. Official Magister API host discovery via .well-known/host-meta.json
        try {
            val hostMetaUrl = "https://magister.net/.well-known/host-meta.json?rel=magister-api"
            val req = newApiRequest(hostMetaUrl, accessToken)
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            com.example.util.RawConsoleLogger.http("Tenant", "GET host-meta.json -> ${resp.code} (${body.length}b)")
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val links = json.optJSONArray("links")
                if (links != null) {
                    for (i in 0 until links.length()) {
                        val linkObj = links.optJSONObject(i) ?: continue
                        val href = linkObj.optString("href")
                        val normalized = normalizeSubdomain(href)
                        if (normalized != null) {
                            foundSubdomain = normalized
                            com.example.util.RawConsoleLogger.info("Tenant", "host-meta href=$href -> $normalized")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.warn("Tenant", "host-meta.json check overgeslagen: ${e.message}")
        }

        // 2. Inspect JWT payload claims of access_token and id_token
        val tokensToInspect = listOfNotNull(accessToken, idToken)
        if (foundSubdomain.isNullOrBlank()) {
            for (tok in tokensToInspect) {
                val payload = PkceHelper.decodeJwtPayload(tok)
                if (payload != null) {
                    val keysList = payload.keys().asSequence().toList()
                    com.example.util.RawConsoleLogger.info("JWT", "Claims keys: ${keysList.joinToString(", ")}")
                    val candidates = listOf(
                        payload.optString("tenant"),
                        payload.optString("magister_tenant"),
                        payload.optString("school"),
                        payload.optString("domain")
                    )
                    for (cand in candidates) {
                        val norm = normalizeSubdomain(cand)
                        if (norm != null) {
                            foundSubdomain = norm
                            break
                        }
                    }
                    if (foundSubdomain.isNullOrBlank()) {
                        val sub = payload.optString("sub")
                        val pref = payload.optString("preferred_username")
                        for (emailLike in listOf(sub, pref)) {
                            if (emailLike.contains("@")) {
                                val domainPart = emailLike.substringAfter("@")
                                val norm = normalizeSubdomain(domainPart)
                                if (norm != null) {
                                    foundSubdomain = norm
                                    break
                                }
                            }
                        }
                    }
                }
                if (!foundSubdomain.isNullOrBlank()) break
            }
        }

        // 3. Fallback: Query /connect/userinfo
        if (foundSubdomain.isNullOrBlank()) {
            try {
                val userinfoReq = newApiRequest("https://accounts.magister.net/connect/userinfo", accessToken)
                val resp = client.newCall(userinfoReq).execute()
                val body = resp.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http("Tenant", "GET /connect/userinfo -> ${resp.code}")
                if (resp.isSuccessful) {
                    val infoJson = JSONObject(body)
                    foundSubdomain = normalizeSubdomain(infoJson.optString("tenant"))
                        ?: normalizeSubdomain(infoJson.optString("magister_tenant"))
                        ?: normalizeSubdomain(infoJson.optString("school"))
                }
            } catch (e: Exception) {
                com.example.util.RawConsoleLogger.warn("Tenant", "userinfo fout: ${e.message}")
            }
        }

        val finalSubdomain = foundSubdomain ?: "ignatius"
        val schoolName = when (finalSubdomain) {
            "elde" -> "Elde College (Magister)"
            else -> "${finalSubdomain.replaceFirstChar { it.uppercase() }} (Magister)"
        }
        val schoolCity = when (finalSubdomain) {
            "elde" -> "Schijndel"
            "ignatius", "barlaeus", "vossius", "het4egymnasium" -> "Amsterdam"
            "sgl" -> "Leiden"
            "usg" -> "Utrecht"
            "wolfert" -> "Rotterdam"
            "daltondenhaag" -> "Den Haag"
            else -> "Nederland"
        }

        return SchoolTenant(
            id = finalSubdomain,
            name = schoolName,
            city = schoolCity,
            subdomain = finalSubdomain,
            provider = SchoolProvider.MAGISTER
        )
    }

    private fun JSONObject.optCleanString(vararg keys: String): String? {
        for (key in keys) {
            if (!this.has(key) || this.isNull(key)) continue
            val value = this.optString(key, "").cleanSchoolText()
            if (value != null) return value
        }
        return null
    }

    /**
     * Authenticates with existing Bearer token directly.
     */
    suspend fun verifyExistingBearerToken(
        tenant: SchoolTenant,
        token: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val resolvedTenant = deduceTenant(tenant, token.trim(), null)
            val profile = fetchStudentProfileInternal(resolvedTenant, token.trim())
            AuthResult.Success(
                accessToken = token.trim(),
                refreshToken = null,
                studentName = profile.name,
                studentId = profile.id,
                studentClass = profile.className,
                personId = profile.personId,
                resolvedTenant = resolvedTenant
            )
        } catch (e: Exception) {
            AuthResult.Error(
                errorCode = 401,
                errorMessage = "Ongeldig of verlopen Magister token",
                technicalDetails = e.localizedMessage ?: "Token kon niet gevalideerd worden bij ${tenant.subdomain}.magister.net"
            )
        }
    }

    private fun fetchStudentProfileInternal(
        tenant: SchoolTenant,
        token: String,
        idToken: String? = null
    ): ProfileData {
        val candidateUrls = listOf(
            "${tenant.apiBaseUrl}/account?noCache=0",
            "${tenant.apiBaseUrl}/account",
            "${tenant.apiBaseUrl}/personen/zelf"
        )

        var lastStatus = 0
        var lastErrorBody = ""

        for (url in candidateUrls) {
            try {
                val request = newApiRequest(url, token)
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                com.example.util.RawConsoleLogger.http("Profile", "GET $url -> ${response.code} (${body.length}b)")

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val person = json.optJSONObject("Persoon") ?: json.optJSONObject("persoon")
                    val personId = person?.optLong("Id", 0L)?.takeIf { it > 0 }
                        ?: person?.optLong("id", 0L)?.takeIf { it > 0 }
                        ?: json.optLong("Id", 0L).takeIf { it > 0 }
                        ?: json.optLong("id", 0L)

                    val firstName = person?.optCleanString("Roepnaam", "roepnaam") ?: ""
                    val prefix = person?.optCleanString("Tussenvoegsel", "tussenvoegsel") ?: ""
                    val lastName = person?.optCleanString("Achternaam", "achternaam") ?: ""
                    val combinedName = listOf(firstName, prefix, lastName)
                        .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        .joinToString(" ")

                    val fullName = combinedName.ifBlank {
                        json.optCleanString("Gebruikersnaam", "gebruikersnaam")
                            ?: extractProfileFromJwt(idToken ?: token, tenant).name
                    }

                    if (personId > 0) {
                        return ProfileData(
                            name = fullName,
                            id = "MAG-$personId",
                            className = "Leerling",
                            personId = personId
                        )
                    }
                } else {
                    lastStatus = response.code
                    lastErrorBody = body.take(200)
                    com.example.util.RawConsoleLogger.error("Profile", "Fout HTTP ${response.code} op $url: $lastErrorBody")
                }
            } catch (e: Exception) {
                lastErrorBody = e.message ?: "Netwerkfout"
                com.example.util.RawConsoleLogger.error("Profile", "Exception op $url: $lastErrorBody")
            }
        }

        throw IOException("Fout bij ophalen account op ${tenant.subdomain}.magister.net: HTTP $lastStatus ($lastErrorBody)")
    }

    private fun extractProfileFromJwt(jwt: String, tenant: SchoolTenant): ProfileData {
        val payload = PkceHelper.decodeJwtPayload(jwt)
        val sub = payload?.optCleanString("sub") ?: ""
        val name = payload?.optCleanString("name")
            ?: run {
                val given = payload?.optCleanString("given_name") ?: ""
                val family = payload?.optCleanString("family_name") ?: ""
                listOf(given, family).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            }
            ?: payload?.optCleanString("preferred_username")
            ?: "Magister Student (${tenant.subdomain.replaceFirstChar { it.uppercase() }})"

        val personId = sub.filter { it.isDigit() }.toLongOrNull() ?: 0L

        return ProfileData(
            name = name,
            id = if (personId > 0) "MAG-$personId" else "MAG",
            className = "Leerling",
            personId = personId
        )
    }

    /**
     * Fetches supported live data from Magister for the authenticated student.
     */
    suspend fun fetchAllMagisterData(
        tenant: SchoolTenant,
        token: String,
        personId: Long
    ): MagisterDataBundle = withContext(Dispatchers.IO) {
        val pId = if (personId > 0) personId else fetchStudentProfileInternal(tenant, token).personId
        com.example.util.RawConsoleLogger.info("Magister-Sync", "Start live sync voor ${tenant.subdomain}.magister.net (personId=$pId)")

        val appointmentsAndDisruptions = fetchAppointments(tenant, token, pId)
        val grades = fetchGrades(tenant, token, pId)
        val tasks = fetchTasks(tenant, token, pId, appointmentsAndDisruptions.first)
        val folders = extendedApi.fetchMessageFolders(tenant, token)
        val rawMessages = extendedApi.fetchMessages(tenant, token, folders)
        val messages = extendedApi.enrichMessagesWithDetails(tenant, token, rawMessages, limit = 25)
        val studyGuides = extendedApi.fetchStudyGuides(tenant, token, pId)
        val kwtSlots = extendedApi.fetchKwtSlots(tenant, token, pId, appointmentsAndDisruptions.first)
        val absences = fetchAbsences(tenant, token, pId)

        com.example.util.RawConsoleLogger.info(
            "Magister-Sync",
            "Sync voltooid: ${appointmentsAndDisruptions.first.size} lessen, ${grades.size} vakken, ${tasks.size} taken, ${messages.size} berichten, ${studyGuides.size} studiewijzers, ${absences.size} absenties"
        )

        MagisterDataBundle(
            appointments = appointmentsAndDisruptions.first,
            disruptions = appointmentsAndDisruptions.second,
            grades = grades,
            tasks = tasks,
            absences = absences,
            announcements = emptyList(),
            messages = messages,
            messageFolders = folders,
            studyGuides = studyGuides,
            kwtSlots = kwtSlots
        )
    }

    suspend fun fetchMessageDetail(tenant: SchoolTenant, token: String, messageId: String): String? =
        withContext(Dispatchers.IO) { extendedApi.fetchMessageDetail(tenant, token, messageId) }

    suspend fun enrichMessagesWithDetails(
        tenant: SchoolTenant,
        token: String,
        messages: List<MagisterMessage>,
        limit: Int = 25
    ): List<MagisterMessage> = withContext(Dispatchers.IO) {
        extendedApi.enrichMessagesWithDetails(tenant, token, messages, limit)
    }

    suspend fun markMessageAsRead(tenant: SchoolTenant, token: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) { extendedApi.markMessageAsRead(tenant, token, messageId) }

    suspend fun enrollKwtOption(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appointmentId: Long,
        optionId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        extendedApi.enrollKwtOption(tenant, token, personId, appointmentId, optionId)
    }

    suspend fun fetchKwtSlotForAppointment(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appointment: Appointment
    ): KwtSlot? = withContext(Dispatchers.IO) {
        extendedApi.fetchKwtSlotForAppointment(tenant, token, personId, appointment)
    }

    private fun parseMagisterDateTime(raw: String): Date? {
        if (raw.isBlank()) return null
        val trimmed = raw.trim()
        // Strip fractional seconds (e.g. .0000000) while keeping trailing Z or offset
        val normalized = trimmed.replace(Regex("\\.\\d+"), "")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to TimeZone.getTimeZone("UTC"),
            "yyyy-MM-dd'T'HH:mm:ssXXX" to TimeZone.getDefault(),
            "yyyy-MM-dd'T'HH:mm:ss" to TimeZone.getDefault()
        )
        for ((pattern, tz) in patterns) {
            try {
                if (pattern.endsWith("'Z'") && !normalized.endsWith("Z")) continue
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }
                val parsed = sdf.parse(normalized)
                if (parsed != null) return parsed
            } catch (_: Exception) {
            }
        }
        // Fallback: parse first 19 chars as UTC if ends with Z, otherwise local
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = if (trimmed.endsWith("Z")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
            }
            sdf.parse(trimmed.take(19))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * GET /api/personen/{id}/afspraken
     */
    private fun fetchAppointments(
        tenant: SchoolTenant,
        token: String,
        personId: Long
    ): Pair<List<Appointment>, List<Disruption>> {
        val cal = Calendar.getInstance()
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Fetch 6-week window around current date: 14 days ago to 28 days ahead
        cal.add(Calendar.DAY_OF_YEAR, -14)
        val startDateStr = isoDateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 42)
        val endDateStr = isoDateFormat.format(cal.time)

        val candidateUrls = listOf(
            "${tenant.apiBaseUrl}/personen/$personId/afspraken?status=1&van=$startDateStr&tot=$endDateStr",
            "${tenant.apiBaseUrl}/personen/$personId/afspraken?van=$startDateStr&tot=$endDateStr"
        )

        val appts = mutableListOf<Appointment>()
        val disruptions = mutableListOf<Disruption>()
        var parsedSuccess = false

        for (url in candidateUrls) {
            if (parsedSuccess && appts.isNotEmpty()) break
            try {
                val request = newApiRequest(url, token)
                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: "{}"
                com.example.util.RawConsoleLogger.http(
                    "Magister-Api",
                    "GET $url -> ${response.code} (${bodyStr.length}b)"
                )

                if (!response.isSuccessful) {
                    com.example.util.RawConsoleLogger.error(
                        "Magister-Api",
                        "Afspraken fout HTTP ${response.code}: ${bodyStr.take(200)}"
                    )
                    continue
                }

                parsedSuccess = true
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("Items")
                    ?: json.optJSONArray("items")
                    ?: json.optJSONArray("data")
                    ?: JSONArray()

                com.example.util.RawConsoleLogger.info(
                    "Magister-Api",
                    "Afspraken JSON items count: ${items.length()}"
                )

                if (items.length() == 0) continue

                val hourFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
                val localDateIsoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }

                appts.clear()
                disruptions.clear()

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.optLong("Id", item.optLong("id", i.toLong()))

                    val vakken = item.optJSONArray("Vakken") ?: item.optJSONArray("vakken")
                    val vakName = if (vakken != null && vakken.length() > 0) {
                        vakken.optJSONObject(0)?.optCleanString("Naam", "naam")
                    } else null

                    val rawTitle = item.optCleanString("Omschrijving", "Titel", "omschrijving", "titel") ?: ""
                    val subjectName = (vakName ?: rawTitle.ifBlank { "Les" }).capitalizeSubjectName().ifBlank { "Les" }
                    val title = rawTitle.ifBlank { subjectName }

                    val rawStart = item.optCleanString("Start", "start", "begin") ?: ""
                    val rawEnd = item.optCleanString("Einde", "einde", "eind") ?: ""

                    var startTime = "08:30"
                    var endTime = "09:15"
                    var dayOfWeek = 1
                    var dateIso = ""

                    val parsedStart = parseMagisterDateTime(rawStart)
                    if (parsedStart != null) {
                        startTime = hourFormat.format(parsedStart)
                        dateIso = localDateIsoFormat.format(parsedStart)
                        val itemCal = Calendar.getInstance().apply { time = parsedStart }
                        dayOfWeek = when (itemCal.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.MONDAY -> 1
                            Calendar.TUESDAY -> 2
                            Calendar.WEDNESDAY -> 3
                            Calendar.THURSDAY -> 4
                            Calendar.FRIDAY -> 5
                            Calendar.SATURDAY -> 6
                            Calendar.SUNDAY -> 7
                            else -> 1
                        }
                    } else if (rawStart.length >= 10) {
                        dateIso = rawStart.take(10)
                    }

                    val parsedEnd = parseMagisterDateTime(rawEnd)
                    if (parsedEnd != null) {
                        endTime = hourFormat.format(parsedEnd)
                    }

                    val periodIndex = item.optInt("LesuurVan", item.optInt("lesuurVan", item.optInt("lesuur", 1))).coerceAtLeast(1)
                    val status = item.optInt("Status", item.optInt("status", 1))
                    val isCanceled = status == 4 || status == 5 || item.optBoolean("Vervallen", item.optBoolean("vervallen", false))
                    val isRelocated = status == 3 || item.optBoolean("IsLokaalwijziging", false)

                    val lokalen = item.optJSONArray("Lokalen") ?: item.optJSONArray("lokalen")
                    val classroom = if (lokalen != null && lokalen.length() > 0) {
                        lokalen.optJSONObject(0)?.optCleanString("Naam", "naam") ?: ""
                    } else {
                        item.optCleanString("Lokatie", "lokatie") ?: ""
                    }

                    val docenten = item.optJSONArray("Docenten") ?: item.optJSONArray("docenten")
                    val teacherName = if (docenten != null && docenten.length() > 0) {
                        docenten.optJSONObject(0)?.optCleanString("Naam", "naam") ?: ""
                    } else ""

                    val rawHomework = item.optCleanString("Inhoud", "inhoud")
                    val homework = rawHomework?.let {
                        android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT)
                            .toString()
                            .cleanSchoolText()
                    }

                    val appointmentType = item.optInt("Type", item.optInt("type", 0))
                    val infoType = item.optInt("InfoType", item.optInt("infoType", 0))
                    val isKwt = appointmentType == 1 ||
                        rawTitle.contains("KWT", ignoreCase = true) ||
                        rawTitle.contains("keuzeles", ignoreCase = true) ||
                        rawTitle.contains("keuzewerktijd", ignoreCase = true)

                    val appt = Appointment(
                        id = id,
                        title = title,
                        subjectCode = subjectName.take(4).lowercase(),
                        subjectName = subjectName,
                        teacherName = teacherName,
                        teacherInitials = teacherName.take(3).uppercase(),
                        classroom = classroom,
                        originalClassroom = null,
                        periodIndex = periodIndex,
                        startTime = startTime,
                        endTime = endTime,
                        dayOfWeek = dayOfWeek,
                        isCanceled = isCanceled,
                        isRelocated = isRelocated,
                        homework = homework,
                        dateIso = dateIso,
                        isKwt = isKwt,
                        appointmentType = appointmentType,
                        infoType = infoType
                    )
                    appts.add(appt)

                    if (isCanceled) {
                        disruptions.add(
                            Disruption(
                                id = "dis_$id",
                                title = "Uitval: ${appt.periodIndex}e uur ${appt.title} vervalt",
                                subject = appt.title,
                                period = appt.periodIndex,
                                description = "$teacherName is afwezig.",
                                type = DisruptionType.CANCELED
                            )
                        )
                    } else if (isRelocated) {
                        disruptions.add(
                            Disruption(
                                id = "dis_$id",
                                title = "Wijziging: ${appt.title}",
                                subject = appt.title,
                                period = appt.periodIndex,
                                description = "Lokaal $classroom ($startTime - $endTime).",
                                type = DisruptionType.ROOM_CHANGE
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                com.example.util.RawConsoleLogger.error("Magister-Api", "Exception op afspraken: ${e.message}")
            }
        }

        return Pair(appts, disruptions)
    }

    /**
     * GET /api/personen/{id}/cijfers/laatste + fallback /aanmeldingen/.../cijferoverzichtvooraanmelding
     */
    private fun fetchGrades(
        tenant: SchoolTenant,
        token: String,
        personId: Long
    ): List<SubjectGradeSummary> {
        try {
            val url = "${tenant.apiBaseUrl}/personen/$personId/cijfers/laatste?aantal=50"
            val request = newApiRequest(url, token)

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "{}"
            com.example.util.RawConsoleLogger.http("Magister-Api", "GET cijfers -> ${response.code} (${bodyStr.length}b)")
            if (response.isSuccessful) {
                val parsed = parseGradesJson(bodyStr)
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            } else {
                com.example.util.RawConsoleLogger.info("Magister-Api", "Cijfers laatste HTTP ${response.code}")
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.error("Magister-Api", "Cijfers fout: ${e.message}")
        }

        // Fallback: check current active enrollment (aanmelding) grade overview
        try {
            val aanmUrl = "${tenant.apiBaseUrl}/personen/$personId/aanmeldingen?geenToekomstige=false"
            val aanmResp = client.newCall(newApiRequest(aanmUrl, token)).execute()
            val aanmBody = aanmResp.body?.string() ?: "{}"
            if (aanmResp.isSuccessful) {
                val aanmJson = JSONObject(aanmBody)
                val aanmItems = aanmJson.optJSONArray("items") ?: aanmJson.optJSONArray("Items") ?: JSONArray()
                val latestAanm = if (aanmItems.length() > 0) aanmItems.optJSONObject(aanmItems.length() - 1) else null
                val aanmId = latestAanm?.optLong("id", latestAanm.optLong("Id", 0L)) ?: 0L
                if (aanmId > 0L) {
                    val overzUrl = "${tenant.apiBaseUrl}/personen/$personId/aanmeldingen/$aanmId/cijfers/cijferoverzichtvooraanmelding?actievePerioden=true&alleenBerekendeKolommen=false&alleenPTAKolommen=false"
                    val overzResp = client.newCall(newApiRequest(overzUrl, token)).execute()
                    val overzBody = overzResp.body?.string() ?: "{}"
                    com.example.util.RawConsoleLogger.http("Magister-Api", "GET cijferoverzicht -> ${overzResp.code} (${overzBody.length}b)")
                    if (overzResp.isSuccessful) {
                        return parseGradesJson(overzBody)
                    }
                }
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.warn("Magister-Api", "Cijferoverzicht fallback fout: ${e.message}")
        }

        return emptyList()
    }

    internal fun parseGradesJson(bodyStr: String): List<SubjectGradeSummary> {
        val summariesMap = mutableMapOf<String, MutableList<GradeItem>>()
        val trimmed = bodyStr.trim()
        if (trimmed.isBlank()) return emptyList()

        val items: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val json = JSONObject(trimmed)
            json.optJSONArray("Items")
                ?: json.optJSONArray("items")
                ?: json.optJSONArray("data")
                ?: JSONArray()
        }

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue

            val cijferObj = item.optJSONObject("cijfer") ?: item.optJSONObject("Cijfer")
            val rawScoreStr = item.optCleanString(
                "waarde", "Waarde",
                "CijferStr", "cijferStr",
                "Cijfer", "cijfer",
                "resultaat", "Resultaat",
                "result", "Result",
                "grade", "Grade",
                "value", "Value"
            ) ?: cijferObj?.optCleanString("waarde", "Waarde", "cijferStr", "CijferStr", "resultaat", "Resultaat", "result", "Result")
                ?: cijferObj?.optDouble("waarde", Double.NaN)?.takeIf { !it.isNaN() }?.toString()
                ?: cijferObj?.optDouble("cijfer", Double.NaN)?.takeIf { !it.isNaN() }?.toString()
                ?: item.optDouble("waarde", Double.NaN).takeIf { !it.isNaN() }?.toString()
                ?: item.optDouble("Cijfer", Double.NaN).takeIf { !it.isNaN() }?.toString()
                ?: item.optDouble("grade", Double.NaN).takeIf { !it.isNaN() }?.toString()
                ?: item.optDouble("value", Double.NaN).takeIf { !it.isNaN() }?.toString()
                ?: ""

            val score = rawScoreStr.replace(",", ".").trim().toDoubleOrNull() ?: continue
            if (score <= 0.0) continue

            val kolomObj = item.optJSONObject("cijferKolom") ?: item.optJSONObject("CijferKolom")
            val weight = listOf(
                item.optDouble("weegfactor", Double.NaN),
                item.optDouble("Weegfactor", Double.NaN),
                item.optDouble("Weging", Double.NaN),
                item.optDouble("weging", Double.NaN),
                kolomObj?.optDouble("weegfactor", Double.NaN) ?: Double.NaN,
                kolomObj?.optDouble("Weegfactor", Double.NaN) ?: Double.NaN
            ).firstOrNull { !it.isNaN() && it > 0.0 }
                ?: item.optCleanString("weegfactor", "Weegfactor", "Weging", "weging")
                    ?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: 1.0

            val vakObj = item.optJSONObject("Vak") ?: item.optJSONObject("vak")
            val rawSubjectName = vakObj?.optCleanString("Omschrijving", "omschrijving", "Naam", "naam")
                ?: item.optCleanString("VakOmschrijving", "vakOmschrijving", "vakNaam", "VakNaam")
                ?: "Algemeen"
            val subjectName = rawSubjectName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val subjectCode = vakObj?.optCleanString("Afkorting", "afkorting", "Code", "code")
                ?: item.optCleanString("vakCode", "VakCode")
                ?: subjectName.take(4).lowercase()

            val description = item.optCleanString("Omschrijving", "omschrijving", "kolomNaam", "KolomNaam", "Titel", "titel")
                ?: kolomObj?.optCleanString("kolomOmschrijving", "kolomNaam", "omschrijving", "naam")
                ?: "Toets $subjectName"

            val date = item.optCleanString(
                "behaaldOp", "BehaaldOp",
                "ingevoerdOp", "IngevoerdOp",
                "DatumIngevoerd", "datumIngevoerd"
            )?.take(10) ?: "Recent"

            val periodObj = item.optJSONObject("periode") ?: item.optJSONObject("Periode")
            val period = periodObj?.optInt("id", periodObj.optInt("Id", 1))
                ?: item.optInt("Periode", item.optInt("periode", 1)).coerceAtLeast(1)

            val id = item.optCleanString("CijferId", "cijferId", "kolomId", "KolomId", "Id", "id")
                ?: item.optLong("kolomId", item.optLong("cijferId", i.toLong())).toString()

            val kolomSoort = kolomObj?.optCleanString("kolomSoort", "KolomSoort", "soort", "Soort") ?: ""
            val isPta = item.optBoolean("isPta", item.optBoolean("IsPta", false)) ||
                (kolomObj?.optBoolean("isPta", kolomObj.optBoolean("IsPta", false)) == true) ||
                kolomSoort.equals("PTA", ignoreCase = true) ||
                description.contains("PTA", ignoreCase = true) ||
                description.contains("Schoolexamen", ignoreCase = true)

            val gradeItem = GradeItem(
                id = id,
                subjectCode = subjectCode,
                subjectName = subjectName,
                score = score,
                weight = weight,
                description = description,
                date = date,
                period = period,
                isPta = isPta
            )

            val list = summariesMap.getOrPut(subjectName) { mutableListOf() }
            list.add(gradeItem)
        }

        if (items.length() > 0 && summariesMap.isEmpty()) {
            val firstKeys = items.optJSONObject(0)?.keys()?.asSequence()?.toList()?.joinToString(", ") ?: ""
            com.example.util.RawConsoleLogger.info("Magister-Api", "Cijfers keys niet herkend: [$firstKeys]")
        }

        return summariesMap.map { (name, grades) ->
            val totalWeight = grades.sumOf { it.weight }
            val weightedSum = grades.sumOf { it.score * it.weight }
            val avg = if (totalWeight > 0) round((weightedSum / totalWeight) * 10) / 10.0 else 0.0

            SubjectGradeSummary(
                subjectCode = grades.firstOrNull()?.subjectCode ?: name.take(4).lowercase(),
                subjectName = name,
                weightedAverage = avg,
                grades = grades
            )
        }.sortedBy { it.subjectName }
    }

    /**
     * GET /api/personen/{id}/opdrachten or parsed from appointments
     */
    private fun fetchTasks(
        tenant: SchoolTenant,
        token: String,
        personId: Long,
        appointments: List<Appointment>
    ): List<HomeworkTask> {
        val tasks = mutableListOf<HomeworkTask>()

        // 1. Convert appointments that contain valid non-null homework or tests into tasks
        appointments.filter { !it.homework.cleanSchoolText().isNullOrBlank() || it.isTestOrExam || it.isPresentation }.forEachIndexed { idx, appt ->
            val hwTitle = appt.homework.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: return@forEachIndexed
            val teacherLabel = appt.teacherName.cleanSchoolText()?.let { "Opgegeven door $it" } ?: "Uit lesrooster"

            // Official Magister test system & Presentation checker:
            // - Presentation: detected via keyword checker ("spreekbeurt", "presentatie")
            // - InfoType 2 (PW), 3 (Toets/Tentamen): official Magister tests -> MAJOR_TEST
            // - InfoType 4 (SO), 5 (MO), or AppointmentType 7 (Toets): official Magister test -> EXAM
            // - InfoType 1 (Huiswerk): ALWAYS HOMEWORK (never a test, even if notes say "Oefenen SO")
            // - If InfoType == 0: check if appointment title explicitly designates a test/PO on the rooster
            val category = when {
                appt.isPresentation -> TaskCategory.PRESENTATION
                appt.infoType in listOf(2, 3) -> TaskCategory.MAJOR_TEST
                appt.infoType in listOf(4, 5) -> TaskCategory.EXAM
                appt.appointmentType == 7 -> TaskCategory.EXAM
                appt.infoType == 1 -> TaskCategory.HOMEWORK
                isPoMatch(appt.title) -> TaskCategory.PRACTICAL_ASSIGNMENT
                isMajorTestTitle(appt.title) -> TaskCategory.MAJOR_TEST
                isExamTitle(appt.title) -> TaskCategory.EXAM
                else -> TaskCategory.HOMEWORK
            }
            tasks.add(
                HomeworkTask(
                    id = "hw_${appt.id}_$idx",
                    subject = appt.subjectName,
                    title = hwTitle,
                    description = "$teacherLabel • ${appt.title}",
                    dueDay = appt.dateIso.ifBlank { "Lesdag ${appt.dayOfWeek}" },
                    dueDate = "${appt.startTime} (${appt.periodIndex}e uur)",
                    category = category,
                    isCompleted = false,
                    priority = if (category != TaskCategory.HOMEWORK) TaskPriority.HIGH else TaskPriority.MEDIUM
                )
            )
        }

        // 2. Fetch live ELO opdrachten only if tenant supports it (403/404 are ignored quietly)
        try {
            val url = "${tenant.apiBaseUrl}/personen/$personId/opdrachten"
            val request = newApiRequest(url, token)

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                com.example.util.RawConsoleLogger.http("Magister-Api", "GET opdrachten -> ${response.code}")
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("Items") ?: json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val title = item.optCleanString("Titel", "titel") ?: "Opdracht"
                    val subject = (item.optCleanString("Vak", "vak") ?: "Algemeen").capitalizeSubjectName().ifBlank { "Algemeen" }
                    val desc = item.optCleanString("Omschrijving", "omschrijving") ?: ""
                    val deadline = item.optCleanString("InleverenVoor", "inleverenVoor")?.take(16) ?: "Binnenkort"
                    val cat = when {
                        isPresentationKeywordMatch(title) || isPresentationKeywordMatch(desc) -> TaskCategory.PRESENTATION
                        isPoMatch(title) || isPoMatch(desc) -> TaskCategory.PRACTICAL_ASSIGNMENT
                        isMajorTestTitle(title) -> TaskCategory.MAJOR_TEST
                        isExamTitle(title) -> TaskCategory.EXAM
                        else -> TaskCategory.HOMEWORK
                    }

                    tasks.add(
                        HomeworkTask(
                            id = item.optCleanString("Id", "id") ?: "opdr_$i",
                            subject = subject,
                            title = title,
                            description = desc,
                            dueDay = "ELO Opdracht",
                            dueDate = deadline,
                            category = cat,
                            isCompleted = item.optBoolean("Afgesloten", false),
                            priority = if (cat != TaskCategory.HOMEWORK) TaskPriority.HIGH else TaskPriority.MEDIUM
                        )
                    )
                }
            } else {
                com.example.util.RawConsoleLogger.info(
                    "Magister-Api",
                    "ELO opdrachten niet actief voor ${tenant.subdomain} (HTTP ${response.code})"
                )
            }
        } catch (_: Exception) {
            // Silently ignore unsupported ELO endpoints
        }

        return tasks
    }

    /**
     * GET /api/personen/{id}/absenties
     */
    internal fun fetchAbsences(
        tenant: SchoolTenant,
        token: String,
        personId: Long
    ): List<AbsenceRecord> {
        val records = mutableListOf<AbsenceRecord>()
        try {
            val cal = Calendar.getInstance()
            val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            cal.add(Calendar.DAY_OF_YEAR, 7)
            val totStr = isoFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -67)
            val vanStr = isoFormat.format(cal.time)

            val url = "${tenant.apiBaseUrl}/personen/$personId/absenties?van=$vanStr&tot=$totStr"
            val request = newApiRequest(url, token)

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "{}"
            com.example.util.RawConsoleLogger.http("Magister-Api", "GET absenties -> ${response.code}")
            if (response.isSuccessful) {
                records.addAll(parseAbsencesJson(bodyStr))
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.error("Magister-Api", "Absenties fout: ${e.message}")
        }
        return records
    }

    internal fun parseAbsencesJson(bodyStr: String): List<AbsenceRecord> {
        val records = mutableListOf<AbsenceRecord>()
        val trimmed = bodyStr.trim()
        if (trimmed.isBlank()) return emptyList()
        val items = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val json = JSONObject(trimmed)
            json.optJSONArray("Items") ?: json.optJSONArray("items") ?: JSONArray()
        }
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val afspraakObj = item.optJSONObject("Afspraak") ?: item.optJSONObject("afspraak")
            val desc = item.optCleanString("Omschrijving", "omschrijving", "Reden", "reden")
                ?: afspraakObj?.optCleanString("Omschrijving", "omschrijving")
                ?: "Verzuim"
            val rawStart = item.optCleanString("Start", "start", "Datum", "datum", "Begin", "begin")
                ?: afspraakObj?.optCleanString("Start", "start")
                ?: ""
            val rawEnd = item.optCleanString("Einde", "einde")
                ?: afspraakObj?.optCleanString("Einde", "einde")
                ?: ""
            val date = rawStart.take(10)
            val isApproved = if (item.has("Geoorloofd")) {
                item.optBoolean("Geoorloofd", true)
            } else if (item.has("geoorloofd")) {
                item.optBoolean("geoorloofd", true)
            } else {
                !desc.contains("ongeoorloofd", ignoreCase = true)
            }
            val type = when {
                desc.contains("ziek", ignoreCase = true) -> AbsenceType.SICK
                desc.contains("laat", ignoreCase = true) -> AbsenceType.TARDY
                !isApproved -> AbsenceType.UNEXCUSED
                else -> AbsenceType.EXCUSED
            }
            val hourInt = item.optInt(
                "LesuurVan",
                item.optInt("lesuurVan", item.optInt("Lesuur", item.optInt("lesuur", -1)))
            ).takeIf { it > 0 }
                ?: afspraakObj?.optInt("LesuurVan", afspraakObj.optInt("lesuurVan", -1))?.takeIf { it > 0 }
            val periodRaw = item.optCleanString("Lesuur", "lesuur")
                ?: hourInt?.let { "${it}e uur" }
                ?: "Hele dag"
            val parsedHour = hourInt ?: Regex("\\d+").find(periodRaw)?.value?.toIntOrNull()

            val afspraakVakken = afspraakObj?.optJSONArray("Vakken") ?: afspraakObj?.optJSONArray("vakken")
            val subjectName = (item.optCleanString("Vak", "vak", "VakOmschrijving", "vakOmschrijving")
                ?: afspraakVakken?.optJSONObject(0)?.optCleanString("Naam", "naam")
                ?: afspraakObj?.optCleanString("Omschrijving", "omschrijving")
                ?: "").capitalizeSubjectName()
            val code = item.optCleanString("Code", "code", "Type", "type", "Soort", "soort") ?: ""
            val id = item.optCleanString("Id", "id")
                ?: item.optLong("Id", item.optLong("id", i.toLong())).toString()

            records.add(
                AbsenceRecord(
                    id = id,
                    date = date,
                    period = periodRaw,
                    type = type,
                    reason = desc,
                    isApproved = isApproved,
                    startIso = rawStart,
                    endIso = rawEnd,
                    hour = parsedHour,
                    subject = subjectName,
                    code = code
                )
            )
        }
        return records
    }

    /**
     * GET /api/berichten/ontvangen
     */
    private fun fetchAnnouncements(
        tenant: SchoolTenant,
        token: String
    ): List<AnnouncementItem> {
        val itemsList = mutableListOf<AnnouncementItem>()
        try {
            val url = "${tenant.apiBaseUrl}/berichten/ontvangen?aantal=20"
            val request = newApiRequest(url, token)

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("Items") ?: json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val senderObj = item.optJSONObject("Afzender")
                    val senderName = senderObj?.optString("Naam") ?: "School"
                    val subject = item.optString("Onderwerp", "Mededeling")
                    val date = item.optString("VerzondenOp").take(10)
                    val content = item.optString("Inhoud", "").ifBlank { "Geen berichttekst" }

                    itemsList.add(
                        AnnouncementItem(
                            id = item.optString("Id", "msg_$i"),
                            title = subject,
                            author = senderName,
                            date = date,
                            content = content,
                            isPinned = i == 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.error("Magister-Api", "Mededelingen fout: ${e.message}")
        }
        return itemsList
    }

    /**
     * GET /api/berichten/ontvangen
     */
    fun fetchMessages(
        tenant: SchoolTenant,
        token: String
    ): List<MagisterMessage> {
        val messagesList = mutableListOf<MagisterMessage>()
        try {
            val url = "${tenant.apiBaseUrl}/berichten/ontvangen?aantal=50"
            val request = newApiRequest(url, token)

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "{}"
            com.example.util.RawConsoleLogger.http("Magister-Api", "GET berichten -> ${response.code} (${bodyStr.length}b)")
            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("Items") ?: json.optJSONArray("items") ?: JSONArray()
                val dutchDateFormat = SimpleDateFormat("d MMM HH:mm", Locale("nl", "NL"))

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.optCleanString("Id", "id") ?: item.optLong("Id", item.optLong("id", i.toLong())).toString()
                    val subject = item.optCleanString("Onderwerp", "onderwerp") ?: "Schoolbericht"
                    val senderObj = item.optJSONObject("Afzender") ?: item.optJSONObject("afzender")
                    val senderName = senderObj?.optCleanString("Naam", "naam", "Weergavenaam", "weergavenaam")
                        ?: item.optCleanString("AfzenderNaam", "afzenderNaam", "Afzender", "afzender")
                            ?.takeIf { !it.startsWith("{") }
                        ?: "Schooladministratie"
                    val senderEmail = senderObj?.optCleanString("Email", "email")
                    val rawDate = item.optCleanString("VerzondenOp", "verzondenOp") ?: ""
                    val parsedDate = parseMagisterDateTime(rawDate)
                    val formattedDate = if (parsedDate != null) {
                        dutchDateFormat.format(parsedDate)
                    } else if (rawDate.length >= 10) {
                        rawDate.take(10)
                    } else {
                        "Recent"
                    }

                    val rawBody = item.optCleanString("Inhoud", "inhoud", "Omschrijving", "omschrijving") ?: ""
                    val cleanBody = if (rawBody.isNotBlank()) {
                        android.text.Html.fromHtml(rawBody, android.text.Html.FROM_HTML_MODE_COMPACT)
                            .toString()
                            .cleanSchoolText() ?: ""
                    } else ""
                    val isRead = item.optBoolean("IsGelezen", item.optBoolean("isGelezen", false))
                    val hasAttachments = item.optBoolean("HeeftBijlagen", item.optBoolean("heeftBijlagen", false))
                    val ontvangersArr = item.optJSONArray("Ontvangers") ?: item.optJSONArray("ontvangers")
                    val recipientsStr = if (ontvangersArr != null) {
                        (0 until ontvangersArr.length()).mapNotNull { idx ->
                            val o = ontvangersArr.optJSONObject(idx)
                            o?.optCleanString("Naam", "naam", "Weergavenaam", "weergavenaam")
                                ?: ontvangersArr.optString(idx, "").cleanSchoolText()
                        }.distinct().joinToString(", ")
                    } else {
                        item.optCleanString("Ontvangers", "ontvangers", "OntvangersNamen", "ontvangersNamen")
                            ?.takeIf { !it.startsWith("[") && !it.startsWith("{") } ?: ""
                    }

                    messagesList.add(
                        MagisterMessage(
                            id = id,
                            subject = subject,
                            senderName = senderName,
                            senderEmail = senderEmail,
                            dateSent = formattedDate,
                            body = cleanBody,
                            isRead = isRead,
                            hasAttachments = hasAttachments,
                            folder = MessageFolder.INBOX,
                            recipients = recipientsStr,
                            rawDateIso = rawDate
                        )
                    )
                }
            } else {
                com.example.util.RawConsoleLogger.error("Magister-Api", "Berichten HTTP ${response.code}: ${bodyStr.take(150)}")
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.error("Magister-Api", "Berichten fout: ${e.message}")
        }

        return messagesList
    }
}

private data class ProfileData(
    val name: String,
    val id: String,
    val className: String,
    val personId: Long
)
