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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.data.db.CustomScheduleItem
import com.example.model.*
import com.example.network.CitySearchResult
import com.example.ui.components.AppThemedHeader
import com.example.ui.components.BusLiveMapCard
import com.example.ui.components.CustomScheduleItemCard
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.LocalSubjectCustomizationState
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.NothingDotMatrixSpinner
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.ScheduleLoadingSkeleton
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appLiquidGlassContainerColor
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isIosLiquidGlass
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.isWindowsPhone
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.oneUiPillContainerColor
import com.example.ui.components.oneUiPillContentColor
import com.example.ui.components.rememberSubjectDisplay
import com.example.ui.theme.MetroCrimson
import androidx.compose.ui.graphics.toArgb
import com.example.ui.dialogs.ImageExportPreviewDialog
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalAxolaCustomTheme
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.LocalMetroAccent
import com.example.util.DayStoryMode
import com.example.util.StoryAndScheduleExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private sealed class TodayTimelineEntry(val sortTime: String, val key: String) {
    class Lesson(val appointment: Appointment) : TodayTimelineEntry(appointment.startTime, "lesson_${appointment.id}")
    class Custom(val item: CustomScheduleItem) : TodayTimelineEntry(item.startTime, "custom_${item.id}")
}

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
    smartBusAdvice: SmartBusAdvice? = null,
    commuteModes: List<CommuteModeConfig> = emptyList(),
    activeCommuteModeId: String = "",
    changedLessonDiffs: Map<Long, String> = emptyMap(),
    customScheduleItems: List<CustomScheduleItem> = emptyList(),
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
    onSelectCommuteMode: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    onSaveCommuteModes: (List<CommuteModeConfig>) -> Unit = {},
    onAddCustomScheduleItem: () -> Unit = {},
    onEditCustomScheduleItem: (CustomScheduleItem) -> Unit = {},
    onToggleCustomScheduleItem: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteCustomScheduleItem: (Long) -> Unit = {},
    onOpenKwtSlot: (Appointment) -> Unit = {},
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    studentName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val oneUiMode = isOneUi85
    val windowsPhoneMode = isWindowsPhone
    val iosMode = isIosLiquidGlass
    val usesFrostedInset = oneUiMode || iosMode
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 }
    }
    LaunchedEffect(isScrolled, usesFrostedInset) {
        onHeaderCollapsedChange(usesFrostedInset && isScrolled)
    }
    var showCityPickerDialog by remember { mutableStateOf(false) }
    var showStoryExportDialog by remember { mutableStateOf(false) }
    var storyMode by remember { mutableStateOf(DayStoryMode.TODAY_SCHEDULE) }
    var hideTeacherAndRoom by remember { mutableStateOf(false) }
    var storyPngFile by remember { mutableStateOf<File?>(null) }
    var isStoryGenerating by remember { mutableStateOf(false) }

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

    val todayCustomItems = remember(customScheduleItems) {
        val cal = java.util.Calendar.getInstance()
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val dayNum = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }
        customScheduleItems.filter { it.appliesToDate(todayIso, dayNum) }.sortedBy { it.startTime }
    }

    val mergedTimeline = remember(todayClasses, todayCustomItems) {
        (todayClasses.map { TodayTimelineEntry.Lesson(it) } + todayCustomItems.map { TodayTimelineEntry.Custom(it) })
            .sortedBy { it.sortTime }
    }

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

    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("today_feed_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            top = oneUiInsets.calculateTopPadding() + 12.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 28.dp
        )
    ) {
        if (oneUiMode || windowsPhoneMode || iosMode) {
            item(key = "themed_today_header") {
                AppThemedHeader(
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
                                    changedDiff = changedLessonDiffs[nextClass.id],
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
                                        shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
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
                            smartBusAdvice = smartBusAdvice,
                            commuteModes = commuteModes,
                            activeCommuteModeId = activeCommuteModeId,
                            onSelectCommuteMode = onSelectCommuteMode,
                            onSaveCommuteModes = onSaveCommuteModes,
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
                            val taskSubjectDisplay = rememberSubjectDisplay(rawTitleOrName = task.subject)
                            val taskBadgeColor = if (!isNothingOs && taskSubjectDisplay.customColor != null) {
                                taskSubjectDisplay.customColor.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            val taskBadgeContentColor = if (!isNothingOs && taskSubjectDisplay.customColor != null) {
                                taskSubjectDisplay.customColor
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("today_task_item_${task.id}"),
                                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                                border = nothingCardBorder(isAlert = task.priority == TaskPriority.HIGH && !task.isCompleted),
                                colors = CardDefaults.cardColors(
                                    containerColor = appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
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
                                                shape = appButtonShape(),
                                                color = taskBadgeColor
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = taskSubjectDisplay.icon,
                                                        contentDescription = null,
                                                        tint = taskBadgeContentColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = taskSubjectDisplay.displayName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = taskBadgeContentColor
                                                    )
                                                }
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStoryExportDialog = true },
                        shape = appButtonShape(),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("today_story_export_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("9:16 Story")
                    }
                    TextButton(
                        onClick = onAddCustomScheduleItem,
                        modifier = Modifier.testTag("today_add_custom_block_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Blok")
                    }
                    TextButton(
                        onClick = onNavigateToRooster,
                        modifier = Modifier.testTag("view_all_schedule_button")
                    ) {
                        Text("Rooster")
                    }
                }
            }
        }

        if (mergedTimeline.isEmpty()) {
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
            if (todayClasses.isNotEmpty()) {
                item {
                    Surface(
                        shape = appCardShape(standard = 18.dp, nothing = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            todayClasses.forEachIndexed { idx, appt ->
                                val isKwtItem = appt.isKwt || kwtSlots.any { it.appointmentId == appt.id }
                                CompactRoosterRow(
                                    appointment = appt,
                                    isKwtSelectable = isKwtItem,
                                    changedDiff = changedLessonDiffs[appt.id],
                                    onOpenKwt = { onOpenKwtSlot(appt) }
                                )
                                if (idx < todayClasses.lastIndex) {
                                    NothingAwareDivider(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        insetStart = if (oneUiMode) 52.dp else 0.dp,
                                        modifier = if (oneUiMode) Modifier else Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(todayCustomItems, key = { "today_custom_${it.id}" }) { custom ->
                CustomScheduleItemCard(
                    item = custom,
                    onToggleCompleted = { onToggleCustomScheduleItem(custom.id, it) },
                    onEdit = { onEditCustomScheduleItem(custom) },
                    onDelete = { onDeleteCustomScheduleItem(custom.id) }
                )
            }
        } else {
            items(mergedTimeline, key = { it.key }) { entry ->
                when (entry) {
                    is TodayTimelineEntry.Lesson -> {
                        val isKwtItem = entry.appointment.isKwt || kwtSlots.any { it.appointmentId == entry.appointment.id }
                        TodayClassRow(
                            appointment = entry.appointment,
                            isKwtSelectable = isKwtItem,
                            changedDiff = changedLessonDiffs[entry.appointment.id],
                            onOpenKwt = { onOpenKwtSlot(entry.appointment) }
                        )
                    }
                    is TodayTimelineEntry.Custom -> {
                        CustomScheduleItemCard(
                            item = entry.item,
                            onToggleCompleted = { onToggleCustomScheduleItem(entry.item.id, it) },
                            onEdit = { onEditCustomScheduleItem(entry.item) },
                            onDelete = { onDeleteCustomScheduleItem(entry.item.id) }
                        )
                    }
                }
            }
        }

        // Bottom "Aanpassen" button with pencil icon
        item(key = "bottom_modify_today_button") {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onOpenTodayModifySheet,
                shape = appButtonShape(),
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

    if (showStoryExportDialog) {
        val currentThemeStyle = LocalAppThemeStyle.current
        val currentAxolaTheme = LocalAxolaCustomTheme.current
        val currentMetroAccent = LocalMetroAccent.current
        val isDark = LocalDarkThemeActive.current
        val customState = LocalSubjectCustomizationState.current
        val dynamicPrimaryArgb = MaterialTheme.colorScheme.primary.toArgb()
        val cal = java.util.Calendar.getInstance()
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)

        LaunchedEffect(showStoryExportDialog, storyMode, hideTeacherAndRoom, appointments, tasks) {
            isStoryGenerating = true
            val file = withContext(Dispatchers.IO) {
                StoryAndScheduleExporter.renderDayOrExamStoryPng(
                    context = context,
                    appointments = appointments,
                    tasks = tasks,
                    targetDateIso = todayIso,
                    mode = storyMode,
                    hideTeacherAndRoom = hideTeacherAndRoom,
                    studentName = studentName,
                    customizationState = customState,
                    themeStyle = currentThemeStyle,
                    axolaTheme = currentAxolaTheme,
                    metroAccent = currentMetroAccent,
                    isDarkMode = isDark,
                    dynamicPrimaryArgb = dynamicPrimaryArgb
                )
            }
            storyPngFile = file
            isStoryGenerating = false
        }

        ImageExportPreviewDialog(
            title = if (storyMode == DayStoryMode.TODAY_SCHEDULE) "Mijn Dag 9:16 Story" else "Proefwerkweek 9:16 Story",
            pngFile = storyPngFile,
            isGenerating = isStoryGenerating,
            shareChooserTitle = "Deel Story",
            onDismiss = { showStoryExportDialog = false },
            controlsContent = {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DayStoryMode.entries.forEachIndexed { idx, m ->
                        SegmentedButton(
                            selected = storyMode == m,
                            onClick = { storyMode = m },
                            shape = SegmentedButtonDefaults.itemShape(idx, DayStoryMode.entries.size)
                        ) {
                            Text(m.labelDutch)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verberg docent & lokaalnamen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = hideTeacherAndRoom,
                        onCheckedChange = { hideTeacherAndRoom = it }
                    )
                }
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
                shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
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
                shape = appThumbnailShape(standard = 14.dp, nothing = 14.dp),
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
        shape = appCardShape(standard = 24.dp, nothing = 26.dp),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else AlertDialogDefaults.TonalElevation,
        title = {
            Text(
                text = "Plaatsnaam kiezen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isOneUi85) FontWeight.Medium else FontWeight.Bold
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
                    shape = appInnerCardShape(standard = 12.dp, nothing = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_city_search_input")
                )

                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    results.take(5).forEach { city ->
                        val cityItemShape = appInnerCardShape(standard = 12.dp, nothing = 12.dp)
                        Surface(
                            shape = cityItemShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cityItemShape)
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
    changedDiff: String? = null,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanRoom = appointment.classroom.cleanSchoolText()
    val cleanOrigRoom = appointment.originalClassroom.cleanSchoolText()
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanHomework = appointment.homework.cleanSchoolText()

    val nothingMode = isNothingOs
    val windowsPhoneMode = isWindowsPhone
    val iosMode = isIosLiquidGlass
    val subjectDisplay = rememberSubjectDisplay(
        rawTitleOrName = appointment.subjectName.cleanSchoolText() ?: appointment.title,
        rawCode = appointment.subjectCode
    )

    val heroShape = appCardShape(standard = 26.dp, nothing = 26.dp)
    val cardContainerColor = when {
        windowsPhoneMode -> MaterialTheme.colorScheme.primary
        iosMode -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val heroPrimaryText = if (windowsPhoneMode) Color.White else MaterialTheme.colorScheme.onSurface
    val heroSecondaryText = if (windowsPhoneMode) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(heroShape)
            .clickable(onClick = onCardClick)
            .testTag("hero_class_card"),
        shape = heroShape,
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor
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
                    shape = appButtonShape(),
                    color = when {
                        windowsPhoneMode -> Color.Black.copy(alpha = 0.22f)
                        nothingMode -> MaterialTheme.colorScheme.tertiary
                        subjectDisplay.customColor != null && !nothingMode -> subjectDisplay.customColor
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = when {
                                windowsPhoneMode -> Color.White
                                nothingMode -> MaterialTheme.colorScheme.onTertiary
                                else -> Color.White
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nothingMode || windowsPhoneMode) statusLabel.uppercase() else statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                windowsPhoneMode -> Color.White
                                nothingMode -> MaterialTheme.colorScheme.onTertiary
                                else -> Color.White
                            }
                        )
                    }
                }

                Text(
                    text = "${appointment.periodIndex}e uur (${appointment.startTime} - ${appointment.endTime})",
                    style = MaterialTheme.typography.labelMedium,
                    color = heroSecondaryText
                )
            }

            if (!changedDiff.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OneUiPillBadge(text = "Gewijzigd: $changedDiff")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
                    color = when {
                        windowsPhoneMode -> Color.Black.copy(alpha = 0.18f)
                        !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = subjectDisplay.icon,
                            contentDescription = subjectDisplay.displayName,
                            tint = when {
                                windowsPhoneMode -> Color.White
                                !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subjectDisplay.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (windowsPhoneMode) FontWeight.Light else FontWeight.Bold,
                        color = heroPrimaryText
                    )
                    if (subjectDisplay.showOriginalCode && subjectDisplay.rawCode.isNotBlank()) {
                        Text(
                            text = subjectDisplay.rawCode.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = heroSecondaryText
                        )
                    }
                }
            }

            if (cleanRoom != null || cleanTeacher != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (cleanTeacher != null) {
                            Surface(
                                shape = appButtonShape(),
                                color = if (windowsPhoneMode) Color.Black.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = cleanTeacher,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = heroPrimaryText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (!windowsPhoneMode && cleanRoom != null) {
                            val roomText = if (appointment.isRelocated && cleanOrigRoom != null) {
                                "Lokaal $cleanRoom (was $cleanOrigRoom)"
                            } else {
                                "Lokaal $cleanRoom"
                            }
                            Surface(
                                shape = appButtonShape(),
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
                    }

                    // Windows Phone Metro corner counter pinned bottom-right
                    if (windowsPhoneMode && cleanRoom != null) {
                        Text(
                            text = cleanRoom.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color.White
                        )
                    }
                }
            }

            if (cleanHomework != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = appInnerCardShape(standard = 14.dp, nothing = 14.dp),
                    color = if (windowsPhoneMode) Color.Black.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentLate,
                            contentDescription = null,
                            tint = if (windowsPhoneMode) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Huiswerk: $cleanHomework",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroPrimaryText
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
    isKwtSelectable: Boolean = false,
    changedDiff: String? = null,
    onOpenKwt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCanceled = appointment.isCanceled
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanRoom = appointment.classroom.cleanSchoolText()

    val oneUiMode = isOneUi85
    val nothingMode = isNothingOs
    val windowsPhoneMode = isWindowsPhone
    val customState = LocalSubjectCustomizationState.current
    val subjectDisplay = rememberSubjectDisplay(
        rawTitleOrName = appointment.subjectName.cleanSchoolText() ?: appointment.title,
        rawCode = appointment.subjectCode
    )
    val hasGenuineDiff = !changedDiff.isNullOrBlank() || (appointment.isRelocated && !appointment.originalClassroom.isNullOrBlank())
    val useMetroSubjectFill = windowsPhoneMode && !isCanceled && customState.useSubjectColorsForMetroTiles && subjectDisplay.customColor != null

    val rowShape = appCardShape(standard = 16.dp, nothing = 24.dp)
    val rowBg = when {
        windowsPhoneMode && isCanceled -> MetroCrimson
        useMetroSubjectFill -> subjectDisplay.customColor!!
        isCanceled -> MaterialTheme.colorScheme.errorContainer
        else -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
    }
    val onWhiteTile = windowsPhoneMode && (isCanceled || useMetroSubjectFill)
    val primaryTextColor = when {
        onWhiteTile -> Color.White
        isCanceled -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = when {
        onWhiteTile -> Color.White.copy(alpha = 0.84f)
        isCanceled -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isKwtSelectable) Modifier.clip(rowShape).clickable(onClick = onOpenKwt) else Modifier
            )
            .testTag("today_class_item_${appointment.id}"),
        shape = rowShape,
        border = nothingCardBorder(isAlert = isCanceled),
        colors = CardDefaults.cardColors(containerColor = rowBg)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical accent indicator bar when custom subject color or genuine status exists
            if (!windowsPhoneMode && (!nothingMode || isCanceled || hasGenuineDiff || appointment.isTestOrExam)) {
                val barColor = when {
                    isCanceled || hasGenuineDiff -> MaterialTheme.colorScheme.error
                    appointment.isTestOrExam -> Color(0xFFF59E0B)
                    !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor
                    nothingMode -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(appThumbnailShape(2.dp, 2.dp))
                        .background(barColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Surface(
                shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
                color = when {
                    onWhiteTile -> Color.Black.copy(alpha = 0.20f)
                    isCanceled -> MaterialTheme.colorScheme.error
                    !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!nothingMode && !isCanceled) {
                        Icon(
                            imageVector = subjectDisplay.icon,
                            contentDescription = subjectDisplay.displayName,
                            tint = when {
                                onWhiteTile -> Color.White
                                subjectDisplay.customColor != null -> subjectDisplay.customColor
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "${appointment.periodIndex}e",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCanceled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subjectDisplay.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None,
                        color = primaryTextColor
                    )
                    if (isCanceled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (oneUiMode) {
                            OneUiPillBadge(text = "Uitval", isError = true)
                        } else {
                            Surface(
                                shape = appButtonShape(),
                                color = if (onWhiteTile) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "UITVAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (onWhiteTile) Color.White else MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else if (hasGenuineDiff) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (oneUiMode) {
                            OneUiPillBadge(text = "Gewijzigd")
                        } else {
                            Surface(
                                shape = appButtonShape(),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "GEWIJZIGD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                val subLine = buildString {
                    append("${appointment.periodIndex}e • ${appointment.startTime} - ${appointment.endTime}")
                    if (cleanTeacher != null) append(" • $cleanTeacher")
                    if (subjectDisplay.showOriginalCode && subjectDisplay.rawCode.isNotBlank()) {
                        append(" • ${subjectDisplay.rawCode.uppercase()}")
                    }
                }
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                if (!changedDiff.isNullOrBlank()) {
                    Text(
                        text = changedDiff,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (onWhiteTile) Color.White else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (cleanRoom != null) {
                when {
                    windowsPhoneMode -> {
                        Text(
                            text = cleanRoom.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = primaryTextColor,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                    }
                    oneUiMode -> {
                        OneUiPillBadge(text = "Lok. $cleanRoom", isError = isCanceled)
                    }
                    else -> {
                        Text(
                            text = "Lok. $cleanRoom",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    }
                }
            }
        }
    }
}

