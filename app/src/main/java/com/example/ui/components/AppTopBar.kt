package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.model.AgendaStyle
import com.example.model.AppThemeStyle
import com.example.model.AuthSession
import com.example.model.DarkModePreference
import com.example.model.GradeUnboxingMode
import com.example.model.UiScalePreset
import com.example.model.cleanSchoolText
import com.example.ui.theme.NothingOutline
import com.example.ui.theme.NothingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    session: AuthSession,
    lastSyncTime: String,
    isRefreshing: Boolean,
    agendaStyle: AgendaStyle,
    uiScale: Float,
    appThemeStyle: AppThemeStyle = AppThemeStyle.STANDARD,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    collapsedTitle: String? = null,
    uitvalNotificationsEnabled: Boolean = true,
    gradeNotificationsEnabled: Boolean = true,
    testNotificationsEnabled: Boolean = true,
    gradeUnboxingEnabled: Boolean = true,
    gradeUnboxingMode: GradeUnboxingMode = GradeUnboxingMode.CASE_OPENING,
    isOffline: Boolean = false,
    onRefreshClick: () -> Unit,
    onOpenRawConsole: () -> Unit = {},
    onSetAgendaStyle: (AgendaStyle) -> Unit = {},
    onSetUiScale: (Float) -> Unit = {},
    onSetAppThemeStyle: (AppThemeStyle) -> Unit = {},
    onSetDarkModePreference: (DarkModePreference) -> Unit = {},
    onSetUitvalNotifications: (Boolean) -> Unit = {},
    onSetGradeNotifications: (Boolean) -> Unit = {},
    onSetTestNotifications: (Boolean) -> Unit = {},
    onSetGradeUnboxingEnabled: (Boolean) -> Unit = {},
    onSetGradeUnboxingMode: (GradeUnboxingMode) -> Unit = {},
    onTestUitvalNotification: () -> Boolean = { false },
    onOpenSetupWizard: () -> Unit = {},
    onResetAndLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAccountSheet by remember { mutableStateOf(false) }
    var testNotificationFeedback by remember { mutableStateOf<String?>(null) }
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85
    val cardBorder: BorderStroke? = nothingCardBorder()

    val cleanStudentName = session.studentName.cleanSchoolText() ?: "Account"
    val cleanStudentClass = session.studentClass.cleanSchoolText()
    val firstName = cleanStudentName.split(" ").firstOrNull()?.cleanSchoolText() ?: "Account"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_top_bar"),
        color = when {
            oneUiMode && !collapsedTitle.isNullOrBlank() -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (nothingMode || oneUiMode) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (oneUiMode) 8.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // School Tenant & App Title Brand (or collapsed One UI section title on scroll)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AgendaPlusLogo(
                        size = if (oneUiMode) 36.dp else 40.dp,
                        modifier = Modifier.testTag("app_logo_badge")
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    AnimatedContent(
                        targetState = if (oneUiMode && !collapsedTitle.isNullOrBlank()) collapsedTitle else null,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "topbar_collapsed_title"
                    ) { pinnedTitle ->
                        if (pinnedTitle != null) {
                            Column {
                                Text(
                                    text = pinnedTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isOffline) "Offline-cache • $lastSyncTime" else "Sync: $lastSyncTime",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (nothingMode) "AGENDA+" else "Agenda+",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = cardBorder
                                    ) {
                                        Text(
                                            text = session.provider.displayName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (isOffline) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(percent = 50),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.testTag("topbar_offline_badge")
                                        ) {
                                            Text(
                                                text = "OFFLINE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = session.school?.name?.cleanSchoolText() ?: "Geen school geselecteerd",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Actions: Raw CLI Console + Refresh + Account Avatar Chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenRawConsole,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_raw_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Raw CLI Log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_button")
                    ) {
                        if (isRefreshing) {
                            if (nothingMode) {
                                NothingDotMatrixSpinner()
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Synchroniseer met ${session.provider.displayName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = cardBorder,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { showAccountSheet = true }
                            .testTag("account_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account instellingen",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = firstName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Subtitle status row: Offline sync timestamp (hidden when One UI collapsed title already shows sync)
            if (!(oneUiMode && !collapsedTitle.isNullOrBlank())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !session.isAuthenticated || isOffline -> MaterialTheme.colorScheme.error
                                    nothingMode -> NothingRed
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !session.isAuthenticated -> "Niet verbonden"
                            isOffline -> "Offline-cache actief • $lastSyncTime"
                            else -> "Laatste sync: $lastSyncTime"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (nothingMode) {
                Spacer(modifier = Modifier.height(6.dp))
                NothingDottedDivider()
            } else if (oneUiMode && !collapsedTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    // Account & Settings Bottom Sheet
    if (showAccountSheet) {
        val currentPreset = UiScalePreset.entries.minByOrNull { kotlin.math.abs(it.scaleFactor - uiScale) }
            ?: UiScalePreset.NORMAL

        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("account_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Account & Instellingen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showAccountSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = cleanStudentName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (cleanStudentClass != null) {
                                    Text(
                                        text = cleanStudentClass,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("School:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.school?.name?.cleanSchoolText() ?: "Onbekend", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Systeem:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.provider.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Weergave & Indeling section
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Weergave & Indeling",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "App, Widget & Icoon Thema",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_app_theme_segmented")
                        ) {
                            AppThemeStyle.entries.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = appThemeStyle == style,
                                    onClick = { onSetAppThemeStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppThemeStyle.entries.size
                                    ),
                                    modifier = Modifier.testTag("settings_theme_${style.name}")
                                ) {
                                    Text(
                                        text = style.labelDutch,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = appThemeStyle.subtitleDutch,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Donkere modus",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_dark_mode_segmented")
                        ) {
                            DarkModePreference.entries.forEachIndexed { index, pref ->
                                SegmentedButton(
                                    selected = darkModePreference == pref,
                                    onClick = { onSetDarkModePreference(pref) },
                                    enabled = appThemeStyle != AppThemeStyle.NOTHING_OS,
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = DarkModePreference.entries.size
                                    ),
                                    modifier = Modifier.testTag("settings_dark_mode_${pref.name}")
                                ) {
                                    Text(
                                        text = pref.labelDutch,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (appThemeStyle == AppThemeStyle.NOTHING_OS) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nothing OS gebruikt altijd OLED zwart.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Roosterstijl",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_agenda_style_segmented")
                        ) {
                            AgendaStyle.entries.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = agendaStyle == style,
                                    onClick = { onSetAgendaStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AgendaStyle.entries.size
                                    ),
                                    modifier = Modifier.testTag("settings_style_${style.name}")
                                ) {
                                    Text(style.labelDutch)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Tekstgrootte",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_ui_scale_segmented")
                        ) {
                            UiScalePreset.entries.forEachIndexed { index, preset ->
                                SegmentedButton(
                                    selected = currentPreset == preset,
                                    onClick = { onSetUiScale(preset.scaleFactor) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = UiScalePreset.entries.size
                                    ),
                                    modifier = Modifier.testTag("settings_scale_${preset.name}")
                                ) {
                                    Text(preset.labelDutch)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                showAccountSheet = false
                                onOpenSetupWizard()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reopen_setup_wizard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Startscherm opnieuw openen")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Meldingen & Uitval section
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Meldingen & Cijfer Unboxing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("uitval_notifications_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Uitval & roosterwijzigingen",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Bij lesuitval, lokaal- of tijdwijziging",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uitvalNotificationsEnabled,
                                onCheckedChange = onSetUitvalNotifications,
                                modifier = Modifier.testTag("uitval_notifications_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Nieuwe cijfers",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Directe melding bij nieuw cijfer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = gradeNotificationsEnabled,
                                onCheckedChange = onSetGradeNotifications,
                                modifier = Modifier.testTag("topbar_grade_notifs_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Nieuwe toetsen",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Melding bij nieuwe PW, SO of MO",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = testNotificationsEnabled,
                                onCheckedChange = onSetTestNotifications,
                                modifier = Modifier.testTag("topbar_test_notifs_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Cijfer Unboxing inschakelen",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Modus: ${gradeUnboxingMode.labelDutch}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = gradeUnboxingEnabled,
                                onCheckedChange = onSetGradeUnboxingEnabled,
                                modifier = Modifier.testTag("topbar_unboxing_switch")
                            )
                        }

                        if (gradeUnboxingEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GradeUnboxingMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = gradeUnboxingMode == mode,
                                        onClick = { onSetGradeUnboxingMode(mode) },
                                        label = {
                                            Text(
                                                text = mode.labelDutch.split(" ").first(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                val sent = onTestUitvalNotification()
                                testNotificationFeedback = if (sent) {
                                    "Testmelding verstuurd naar je statusbalk."
                                } else {
                                    "Geen meldingspermissie — sta meldingen toe in Android-instellingen."
                                }
                            },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_uitval_notification_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Uitval Melding")
                        }

                        testNotificationFeedback?.let { feedback ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        showAccountSheet = false
                        onResetAndLogout()
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("reset_app_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App resetten & Uitloggen",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
