package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SubjectCustomization
import com.example.model.SubjectCustomizationCatalog
import com.example.ui.components.SubjectIconCatalog
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.contrastForeground
import com.example.ui.components.isIosLiquidGlass
import com.example.ui.components.isOneUi85
import com.example.ui.components.isWindowsPhone
import com.example.ui.components.nothingCardBorder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VakBewerkenBottomSheet(
    subjectCode: String,
    detectedLabel: String = "",
    existingCustomization: SubjectCustomization?,
    smartDefaultNamesEnabled: Boolean,
    onSave: (SubjectCustomization) -> Unit,
    onReset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cleanCode = remember(subjectCode) { subjectCode.trim().lowercase(Locale.ROOT) }
    val smartDefault = remember(cleanCode) { SubjectCustomizationCatalog.smartDefaults[cleanCode] }

    var nickname by remember(cleanCode, existingCustomization) {
        mutableStateOf(
            existingCustomization?.nickname
                ?: if (smartDefaultNamesEnabled && smartDefault != null) "" else ""
        )
    }
    var showOriginalCode by remember(cleanCode, existingCustomization) {
        mutableStateOf(existingCustomization?.showOriginalCode ?: true)
    }
    var selectedColorHex by remember(cleanCode, existingCustomization) {
        val initial = when {
            existingCustomization != null -> existingCustomization.colorHex
            smartDefaultNamesEnabled && smartDefault != null -> smartDefault.defaultColorHex
            else -> null
        }
        mutableStateOf(initial)
    }
    var hexInput by remember(cleanCode, existingCustomization) {
        val initialHex = when {
            existingCustomization?.colorHex != null ->
                SubjectCustomizationCatalog.formatHexColor(existingCustomization.colorHex)
            smartDefaultNamesEnabled && smartDefault != null ->
                SubjectCustomizationCatalog.formatHexColor(smartDefault.defaultColorHex)
            else -> ""
        }
        mutableStateOf(initialHex)
    }
    var selectedIconName by remember(cleanCode, existingCustomization) {
        mutableStateOf(
            existingCustomization?.iconName
                ?: smartDefault?.defaultIconName
                ?: "school"
        )
    }
    var iconQuery by remember { mutableStateOf("") }

    val filteredIcons = remember(iconQuery) { SubjectIconCatalog.filter(iconQuery) }
    val windowsPhone = isWindowsPhone
    val iosMode = isIosLiquidGlass

    val placeholderName = smartDefault?.displayName
        ?: detectedLabel.takeIf { it.isNotBlank() }
        ?: cleanCode.uppercase(Locale.ROOT)

    val previewName = nickname.trim().ifBlank { placeholderName }
    val previewColor = selectedColorHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val previewIcon = SubjectIconCatalog.iconFor(selectedIconName)
    val cardShape = appCardShape(standard = 18.dp, nothing = 20.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85 || iosMode || windowsPhone) 0.dp else 4.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (windowsPhone) "vak bewerken" else "Vak Bewerken",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Magister-code: ${cleanCode.uppercase(Locale.ROOT)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vak_editor_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Live Preview Card
            val tilePreviewBg = when {
                windowsPhone && selectedColorHex != null -> Color(selectedColorHex!!)
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            val tilePreviewFg = if (windowsPhone && selectedColorHex != null) {
                Color(selectedColorHex!!).contrastForeground()
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Surface(
                shape = cardShape,
                color = tilePreviewBg,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!windowsPhone) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .background(previewColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Surface(
                        shape = appThumbnailShape(),
                        color = if (windowsPhone && selectedColorHex != null) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            previewColor.copy(alpha = 0.16f)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = previewIcon,
                                contentDescription = previewName,
                                tint = if (windowsPhone && selectedColorHex != null) tilePreviewFg else previewColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = previewName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tilePreviewFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showOriginalCode) {
                            Text(
                                text = "Code: ${cleanCode.uppercase(Locale.ROOT)} • Lokaal G0411",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (windowsPhone && selectedColorHex != null) {
                                    tilePreviewFg.copy(alpha = 0.82f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Nickname input
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Bijnaam / Weergavenaam") },
                placeholder = { Text(placeholderName) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vak_editor_nickname_input")
            )

            // Show original Magister code subtitle toggle
            Surface(
                shape = appCardShape(standard = 14.dp, nothing = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Toon originele code als subtitel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Laat '${cleanCode.uppercase(Locale.ROOT)}' klein zien onder de bijnaam",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showOriginalCode,
                        onCheckedChange = { showOriginalCode = it },
                        modifier = Modifier.testTag("vak_editor_show_code_switch")
                    )
                }
            }

            // Color Picker Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vakkleur",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = {
                            selectedColorHex = null
                            hexInput = ""
                        },
                        shape = appButtonShape(),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("vak_editor_follow_theme_color_button")
                    ) {
                        Text(
                            text = if (selectedColorHex == null) "Volgt thema ✓" else "Volg thema",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SubjectCustomizationCatalog.curatedColors.forEach { option ->
                        val isSelected = selectedColorHex == option.colorHex
                        val swatchColor = Color(option.colorHex)
                        val swatchShape = if (windowsPhone) RectangleShape else CircleShape
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(swatchShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        Color.White.copy(alpha = 0.25f)
                                    },
                                    shape = swatchShape
                                )
                                .clickable {
                                    selectedColorHex = option.colorHex
                                    hexInput = option.hexString
                                }
                                .testTag("vak_color_swatch_${option.name.lowercase(Locale.ROOT)}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "${option.name} geselecteerd",
                                    tint = swatchColor.contrastForeground(),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = raw.take(9)
                        SubjectCustomizationCatalog.parseHexColorOrNull(raw)?.let { parsed ->
                            selectedColorHex = parsed
                        }
                    },
                    label = { Text("Aangepaste HEX-kleur (#RRGGBB)") },
                    placeholder = { Text("#1BA1E2") },
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(if (windowsPhone) RectangleShape else CircleShape)
                                .background(
                                    selectedColorHex?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    if (windowsPhone) RectangleShape else CircleShape
                                )
                        )
                    },
                    isError = hexInput.isNotBlank() && SubjectCustomizationCatalog.parseHexColorOrNull(hexInput) == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vak_editor_hex_input")
                )
            }

            // Icon Picker Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vaksymbool",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = iconQuery,
                    onValueChange = { iconQuery = it },
                    placeholder = { Text("Zoek symbool (bijv. wiskunde, bio, sport)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vak_editor_icon_search")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredIcons.forEach { entry ->
                        val isSelected = selectedIconName == entry.name
                        val tileShape = appThumbnailShape()
                        Surface(
                            shape = tileShape,
                            color = if (isSelected) {
                                previewColor.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) previewColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(tileShape)
                                .clickable { selectedIconName = entry.name }
                                .testTag("vak_icon_option_${entry.name}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = entry.labelDutch,
                                    tint = if (isSelected) previewColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onReset(cleanCode)
                        onDismiss()
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("vak_editor_reset_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Herstel standaard", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
                    onClick = {
                        onSave(
                            SubjectCustomization(
                                code = cleanCode,
                                nickname = nickname.trim(),
                                colorHex = selectedColorHex,
                                iconName = selectedIconName,
                                showOriginalCode = showOriginalCode
                            )
                        )
                        onDismiss()
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("vak_editor_save_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Opslaan")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
