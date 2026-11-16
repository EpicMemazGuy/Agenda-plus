package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import com.example.ui.components.appCardShape
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder

private data class TutorialStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun TutorialDialog(
    onDismissAndComplete: () -> Unit
) {
    val steps = remember {
        listOf(
            TutorialStep(
                title = "Welkom bij Agenda+",
                subtitle = "Stap 1 van 5 • Vandaag & Aanpassen",
                description = "Op het tabblad Vandaag zie je direct je weer, volgende les en huiswerk. Via de knop 'Aanpassen' onderaan stel je zelf blokken, volgorde en navigatie in.",
                icon = Icons.Default.Today
            ),
            TutorialStep(
                title = "Rooster & Keuzelessen (KWT)",
                subtitle = "Stap 2 van 5 • Altijd actueel",
                description = "Bekijk je lessen per dag, 3 dagen of hele week. Biedt je school keuzelessen (KWT) aan, dan kies je jouw keuzeles direct vanuit je rooster of het Meer-tabblad.",
                icon = Icons.Default.CalendarViewDay
            ),
            TutorialStep(
                title = "Huiswerk & Cijfers",
                subtitle = "Stap 3 van 5 • Wat-Als Simulator",
                description = "Vink taken af bij Huiswerk en bekijk al je cijfers per vak. Tik op een vak bij Cijfers om met de Wat-Als Simulator te zien welk cijfer je moet halen.",
                icon = Icons.Default.Analytics
            ),
            TutorialStep(
                title = "Bustijd-kaart & Schoolnavigatie",
                subtitle = "Stap 4 van 5 • Live onderweg",
                description = "Voeg op Vandaag jouw buslijn toe om live bussen en haltes op de kaart te zien. Met 1-tik schoolnavigatie start je direct Google Maps naar je schooladres.",
                icon = Icons.Default.DirectionsBus
            ),
            TutorialStep(
                title = "De nieuwe Meer-hub",
                subtitle = "Stap 5 van 5 • Alles op één plek",
                description = "Open 'Meer' rechtsonder voor Magister Berichten, Studiewijzers & Bronnen, ELO (itslearning), de Offline-modus en het Nothing OS thema.",
                icon = Icons.Default.Apps
            )
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]
    Dialog(onDismissRequest = onDismissAndComplete) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tutorial_dialog"),
            shape = appCardShape(standard = 24.dp, nothing = 26.dp),
            color = appSheetContainerColor(),
            border = nothingCardBorder(),
            tonalElevation = if (isOneUi85) 0.dp else 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = onDismissAndComplete,
                        modifier = Modifier.testTag("tutorial_skip_button")
                    ) {
                        Text("Overslaan")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = nothingCardBorder(),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = step.title,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Step progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .size(if (idx == currentStep) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tutorial_prev_button")
                        ) {
                            Text("Vorige")
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep < steps.lastIndex) {
                                currentStep++
                            } else {
                                onDismissAndComplete()
                            }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tutorial_next_button")
                    ) {
                        Text(
                            text = if (currentStep < steps.lastIndex) "Volgende" else "Aan de slag!",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
