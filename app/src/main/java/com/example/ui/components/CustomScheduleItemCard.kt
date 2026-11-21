package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomScheduleItem

@Composable
fun customAccentColor(colorKey: String): Color {
    return when (colorKey) {
        "EMERALD" -> MaterialTheme.colorScheme.secondary
        "AMBER" -> MaterialTheme.colorScheme.tertiary
        "PURPLE" -> MaterialTheme.colorScheme.primary
        "CORAL" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun CustomScheduleItemCard(
    item: CustomScheduleItem,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = customAccentColor(item.colorKey)
    val shape = appCardShape(standard = 18.dp, nothing = 24.dp)
    val baseColor = if (item.isCompleted) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        shape = shape,
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = appLiquidGlassContainerColor(defaultColor = baseColor)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onEdit)
            .testTag("custom_schedule_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onToggleCompleted(!item.isCompleted) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_custom_item_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Persoonlijk blok afvinken",
                    tint = if (item.isCompleted) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isOneUi85 || isIosLiquidGlass) {
                        OneUiPillBadge(
                            text = if (item.isRecurring) "Herhalend" else "Eigen blok",
                            containerColor = accent.copy(alpha = 0.16f),
                            contentColor = accent
                        )
                    } else {
                        Surface(
                            shape = if (isWindowsPhone) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(percent = 50),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventNote,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (item.isRecurring) "Herhalend" else "Eigen blok",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }
                    }
                    Text(
                        text = "${item.startTime} - ${item.endTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isOneUi85) FontWeight.Medium else FontWeight.Bold,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_custom_item_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Persoonlijk blok verwijderen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
