package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GradeItem
import com.example.model.SubjectGradeSummary
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.NothingConcentricGradeRing
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.oneUiPillContainerColor
import com.example.ui.components.oneUiPillContentColor
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CijfersScreen(
    summaries: List<SubjectGradeSummary>,
    gradeUnboxingEnabled: Boolean = true,
    unboxedGradeIds: Set<String> = emptySet(),
    onOpenWhatIf: (SubjectGradeSummary) -> Unit,
    onOpenExportSheet: () -> Unit = {},
    onPlayGradeUnboxing: (GradeItem) -> Unit = {},
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
    // Overall GPA calculation
    val overallAverage = remember(summaries) {
        if (summaries.isNotEmpty()) {
            val total = summaries.sumOf { it.weightedAverage }
            round((total / summaries.size) * 10) / 10.0
        } else 0.0
    }

    val allGradesSorted = remember(summaries) {
        summaries.flatMap { it.grades }.sortedByDescending { it.date }
    }
    val nextUnrevealedGrade = remember(allGradesSorted, unboxedGradeIds, gradeUnboxingEnabled) {
        if (gradeUnboxingEnabled) allGradesSorted.firstOrNull { it.id !in unboxedGradeIds } else null
    }
    val latestGrade = nextUnrevealedGrade ?: allGradesSorted.firstOrNull()

    val failingSubjects = summaries.count { !it.isPassingAverage }
    val passingSubjects = summaries.count { it.isPassingAverage }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("cijfers_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        if (oneUiMode) {
            item(key = "oneui_cijfers_header") {
                OneUiExpandingHeader(
                    title = "Cijfers",
                    subtitle = "Algemeen gemiddelde: $overallAverage",
                    isScrolled = isScrolled
                )
            }
        }

        // GPA Overview Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gpa_overview_card"),
                shape = appCardShape(standard = 28.dp, nothing = 28.dp),
                border = nothingCardBorder(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isNothingOs) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NothingConcentricGradeRing(average = overallAverage)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "ALGEMEEN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Gemiddelde",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Algemeen Gemiddelde",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$overallAverage",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = if (oneUiMode) CircleShape else RoundedCornerShape(20.dp),
                            color = if (oneUiMode) oneUiPillContainerColor(isError = failingSubjects > 0) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Overgangsnorm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (oneUiMode) oneUiPillContentColor(isError = failingSubjects > 0) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (failingSubjects == 0) "Voldoende" else "$failingSubjects onvoldoende(s)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (oneUiMode) oneUiPillContentColor(isError = failingSubjects > 0) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$passingSubjects Voldoendes (≥ 5.5)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (failingSubjects > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$failingSubjects Onvoldoendes (< 5.5)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (failingSubjects > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Bar: Exporteer Cijfers + Cijfer Unboxing
        if (summaries.isNotEmpty()) {
            item(key = "cijfers_actions_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenExportSheet,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_grade_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exporteer", fontWeight = FontWeight.Bold)
                    }

                    if (latestGrade != null) {
                        Button(
                            onClick = { onPlayGradeUnboxing(latestGrade) },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (nextUnrevealedGrade != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("play_latest_unboxing_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (nextUnrevealedGrade != null) "Nieuw Cijfer!" else "Cijfer Unboxing",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section Title: Vakken & Cijfers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vakken & Behaalde Cijfers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Klik voor What-If",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (summaries.isEmpty()) {
            item {
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("empty_grades_card")
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Geen cijfers gevonden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cijfers worden automatisch geladen via je schoolkoppeling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(summaries) { summary ->
                SubjectGradeCard(
                    summary = summary,
                    gradeUnboxingEnabled = gradeUnboxingEnabled,
                    unboxedGradeIds = unboxedGradeIds,
                    onWhatIfClick = { onOpenWhatIf(summary) },
                    onUnboxGrade = onPlayGradeUnboxing
                )
            }
        }
    }
}

@Composable
fun SubjectGradeCard(
    summary: SubjectGradeSummary,
    gradeUnboxingEnabled: Boolean = true,
    unboxedGradeIds: Set<String> = emptySet(),
    onWhatIfClick: () -> Unit,
    onUnboxGrade: (GradeItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subject_card_${summary.subjectCode}"),
        shape = appCardShape(standard = 22.dp, nothing = 24.dp),
        border = nothingCardBorder(isAlert = !summary.isPassingAverage),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${summary.grades.size} cijfers ingevoerd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Average Pill
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = when {
                            isOneUi85 -> oneUiPillContainerColor(isError = !summary.isPassingAverage)
                            summary.isPassingAverage -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = "${summary.weightedAverage}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isOneUi85 -> oneUiPillContentColor(isError = !summary.isPassingAverage)
                                summary.isPassingAverage -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("expand_subject_${summary.subjectCode}")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded Grades History & What-If Button
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    summary.grades.forEach { grade ->
                        val isUnrevealed = gradeUnboxingEnabled && grade.id !in unboxedGradeIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = gradeUnboxingEnabled) { onUnboxGrade(grade) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = grade.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isUnrevealed) {
                                        "Tik om dit nieuwe cijfer te unboxen"
                                    } else {
                                        "${grade.date} • Weging ${grade.weight}x • Periode ${grade.period}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isUnrevealed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = if (isOneUi85) CircleShape else RoundedCornerShape(8.dp),
                                color = when {
                                    isUnrevealed -> MaterialTheme.colorScheme.primary
                                    isOneUi85 && !grade.isPassing -> oneUiPillContainerColor(isError = true)
                                    isOneUi85 && grade.isPassing -> oneUiPillContainerColor(isError = false)
                                    grade.isPassing -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    else -> MaterialTheme.colorScheme.errorContainer
                                }
                            ) {
                                Text(
                                    text = if (isUnrevealed) "?" else "${grade.score}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isUnrevealed -> MaterialTheme.colorScheme.onPrimary
                                        isOneUi85 -> oneUiPillContentColor(isError = !grade.isPassing)
                                        grade.isPassing -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onWhatIfClick,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("what_if_button_${summary.subjectCode}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("What-If Cijfer Berekenen voor ${summary.subjectName}")
                    }
                }
            }
        }
    }
}
