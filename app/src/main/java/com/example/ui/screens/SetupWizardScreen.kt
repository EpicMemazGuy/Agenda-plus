package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AgendaStyle
import com.example.model.UiScalePreset
import com.example.ui.components.AgendaPlusLogo

@Composable
fun SetupWizardScreen(
    initialStyle: AgendaStyle,
    initialScale: Float,
    onComplete: (AgendaStyle, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var selectedPreset by remember {
        mutableStateOf(
            UiScalePreset.entries.minByOrNull { kotlin.math.abs(it.scaleFactor - initialScale) }
                ?: UiScalePreset.NORMAL
        )
    }

    if (step > 0) {
        BackHandler { step-- }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("setup_wizard_screen"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top step indicator & Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (index == step) 28.dp else 10.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (index <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stap ${step + 1} van 3",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { onComplete(selectedStyle, selectedPreset.scaleFactor) },
                    modifier = Modifier.testTag("wizard_skip_button")
                ) {
                    Text("Overslaan")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main step content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> WizardIntroStep()
                    1 -> WizardAgendaStyleStep(
                        selectedStyle = selectedStyle,
                        onSelectStyle = { selectedStyle = it }
                    )
                    else -> WizardUiScaleStep(
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("wizard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vorige")
                    }
                }

                Button(
                    onClick = {
                        if (step < 2) {
                            step++
                        } else {
                            onComplete(selectedStyle, selectedPreset.scaleFactor)
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag(if (step < 2) "wizard_next_button" else "wizard_finish_button")
                ) {
                    Text(
                        text = if (step < 2) "Volgende" else "Naar inloggen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (step < 2) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardIntroStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        AgendaPlusLogo(
            size = 88.dp,
            modifier = Modifier.testTag("wizard_app_logo")
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Stel Agenda+ in",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Kies in twee snelle stappen hoe jouw rooster en tekst eruitzien. Je kunt dit later altijd aanpassen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        WizardHighlightItem(
            icon = Icons.Default.ViewAgenda,
            title = "Compact of Ruim Rooster",
            description = "Bekijk al je lessen in één strak overzicht of in ruime leskaarten."
        )
        Spacer(modifier = Modifier.height(12.dp))
        WizardHighlightItem(
            icon = Icons.Default.WbSunny,
            title = "Lokaal Weer & Meer-hub",
            description = "Actueel weer op Vandaag, Magister Berichten, Studiewijzers en je favoriete leersites."
        )
        Spacer(modifier = Modifier.height(12.dp))
        WizardHighlightItem(
            icon = Icons.Default.Widgets,
            title = "Startscherm-widgets",
            description = "Zie direct je volgende les, cijfers en huiswerk zonder de app te openen."
        )
    }
}

@Composable
private fun WizardHighlightItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WizardAgendaStyleStep(
    selectedStyle: AgendaStyle,
    onSelectStyle: (AgendaStyle) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Kies je Roosterstijl",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Bepaal hoeveel lessen je tegelijk op je scherm wilt zien.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        StyleOptionCard(
            title = "Compact (Aanbevolen)",
            subtitle = "Strakke rijen met minimale tussenruimte. De hele schooldag past op één scherm.",
            selected = selectedStyle == AgendaStyle.COMPACT,
            onClick = { onSelectStyle(AgendaStyle.COMPACT) },
            testTag = "wizard_style_compact"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniCompactRow("08:30", "1e", "Wiskunde B", "L204")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                MiniCompactRow("09:20", "2e", "Nederlands", "L112")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                MiniCompactRow("10:10", "3e", "Scheikunde", "Lab 2")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StyleOptionCard(
            title = "Ruime Kaarten",
            subtitle = "Grotere aparte leskaarten met extra ademruimte en direct zichtbaar huiswerk.",
            selected = selectedStyle == AgendaStyle.SPACIOUS,
            onClick = { onSelectStyle(AgendaStyle.SPACIOUS) },
            testTag = "wizard_style_spacious"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniSpaciousCard("1e uur", "Wiskunde B", "08:30 - 09:20", "Lok. 204")
                MiniSpaciousCard("2e uur", "Nederlands", "09:20 - 10:10", "Lok. 112")
            }
        }
    }
}

@Composable
private fun StyleOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    previewContent: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadioButton(
                    selected = selected,
                    onClick = onClick
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            previewContent()
        }
    }
}

@Composable
private fun MiniCompactRow(time: String, period: String, subject: String, room: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$period  $subject",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = room,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MiniSpaciousCard(period: String, subject: String, time: String, room: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = period.take(2),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = room,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WizardUiScaleStep(
    selectedPreset: UiScalePreset,
    onSelectPreset: (UiScalePreset) -> Unit
) {
    val scale = selectedPreset.scaleFactor

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tekstgrootte & Schaling",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Stem de lettergrootte en compactheid af op jouw voorkeur.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_scale_segmented")
        ) {
            UiScalePreset.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = UiScalePreset.entries.size
                    ),
                    modifier = Modifier.testTag("wizard_scale_${preset.name}")
                ) {
                    Text(preset.labelDutch)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "VOORBEELD WEERGAVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding((16 * scale).dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engels • 3e uur",
                        fontSize = (16 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Lok. B14",
                            fontSize = (12 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "10:15 - 11:05 • Mevr. de Vries",
                    fontSize = (13 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((18 * scale).dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Woordjes Unit 4 leren voor toets",
                            fontSize = (12 * scale).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
