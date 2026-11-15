package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notifications.UitvalNotificationHelper
import com.example.ui.AgendaViewModel
import com.example.ui.AppTab
import com.example.ui.MeerSection
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.components.AppTopBar
import com.example.ui.dialogs.AddTaskDialog
import com.example.ui.dialogs.InAppWebViewDialog
import com.example.ui.dialogs.LoginDialog
import com.example.ui.dialogs.MagisterOAuthWebViewDialog
import com.example.ui.dialogs.TodayModifySheet
import com.example.ui.dialogs.TutorialDialog
import com.example.ui.screens.*
import com.example.ui.theme.AgendaPlusTheme
import com.example.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    private var pendingOAuthCode: String? = null
    private var pendingIdToken: String? = null
    private var deepLinkTriggered by mutableStateOf(0)
    private var pendingWidgetTab by mutableStateOf<AppTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            val viewModel: AgendaViewModel = viewModel()
            val appThemeStyle by viewModel.appThemeStyle.collectAsStateWithLifecycle()

            AgendaPlusTheme(appThemeStyle = appThemeStyle) {
                LaunchedEffect(pendingWidgetTab) {
                    pendingWidgetTab?.let { tab ->
                        viewModel.selectTab(tab)
                        pendingWidgetTab = null
                    }
                }

                LaunchedEffect(deepLinkTriggered) {
                    val code = pendingOAuthCode
                    if (!code.isNullOrBlank()) {
                        viewModel.onOAuthCodeCaptured(code, pendingIdToken)
                        pendingOAuthCode = null
                        pendingIdToken = null
                    }
                }

                AgendaPlusApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val requestedTabName = intent?.getStringExtra(WidgetUpdater.EXTRA_OPEN_TAB)
        if (!requestedTabName.isNullOrBlank()) {
            pendingWidgetTab = runCatching { AppTab.valueOf(requestedTabName) }.getOrNull()
        }
        val uri: Uri = intent?.data ?: return
        val scheme = uri.scheme
        val host = uri.host
        if (scheme == "m6loapp" && host == "oauth2redirect") {
            val normalized = uri.toString().replace("#", "?")
            val parsed = Uri.parse(normalized)
            val code = parsed.getQueryParameter("code")
            val idToken = parsed.getQueryParameter("id_token")
            val accessToken = parsed.getQueryParameter("access_token")

            val tokenOrCode = code?.takeIf { it.isNotBlank() }
                ?: accessToken?.takeIf { it.isNotBlank() }
                ?: idToken

            if (!tokenOrCode.isNullOrBlank()) {
                pendingOAuthCode = tokenOrCode
                pendingIdToken = idToken
                deepLinkTriggered++
            }
        }
    }
}

