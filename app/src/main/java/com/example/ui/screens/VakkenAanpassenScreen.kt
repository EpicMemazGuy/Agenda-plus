package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DetectedSubject
import com.example.model.SubjectCustomization
import com.example.model.SubjectCustomizationCatalog
import com.example.model.SubjectCustomizationState
import com.example.ui.components.IosLargeTitleHeader
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.MetroPanoramaHeader
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.SubjectIconCatalog
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appLiquidGlassContainerColor
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isFloatingOverlayTheme
import com.example.ui.components.isIosLiquidGlass
import com.example.ui.components.isOneUi85
import com.example.ui.components.isWindowsPhone
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.components.staggeredEntrance
import java.util.Locale

data class DetectedSubjectRow(
    val normalizedCode: String,
    val detectedLabel: String
)

@Composable
fun VakkenAanpassenScreen(
    detectedSubjects: List<DetectedSubject>,
    customizations: Map<String, SubjectCustomization>,
    smartDefaultsEnabled: Boolean,
    useSubjectColorsForMetroTiles: Boolean,
    onToggleSmartDefaults: (Boolean) -> Unit,
    onToggleMetroSubjectColors: (Boolean) -> Unit,
    onOpenSubjectEditor: (code: String, detectedLabel: String) -> Unit,
    onResetAllCustomizations: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val haptics = rememberAgendaHaptics()
    var showAddCodeDialog by remember { mutableStateOf(false) }
    var newCodeInput by remember { mutableStateOf("") }
    var showResetAllDialog by remember { mutableStateOf(false) }

    val smartDefaultNamesEnabled = smartDefaultsEnabled
    val onToggleMetroTileColors = onToggleMetroSubjectColors

    val allDetectedSubjects = remember(detectedSubjects, customizations) {
        val map = linkedMapOf<String, String>()
        detectedSubjects.forEach { d ->
            val code = SubjectCustomizationCatalog.normalizeCode(d.code)
            if (code != "les" && code !in map) {
                map[code] = d.detectedName.ifBlank { code.uppercase(Locale.ROOT) }
            }
        }
        customizations.keys.forEach { code ->
            val clean = SubjectCustomizationCatalog.normalizeCode(code)
            if (clean.isNotBlank() && clean != "les" && clean !in map) {
                map[clean] = customizations[code]?.nickname?.takeIf { it.isNotBlank() }
                    ?: clean.uppercase(Locale.ROOT)
            }
        }
        map.entries
            .map { DetectedSubjectRow(it.key, it.value) }
            .sortedBy { it.normalizedCode }
    }

    val stateSnapshot = remember(customizations, smartDefaultNamesEnabled, useSubjectColorsForMetroTiles) {
        SubjectCustomizationState(
            customizations = customizations,
            smartDefaultNamesEnabled = smartDefaultNamesEnabled,
            useSubjectColorsForMetroTiles = useSubjectColorsForMetroTiles
        )
    }

    val windowsPhone = isWindowsPhone
    val iosMode = isIosLiquidGlass
    val oneUiMode = isOneUi85
    val floatingInsets = LocalOneUiScreenInsets.current
    val horizontalPad = appScreenHorizontalPadding()
    val cardShape = appCardShape(standard = 18.dp, nothing = 20.dp)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPad),
        contentPadding = PaddingValues(
            top = if (isFloatingOverlayTheme) floatingInsets.calculateTopPadding() + 4.dp else 12.dp,
            bottom = if (isFloatingOverlayTheme) floatingInsets.calculateBottomPadding() + 18.dp else 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "vakken_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onBack()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vakken_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Terug"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        windowsPhone -> MetroPanoramaHeader(
                            title = "vakken aanpassen",
                            subtitle = "${allDetectedSubjects.size} gedetecteerde vakcodes"
                        )
                        iosMode -> IosLargeTitleHeader(
                            title = "Vakken Aanpassen",
                            subtitle = "${allDetectedSubjects.size} vakken"
                        )
                        oneUiMode -> OneUiExpandingHeader(
                            title = "Vakken",
                            subtitle = "${allDetectedSubjects.size} gedetecteerde vakken"
                        )
                        else -> Column {
                            Text(
                                text = "Vakken Aanpassen",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${allDetectedSubjects.size} vakken gevonden in rooster & cijfers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Settings & Actions Card
        item(key = "vakken_settings_card") {
            Card(
                shape = cardShape,
                border = nothingCardBorder(),
                colors = CardDefaults.cardColors(
                    containerColor = appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Slimme Standaardnamen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Herken Magister-codes automatisch (bijv. wi -> Wiskunde, ne -> Nederlands)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = smartDefaultNamesEnabled,
                            onCheckedChange = {
                                haptics.confirm()
                                onToggleSmartDefaults(it)
                            },
                            modifier = Modifier.testTag("vakken_smart_defaults_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gebruik vakkleuren voor Metro-tegels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kleur Windows Phone Live Tiles met jouw gekozen vakkleuren",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSubjectColorsForMetroTiles,
                            onCheckedChange = {
                                haptics.confirm()
                                onToggleMetroTileColors(it)
                            },
                            modifier = Modifier.testTag("vakken_metro_tile_colors_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                newCodeInput = ""
                                showAddCodeDialog = true
                            },
                            shape = appButtonShape(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vakken_add_code_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nieuw vak / code", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (customizations.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showResetAllDialog = true },
                                shape = appButtonShape(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("vakken_reset_all_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Herstel")
                            }
                        }
                    }
                }
            }
        }

        if (allDetectedSubjects.isEmpty()) {
            item(key = "vakken_empty") {
                Card(
                    shape = cardShape,
                    border = nothingCardBorder(),
                    colors = CardDefaults.cardColors(
                        containerColor = appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Nog geen vakken gedetecteerd",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Synchroniseer je Magister-rooster of voeg handmatig een vakcode toe (bijv. wi, ne, en).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(allDetectedSubjects, key = { _, it -> it.normalizedCode }) { idx, row ->
                val resolved = remember(row, stateSnapshot) {
                    SubjectCustomizationCatalog.resolveSubject(
                        rawSubject = row.detectedLabel,
                        explicitCode = row.normalizedCode,
                        state = stateSnapshot
                    )
                }
                val icon = SubjectIconCatalog.iconFor(resolved.iconName)
                val accent = resolved.accentColorHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

                Card(
                    shape = cardShape,
                    border = nothingCardBorder(),
                    colors = CardDefaults.cardColors(
                        containerColor = appLiquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainer)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(idx)
                        .clip(cardShape)
                        .clickable {
                            haptics.tick()
                            onOpenSubjectEditor(row.normalizedCode, row.detectedLabel)
                        }
                        .testTag("vakken_row_${row.normalizedCode}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(34.dp)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = appThumbnailShape(),
                            color = accent.copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = resolved.displayName,
                                    tint = accent,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = resolved.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (resolved.isCustomized) {
                                    Surface(
                                        shape = appButtonShape(6.dp),
                                        color = accent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Aangepast",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Code: ${row.normalizedCode.uppercase(Locale.ROOT)} • Magister: ${row.detectedLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onOpenSubjectEditor(row.normalizedCode, row.detectedLabel) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vakken_edit_${row.normalizedCode}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "${resolved.displayName} bewerken",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCodeDialog) {
        AlertDialog(
            onDismissRequest = { showAddCodeDialog = false },
            title = { Text("Nieuw vak / code toevoegen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Voer een korte Magister-vakcode in (bijv. wi, ne, en, los, nlt).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newCodeInput,
                        onValueChange = { newCodeInput = it },
                        label = { Text("Vakcode") },
                        placeholder = { Text("bijv. wi") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vakken_new_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalized = SubjectCustomizationCatalog.extractNormalizedSubjectCode(newCodeInput)
                        if (normalized.isNotBlank() && normalized != "les") {
                            showAddCodeDialog = false
                            onOpenSubjectEditor(normalized, newCodeInput.trim())
                        }
                    },
                    shape = appButtonShape(),
                    modifier = Modifier.testTag("vakken_new_code_confirm")
                ) {
                    Text("Bewerken")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCodeDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Alle aanpassingen herstellen?") },
            text = {
                Text("Alle aangepaste vaktitels, kleuren en symbolen worden gereset naar de standaardwaarden.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllCustomizations()
                        showResetAllDialog = false
                    },
                    shape = appButtonShape(),
                    modifier = Modifier.testTag("vakken_reset_all_confirm")
                ) {
                    Text("Alles herstellen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
}
