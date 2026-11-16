package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.TestPrepChecklist
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPrepChecklistSheet(
    checklist: TestPrepChecklist,
    onToggleTopic: (String) -> Unit,
    onAddTopic: (String) -> Unit,
    onRemoveTopic: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTopicInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("test_prep_checklist_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOneUi85) {
                                OneUiPillBadge(text = checklist.subject)
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = checklist.subject,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Studiechecklist",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = checklist.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_test_prep_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Progress summary
            Surface(
                shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voortgang voorbereiding",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${checklist.completedCount} / ${checklist.topics.size} klaar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { checklist.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            // Topics list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (checklist.topics.isEmpty()) {
                    item {
                        Text(
                            text = "Nog geen leeronderwerpen. Voeg hieronder je eerste onderwerp toe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(checklist.topics, key = { it.id }) { topic ->
                        Surface(
                            shape = appCardShape(standard = 14.dp, nothing = 18.dp),
                            color = if (topic.isChecked) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = nothingCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(appCardShape(standard = 14.dp, nothing = 18.dp))
                                .clickable { onToggleTopic(topic.id) }
                                .testTag("test_prep_topic_${topic.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = topic.isChecked,
                                    onCheckedChange = { onToggleTopic(topic.id) }
                                )
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (topic.isChecked) FontWeight.Normal else FontWeight.Medium,
                                    textDecoration = if (topic.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (topic.isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onRemoveTopic(topic.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Onderwerp verwijderen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add topic input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTopicInput,
                    onValueChange = { newTopicInput = it },
                    label = { Text("Onderwerp toevoegen") },
                    placeholder = { Text("Bijv. Paragraaf 3.2 oefenen") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_prep_add_topic_input")
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = newTopicInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddTopic(trimmed)
                            newTopicInput = ""
                        }
                    },
                    enabled = newTopicInput.isNotBlank(),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("test_prep_add_topic_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Toevoegen")
                }
            }
        }
    }
}
