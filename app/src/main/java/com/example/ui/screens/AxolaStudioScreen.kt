package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeStyle
import com.example.model.AxolaBackgroundStyle
import com.example.model.AxolaBorderStyle
import com.example.model.AxolaCustomTheme
import com.example.model.AxolaTypographyStyle
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.nothingCardBorder
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalAxolaCustomTheme
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.axolaCustomColorScheme
import com.example.ui.theme.axolaTypography
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AxolaStudioScreen(
    currentTheme: AxolaCustomTheme,
    isActiveTheme: Boolean,
    onApplyTheme: (AxolaCustomTheme) -> Unit,
    onImportThemeCode: (String) -> Result<AxolaCustomTheme>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var draftTheme by remember(currentTheme) { mutableStateOf(currentTheme) }
    var primaryHexInput by remember(currentTheme) { mutableStateOf(currentTheme.primaryAccentHex) }
    var secondaryHexInput by remember(currentTheme) { mutableStateOf(currentTheme.secondaryAccentHex) }
    var importCodeInput by remember { mutableStateOf("") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    val oneUiInsets = LocalOneUiScreenInsets.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("axola_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 28.dp
        )
    ) {
        // 1. Live Interactive Preview Card
        item(key = "axola_live_preview") {
            val previewColors = remember(draftTheme) { axolaCustomColorScheme(draftTheme) }
            val previewTypo = remember(draftTheme.typographyStyle) { axolaTypography(draftTheme.typographyStyle) }
            val previewShape = if (draftTheme.cornerRadiusDp <= 0) {
                RectangleShape
            } else {
                RoundedCornerShape(draftTheme.cornerRadiusDp.dp)
            }
            val previewBorder = when (draftTheme.borderStyle) {
                AxolaBorderStyle.NONE -> null
                AxolaBorderStyle.SUBTLE_1DP -> BorderStroke(1.dp, previewColors.outlineVariant)
                AxolaBorderStyle.NEON_GLOW -> BorderStroke(1.5.dp, previewColors.primary)
            }

            CompositionLocalProvider(
                LocalAppThemeStyle provides AppThemeStyle.AXOLA_CUSTOM,
                LocalAxolaCustomTheme provides draftTheme,
                LocalDarkThemeActive provides draftTheme.backgroundStyle.isDark
            ) {
                MaterialTheme(
                    colorScheme = previewColors,
                    typography = previewTypo
                ) {
                    Surface(
                        shape = previewShape,
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("axola_live_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (draftTheme.typographyStyle) {
                                        AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE -> "vandaag • live preview"
                                        AxolaTypographyStyle.NOTHING_DOT_MATRIX -> "VANDAAG • LIVE PREVIEW"
                                        else -> "Vandaag • Live Preview"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Surface(
                                    shape = if (draftTheme.cornerRadiusDp <= 0) RectangleShape else RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (isActiveTheme) "ACTIEF" else "PREVIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = previewShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = previewBorder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (draftTheme.typographyStyle == AxolaTypographyStyle.NOTHING_DOT_MATRIX) {
                                                "WISKUNDE B"
                                            } else {
                                                "Wiskunde B"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "08:30 - 09:20 • Lokaal G0411",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = if (draftTheme.cornerRadiusDp <= 0) {
                                            RectangleShape
                                        } else {
                                            RoundedCornerShape((draftTheme.cornerRadiusDp / 2).coerceAtLeast(6).dp)
                                        },
                                        color = MaterialTheme.colorScheme.secondary
                                    ) {
                                        Text(
                                            text = "8.4",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Base Preset Clone Chips
        item(key = "axola_base_presets") {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Start vanaf basisthema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AppThemeStyle.NOTHING_OS,
                            AppThemeStyle.ONE_UI,
                            AppThemeStyle.WINDOWS_PHONE,
                            AppThemeStyle.IOS_LIQUID_GLASS,
                            AppThemeStyle.STANDARD
                        ).forEach { preset ->
                            FilterChip(
                                selected = draftTheme.basePreset == preset,
                                onClick = {
                                    val cloned = AxolaCustomTheme.fromBasePreset(preset)
                                    draftTheme = cloned
                                    primaryHexInput = cloned.primaryAccentHex
                                    secondaryHexInput = cloned.secondaryAccentHex
                                    statusFeedback = "Sjabloon '${preset.labelDutch}' geladen."
                                    isErrorFeedback = false
                                },
                                label = { Text(preset.labelDutch) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("axola_preset_${preset.name}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Corner Radius & Background Style
        item(key = "axola_shape_and_bg") {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hoekradius (Corner Radius)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${draftTheme.cornerRadiusDp}dp",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = draftTheme.cornerRadiusDp.toFloat(),
                        onValueChange = { value ->
                            draftTheme = draftTheme.copy(cornerRadiusDp = value.roundToInt().coerceIn(0, 28))
                        },
                        valueRange = 0f..28f,
                        steps = 27,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("axola_corner_radius_slider")
                    )

                    Text(
                        text = "Achtergrondstijl",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AxolaBackgroundStyle.entries.forEach { bg ->
                            FilterChip(
                                selected = draftTheme.backgroundStyle == bg,
                                onClick = { draftTheme = draftTheme.copy(backgroundStyle = bg) },
                                label = { Text(bg.labelDutch) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("axola_bg_${bg.name}")
                            )
                        }
                    }
                }
            }
        }

        // 4. Primary & Secondary Accent Colors (Swatches + HEX)
        item(key = "axola_colors") {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Primaire accentkleur",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ColorSwatchRow(
                        selectedHex = draftTheme.primaryAccentHex,
                        tagPrefix = "axola_primary_swatch",
                        onSelectHex = { hex ->
                            draftTheme = draftTheme.copy(primaryAccentHex = hex)
                            primaryHexInput = hex
                        }
                    )
                    OutlinedTextField(
                        value = primaryHexInput,
                        onValueChange = { raw ->
                            primaryHexInput = raw.uppercase(Locale.US).take(7)
                            AxolaCustomTheme.parseHexArgb(primaryHexInput)?.let {
                                draftTheme = draftTheme.copy(
                                    primaryAccentHex = AxolaCustomTheme.normalizeHex(primaryHexInput)
                                )
                            }
                        },
                        label = { Text("Primair HEX (#RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("axola_primary_hex_input")
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Secundaire accentkleur",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ColorSwatchRow(
                        selectedHex = draftTheme.secondaryAccentHex,
                        tagPrefix = "axola_secondary_swatch",
                        onSelectHex = { hex ->
                            draftTheme = draftTheme.copy(secondaryAccentHex = hex)
                            secondaryHexInput = hex
                        }
                    )
                    OutlinedTextField(
                        value = secondaryHexInput,
                        onValueChange = { raw ->
                            secondaryHexInput = raw.uppercase(Locale.US).take(7)
                            AxolaCustomTheme.parseHexArgb(secondaryHexInput)?.let {
                                draftTheme = draftTheme.copy(
                                    secondaryAccentHex = AxolaCustomTheme.normalizeHex(secondaryHexInput)
                                )
                            }
                        },
                        label = { Text("Secundair HEX (#RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("axola_secondary_hex_input")
                    )
                }
            }
        }

        // 5. Card Borders & Typography
        item(key = "axola_borders_and_typography") {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Kaartranden",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AxolaBorderStyle.entries.forEach { border ->
                            FilterChip(
                                selected = draftTheme.borderStyle == border,
                                onClick = { draftTheme = draftTheme.copy(borderStyle = border) },
                                label = { Text(border.labelDutch) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("axola_border_${border.name}")
                            )
                        }
                    }

                    Text(
                        text = "Typografie",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AxolaTypographyStyle.entries.forEach { typo ->
                            FilterChip(
                                selected = draftTheme.typographyStyle == typo,
                                onClick = { draftTheme = draftTheme.copy(typographyStyle = typo) },
                                label = { Text(typo.labelDutch) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("axola_typo_${typo.name}")
                            )
                        }
                    }
                }
            }
        }

        // 6. Export / Import AXOLA-THEME:// Code
        item(key = "axola_share_code") {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Deel of importeer themacode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deel jouw thema als compacte AXOLA-THEME:// code met vrienden of plak een code om direct over te nemen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val code = draftTheme.encodeToThemeCode()
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("AXOLA Themacode", code))
                            statusFeedback = "Themacode gekopieerd naar klembord!"
                            isErrorFeedback = false
                        },
                        shape = appButtonShape(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("axola_copy_code_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kopieer Themacode", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedTextField(
                        value = importCodeInput,
                        onValueChange = { importCodeInput = it },
                        label = { Text("Plak AXOLA-THEME://... code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("axola_import_code_input")
                    )

                    OutlinedButton(
                        onClick = {
                            val res = onImportThemeCode(importCodeInput)
                            res.onSuccess { imported ->
                                draftTheme = imported
                                primaryHexInput = imported.primaryAccentHex
                                secondaryHexInput = imported.secondaryAccentHex
                                importCodeInput = ""
                                statusFeedback = "Themacode geïmporteerd en toegepast!"
                                isErrorFeedback = false
                            }.onFailure { err ->
                                statusFeedback = err.message ?: "Ongeldige themacode."
                                isErrorFeedback = true
                            }
                        },
                        enabled = importCodeInput.isNotBlank(),
                        shape = appButtonShape(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("axola_import_code_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importeer Themacode", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Feedback & Primary Apply CTA
        item(key = "axola_apply_cta") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statusFeedback?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isErrorFeedback) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("axola_status_feedback")
                    )
                }

                Button(
                    onClick = {
                        onApplyTheme(draftTheme)
                        statusFeedback = "AXOLA Studio thema opgeslagen en geactiveerd!"
                        isErrorFeedback = false
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("axola_apply_theme_button")
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thema toepassen", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    selectedHex: String,
    tagPrefix: String,
    onSelectHex: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AxolaCustomTheme.CURATED_SWATCHES.forEach { hex ->
            val colorArgb = AxolaCustomTheme.parseHexArgb(hex) ?: 0xFF3B82F6L
            val swatchColor = Color(colorArgb)
            val isSelected = selectedHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onSelectHex(hex) }
                    .testTag("${tagPrefix}_${hex.removePrefix("#")}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Geselecteerd ($hex)",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
