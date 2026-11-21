package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.example.model.GradeScopeFilter
import com.example.model.SubjectGradeSummary
import com.example.model.capitalizeSubjectName
import java.util.Locale
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.NothingConcentricGradeRing
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.oneUiPillContainerColor
import com.example.ui.components.oneUiPillContentColor
import com.example.ui.components.oneUiSpring
import com.example.ui.components.oneUiTween
import kotlin.math.round

fun filterGradeSummariesByScope(
    summaries: List<SubjectGradeSummary>,
    filter: GradeScopeFilter
): List<SubjectGradeSummary> {
    if (filter == GradeScopeFilter.ALL) return summaries
    return summaries.mapNotNull { summary ->
        val matchingGrades = summary.grades.filter { grade ->
            when (filter) {
                GradeScopeFilter.ALL -> true
                GradeScopeFilter.REGULAR -> !grade.isPta
                GradeScopeFilter.PTA -> grade.isPta
            }
        }
        if (matchingGrades.isEmpty()) {
            null
        } else {
            val weightSum = matchingGrades.sumOf { it.weight }.coerceAtLeast(0.01)
            val weightedAvg = round((matchingGrades.sumOf { it.score * it.weight } / weightSum) * 10) / 10.0
            summary.copy(
                weightedAverage = weightedAvg,
                grades = matchingGrades
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CijfersScreen(
    summaries: List<SubjectGradeSummary>,
    gradeUnboxingEnabled: Boolean = true,
    unboxedGradeIds: Set<String> = emptySet(),
    onOpenWhatIf: (SubjectGradeSummary) -> Unit,
    onOpenExportSheet: () -> Unit = {},
    onOpenOvergangsSimulator: () -> Unit = {},
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

    var selectedScope by remember { mutableStateOf(GradeScopeFilter.ALL) }
    val filteredSummaries = remember(summaries, selectedScope) {
        filterGradeSummariesByScope(summaries, selectedScope)
    }

    // Overall GPA calculation
    val overallAverage = remember(filteredSummaries) {
        if (filteredSummaries.isNotEmpty()) {
            val total = filteredSummaries.sumOf { it.weightedAverage }
            round((total / filteredSummaries.size) * 10) / 10.0
        } else 0.0
    }

    val allGradesSorted = remember(filteredSummaries) {
        filteredSummaries.flatMap { it.grades }.sortedByDescending { it.date }
    }
    val nextUnrevealedGrade = remember(allGradesSorted, unboxedGradeIds, gradeUnboxingEnabled) {
        if (gradeUnboxingEnabled) allGradesSorted.firstOrNull { it.id !in unboxedGradeIds } else null
    }
    val latestGrade = nextUnrevealedGrade ?: allGradesSorted.firstOrNull()

    val failingSubjects = filteredSummaries.count { !it.isPassingAverage }
    val passingSubjects = filteredSummaries.count { it.isPassingAverage }

    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("cijfers_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            top = oneUiInsets.calculateTopPadding() + 12.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 24.dp
        )
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

                        val overgangShape = if (oneUiMode) CircleShape else RoundedCornerShape(20.dp)
                        Surface(
                            shape = overgangShape,
                            color = if (oneUiMode) oneUiPillContainerColor(isError = failingSubjects > 0) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(overgangShape)
                                .clickable(onClick = onOpenOvergangsSimulator)
                                .minimumInteractiveComponentSize()
                                .testTag("open_overgangsnorm_simulator")
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
                                    text = if (failingSubjects == 0) "Voldoende • Simuleer" else "$failingSubjects onvoldoende(s) • Simuleer",
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

        // Scope Filter: Alle / Regulier / PTA
        item(key = "cijfers_scope_filter") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grade_scope_filter_row")
            ) {
                GradeScopeFilter.entries.forEachIndexed { idx, scopeFilter ->
                    SegmentedButton(
                        selected = selectedScope == scopeFilter,
                        onClick = { selectedScope = scopeFilter },
                        shape = SegmentedButtonDefaults.itemShape(idx, GradeScopeFilter.entries.size),
                        modifier = Modifier.testTag("grade_scope_${scopeFilter.name}")
                    ) {
                        Text(scopeFilter.labelDutch, maxLines = 1)
                    }
                }
            }
        }

        // Action Bar: Exporteer Cijfers + Cijfer Unboxing
        if (filteredSummaries.isNotEmpty()) {
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

        if (filteredSummaries.isEmpty()) {
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
                            text = if (selectedScope == GradeScopeFilter.ALL) "Geen cijfers gevonden" else "Geen ${selectedScope.labelDutch.lowercase()}-cijfers gevonden",
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
            items(filteredSummaries, key = { it.subjectCode + "_" + it.subjectName }) { summary ->
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
            val rowTouchShape = appInnerCardShape(standard = 12.dp, nothing = 12.dp)
            val displaySubject = summary.subjectName.capitalizeSubjectName()
            val trendDelta = remember(summary) {
                if (summary.grades.size < 2) {
                    null
                } else {
                    val sorted = summary.grades.sortedByDescending { it.date }
                    val previous = sorted.drop(1)
                    val prevWeight = previous.sumOf { it.weight }.coerceAtLeast(0.01)
                    val prevAvg = round((previous.sumOf { it.score * it.weight } / prevWeight) * 10) / 10.0
                    val delta = round((summary.weightedAverage - prevAvg) * 10) / 10.0
                    delta.takeIf { kotlin.math.abs(it) >= 0.1 }
                }
            }
            val isNearEdge = summary.weightedAverage in 5.5..5.7

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowTouchShape)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displaySubject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isNearEdge) {
                            OneUiPillBadge(
                                text = "Op het randje",
                                isError = true
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${summary.grades.size} cijfers ingevoerd",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        trendDelta?.let { delta ->
                            val sign = if (delta > 0) "+" else ""
                            OneUiPillBadge(
                                text = "$sign${String.format(Locale.US, "%.1f", delta)}",
                                isError = delta < 0.0
                            )
                        }
                    }
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
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(oneUiSpring()) + fadeIn(oneUiTween()),
                exit = shrinkVertically(oneUiSpring()) + fadeOut(oneUiTween())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingAwareDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        insetStart = if (isOneUi85) 12.dp else 0.dp
                    )

                    summary.grades.forEachIndexed { gradeIdx, grade ->
                        val isUnrevealed = gradeUnboxingEnabled && grade.id !in unboxedGradeIds
                        val gradeTouchShape = appInnerCardShape(standard = 10.dp, nothing = 10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(gradeTouchShape)
                                .clickable(enabled = gradeUnboxingEnabled) { onUnboxGrade(grade) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = grade.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (grade.isPta) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        OneUiPillBadge(text = "PTA")
                                    }
                                }
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
                        if (isOneUi85 && gradeIdx < summary.grades.lastIndex) {
                            NothingAwareDivider(insetStart = 12.dp)
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
                        Text("What-If Cijfer Berekenen voor ${summary.subjectName.capitalizeSubjectName()}")
                    }
                }
            }
        }
    }
}
