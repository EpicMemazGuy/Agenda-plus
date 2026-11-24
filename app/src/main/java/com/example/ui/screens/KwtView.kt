package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KwtOption
import com.example.model.KwtSlot
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.staggeredEntrance
import kotlinx.coroutines.launch

@Composable
fun KwtView(
    kwtSlots: List<KwtSlot>,
    isOffline: Boolean,
    onEnrollOption: suspend (appointmentId: Long, optionId: Long) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberAgendaHaptics()
    var activeEnrollmentId by remember { mutableStateOf<Long?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("kwt_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 28.dp
        )
    ) {
        feedbackMessage?.let { msg ->
            item(key = "kwt_feedback") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (kwtSlots.isEmpty()) {
            item(key = "kwt_empty") {
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kwt_empty_card")
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Geen openstaande keuzelessen (KWT)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Zodra jouw school keuzewerktijd (KWT) of keuzelessen openzet in Magister, kun je ze hier direct selecteren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(kwtSlots, key = { _, it -> it.appointmentId }) { idx, slot ->
                KwtSlotCard(
                    slot = slot,
                    isOffline = isOffline,
                    activeEnrollmentId = activeEnrollmentId,
                    modifier = Modifier.staggeredEntrance(idx),
                    onSelectOption = { opt ->
                        haptics.confirm()
                        scope.launch {
                            activeEnrollmentId = opt.id
                            val res = onEnrollOption(slot.appointmentId, opt.id)
                            activeEnrollmentId = null
                            feedbackMessage = if (res.isSuccess) {
                                "Ingeschreven voor '${opt.title}'."
                            } else {
                                res.exceptionOrNull()?.message ?: "Inschrijven voor KWT mislukt."
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KwtSlotCard(
    slot: KwtSlot,
    isOffline: Boolean,
    activeEnrollmentId: Long?,
    onSelectOption: (KwtOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = appCardShape(standard = 20.dp, nothing = 24.dp),
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kwt_slot_card_${slot.appointmentId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${slot.dateIso} • ${slot.periodIndex}e uur (${slot.startTime} - ${slot.endTime})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (slot.options.isEmpty()) {
                Text(
                    text = "Geen losse KWT-opties beschikbaar via de Magister API voor dit uur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                slot.options.forEach { opt ->
                    KwtOptionRow(
                        option = opt,
                        isOffline = isOffline,
                        isLoading = activeEnrollmentId == opt.id,
                        onChoose = { onSelectOption(opt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KwtOptionRow(
    option: KwtOption,
    isOffline: Boolean,
    isLoading: Boolean,
    onChoose: () -> Unit
) {
    Surface(
        shape = appInnerCardShape(standard = 14.dp, nothing = 14.dp),
        color = if (option.isEnrolled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = nothingCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (option.isEnrolled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                val meta = buildString {
                    if (option.subjectName.isNotBlank()) append(option.subjectName)
                    if (option.teacherName.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(option.teacherName)
                    }
                    if (option.classroom.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("Lok. ${option.classroom}")
                    }
                    if (option.maxCount > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${option.enrolledCount}/${option.maxCount}")
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (option.isEnrolled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (option.isEnrolled) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Gekozen",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(
                    onClick = onChoose,
                    enabled = !isOffline && !isLoading,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("kwt_select_option_${option.id}")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Kies")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KwtPickerBottomSheet(
    slot: KwtSlot,
    isOffline: Boolean,
    isLoading: Boolean = false,
    onEnrollOption: suspend (appointmentId: Long, optionId: Long) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loadingId by remember { mutableStateOf<Long?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("kwt_picker_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appScreenHorizontalPadding(standard = 20.dp))
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keuzeles (KWT) kiezen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${slot.title} • ${slot.startTime} - ${slot.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            statusMsg?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Beschikbare KWT-keuzes ophalen uit Magister...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (slot.options.isEmpty()) {
                Text(
                    text = "Voor dit lesuur zijn geen inschrijfbare KWT-opties gevonden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                slot.options.forEach { opt ->
                    KwtOptionRow(
                        option = opt,
                        isOffline = isOffline,
                        isLoading = loadingId == opt.id,
                        onChoose = {
                            scope.launch {
                                loadingId = opt.id
                                val res = onEnrollOption(slot.appointmentId, opt.id)
                                loadingId = null
                                statusMsg = if (res.isSuccess) {
                                    "Je bent ingeschreven voor '${opt.title}'."
                                } else {
                                    res.exceptionOrNull()?.message ?: "Kon niet inschrijven."
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
