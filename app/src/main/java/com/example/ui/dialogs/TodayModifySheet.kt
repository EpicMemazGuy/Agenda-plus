package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.model.SchoolAddress
import com.example.model.TodayBlockConfig
import com.example.model.TodayBlockType
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayModifySheet(
    todayBlocks: List<TodayBlockConfig>,
    schoolAddress: SchoolAddress?,
    savedBusLine: String,
    isOffline: Boolean,
    onToggleBlock: (TodayBlockType, Boolean) -> Unit,
    onMoveBlock: (TodayBlockType, Int) -> Unit,
    onSearchSchoolAddress: suspend (String) -> List<SchoolAddress>,
    onSaveSchoolAddress: (SchoolAddress) -> Unit,
    onSetBusLine: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val nothingMode = isNothingOs
    val scope = rememberCoroutineScope()
    var addressQuery by remember { mutableStateOf("") }
    var addressResults by remember { mutableStateOf<List<SchoolAddress>>(emptyList()) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var busLineInput by remember(savedBusLine) { mutableStateOf(savedBusLine) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("today_modify_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vandaag aanpassen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kies welke onderdelen je wilt zien, stel je schooladres en buslijn in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_today_modify_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // 1. 1-Tap School Address Setup
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1-Tik Schoolnavigatie (Google Maps)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (schoolAddress != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = schoolAddress.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = schoolAddress.streetAndCity,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = addressQuery,
                        onValueChange = { newVal ->
                            addressQuery = newVal
                            if (newVal.trim().length >= 3 && !isOffline) {
                                scope.launch {
                                    isSearchingAddress = true
                                    addressResults = onSearchSchoolAddress(newVal)
                                    isSearchingAddress = false
                                }
                            } else {
                                addressResults = emptyList()
                            }
                        },
                        enabled = !isOffline,
                        label = { Text("Zoek jouw schooladres of schoolnaam") },
                        placeholder = { Text("Bijv. Elde College Schijndel") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("school_address_search_input")
                    )

                    if (isSearchingAddress) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    addressResults.take(4).forEach { result ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSaveSchoolAddress(result)
                                    addressQuery = ""
                                    addressResults = emptyList()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocationAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = result.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = result.streetAndCity,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Bustijd setup
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bustijd & Live OV-kaart",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = busLineInput,
                            onValueChange = { busLineInput = it },
                            label = { Text("Buslijnnummer") },
                            placeholder = { Text("Bijv. 156") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("modify_sheet_bus_line_input")
                        )
                        Button(
                            onClick = { onSetBusLine(busLineInput) },
                            enabled = busLineInput.isNotBlank() && !isOffline,
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("modify_sheet_save_bus_button")
                        ) {
                            Text("Instellen")
                        }
                    }
                }
            }

            // 3. Onderdelen aan/uit & volgorde
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Onderdelen en volgorde",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    todayBlocks.forEachIndexed { idx, block ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("today_block_config_${block.type.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.type.labelDutch,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = block.type.descriptionDutch,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { onMoveBlock(block.type, -1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Omhoog verplaatsen"
                                    )
                                }

                                IconButton(
                                    onClick = { onMoveBlock(block.type, 1) },
                                    enabled = idx < todayBlocks.lastIndex,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Omlaag verplaatsen"
                                    )
                                }

                                Switch(
                                    checked = block.enabled,
                                    onCheckedChange = { onToggleBlock(block.type, it) },
                                    modifier = Modifier.testTag("toggle_block_${block.type.name}")
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("done_today_modify_button")
            ) {
                Text("Klaar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
