package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Appointment
import com.example.model.cleanSchoolText
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.isWindowsPhone
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.rememberSubjectDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailSheet(
    appointment: Appointment,
    changedDiff: String? = null,
    isKwtSelectable: Boolean = false,
    onDismiss: () -> Unit,
    onOpenTestPrep: (key: String, subject: String, title: String, toetsstof: String, targetDateIso: String?) -> Unit = { _, _, _, _, _ -> },
    onOpenKwtSlot: (Appointment) -> Unit = {},
    onEditSubject: (code: String, detectedName: String) -> Unit = { _, _ -> },
    onPlanStudyBlock: (Appointment) -> Unit = {}
) {
    val haptics = rememberAgendaHaptics()
    val windowsPhone = isWindowsPhone
    val subjDisplay = rememberSubjectDisplay(
        rawTitleOrName = appointment.subjectName.cleanSchoolText() ?: appointment.title,
        rawCode = appointment.subjectCode
    )
    val subjectColor = subjDisplay.customColor ?: MaterialTheme.colorScheme.primary
    val subjectIcon = subjDisplay.icon
    val cleanRoom = appointment.classroom.cleanSchoolText() ?: "Lokaal onbekend"
    val cleanTeacher = appointment.teacherName.cleanSchoolText()
        ?: appointment.teacherInitials.cleanSchoolText()
        ?: "Docent onbekend"
    val cleanHomework = appointment.homework.cleanSchoolText()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("lesson_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appScreenHorizontalPadding(standard = 20.dp))
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Subject Icon, Name, Time & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = appThumbnailShape(standard = 14.dp, nothing = 14.dp),
                    color = if (appointment.isCanceled) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        subjectColor.copy(alpha = 0.16f)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (appointment.isCanceled) Icons.Default.Cancel else subjectIcon,
                            contentDescription = subjDisplay.displayName,
                            tint = if (appointment.isCanceled) MaterialTheme.colorScheme.error else subjectColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (windowsPhone) subjDisplay.displayName.lowercase() else subjDisplay.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (appointment.isCanceled) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${appointment.periodIndex}e uur • ${appointment.startTime} - ${appointment.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Status Banner (Uitval, Gewijzigd, Toets)
            if (appointment.isCanceled || appointment.isRelocated || appointment.isSubstitution || changedDiff != null || appointment.isTestOrExam) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appointment.isCanceled) {
                        OneUiPillBadge(text = "Uitval", isError = true)
                    }
                    if (appointment.isRelocated || appointment.isSubstitution || changedDiff != null) {
                        OneUiPillBadge(
                            text = changedDiff ?: if (appointment.isRelocated) "Lokaal gewijzigd" else "Docent gewijzigd"
                        )
                    }
                    if (appointment.isTestOrExam && !appointment.isCanceled) {
                        OneUiPillBadge(
                            text = appointment.testBadgeLabel ?: "Toets",
                            isError = true
                        )
                    }
                }
            }

            // Classroom & Teacher Details Card
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = "Lokaal",
                            tint = subjectColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lokaal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buildString {
                                    append(cleanRoom)
                                    appointment.originalClassroom?.takeIf {
                                        appointment.isRelocated && !it.equals(cleanRoom, ignoreCase = true)
                                    }?.let { orig ->
                                        append(" (was $orig)")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Docent",
                            tint = subjectColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Docent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = cleanTeacher,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Homework / Notes Box
            if (!cleanHomework.isNullOrBlank()) {
                Surface(
                    shape = appInnerCardShape(standard = 16.dp, nothing = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = subjectColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appointment.isTestOrExam) "Toetsstof & info" else "Huiswerk & notities",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = cleanHomework,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Action Buttons
            if (appointment.isTestOrExam && !appointment.isCanceled) {
                Button(
                    onClick = {
                        haptics.confirm()
                        onDismiss()
                        onOpenTestPrep(
                            "appt_${appointment.id}",
                            subjDisplay.displayName,
                            "${appointment.testBadgeLabel ?: "Toets"} ${subjDisplay.displayName}",
                            cleanHomework ?: appointment.title,
                            appointment.dateIso.takeIf { it.isNotBlank() }
                        )
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("lesson_detail_test_prep_button")
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Studiechecklist", fontWeight = FontWeight.Bold)
                }
            }

            if (isKwtSelectable) {
                Button(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onOpenKwtSlot(appointment)
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("lesson_detail_kwt_button")
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keuzeles (KWT) kiezen", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.confirm()
                        onDismiss()
                        onPlanStudyBlock(appointment)
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("lesson_detail_plan_study_button")
                ) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan blok", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onEditSubject(subjDisplay.normalizedCode, subjDisplay.displayName)
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("lesson_detail_edit_subject_button")
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vak aanpassen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
