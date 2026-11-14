package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.MagisterMessage
import com.example.model.MessageFolder
import com.example.model.MessageFolderInfo
import com.example.model.MessageRecipient
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import kotlinx.coroutines.launch

@Composable
fun BerichtenView(
    messages: List<MagisterMessage>,
    folders: List<MessageFolderInfo>,
    isOffline: Boolean,
    onLoadMessageDetail: suspend (String) -> String?,
    onMarkMessageRead: (String) -> Unit,
    onSearchRecipients: suspend (String) -> List<MessageRecipient>,
    onSendMessage: suspend (List<MessageRecipient>, String, String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMessage by remember { mutableStateOf<MagisterMessage?>(null) }
    var loadedDetailBody by remember { mutableStateOf<String?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier.testTag("berichten_view"),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showComposeDialog = true },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Nieuw bericht") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("compose_message_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp)
        ) {
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
                    shape = RoundedCornerShape(16.dp),
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
                    item {
                        FilterChip(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null },
                            label = { Text("Alle (${messages.size})") },
                            modifier = Modifier.testTag("folder_chip_all")
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = {
                                val countSuffix = if (folder.unreadCount > 0) " (${folder.unreadCount})" else ""
                                Text("${folder.name}$countSuffix")
                            },
                            modifier = Modifier.testTag("folder_chip_${folder.id}")
                        )
                    }
                    item {
                        FilterChip(
                            selected = unreadOnly,
                            onClick = { unreadOnly = !unreadOnly },
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
                items(filteredMessages, key = { it.id }) { msg ->
                    MessageRowCard(
                        message = msg,
                        onClick = {
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

    if (showComposeDialog) {
        ComposeMessageDialog(
            isOffline = isOffline,
            onSearchRecipients = onSearchRecipients,
            onSend = onSendMessage,
            onDismiss = { showComposeDialog = false }
        )
    }
}

@Composable
private fun MessageRowCard(
    message: MagisterMessage,
    onClick: () -> Unit
) {
    val nothingMode = isNothingOs
    Card(
        shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
        border = nothingCardBorder(isAlert = !message.isRead),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (nothingMode) 22.dp else 18.dp))
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
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = nothingCardBorder(),
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

@Composable
private fun ComposeMessageDialog(
    isOffline: Boolean,
    onSearchRecipients: suspend (String) -> List<MessageRecipient>,
    onSend: suspend (List<MessageRecipient>, String, String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var recipientQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageRecipient>>(emptyList()) }
    var selectedRecipients by remember { mutableStateOf<List<MessageRecipient>>(emptyList()) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = nothingCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compose_message_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nieuw bericht",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                if (selectedRecipients.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectedRecipients, key = { it.id }) { rec ->
                            InputChip(
                                selected = true,
                                onClick = { selectedRecipients = selectedRecipients.filterNot { it.id == rec.id } },
                                label = { Text(rec.name) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Verwijder", modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = recipientQuery,
                    onValueChange = { q ->
                        recipientQuery = q
                        if (q.trim().length >= 2 && !isOffline) {
                            scope.launch { searchResults = onSearchRecipients(q) }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    enabled = !isOffline,
                    label = { Text("Zoek ontvanger (docent of leerling)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_recipient_input")
                )

                searchResults.take(4).forEach { rec ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (selectedRecipients.none { it.id == rec.id }) {
                                    selectedRecipients = selectedRecipients + rec
                                }
                                recipientQuery = ""
                                searchResults = emptyList()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(rec.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (rec.roleLabel.isNotBlank()) {
                                    Text(rec.roleLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Onderwerp") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_subject_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Bericht") },
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_body_input")
                )

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isSending = true
                            errorMsg = null
                            val res = onSend(selectedRecipients, subject, body)
                            isSending = false
                            if (res.isSuccess) {
                                onDismiss()
                            } else {
                                errorMsg = res.exceptionOrNull()?.message ?: "Verzenden mislukt."
                            }
                        }
                    },
                    enabled = !isOffline && !isSending && selectedRecipients.isNotEmpty() && subject.isNotBlank() && body.isNotBlank(),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("compose_send_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verzenden", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
