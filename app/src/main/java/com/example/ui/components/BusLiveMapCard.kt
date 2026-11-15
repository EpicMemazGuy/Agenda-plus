package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.model.BusTrackingState
import com.example.ui.theme.LocalDarkThemeActive
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun BusLiveMapCard(
    busTrackingState: BusTrackingState,
    savedBusLine: String,
    isOffline: Boolean,
    onSetBusLine: (line: String, userLat: Double?, userLon: Double?) -> Unit,
    onRefreshBus: (userLat: Double?, userLon: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85
    val isDarkMap = nothingMode || LocalDarkThemeActive.current
    var lineInput by remember(savedBusLine) { mutableStateOf(savedBusLine) }
    var isEditingLine by remember(savedBusLine) { mutableStateOf(savedBusLine.isBlank()) }
    var userLat by remember { mutableStateOf(busTrackingState.userLat) }
    var userLon by remember { mutableStateOf(busTrackingState.userLon) }
    var locationNotice by remember { mutableStateOf<String?>(null) }

    fun fetchDeviceLocationAndTrack(targetLine: String?) {
        resolveCurrentLocation(
            context = context,
            onResult = { lat, lon ->
                userLat = lat
                userLon = lon
                locationNotice = null
                if (!targetLine.isNullOrBlank()) {
                    onSetBusLine(targetLine, lat, lon)
                } else {
                    onRefreshBus(lat, lon)
                }
            },
            onUnavailable = {
                locationNotice = "GPS-locatie kon niet direct worden bepaald. Controleer je locatieservices."
                if (!targetLine.isNullOrBlank()) {
                    onSetBusLine(targetLine, userLat, userLon)
                } else {
                    onRefreshBus(userLat, userLon)
                }
            }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocationAndTrack(lineInput.trim().takeIf { it.isNotBlank() })
        } else {
            locationNotice = "Locatietoestemming geweigerd. De kaart toont wel de live buslijn."
        }
    }

    fun requestGpsAndRefresh() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchDeviceLocationAndTrack(null)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bus_live_map_card"),
        shape = appCardShape(standard = 22.dp, nothing = 24.dp),
        border = nothingCardBorder(isAlert = busTrackingState.errorMessage != null),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "Bustijd",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (savedBusLine.isNotBlank()) "Bustijd • Lijn $savedBusLine" else "Bustijd & Kaart",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = busTrackingState.routeName.ifBlank {
                                "Live OVapi halte- en buslocaties (NL)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEditingLine = !isEditingLine },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bus_edit_line_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Buslijn aanpassen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onRefreshBus(userLat, userLon) },
                        enabled = savedBusLine.isNotBlank() && !busTrackingState.isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bus_refresh_button")
                    ) {
                        if (busTrackingState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Bustijd verversen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Bus line input bar
            if (isEditingLine || savedBusLine.isBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = lineInput,
                        onValueChange = { lineInput = it },
                        label = { Text("Buslijn (bijv. 156 of 300)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bus_line_input")
                    )
                    Button(
                        onClick = {
                            isEditingLine = false
                            onSetBusLine(lineInput, userLat, userLon)
                        },
                        enabled = lineInput.isNotBlank() && !isOffline,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("bus_line_save_button")
                    ) {
                        Text("Volgen")
                    }
                }
            }

            // GPS / Nearest Stop Action Button
            if (savedBusLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { requestGpsAndRefresh() },
                    enabled = !isOffline,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bus_gps_nearest_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (userLat != null) "Locatie & dichtstbijzijnde halte bijwerken" else "Mijn locatie & dichtstbijzijnde halte"
                    )
                }
            }

            locationNotice?.let { notice ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            busTrackingState.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            busTrackingState.statusNotice?.let { notice ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bus_status_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Nearest stop highlight + optional walking route
            busTrackingState.nearestStop?.let { nearest ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bus_nearest_stop_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dichtstbijzijnde halte",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = nearest.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val distLabel = busTrackingState.nearestDistanceMeters?.let { m ->
                                    if (m >= 1000) String.format("%.1f km lopen", m / 1000.0) else "$m meter lopen"
                                } ?: ""
                                val etaLabel = nearest.expectedArrival?.let { " • Verwacht: $it" } ?: ""
                                val delayLabel = if (nearest.delayMinutes > 0) " (+${nearest.delayMinutes} min)" else ""
                                Text(
                                    text = "$distLabel$etaLabel$delayLabel".trimStart(' ', '•', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    openWalkingDirectionsInGoogleMaps(
                                        context = context,
                                        lat = nearest.latitude,
                                        lon = nearest.longitude,
                                        label = nearest.name
                                    )
                                },
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier.testTag("bus_walk_to_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsWalk,
                                    contentDescription = "Looproute",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Looproute")
                            }
                        }
                    }
                }
            }

            // Interactive OpenStreetMap Leaflet Map
            if (busTrackingState.stops.isNotEmpty() || busTrackingState.liveVehicles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val mapHtml = remember(
                    busTrackingState.stops,
                    busTrackingState.liveVehicles,
                    busTrackingState.nearestStop,
                    userLat,
                    userLon,
                    nothingMode,
                    isDarkMap
                ) {
                    buildLeafletMapHtml(
                        state = busTrackingState,
                        userLat = userLat,
                        userLon = userLon,
                        isNothingOs = nothingMode,
                        isDarkMap = isDarkMap
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("bus_osm_webview_container")
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.userAgentString = "AgendaPlus-Android/1.0 (contact@agendaplus.nl)"
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.postDelayed({
                                            view.evaluateJavascript("if (typeof map !== 'undefined' && map) { map.invalidateSize(true); }", null)
                                        }, 100)
                                        view?.postDelayed({
                                            view.evaluateJavascript("if (typeof map !== 'undefined' && map) { map.invalidateSize(true); }", null)
                                        }, 400)
                                    }
                                }
                                setOnTouchListener { view, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                            view.parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            view.parent?.requestDisallowInterceptTouchEvent(false)
                                        }
                                    }
                                    false
                                }
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                "https://agendaplus.local/",
                                mapHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                            webView.postDelayed({
                                webView.evaluateJavascript("if (typeof map !== 'undefined' && map) { map.invalidateSize(true); }", null)
                            }, 200)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live arrivals summary chips
                val upcomingStops = busTrackingState.stops
                    .filter { it.expectedArrival != null }
                    .ifEmpty { busTrackingState.stops.take(4) }
                    .take(4)
                if (upcomingStops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (busTrackingState.lastUpdatedLabel.isNotBlank()) {
                            "Haltes & live tijden (${busTrackingState.lastUpdatedLabel})"
                        } else {
                            "Haltes op deze lijn"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        upcomingStops.forEach { stop ->
                            Surface(
                                shape = RoundedCornerShape(if (oneUiMode) 18.dp else 12.dp),
                                color = if (oneUiMode) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (stop.timingPointCode == busTrackingState.nearestStop?.timingPointCode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = stop.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val displayTown = stop.town.trim()
                                            if (displayTown.isNotBlank() &&
                                                !displayTown.equals("unknown", ignoreCase = true) &&
                                                !displayTown.equals("null", ignoreCase = true) &&
                                                !displayTown.equals("onbekend", ignoreCase = true)
                                            ) {
                                                Text(
                                                    text = displayTown,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    val etaText = stop.expectedArrival ?: stop.scheduledArrival ?: "Gepland"
                                    if (oneUiMode) {
                                        OneUiPillBadge(
                                            text = etaText,
                                            containerColor = if (stop.isRealtime) {
                                                oneUiPillContainerColor(isError = false)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            contentColor = if (stop.isRealtime) {
                                                oneUiPillContentColor(isError = false)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    } else {
                                        Text(
                                            text = etaText,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (stop.isRealtime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openWalkingDirectionsInGoogleMaps(
    context: Context,
    lat: Double,
    lon: Double,
    label: String
) {
    val gmmUri = Uri.parse("google.navigation:q=$lat,$lon&mode=w")
    val mapsIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(mapsIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=walking&query=${Uri.encode(label)}"
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun resolveCurrentLocation(
    context: Context,
    onResult: (Double, Double) -> Unit,
    onUnavailable: () -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onUnavailable()
        return
    }
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    var bestLast: Location? = null
    for (p in providers) {
        val loc = runCatching { locationManager.getLastKnownLocation(p) }.getOrNull()
        if (loc != null && (bestLast == null || loc.time > bestLast.time)) {
            bestLast = loc
        }
    }
    if (bestLast != null) {
        onResult(bestLast.latitude, bestLast.longitude)
        return
    }

    val activeProvider = providers.firstOrNull {
        runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
    }
    if (activeProvider == null) {
        onUnavailable()
        return
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            runCatching { locationManager.removeUpdates(this) }
            onResult(location.latitude, location.longitude)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    try {
        locationManager.requestLocationUpdates(activeProvider, 0L, 0f, listener, Looper.getMainLooper())
    } catch (_: Exception) {
        onUnavailable()
    }
}

private fun buildLeafletMapHtml(
    state: BusTrackingState,
    userLat: Double?,
    userLon: Double?,
    isNothingOs: Boolean,
    isDarkMap: Boolean
): String {
    val stopsJson = JSONArray()
    state.stops.forEach { stop ->
        stopsJson.put(
            JSONObject().apply {
                put("name", stop.name)
                put("lat", stop.latitude)
                put("lon", stop.longitude)
                put("eta", stop.expectedArrival ?: stop.scheduledArrival ?: "")
                put("isNearest", stop.timingPointCode == state.nearestStop?.timingPointCode)
            }
        )
    }

    val busesJson = JSONArray()
    state.liveVehicles.forEach { bus ->
        busesJson.put(
            JSONObject().apply {
                put("line", bus.lineNumber)
                put("dest", bus.destination)
                put("stop", bus.stopName)
                put("eta", bus.expectedTime)
                put("lat", bus.latitude)
                put("lon", bus.longitude)
            }
        )
    }

    val userJson = if (userLat != null && userLon != null) {
        JSONObject().apply {
            put("lat", userLat)
            put("lon", userLon)
        }.toString()
    } else {
        "null"
    }

    val nearestJson = state.nearestStop?.let {
        JSONObject().apply {
            put("lat", it.latitude)
            put("lon", it.longitude)
            put("name", it.name)
        }.toString()
    } ?: "null"

    val tileFilterCss = if (isDarkMap) {
        """
            .leaflet-tile-pane { filter: invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%); }
            .leaflet-control-attribution { background: rgba(15, 15, 18, 0.72) !important; color: #B4B8C2 !important; }
            .leaflet-control-attribution a { color: #E2E8F0 !important; }
        """.trimIndent()
    } else ""

    val bgCss = when {
        isNothingOs -> "#000000"
        isDarkMap -> "#111318"
        else -> "#ECEFF4"
    }
    val lineColor = when {
        isNothingOs -> "#FFFFFF"
        isDarkMap -> "#60A5FA"
        else -> "#1D4ED8"
    }
    val busColor = if (isNothingOs) "#D71921" else "#DC2626"

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: $bgCss; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
            #map { width: 100%; height: 100%; min-height: 240px; background: $bgCss; }
            .leaflet-container { background: $bgCss !important; }
            $tileFilterCss
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var stops = $stopsJson;
            var buses = $busesJson;
            var userPos = $userJson;
            var nearestStop = $nearestJson;

            var centerLat = userPos ? userPos.lat : (buses.length > 0 ? buses[0].lat : (stops.length > 0 ? stops[0].lat : 52.0907));
            var centerLon = userPos ? userPos.lon : (buses.length > 0 ? buses[0].lon : (stops.length > 0 ? stops[0].lon : 5.1214));

            var map = L.map('map', { zoomControl: false }).setView([centerLat, centerLon], 13);

            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }).addTo(map);

            // Invalidate size immediately, and at intervals to ensure complete render without black box
            setTimeout(function() { if (map) map.invalidateSize(true); }, 50);
            setTimeout(function() { if (map) map.invalidateSize(true); }, 200);
            setTimeout(function() { if (map) map.invalidateSize(true); }, 500);
            window.addEventListener('resize', function() { if (map) map.invalidateSize(true); });

            var bounds = [];
            var routeCoords = [];
            stops.forEach(function(s) {
              routeCoords.push([s.lat, s.lon]);
              bounds.push([s.lat, s.lon]);
              var marker = L.circleMarker([s.lat, s.lon], {
                radius: s.isNearest ? 8 : 5,
                color: s.isNearest ? '$busColor' : '$lineColor',
                fillColor: s.isNearest ? '$busColor' : '#FFFFFF',
                fillOpacity: 0.95,
                weight: 2
              }).addTo(map);
              var label = '<b>' + s.name + '</b>' + (s.eta ? '<br>Verwacht: ' + s.eta : '');
              marker.bindPopup(label);
            });

            if (routeCoords.length > 1) {
              L.polyline(routeCoords, { color: '$lineColor', weight: 3, opacity: 0.65 }).addTo(map);
            }

            buses.forEach(function(b) {
              bounds.push([b.lat, b.lon]);
              var busIcon = L.divIcon({
                className: 'bus-marker',
                html: '<div style="background:$busColor;color:#FFF;border:2px solid #FFF;border-radius:999px;padding:2px 7px;font-weight:bold;font-size:11px;white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,0.4);">BUS ' + b.line + '</div>',
                iconSize: [54, 22],
                iconAnchor: [27, 11]
              });
              L.marker([b.lat, b.lon], { icon: busIcon })
                .addTo(map)
                .bindPopup('<b>Lijn ' + b.line + '</b><br>' + b.dest + '<br>Bij halte: ' + b.stop + ' (' + b.eta + ')');
            });

            if (userPos) {
              bounds.push([userPos.lat, userPos.lon]);
              L.circleMarker([userPos.lat, userPos.lon], {
                radius: 7,
                color: '#FFFFFF',
                fillColor: '#2563EB',
                fillOpacity: 1,
                weight: 3
              }).addTo(map).bindPopup('<b>Jouw locatie</b>');

              if (nearestStop) {
                L.polyline([[userPos.lat, userPos.lon], [nearestStop.lat, nearestStop.lon]], {
                  color: '$busColor',
                  weight: 3,
                  dashArray: '6, 6'
                }).addTo(map);
              }
            }

            if (bounds.length > 1) {
              map.fitBounds(bounds, { padding: [24, 24], maxZoom: 15 });
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
