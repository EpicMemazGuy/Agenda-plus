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
import com.example.model.CommuteModeConfig
import com.example.model.CommuteModeType
import com.example.model.SchoolAddress
import com.example.model.TodayBlockConfig
import com.example.model.TodayBlockType
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
fun TodayModifySheet(
    todayBlocks: List<TodayBlockConfig>,
    schoolAddress: SchoolAddress?,
    savedBusLine: String,
    isOffline: Boolean,
    commuteModes: List<CommuteModeConfig> = emptyList(),
    morningLockNotificationEnabled: Boolean = true,
    onToggleBlock: (TodayBlockType, Boolean) -> Unit,
    onMoveBlock: (TodayBlockType, Int) -> Unit,
    onSearchSchoolAddress: suspend (String) -> List<SchoolAddress>,
    onSaveSchoolAddress: (SchoolAddress) -> Unit,
    onSetBusLine: (String) -> Unit,
    onSaveCommuteModes: (List<CommuteModeConfig>) -> Unit = {},
    onSetMorningLockNotificationEnabled: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var addressQuery by remember { mutableStateOf("") }
    var addressResults by remember { mutableStateOf<List<SchoolAddress>>(emptyList()) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var busLineInput by remember(savedBusLine) { mutableStateOf(savedBusLine) }
    var backupBusInput by remember(commuteModes) {
        mutableStateOf(commuteModes.filter { it.type == CommuteModeType.BUS }.getOrNull(1)?.busLine.orEmpty())
    }
    var bikeMinutesInput by remember(commuteModes) {
        mutableStateOf((commuteModes.firstOrNull { it.type == CommuteModeType.BIKE }?.bikeMinutes ?: 25).toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("today_modify_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appScreenHorizontalPadding(standard = 20.dp))
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
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
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
                        val resShape = appInnerCardShape(standard = 12.dp, nothing = 12.dp)
                        Surface(
                            shape = resShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(resShape)
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
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = backupBusInput,
                            onValueChange = { backupBusInput = it },
                            label = { Text("Backup buslijn") },
                            placeholder = { Text("Bijv. 306") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("modify_sheet_backup_bus_input")
                        )
                        OutlinedTextField(
                            value = bikeMinutesInput,
                            onValueChange = { bikeMinutesInput = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Fiets (min)") },
                            placeholder = { Text("25") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .width(110.dp)
                                .testTag("modify_sheet_bike_minutes_input")
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val b1 = busLineInput.trim()
                            val b2 = backupBusInput.trim()
                            val bikeMins = (bikeMinutesInput.toIntOrNull() ?: 25).coerceIn(5, 60)
                            val updated = buildList {
                                if (b1.isNotBlank()) {
                                    add(CommuteModeConfig("bus_1", CommuteModeType.BUS, "Lijn $b1", busLine = b1))
                                }
                                if (b2.isNotBlank() && b2 != b1) {
                                    add(CommuteModeConfig("bus_2", CommuteModeType.BUS, "Lijn $b2", busLine = b2))
                                }
                                add(CommuteModeConfig("bike_1", CommuteModeType.BIKE, "Fiets", bikeMinutes = bikeMins))
                            }.take(3)
                            onSaveCommuteModes(updated)
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("modify_sheet_save_commute_modes_button")
                    ) {
                        Text("Vervoersmodi opslaan (max. 3)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ochtend-vergrendelschermmelding",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Toon afteller, lokaal en live bus op schooldagen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = morningLockNotificationEnabled,
                            onCheckedChange = onSetMorningLockNotificationEnabled,
                            modifier = Modifier.testTag("toggle_morning_lock_notif")
                        )
                    }
                }
            }

            // 3. Onderdelen aan/uit & volgorde
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
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
                        fontWeight = if (isOneUi85) FontWeight.Medium else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    todayBlocks.forEachIndexed { idx, block ->
                        Surface(
                            shape = appInnerCardShape(standard = 14.dp, nothing = 14.dp),
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
