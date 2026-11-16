package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GradeItem
import com.example.model.SubjectGradeSummary
import com.example.ui.components.appCardShape
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import kotlin.math.round

fun calculateRequiredGradeForTarget(
    currentGrades: List<GradeItem>,
    targetAverage: Double,
    nextTestWeight: Double
): Double {
    val safeWeight = nextTestWeight.coerceAtLeast(0.1)
    val currentSum = currentGrades.sumOf { it.score * it.weight }
    val currentWeightSum = currentGrades.sumOf { it.weight }
    val totalNewWeight = currentWeightSum + safeWeight
    val needed = (targetAverage * totalNewWeight - currentSum) / safeWeight
    return round(needed * 10) / 10.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfBottomSheet(
    subjectSummary: SubjectGradeSummary,
    allSubjects: List<SubjectGradeSummary> = listOf(subjectSummary),
    onDismiss: () -> Unit
) {
    val subjectList = remember(subjectSummary, allSubjects) {
        if (allSubjects.isNotEmpty()) allSubjects else listOf(subjectSummary)
    }
    var selectedSubject by remember(subjectSummary) { mutableStateOf(subjectSummary) }
    var calculatorMode by remember { mutableIntStateOf(0) } // 0: Doelgemiddelde, 1: Wat moet ik halen?
    var targetAverage by remember { mutableDoubleStateOf(6.0) }
    var testWeight by remember { mutableDoubleStateOf(2.0) }

    val requiredScore = remember(selectedSubject, targetAverage, testWeight) {
        calculateRequiredGradeForTarget(
            currentGrades = selectedSubject.grades,
            targetAverage = targetAverage,
            nextTestWeight = testWeight
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("what_if_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (calculatorMode == 1) "Wat moet ik halen?" else "What-If Cijfer Simulator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selectedSubject.subjectName} • Huidig gemiddelde: ${selectedSubject.weightedAverage}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_what_if_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sluiten",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mode toggle
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("what_if_mode_toggle")
            ) {
                SegmentedButton(
                    selected = calculatorMode == 0,
                    onClick = { calculatorMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) {
                    Text("Simulator")
                }
                SegmentedButton(
                    selected = calculatorMode == 1,
                    onClick = { calculatorMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("Wat moet ik halen?")
                }
            }

            // Subject picker chips when multiple subjects exist
            if (subjectList.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("what_if_subject_picker")
                ) {
                    items(subjectList, key = { it.subjectCode + "_" + it.subjectName }) { subj ->
                        val isSelected = subj.subjectName == selectedSubject.subjectName && subj.subjectCode == selectedSubject.subjectCode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSubject = subj },
                            label = { Text("${subj.subjectName} (${subj.weightedAverage})") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Average Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gewenst doelgemiddelde",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (calculatorMode == 1) {
                        FilledTonalIconButton(
                            onClick = { targetAverage = round((targetAverage - 0.1).coerceAtLeast(1.0) * 10) / 10.0 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Lager doel", modifier = Modifier.size(18.dp))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$targetAverage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    if (calculatorMode == 1) {
                        FilledTonalIconButton(
                            onClick = { targetAverage = round((targetAverage + 0.1).coerceAtMost(10.0) * 10) / 10.0 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Hoger doel", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (calculatorMode == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(5.5, 6.0, 6.5, 7.0, 7.5, 8.0)
                    items(presets) { preset ->
                        FilterChip(
                            selected = targetAverage == preset,
                            onClick = { targetAverage = preset },
                            label = { Text("$preset") }
                        )
                    }
                }
            }

            Slider(
                value = targetAverage.toFloat(),
                onValueChange = { targetAverage = round(it * 10) / 10.0 },
                valueRange = if (calculatorMode == 1) 1.0f..10.0f else 5.0f..9.0f,
                steps = if (calculatorMode == 1) 89 else 39,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_average_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test Weighting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weging komende toets",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = "${testWeight.toInt()}x",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Slider(
                value = testWeight.toFloat(),
                onValueChange = { testWeight = it.toDouble() },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_weight_slider")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Result Card
            val isFeasible = requiredScore in 1.0..10.0
            val isSafe = requiredScore < 1.0
            val isImpossible = requiredScore > 10.0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("what_if_result_card"),
                shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                border = nothingCardBorder(isAlert = isImpossible),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSafe -> MaterialTheme.colorScheme.primaryContainer
                        isFeasible -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isImpossible) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Niet haalbaar (> 10.0)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = if (isSafe) "Veilige Marge" else "Benodigd Cijfer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSafe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isSafe -> "≥ 1.0"
                            isImpossible -> "$requiredScore (> 10.0)"
                            else -> "$requiredScore"
                        },
                        style = if (isImpossible) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSafe -> MaterialTheme.colorScheme.onPrimaryContainer
                            isFeasible -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isSafe -> "Zelfs met het laagste cijfer (1.0) behoud je minimaal een $targetAverage voor ${selectedSubject.subjectName}!"
                            isFeasible -> "Haal minimaal een $requiredScore (weging ${testWeight.toInt()}x) om gemiddeld op een $targetAverage uit te komen."
                            else -> "Om op $targetAverage uit te komen heb je een $requiredScore nodig. Dat is hoger dan 10.0 en helaas wiskundig niet haalbaar met weging ${testWeight.toInt()}x."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isSafe -> MaterialTheme.colorScheme.onPrimaryContainer
                            isFeasible -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_what_if_button")
            ) {
                Text("Sluiten")
            }
        }
    }
}
