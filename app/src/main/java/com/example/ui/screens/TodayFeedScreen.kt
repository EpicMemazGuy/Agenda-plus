package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.network.CitySearchResult
import com.example.ui.components.BusLiveMapCard
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.NothingDotMatrixSpinner
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.ScheduleLoadingSkeleton
import com.example.ui.components.appCardShape
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.oneUiPillContainerColor
import com.example.ui.components.oneUiPillContentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TodayFeedScreen(
    appointments: List<Appointment>,
    tasks: List<HomeworkTask>,
    kwtSlots: List<KwtSlot>,
    weatherInfo: WeatherInfo?,
    isWeatherLoading: Boolean,
    agendaStyle: AgendaStyle,
    isRefreshing: Boolean,
    isOffline: Boolean,
    navPromptDismissed: Boolean,
    todayBlocks: List<TodayBlockConfig>,
    schoolAddress: SchoolAddress?,
    savedBusLine: String,
    busTrackingState: BusTrackingState,
    onNavigateToRooster: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToKwt: () -> Unit,
    onOpenElo: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSearchCities: suspend (String) -> List<CitySearchResult>,
    onSelectWeatherCity: (String, Double, Double) -> Unit,
    onToggleTask: (String) -> Unit,
    onDismissNavPrompt: () -> Unit,
    onOpenTodayModifySheet: () -> Unit,
    onSetBusLine: (String, Double?, Double?) -> Unit,
    onRefreshBusTracking: (Double?, Double?) -> Unit,
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val oneUiMode = isOneUi85
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 }
    }
    LaunchedEffect(isScrolled, oneUiMode) {
        onHeaderCollapsedChange(oneUiMode && isScrolled)
    }
    var showCityPickerDialog by remember { mutableStateOf(false) }

    val todayContext = remember(appointments) {
        val cal = java.util.Calendar.getInstance()
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val hhmmFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val todayIso = isoFormat.format(cal.time)
        val nowHHmm = hhmmFormat.format(cal.time)

        val dayName = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "Maandag"
            java.util.Calendar.TUESDAY -> "Dinsdag"
            java.util.Calendar.WEDNESDAY -> "Woensdag"
            java.util.Calendar.THURSDAY -> "Donderdag"
            java.util.Calendar.FRIDAY -> "Vrijdag"
            java.util.Calendar.SATURDAY -> "Zaterdag"
            else -> "Zondag"
        }
        val dayNum = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }

        val hasIso = appointments.any { it.dateIso.isNotBlank() }
        val todayList = if (hasIso) {
            appointments.filter { it.dateIso == todayIso }.sortedWith(compareBy({ it.periodIndex }, { it.startTime }))
        } else {
            appointments.filter { it.dayOfWeek == dayNum }.sortedWith(compareBy({ it.periodIndex }, { it.startTime }))
        }

        val upcomingToday = todayList.firstOrNull { !it.isCanceled && it.endTime > nowHHmm }
        val nextScheduled = upcomingToday ?: if (hasIso) {
            appointments
                .filter { !it.isCanceled && it.dateIso >= todayIso }
                .sortedWith(compareBy({ it.dateIso }, { it.startTime }))
                .firstOrNull { it.dateIso > todayIso || it.endTime > nowHHmm }
        } else null

        Triple(dayName, todayList, nextScheduled)
    }

    val dayNameDutch = todayContext.first
    val todayClasses = todayContext.second
    val nextClass = todayContext.third

    val activeLessonCount = todayClasses.count { !it.isCanceled }
    val endTimeSummary = todayClasses.lastOrNull { !it.isCanceled }?.endTime
        ?: todayClasses.lastOrNull()?.endTime

    var statusBadgeText by remember { mutableStateOf("") }
    LaunchedEffect(nextClass) {
        while (true) {
            if (nextClass != null) {
                val cal = java.util.Calendar.getInstance()
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val todayIso = isoFormat.format(cal.time)
                val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(cal.time)

                statusBadgeText = when {
                    nextClass.dateIso.isNotBlank() && nextClass.dateIso != todayIso ->
                        "Volgende les (${nextClass.dateIso})"
                    hhmm >= nextClass.startTime && hhmm < nextClass.endTime ->
                        "Nu bezig tot ${nextClass.endTime}"
                    else -> {
                        val parts = nextClass.startTime.split(":")
                        val targetMin = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                        val diffMin = (targetMin - nowMin).coerceAtLeast(0)
                        if (diffMin >= 60) {
                            "Over ${diffMin / 60}u ${diffMin % 60}m"
                        } else {
                            val secLeft = (59 - cal.get(java.util.Calendar.SECOND)).coerceIn(0, 59)
                            "Begint over ${diffMin}m ${String.format("%02d", secLeft)}s"
                        }
                    }
                }
            }
            delay(1000L)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("today_feed_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        if (oneUiMode) {
            item(key = "oneui_today_header") {
                OneUiExpandingHeader(
                    title = "Vandaag",
                    subtitle = "$dayNameDutch • $activeLessonCount ${if (activeLessonCount == 1) "lesuur" else "lesuren"}",
                    isScrolled = isScrolled
                )
            }
        }

        // 0a. Top dismissible Navigation Pop-Up ("Wil je ook navigatie?")
        if (!navPromptDismissed) {
            item(key = "nav_prompt_banner") {
                NavigationSetupPromptBanner(
                    onCustomizeNow = onOpenTodayModifySheet,
                    onDismiss = onDismissNavPrompt
                )
            }
        }

        // 0b. Offline banner when Offline-modus or no network
        if (isOffline) {
            item(key = "offline_banner") {
                Surface(
                    shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = nothingCardBorder(isAlert = true),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_offline_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline-modus actief • Opgeslagen gegevens worden getoond.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 1. Dynamic Configurable Blocks on Vandaag
        todayBlocks.filter { it.enabled }.forEach { block ->
            when (block.type) {
                TodayBlockType.WEATHER -> {
                    item(key = "block_weather") {
                        LocalWeatherHeaderCard(
                            weatherInfo = weatherInfo,
                            isLoading = isWeatherLoading,
                            onRefresh = onRefreshWeather,
                            onChangeCity = { showCityPickerDialog = true }
                        )
                    }
                }

                TodayBlockType.NEXT_LESSON -> {
                    if (isRefreshing && appointments.isEmpty()) {
                        item(key = "block_next_lesson_skeleton") {
                            ScheduleLoadingSkeleton(
                                agendaStyle = agendaStyle,
                                statusLabel = "Rooster en huiswerk ophalen..."
                            )
                        }
                    } else {
                        item(key = "block_next_lesson") {
                            if (nextClass != null) {
                                HeroClassCard(
                                    appointment = nextClass,
                                    statusLabel = statusBadgeText.ifBlank { "${nextClass.startTime} - ${nextClass.endTime}" },
                                    onCardClick = onNavigateToRooster
                                )
                            } else {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("hero_empty_card"),
                                    shape = appCardShape(standard = 24.dp, nothing = 24.dp),
                                    border = nothingCardBorder(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.EventAvailable,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Geen actieve les op dit moment",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Druk op verversen rechtsboven om je rooster bij te werken.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TodayBlockType.KWT_ALERT -> {
                    val hasKwt = kwtSlots.isNotEmpty() || appointments.any { it.isKwt }
                    if (hasKwt) {
                        item(key = "block_kwt_alert") {
                            val openChoices = kwtSlots.count { slot -> slot.options.none { it.isEnrolled } }
                            Surface(
                                shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = nothingCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(appCardShape(standard = 20.dp, nothing = 24.dp))
                                    .clickable(onClick = onNavigateToKwt)
                                    .testTag("today_kwt_alert_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.FactCheck,
                                                contentDescription = "Keuzelessen",
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Keuzeles (KWT) beschikbaar",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (openChoices > 0) "$openChoices openstaande KWT-blok(ken)" else "Bekijk of wijzig je gekozen KWT-uur",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onNavigateToKwt,
                                        shape = RoundedCornerShape(percent = 50),
                                        modifier = Modifier.testTag("today_kwt_choose_button")
                                    ) {
                                        Text("Kiezen")
                                    }
                                }
                            }
                        }
                    }
                }

                TodayBlockType.SCHOOL_NAV -> {
                    item(key = "block_school_nav") {
                        SchoolNavigationCard(
                            schoolAddress = schoolAddress,
                            onLaunchMaps = { addr -> launchSchoolNavigation(context, addr) },
                            onConfigureAddress = onOpenTodayModifySheet
                        )
                    }
                }

                TodayBlockType.BUS_TIME -> {
                    item(key = "block_bus_time") {
                        BusLiveMapCard(
                            busTrackingState = busTrackingState,
                            savedBusLine = savedBusLine,
                            isOffline = isOffline,
                            onSetBusLine = onSetBusLine,
                            onRefreshBus = onRefreshBusTracking
                        )
                    }
                }

                TodayBlockType.QUICK_ACTIONS -> {
                    item(key = "block_quick_actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(appCardShape(standard = 20.dp, nothing = 24.dp))
                                    .clickable(onClick = onOpenElo)
                                    .testTag("quick_elo_button"),
                                shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                                border = nothingCardBorder(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = "ELO itslearning",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "ELO-portaal",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "itslearning",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(appCardShape(standard = 20.dp, nothing = 24.dp))
                                    .clickable(onClick = onNavigateToRooster)
                                    .testTag("quick_view_rooster_button"),
                                shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                                border = nothingCardBorder(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Dagoverzicht",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "$activeLessonCount ${if (activeLessonCount == 1) "lesuur" else "lesuren"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (endTimeSummary != null) "Tot $endTimeSummary" else "Vandaag vrij",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TodayBlockType.HOMEWORK -> {
                    item(key = "block_homework_header") {
                        val openCount = tasks.count { !it.isCompleted }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (openCount > 0) "Huiswerk ($openCount)" else "Huiswerk",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("today_huiswerk_header")
                                )
                            }
                            TextButton(
                                onClick = onNavigateToTasks,
                                modifier = Modifier.testTag("view_all_tasks_button")
                            ) {
                                Text("Alles bekijken")
                            }
                        }
                    }

                    val urgentTasks = tasks
                        .sortedBy { it.isCompleted }
                        .take(3)
                    if (urgentTasks.isEmpty()) {
                        item(key = "block_homework_empty") {
                            Surface(
                                shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = nothingCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("empty_urgent_tasks_card")
                            ) {
                                Text(
                                    text = "Geen openstaand huiswerk geregistreerd.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(urgentTasks, key = { "today_hw_${it.id}" }) { task ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("today_task_item_${task.id}"),
                                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                                border = nothingCardBorder(isAlert = task.priority == TaskPriority.HIGH && !task.isCompleted),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onToggleTask(task.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("toggle_task_${task.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                            contentDescription = "Voltooi taak",
                                            tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(percent = 50),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = task.subject,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (task.dueDate.cleanSchoolText() != null) {
                                                Text(
                                                    text = task.dueDate,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = task.title.cleanSchoolText() ?: "Taak",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Today's Schedule Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lessen Vandaag ($dayNameDutch)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onNavigateToRooster,
                    modifier = Modifier.testTag("view_all_schedule_button")
                ) {
                    Text("Volledig rooster")
                }
            }
        }

        if (todayClasses.isEmpty()) {
            item {
                Surface(
                    shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_today_classes_card")
                ) {
                    Text(
                        text = "Geen lesuren gepland voor $dayNameDutch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else if (agendaStyle == AgendaStyle.COMPACT) {
            item {
                Surface(
                    shape = appCardShape(standard = 18.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        todayClasses.forEachIndexed { idx, appt ->
                            CompactRoosterRow(appointment = appt)
                            if (idx < todayClasses.lastIndex) {
                                NothingAwareDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(todayClasses, key = { it.id }) { appt ->
                TodayClassRow(appointment = appt)
            }
        }

        // Bottom "Aanpassen" button with pencil icon
        item(key = "bottom_modify_today_button") {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onOpenTodayModifySheet,
                shape = RoundedCornerShape(percent = 50),
                border = nothingCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("modify_today_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Aanpassen",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aanpassen",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showCityPickerDialog) {
        WeatherCityPickerDialog(
            onDismiss = { showCityPickerDialog = false },
            onSearchCities = onSearchCities,
            onSelectCity = { city ->
                onSelectWeatherCity(city.name, city.latitude, city.longitude)
                showCityPickerDialog = false
            }
        )
    }
}

@Composable
private fun NavigationSetupPromptBanner(
    onCustomizeNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val nothingMode = isNothingOs
    Surface(
        shape = appCardShape(standard = 20.dp, nothing = 24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = nothingCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nav_prompt_banner")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
                    .testTag("dismiss_nav_prompt_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sluiten",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 48.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wil je ook navigatie?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "Voeg 1-tik route naar school en live bustijden met kaart toe aan jouw Vandaag-scherm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(
                    onClick = onCustomizeNow,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("customize_nav_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nu instellen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SchoolNavigationCard(
    schoolAddress: SchoolAddress?,
    onLaunchMaps: (SchoolAddress) -> Unit,
    onConfigureAddress: () -> Unit
) {
    val nothingMode = isNothingOs
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("school_navigation_card"),
        shape = appCardShape(standard = 22.dp, nothing = 24.dp),
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = "Schoolnavigatie",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schoolAddress?.displayName ?: "1-Tik Schoolnavigatie",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = schoolAddress?.streetAndCity ?: "Stel je schooladres in om direct Google Maps te starten",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (schoolAddress != null) {
                Button(
                    onClick = { onLaunchMaps(schoolAddress) },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("one_tap_school_nav_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = "Navigeer naar school",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Route")
                }
            } else {
                OutlinedButton(
                    onClick = onConfigureAddress,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("setup_school_address_button")
                ) {
                    Text("Instellen")
                }
            }
        }
    }
}

private fun launchSchoolNavigation(context: Context, address: SchoolAddress) {
    val gmmUri = Uri.parse("google.navigation:q=${address.latitude},${address.longitude}")
    val mapsIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(mapsIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${address.latitude},${address.longitude}&query=${Uri.encode(address.displayName)}"
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }
}

@Composable
private fun LocalWeatherHeaderCard(
    weatherInfo: WeatherInfo?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onChangeCity: () -> Unit
) {
    Surface(
        shape = appCardShape(standard = 22.dp, nothing = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = nothingCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(appCardShape(standard = 22.dp, nothing = 24.dp))
            .clickable(onClick = onChangeCity)
            .testTag("local_weather_header")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = weatherIconForCode(weatherInfo?.weatherCode ?: 0, weatherInfo?.isDay ?: true),
                        contentDescription = weatherInfo?.conditionDutch ?: "Weer",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (weatherInfo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${weatherInfo.temperatureC.roundToInt()}°C",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${weatherInfo.conditionDutch}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${weatherInfo.cityName} • ${weatherInfo.minTempC.roundToInt()}°/${weatherInfo.maxTempC.roundToInt()}° • Wind ${weatherInfo.windKmh.roundToInt()} km/u",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (isLoading) "Lokaal weer laden..." else "Lokaal weer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tik om jouw plaatsnaam in te stellen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onChangeCity,
                modifier = Modifier.testTag("change_weather_city_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Plaats kiezen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_weather_button")
            ) {
                if (isLoading) {
                    if (isNothingOs) {
                        NothingDotMatrixSpinner()
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Weer vernieuwen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherCityPickerDialog(
    onDismiss: () -> Unit,
    onSearchCities: suspend (String) -> List<CitySearchResult>,
    onSelectCity: (CitySearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CitySearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Plaatsnaam kiezen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { newVal ->
                        query = newVal
                        if (newVal.trim().length >= 2) {
                            scope.launch {
                                isSearching = true
                                results = onSearchCities(newVal)
                                isSearching = false
                            }
                        } else {
                            results = emptyList()
                        }
                    },
                    label = { Text("Zoek stad of dorp") },
                    placeholder = { Text("Bijv. Schijndel, Utrecht...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_city_search_input")
                )

                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    results.take(5).forEach { city ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectCity(city) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = city.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val sub = listOf(city.adminArea, city.country).filter { it.isNotBlank() }.joinToString(", ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Sluiten")
            }
        }
    )
}

private fun weatherIconForCode(code: Int, isDay: Boolean): ImageVector = when (code) {
    0 -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
    1, 2, 3 -> Icons.Default.Cloud
    45, 48 -> Icons.Default.BlurOn
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Default.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Default.AcUnit
    95, 96, 99 -> Icons.Default.Thunderstorm
    else -> Icons.Default.WbCloudy
}

@Composable
fun HeroClassCard(
    appointment: Appointment,
    statusLabel: String,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanRoom = appointment.classroom.cleanSchoolText()
    val cleanOrigRoom = appointment.originalClassroom.cleanSchoolText()
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanHomework = appointment.homework.cleanSchoolText()

    val nothingMode = isNothingOs
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardClick)
            .testTag("hero_class_card"),
        shape = RoundedCornerShape(26.dp),
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (nothingMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (nothingMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nothingMode) statusLabel.uppercase() else statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (nothingMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = "${appointment.periodIndex}e uur (${appointment.startTime} - ${appointment.endTime})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = appointment.subjectName.cleanSchoolText() ?: appointment.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (cleanRoom != null || cleanTeacher != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cleanRoom != null) {
                        val roomText = if (appointment.isRelocated && cleanOrigRoom != null) {
                            "Lokaal $cleanRoom (was $cleanOrigRoom)"
                        } else {
                            "Lokaal $cleanRoom"
                        }
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isOneUi85 -> oneUiPillContainerColor(isError = false)
                                appointment.isRelocated -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Room,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isOneUi85) oneUiPillContentColor() else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = roomText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOneUi85) oneUiPillContentColor() else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (cleanTeacher != null) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = cleanTeacher,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (cleanHomework != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentLate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Huiswerk: $cleanHomework",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodayClassRow(
    appointment: Appointment,
    modifier: Modifier = Modifier
) {
    val isCanceled = appointment.isCanceled
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanRoom = appointment.classroom.cleanSchoolText()

    val oneUiMode = isOneUi85
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today_class_item_${appointment.id}"),
        shape = appCardShape(standard = 16.dp, nothing = 24.dp),
        border = nothingCardBorder(isAlert = isCanceled),
        colors = CardDefaults.cardColors(
            containerColor = if (isCanceled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCanceled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${appointment.periodIndex}e",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isCanceled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appointment.title.cleanSchoolText() ?: appointment.subjectName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCanceled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (oneUiMode) {
                            OneUiPillBadge(text = "UITVAL", isError = true)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "UITVAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else if (appointment.isRelocated) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (oneUiMode) {
                            OneUiPillBadge(text = "GEWIJZIGD")
                        } else {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "GEWIJZIGD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                val subLine = buildString {
                    append("${appointment.startTime} - ${appointment.endTime}")
                    if (cleanTeacher != null) append(" • $cleanTeacher")
                }
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (cleanRoom != null) {
                if (oneUiMode) {
                    OneUiPillBadge(text = "Lok. $cleanRoom", isError = isCanceled)
                } else {
                    Text(
                        text = "Lok. $cleanRoom",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
