package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AgendaStyle

@Composable
fun rememberAgendaShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "agenda_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "agenda_shimmer_offset"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim, translateAnim * 0.35f),
        end = Offset(translateAnim + 420f, translateAnim * 0.35f + 220f)
    )
}

@Composable
fun ScheduleLoadingSkeleton(
    agendaStyle: AgendaStyle,
    modifier: Modifier = Modifier,
    statusLabel: String = "Rooster en vakken ophalen..."
) {
    val shimmerBrush = rememberAgendaShimmerBrush()
    val nothingMode = isNothingOs
    val metroMode = isWindowsPhone
    val cardBorder: BorderStroke? = nothingCardBorder()
    val blockShape = if (metroMode) RectangleShape else RoundedCornerShape(6.dp)
    val badgeShape = appButtonShape(standard = 50.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("schedule_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(if (agendaStyle == AgendaStyle.COMPACT) 8.dp else 12.dp)
    ) {
        Surface(
            shape = appCardShape(standard = 18.dp, nothing = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = cardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (nothingMode) {
                            NothingDotMatrixSpinner()
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (nothingMode) statusLabel.uppercase() else statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (nothingMode) {
                            NothingStatusDot(isUrgent = true)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "LIVE SYNC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (nothingMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (nothingMode) {
                    NothingDottedDivider()
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(if (metroMode) RectangleShape else RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        if (agendaStyle == AgendaStyle.COMPACT) {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    repeat(6) { idx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.width(42.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(34.dp)
                                        .height(10.dp)
                                        .clip(blockShape)
                                        .background(shimmerBrush)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(9.dp)
                                        .clip(blockShape)
                                        .background(shimmerBrush)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(if (metroMode) RectangleShape else RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(appThumbnailShape(standard = 8.dp, nothing = 8.dp))
                                    .background(shimmerBrush)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (idx % 2 == 0) 0.58f else 0.72f)
                                        .height(13.dp)
                                        .clip(blockShape)
                                        .background(shimmerBrush)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.36f)
                                        .height(10.dp)
                                        .clip(blockShape)
                                        .background(shimmerBrush)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(24.dp)
                                    .clip(badgeShape)
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        } else {
            repeat(4) { idx ->
                Surface(
                    shape = appCardShape(standard = 22.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(appThumbnailShape(standard = 12.dp, nothing = 12.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (idx % 2 == 0) 0.6f else 0.75f)
                                    .height(16.dp)
                                    .clip(blockShape)
                                    .background(shimmerBrush)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(12.dp)
                                    .clip(blockShape)
                                    .background(shimmerBrush)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(28.dp)
                                .clip(badgeShape)
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradesLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberAgendaShimmerBrush()
    val metroMode = isWindowsPhone
    val blockShape = if (metroMode) RectangleShape else RoundedCornerShape(6.dp)
    val cardBorder = nothingCardBorder()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("grades_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = appCardShape(standard = 22.dp, nothing = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = cardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(12.dp)
                            .clip(blockShape)
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(32.dp)
                            .clip(blockShape)
                            .background(shimmerBrush)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(if (metroMode) RectangleShape else CircleShape)
                        .background(shimmerBrush)
                )
            }
        }

        repeat(4) { idx ->
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(appThumbnailShape(standard = 12.dp, nothing = 12.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (idx % 2 == 0) 0.55f else 0.7f)
                                .height(14.dp)
                                .clip(blockShape)
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(10.dp)
                                .clip(blockShape)
                                .background(shimmerBrush)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(28.dp)
                            .clip(appButtonShape(standard = 10.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeworkLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberAgendaShimmerBrush()
    val metroMode = isWindowsPhone
    val blockShape = if (metroMode) RectangleShape else RoundedCornerShape(6.dp)
    val cardBorder = nothingCardBorder()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("homework_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) { idx ->
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(if (metroMode) RectangleShape else CircleShape)
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (idx % 2 == 0) 0.65f else 0.5f)
                                .height(14.dp)
                                .clip(blockShape)
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(11.dp)
                                .clip(blockShape)
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}
