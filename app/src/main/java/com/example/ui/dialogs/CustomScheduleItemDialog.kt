package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.db.CustomScheduleItem
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder

private val WEEKDAY_OPTIONS = listOf(
    1 to "Ma",
    2 to "Di",
    3 to "Wo",
    4 to "Do",
    5 to "Vr",
    6 to "Za",
    7 to "Zo"
)

private val COLOR_OPTIONS = listOf(
    "BLUE" to "Blauw",
    "EMERALD" to "Groen",
    "AMBER" to "Amber",
    "PURPLE" to "Paars",
    "CORAL" to "Koraal"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomScheduleItemDialog(
    existingItem: CustomScheduleItem?,
    initialDateIso: String,
    initialDayOfWeek: Int,
    onSave: (CustomScheduleItem) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var title by remember(existingItem) { mutableStateOf(existingItem?.title ?: "") }
    var description by remember(existingItem) { mutableStateOf(existingItem?.description ?: "") }
    var startTime by remember(existingItem) { mutableStateOf(existingItem?.startTime ?: "15:30") }
    var endTime by remember(existingItem) { mutableStateOf(existingItem?.endTime ?: "16:30") }
    var isRecurring by remember(existingItem) { mutableStateOf(existingItem?.isRecurring ?: false) }
    var dateIso by remember(existingItem, initialDateIso) {
        mutableStateOf(existingItem?.dateIso?.takeIf { it.isNotBlank() } ?: initialDateIso)
    }
    var selectedDays by remember(existingItem, initialDayOfWeek) {
        val initialSet = existingItem?.daysOfWeekCsv
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(initialDayOfWeek.coerceIn(1, 7))
        mutableStateOf(initialSet)
    }
    var colorKey by remember(existingItem) { mutableStateOf(existingItem?.colorKey ?: "BLUE") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val timeRegex = remember { Regex("""^([01]?\d|2[0-3]):[0-5]\d$""") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = appCardShape(standard = 24.dp, nothing = 26.dp),
            color = appSheetContainerColor(),
            border = nothingCardBorder(),
            tonalElevation = if (isOneUi85) 0.dp else 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_schedule_item_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(appScreenHorizontalPadding(standard = 20.dp)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (existingItem != null) "Persoonlijk blok bewerken" else "Persoonlijk blok toevoegen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_custom_schedule_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Titel") },
                    placeholder = { Text("Bijv. Bijles Wiskunde of Sport") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_item_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notitie / locatie (optioneel)") },
                    placeholder = { Text("Bijv. Lokaal B12 of sporthal") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_item_desc_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {
                            startTime = it
                            validationError = null
                        },
                        label = { Text("Begin (UU:MM)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_item_start_input")
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {
                            endTime = it
                            validationError = null
                        },
                        label = { Text("Eind (UU:MM)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_item_end_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wekelijks herhalen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isRecurring) "Herhaalt elke gekozen weekdag" else "Alleen op datum $dateIso",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        modifier = Modifier.testTag("custom_item_recurring_switch")
                    )
                }

                if (isRecurring) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        WEEKDAY_OPTIONS.forEach { (dayNum, label) ->
                            val selected = dayNum in selectedDays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedDays = if (selected && selectedDays.size > 1) {
                                        selectedDays - dayNum
                                    } else {
                                        selectedDays + dayNum
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Datum (yyyy-MM-dd)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_item_date_input")
                    )
                }

                Text(
                    text = "Kleuraccent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COLOR_OPTIONS.forEach { (key, label) ->
                        FilterChip(
                            selected = colorKey == key,
                            onClick = { colorKey = key },
                            label = { Text(label) }
                        )
                    }
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (existingItem != null && existingItem.id > 0L && onDelete != null) {
                        OutlinedButton(
                            onClick = {
                                onDelete(existingItem.id)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("delete_custom_schedule_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Verwijderen")
                        }
                    }
                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            val normStart = normalizeHhMm(startTime.trim())
                            val normEnd = normalizeHhMm(endTime.trim())
                            when {
                                cleanTitle.isEmpty() -> validationError = "Vul een titel in."
                                !timeRegex.matches(normStart) || !timeRegex.matches(normEnd) ->
                                    validationError = "Gebruik geldige tijden (bijv. 15:30)."
                                else -> {
                                    onSave(
                                        CustomScheduleItem(
                                            id = existingItem?.id ?: 0L,
                                            title = cleanTitle,
                                            description = description.trim(),
                                            startTime = normStart,
                                            endTime = normEnd,
                                            dateIso = dateIso.trim(),
                                            daysOfWeekCsv = selectedDays.sorted().joinToString(","),
                                            isRecurring = isRecurring,
                                            colorKey = colorKey,
                                            isCompleted = existingItem?.isCompleted ?: false,
                                            createdAt = existingItem?.createdAt ?: System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_custom_schedule_button")
                    ) {
                        Text(
                            text = if (existingItem != null) "Opslaan" else "Toevoegen",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeHhMm(raw: String): String {
    val parts = raw.split(":")
    if (parts.size != 2) return raw
    val h = parts[0].toIntOrNull() ?: return raw
    val m = parts[1].toIntOrNull() ?: return raw
    return String.format(java.util.Locale.US, "%02d:%02d", h, m)
}