@Composable
fun AgendaPlusApp(
    viewModel: AgendaViewModel = viewModel()
) {
    val session by viewModel.currentSession.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val agendaStyle by viewModel.agendaStyle.collectAsStateWithLifecycle()
    val uiScale by viewModel.uiScale.collectAsStateWithLifecycle()
    val appThemeStyle by viewModel.appThemeStyle.collectAsStateWithLifecycle()
    val eloUrl by viewModel.eloUrl.collectAsStateWithLifecycle()
    val studySites by viewModel.studySites.collectAsStateWithLifecycle()
    val weatherInfo by viewModel.weatherInfo.collectAsStateWithLifecycle()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsStateWithLifecycle()
    val inAppBrowserTarget by viewModel.inAppBrowserTarget.collectAsStateWithLifecycle()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageFolders by viewModel.messageFolders.collectAsStateWithLifecycle()
    val studyGuides by viewModel.studyGuides.collectAsStateWithLifecycle()
    val kwtSlots by viewModel.kwtSlots.collectAsStateWithLifecycle()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsStateWithLifecycle()
    val isEffectivelyOffline = offlineModeEnabled || !isDeviceOnline
    val navPromptDismissed by viewModel.navPromptDismissed.collectAsStateWithLifecycle()
    val todayBlocks by viewModel.todayBlocks.collectAsStateWithLifecycle()
    val schoolAddress by viewModel.schoolAddress.collectAsStateWithLifecycle()
    val savedBusLine by viewModel.savedBusLine.collectAsStateWithLifecycle()
    val busTrackingState by viewModel.busTrackingState.collectAsStateWithLifecycle()
    val currentMeerSection by viewModel.currentMeerSection.collectAsStateWithLifecycle()
    val showTutorialDialog by viewModel.showTutorialDialog.collectAsStateWithLifecycle()
    val showTodayModifySheet by viewModel.showTodayModifySheet.collectAsStateWithLifecycle()
    val activeKwtSlotForSheet by viewModel.activeKwtSlotForSheet.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val uitvalNotificationsEnabled by viewModel.uitvalNotificationsEnabled.collectAsStateWithLifecycle()
    val gradeSummaries by viewModel.gradeSummaries.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val roosterViewMode by viewModel.roosterViewMode.collectAsStateWithLifecycle()
    val taskFilter by viewModel.taskFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()

    val selectedProviderForLogin by viewModel.selectedProviderForLogin.collectAsStateWithLifecycle()
    val showLoginDialog by viewModel.showLoginDialog.collectAsStateWithLifecycle()
    val showOAuthWebView by viewModel.showOAuthWebView.collectAsStateWithLifecycle()
    val oAuthTargetTenant by viewModel.oAuthTargetTenant.collectAsStateWithLifecycle()
    val oAuthUrl by viewModel.oAuthUrl.collectAsStateWithLifecycle()
    val showRawConsole by viewModel.showRawConsole.collectAsStateWithLifecycle()

    val showAddTaskDialog by viewModel.showAddTaskDialog.collectAsStateWithLifecycle()
    val selectedSubjectForWhatIf by viewModel.selectedSubjectForWhatIf.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * uiScale
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        // 1. First-download onboarding setup wizard (only on initial install or if reopened in settings)
        if (!hasCompletedOnboarding) {
            SetupWizardScreen(
                initialStyle = agendaStyle,
                initialScale = uiScale,
                onComplete = { style, scale ->
                    viewModel.completeOnboarding(style, scale)
                }
            )
        } else if (!session.isAuthenticated) {
            // 2. Login landing screen (skipped automatically once authenticated!)
            WelcomeScreen(
                onSelectProvider = { provider ->
                    viewModel.selectProviderAndOpenLogin(provider)
                },
                onOpenRawConsole = { viewModel.openRawConsole() }
            )
        } else {
            // 3. Main Authenticated School Dashboard
            val context = LocalContext.current
            var requestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* User response handled gracefully by UitvalNotificationHelper */ }

            LaunchedEffect(session.isAuthenticated, uitvalNotificationsEnabled) {
                UitvalNotificationHelper.ensureChannel(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    uitvalNotificationsEnabled &&
                    !requestedNotificationPermission &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestedNotificationPermission = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (currentTab != AppTab.TODAY && !(currentTab == AppTab.MEER && currentMeerSection != MeerSection.HOME)) {
                BackHandler {
                    viewModel.selectTab(AppTab.TODAY)
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AppTopBar(
                        session = session,
                        lastSyncTime = lastSyncTime,
                        isRefreshing = isRefreshing,
                        agendaStyle = agendaStyle,
                        uiScale = uiScale,
                        appThemeStyle = appThemeStyle,
                        uitvalNotificationsEnabled = uitvalNotificationsEnabled,
                        isOffline = isEffectivelyOffline,
                        onRefreshClick = { viewModel.refreshAll() },
                        onOpenRawConsole = { viewModel.openRawConsole() },
                        onSetAgendaStyle = { viewModel.setAgendaStyle(it) },
                        onSetUiScale = { viewModel.setUiScale(it) },
                        onSetAppThemeStyle = { viewModel.setAppThemeStyle(it) },
                        onSetUitvalNotifications = { enabled ->
                            viewModel.setUitvalNotificationsEnabled(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onTestUitvalNotification = { viewModel.sendTestUitvalNotification() },
                        onOpenSetupWizard = { viewModel.reopenOnboarding() },
                        onResetAndLogout = { viewModel.resetAppAndLogout() }
                    )
                },
                bottomBar = {
                    AppBottomNavigationBar(
                        currentTab = currentTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)

                when (currentTab) {
                    AppTab.TODAY -> {
                        TodayFeedScreen(
                            appointments = appointments,
                            tasks = tasks,
                            kwtSlots = kwtSlots,
                            weatherInfo = weatherInfo,
                            isWeatherLoading = isWeatherLoading,
                            agendaStyle = agendaStyle,
                            isRefreshing = isRefreshing,
                            isOffline = isEffectivelyOffline,
                            navPromptDismissed = navPromptDismissed,
                            todayBlocks = todayBlocks,
                            schoolAddress = schoolAddress,
                            savedBusLine = savedBusLine,
                            busTrackingState = busTrackingState,
                            onNavigateToRooster = { viewModel.selectTab(AppTab.ROOSTER) },
                            onNavigateToTasks = { viewModel.selectTab(AppTab.HUISWERK) },
                            onNavigateToKwt = {
                                viewModel.selectMeerSection(MeerSection.KWT)
                                viewModel.selectTab(AppTab.MEER)
                            },
                            onOpenElo = { viewModel.openEloInApp() },
                            onRefreshWeather = { viewModel.refreshWeather() },
                            onSearchCities = { query -> viewModel.searchCities(query) },
                            onSelectWeatherCity = { name, lat, lon ->
                                viewModel.selectWeatherCity(name, lat, lon)
                            },
                            onToggleTask = { viewModel.toggleTask(it) },
                            onDismissNavPrompt = { viewModel.dismissNavPrompt() },
                            onOpenTodayModifySheet = { viewModel.openTodayModifySheet() },
                            onSetBusLine = { line, lat, lon -> viewModel.setBusLineAndTrack(line, lat, lon) },
                            onRefreshBusTracking = { lat, lon -> viewModel.refreshBusTracking(lat, lon) },
                            modifier = contentModifier
                        )
                    }
                    AppTab.ROOSTER -> {
                        RoosterScreen(
                            appointments = appointments,
                            selectedDay = selectedDay,
                            viewMode = roosterViewMode,
                            agendaStyle = agendaStyle,
                            isRefreshing = isRefreshing,
                            kwtSlots = kwtSlots,
                            onSelectDay = { viewModel.selectDay(it) },
                            onSelectViewMode = { viewModel.setRoosterViewMode(it) },
                            onOpenKwtSlot = { appt -> viewModel.openKwtSlotSheet(appt) },
                            modifier = contentModifier
                        )
                    }
                    AppTab.HUISWERK -> {
                        HuiswerkScreen(
                            tasks = tasks,
                            selectedCategory = taskFilter,
                            onSelectCategory = { viewModel.setTaskFilter(it) },
                            onToggleTask = { viewModel.toggleTask(it) },
                            onAddTaskClick = { viewModel.openAddTaskDialog() },
                            modifier = contentModifier
                        )
                    }
                    AppTab.CIJFERS -> {
                        CijfersScreen(
                            summaries = gradeSummaries,
                            onOpenWhatIf = { viewModel.openWhatIf(it) },
                            modifier = contentModifier
                        )
                    }
                    AppTab.MEER -> {
                        MeerScreen(
                            currentSection = currentMeerSection,
                            session = session,
                            offlineModeEnabled = offlineModeEnabled,
                            isEffectivelyOffline = isEffectivelyOffline,
                            appointmentsCount = appointments.size,
                            gradesCount = gradeSummaries.size,
                            messages = messages,
                            messageFolders = messageFolders,
                            studyGuides = studyGuides,
                            kwtSlots = kwtSlots,
                            eloUrl = eloUrl,
                            studySites = studySites,
                            agendaStyle = agendaStyle,
                            uiScale = uiScale,
                            appThemeStyle = appThemeStyle,
                            uitvalNotificationsEnabled = uitvalNotificationsEnabled,
                            onSelectSection = { viewModel.selectMeerSection(it) },
                            onSetOfflineMode = { viewModel.setOfflineModeEnabled(it) },
                            onOpenTodayModifySheet = { viewModel.openTodayModifySheet() },
                            onOpenTutorial = { viewModel.openTutorial() },
                            onOpenRawConsole = { viewModel.openRawConsole() },
                            onOpenSetupWizard = { viewModel.reopenOnboarding() },
                            onSetAgendaStyle = { viewModel.setAgendaStyle(it) },
                            onSetUiScale = { viewModel.setUiScale(it) },
                            onSetAppThemeStyle = { viewModel.setAppThemeStyle(it) },
                            onSetUitvalNotifications = { viewModel.setUitvalNotificationsEnabled(it) },
                            onTestUitvalNotification = { viewModel.sendTestUitvalNotification() },
                            onResetAndLogout = { viewModel.resetAppAndLogout() },
                            onLoadMessageDetail = { id -> viewModel.loadMessageDetail(id) },
                            onMarkMessageRead = { id -> viewModel.markMessageRead(id) },
                            onSearchRecipients = { q -> viewModel.searchMessageRecipients(q) },
                            onSendMessage = { rec, subj, body -> viewModel.sendMessage(rec, subj, body) },
                            onOpenInAppBrowser = { title, url -> viewModel.openInAppBrowser(title, url) },
                            onUpdateEloUrl = { viewModel.setEloUrl(it) },
                            onAddStudySite = { name, url, cat -> viewModel.addStudySite(name, url, cat) },
                            onRemoveStudySite = { viewModel.removeStudySite(it) },
                            onEnrollKwtOption = { apptId, optId -> viewModel.enrollKwtOption(apptId, optId) },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }

        // Modal Dialogs & WebViews
        if (showLoginDialog) {
            val tenants = viewModel.getTenantsForProvider(selectedProviderForLogin)
            LoginDialog(
                provider = selectedProviderForLogin,
                availableTenants = tenants,
                currentTenant = session.school,
                activeVerifier = viewModel.activeVerifier,
                isAuthenticating = isAuthenticating,
                authError = authError,
                onDismiss = { viewModel.closeLoginDialog() },
                onLogin = { tenant, code -> viewModel.performSchoolAuth(tenant, code) },
                onOpenWebViewLogin = { tenant -> viewModel.openOAuthWebView(tenant) },
                onClearError = { viewModel.clearAuthError() }
            )
        }

        if (showOAuthWebView) {
            MagisterOAuthWebViewDialog(
                authUrl = oAuthUrl,
                tenant = oAuthTargetTenant,
                onCodeReceived = { code, idToken -> viewModel.onOAuthCodeCaptured(code, idToken) },
                onOpenRawConsole = { viewModel.openRawConsole() },
                onDismiss = { viewModel.closeOAuthWebView() }
            )
        }

        inAppBrowserTarget?.let { target ->
            InAppWebViewDialog(
                title = target.title,
                initialUrl = target.url,
                onDismiss = { viewModel.closeInAppBrowser() }
            )
        }

        if (showRawConsole) {
            com.example.ui.dialogs.RawConsoleDialog(
                onDismiss = { viewModel.closeRawConsole() }
            )
        }

        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { viewModel.closeAddTaskDialog() },
                onAdd = { subject, title, desc, dueDate, category, priority ->
                    viewModel.addTask(subject, title, desc, dueDate, category, priority)
                }
            )
        }

        selectedSubjectForWhatIf?.let { subject ->
            WhatIfBottomSheet(
                subjectSummary = subject,
                onDismiss = { viewModel.closeWhatIf() }
            )
        }

        if (showTutorialDialog) {
            TutorialDialog(
                onDismissAndComplete = { viewModel.closeAndMarkTutorialSeen() }
            )
        }

        if (showTodayModifySheet) {
            TodayModifySheet(
                todayBlocks = todayBlocks,
                schoolAddress = schoolAddress,
                savedBusLine = savedBusLine,
                isOffline = isEffectivelyOffline,
                onToggleBlock = { type, enabled -> viewModel.toggleTodayBlock(type, enabled) },
                onMoveBlock = { type, delta -> viewModel.moveTodayBlock(type, delta) },
                onSearchSchoolAddress = { query -> viewModel.searchSchoolAddresses(query) },
                onSaveSchoolAddress = { addr -> viewModel.saveSchoolAddress(addr) },
                onSetBusLine = { line -> viewModel.setBusLineAndTrack(line) },
                onDismiss = { viewModel.closeTodayModifySheet() }
            )
        }

        activeKwtSlotForSheet?.let { slot ->
            KwtPickerBottomSheet(
                slot = slot,
                isOffline = isEffectivelyOffline,
                onEnrollOption = { apptId, optId -> viewModel.enrollKwtOption(apptId, optId) },
                onDismiss = { viewModel.closeKwtSlotSheet() }
            )
        }

        updateInfo?.let { info ->
            com.example.ui.dialogs.UpdateDialog(
                updateInfo = info,
                appThemeStyle = appThemeStyle,
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
    }
}
