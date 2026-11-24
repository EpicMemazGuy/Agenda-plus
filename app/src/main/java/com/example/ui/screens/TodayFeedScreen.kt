package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.ScheduleLoadingSkeleton
import com.example.ui.components.agendaPressable
import com.example.ui.components.agendaSpring
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
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.rememberSubjectDisplay
import com.example.ui.components.staggeredEntrance
import com.example.ui.dialogs.ImageExportPreviewDialog
import com.example.ui.dialogs.LessonDetailSheet
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalAxolaCustomTheme
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.LocalMetroAccent
import com.example.ui.theme.MetroCrimson
import com.example.util.DayStoryMode
import com.example.util.StoryAndScheduleExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private sealed class TodayTimelineEntry(val sortTime: String, val key: String) {
    class Lesson(val appointment: Appointment) : TodayTimelineEntry(appointment.startTime, "lesson_${appointment.id}")
    class Custom(val item: CustomScheduleItem) : TodayTimelineEntry(item.startTime, "custom_${item.id}")
}

internal sealed class TodayHeroStatus {
    data class InLesson(
        val appointment: Appointment,
        val progressFraction: Float,
        val remainingMinutes: Int,
        val statusLabel: String
    ) : TodayHeroStatus()

    data class Break(
        val nextAppointment: Appointment,
        val minutesUntil: Int,
        val statusLabel: String
    ) : TodayHeroStatus()

    data class GapOrUitval(
        val canceledOrGapAppointment: Appointment?,
        val nextActiveAppointment: Appointment?,
        val minutesUntilNext: Int?,
        val statusLabel: String
    ) : TodayHeroStatus()

    data class UpcomingToday(
        val nextAppointment: Appointment,
        val statusLabel: String
    ) : TodayHeroStatus()

    data class WeekendOrEvening(
        val nextActiveAppointment: Appointment?,
        val countdownLabel: String,
        val upcomingTestsPreview: List<UpcomingTestEntry>
    ) : TodayHeroStatus()
}

