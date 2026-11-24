package com.example.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppThemeStyle
import com.example.model.GradeItem
import com.example.model.GradeUnboxingMode
import com.example.model.capitalizeSubjectName
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.LocalSubjectCustomizationState
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.theme.LocalAxolaCustomTheme
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.LocalMetroAccent
import com.example.util.GradeExporter
import com.example.util.StoryAndScheduleExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

@Composable
fun GradeUnboxingDialog(
    grade: GradeItem,
    initialMode: GradeUnboxingMode,
    appThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    studentName: String = "",
    onChangeMode: (GradeUnboxingMode) -> Unit,
    onRevealComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberAgendaHaptics()
    var activeMode by remember(initialMode) { mutableStateOf(initialMode) }
    var isRevealed by remember(grade.id, activeMode) { mutableStateOf(false) }

    LaunchedEffect(isRevealed, grade.id) {
        if (isRevealed) {
            if (grade.score >= 8.0) {
                haptics.celebrationOrAlert()
            } else {
                haptics.confirm()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isRevealed) onRevealComplete()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = nothingCardBorder(isAlert = isRevealed),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("grade_unboxing_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cijfer Unboxing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${grade.subjectName.capitalizeSubjectName()} • Weging ${grade.weight}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isRevealed) onRevealComplete()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                // Mode selector chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GradeUnboxingMode.entries) { mode ->
                        FilterChip(
                            selected = activeMode == mode,
                            onClick = {
                                haptics.tick()
                                activeMode = mode
                                onChangeMode(mode)
                                isRevealed = false
                            },
                            label = { Text(mode.labelDutch) },
                            modifier = Modifier.testTag("unboxing_mode_chip_${mode.name}")
                        )
                    }
                }

                // Minigame Canvas / Reveal Card
                AnimatedContent(
                    targetState = isRevealed,
                    label = "unboxing_reveal_state"
                ) { revealed ->
                    if (revealed) {
                        GradeRevealedCelebrationCard(
                            grade = grade,
                            appThemeStyle = appThemeStyle,
                            studentName = studentName,
                            onReplay = { isRevealed = false },
                            onDone = {
                                onRevealComplete()
                                onDismiss()
                            }
                        )
                    } else {
                        when (activeMode) {
                            GradeUnboxingMode.CASE_OPENING -> {
                                CaseOpeningMinigame(
                                    grade = grade,
                                    onRevealed = { isRevealed = true }
                                )
                            }
                            GradeUnboxingMode.TAP_IT_UP -> {
                                TapItUpMinigame(
                                    grade = grade,
                                    onRevealed = { isRevealed = true }
                                )
                            }
                            GradeUnboxingMode.PENALTIES -> {
                                PenaltiesMinigame(
                                    grade = grade,
                                    onRevealed = { isRevealed = true }
                                )
                            }
                            GradeUnboxingMode.ULTIMATE_GRADE -> {
                                UltimateGradeMinigame(
                                    grade = grade,
                                    onRevealed = { isRevealed = true }
                                )
                            }
                            GradeUnboxingMode.PLINKO_DROP -> {
                                PlinkoDropMinigame(
                                    grade = grade,
                                    onRevealed = { isRevealed = true }
                                )
                            }
                        }
                    }
                }

                if (!isRevealed) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            isRevealed = true
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("unboxing_skip_button")
                    ) {
                        Text("Toon cijfer direct")
                    }
                }
            }
        }
    }
}

private fun gradeRarityColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFFFB300) // Gold/Red tier
    score >= 5.5 -> Color(0xFF7C3AED) // Purple/Blue tier
    else -> Color(0xFF6B7280) // Grey tier
}

private fun gradeRaritySecondaryColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFD71920)
    score >= 5.5 -> Color(0xFF2563EB)
    else -> Color(0xFF4B5563)
}

private fun gradeRarityLabel(score: Double): String = when {
    score >= 8.5 -> "LEGENDARISCH"
    score >= 8.0 -> "GOUD"
    score >= 6.8 -> "ZELDZAAM"
    score >= 5.5 -> "VOLDOENDE"
    else -> "ONVOLDOENDE"
}

