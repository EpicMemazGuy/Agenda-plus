package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CustomScheduleItem
import com.example.model.AgendaStyle
import com.example.model.Appointment
import com.example.model.HomeworkTask
import com.example.model.KwtSlot
import com.example.model.TaskCategory
import com.example.model.TestPrepChecklist
import com.example.model.cleanSchoolText
import com.example.model.isTestKeywordMatch
import com.example.ui.components.CustomScheduleItemCard
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.ScheduleLoadingSkeleton
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.oneUiPillContainerColor
import com.example.ui.components.oneUiPillContentColor
import com.example.ui.components.oneUiSpring
import com.example.ui.components.oneUiTween
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DayItem(
    val dayNumber: Int,
    val nameDutch: String,
    val dateString: String,
    val dateIso: String,
    val hasDisruption: Boolean
)

data class UpcomingTestEntry(
    val id: String,
    val subject: String,
    val typeBadge: String,
    val dateIso: String,
    val dateDisplay: String,
    val timeAndPeriod: String,
    val description: String,
    val countdownLabel: String,
    val daysRemaining: Int?
)

private sealed class RoosterDayEntry(val sortTime: String, val key: String) {
    class Lesson(val appointment: Appointment) : RoosterDayEntry(appointment.startTime, "appt_${appointment.id}")
    class Custom(val item: CustomScheduleItem) : RoosterDayEntry(item.startTime, "custom_${item.id}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoosterScreen(
    appointments: List<Appointment>,
    tasks: List<HomeworkTask> = emptyList(),
    selectedDay: Int,
    viewMode: Int,
    agendaStyle: AgendaStyle,
    isRefreshing: Boolean,
    kwtSlots: List<KwtSlot> = emptyList(),
    customScheduleItems: List<CustomScheduleItem> = emptyList(),
    testPrepChecklists: Map<String, TestPrepChecklist> = emptyMap(),
    onSelectDay: (Int) -> Unit,
    onSelectViewMode: (Int) -> Unit,
    onOpenKwtSlot: (Appointment) -> Unit = {},
    onAddCustomScheduleItem: (dateIso: String, dayOfWeek: Int) -> Unit = { _, _ -> },
    onEditCustomScheduleItem: (CustomScheduleItem) -> Unit = {},
    onToggleCustomScheduleItem: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteCustomScheduleItem: (Long) -> Unit = {},
    onOpenTestPrep: (key: String, subject: String, title: String, toetsstof: String) -> Unit = { _, _, _, _ -> },
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val oneUiMode = isOneUi85
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 }
    }
    LaunchedEffect(isScrolled, oneUiMode) {
        onHeaderCollapsedChange(oneUiMode && isScrolled)
    }
    var weekOffset by remember { mutableIntStateOf(0) }

    val (weekNumber, days) = remember(weekOffset, appointments) {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = when (dow) {
            Calendar.SATURDAY -> 2
            Calendar.SUNDAY -> 1
            else -> Calendar.MONDAY - dow
        }
        cal.add(Calendar.DAY_OF_YEAR, daysToMonday + (weekOffset * 7))

        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortDateFormat = SimpleDateFormat("d MMM", Locale("nl", "NL"))
        val dayLabels = listOf("Ma", "Di", "Wo", "Do", "Vr")
        val targetWeekNumber = cal.get(Calendar.WEEK_OF_YEAR)

        val generatedDays = (1..5).map { dayIdx ->
            val dateIso = isoFormat.format(cal.time)
            val dateLabel = shortDateFormat.format(cal.time)
            val hasDisruption = appointments.any { appt ->
                val matchesDay = if (appt.dateIso.isNotBlank()) appt.dateIso == dateIso else appt.dayOfWeek == dayIdx
                matchesDay && (appt.isCanceled || appt.isRelocated || appt.isSubstitution)
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            DayItem(
                dayNumber = dayIdx,
                nameDutch = dayLabels[dayIdx - 1],
                dateString = dateLabel,
                dateIso = dateIso,
                hasDisruption = hasDisruption
            )
        }
        targetWeekNumber to generatedDays
    }

    val selectedDayItem = days.firstOrNull { it.dayNumber == selectedDay } ?: days.first()
    val hasIsoDates = appointments.any { it.dateIso.isNotBlank() }

    val currentAppointments = remember(appointments, selectedDay, viewMode, days, hasIsoDates) {
        if (hasIsoDates) {
            val allowedIsos = when (viewMode) {
                0 -> setOf(selectedDayItem.dateIso)
                1 -> days.filter { it.dayNumber in selectedDay..(selectedDay + 2).coerceAtMost(5) }.map { it.dateIso }.toSet()
                else -> days.map { it.dateIso }.toSet()
            }
            appointments
                .filter { it.dateIso in allowedIsos }
                .sortedWith(compareBy({ it.dateIso }, { it.periodIndex }, { it.startTime }))
        } else {
            appointments.filter { appt ->
                when (viewMode) {
                    0 -> appt.dayOfWeek == selectedDay
                    1 -> appt.dayOfWeek in selectedDay..(selectedDay + 2).coerceAtMost(5)
                    else -> true
                }
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.periodIndex }))
        }
    }

    val groupedByDay = remember(currentAppointments, days, hasIsoDates) {
        if (hasIsoDates) {
            currentAppointments.groupBy { appt ->
                val dayMatch = days.firstOrNull { it.dateIso == appt.dateIso }
                if (dayMatch != null) "${dayMatch.nameDutch} ${dayMatch.dateString}" else appt.dateIso
            }
        } else {
            currentAppointments.groupBy { appt ->
                val dayMatch = days.firstOrNull { it.dayNumber == appt.dayOfWeek }
                if (dayMatch != null) "${dayMatch.nameDutch} ${dayMatch.dateString}" else "Dag ${appt.dayOfWeek}"
            }
        }
    }

    val upcomingTests = remember(appointments, tasks) {
        buildUpcomingTestsList(appointments, tasks)
    }

    val visibleCustomItems = remember(customScheduleItems, selectedDay, viewMode, days) {
        val visibleDays = when (viewMode) {
            0 -> listOf(selectedDayItem)
            1 -> days.filter { it.dayNumber in selectedDay..(selectedDay + 2).coerceAtMost(5) }
            2 -> days
            else -> emptyList()
        }
        visibleDays.associateWith { d ->
            customScheduleItems
                .filter { it.appliesToDate(d.dateIso, d.dayNumber) }
                .sortedBy { it.startTime }
        }
    }

    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding(if (agendaStyle == AgendaStyle.COMPACT) 12.dp else 16.dp))
            .testTag("rooster_screen"),
        verticalArrangement = Arrangement.spacedBy(if (agendaStyle == AgendaStyle.COMPACT) 10.dp else 14.dp),
        contentPadding = PaddingValues(
            top = oneUiInsets.calculateTopPadding() + 10.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 24.dp
        )
    ) {
        // 1. Live Week Banner & Week Navigation
        item {
            val weekNavControls: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { weekOffset-- },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rooster_prev_week_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Vorige week"
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (oneUiMode) oneUiPillContainerColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { weekOffset = 0 }
                    ) {
                        Text(
                            text = if (weekOffset == 0) "Actueel" else "W$weekNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (oneUiMode) oneUiPillContentColor() else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = { weekOffset++ },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rooster_next_week_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Volgende week"
                        )
                    }
                }
            }

            if (oneUiMode) {
                OneUiExpandingHeader(
                    title = if (viewMode == 3) "Toetsen" else "Lesrooster",
                    subtitle = if (viewMode == 3) {
                        "${upcomingTests.size} aankomende toets(en) • PW, SO, MO"
                    } else {
                        "Week $weekNumber • ${days.first().dateString} - ${days.last().dateString}"
                    },
                    isScrolled = isScrolled,
                    trailingContent = if (viewMode != 3) weekNavControls else null
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (viewMode == 3) "Toetsen & Deadlines" else "Lesrooster",
                            style = if (agendaStyle == AgendaStyle.COMPACT) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (viewMode == 3) {
                                "${upcomingTests.size} aankomende toets(en) • PW, SO, MO"
                            } else {
                                "Week $weekNumber • ${days.first().dateString} - ${days.last().dateString}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    weekNavControls()
                }
            }
        }

        // 2. View Mode Switcher: Dag, 3-Dagen, Week, Toetsen
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rooster_view_mode_segmented")
            ) {
                SegmentedButton(
                    selected = viewMode == 0,
                    onClick = { onSelectViewMode(0) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                    modifier = Modifier.testTag("rooster_mode_dag")
                ) {
                    Text("Dag", maxLines = 1)
                }
                SegmentedButton(
                    selected = viewMode == 1,
                    onClick = { onSelectViewMode(1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                    modifier = Modifier.testTag("rooster_mode_3dagen")
                ) {
                    Text("3-Dagen", maxLines = 1)
                }
                SegmentedButton(
                    selected = viewMode == 2,
                    onClick = { onSelectViewMode(2) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                    modifier = Modifier.testTag("rooster_mode_week")
                ) {
                    Text("Week", maxLines = 1)
                }
                SegmentedButton(
                    selected = viewMode == 3,
                    onClick = { onSelectViewMode(3) },
                    shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                    modifier = Modifier.testTag("rooster_mode_toetsen")
                ) {
                    Text("Toetsen", maxLines = 1)
                }
            }
        }

        // 3. Interactive Horizontal Day Selector Strip (hidden in Toetsen mode)
        if (viewMode != 3) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("day_strip_selector"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(days) { day ->
                        val isSelected = selectedDay == day.dayNumber
                        val dayShape = appInnerCardShape(standard = 16.dp, nothing = 16.dp)
                        Surface(
                            modifier = Modifier
                                .width(if (agendaStyle == AgendaStyle.COMPACT) 62.dp else 68.dp)
                                .clip(dayShape)
                                .clickable { onSelectDay(day.dayNumber) }
                                .testTag("day_selector_${day.dayNumber}"),
                            shape = dayShape,
                            border = nothingCardBorder(isAlert = day.hasDisruption && !isSelected),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = if (agendaStyle == AgendaStyle.COMPACT) 8.dp else 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.nameDutch,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = day.dateString.split(" ").first(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(3.dp))
                                if (day.hasDisruption) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                            )
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(5.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (viewMode != 3) {
            item(key = "rooster_add_custom_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = { onAddCustomScheduleItem(selectedDayItem.dateIso, selectedDayItem.dayNumber) },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("rooster_add_custom_block_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Persoonlijk blok toevoegen")
                    }
                }
            }
        }

        // 4. Toetsen View OR Schedule Skeleton / Empty / Dense or Spacious List
        if (viewMode == 3) {
            if (upcomingTests.isEmpty()) {
                item {
                    Surface(
                        shape = appCardShape(standard = 20.dp, nothing = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toetsen_empty_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Geen geplande toetsen gevonden",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aankomende proefwerken, SO's, MO's en deadlines uit Magister verschijnen hier chronologisch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(upcomingTests, key = { it.id }) { test ->
                    val checklist = testPrepChecklists[test.id]
                    UpcomingTestCard(
                        test = test,
                        checklistProgressText = checklist?.let { "${it.completedCount}/${it.topics.size} klaar" },
                        onClick = { onOpenTestPrep(test.id, test.subject, "${test.typeBadge} ${test.subject}", test.description) }
                    )
                }
            }
        } else if (isRefreshing && appointments.isEmpty()) {
            item {
                ScheduleLoadingSkeleton(
                    agendaStyle = agendaStyle,
                    statusLabel = "Rooster ophalen van Magister..."
                )
            }
        } else if (currentAppointments.isEmpty() && visibleCustomItems.values.all { it.isEmpty() }) {
            item {
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Geen lessen gepland voor ${selectedDayItem.nameDutch} ${selectedDayItem.dateString}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kies een andere dag, bekijk de hele week of schakel naar volgende week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { onSelectViewMode(2) }
                        ) {
                            Icon(Icons.Default.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bekijk Hele Week")
                        }
                    }
                }
            }
        } else if (agendaStyle == AgendaStyle.COMPACT) {
            groupedByDay.forEach { (dayHeader, dayAppointments) ->
                if (viewMode != 0) {
                    item(key = "header_$dayHeader") {
                        Text(
                            text = if (oneUiMode) dayHeader else dayHeader.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (oneUiMode) FontWeight.Medium else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                item(key = "compact_group_$dayHeader") {
                    Surface(
                        shape = appCardShape(standard = 18.dp, nothing = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compact_rooster_container")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            dayAppointments.forEachIndexed { idx, appt ->
                                val isKwtItem = appt.isKwt || kwtSlots.any { it.appointmentId == appt.id }
                                CompactRoosterRow(
                                    appointment = appt,
                                    isKwtSelectable = isKwtItem,
                                    onOpenKwt = { onOpenKwtSlot(appt) }
                                )
                                if (idx < dayAppointments.lastIndex) {
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
            visibleCustomItems.forEach { (dayItem, itemsForDay) ->
                items(itemsForDay, key = { "compact_custom_${dayItem.dateIso}_${it.id}" }) { custom ->
                    CustomScheduleItemCard(
                        item = custom,
                        onToggleCompleted = { onToggleCustomScheduleItem(custom.id, it) },
                        onEdit = { onEditCustomScheduleItem(custom) },
                        onDelete = { onDeleteCustomScheduleItem(custom.id) }
                    )
                }
            }
        } else {
            val dayHeadersOrder = if (viewMode == 0) listOf(selectedDayItem) else if (viewMode == 1) days.filter { it.dayNumber in selectedDay..(selectedDay + 2).coerceAtMost(5) } else days
            dayHeadersOrder.forEach { dayItem ->
                val dayAppts = currentAppointments.filter { appt ->
                    if (hasIsoDates) appt.dateIso == dayItem.dateIso else appt.dayOfWeek == dayItem.dayNumber
                }
                val dayCustom = visibleCustomItems[dayItem].orEmpty()
                val dayTimeline = (dayAppts.map { RoosterDayEntry.Lesson(it) } + dayCustom.map { RoosterDayEntry.Custom(it) })
                    .sortedBy { it.sortTime }

                if (dayTimeline.isNotEmpty()) {
                    if (viewMode != 0) {
                        item(key = "spacious_header_${dayItem.dateIso}") {
                            val headerLabel = "${dayItem.nameDutch} ${dayItem.dateString}"
                            Text(
                                text = if (oneUiMode) headerLabel else headerLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (oneUiMode) FontWeight.Medium else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                    items(dayTimeline, key = { "${dayItem.dateIso}_${it.key}" }) { entry ->
                        when (entry) {
                            is RoosterDayEntry.Lesson -> {
                                val appt = entry.appointment
                                val isKwtItem = appt.isKwt || kwtSlots.any { it.appointmentId == appt.id }
                                RoosterPeriodCard(
                                    appointment = appt,
                                    isKwtSelectable = isKwtItem,
                                    onOpenKwt = { onOpenKwtSlot(appt) }
                                )
                            }
                            is RoosterDayEntry.Custom -> {
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
            }
        }
    }
}

@Composable
fun CompactRoosterRow(
    appointment: Appointment,
    isKwtSelectable: Boolean = false,
    onOpenKwt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCanceled = appointment.isCanceled
    val cleanHomework = appointment.homework.cleanSchoolText()
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanRoom = appointment.classroom.cleanSchoolText()
    var showHomework by remember { mutableStateOf(false) }

    val accentColor = when {
        isCanceled -> MaterialTheme.colorScheme.error
        appointment.isRelocated || appointment.isSubstitution -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isKwtSelectable) {
                    Modifier.clickable { onOpenKwt() }
                } else if (cleanHomework != null) {
                    Modifier.clickable { showHomework = !showHomework }
                } else Modifier
            )
            .background(
                if (isCanceled) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("compact_rooster_row_${appointment.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tight stacked start/end time column
            Column(
                modifier = Modifier.width(42.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = appointment.startTime,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCanceled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = appointment.endTime,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Thin vertical accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Compact period chip
            Surface(
                shape = appThumbnailShape(standard = 8.dp, nothing = 8.dp),
                color = if (isCanceled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${appointment.periodIndex}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCanceled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Subject + compact teacher & status
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = appointment.title.cleanSchoolText() ?: appointment.subjectName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCanceled) {
                        if (isOneUi85) {
                            OneUiPillBadge(text = "UITVAL", isError = true)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "UITVAL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    } else if (appointment.isSubstitution) {
                        if (isOneUi85) {
                            OneUiPillBadge(text = "VERV")
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "VERV",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    } else if (isKwtSelectable) {
                        if (isOneUi85) {
                            OneUiPillBadge(text = "KWT")
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "KWT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                if (cleanTeacher != null || cleanHomework != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (cleanTeacher != null) {
                            Text(
                                text = cleanTeacher,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (cleanHomework != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = "Huiswerk aanwezig",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (showHomework) "Verberg" else "Huiswerk",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (cleanRoom != null) {
                Spacer(modifier = Modifier.width(6.dp))
                val roomLabel = if (appointment.isRelocated) "$cleanRoom!" else cleanRoom
                if (isOneUi85) {
                    OneUiPillBadge(
                        text = roomLabel,
                        containerColor = if (appointment.isRelocated) oneUiPillContainerColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (appointment.isRelocated) oneUiPillContentColor() else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (appointment.isRelocated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = roomLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (appointment.isRelocated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Expandable clean homework strip (only shown when non-null)
        AnimatedVisibility(
            visible = showHomework && cleanHomework != null,
            enter = expandVertically(oneUiSpring()) + fadeIn(oneUiTween()),
            exit = shrinkVertically(oneUiSpring()) + fadeOut(oneUiTween())
        ) {
            Surface(
                shape = appInnerCardShape(standard = 10.dp, nothing = 10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 50.dp)
            ) {
                Text(
                    text = cleanHomework ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun RoosterPeriodCard(
    appointment: Appointment,
    isKwtSelectable: Boolean = false,
    onOpenKwt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCanceled = appointment.isCanceled
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
    val cleanRoom = appointment.classroom.cleanSchoolText()
    val cleanHomework = appointment.homework.cleanSchoolText()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rooster_period_card_${appointment.id}"),
        shape = appCardShape(standard = 24.dp, nothing = 24.dp),
        border = nothingCardBorder(isAlert = isCanceled),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCanceled -> MaterialTheme.colorScheme.errorContainer
                appointment.isRelocated -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
                    color = if (isCanceled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${appointment.periodIndex}e",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCanceled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appointment.title.cleanSchoolText() ?: appointment.subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )

                        if (isCanceled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isOneUi85) {
                                OneUiPillBadge(text = "VERVALLEN", isError = true)
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = "VERVALLEN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else if (appointment.isSubstitution) {
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isOneUi85) {
                                OneUiPillBadge(text = "VERVANGING")
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "VERVANGING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    val timeTeacherText = buildString {
                        append("${appointment.startTime} - ${appointment.endTime}")
                        if (cleanTeacher != null) append(" • $cleanTeacher")
                    }
                    Text(
                        text = timeTeacherText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCanceled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (cleanRoom != null) {
                    val roomText = if (appointment.isRelocated) "Lok. $cleanRoom!" else "Lok. $cleanRoom"
                    if (isOneUi85) {
                        OneUiPillBadge(
                            text = roomText,
                            containerColor = if (appointment.isRelocated) oneUiPillContainerColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (appointment.isRelocated) oneUiPillContentColor() else MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (appointment.isRelocated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = roomText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (appointment.isRelocated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (cleanHomework != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = appInnerCardShape(standard = 12.dp, nothing = 12.dp),
                    color = if (isCanceled) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cleanHomework,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (isKwtSelectable) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onOpenKwt,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("rooster_kwt_choose_${appointment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("KWT-les kiezen")
                }
            }
        }
    }
}

@Composable
private fun UpcomingTestCard(
    test: UpcomingTestEntry,
    checklistProgressText: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUrgent = test.daysRemaining != null && test.daysRemaining <= 2
    val cardShape = appCardShape(standard = 18.dp, nothing = 22.dp)
    Card(
        shape = cardShape,
        border = nothingCardBorder(isAlert = isUrgent),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick)
            .testTag("upcoming_test_card_${test.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isOneUi85) {
                        OneUiPillBadge(text = test.typeBadge)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = test.typeBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = test.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isOneUi85) {
                    OneUiPillBadge(
                        text = test.countdownLabel,
                        isError = isUrgent
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = test.countdownLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUrgent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${test.dateDisplay} • ${test.timeAndPeriod}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (test.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = appInnerCardShape(standard = 12.dp, nothing = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = test.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OneUiPillBadge(
                    text = checklistProgressText ?: "Studiechecklist openen",
                    modifier = Modifier.testTag("upcoming_test_checklist_${test.id}")
                )
            }
        }
    }
}

internal fun buildUpcomingTestsList(
    appointments: List<Appointment>,
    tasks: List<HomeworkTask>,
    todayIsoOverride: String? = null
): List<UpcomingTestEntry> {
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFmt = SimpleDateFormat("EEE d MMM", Locale("nl", "NL"))
    val todayIso = todayIsoOverride ?: isoFmt.format(Date())
    val entries = mutableListOf<UpcomingTestEntry>()
    val seenSignature = mutableSetOf<String>()
    val fromApptIds = mutableSetOf<Long>()

    appointments
        .filter { !it.isCanceled && it.isTestOrExam && (it.dateIso.isBlank() || it.dateIso >= todayIso) }
        .sortedWith(compareBy({ it.dateIso.ifBlank { "9999-99-99" } }, { it.periodIndex }, { it.startTime }))
        .forEach { appt ->
            fromApptIds.add(appt.id)
            val subject = appt.subjectName.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: "Toets"
            val desc = appt.homework.cleanSchoolText() ?: appt.title.cleanSchoolText() ?: ""
            val sig = "${subject.lowercase()}_${appt.dateIso}_${desc.take(24).lowercase()}"
            if (seenSignature.add(sig)) {
                val badge = resolveTestTypeBadge(appt.infoType, "${appt.title} $desc")
                val dateDisp = formatTestDateDisplay(appt.dateIso, isoFmt, displayFmt)
                val (countdown, daysRem) = computeTestCountdown(appt.dateIso, todayIso, isoFmt)
                val roomText = appt.classroom.cleanSchoolText()?.let { " • Lok. $it" } ?: ""
                entries.add(
                    UpcomingTestEntry(
                        id = "appt_${appt.id}",
                        subject = subject,
                        typeBadge = badge,
                        dateIso = appt.dateIso.ifBlank { "9999-99-99" },
                        dateDisplay = dateDisp,
                        timeAndPeriod = "${appt.periodIndex}e uur (${appt.startTime})$roomText",
                        description = desc,
                        countdownLabel = countdown,
                        daysRemaining = daysRem
                    )
                )
            }
        }

    tasks
        .filter { task ->
            if (task.isCompleted) return@filter false
            val isApptBacked = task.id.startsWith("hw_") && fromApptIds.any { task.id.startsWith("hw_${it}_") }
            if (isApptBacked) return@filter false
            val isTest = task.category == TaskCategory.EXAM ||
                task.category == TaskCategory.MAJOR_TEST ||
                isTestKeywordMatch(task.title) ||
                isTestKeywordMatch(task.description)
            val isNotPast = task.dueDay.length != 10 || task.dueDay >= todayIso
            isTest && isNotPast
        }
        .sortedBy { if (it.dueDay.length == 10) it.dueDay else "9999-99-99" }
        .forEach { task ->
            val iso = if (task.dueDay.length == 10) task.dueDay else ""
            val subject = task.subject.cleanSchoolText() ?: "Toets"
            val desc = buildString {
                append(task.title)
                val extra = task.description.cleanSchoolText()
                if (extra != null && !extra.equals(task.title, ignoreCase = true)) {
                    append("\n").append(extra)
                }
            }
            val sig = "${subject.lowercase()}_${iso}_${task.title.take(24).lowercase()}"
            if (seenSignature.add(sig)) {
                val badge = resolveTestTypeBadge(0, "${task.title} ${task.description}")
                val dateDisp = if (iso.isNotBlank()) formatTestDateDisplay(iso, isoFmt, displayFmt) else task.dueDay
                val (countdown, daysRem) = computeTestCountdown(iso, todayIso, isoFmt)
                entries.add(
                    UpcomingTestEntry(
                        id = "task_${task.id}",
                        subject = subject,
                        typeBadge = badge,
                        dateIso = iso.ifBlank { "9999-99-99" },
                        dateDisplay = dateDisp,
                        timeAndPeriod = task.dueDate,
                        description = desc,
                        countdownLabel = countdown,
                        daysRemaining = daysRem
                    )
                )
            }
        }

    return entries.sortedBy { it.dateIso }
}

private fun resolveTestTypeBadge(infoType: Int, text: String): String {
    val lower = text.lowercase()
    return when {
        infoType == 2 || Regex("""\b(pw|proefwerk)\b""").containsMatchIn(lower) -> "PW"
        infoType == 4 || Regex("""\b(so|schriftelijke overhoring)\b""").containsMatchIn(lower) -> "SO"
        infoType == 5 || Regex("""\b(mo|mondeling|spreekbeurt)\b""").containsMatchIn(lower) -> "MO"
        infoType == 3 || lower.contains("tentamen") || lower.contains("examen") -> "Tentamen"
        else -> "Toets"
    }
}

private fun formatTestDateDisplay(
    dateIso: String,
    isoFmt: SimpleDateFormat,
    displayFmt: SimpleDateFormat
): String {
    if (dateIso.isBlank()) return "Binnenkort"
    return try {
        val date = isoFmt.parse(dateIso) ?: return dateIso
        displayFmt.format(date).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dateIso
    }
}

private fun computeTestCountdown(
    dateIso: String,
    todayIso: String,
    isoFmt: SimpleDateFormat
): Pair<String, Int?> {
    if (dateIso.isBlank()) return "Gepland" to null
    return try {
        val target = isoFmt.parse(dateIso) ?: return "Gepland" to null
        val today = isoFmt.parse(todayIso) ?: return "Gepland" to null
        val diffDays = TimeUnit.MILLISECONDS.toDays(target.time - today.time).toInt()
        val label = when {
            diffDays <= 0 -> "Vandaag"
            diffDays == 1 -> "Morgen"
            else -> "Over $diffDays dagen"
        }
        label to diffDays
    } catch (_: Exception) {
        "Gepland" to null
    }
}

