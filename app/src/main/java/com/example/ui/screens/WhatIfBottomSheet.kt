package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SubjectGradeSummary
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfBottomSheet(
    subjectSummary: SubjectGradeSummary,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("what_if_bottom_sheet")
    ) {
        var targetAverage by remember { mutableStateOf(6.0) }
        var testWeight by remember { mutableStateOf(2.0) }

        // Existing grades total weight & sum
        val currentSum = remember(subjectSummary) {
            subjectSummary.grades.sumOf { it.score * it.weight }
        }
        val currentWeightSum = remember(subjectSummary) {
            subjectSummary.grades.sumOf { it.weight }
        }

        val requiredScore = remember(targetAverage, testWeight, currentSum, currentWeightSum) {
            val totalNewWeight = currentWeightSum + testWeight
            val needed = (targetAverage * totalNewWeight - currentSum) / testWeight
            round(needed * 10) / 10.0
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "What-If Cijfer Simulator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${subjectSummary.subjectName} • Huidig gemiddelde: ${subjectSummary.weightedAverage}",
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

            Spacer(modifier = Modifier.height(20.dp))

            // Slider 1: Target Average
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
            }

            Slider(
                value = targetAverage.toFloat(),
                onValueChange = { targetAverage = round(it * 10) / 10.0 },
                valueRange = 5.0f..9.0f,
                steps = 39,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_average_slider")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Slider 2: Test Weighting
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

            Spacer(modifier = Modifier.height(24.dp))

            // Result Card
            val isFeasible = requiredScore in 1.0..10.0
            val isSafe = requiredScore < 1.0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("what_if_result_card"),
                shape = RoundedCornerShape(20.dp),
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
                    Text(
                        text = when {
                            isSafe -> "Veilige Marge"
                            isFeasible -> "Benodigd Cijfer"
                            else -> "Niet Haalbaar"
                        },
                        style = MaterialTheme.typography.labelLarge,
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
                            isSafe -> "≥ 1.0"
                            requiredScore > 10.0 -> "> 10.0"
                            else -> "$requiredScore"
                        },
                        style = MaterialTheme.typography.displayLarge,
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
                            isSafe -> "Zelfs met het laagste cijfer (1.0) behoud je minimaal een $targetAverage!"
                            isFeasible -> "Als je minimaal een $requiredScore haalt op deze ${testWeight.toInt()}x weging, staat je gemiddelde op $targetAverage."
                            else -> "Met een ${testWeight.toInt()}x weging is een $targetAverage wiskundig onhaalbaar in deze periode."
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
