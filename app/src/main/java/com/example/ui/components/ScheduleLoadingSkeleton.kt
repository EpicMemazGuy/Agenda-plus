package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AgendaStyle

@Composable
fun ScheduleLoadingSkeleton(
    agendaStyle: AgendaStyle,
    modifier: Modifier = Modifier,
    statusLabel: String = "Rooster en vakken ophalen..."
) {
    val transition = rememberInfiniteTransition(label = "schedule_skeleton_pulse")
    val pulseColor by transition.animateColor(
        initialValue = MaterialTheme.colorScheme.surfaceContainerLow,
        targetValue = MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_color"
    )
    val accentPulseColor by transition.animateColor(
        initialValue = MaterialTheme.colorScheme.surfaceContainer,
        targetValue = MaterialTheme.colorScheme.primaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_accent_color"
    )

    val nothingMode = isNothingOs
    val cardBorder: BorderStroke? = nothingCardBorder()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("schedule_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(if (agendaStyle == AgendaStyle.COMPACT) 6.dp else 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(if (nothingMode) 24.dp else 16.dp),
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
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        if (agendaStyle == AgendaStyle.COMPACT) {
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 24.dp else 18.dp),
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
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(pulseColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(9.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(pulseColor)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accentPulseColor)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(pulseColor)
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
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(pulseColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.36f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(pulseColor)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(pulseColor)
                            )
                        }
                    }
                }
            }
        } else {
            repeat(4) { idx ->
                Surface(
                    shape = RoundedCornerShape(if (nothingMode) 24.dp else 22.dp),
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentPulseColor)
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(pulseColor)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(pulseColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(pulseColor)
                        )
                    }
                }
            }
        }
    }
}