@Composable
private fun GradeRevealedCelebrationCard(
    grade: GradeItem,
    appThemeStyle: AppThemeStyle,
    studentName: String,
    onReplay: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAgendaHaptics()
    val isDarkMode = LocalDarkThemeActive.current
    val dynamicPrimary = MaterialTheme.colorScheme.primary
    val primaryRarity = gradeRarityColor(grade.score)
    val secondaryRarity = gradeRaritySecondaryColor(grade.score)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = nothingCardBorder(isAlert = grade.score >= 8.0),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unboxing_revealed_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            primaryRarity.copy(alpha = 0.22f),
                            secondaryRarity.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = primaryRarity
            ) {
                Text(
                    text = gradeRarityLabel(grade.score),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Text(
                text = String.format(Locale.US, "%.1f", grade.score),
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (grade.isPassing) primaryRarity else MaterialTheme.colorScheme.error
            )

            Text(
                text = grade.subjectName.capitalizeSubjectName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "${grade.description} • Weging ${grade.weight}x • ${grade.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val customizationState = LocalSubjectCustomizationState.current
            val metroAccent = LocalMetroAccent.current
            val axolaCustomTheme = LocalAxolaCustomTheme.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val primaryArgb = android.graphics.Color.argb(
                            (dynamicPrimary.alpha * 255).toInt(),
                            (dynamicPrimary.red * 255).toInt(),
                            (dynamicPrimary.green * 255).toInt(),
                            (dynamicPrimary.blue * 255).toInt()
                        )
                        val file = StoryAndScheduleExporter.renderGradeStoryPng(
                            context = context,
                            grade = grade,
                            subjectAverage = null,
                            overallAverage = null,
                            studentName = studentName,
                            customizationState = customizationState,
                            themeStyle = appThemeStyle,
                            axolaTheme = axolaCustomTheme,
                            metroAccent = metroAccent,
                            isDarkMode = isDarkMode,
                            dynamicPrimaryArgb = primaryArgb
                        )
                        GradeExporter.shareFileViaProvider(
                            context = context,
                            file = file,
                            mimeType = "image/png",
                            chooserTitle = "Deel 9:16 Cijfer Story"
                        )
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("unboxing_share_story_button")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("9:16 Story", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        val primaryArgb = android.graphics.Color.argb(
                            (dynamicPrimary.alpha * 255).toInt(),
                            (dynamicPrimary.red * 255).toInt(),
                            (dynamicPrimary.green * 255).toInt(),
                            (dynamicPrimary.blue * 255).toInt()
                        )
                        val file = GradeExporter.renderSingleGradeUnboxingCardPng(
                            context = context,
                            grade = grade,
                            studentName = studentName,
                            appThemeStyle = appThemeStyle,
                            isDarkMode = isDarkMode,
                            dynamicPrimaryArgb = primaryArgb
                        )
                        GradeExporter.shareFileViaProvider(
                            context = context,
                            file = file,
                            mimeType = "image/png",
                            chooserTitle = "Deel Cijferkaart"
                        )
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("unboxing_share_card_button")
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cijferkaart", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReplay,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Opnieuw")
                }
                Button(
                    onClick = {
                        haptics.confirm()
                        onDone()
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("unboxing_done_button")
                ) {
                    Text("Opslaan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CaseOpeningMinigame(
    grade: GradeItem,
    onRevealed: () -> Unit
) {
    val winnerIndex = 28
    val tiles = remember(grade.id) {
        val decoys = listOf(4.2, 5.1, 5.8, 6.4, 7.1, 7.6, 8.3, 9.2, 4.8, 6.9, 8.7, 5.5)
        List(36) { idx ->
            if (idx == winnerIndex) grade.score else decoys[(idx * 7 + grade.id.hashCode().let { abs(it) }) % decoys.size]
        }
    }

    val scrollIndex = remember(grade.id) { Animatable(0f) }
    var isSpinning by remember(grade.id) { mutableStateOf(true) }

    LaunchedEffect(grade.id) {
        isSpinning = true
        scrollIndex.snapTo(0f)
        scrollIndex.animateTo(
            targetValue = winnerIndex.toFloat(),
            animationSpec = tween(
                durationMillis = 3600,
                easing = CubicBezierEasing(0.10f, 0.88f, 0.18f, 1.0f)
            )
        )
        isSpinning = false
        delay(450)
        onRevealed()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minigame_case_opening"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isSpinning) "Case wordt geopend..." else "Geland op jouw cijfer!",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E0E12))
        ) {
            val currentOffset = scrollIndex.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val cardW = 112.dp.toPx()
                val gap = 10.dp.toPx()
                val step = cardW + gap
                val cardH = 98.dp.toPx()
                val topY = (size.height - cardH) / 2f

                tiles.forEachIndexed { idx, value ->
                    val x = centerX + (idx - currentOffset) * step - cardW / 2f
                    if (x + cardW >= -20f && x <= size.width + 20f) {
                        val rarity = gradeRarityColor(value)
                        val rarityBottom = gradeRaritySecondaryColor(value)
                        drawRoundRect(
                            color = Color(0xFF1D1D24),
                            topLeft = Offset(x, topY),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(rarity, rarityBottom)),
                            topLeft = Offset(x, topY + cardH - 12.dp.toPx()),
                            size = Size(cardW, 12.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawRoundRect(
                            color = rarity.copy(alpha = 0.6f),
                            topLeft = Offset(x, topY),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Center selector needle
                drawLine(
                    color = Color(0xFFD71920),
                    start = Offset(centerX, 6.dp.toPx()),
                    end = Offset(centerX, size.height - 6.dp.toPx()),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Overlay floating text for visible cards around center
            val centerCard = currentOffset.toInt().coerceIn(0, tiles.lastIndex)
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(centerCard - 1, centerCard, centerCard + 1).forEach { idx ->
                    val score = tiles.getOrNull(idx)
                    Text(
                        text = score?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                        fontSize = if (idx == centerCard) 26.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (idx == centerCard) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TapItUpMinigame(
    grade: GradeItem,
    onRevealed: () -> Unit
) {
    var taps by remember(grade.id) { mutableIntStateOf(0) }
    val haptics = rememberAgendaHaptics()
    val targetTaps = 10
    val progress = (taps.toFloat() / targetTaps).coerceIn(0f, 1f)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(taps) {
        if (taps >= targetTaps) {
            delay(250)
            onRevealed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minigame_tap_it_up"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tik zo snel mogelijk om het cijfer open te breken!",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((158 * scale.value).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = taps < targetTaps) {
                    haptics.tick()
                    taps++
                    scope.launch {
                        scale.snapTo(1.12f)
                        scale.animateTo(1f, tween(140))
                    }
                }
                .testTag("tap_it_up_target")
        ) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 10.dp,
                color = if (progress > 0.7f) Color(0xFFD71920) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (taps >= targetTaps) Icons.Default.LockOpen else Icons.Default.TouchApp,
                    contentDescription = "Tik om te openen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Text(
            text = "$taps / $targetTaps tikken",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PenaltiesMinigame(
    grade: GradeItem,
    onRevealed: () -> Unit
) {
    var chosenCorner by remember(grade.id) { mutableStateOf<Int?>(null) } // 0=Links, 1=Midden, 2=Rechts
    var keeperCorner by remember(grade.id) { mutableIntStateOf(1) }
    var attemptCount by remember(grade.id) { mutableIntStateOf(0) }
    var statusText by remember(grade.id) { mutableStateOf("Kies een hoek en schiet!") }
    val haptics = rememberAgendaHaptics()
    val ballProgress = remember(grade.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun takeShot(corner: Int) {
        if (ballProgress.isRunning) return
        haptics.tick()
        attemptCount++
        chosenCorner = corner
        // Keeper can save shot 1 occasionally for suspense, never blocks shot 2+
        keeperCorner = if (attemptCount == 1 && Random.nextBoolean()) {
            corner
        } else {
            listOf(0, 1, 2).first { it != corner }
        }
        scope.launch {
            ballProgress.snapTo(0f)
            ballProgress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            if (chosenCorner != keeperCorner) {
                statusText = "GOAL! Scoren = Cijfer onthuld!"
                delay(450)
                onRevealed()
            } else {
                statusText = "Keeper redt 'm! Schiet nog een keer!"
                delay(450)
                ballProgress.snapTo(0f)
                chosenCorner = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minigame_penalties"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14532D))
        ) {
            val t = ballProgress.value
            val targetCorner = chosenCorner ?: 1
            Canvas(modifier = Modifier.fillMaxSize()) {
                val goalLeft = size.width * 0.14f
                val goalRight = size.width * 0.86f
                val goalTop = size.height * 0.14f
                val goalBottom = size.height * 0.54f

                // Goal frame
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(goalLeft, goalTop),
                    size = Size(goalRight - goalLeft, goalBottom - goalTop)
                )
                drawLine(Color.White, Offset(goalLeft, goalBottom), Offset(goalLeft, goalTop), strokeWidth = 6f)
                drawLine(Color.White, Offset(goalLeft, goalTop), Offset(goalRight, goalTop), strokeWidth = 6f)
                drawLine(Color.White, Offset(goalRight, goalTop), Offset(goalRight, goalBottom), strokeWidth = 6f)

                // Scoreboard in goal
                val targetPositions = listOf(
                    size.width * 0.25f,
                    size.width * 0.50f,
                    size.width * 0.75f
                )

                // Keeper
                val keeperBaseX = size.width * 0.5f
                val keeperTargetX = targetPositions[keeperCorner]
                val keeperX = keeperBaseX + (keeperTargetX - keeperBaseX) * t
                val keeperY = goalBottom - 16.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(keeperX - 18.dp.toPx(), keeperY - 26.dp.toPx()),
                    size = Size(36.dp.toPx(), 42.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )

                // Ball
                val ballStartX = size.width * 0.5f
                val ballStartY = size.height * 0.84f
                val ballEndX = targetPositions[targetCorner]
                val ballEndY = goalTop + (goalBottom - goalTop) * 0.55f
                val bx = ballStartX + (ballEndX - ballStartX) * t
                val by = ballStartY + (ballEndY - ballStartY) * t
                val radius = (13f - 4f * t).dp.toPx()

                drawCircle(color = Color.White, radius = radius, center = Offset(bx, by))
                drawCircle(
                    color = Color.Black,
                    radius = radius,
                    center = Offset(bx, by),
                    style = Stroke(width = 2f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0 to "Links", 1 to "Midden", 2 to "Rechts").forEach { (corner, label) ->
                Button(
                    onClick = { takeShot(corner) },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("penalty_shoot_$corner")
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun UltimateGradeMinigame(
    grade: GradeItem,
    onRevealed: () -> Unit
) {
    var stage by remember(grade.id) { mutableIntStateOf(0) }
    val flareProgress = remember(grade.id) { Animatable(0f) }

    LaunchedEffect(grade.id) {
        stage = 0
        flareProgress.snapTo(0f)
        flareProgress.animateTo(1f, tween(900))
        stage = 1
        delay(850)
        stage = 2
        delay(900)
        onRevealed()
    }

    val rarity = gradeRarityColor(grade.score)
    val secondary = gradeRaritySecondaryColor(grade.score)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A0A10))
            .testTag("minigame_ultimate_grade"),
        contentAlignment = Alignment.Center
    ) {
        val fp = flareProgress.value
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Walkout tunnel beams
            val center = Offset(size.width / 2f, size.height * 0.48f)
            val beamCount = 8
            for (i in 0 until beamCount) {
                val angle = (i - beamCount / 2f) * 0.25f
                val endX = center.x + kotlin.math.sin(angle) * size.width * 0.7f * fp
                val endY = center.y - kotlin.math.cos(angle) * size.height * 0.85f * fp
                drawLine(
                    color = rarity.copy(alpha = 0.28f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 28f
                )
            }

            // Shield outline
            if (stage >= 1) {
                val w = 130.dp.toPx()
                val h = 152.dp.toPx()
                val left = center.x - w / 2f
                val top = center.y - h / 2f
                val path = Path().apply {
                    moveTo(center.x, top)
                    lineTo(left + w, top + h * 0.18f)
                    lineTo(left + w * 0.88f, top + h * 0.76f)
                    lineTo(center.x, top + h)
                    lineTo(left + w * 0.12f, top + h * 0.76f)
                    lineTo(left, top + h * 0.18f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(listOf(rarity, secondary))
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            when (stage) {
                0 -> {
                    Text(
                        text = "WALKOUT WORDT GESTART...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                1 -> {
                    Text(
                        text = grade.subjectName.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "WEGING ${grade.weight}X",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                else -> {
                    Text(
                        text = String.format(Locale.US, "%.1f", grade.score),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = grade.subjectName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PlinkoDropMinigame(
    grade: GradeItem,
    onRevealed: () -> Unit
) {
    var dropStarted by remember(grade.id) { mutableStateOf(false) }
    val haptics = rememberAgendaHaptics()
    var ballX by remember(grade.id) { mutableFloatStateOf(0.5f) }
    var ballY by remember(grade.id) { mutableFloatStateOf(0.08f) }
    var finishedSlot by remember(grade.id) { mutableStateOf<Int?>(null) }

    val slotScores = remember(grade.id) {
        listOf(5.2, 6.5, grade.score, 7.4, 8.1)
    }

    LaunchedEffect(dropStarted) {
        if (!dropStarted) return@LaunchedEffect
        var x = 0.49f + (Random.nextFloat() - 0.5f) * 0.04f
        var y = 0.08f
        var vx = (Random.nextFloat() - 0.5f) * 0.008f
        var vy = 0.002f
        val rows = 6

        val pegs = buildList {
            for (r in 0 until rows) {
                val count = r + 3
                val py = 0.20f + r * 0.105f
                for (c in 0 until count) {
                    val span = 0.72f
                    val px = 0.5f - (span / 2f) * (count - 1).toFloat() / (rows + 1) +
                        c * (span / (rows + 1))
                    add(Offset(px, py))
                }
            }
        }

        while (y < 0.85f) {
            vy += 0.0011f
            x += vx
            y += vy

            // Gentle guidance toward center winning slot (slot 2)
            if (y > 0.60f) {
                vx += (0.5f - x) * 0.004f
            }

            if (x < 0.12f) {
                x = 0.12f
                vx = abs(vx) * 0.7f
            } else if (x > 0.88f) {
                x = 0.88f
                vx = -abs(vx) * 0.7f
            }

            for (peg in pegs) {
                val dx = x - peg.x
                val dy = y - peg.y
                val dist = hypot(dx, dy)
                val minDist = 0.036f
                if (dist < minDist && dist > 0.0001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    x = peg.x + nx * minDist
                    y = peg.y + ny * minDist
                    val dot = vx * nx + vy * ny
                    vx = (vx - 1.5f * dot * nx) + (Random.nextFloat() - 0.5f) * 0.003f
                    vy = (vy - 1.5f * dot * ny).coerceAtLeast(0.004f)
                }
            }

            ballX = x
            ballY = y
            delay(16)
        }

        ballX = 0.5f
        ballY = 0.86f
        finishedSlot = 2
        delay(500)
        onRevealed()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minigame_plinko"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (!dropStarted) "Laat de bal vallen naar jouw cijfervak!" else "Bal onderweg...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0F172A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = 6
                for (r in 0 until rows) {
                    val count = r + 3
                    val py = (0.20f + r * 0.105f) * size.height
                    for (c in 0 until count) {
                        val span = 0.72f
                        val px = (0.5f - (span / 2f) * (count - 1).toFloat() / (rows + 1) +
                            c * (span / (rows + 1))) * size.width
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = 4.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Bottom 5 multiplier/grade slots
                val slotCount = slotScores.size
                val slotW = (size.width * 0.84f) / slotCount
                val startX = size.width * 0.08f
                val slotTop = size.height * 0.83f
                val slotH = size.height * 0.13f

                for (s in 0 until slotCount) {
                    val sx = startX + s * slotW + 3.dp.toPx()
                    val isWinner = s == 2
                    val color = if (isWinner) Color(0xFFD71920) else Color(0xFF334155)
                    drawRoundRect(
                        color = if (finishedSlot == s) Color(0xFFFFB300) else color,
                        topLeft = Offset(sx, slotTop),
                        size = Size(slotW - 6.dp.toPx(), slotH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }

                // Plinko ball
                drawCircle(
                    color = Color(0xFFFFB300),
                    radius = 9.dp.toPx(),
                    center = Offset(ballX * size.width, ballY * size.height)
                )
            }
        }

        if (!dropStarted) {
            Button(
                onClick = {
                    haptics.tick()
                    dropStarted = true
                },
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plinko_drop_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Laat bal vallen", fontWeight = FontWeight.Bold)
            }
        }
    }
}
