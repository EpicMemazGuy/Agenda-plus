package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HomeworkTask
import com.example.model.capitalizeSubjectName
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PomodoroTimerDialog(
    task: HomeworkTask,
    onCompleteSession: (minutesFocused: Int, markTaskDone: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var focusMinutes by remember { mutableIntStateOf(25) }
    var breakMinutes by remember { mutableIntStateOf(5) }
    var isBreakPhase by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var markTaskDoneOnFinish by remember { mutableStateOf(!task.isCompleted) }
    var sessionCompletedBanner by remember { mutableStateOf(false) }

    val totalPhaseSeconds = (if (isBreakPhase) breakMinutes else focusMinutes) * 60
    val progress = if (totalPhaseSeconds > 0) {
        (remainingSeconds.toFloat() / totalPhaseSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(isRunning, isBreakPhase, remainingSeconds) {
        if (!isRunning) return@LaunchedEffect
        if (remainingSeconds <= 0) {
            isRunning = false
            if (!isBreakPhase) {
                sessionCompletedBanner = true
                onCompleteSession(focusMinutes, markTaskDoneOnFinish)
                isBreakPhase = true
                remainingSeconds = breakMinutes * 60
            } else {
                isBreakPhase = false
                remainingSeconds = focusMinutes * 60
            }
            return@LaunchedEffect
        }
        delay(1000L)
        remainingSeconds -= 1
    }

    val mins = (remainingSeconds / 60).coerceAtLeast(0)
    val secs = (remainingSeconds % 60).coerceAtLeast(0)
    val formattedClock = String.format(Locale.US, "%02d:%02d", mins, secs)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = appCardShape(standard = 28.dp, nothing = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = nothingCardBorder(isAlert = isRunning && !isBreakPhase),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("pomodoro_timer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OneUiPillBadge(
                                text = task.subject.capitalizeSubjectName().ifBlank { "Studie" }
                            )
                            Text(
                                text = if (isBreakPhase) "Pauze" else "Focus Timer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isBreakPhase) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pomodoro_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Sluit focustimer")
                    }
                }

                // Focus / Break preset rows
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Focusduur",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 45).forEach { preset ->
                            FilterChip(
                                selected = focusMinutes == preset && !isBreakPhase,
                                onClick = {
                                    focusMinutes = preset
                                    if (!isRunning) {
                                        isBreakPhase = false
                                        remainingSeconds = preset * 60
                                        sessionCompletedBanner = false
                                    }
                                },
                                label = { Text("$preset min") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pomodoro_focus_preset_$preset")
                            )
                        }
                    }

                    Text(
                        text = "Pauzeduur",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10).forEach { preset ->
                            FilterChip(
                                selected = breakMinutes == preset,
                                onClick = {
                                    breakMinutes = preset
                                    if (!isRunning && isBreakPhase) {
                                        remainingSeconds = preset * 60
                                    }
                                },
                                label = { Text("$preset min pauze") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pomodoro_break_preset_$preset")
                            )
                        }
                    }
                }

                // Circular Countdown Display
                Surface(
                    shape = appInnerCardShape(standard = 22.dp, nothing = 22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(164.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                strokeWidth = 10.dp,
                                color = if (isBreakPhase) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedClock,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("pomodoro_clock_text")
                                )
                                Text(
                                    text = if (isBreakPhase) "Even opladen" else if (isRunning) "Bezig met focussen" else "Klaar om te starten",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (sessionCompletedBanner) {
                            OneUiPillBadge(
                                text = "Sessie voltooid (+$focusMinutes min opgeslagen)"
                            )
                        }
                    }
                }

                // Auto-check task option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = markTaskDoneOnFinish,
                        onCheckedChange = { markTaskDoneOnFinish = it },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pomodoro_mark_done_checkbox")
                    )
                    Text(
                        text = "Taak automatisch afvinken",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isRunning = false
                            isBreakPhase = false
                            remainingSeconds = focusMinutes * 60
                            sessionCompletedBanner = false
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pomodoro_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Reset timer",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Button(
                        onClick = { isRunning = !isRunning },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pomodoro_start_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Pauzeer" else "Start",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            isRunning = false
                            val elapsedSec = ((focusMinutes * 60) - remainingSeconds).coerceAtLeast(0)
                            val focusedMins = if (isBreakPhase) {
                                focusMinutes
                            } else if (elapsedSec == 0) {
                                focusMinutes
                            } else {
                                ((elapsedSec + 59) / 60).coerceAtLeast(1)
                            }
                            onCompleteSession(focusedMins, markTaskDoneOnFinish)
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pomodoro_finish_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nu afronden", maxLines = 1)
                    }
                }
            }
        }
    }
}