private fun parseHhmmMinutes(hhmm: String): Int? {
    val parts = hhmm.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

internal fun computeTodayHeroStatus(
    appointments: List<Appointment>,
    tasks: List<HomeworkTask>,
    nowCal: Calendar = Calendar.getInstance()
): TodayHeroStatus {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayIso = isoFormat.format(nowCal.time)
    val dayNum = when (nowCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
    val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val nowSec = nowCal.get(Calendar.SECOND)

    val hasIso = appointments.any { it.dateIso.isNotBlank() }
    val todayList = if (hasIso) {
        appointments.filter { it.dateIso == todayIso }.sortedWith(compareBy({ it.periodIndex }, { it.startTime }))
    } else {
        appointments.filter { it.dayOfWeek == dayNum }.sortedWith(compareBy({ it.periodIndex }, { it.startTime }))
    }

    val activeNow = todayList.firstOrNull { appt ->
        if (appt.isCanceled) return@firstOrNull false
        val start = parseHhmmMinutes(appt.startTime) ?: return@firstOrNull false
        val end = parseHhmmMinutes(appt.endTime) ?: return@firstOrNull false
        nowMin in start until end
    }
    if (activeNow != null) {
        val startMin = parseHhmmMinutes(activeNow.startTime) ?: nowMin
        val endMin = parseHhmmMinutes(activeNow.endTime) ?: (startMin + 45)
        val totalSec = ((endMin - startMin).coerceAtLeast(1)) * 60
        val elapsedSec = ((nowMin - startMin) * 60 + nowSec).coerceIn(0, totalSec)
        val fraction = (elapsedSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
        val remaining = (endMin - nowMin).coerceAtLeast(1)
        return TodayHeroStatus.InLesson(
            appointment = activeNow,
            progressFraction = fraction,
            remainingMinutes = remaining,
            statusLabel = "Nu bezig • nog ${remaining}m (tot ${activeNow.endTime})"
        )
    }

    val nextActiveToday = todayList.firstOrNull { appt ->
        if (appt.isCanceled) return@firstOrNull false
        val start = parseHhmmMinutes(appt.startTime) ?: return@firstOrNull false
        start > nowMin
    }

    val canceledNow = todayList.firstOrNull { appt ->
        if (!appt.isCanceled) return@firstOrNull false
        val start = parseHhmmMinutes(appt.startTime) ?: return@firstOrNull false
        val end = parseHhmmMinutes(appt.endTime) ?: return@firstOrNull false
        nowMin in start until end
    }
    if (canceledNow != null) {
        val minsUntil = nextActiveToday?.let { next ->
            ((parseHhmmMinutes(next.startTime) ?: nowMin) - nowMin).coerceAtLeast(1)
        }
        val subjectLabel = canceledNow.subjectName.cleanSchoolText() ?: canceledNow.title.cleanSchoolText() ?: "Les"
        val label = if (minsUntil != null) {
            "Uitval ($subjectLabel) • volgende over ${minsUntil}m"
        } else {
            "Uitval ($subjectLabel) • vrij"
        }
        return TodayHeroStatus.GapOrUitval(
            canceledOrGapAppointment = canceledNow,
            nextActiveAppointment = nextActiveToday,
            minutesUntilNext = minsUntil,
            statusLabel = label
        )
    }

    if (nextActiveToday != null) {
        val startMin = parseHhmmMinutes(nextActiveToday.startTime) ?: nowMin
        val diffMin = (startMin - nowMin).coerceAtLeast(0)
        val secLeft = (59 - nowSec).coerceIn(0, 59)
        val hadEarlierLesson = todayList.any { appt ->
            if (appt.isCanceled) return@any false
            val end = parseHhmmMinutes(appt.endTime) ?: return@any false
            end <= nowMin
        }
        if (hadEarlierLesson) {
            return if (diffMin <= 25) {
                TodayHeroStatus.Break(
                    nextAppointment = nextActiveToday,
                    minutesUntil = diffMin,
                    statusLabel = "Pauze • volgende les over ${diffMin}m ${String.format(Locale.US, "%02d", secLeft)}s"
                )
            } else {
                TodayHeroStatus.GapOrUitval(
                    canceledOrGapAppointment = null,
                    nextActiveAppointment = nextActiveToday,
                    minutesUntilNext = diffMin,
                    statusLabel = "Tussenuur • volgende les over ${diffMin}m"
                )
            }
        } else {
            val label = if (diffMin >= 60) {
                "Begint over ${diffMin / 60}u ${diffMin % 60}m"
            } else {
                "Begint over ${diffMin}m ${String.format(Locale.US, "%02d", secLeft)}s"
            }
            return TodayHeroStatus.UpcomingToday(
                nextAppointment = nextActiveToday,
                statusLabel = label
            )
        }
    }

    val futureNext = if (hasIso) {
        appointments
            .filter { !it.isCanceled && it.dateIso > todayIso }
            .sortedWith(compareBy({ it.dateIso }, { it.periodIndex }, { it.startTime }))
            .firstOrNull()
    } else {
        appointments
            .filter { !it.isCanceled && it.dayOfWeek > dayNum }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.periodIndex }, { it.startTime }))
            .firstOrNull()
            ?: appointments
                .filter { !it.isCanceled }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.periodIndex }, { it.startTime }))
                .firstOrNull()
    }

    val countdownLabel = if (futureNext != null && futureNext.dateIso.length == 10) {
        val targetDate = runCatching { isoFormat.parse(futureNext.dateIso) }.getOrNull()
        val startMin = parseHhmmMinutes(futureNext.startTime) ?: (8 * 60 + 30)
        if (targetDate != null) {
            val targetCal = Calendar.getInstance().apply {
                time = targetDate
                set(Calendar.HOUR_OF_DAY, startMin / 60)
                set(Calendar.MINUTE, startMin % 60)
                set(Calendar.SECOND, 0)
            }
            val diffMins = ((targetCal.timeInMillis - nowCal.timeInMillis) / 60_000L).coerceAtLeast(1L)
            val days = diffMins / (60 * 24)
            val hours = (diffMins / 60) % 24
            val mins = diffMins % 60
            when {
                days >= 1 -> "Volgende les over ${days}d ${hours}u"
                hours >= 1 -> "Volgende les over ${hours}u ${mins}m"
                else -> "Volgende les over ${mins}m"
            }
        } else {
            "Volgende les om ${futureNext.startTime}"
        }
    } else if (futureNext != null) {
        "Volgende les om ${futureNext.startTime}"
    } else {
        "Geen lessen meer gepland"
    }

    val upcomingTestsPreview = buildUpcomingTestsList(appointments, tasks, todayIso).take(2)
    return TodayHeroStatus.WeekendOrEvening(
        nextActiveAppointment = futureNext,
        countdownLabel = countdownLabel,
        upcomingTestsPreview = upcomingTestsPreview
    )
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
    onOpenTestPrep: (key: String, subject: String, title: String, toetsstof: String, targetDateIso: String?) -> Unit = { _, _, _, _, _ -> },
    onEditSubject: (code: String, detectedName: String) -> Unit = { _, _ -> },
    onPlanStudyBlockForDate: (dateIso: String, dayOfWeek: Int) -> Unit = { _, _ -> },
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    studentName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberAgendaHaptics()
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
    var selectedLessonForDetail by remember { mutableStateOf<Appointment?>(null) }
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

    var heroStatus by remember(appointments, tasks) {
        mutableStateOf(computeTodayHeroStatus(appointments, tasks, Calendar.getInstance()))
    }
    LaunchedEffect(appointments, tasks) {
        while (true) {
            heroStatus = computeTodayHeroStatus(appointments, tasks, Calendar.getInstance())
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
                            when (val status = heroStatus) {
                                is TodayHeroStatus.InLesson -> {
                                    HeroClassCard(
                                        appointment = status.appointment,
                                        statusLabel = status.statusLabel,
                                        changedDiff = changedLessonDiffs[status.appointment.id],
                                        progressFraction = status.progressFraction,
                                        onCardClick = {
                                            haptics.tick()
                                            selectedLessonForDetail = status.appointment
                                        }
                                    )
                                }
                                is TodayHeroStatus.Break -> {
                                    HeroClassCard(
                                        appointment = status.nextAppointment,
                                        statusLabel = status.statusLabel,
                                        changedDiff = changedLessonDiffs[status.nextAppointment.id],
                                        onCardClick = {
                                            haptics.tick()
                                            selectedLessonForDetail = status.nextAppointment
                                        }
                                    )
                                }
                                is TodayHeroStatus.GapOrUitval -> {
                                    val displayAppt = status.nextActiveAppointment ?: status.canceledOrGapAppointment
                                    if (displayAppt != null) {
                                        HeroClassCard(
                                            appointment = displayAppt,
                                            statusLabel = status.statusLabel,
                                            changedDiff = changedLessonDiffs[displayAppt.id],
                                            isAlertHero = status.canceledOrGapAppointment != null,
                                            onCardClick = {
                                                haptics.tick()
                                                selectedLessonForDetail = displayAppt
                                            }
                                        )
                                    } else {
                                        WeekendEveningHeroCard(
                                            countdownLabel = status.statusLabel,
                                            nextActiveAppointment = null,
                                            upcomingTestsPreview = emptyList(),
                                            onOpenTestPrep = { t ->
                                                haptics.tick()
                                                onOpenTestPrep(
                                                    t.id,
                                                    t.subject.capitalizeSubjectName(),
                                                    "${t.typeBadge} ${t.subject.capitalizeSubjectName()}",
                                                    t.description,
                                                    t.dateIso
                                                )
                                            },
                                            onPlanStudyBlock = {
                                                haptics.confirm()
                                                val cal = Calendar.getInstance()
                                                val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                                                val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                                    Calendar.MONDAY -> 1
                                                    Calendar.TUESDAY -> 2
                                                    Calendar.WEDNESDAY -> 3
                                                    Calendar.THURSDAY -> 4
                                                    Calendar.FRIDAY -> 5
                                                    Calendar.SATURDAY -> 6
                                                    else -> 7
                                                }
                                                onPlanStudyBlockForDate(iso, dow)
                                            },
                                            onOpenRooster = {
                                                haptics.tick()
                                                onNavigateToRooster()
                                            }
                                        )
                                    }
                                }
                                is TodayHeroStatus.UpcomingToday -> {
                                    HeroClassCard(
                                        appointment = status.nextAppointment,
                                        statusLabel = status.statusLabel,
                                        changedDiff = changedLessonDiffs[status.nextAppointment.id],
                                        onCardClick = {
                                            haptics.tick()
                                            selectedLessonForDetail = status.nextAppointment
                                        }
                                    )
                                }
                                is TodayHeroStatus.WeekendOrEvening -> {
                                    WeekendEveningHeroCard(
                                        countdownLabel = status.countdownLabel,
                                        nextActiveAppointment = status.nextActiveAppointment,
                                        upcomingTestsPreview = status.upcomingTestsPreview,
                                        onOpenTestPrep = { t ->
                                            haptics.tick()
                                            onOpenTestPrep(
                                                t.id,
                                                t.subject.capitalizeSubjectName(),
                                                "${t.typeBadge} ${t.subject.capitalizeSubjectName()}",
                                                t.description,
                                                t.dateIso
                                            )
                                        },
                                        onPlanStudyBlock = {
                                            haptics.confirm()
                                            val targetTest = status.upcomingTestsPreview.firstOrNull()
                                            val targetIso = targetTest?.dateIso?.takeIf { it.length == 10 }
                                                ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
                                            onPlanStudyBlockForDate(targetIso, 1)
                                        },
                                        onOpenRooster = {
                                            haptics.tick()
                                            status.nextActiveAppointment?.let { selectedLessonForDetail = it } ?: onNavigateToRooster()
                                        }
                                    )
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
                                        onClick = {
                                            haptics.confirm()
                                            onToggleTask(task.id)
                                        },
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
        item(key = "today_schedule_header") {
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
                        onClick = {
                            haptics.tick()
                            showStoryExportDialog = true
                        },
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
                        onClick = {
                            haptics.tick()
                            onAddCustomScheduleItem()
                        },
                        modifier = Modifier.testTag("today_add_custom_block_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Blok")
                    }
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onNavigateToRooster()
                        },
                        modifier = Modifier.testTag("view_all_schedule_button")
                    ) {
                        Text("Rooster")
                    }
                }
            }
        }

        if (mergedTimeline.isEmpty()) {
            item(key = "today_schedule_empty") {
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
                item(key = "today_compact_container") {
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
                                    onOpenKwt = { onOpenKwtSlot(appt) },
                                    onOpenDetail = {
                                        haptics.tick()
                                        selectedLessonForDetail = appt
                                    },
                                    onEditSubject = onEditSubject
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
            itemsIndexed(todayCustomItems, key = { _, it -> "today_custom_${it.id}" }) { idx, custom ->
                CustomScheduleItemCard(
                    item = custom,
                    onToggleCompleted = {
                        haptics.confirm()
                        onToggleCustomScheduleItem(custom.id, it)
                    },
                    onEdit = { onEditCustomScheduleItem(custom) },
                    onDelete = { onDeleteCustomScheduleItem(custom.id) },
                    modifier = Modifier.staggeredEntrance(idx)
                )
            }
        } else {
            itemsIndexed(mergedTimeline, key = { _, it -> it.key }) { idx, entry ->
                when (entry) {
                    is TodayTimelineEntry.Lesson -> {
                        val isKwtItem = entry.appointment.isKwt || kwtSlots.any { it.appointmentId == entry.appointment.id }
                        TodayClassRow(
                            appointment = entry.appointment,
                            isKwtSelectable = isKwtItem,
                            changedDiff = changedLessonDiffs[entry.appointment.id],
                            onOpenKwt = { onOpenKwtSlot(entry.appointment) },
                            onOpenDetail = {
                                haptics.tick()
                                selectedLessonForDetail = entry.appointment
                            },
                            modifier = Modifier.staggeredEntrance(idx)
                        )
                    }
                    is TodayTimelineEntry.Custom -> {
                        CustomScheduleItemCard(
                            item = entry.item,
                            onToggleCompleted = {
                                haptics.confirm()
                                onToggleCustomScheduleItem(entry.item.id, it)
                            },
                            onEdit = { onEditCustomScheduleItem(entry.item) },
                            onDelete = { onDeleteCustomScheduleItem(entry.item.id) },
                            modifier = Modifier.staggeredEntrance(idx)
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

    selectedLessonForDetail?.let { detailAppt ->
        val isKwtItem = detailAppt.isKwt || kwtSlots.any { it.appointmentId == detailAppt.id }
        val subjectTitle = detailAppt.subjectName.cleanSchoolText() ?: detailAppt.title.cleanSchoolText() ?: "Les"
        LessonDetailSheet(
            appointment = detailAppt,
            changedDiff = changedLessonDiffs[detailAppt.id],
            isKwtSelectable = isKwtItem,
            onDismiss = { selectedLessonForDetail = null },
            onOpenTestPrep = { key, subject, title, toetsstof, targetDateIso ->
                selectedLessonForDetail = null
                onOpenTestPrep(key, subject, title, toetsstof, targetDateIso)
            },
            onOpenKwtSlot = {
                selectedLessonForDetail = null
                onOpenKwtSlot(detailAppt)
            },
            onEditSubject = { code, detectedName ->
                selectedLessonForDetail = null
                onEditSubject(code, detectedName)
            },
            onPlanStudyBlock = {
                selectedLessonForDetail = null
                val targetIso = detailAppt.dateIso.takeIf { it.length == 10 }
                    ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
                onPlanStudyBlockForDate(targetIso, detailAppt.dayOfWeek.coerceIn(1, 7))
            }
        )
    }
}

@Composable
private fun WeekendEveningHeroCard(
    countdownLabel: String,
    nextActiveAppointment: Appointment?,
    upcomingTestsPreview: List<UpcomingTestEntry>,
    onOpenTestPrep: (UpcomingTestEntry) -> Unit,
    onPlanStudyBlock: () -> Unit,
    onOpenRooster: () -> Unit
) {
    val windowsPhoneMode = isWindowsPhone
    val iosMode = isIosLiquidGlass
    val heroShape = appCardShape(standard = 24.dp, nothing = 24.dp)
    val containerColor = when {
        windowsPhoneMode -> MaterialTheme.colorScheme.surfaceContainerLow
        iosMode -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_empty_card"),
        shape = heroShape,
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = if (windowsPhoneMode) RectangleShape else CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = "Vrije avond of weekend",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (windowsPhoneMode) "weekend / vrije avond" else "Weekend / Vrije Avond",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (windowsPhoneMode) FontWeight.Light else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = appButtonShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = countdownLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (nextActiveAppointment != null) {
                val nextDisp = rememberSubjectDisplay(
                    rawTitleOrName = nextActiveAppointment.subjectName.cleanSchoolText() ?: nextActiveAppointment.title,
                    rawCode = nextActiveAppointment.subjectCode
                )
                Surface(
                    shape = appInnerCardShape(standard = 14.dp, nothing = 14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .agendaPressable(onClick = onOpenRooster)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = nextDisp.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Eerstvolgend: ${nextDisp.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${nextActiveAppointment.startTime} - ${nextActiveAppointment.endTime}" +
                                    (nextActiveAppointment.classroom.cleanSchoolText()?.let { " • Lok. $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (upcomingTestsPreview.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Aankomende toetsen",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    upcomingTestsPreview.forEach { test ->
                        val testDisp = rememberSubjectDisplay(rawTitleOrName = test.subject)
                        Surface(
                            shape = appInnerCardShape(standard = 12.dp, nothing = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .agendaPressable(onClick = { onOpenTestPrep(test) })
                                .testTag("hero_upcoming_test_${test.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OneUiPillBadge(text = test.typeBadge)
                                    Text(
                                        text = testDisp.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = test.countdownLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPlanStudyBlock,
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("hero_plan_study_block_button")
                ) {
                    Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan studieblok", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenRooster,
                    shape = appButtonShape(),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("hero_open_rooster_button")
                ) {
                    Text("Rooster")
                }
            }
        }
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
    progressFraction: Float? = null,
    isAlertHero: Boolean = false,
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
        windowsPhoneMode && isAlertHero -> MetroCrimson
        windowsPhoneMode -> MaterialTheme.colorScheme.primary
        isAlertHero -> MaterialTheme.colorScheme.errorContainer
        iosMode -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val heroPrimaryText = when {
        windowsPhoneMode -> Color.White
        isAlertHero -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val heroSecondaryText = when {
        windowsPhoneMode -> Color.White.copy(alpha = 0.86f)
        isAlertHero -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .agendaPressable(onClick = onCardClick)
            .testTag("hero_class_card"),
        shape = heroShape,
        border = nothingCardBorder(isAlert = isAlertHero),
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
            if (progressFraction != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction.coerceIn(0f, 1f),
                    animationSpec = agendaSpring(),
                    label = "hero_lesson_progress"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = if (windowsPhoneMode) Color.White else MaterialTheme.colorScheme.primary,
                    trackColor = if (windowsPhoneMode) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(if (windowsPhoneMode) RectangleShape else CircleShape)
                        .testTag("hero_lesson_progress_bar")
                )
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
    onOpenDetail: () -> Unit = {},
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
            .agendaPressable(onClick = onOpenDetail)
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

