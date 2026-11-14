package com.example.network

import com.example.data.AppPreferencesStore
import com.example.model.BusStopInfo
import com.example.model.BusTrackingState
import com.example.model.BusVehiclePoint
import com.example.model.SchoolAddress
import com.example.model.cleanSchoolText
import com.example.util.RawConsoleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class TransitClient(private val prefs: AppPreferencesStore? = null) {

    private val client: OkHttpClient = run {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedLineCatalog: List<LineCatalogEntry>? = null

    @Volatile
    private var cachedCatalogTimestamp: Long = 0L

    private data class LineCatalogEntry(
        val key: String,
        val publicNumber: String,
        val planningNumber: String,
        val direction: Int,
        val name: String,
        val destination: String,
        val transportType: String,
        val ownerCode: String
    )

    suspend fun searchSchoolAddress(query: String): List<SchoolAddress> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&countrycodes=nl&limit=6&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AgendaPlus-Android/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: "[]"
            RawConsoleLogger.http("OSM-Geo", "Nominatim '$clean' -> ${resp.code}")
            if (!resp.isSuccessful) return@withContext emptyList()

            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                    val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                    val fullDisplay = obj.optString("display_name").cleanSchoolText() ?: continue
                    val parts = fullDisplay.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val shortTitle = parts.take(2).joinToString(", ").ifBlank { fullDisplay }
                    val sub = parts.drop(1).take(3).joinToString(", ").ifBlank { fullDisplay }
                    add(
                        SchoolAddress(
                            displayName = shortTitle,
                            streetAndCity = sub,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
        } catch (e: Exception) {
            RawConsoleLogger.warn("OSM-Geo", "Adres zoeken fout: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchLiveBusTracking(
        rawLineNumber: String,
        cityHint: String?,
        userLat: Double?,
        userLon: Double?
    ): BusTrackingState = withContext(Dispatchers.IO) {
        val cleanLine = rawLineNumber.trim()
        if (cleanLine.isBlank()) {
            return@withContext BusTrackingState(errorMessage = "Vul eerst een buslijnnummer in (bijv. 156 of 300).")
        }

        try {
            val catalog = getOrFetchLineCatalog()
            if (catalog.isEmpty()) {
                return@withContext BusTrackingState(
                    lineNumber = cleanLine,
                    errorMessage = "OVapi catalogus kon niet worden geladen. Controleer je internetverbinding."
                )
            }

            // Match entries where LinePublicNumber equals user's trimmed input
            val cleanNormalized = cleanLine.lowercase(Locale.ROOT)
            val matchedEntries = catalog.filter { entry ->
                entry.publicNumber.lowercase(Locale.ROOT) == cleanNormalized ||
                    entry.publicNumber.lowercase(Locale.ROOT) == "lijn $cleanNormalized" ||
                    entry.publicNumber.replace(" ", "").lowercase(Locale.ROOT) == cleanNormalized.replace(" ", "")
            }.ifEmpty {
                // Secondary fallback: ends with cleanLine (e.g. "N300" or planning match)
                catalog.filter { entry ->
                    entry.publicNumber.endsWith(cleanLine, ignoreCase = true) ||
                        entry.planningNumber.equals(cleanLine, ignoreCase = true)
                }
            }

            if (matchedEntries.isEmpty()) {
                return@withContext BusTrackingState(
                    lineNumber = cleanLine,
                    errorMessage = "Geen actieve OVapi-lijn gevonden voor buslijn '$cleanLine'."
                )
            }

            // Extract keywords from school city / address / GPS region
            val hintTokens = (cityHint ?: "")
                .split(",", " ", "-", "/", ";")
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.length >= 3 }

            val brabantTerms = listOf(
                "schijndel", "hertogenbosch", "den bosch", "eindhoven",
                "sint-michielsgestel", "gestel", "uden", "veghel", "vught",
                "boxtel", "brabant", "elde", "arriva"
            )
            val isBrabantContext = hintTokens.any { token -> brabantTerms.any { it.contains(token) } } ||
                (userLat != null && userLon != null && userLat in 51.2..51.9 && userLon in 4.7..5.9)

            fun scoreEntry(entry: LineCatalogEntry): Int {
                var score = 0
                val nameLower = entry.name.lowercase(Locale.ROOT)
                val destLower = entry.destination.lowercase(Locale.ROOT)
                val ownerLower = entry.ownerCode.lowercase(Locale.ROOT)

                for (token in hintTokens) {
                    if (nameLower.contains(token)) score += 35
                    if (destLower.contains(token)) score += 25
                    if (ownerLower.contains(token)) score += 15
                }

                if (isBrabantContext) {
                    for (b in brabantTerms) {
                        if (nameLower.contains(b)) score += 40
                        if (destLower.contains(b)) score += 25
                    }
                    if (entry.ownerCode.equals("ARR", ignoreCase = true)) score += 25
                }

                if (entry.transportType.equals("BUS", ignoreCase = true)) score += 10
                return score
            }

            // Group matched entries by planningNumber
            val groupedByPlanning = matchedEntries.groupBy { it.planningNumber }
            val sortedPlanningGroups = groupedByPlanning.entries.sortedByDescending { (_, entries) ->
                entries.maxOfOrNull { scoreEntry(it) } ?: 0
            }.map { it.value }

            var bestStops: List<BusStopInfo> = emptyList()
            var bestVehicles: List<BusVehiclePoint> = emptyList()
            var bestGroupName = ""
            var bestOwnerCode = ""
            var chosenPlanningNumber = ""

            // Iterate through top candidate planning groups to load both directions (_1 and _2)
            for (candidateGroup in sortedPlanningGroups.take(3)) {
                val planNum = candidateGroup.first().planningNumber
                val candidateName = candidateGroup.map { it.name }.firstOrNull { it.isNotBlank() }
                    ?: "Buslijn $cleanLine"
                val candidateOwner = candidateGroup.map { it.ownerCode }.firstOrNull { it.isNotBlank() && it != "NL" }
                    ?: if (isBrabantContext) "ARR" else ""

                val (stops, vehicles) = fetchBothDirections(
                    planningNumber = planNum,
                    candidateOwner = candidateOwner,
                    isBrabant = isBrabantContext,
                    cleanLine = cleanLine
                )

                if (stops.isNotEmpty() || vehicles.isNotEmpty()) {
                    bestStops = stops
                    bestVehicles = vehicles
                    bestGroupName = candidateName
                    bestOwnerCode = candidateOwner
                    chosenPlanningNumber = planNum
                    break
                } else if (bestGroupName.isBlank()) {
                    bestGroupName = candidateName
                    bestOwnerCode = candidateOwner
                    chosenPlanningNumber = planNum
                }
            }

            // Corridor Fallback: If Network stops are empty (e.g. line 154 has no runs today on weekend),
            // look for a corridor line in the catalog sharing the same LineName (such as line 156 / 22156)
            if (bestStops.isEmpty() && bestGroupName.isNotBlank()) {
                val corridorCandidate = catalog.firstOrNull { entry ->
                    entry.name.equals(bestGroupName, ignoreCase = true) &&
                        entry.planningNumber != chosenPlanningNumber
                }
                if (corridorCandidate != null) {
                    val (cStops, cVehicles) = fetchBothDirections(
                        planningNumber = corridorCandidate.planningNumber,
                        candidateOwner = corridorCandidate.ownerCode.takeIf { it.isNotBlank() && it != "NL" }
                            ?: if (isBrabantContext) "ARR" else "",
                        isBrabant = isBrabantContext,
                        cleanLine = cleanLine
                    )
                    if (cStops.isNotEmpty()) {
                        bestStops = cStops
                        if (bestVehicles.isEmpty()) {
                            bestVehicles = cVehicles
                        }
                        RawConsoleLogger.info("OVapi", "Corridor-haltes geleend van ${corridorCandidate.publicNumber} (${corridorCandidate.planningNumber})")
                    }
                }
            }

            // Nearest stop calculation if user GPS is available
            var nearestStop: BusStopInfo? = null
            var nearestDistMeters: Int? = null
            if (userLat != null && userLon != null && bestStops.isNotEmpty()) {
                val pair = bestStops
                    .map { stop -> stop to haversineMeters(userLat, userLon, stop.latitude, stop.longitude) }
                    .minByOrNull { it.second }
                if (pair != null) {
                    nearestStop = pair.first
                    nearestDistMeters = pair.second.roundToInt()
                    val enriched = enrichStopWithTpcDepartures(pair.first, cleanLine)
                    if (enriched != null) {
                        nearestStop = enriched
                        bestStops = bestStops.map {
                            if (it.timingPointCode == enriched.timingPointCode) enriched else it
                        }
                    }
                }
            }

            val updatedStamp = SimpleDateFormat("HH:mm:ss", Locale("nl", "NL")).format(Date())

            // Fallback & Empty-Schedule Handling:
            // If Actuals is empty (e.g. late at night or on weekends when line isn't running),
            // still render route stops from Network and set clean Dutch status message without error card!
            val statusNotice = if (bestVehicles.isEmpty()) {
                "Geen actieve ritten op dit moment (bijv. avond/weekend)"
            } else {
                null
            }

            val displayRouteName = if (bestGroupName.isNotBlank()) {
                if (bestOwnerCode.isNotBlank()) "$bestGroupName ($bestOwnerCode)" else bestGroupName
            } else {
                "Buslijn $cleanLine"
            }

            BusTrackingState(
                lineNumber = cleanLine,
                routeName = displayRouteName,
                stops = bestStops,
                liveVehicles = bestVehicles,
                nearestStop = nearestStop,
                nearestDistanceMeters = nearestDistMeters,
                userLat = userLat,
                userLon = userLon,
                lastUpdatedLabel = updatedStamp,
                isLoading = false,
                errorMessage = null,
                statusNotice = statusNotice
            )
        } catch (e: Exception) {
            RawConsoleLogger.warn("OVapi", "Bustijd fout: ${e.message}")
            BusTrackingState(
                lineNumber = cleanLine,
                errorMessage = "Kon busgegevens niet ophalen (${e.message ?: "netwerkfout"})."
            )
        }
    }

    private fun fetchBothDirections(
        planningNumber: String,
        candidateOwner: String,
        isBrabant: Boolean,
        cleanLine: String
    ): Pair<List<BusStopInfo>, List<BusVehiclePoint>> {
        val operatorsToTry = buildList {
            if (candidateOwner.isNotBlank() && candidateOwner != "NL") add(candidateOwner)
            if (isBrabant) add("ARR")
            addAll(listOf("ARR", "CXX", "EBS", "QBU", "GVB", "RET", "HTM", "KEOL", "HERMES", "SYNT", "ALLGO", "NL"))
        }.distinct()

        for (op in operatorsToTry) {
            val key1 = "${op}_${planningNumber}_1"
            val key2 = "${op}_${planningNumber}_2"
            val url = "https://v0.ovapi.nl/line/$key1,$key2"

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AgendaPlus-Android/1.0")
                    .header("Accept", "application/json")
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: continue
                RawConsoleLogger.http("OVapi", "GET $url -> ${resp.code}")
                if (!resp.isSuccessful || !body.trimStart().startsWith("{")) continue

                val root = JSONObject(body)
                val dir1Obj = root.optJSONObject(key1)
                val dir2Obj = root.optJSONObject(key2)

                if (dir1Obj == null && dir2Obj == null) continue

                val allStops = mutableListOf<BusStopInfo>()
                val vehicles = mutableListOf<BusVehiclePoint>()
                val actualsByTpc = mutableMapOf<String, JSONObject>()

                // Helper to parse direction object
                fun parseDirection(dirObj: JSONObject?, dirIndex: Int) {
                    if (dirObj == null) return

                    // Parse Actuals
                    val actualsObj = dirObj.optJSONObject("Actuals")
                    if (actualsObj != null) {
                        val aKeys = actualsObj.keys()
                        while (aKeys.hasNext()) {
                            val ak = aKeys.next()
                            val pass = actualsObj.optJSONObject(ak) ?: continue
                            val tpc = pass.optString("TimingPointCode").cleanSchoolText()
                            if (tpc != null && !actualsByTpc.containsKey(tpc)) {
                                actualsByTpc[tpc] = pass
                            }
                            val vLat = pass.optDouble("Latitude", Double.NaN)
                            val vLon = pass.optDouble("Longitude", Double.NaN)
                            if (!vLat.isNaN() && !vLon.isNaN() && vLat != 0.0 && vLon != 0.0) {
                                val expectedRaw = pass.optString("ExpectedDepartureTime")
                                    .ifBlank { pass.optString("ExpectedArrivalTime") }
                                val hhmm = extractTimeHHmm(expectedRaw) ?: "--:--"
                                vehicles.add(
                                    BusVehiclePoint(
                                        id = ak,
                                        lineNumber = cleanLine,
                                        destination = pass.optString("DestinationName50").cleanSchoolText()
                                            ?: pass.optString("TimingPointName").cleanSchoolText() ?: "Lijn $cleanLine",
                                        latitude = vLat,
                                        longitude = vLon,
                                        stopName = pass.optString("TimingPointName").cleanSchoolText() ?: "Onderweg",
                                        expectedTime = hhmm
                                    )
                                )
                            }
                        }
                    }

                    // Parse Network stops
                    val networkObj = dirObj.optJSONObject("Network")
                    if (networkObj != null) {
                        val nKeys = networkObj.keys()
                        val dirStops = mutableListOf<Pair<Int, BusStopInfo>>()
                        while (nKeys.hasNext()) {
                            val nk = nKeys.next()
                            val stopGroup = networkObj.optJSONObject(nk) ?: continue
                            val candidateStops = if (stopGroup.has("Latitude") && stopGroup.has("Longitude")) {
                                listOf(stopGroup)
                            } else {
                                stopGroup.keys().asSequence().mapNotNull { stopGroup.optJSONObject(it) }.toList()
                            }
                            for (so in candidateStops) {
                                val lat = so.optDouble("Latitude", Double.NaN)
                                val lon = so.optDouble("Longitude", Double.NaN)
                                if (lat.isNaN() || lon.isNaN() || lat == 0.0) continue
                                val tpc = so.optString("TimingPointCode").cleanSchoolText() ?: nk
                                val order = so.optInt("UserStopOrderNumber", dirStops.size + 1)
                                val pass = actualsByTpc[tpc]
                                val expTime = extractTimeHHmm(
                                    pass?.optString("ExpectedDepartureTime")
                                        ?: pass?.optString("ExpectedArrivalTime")
                                )
                                val schedTime = extractTimeHHmm(
                                    pass?.optString("TargetDepartureTime")
                                        ?: pass?.optString("TargetArrivalTime")
                                )
                                dirStops.add(
                                    order to BusStopInfo(
                                        timingPointCode = tpc,
                                        name = so.optString("TimingPointName").cleanSchoolText() ?: "Halte $tpc",
                                        town = so.optString("TimingPointTown").cleanSchoolText() ?: "",
                                        latitude = lat,
                                        longitude = lon,
                                        expectedArrival = expTime,
                                        scheduledArrival = schedTime,
                                        destination = pass?.optString("DestinationName50").cleanSchoolText(),
                                        isRealtime = expTime != null,
                                        delayMinutes = computeDelayMinutes(schedTime, expTime)
                                    )
                                )
                            }
                        }
                        val sortedDirStops = dirStops.sortedBy { it.first }.map { it.second }
                        allStops.addAll(sortedDirStops)
                    }
                }

                parseDirection(dir1Obj, 1)
                parseDirection(dir2Obj, 2)

                val uniqueStops = allStops.distinctBy { it.timingPointCode }
                val uniqueVehicles = vehicles.distinctBy { it.latitude to it.longitude }

                if (uniqueStops.isNotEmpty() || uniqueVehicles.isNotEmpty()) {
                    return uniqueStops to uniqueVehicles
                }
            } catch (e: Exception) {
                RawConsoleLogger.warn("OVapi", "Fout bij ophalen $op ($planningNumber): ${e.message}")
            }
        }
        return emptyList<BusStopInfo>() to emptyList()
    }

    private fun getOrFetchLineCatalog(): List<LineCatalogEntry> {
        val now = System.currentTimeMillis()
        val maxAgeMs = 24 * 60 * 60 * 1000L // 24 hours

        // 1. In-memory cache
        cachedLineCatalog?.let {
            if (it.isNotEmpty() && (now - cachedCatalogTimestamp < maxAgeMs)) {
                return it
            }
        }

        // 2. SharedPreferences cache
        val (savedJson, savedTimestamp) = prefs?.getOvapiCatalog() ?: Pair(null, 0L)
        if (!savedJson.isNullOrBlank() && (now - savedTimestamp < maxAgeMs)) {
            val parsed = parseCatalogJson(savedJson)
            if (parsed.isNotEmpty()) {
                cachedLineCatalog = parsed
                cachedCatalogTimestamp = savedTimestamp
                return parsed
            }
        }

        // 3. Network fetch via HTTPS with custom headers
        try {
            val req = Request.Builder()
                .url("https://v0.ovapi.nl/line/")
                .header("User-Agent", "AgendaPlus-Android/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            RawConsoleLogger.http("OVapi", "GET https://v0.ovapi.nl/line/ -> ${resp.code} (${body.length}b)")
            if (resp.isSuccessful && body.trimStart().startsWith("{")) {
                val parsed = parseCatalogJson(body)
                if (parsed.isNotEmpty()) {
                    cachedLineCatalog = parsed
                    cachedCatalogTimestamp = now
                    prefs?.saveOvapiCatalog(body, now)
                    return parsed
                }
            }
        } catch (e: Exception) {
            RawConsoleLogger.warn("OVapi", "Catalogus ophalen mislukt: ${e.message}")
        }

        // 4. Stale cache fallback if network was unavailable
        if (!savedJson.isNullOrBlank()) {
            val parsed = parseCatalogJson(savedJson)
            if (parsed.isNotEmpty()) {
                cachedLineCatalog = parsed
                cachedCatalogTimestamp = savedTimestamp
                return parsed
            }
        }

        return emptyList()
    }

    private fun parseCatalogJson(jsonStr: String): List<LineCatalogEntry> {
        return try {
            val root = JSONObject(jsonStr)
            val list = mutableListOf<LineCatalogEntry>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val pubNum = obj.optString("LinePublicNumber").cleanSchoolText() ?: continue
                val planNum = obj.optString("LinePlanningNumber").cleanSchoolText()
                    ?: key.substringAfter('_').substringBeforeLast('_')
                val dir = obj.optInt("LineDirection", 1)
                list.add(
                    LineCatalogEntry(
                        key = key,
                        publicNumber = pubNum,
                        planningNumber = planNum,
                        direction = dir,
                        name = obj.optString("LineName").cleanSchoolText() ?: "",
                        destination = obj.optString("DestinationName50").cleanSchoolText() ?: "",
                        transportType = obj.optString("TransportType").cleanSchoolText() ?: "BUS",
                        ownerCode = obj.optString("DataOwnerCode").cleanSchoolText() ?: ""
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun enrichStopWithTpcDepartures(stop: BusStopInfo, lineNumber: String): BusStopInfo? {
        val encodedTpc = URLEncoder.encode(stop.timingPointCode, "UTF-8")
        val url = "https://v0.ovapi.nl/tpc/$encodedTpc"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AgendaPlus-Android/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return null
            if (!resp.isSuccessful) return null

            val root = JSONObject(body)
            val tpcObj = root.optJSONObject(stop.timingPointCode) ?: return null
            val passes = tpcObj.optJSONObject("Passes") ?: return null

            var bestExpected: String? = null
            var bestScheduled: String? = null
            var bestDest: String? = null

            val pKeys = passes.keys()
            while (pKeys.hasNext()) {
                val pk = pKeys.next()
                val pass = passes.optJSONObject(pk) ?: continue
                val pubNum = pass.optString("LinePublicNumber").cleanSchoolText() ?: ""
                if (!pubNum.equals(lineNumber, ignoreCase = true)) continue
                val exp = extractTimeHHmm(
                    pass.optString("ExpectedDepartureTime").ifBlank { pass.optString("ExpectedArrivalTime") }
                ) ?: continue
                val sched = extractTimeHHmm(
                    pass.optString("TargetDepartureTime").ifBlank { pass.optString("TargetArrivalTime") }
                )
                if (bestExpected == null || exp < bestExpected) {
                    bestExpected = exp
                    bestScheduled = sched
                    bestDest = pass.optString("DestinationName50").cleanSchoolText()
                }
            }

            if (bestExpected != null) {
                return stop.copy(
                    expectedArrival = bestExpected,
                    scheduledArrival = bestScheduled,
                    destination = bestDest ?: stop.destination,
                    isRealtime = true,
                    delayMinutes = computeDelayMinutes(bestScheduled, bestExpected)
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun extractTimeHHmm(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val tIndex = iso.indexOf('T')
        if (tIndex >= 0 && iso.length >= tIndex + 6) {
            return iso.substring(tIndex + 1, tIndex + 6)
        }
        return null
    }

    private fun computeDelayMinutes(scheduled: String?, expected: String?): Int {
        if (scheduled.isNullOrBlank() || expected.isNullOrBlank()) return 0
        val sParts = scheduled.split(":")
        val eParts = expected.split(":")
        val sMin = (sParts.getOrNull(0)?.toIntOrNull() ?: return 0) * 60 + (sParts.getOrNull(1)?.toIntOrNull() ?: return 0)
        val eMin = (eParts.getOrNull(0)?.toIntOrNull() ?: return 0) * 60 + (eParts.getOrNull(1)?.toIntOrNull() ?: return 0)
        return (eMin - sMin).coerceIn(-10, 60)
    }

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            val c = 2 * asin(sqrt(a))
            return r * c
        }
    }
}
