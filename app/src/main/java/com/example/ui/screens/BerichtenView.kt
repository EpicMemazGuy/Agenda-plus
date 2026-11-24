package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.MagisterMessage
import com.example.model.MessageFolder
import com.example.model.MessageFolderInfo
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.staggeredEntrance
import kotlinx.coroutines.launch

@Composable
fun BerichtenView(
    messages: List<MagisterMessage>,
    folders: List<MessageFolderInfo>,
    isOffline: Boolean,
    onLoadMessageDetail: suspend (String) -> String?,
    onMarkMessageRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberAgendaHaptics()
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMessage by remember { mutableStateOf<MagisterMessage?>(null) }
    var loadedDetailBody by remember { mutableStateOf<String?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }

    val filteredMessages = remember(messages, selectedFolderId, unreadOnly, searchQuery) {
        messages.filter { msg ->
            val matchesFolder = selectedFolderId == null || msg.folderId == selectedFolderId
            val matchesUnread = !unreadOnly || !msg.isRead
            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                msg.subject.contains(q, ignoreCase = true) ||
                msg.senderName.contains(q, ignoreCase = true) ||
                msg.body.contains(q, ignoreCase = true)
            matchesFolder && matchesUnread && matchesQuery
        }
    }

    val oneUiMode = isOneUi85
    val oneUiInsets = LocalOneUiScreenInsets.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (oneUiMode) Color.Transparent else MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("berichten_view")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = appScreenHorizontalPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = oneUiInsets.calculateBottomPadding() + 28.dp
            )
        ) {
            // Read-Only Info Banner
            item(key = "messages_readonly_banner") {
                Surface(
                    shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("messages_readonly_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Alleen-lezen modus",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Alleen-lezen modus: Berichten versturen is uitgeschakeld in Agenda+. Gebruik officiële Magister om te antwoorden.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Search box
            item(key = "messages_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Zoek op onderwerp of afzender...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Wissen")
                            }
                        }
                    },
                    singleLine = true,
                    shape = appInnerCardShape(standard = 16.dp, nothing = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("messages_search_input")
                )
            }

            // Folder chips + unread filter
            item(key = "messages_filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "folder_chip_all") {
                        FilterChip(
                            selected = selectedFolderId == null,
                            onClick = {
                                haptics.tick()
                                selectedFolderId = null
                            },
                            label = { Text("Alle (${messages.size})") },
                            modifier = Modifier.testTag("folder_chip_all")
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = {
                                haptics.tick()
                                selectedFolderId = folder.id
                            },
                            label = {
                                val countSuffix = if (folder.unreadCount > 0) " (${folder.unreadCount})" else ""
                                Text("${folder.name}$countSuffix")
                            },
                            modifier = Modifier.testTag("folder_chip_${folder.id}")
                        )
                    }
                    item(key = "filter_unread_chip") {
                        FilterChip(
                            selected = unreadOnly,
                            onClick = {
                                haptics.tick()
                                unreadOnly = !unreadOnly
                            },
                            leadingIcon = if (unreadOnly) {
                                { Icon(Icons.Default.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("Ongelezen") },
                            modifier = Modifier.testTag("filter_unread_chip")
                        )
                    }
                }
            }

            if (filteredMessages.isEmpty()) {
                item(key = "messages_empty") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("messages_empty_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Geen berichten gevonden",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isOffline) {
                                    "In offline-modus zie je alleen eerder opgeslagen berichten."
                                } else {
                                    "Er zijn geen berichten die overeenkomen met je filter."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredMessages, key = { _, it -> it.id }) { idx, msg ->
                    MessageRowCard(
                        message = msg,
                        modifier = Modifier.staggeredEntrance(idx),
                        onClick = {
                            haptics.tick()
                            activeMessage = msg
                            loadedDetailBody = msg.body.ifBlank { null }
                            if (!msg.isRead) {
                                onMarkMessageRead(msg.id)
                            }
                            if (!isOffline) {
                                scope.launch {
                                    isLoadingDetail = true
                                    val full = onLoadMessageDetail(msg.id)
                                    if (!full.isNullOrBlank()) {
                                        loadedDetailBody = full
                                    }
                                    isLoadingDetail = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    activeMessage?.let { msg ->
        MessageReaderDialog(
            message = msg,
            bodyText = loadedDetailBody ?: msg.body.ifBlank { "Geen berichttekst beschikbaar." },
            isLoading = isLoadingDetail,
            onDismiss = { activeMessage = null }
        )
    }
}

@Composable
private fun MessageRowCard(
    message: MagisterMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = appCardShape(standard = 18.dp, nothing = 22.dp)
    Card(
        shape = cardShape,
        border = nothingCardBorder(isAlert = !message.isRead),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick)
            .testTag("message_item_${message.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = if (!message.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (message.folder == MessageFolder.SENT) Icons.Default.Send else if (!message.isRead) Icons.Default.MarkEmailUnread else Icons.Default.Drafts,
                        contentDescription = null,
                        tint = if (!message.isRead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.dateSent,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (message.hasAttachments) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Bijlage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (message.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageReaderDialog(
    message: MagisterMessage,
    bodyText: String,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = appCardShape(standard = 24.dp, nothing = 24.dp),
            color = appSheetContainerColor(),
            border = nothingCardBorder(),
            tonalElevation = if (isOneUi85) 0.dp else 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .testTag("message_reader_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = message.folderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${message.senderName} • ${message.dateSent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sluiten")
                }
            }
        }
    }
}
