package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.automation.CalendarBridgeManager
import com.example.automation.LessonDndScheduler
import com.example.model.AgendaStyle
import com.example.model.AppThemeStyle
import com.example.model.DarkModePreference
import com.example.model.UiScalePreset
import com.example.ui.components.AgendaPlusLogo
import com.example.ui.components.agendaPressable
import com.example.ui.components.agendaSpring
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.nothingCardBorder
import com.example.ui.components.rememberAgendaHaptics
import com.example.ui.theme.IosBlue
import com.example.ui.theme.MetroCobalt
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingRed
import com.example.ui.theme.OneUiBlueDark
import com.example.ui.theme.OneUiBlueLight

@Composable
fun SetupWizardScreen(
    initialStyle: AgendaStyle,
    initialScale: Float,
    initialThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    initialDarkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    initialDndEnabled: Boolean = false,
    initialCalendarBridgeEnabled: Boolean = false,
    onPreviewTheme: (AppThemeStyle, DarkModePreference) -> Unit = { _, _ -> },
    onComplete: (
        style: AgendaStyle,
        scale: Float,
        themeStyle: AppThemeStyle,
        darkModePref: DarkModePreference,
        dndEnabled: Boolean,
        calendarBridgeEnabled: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAgendaHaptics()
    var step by remember { mutableIntStateOf(0) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var selectedThemeStyle by remember { mutableStateOf(initialThemeStyle) }
    var selectedDarkMode by remember { mutableStateOf(initialDarkModePref) }
    var dndEnabled by remember { mutableStateOf(initialDndEnabled) }
    var calendarBridgeEnabled by remember { mutableStateOf(initialCalendarBridgeEnabled) }
    var selectedPreset by remember {
        mutableStateOf(
            UiScalePreset.entries.minByOrNull { kotlin.math.abs(it.scaleFactor - initialScale) }
                ?: UiScalePreset.NORMAL
        )
    }

    if (step > 0) {
        BackHandler {
            haptics.tick()
            step--
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("setup_wizard_screen"),
        color = MaterialTheme.colorScheme.background
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
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (index == step) 26.dp else 9.dp)
                                .height(8.dp)
                                .clip(if (selectedThemeStyle == AppThemeStyle.WINDOWS_PHONE) RectangleShape else RoundedCornerShape(4.dp))
                                .background(
                                    if (index <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stap ${step + 1} van 5",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        haptics.tick()
                        onComplete(
                            selectedStyle,
                            selectedPreset.scaleFactor,
                            selectedThemeStyle,
                            selectedDarkMode,
                            dndEnabled,
                            calendarBridgeEnabled
                        )
                    },
                    modifier = Modifier.testTag("wizard_skip_button")
                ) {
                    Text("Overslaan")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main step content with crisp directional transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(agendaSpring()) { full -> if (forward) full / 4 else -full / 4 } +
                            fadeIn()) togetherWith
                            (slideOutHorizontally { full -> if (forward) -full / 6 else full / 6 } + fadeOut())
                    },
                    label = "wizard_step_transition"
                ) { activeStep ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (activeStep) {
                            0 -> WizardIntroStep()
                            1 -> WizardThemePickerStep(
                                selectedTheme = selectedThemeStyle,
                                selectedDarkMode = selectedDarkMode,
                                onSelectTheme = { style ->
                                    haptics.tick()
                                    selectedThemeStyle = style
                                    onPreviewTheme(style, selectedDarkMode)
                                },
                                onSelectDarkMode = { pref ->
                                    haptics.tick()
                                    selectedDarkMode = pref
                                    onPreviewTheme(selectedThemeStyle, pref)
                                }
                            )
                            2 -> WizardAutopilotStep(
                                dndEnabled = dndEnabled,
                                calendarBridgeEnabled = calendarBridgeEnabled,
                                onToggleDnd = {
                                    haptics.confirm()
                                    dndEnabled = it
                                },
                                onToggleCalendarBridge = {
                                    haptics.confirm()
                                    calendarBridgeEnabled = it
                                }
                            )
                            3 -> WizardUiScaleStep(
                                selectedPreset = selectedPreset,
                                onSelectPreset = {
                                    haptics.tick()
                                    selectedPreset = it
                                }
                            )
                            else -> WizardAgendaStyleStep(
                                selectedStyle = selectedStyle,
                                onSelectStyle = {
                                    haptics.tick()
                                    selectedStyle = it
                                }
                            )
                        }
                    }
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
                        onClick = {
                            haptics.tick()
                            step--
                        },
                        shape = appButtonShape(),
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
                        if (step < 4) {
                            haptics.tick()
                            step++
                        } else {
                            haptics.confirm()
                            onComplete(
                                selectedStyle,
                                selectedPreset.scaleFactor,
                                selectedThemeStyle,
                                selectedDarkMode,
                                dndEnabled,
                                calendarBridgeEnabled
                            )
                        }
                    },
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag(if (step < 4) "wizard_next_button" else "wizard_finish_button")
                ) {
                    Text(
                        text = if (step < 4) "Volgende" else "Naar inloggen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (step < 4) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
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
        Spacer(modifier = Modifier.height(10.dp))

        AgendaPlusLogo(
            size = 86.dp,
            modifier = Modifier.testTag("wizard_app_logo")
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Jouw School, Jouw Regels",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Agenda+ is 100% gratis, zonder reclame en draait volledig lokaal op jouw telefoon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        WizardHighlightItem(
            icon = Icons.Default.Lock,
            title = "100% Lokaal & Privé",
            description = "Jouw rooster, cijfers en berichten verlaten nooit jouw eigen toestel zonder jouw toestemming."
        )
        Spacer(modifier = Modifier.height(10.dp))
        WizardHighlightItem(
            icon = Icons.Default.Palette,
            title = "5 Vrije Thema's + AXOLA Studio",
            description = "Kies uit Nothing OS, Windows Phone, iOS Liquid Glass, One UI of Material You — alles gratis."
        )
        Spacer(modifier = Modifier.height(10.dp))
        WizardHighlightItem(
            icon = Icons.Default.Widgets,
            title = "Slimme Autopiloot & Widgets",
            description = "Automatisch Niet Storen in de les, smartwatch-agenda en directe cijfer- & roosterwidgets."
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
        shape = appCardShape(standard = 20.dp, nothing = 22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = nothingCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = appCardShape(standard = 14.dp, nothing = 14.dp),
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
private fun WizardThemePickerStep(
    selectedTheme: AppThemeStyle,
    selectedDarkMode: DarkModePreference,
    onSelectTheme: (AppThemeStyle) -> Unit,
    onSelectDarkMode: (DarkModePreference) -> Unit
) {
    val themes = remember {
        listOf(
            AppThemeStyle.NOTHING_OS,
            AppThemeStyle.WINDOWS_PHONE,
            AppThemeStyle.IOS_LIQUID_GLASS,
            AppThemeStyle.ONE_UI,
            AppThemeStyle.STANDARD
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Kies Jouw Stijl",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tik op een thema om direct het live uiterlijk van Agenda+ te ervaren.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_dark_mode_segmented")
        ) {
            DarkModePreference.entries.forEachIndexed { idx, pref ->
                SegmentedButton(
                    selected = selectedDarkMode == pref,
                    onClick = { onSelectDarkMode(pref) },
                    enabled = selectedTheme != AppThemeStyle.NOTHING_OS,
                    shape = if (selectedTheme == AppThemeStyle.WINDOWS_PHONE) {
                        RectangleShape
                    } else {
                        SegmentedButtonDefaults.itemShape(idx, DarkModePreference.entries.size)
                    },
                    modifier = Modifier.testTag("wizard_dark_mode_${pref.name}")
                ) {
                    Text(pref.labelDutch, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        themes.forEach { style ->
            val isSelected = selectedTheme == style
            val accentColor = when (style) {
                AppThemeStyle.NOTHING_OS -> NothingRed
                AppThemeStyle.WINDOWS_PHONE -> MetroCobalt
                AppThemeStyle.IOS_LIQUID_GLASS -> IosBlue
                AppThemeStyle.ONE_UI -> OneUiBlueDark
                else -> Color(0xFF3B6FD8)
            }
            val canvasBg = when (style) {
                AppThemeStyle.NOTHING_OS -> NothingBlack
                AppThemeStyle.WINDOWS_PHONE -> NothingBlack
                AppThemeStyle.IOS_LIQUID_GLASS -> Color(0xFF141821)
                AppThemeStyle.ONE_UI -> OneUiBlueLight.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            val rowShape = when (style) {
                AppThemeStyle.WINDOWS_PHONE -> RectangleShape
                AppThemeStyle.IOS_LIQUID_GLASS -> RoundedCornerShape(22.dp)
                AppThemeStyle.NOTHING_OS -> RoundedCornerShape(20.dp)
                AppThemeStyle.ONE_UI -> RoundedCornerShape(24.dp)
                else -> RoundedCornerShape(18.dp)
            }

            Surface(
                shape = rowShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else nothingCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(rowShape)
                    .agendaPressable(onClick = { onSelectTheme(style) })
                    .testTag("wizard_theme_${style.name}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = if (style == AppThemeStyle.WINDOWS_PHONE) RectangleShape else RoundedCornerShape(12.dp),
                        color = canvasBg,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(if (style == AppThemeStyle.WINDOWS_PHONE) RectangleShape else CircleShape)
                                    .background(accentColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.labelDutch,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = style.subtitleDutch,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectTheme(style) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardAutopilotStep(
    dndEnabled: Boolean,
    calendarBridgeEnabled: Boolean,
    onToggleDnd: (Boolean) -> Unit,
    onToggleCalendarBridge: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasCalendarPerm by remember { mutableStateOf(CalendarBridgeManager.hasCalendarPermissions(context)) }
    var hasPolicyAccess by remember { mutableStateOf(LessonDndScheduler.hasPolicyAccess(context)) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        hasCalendarPerm = granted || CalendarBridgeManager.hasCalendarPermissions(context)
        if (hasCalendarPerm) {
            onToggleCalendarBridge(true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCalendarPerm = CalendarBridgeManager.hasCalendarPermissions(context)
        hasPolicyAccess = LessonDndScheduler.hasPolicyAccess(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Slimme School-Autopiloot",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Zet deze automatiseringen direct aan of stel ze later in bij Meer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 1. Silent-in-Class DND Card
        Surface(
            shape = appCardShape(standard = 20.dp, nothing = 22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = nothingCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = appCardShape(standard = 12.dp, nothing = 12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DoNotDisturbOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatisch stil in de les",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zet je telefoon stil bij lesbegin en herstel tijdens pauzes of uitval.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = onToggleDnd,
                        modifier = Modifier.testTag("wizard_dnd_switch")
                    )
                }

                if (dndEnabled && !hasPolicyAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.testTag("wizard_dnd_permission_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Geef Niet Storen-toegang")
                    }
                }
            }
        }

        // 2. Smartwatch & System Calendar Sync Card
        Surface(
            shape = appCardShape(standard = 20.dp, nothing = 22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = nothingCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = appCardShape(standard = 12.dp, nothing = 12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smartwatch & Agenda Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Spiegel lessen automatisch naar Wear OS / Galaxy Watch en Google Agenda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = calendarBridgeEnabled && hasCalendarPerm,
                        onCheckedChange = { want ->
                            if (want && !hasCalendarPerm) {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            } else {
                                onToggleCalendarBridge(want)
                            }
                        },
                        modifier = Modifier.testTag("wizard_calendar_switch")
                    )
                }
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
                    .clip(appCardShape(standard = 12.dp, nothing = 12.dp))
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
    val cardShape = appCardShape(standard = 22.dp, nothing = 22.dp)
    Surface(
        shape = cardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else nothingCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .agendaPressable(onClick = onClick)
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
            shape = appButtonShape(),
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
        shape = appCardShape(standard = 14.dp, nothing = 14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = appCardShape(standard = 8.dp, nothing = 8.dp),
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
            shape = appCardShape(standard = 20.dp, nothing = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = nothingCardBorder(),
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
                        shape = appButtonShape(),
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
                    shape = appCardShape(standard = 12.dp, nothing = 12.dp),
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
