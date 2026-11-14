package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.model.AgendaStyle
import com.example.model.Appointment
import com.example.model.KwtSlot
import com.example.model.cleanSchoolText
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.ScheduleLoadingSkeleton
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DayItem(
    val dayNumber: Int,
    val nameDutch: String,
    val dateString: String,
    val dateIso: String,
    val hasDisruption: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoosterScreen(
    appointments: List<Appointment>,
    selectedDay: Int,
    viewMode: Int,
    agendaStyle: AgendaStyle,
    isRefreshing: Boolean,
    kwtSlots: List<KwtSlot> = emptyList(),
    onSelectDay: (Int) -> Unit,
    onSelectViewMode: (Int) -> Unit,
    onOpenKwtSlot: (Appointment) -> Unit = {},
    modifier: Modifier = Modifier
) {
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (agendaStyle == AgendaStyle.COMPACT) 12.dp else 16.dp)
            .testTag("rooster_screen"),
        verticalArrangement = Arrangement.spacedBy(if (agendaStyle == AgendaStyle.COMPACT) 10.dp else 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // 1. Live Week Banner & Week Navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lesrooster",
                        style = if (agendaStyle == AgendaStyle.COMPACT) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Week $weekNumber • ${days.first().dateString} - ${days.last().dateString}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { weekOffset = 0 }
                    ) {
                        Text(
                            text = if (weekOffset == 0) "Actueel" else "W$weekNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
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
        }

        // 2. View Mode Switcher: Dag, 3-Dagen, Week
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rooster_view_mode_segmented")
            ) {
                SegmentedButton(
                    selected = viewMode == 0,
                    onClick = { onSelectViewMode(0) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Dag")
                }
                SegmentedButton(
                    selected = viewMode == 1,
                    onClick = { onSelectViewMode(1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("3-Dagen")
                }
                SegmentedButton(
                    selected = viewMode == 2,
                    onClick = { onSelectViewMode(2) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Week")
                }
            }
        }

        // 3. Interactive Horizontal Day Selector Strip
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("day_strip_selector"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(days) { day ->
                    val isSelected = selectedDay == day.dayNumber
                    Surface(
                        modifier = Modifier
                            .width(if (agendaStyle == AgendaStyle.COMPACT) 62.dp else 68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectDay(day.dayNumber) }
                            .testTag("day_selector_${day.dayNumber}"),
                        shape = RoundedCornerShape(16.dp),
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

        // 4. Schedule Skeleton / Empty / Dense or Spacious List
        if (isRefreshing && appointments.isEmpty()) {
            item {
                ScheduleLoadingSkeleton(
                    agendaStyle = agendaStyle,
                    statusLabel = "Rooster ophalen van Magister..."
                )
            }
        } else if (currentAppointments.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            text = dayHeader.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                item(key = "compact_group_$dayHeader") {
                    Surface(
                        shape = RoundedCornerShape(if (isNothingOs) 24.dp else 18.dp),
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
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            groupedByDay.forEach { (dayHeader, dayAppointments) ->
                if (viewMode != 0) {
                    item(key = "spacious_header_$dayHeader") {
                        Text(
                            text = dayHeader.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
                items(dayAppointments, key = { it.id }) { appt ->
                    val isKwtItem = appt.isKwt || kwtSlots.any { it.appointmentId == appt.id }
                    RoosterPeriodCard(
                        appointment = appt,
                        isKwtSelectable = isKwtItem,
                        onOpenKwt = { onOpenKwtSlot(appt) }
                    )
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
                shape = RoundedCornerShape(8.dp),
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
                    } else if (appointment.isSubstitution) {
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
                    } else if (isKwtSelectable) {
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
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (appointment.isRelocated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (appointment.isRelocated) "$cleanRoom!" else cleanRoom,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (appointment.isRelocated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Expandable clean homework strip (only shown when non-null)
        AnimatedVisibility(visible = showHomework && cleanHomework != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
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
        shape = RoundedCornerShape(24.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
                        } else if (appointment.isSubstitution) {
                            Spacer(modifier = Modifier.width(8.dp))
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
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (appointment.isRelocated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = if (appointment.isRelocated) "Lok. $cleanRoom!" else "Lok. $cleanRoom",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (appointment.isRelocated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (cleanHomework != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
