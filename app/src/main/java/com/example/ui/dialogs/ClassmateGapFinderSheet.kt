package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Appointment
import com.example.services.SyncManager
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassmateGapFinderSheet(
    appointments: List<Appointment>,
    initialClassmateUri: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var aliasInput by remember { mutableStateOf("Klasgenoot") }
    var generatedShareUri by remember { mutableStateOf<String?>(null) }
    var isSharing by remember { mutableStateOf(false) }

    var classmateUriInput by remember(initialClassmateUri) {
        mutableStateOf(initialClassmateUri.orEmpty())
    }
    var isComparing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var friendSchedule by remember { mutableStateOf<SyncManager.ClassmateGapSchedule?>(null) }
    var matches by remember { mutableStateOf<List<SyncManager.OverlapGapMatch>>(emptyList()) }

    val myGapSlots = remember(appointments) {
        SyncManager.computeFreeAndCanceledSlots(appointments)
    }

    fun compareFromUri(uriText: String) {
        val clean = uriText.trim()
        if (clean.isBlank()) return
        scope.launch {
            isComparing = true
            errorMessage = null
            statusMessage = null
            SyncManager.fetchClassmateGapSchedule(clean)
                .onSuccess { friend ->
                    friendSchedule = friend
                    matches = SyncManager.findOverlappingGaps(appointments, friend)
                    statusMessage = if (matches.isEmpty()) {
                        "Geen overlappende tussenuren gevonden met ${friend.alias}."
                    } else {
                        "${matches.size} gezamenlijke vrije uren met ${friend.alias} gevonden!"
                    }
                }
                .onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Vergelijken mislukt."
                }
            isComparing = false
        }
    }

    LaunchedEffect(initialClassmateUri) {
        if (!initialClassmateUri.isNullOrBlank()) {
            compareFromUri(initialClassmateUri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("classmate_gap_finder_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appScreenHorizontalPadding(standard = 20.dp))
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Zelfde Tussenuur / Uitval",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Vergelijk versleuteld vrije uren zonder account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_gap_finder_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Zero-PII Privacy Card
            Surface(
                shape = appCardShape(standard = 16.dp, nothing = 18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy-veilig: deelt alleen jouw bijnaam en ${myGapSlots.size} vrije/uitgevallen uren (AES-256). Nooit cijfers, huiswerk of docenten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Share section
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Deel jouw tussenuur-link",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Jouw bijnaam voor klasgenoten") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gap_alias_input")
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSharing = true
                                errorMessage = null
                                SyncManager.exportGapShareLink(aliasInput, appointments)
                                    .onSuccess { uri ->
                                        generatedShareUri = uri
                                        statusMessage = "Versleutelde link aangemaakt!"
                                    }
                                    .onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Link aanmaken mislukt."
                                    }
                                isSharing = false
                            }
                        },
                        enabled = !isSharing,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gap_generate_share_button")
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Genereer & Deel Tussenuur Link", fontWeight = FontWeight.Bold)
                        }
                    }

                    generatedShareUri?.let { uri ->
                        Surface(
                            shape = appInnerCardShape(standard = 12.dp, nothing = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uri,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Tussenuur Link", uri))
                                        statusMessage = "Link gekopieerd naar klembord."
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopieer link")
                                }
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Check wanneer we tegelijk tussenuur of uitval hebben in Agenda+:\n$uri"
                                            )
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, "Deel tussenuur-link"))
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.IosShare, contentDescription = "Deel via app")
                                }
                            }
                        }
                    }
                }
            }

            // Compare section
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. Vergelijk met link van klasgenoot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = classmateUriInput,
                        onValueChange = { classmateUriInput = it },
                        label = { Text("Plak agendaplus://tussenuur?... link") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gap_compare_uri_input")
                    )
                    FilledTonalButton(
                        onClick = { compareFromUri(classmateUriInput) },
                        enabled = classmateUriInput.isNotBlank() && !isComparing,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gap_compare_button")
                    ) {
                        if (isComparing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vergelijk Roosters", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            friendSchedule?.let { friend ->
                if (matches.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matches.forEach { match ->
                            Surface(
                                shape = appInnerCardShape(standard = 14.dp, nothing = 16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = nothingCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gap_match_${match.dateIso}_${match.period}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${match.dateIso} • ${match.period}e uur",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "${match.startTime} - ${match.endTime}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    OneUiPillBadge(
                                        text = "Jij: ${match.myKind} • ${friend.alias}: ${match.classmateKind}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
