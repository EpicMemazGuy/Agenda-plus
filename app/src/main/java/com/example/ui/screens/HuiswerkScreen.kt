package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.HomeworkTask
import com.example.model.StudyGuide
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.TestPrepChecklist
import com.example.model.capitalizeSubjectName
import com.example.model.isTestKeywordMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppThemedHeader
import com.example.ui.components.HomeworkLoadingSkeleton
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.LocalSubjectCustomizationState
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.agendaSpring
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appLiquidGlassContainerColor
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isIosLiquidGlass
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.isWindowsPhone
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.rememberSubjectDisplay
import com.example.ui.components.staggeredEntrance
import com.example.ui.theme.MetroCrimson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuiswerkScreen(
    tasks: List<HomeworkTask>,
    isRefreshing: Boolean = false,
    selectedCategory: TaskCategory?,
    testPrepChecklists: Map<String, TestPrepChecklist> = emptyMap(),
    studyGuides: List<StudyGuide> = emptyList(),
    eloUrl: String = "",
    onSelectCategory: (TaskCategory?) -> Unit,
    onToggleTask: (String) -> Unit,
    onAddTaskClick: () -> Unit,
    onOpenTestPrep: (key: String, subject: String, title: String, toetsstof: String, targetDateIso: String?) -> Unit = { _, _, _, _, _ -> },
    onOpenResourceUrl: (title: String, url: String) -> Unit = { _, _ -> },
    onStartFocusSession: (HomeworkTask) -> Unit = {},
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
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

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    val filteredTasks = remember(tasks, selectedCategory, todayIso) {
        tasks.filter { task ->
            if (selectedCategory != null && task.category != selectedCategory) {
                return@filter false
            }
            val isPast = task.dueDay.length == 10 && task.dueDay < todayIso
            !isPast
        }.sortedWith(
            compareBy<HomeworkTask> { it.isCompleted }
                .thenBy { if (it.dueDay.length == 10) it.dueDay else "9999-99-99" }
                .thenBy { it.dueDate }
        )
    }

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size
    val rawProgressFraction = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    val progressFraction by animateFloatAsState(
        targetValue = rawProgressFraction,
        animationSpec = agendaSpring(),
        label = "homework_progress"
    )

    val oneUiInsets = LocalOneUiScreenInsets.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (usesFrostedInset) Color.Transparent else MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("huiswerk_screen"),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptics.tick()
                    onAddTaskClick()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Taak toevoegen") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = if (oneUiMode || windowsPhoneMode || iosMode) appButtonShape() else FloatingActionButtonDefaults.extendedFabShape,
                elevation = if (usesFrostedInset || windowsPhoneMode) {
                    FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                } else {
                    FloatingActionButtonDefaults.elevation()
                },
                modifier = Modifier
                    .padding(bottom = oneUiInsets.calculateBottomPadding())
                    .testTag("add_task_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = appScreenHorizontalPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(
                top = oneUiInsets.calculateTopPadding() + 12.dp,
                bottom = oneUiInsets.calculateBottomPadding() + 80.dp
            )
        ) {
            if (oneUiMode || windowsPhoneMode || iosMode) {
                item(key = "themed_huiswerk_header") {
                    AppThemedHeader(
                        title = "Huiswerk",
                        subtitle = "$completedCount van $totalCount voltooid",
                        isScrolled = isScrolled
                    )
                }
            }

            // Header & Progress Card
            item(key = "homework_progress_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_progress_card"),
                    shape = appCardShape(standard = 24.dp, nothing = 24.dp),
                    border = nothingCardBorder(),
                    colors = CardDefaults.cardColors(
                        containerColor = appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Huiswerk & Toetsen",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (oneUiMode) {
                                OneUiPillBadge(text = "$completedCount / $totalCount")
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$completedCount van $totalCount voltooid",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(appThumbnailShape(standard = 4.dp, nothing = 4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }

            if (totalCount > 0 && completedCount == totalCount) {
                item(key = "homework_all_completed_card") {
                    Surface(
                        shape = appCardShape(standard = 20.dp, nothing = 22.dp),
                        color = appLiquidGlassContainerColor(MaterialTheme.colorScheme.primaryContainer),
                        border = nothingCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("homework_all_completed_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alles afgerond — lekker bezig!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Alle $totalCount taken zijn afgevinkt. Tijd om te ontspannen of vooruit te werken.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Chips Row
            item(key = "category_filter_row") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_filter_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "filter_chip_all") {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = {
                                haptics.tick()
                                onSelectCategory(null)
                            },
                            label = { Text("Alles ($totalCount)") },
                            modifier = Modifier.testTag("filter_chip_all")
                        )
                    }

                    items(TaskCategory.values(), key = { it.name }) { category ->
                        val count = tasks.count { it.category == category }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                haptics.tick()
                                onSelectCategory(category)
                            },
                            label = { Text("${category.labelDutch} ($count)") },
                            modifier = Modifier.testTag("filter_chip_${category.name}")
                        )
                    }
                }
            }

            // Task List
            if (isRefreshing && filteredTasks.isEmpty()) {
                item(key = "homework_loading_skeleton") {
                    HomeworkLoadingSkeleton()
                }
            } else if (filteredTasks.isEmpty()) {
                item(key = "empty_homework_state") {
                    Surface(
                        shape = appCardShape(standard = 20.dp, nothing = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Geen taken in deze categorie",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredTasks, key = { _, it -> it.id }) { idx, task ->
                    val isTest = task.isTest
                    val checklistKey = "task_${task.id}"
                    val checklist = testPrepChecklists[checklistKey]
                    val normalizedSubj = task.subject.trim().lowercase(Locale.getDefault())
                    val matchedResourceUrl = remember(task.subject, studyGuides, eloUrl) {
                        val guideUrl = studyGuides.firstOrNull { guide ->
                            val gs = guide.subjectName.trim().lowercase(Locale.getDefault())
                            val gc = guide.subjectCode.trim().lowercase(Locale.getDefault())
                            (gs.isNotBlank() && (normalizedSubj.contains(gs) || gs.contains(normalizedSubj))) ||
                                (gc.isNotBlank() && normalizedSubj == gc)
                        }?.parts?.flatMap { it.resources }
                            ?.firstOrNull { it.url.startsWith("http") }
                            ?.url
                        guideUrl ?: eloUrl.takeIf { it.startsWith("http") }
                    }
                    TaskCard(
                        task = task,
                        isTest = isTest,
                        checklistProgressText = checklist?.let { "${it.completedCount}/${it.topics.size} klaar" },
                        hasResourceLink = matchedResourceUrl != null,
                        onToggle = {
                            haptics.confirm()
                            onToggleTask(task.id)
                        },
                        onStartFocus = {
                            haptics.tick()
                            onStartFocusSession(task)
                        },
                        onOpenResource = matchedResourceUrl?.let { url ->
                            {
                                haptics.tick()
                                onOpenResourceUrl("${task.subject.capitalizeSubjectName()} • Bron", url)
                            }
                        },
                        onOpenStudyMode = if (isTest || task.isPresentation) {
                            {
                                haptics.tick()
                                val combinedStof = listOf(task.title, task.description)
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n")
                                onOpenTestPrep(
                                    checklistKey,
                                    task.subject.capitalizeSubjectName(),
                                    task.title,
                                    combinedStof,
                                    task.dueDay
                                )
                            }
                        } else null,
                        modifier = Modifier.staggeredEntrance(idx)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: HomeworkTask,
    isTest: Boolean = false,
    checklistProgressText: String? = null,
    hasResourceLink: Boolean = false,
    onToggle: () -> Unit,
    onStartFocus: () -> Unit = {},
    onOpenResource: (() -> Unit)? = null,
    onOpenStudyMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val windowsPhoneMode = isWindowsPhone
    val nothingMode = isNothingOs
    val customState = LocalSubjectCustomizationState.current
    val subjectDisplay = rememberSubjectDisplay(rawTitleOrName = task.subject)
    val isUrgentMetro = windowsPhoneMode && !task.isCompleted && (isTest || task.priority == TaskPriority.HIGH)
    val useMetroSubjectFill = windowsPhoneMode && !task.isCompleted && !isUrgentMetro &&
        customState.useSubjectColorsForMetroTiles && subjectDisplay.customColor != null
    val onWhiteMetroTile = isUrgentMetro || useMetroSubjectFill

    val cardShape = appCardShape(standard = 22.dp, nothing = 24.dp)
    val cardBg = when {
        isUrgentMetro -> MetroCrimson
        useMetroSubjectFill -> subjectDisplay.customColor!!
        task.isCompleted -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerLowest)
        else -> appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
    }
    val primaryTextColor = if (onWhiteMetroTile) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (onWhiteMetroTile) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onOpenStudyMode != null) {
                    Modifier.clip(cardShape).clickable(onClick = onOpenStudyMode)
                } else Modifier
            )
            .testTag("task_card_${task.id}"),
        shape = cardShape,
        border = nothingCardBorder(isAlert = task.priority == TaskPriority.HIGH && !task.isCompleted),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("checkbox_task_${task.id}")
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Voltooi",
                    tint = when {
                        onWhiteMetroTile -> Color.White
                        task.isCompleted -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val subjectBg = when {
                        onWhiteMetroTile -> Color.Black.copy(alpha = 0.22f)
                        !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val subjectFg = when {
                        onWhiteMetroTile -> Color.White
                        !nothingMode && subjectDisplay.customColor != null -> subjectDisplay.customColor
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    Surface(
                        shape = appButtonShape(),
                        color = subjectBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = subjectDisplay.icon,
                                contentDescription = null,
                                tint = subjectFg,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = subjectDisplay.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = subjectFg
                            )
                        }
                    }

                    Surface(
                        shape = appButtonShape(),
                        color = if (onWhiteMetroTile) Color.Black.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = task.category.labelDutch,
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (task.priority == TaskPriority.HIGH) {
                        Surface(
                            shape = appButtonShape(),
                            color = if (onWhiteMetroTile) Color.Black.copy(alpha = 0.28f) else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Urgent",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (onWhiteMetroTile) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (windowsPhoneMode) FontWeight.SemiBold else FontWeight.Bold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = primaryTextColor
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Inleveren: ${task.dueDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor
                        )
                    }
                    if (isTest && onOpenStudyMode != null) {
                        OneUiPillBadge(
                            text = checklistProgressText ?: "Studiechecklist",
                            modifier = Modifier.testTag("open_study_mode_${task.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onStartFocus,
                        shape = appButtonShape(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("focus_task_${task.id}")
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus", style = MaterialTheme.typography.labelMedium)
                    }
                    if (hasResourceLink && onOpenResource != null) {
                        OutlinedButton(
                            onClick = onOpenResource,
                            shape = appButtonShape(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("open_elo_resource_${task.id}")
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open ELO / Bron", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
