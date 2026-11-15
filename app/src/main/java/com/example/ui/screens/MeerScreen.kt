package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.MeerSection
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder

@Composable
fun MeerScreen(
    currentSection: MeerSection,
    session: AuthSession,
    offlineModeEnabled: Boolean,
    isEffectivelyOffline: Boolean,
    appointmentsCount: Int,
    gradesCount: Int,
    messages: List<MagisterMessage>,
    messageFolders: List<MessageFolderInfo>,
    studyGuides: List<StudyGuide>,
    kwtSlots: List<KwtSlot>,
    eloUrl: String,
    studySites: List<StudySite>,
    agendaStyle: AgendaStyle,
    uiScale: Float,
    appThemeStyle: AppThemeStyle,
    uitvalNotificationsEnabled: Boolean,
    onSelectSection: (MeerSection) -> Unit,
    onSetOfflineMode: (Boolean) -> Unit,
    onOpenTodayModifySheet: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenRawConsole: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onSetAgendaStyle: (AgendaStyle) -> Unit,
    onSetUiScale: (Float) -> Unit,
    onSetAppThemeStyle: (AppThemeStyle) -> Unit,
    onSetUitvalNotifications: (Boolean) -> Unit,
    onTestUitvalNotification: () -> Boolean,
    onResetAndLogout: () -> Unit,
    onLoadMessageDetail: suspend (String) -> String?,
    onMarkMessageRead: (String) -> Unit,
    onSearchRecipients: suspend (String) -> List<MessageRecipient>,
    onSendMessage: suspend (List<MessageRecipient>, String, String) -> Result<Unit>,
    onOpenInAppBrowser: (String, String) -> Unit,
    onUpdateEloUrl: (String) -> Unit,
    onAddStudySite: (String, String, String) -> Unit,
    onRemoveStudySite: (String) -> Unit,
    onEnrollKwtOption: suspend (Long, Long) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    if (currentSection != MeerSection.HOME) {
        BackHandler {
            onSelectSection(MeerSection.HOME)
        }
    }

    val context = LocalContext.current
    val nothingMode = isNothingOs
    val unreadMessagesCount = messages.count { !it.isRead }
    var showPromoVideoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("meer_screen")
    ) {
        if (currentSection != MeerSection.HOME) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectSection(MeerSection.HOME) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("meer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Terug naar Meer"
                        )
                    }
                    Text(
                        text = currentSection.titleDutch,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        when (currentSection) {
            MeerSection.HOME -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
                ) {
                    item(key = "meer_header") {
                        Text(
                            text = "Meer & Instellingen",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Offline-modus toggle card
                    item(key = "meer_offline_card") {
                        Surface(
                            shape = RoundedCornerShape(if (nothingMode) 24.dp else 20.dp),
                            color = if (isEffectivelyOffline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = nothingCardBorder(isAlert = isEffectivelyOffline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("meer_offline_mode_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isEffectivelyOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                                    contentDescription = "Offline-modus",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Offline-modus (Cache)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (offlineModeEnabled) "Ingeschakeld • Alleen lokaal opgeslagen gegevens" else "Gebruik opgeslagen cache zonder netwerkverbruik",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = offlineModeEnabled,
                                        onCheckedChange = onSetOfflineMode,
                                        modifier = Modifier.testTag("offline_mode_switch")
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Opgeslagen: $appointmentsCount lessen • $gradesCount vakken • ${messages.size} berichten • ${studyGuides.size} studiewijzers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Feature hubs
                    item(key = "meer_berichten") {
                        MeerMenuTile(
                            title = "Magister Berichten",
                            subtitle = if (unreadMessagesCount > 0) "$unreadMessagesCount ongelezen bericht(en)" else "Inbox, mappen en nieuw bericht sturen",
                            icon = Icons.Default.Mail,
                            badgeText = if (unreadMessagesCount > 0) unreadMessagesCount.toString() else null,
                            onClick = { onSelectSection(MeerSection.BERICHTEN) },
                            tag = "meer_tile_berichten"
                        )
                    }

                    item(key = "meer_studiewijzers") {
                        MeerMenuTile(
                            title = "Studiewijzers & Bronnen",
                            subtitle = "${studyGuides.size} studiewijzers • ELO (itslearning) • ${studySites.size} bronnen",
                            icon = Icons.Default.AutoStories,
                            badgeText = null,
                            onClick = { onSelectSection(MeerSection.STUDIEWIJZERS) },
                            tag = "meer_tile_studiewijzers"
                        )
                    }

                    item(key = "meer_kwt") {
                        MeerMenuTile(
                            title = "Keuzelessen (KWT)",
                            subtitle = if (kwtSlots.isEmpty()) "Bekijk en kies beschikbare keuzelessen" else "${kwtSlots.size} KWT-blok(ken) beschikbaar",
                            icon = Icons.Default.FactCheck,
                            badgeText = if (kwtSlots.isNotEmpty()) kwtSlots.size.toString() else null,
                            onClick = { onSelectSection(MeerSection.KWT) },
                            tag = "meer_tile_kwt"
                        )
                    }

                    item(key = "meer_vandaag_customize") {
                        MeerMenuTile(
                            title = "Vandaag, Bustijd & Schoolnavigatie",
                            subtitle = "Blokken ordenen, buslijn volgen en schooladres kiezen",
                            icon = Icons.Default.DirectionsBus,
                            badgeText = null,
                            onClick = onOpenTodayModifySheet,
                            tag = "meer_tile_vandaag_nav"
                        )
                    }

                    item(key = "meer_tutorial") {
                        MeerMenuTile(
                            title = "App-uitleg (Tutorial)",
                            subtitle = "Bekijk stap-voor-stap hoe Agenda+ werkt",
                            icon = Icons.Default.HelpOutline,
                            badgeText = null,
                            onClick = onOpenTutorial,
                            tag = "meer_tile_tutorial"
                        )
                    }

                    item(key = "meer_support") {
                        MeerMenuTile(
                            title = "Contact & Support",
                            subtitle = "Vragen, feedback of hulp nodig? Mail support@agendaplus.nl",
                            icon = Icons.Default.SupportAgent,
                            badgeText = null,
                            onClick = {
                                val ctx = context
                                val versionName = runCatching {
                                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                                }.getOrDefault("0.1.2")
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@agendaplus.nl?subject=${Uri.encode("Agenda+ Support (v$versionName)")}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching {
                                    ctx.startActivity(intent)
                                }.onFailure {
                                    android.widget.Toast.makeText(ctx, "Geen e-mailapp gevonden", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            tag = "meer_tile_support"
                        )
                    }

                    item(key = "meer_promo_video") {
                        MeerMenuTile(
                            title = "Bekijk Promo Video (115 BPM)",
                            subtitle = "9:16 Motion Graphics • Nothing OS • Rozovoye Vino beat",
                            icon = Icons.Default.PlayCircle,
                            badgeText = "NIEUW",
                            onClick = { showPromoVideoDialog = true },
                            tag = "meer_tile_promo_video"
                        )
                    }

                    item(key = "meer_settings") {
                        MeerMenuTile(
                            title = "App-instellingen & Thema",
                            subtitle = "${appThemeStyle.labelDutch} • ${agendaStyle.labelDutch} • Meldingen",
                            icon = Icons.Default.Settings,
                            badgeText = null,
                            onClick = { onSelectSection(MeerSection.INSTELLINGEN) },
                            tag = "meer_tile_instellingen"
                        )
                    }
                }
            }

            MeerSection.BERICHTEN -> {
                BerichtenView(
                    messages = messages,
                    folders = messageFolders,
                    isOffline = isEffectivelyOffline,
                    onLoadMessageDetail = onLoadMessageDetail,
                    onMarkMessageRead = onMarkMessageRead,
                    onSearchRecipients = onSearchRecipients,
                    onSendMessage = onSendMessage
                )
            }

            MeerSection.STUDIEWIJZERS -> {
                StudiewijzersView(
                    studyGuides = studyGuides,
                    eloUrl = eloUrl,
                    studySites = studySites,
                    onOpenInApp = onOpenInAppBrowser,
                    onUpdateEloUrl = onUpdateEloUrl,
                    onAddStudySite = onAddStudySite,
                    onRemoveStudySite = onRemoveStudySite
                )
            }

            MeerSection.KWT -> {
                KwtView(
                    kwtSlots = kwtSlots,
                    isOffline = isEffectivelyOffline,
                    onEnrollOption = onEnrollKwtOption
                )
            }

            MeerSection.INSTELLINGEN -> {
                MeerSettingsView(
                    session = session,
                    agendaStyle = agendaStyle,
                    uiScale = uiScale,
                    appThemeStyle = appThemeStyle,
                    uitvalNotificationsEnabled = uitvalNotificationsEnabled,
                    onSetAgendaStyle = onSetAgendaStyle,
                    onSetUiScale = onSetUiScale,
                    onSetAppThemeStyle = onSetAppThemeStyle,
                    onSetUitvalNotifications = onSetUitvalNotifications,
                    onTestUitvalNotification = onTestUitvalNotification,
                    onOpenRawConsole = onOpenRawConsole,
                    onOpenSetupWizard = onOpenSetupWizard,
                    onResetAndLogout = onResetAndLogout
                )
            }
        }
    }

    if (showPromoVideoDialog) {
        Dialog(
            onDismissRequest = { showPromoVideoDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                webViewClient = WebViewClient()
                                loadUrl("file:///android_asset/promo-video/index.html")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { showPromoVideoDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sluit promo video",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeerMenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String?,
    onClick: () -> Unit,
    tag: String
) {
    val nothingMode = isNothingOs
    Card(
        shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (nothingMode) 22.dp else 18.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badgeText != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MeerSettingsView(
    session: AuthSession,
    agendaStyle: AgendaStyle,
    uiScale: Float,
    appThemeStyle: AppThemeStyle,
    uitvalNotificationsEnabled: Boolean,
    onSetAgendaStyle: (AgendaStyle) -> Unit,
    onSetUiScale: (Float) -> Unit,
    onSetAppThemeStyle: (AppThemeStyle) -> Unit,
    onSetUitvalNotifications: (Boolean) -> Unit,
    onTestUitvalNotification: () -> Boolean,
    onOpenRawConsole: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onResetAndLogout: () -> Unit
) {
    val nothingMode = isNothingOs
    val currentPreset = UiScalePreset.entries.minByOrNull { kotlin.math.abs(it.scaleFactor - uiScale) }
        ?: UiScalePreset.NORMAL
    var testMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("meer_settings_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. Thema & Weergave
        item {
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Thema & Weergave",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "App, Widget & Icoon Thema",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppThemeStyle.entries.forEachIndexed { idx, style ->
                            SegmentedButton(
                                selected = appThemeStyle == style,
                                onClick = { onSetAppThemeStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(idx, AppThemeStyle.entries.size)
                            ) {
                                Text(style.labelDutch)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Roosterstijl",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AgendaStyle.entries.forEachIndexed { idx, style ->
                            SegmentedButton(
                                selected = agendaStyle == style,
                                onClick = { onSetAgendaStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(idx, AgendaStyle.entries.size)
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        UiScalePreset.entries.forEachIndexed { idx, preset ->
                            SegmentedButton(
                                selected = currentPreset == preset,
                                onClick = { onSetUiScale(preset.scaleFactor) },
                                shape = SegmentedButtonDefaults.itemShape(idx, UiScalePreset.entries.size)
                            ) {
                                Text(preset.labelDutch)
                            }
                        }
                    }
                }
            }
        }

        // 2. Uitval Meldingen
        item {
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Uitvalmeldingen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Directe melding zodra een les vervalt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uitvalNotificationsEnabled,
                            onCheckedChange = onSetUitvalNotifications
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val ok = onTestUitvalNotification()
                            testMsg = if (ok) "Testmelding verzonden." else "Sta meldingen toe in Android-instellingen."
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test uitvalmelding")
                    }
                    testMsg?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 3. Account, Console & Reset
        item {
            Surface(
                shape = RoundedCornerShape(if (nothingMode) 22.dp else 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Account & Diagnose (${session.studentName.cleanSchoolText() ?: "Leerling"})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onOpenRawConsole,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Netwerk- & API-console openen")
                    }
                    OutlinedButton(
                        onClick = onOpenSetupWizard,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Startscherm opnieuw openen")
                    }
                    Button(
                        onClick = onResetAndLogout,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("App resetten & Uitloggen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
