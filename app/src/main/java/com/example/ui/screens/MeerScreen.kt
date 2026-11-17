package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.services.WebSyncStatus
import com.example.ui.components.WebSyncCard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.MeerSection
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.NothingAwareDivider
import com.example.ui.components.OneUiExpandingHeader
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appThumbnailShape
import com.example.ui.components.isNothingOs
import com.example.ui.components.isOneUi85
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
    absences: List<AbsenceRecord> = emptyList(),
    eloUrl: String,
    studySites: List<StudySite>,
    agendaStyle: AgendaStyle,
    uiScale: Float,
    appThemeStyle: AppThemeStyle,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    uitvalNotificationsEnabled: Boolean,
    gradeNotificationsEnabled: Boolean = true,
    testNotificationsEnabled: Boolean = true,
    quietHoursConfig: QuietHoursConfig = QuietHoursConfig(),
    mutedSubjects: Set<String> = emptySet(),
    availableSubjects: List<String> = emptyList(),
    smartBusAlertsEnabled: Boolean = true,
    busWalkMinutes: Int = 5,
    busArrivalBufferMinutes: Int = 10,
    gradeUnboxingEnabled: Boolean = true,
    gradeUnboxingMode: GradeUnboxingMode = GradeUnboxingMode.CASE_OPENING,
    onSelectSection: (MeerSection) -> Unit,
    onSetOfflineMode: (Boolean) -> Unit,
    onOpenTodayModifySheet: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenRawConsole: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onSetAgendaStyle: (AgendaStyle) -> Unit,
    onSetUiScale: (Float) -> Unit,
    onSetAppThemeStyle: (AppThemeStyle) -> Unit,
    onSetDarkModePreference: (DarkModePreference) -> Unit = {},
    onSetUitvalNotifications: (Boolean) -> Unit,
    onSetGradeNotifications: (Boolean) -> Unit = {},
    onSetTestNotifications: (Boolean) -> Unit = {},
    onSetQuietHoursEnabled: (Boolean) -> Unit = {},
    onSetQuietHoursStartMinutes: (Int) -> Unit = {},
    onSetQuietHoursEndMinutes: (Int) -> Unit = {},
    onToggleMutedSubject: (String) -> Unit = {},
    onSetSmartBusAlertsEnabled: (Boolean) -> Unit = {},
    onSetBusWalkMinutes: (Int) -> Unit = {},
    onSetBusArrivalBufferMinutes: (Int) -> Unit = {},
    onSetGradeUnboxingEnabled: (Boolean) -> Unit = {},
    onSetGradeUnboxingMode: (GradeUnboxingMode) -> Unit = {},
    onTestUitvalNotification: () -> Boolean,
    onResetAndLogout: () -> Unit,
    onLoadMessageDetail: suspend (String) -> String?,
    onMarkMessageRead: (String) -> Unit,
    onOpenInAppBrowser: (String, String) -> Unit,
    onUpdateEloUrl: (String) -> Unit,
    onAddStudySite: (String, String, String) -> Unit,
    onRemoveStudySite: (String) -> Unit,
    onEnrollKwtOption: suspend (Long, Long) -> Result<Unit>,
    isCheckingUpdate: Boolean = false,
    manualUpdateMessage: String? = null,
    onCheckForUpdate: () -> Unit = {},
    onExportBackupJson: () -> String = { "{}" },
    onImportBackupJson: (String) -> Result<Int> = { Result.failure(UnsupportedOperationException()) },
    webSyncState: WebSyncStatus = WebSyncStatus(),
    onPairUri: (String) -> Result<Unit> = { Result.success(Unit) },
    onSyncNow: () -> Unit = {},
    onPullLatest: () -> Unit = {},
    onUnpair: () -> Unit = {},
    onHeaderCollapsedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentSection != MeerSection.HOME) {
        BackHandler {
            onSelectSection(MeerSection.HOME)
        }
    }

    val context = LocalContext.current
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85
    val homeListState = rememberLazyListState()
    val isHomeScrolled by remember {
        derivedStateOf { homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 70 }
    }
    LaunchedEffect(currentSection, isHomeScrolled, oneUiMode) {
        if (currentSection == MeerSection.HOME) {
            onHeaderCollapsedChange(oneUiMode && isHomeScrolled)
        } else if (currentSection != MeerSection.INSTELLINGEN) {
            onHeaderCollapsedChange(false)
        }
    }
    val unreadMessagesCount = messages.count { !it.isRead }
    var showPromoVideoDialog by remember { mutableStateOf(false) }
    val oneUiInsets = LocalOneUiScreenInsets.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (currentSection != MeerSection.HOME && oneUiMode) {
                    Modifier.padding(top = oneUiInsets.calculateTopPadding())
                } else Modifier
            )
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
                        .padding(horizontal = if (oneUiMode) 12.dp else 8.dp, vertical = 6.dp),
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
                        fontWeight = if (oneUiMode) FontWeight.Medium else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        when (currentSection) {
            MeerSection.HOME -> {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = appScreenHorizontalPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = oneUiInsets.calculateTopPadding() + 12.dp,
                        bottom = oneUiInsets.calculateBottomPadding() + 28.dp
                    )
                ) {
                    item(key = "meer_header") {
                        if (oneUiMode) {
                            OneUiExpandingHeader(
                                title = "Meer",
                                subtitle = "Berichten, bronnen & instellingen",
                                isScrolled = isHomeScrolled
                            )
                        } else {
                            Text(
                                text = "Meer & Instellingen",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Offline-modus toggle card
                    item(key = "meer_offline_card") {
                        Surface(
                            shape = appCardShape(standard = 20.dp, nothing = 24.dp),
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
                                            shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
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

                    // Web Sync & Pairing section
                    item(key = "meer_web_sync") {
                        WebSyncCard(
                            syncState = webSyncState,
                            session = session,
                            onPairUri = onPairUri,
                            onSyncNow = onSyncNow,
                            onPullLatest = onPullLatest,
                            onUnpair = onUnpair
                        )
                    }

                    // Feature hubs
                    item(key = "meer_berichten") {
                        MeerMenuTile(
                            title = "Magister Berichten",
                            subtitle = if (unreadMessagesCount > 0) "$unreadMessagesCount ongelezen bericht(en) • Alleen-lezen" else "Inbox en mappen bekijken (alleen-lezen)",
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

                    item(key = "meer_afwezigheid") {
                        MeerMenuTile(
                            title = "Afwezigheid",
                            subtitle = if (absences.isEmpty()) "Bekijk verzuim, ziekmeldingen en te laat" else "${absences.size} registratie(s) gevonden",
                            icon = Icons.Default.EventBusy,
                            badgeText = if (absences.isNotEmpty()) absences.size.toString() else null,
                            onClick = { onSelectSection(MeerSection.AFWEZIGHEID) },
                            tag = "meer_tile_afwezigheid"
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
                                }.getOrNull()?.takeIf { it.isNotBlank() } ?: com.example.BuildConfig.VERSION_NAME
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
                            badgeText = if (oneUiMode) "Nieuw" else "NIEUW",
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

                    item(key = "meer_version_footer") {
                        val pkgInfo = remember(context) {
                            runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                        }
                        val installedVersion = pkgInfo?.versionName?.takeIf { it.isNotBlank() }
                            ?: com.example.BuildConfig.VERSION_NAME
                        val installedCode = pkgInfo?.let {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                it.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                it.versionCode.toLong()
                            }
                        } ?: com.example.BuildConfig.VERSION_CODE.toLong()
                        val appLabel = remember(context) {
                            context.getString(com.example.R.string.app_name)
                        }
                        Text(
                            text = "$appLabel • v$installedVersion (build $installedCode) • ${context.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp)
                                .testTag("meer_version_info")
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
                    onMarkMessageRead = onMarkMessageRead
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

            MeerSection.AFWEZIGHEID -> {
                AfwezigheidView(absences = absences)
            }

            MeerSection.INSTELLINGEN -> {
                MeerSettingsView(
                    session = session,
                    agendaStyle = agendaStyle,
                    uiScale = uiScale,
                    appThemeStyle = appThemeStyle,
                    darkModePreference = darkModePreference,
                    uitvalNotificationsEnabled = uitvalNotificationsEnabled,
                    gradeNotificationsEnabled = gradeNotificationsEnabled,
                    testNotificationsEnabled = testNotificationsEnabled,
                    quietHoursConfig = quietHoursConfig,
                    mutedSubjects = mutedSubjects,
                    availableSubjects = availableSubjects,
                    smartBusAlertsEnabled = smartBusAlertsEnabled,
                    busWalkMinutes = busWalkMinutes,
                    busArrivalBufferMinutes = busArrivalBufferMinutes,
                    gradeUnboxingEnabled = gradeUnboxingEnabled,
                    gradeUnboxingMode = gradeUnboxingMode,
                    onSetAgendaStyle = onSetAgendaStyle,
                    onSetUiScale = onSetUiScale,
                    onSetAppThemeStyle = onSetAppThemeStyle,
                    onSetDarkModePreference = onSetDarkModePreference,
                    onSetUitvalNotifications = onSetUitvalNotifications,
                    onSetGradeNotifications = onSetGradeNotifications,
                    onSetTestNotifications = onSetTestNotifications,
                    onSetQuietHoursEnabled = onSetQuietHoursEnabled,
                    onSetQuietHoursStartMinutes = onSetQuietHoursStartMinutes,
                    onSetQuietHoursEndMinutes = onSetQuietHoursEndMinutes,
                    onToggleMutedSubject = onToggleMutedSubject,
                    onSetSmartBusAlertsEnabled = onSetSmartBusAlertsEnabled,
                    onSetBusWalkMinutes = onSetBusWalkMinutes,
                    onSetBusArrivalBufferMinutes = onSetBusArrivalBufferMinutes,
                    onSetGradeUnboxingEnabled = onSetGradeUnboxingEnabled,
                    onSetGradeUnboxingMode = onSetGradeUnboxingMode,
                    onTestUitvalNotification = onTestUitvalNotification,
                    onOpenRawConsole = onOpenRawConsole,
                    onOpenSetupWizard = onOpenSetupWizard,
                    onResetAndLogout = onResetAndLogout,
                    isCheckingUpdate = isCheckingUpdate,
                    manualUpdateMessage = manualUpdateMessage,
                    onCheckForUpdate = onCheckForUpdate,
                    onExportBackupJson = onExportBackupJson,
                    onImportBackupJson = onImportBackupJson,
                    webSyncState = webSyncState,
                    onPairUri = onPairUri,
                    onSyncNow = onSyncNow,
                    onPullLatest = onPullLatest,
                    onUnpair = onUnpair,
                    onHeaderCollapsedChange = { collapsed ->
                        onHeaderCollapsedChange(oneUiMode && collapsed)
                    }
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
    val tileShape = appCardShape(standard = 18.dp, nothing = 22.dp)
    Card(
        shape = tileShape,
        border = nothingCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(tileShape)
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
                shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
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
                    fontWeight = if (isOneUi85) FontWeight.Medium else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badgeText != null) {
                if (isOneUi85) {
                    OneUiPillBadge(text = badgeText)
                } else {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeerSettingsView(
    session: AuthSession,
    agendaStyle: AgendaStyle,
    uiScale: Float,
    appThemeStyle: AppThemeStyle,
    darkModePreference: DarkModePreference,
    uitvalNotificationsEnabled: Boolean,
    gradeNotificationsEnabled: Boolean,
    testNotificationsEnabled: Boolean,
    quietHoursConfig: QuietHoursConfig,
    mutedSubjects: Set<String>,
    availableSubjects: List<String>,
    smartBusAlertsEnabled: Boolean,
    busWalkMinutes: Int,
    busArrivalBufferMinutes: Int,
    gradeUnboxingEnabled: Boolean,
    gradeUnboxingMode: GradeUnboxingMode,
    onSetAgendaStyle: (AgendaStyle) -> Unit,
    onSetUiScale: (Float) -> Unit,
    onSetAppThemeStyle: (AppThemeStyle) -> Unit,
    onSetDarkModePreference: (DarkModePreference) -> Unit,
    onSetUitvalNotifications: (Boolean) -> Unit,
    onSetGradeNotifications: (Boolean) -> Unit,
    onSetTestNotifications: (Boolean) -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietHoursStartMinutes: (Int) -> Unit,
    onSetQuietHoursEndMinutes: (Int) -> Unit,
    onToggleMutedSubject: (String) -> Unit,
    onSetSmartBusAlertsEnabled: (Boolean) -> Unit,
    onSetBusWalkMinutes: (Int) -> Unit,
    onSetBusArrivalBufferMinutes: (Int) -> Unit,
    onSetGradeUnboxingEnabled: (Boolean) -> Unit,
    onSetGradeUnboxingMode: (GradeUnboxingMode) -> Unit,
    onTestUitvalNotification: () -> Boolean,
    onOpenRawConsole: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onResetAndLogout: () -> Unit,
    isCheckingUpdate: Boolean = false,
    manualUpdateMessage: String? = null,
    onCheckForUpdate: () -> Unit = {},
    onExportBackupJson: () -> String = { "{}" },
    onImportBackupJson: (String) -> Result<Int> = { Result.failure(UnsupportedOperationException()) },
    webSyncState: WebSyncStatus = WebSyncStatus(),
    onPairUri: (String) -> Result<Unit> = { Result.success(Unit) },
    onSyncNow: () -> Unit = {},
    onPullLatest: () -> Unit = {},
    onUnpair: () -> Unit = {},
    onHeaderCollapsedChange: (Boolean) -> Unit = {}
) {
    val oneUiMode = isOneUi85
    val settingsListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf { settingsListState.firstVisibleItemIndex > 0 || settingsListState.firstVisibleItemScrollOffset > 70 }
    }
    LaunchedEffect(isScrolled, oneUiMode) {
        onHeaderCollapsedChange(oneUiMode && isScrolled)
    }
    val settingsCtx = LocalContext.current
    val currentPreset = UiScalePreset.entries.minByOrNull { kotlin.math.abs(it.scaleFactor - uiScale) }
        ?: UiScalePreset.NORMAL
    var testMsg by remember { mutableStateOf<String?>(null) }
    var backupMsg by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                settingsCtx.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(onExportBackupJson().toByteArray(Charsets.UTF_8))
                } ?: error("Kon bestand niet openen voor opslaan.")
            }.onSuccess {
                backupMsg = "Keuzes-backup succesvol opgeslagen."
            }.onFailure { err ->
                backupMsg = err.localizedMessage ?: "Opslaan van backup mislukt."
            }
        }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = settingsCtx.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("Kon backupbestand niet lezen.")
                onImportBackupJson(raw).getOrThrow()
            }.onSuccess { count ->
                backupMsg = "$count instellingen & keuzes hersteld."
            }.onFailure { err ->
                backupMsg = err.localizedMessage ?: "Herstellen van backup mislukt."
            }
        }
    }

    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        state = settingsListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("meer_settings_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 28.dp
        )
    ) {
        if (oneUiMode) {
            item(key = "oneui_settings_header") {
                OneUiExpandingHeader(
                    title = "Instellingen",
                    subtitle = "${appThemeStyle.labelDutch} • ${darkModePreference.labelDutch}",
                    isScrolled = isScrolled
                )
            }
        }

        // 1. Thema & Weergave
        item {
            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 22.dp),
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
                                shape = SegmentedButtonDefaults.itemShape(idx, AppThemeStyle.entries.size),
                                modifier = Modifier.testTag("settings_theme_${style.name}")
                            ) {
                                Text(
                                    text = style.labelDutch,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Donkere modus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DarkModePreference.entries.forEachIndexed { idx, pref ->
                            SegmentedButton(
                                selected = darkModePreference == pref,
                                onClick = { onSetDarkModePreference(pref) },
                                enabled = appThemeStyle != AppThemeStyle.NOTHING_OS,
                                shape = SegmentedButtonDefaults.itemShape(idx, DarkModePreference.entries.size),
                                modifier = Modifier.testTag("settings_dark_mode_${pref.name}")
                            ) {
                                Text(
                                    text = pref.labelDutch,
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
                            style = MaterialTheme.typography.labelSmall,
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

        // 2. Meldingen (Uitval/Wijzigingen, Cijfers, Toetsen)
        item {
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
                        text = "Meldingen & Auto-Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    SettingsSwitchRow(
                        title = "Uitval & roosterwijzigingen",
                        subtitle = "Directe melding bij lesuitval, lokaal- of tijdwijziging",
                        checked = uitvalNotificationsEnabled,
                        onCheckedChange = onSetUitvalNotifications,
                        tag = "settings_uitval_switch"
                    )

                    NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    SettingsSwitchRow(
                        title = "Nieuwe cijfers",
                        subtitle = "Melding zodra er een nieuw cijfer in Magister verschijnt",
                        checked = gradeNotificationsEnabled,
                        onCheckedChange = onSetGradeNotifications,
                        tag = "settings_grade_notifs_switch"
                    )

                    NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    SettingsSwitchRow(
                        title = "Nieuwe toetsen",
                        subtitle = "Melding bij een nieuw proefwerk, SO, MO of toets",
                        checked = testNotificationsEnabled,
                        onCheckedChange = onSetTestNotifications,
                        tag = "settings_test_notifs_switch"
                    )

                    NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    SettingsSwitchRow(
                        title = "Stille uren",
                        subtitle = "Onderdruk meldingen tussen ${com.example.notifications.SmartBusPlanner.formatMinutes(quietHoursConfig.startMinutes)} en ${com.example.notifications.SmartBusPlanner.formatMinutes(quietHoursConfig.endMinutes)}",
                        checked = quietHoursConfig.enabled,
                        onCheckedChange = onSetQuietHoursEnabled,
                        tag = "settings_quiet_hours_switch"
                    )

                    if (quietHoursConfig.enabled) {
                        Text(
                            text = "Start stille uren",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(21 * 60 to "21:00", 22 * 60 to "22:00", 23 * 60 to "23:00").forEach { (mins, lbl) ->
                                FilterChip(
                                    selected = quietHoursConfig.startMinutes == mins,
                                    onClick = { onSetQuietHoursStartMinutes(mins) },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        Text(
                            text = "Einde stille uren",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6 * 60 + 30 to "06:30", 7 * 60 to "07:00", 8 * 60 to "08:00").forEach { (mins, lbl) ->
                                FilterChip(
                                    selected = quietHoursConfig.endMinutes == mins,
                                    onClick = { onSetQuietHoursEndMinutes(mins) },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }

                    if (availableSubjects.isNotEmpty()) {
                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Text(
                            text = "Vakken dempen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Kies vakken waarvoor je geen meldingen wilt ontvangen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableSubjects.forEach { subj ->
                                val isMuted = mutedSubjects.any { it.equals(subj, ignoreCase = true) }
                                FilterChip(
                                    selected = isMuted,
                                    onClick = { onToggleMutedSubject(subj) },
                                    label = { Text(subj) },
                                    modifier = Modifier.testTag("mute_subject_chip_$subj")
                                )
                            }
                        }
                    }

                    NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    SettingsSwitchRow(
                        title = "Slimme Busmelding",
                        subtitle = "Waarschuwing wanneer 1e uur uitvalt en je later kunt vertrekken",
                        checked = smartBusAlertsEnabled,
                        onCheckedChange = onSetSmartBusAlertsEnabled,
                        tag = "settings_smart_bus_switch"
                    )

                    if (smartBusAlertsEnabled) {
                        Text(
                            text = "Looptijd naar bushalte",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 8, 10).forEach { mins ->
                                FilterChip(
                                    selected = busWalkMinutes == mins,
                                    onClick = { onSetBusWalkMinutes(mins) },
                                    label = { Text("$mins min") }
                                )
                            }
                        }
                        Text(
                            text = "Marge voor eerste les",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { mins ->
                                FilterChip(
                                    selected = busArrivalBufferMinutes == mins,
                                    onClick = { onSetBusArrivalBufferMinutes(mins) },
                                    label = { Text("$mins min") }
                                )
                            }
                        }
                    }

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
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 3. Cijfer Unboxing Minigames
        item {
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
                    SettingsSwitchRow(
                        title = "Cijfer Unboxing inschakelen",
                        subtitle = "Onthul nieuwe cijfers via interactieve minigames",
                        checked = gradeUnboxingEnabled,
                        onCheckedChange = onSetGradeUnboxingEnabled,
                        tag = "settings_unboxing_switch"
                    )

                    if (gradeUnboxingEnabled) {
                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Text(
                            text = "Kies Unboxing Minigame",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        GradeUnboxingMode.entries.forEach { mode ->
                            val selected = gradeUnboxingMode == mode
                            val optionShape = appInnerCardShape(standard = 14.dp, nothing = 14.dp)
                            Surface(
                                shape = optionShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = nothingCardBorder(isAlert = selected),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(optionShape)
                                    .clickable { onSetGradeUnboxingMode(mode) }
                                    .testTag("settings_unboxing_mode_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.labelDutch,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = mode.subtitleDutch,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Updates & Keuzes-backup
        item {
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
                        text = "Updates & Keuzes-backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Werk bij zonder dataverlies of bewaar je thema, Vandaag-indeling, bustijd en studiewebsites als JSON-backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCheckForUpdate,
                        enabled = !isCheckingUpdate,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_check_updates_button")
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Controleren op updates...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Controleer op updates", fontWeight = FontWeight.Bold)
                        }
                    }
                    manualUpdateMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("settings_update_status_text")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportBackupLauncher.launch("agendaplus_keuzes_backup.json") },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_export_backup_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exporteer", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_import_backup_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Herstel", maxLines = 1)
                        }
                    }
                    backupMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("settings_backup_status_text")
                        )
                    }
                }
            }
        }

        // 5. Web Sync & Pairing
        item {
            WebSyncCard(
                syncState = webSyncState,
                session = session,
                onPairUri = onPairUri,
                onSyncNow = onSyncNow,
                onPullLatest = onPullLatest,
                onUnpair = onUnpair
            )
        }

        // 6. Account, Console & Reset
        item {
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
                    val settingsCtx = LocalContext.current
                    val pkgInfo = remember(settingsCtx) {
                        runCatching { settingsCtx.packageManager.getPackageInfo(settingsCtx.packageName, 0) }.getOrNull()
                    }
                    val installedVersion = pkgInfo?.versionName?.takeIf { it.isNotBlank() }
                        ?: com.example.BuildConfig.VERSION_NAME
                    val installedCode = pkgInfo?.let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            it.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            it.versionCode.toLong()
                        }
                    } ?: com.example.BuildConfig.VERSION_CODE.toLong()
                    val appLabel = remember(settingsCtx) {
                        settingsCtx.getString(com.example.R.string.app_name)
                    }
                    Text(
                        text = "Geïnstalleerde build: $appLabel v$installedVersion ($installedCode) • ${settingsCtx.packageName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_version_info")
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun AfwezigheidView(
    absences: List<AbsenceRecord>,
    modifier: Modifier = Modifier
) {
    val oneUiInsets = LocalOneUiScreenInsets.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = appScreenHorizontalPadding())
            .testTag("afwezigheid_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = oneUiInsets.calculateBottomPadding() + 28.dp
        )
    ) {
        if (absences.isEmpty()) {
            item(key = "absences_empty") {
                Surface(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = nothingCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("afwezigheid_empty_card")
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Geen afwezigheid geregistreerd",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Eventuele ziekmeldingen, te-laatmeldingen en geoorloofd verzuim uit de afgelopen 60 dagen verschijnen hier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(absences, key = { it.id }) { record ->
                val isAlert = record.type == AbsenceType.UNEXCUSED || !record.isApproved
                Card(
                    shape = appCardShape(standard = 20.dp, nothing = 24.dp),
                    border = nothingCardBorder(isAlert = isAlert),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("absence_record_${record.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
                            color = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (record.type) {
                                        AbsenceType.SICK -> Icons.Default.LocalHospital
                                        AbsenceType.TARDY -> Icons.Default.Schedule
                                        AbsenceType.EXCUSED -> Icons.Default.Verified
                                        AbsenceType.UNEXCUSED -> Icons.Default.WarningAmber
                                    },
                                    contentDescription = record.type.labelDutch,
                                    tint = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = record.type.labelDutch,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OneUiPillBadge(
                                    text = if (record.isApproved) "Geoorloofd" else "Ongeoorloofd",
                                    isError = !record.isApproved
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${record.date} • ${record.period}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (record.reason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = record.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
