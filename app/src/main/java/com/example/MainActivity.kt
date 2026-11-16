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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppThemeStyle
import com.example.model.cleanSchoolText
import com.example.notifications.UitvalNotificationHelper
import com.example.ui.AgendaViewModel
import com.example.ui.AppTab
import com.example.ui.MeerSection
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.components.AppTopBar
import com.example.ui.components.LocalOneUiBackdropState
import com.example.ui.components.LocalOneUiScreenInsets
import com.example.ui.components.OneUiBackdropState
import com.example.ui.components.oneUiSpring
import com.example.ui.components.oneUiTween
import com.example.ui.dialogs.AddTaskDialog
import com.example.ui.dialogs.CustomScheduleItemDialog
import com.example.ui.dialogs.GradeExportSheet
import com.example.ui.dialogs.GradeUnboxingDialog
import com.example.ui.dialogs.InAppWebViewDialog
import com.example.ui.dialogs.LoginDialog
import com.example.ui.dialogs.MagisterOAuthWebViewDialog
import com.example.ui.dialogs.TestPrepChecklistSheet
import com.example.ui.dialogs.TodayModifySheet
import com.example.ui.dialogs.TutorialDialog
import com.example.ui.screens.*
import com.example.ui.theme.AgendaPlusTheme
import com.example.widget.WidgetUpdater
import kotlin.math.round

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
            val darkModePreference by viewModel.darkModePreference.collectAsStateWithLifecycle()

            AgendaPlusTheme(
                appThemeStyle = appThemeStyle,
                darkModePreference = darkModePreference
            ) {
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
    val darkModePreference by viewModel.darkModePreference.collectAsStateWithLifecycle()
    val eloUrl by viewModel.eloUrl.collectAsStateWithLifecycle()
    val studySites by viewModel.studySites.collectAsStateWithLifecycle()
    val weatherInfo by viewModel.weatherInfo.collectAsStateWithLifecycle()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsStateWithLifecycle()
    val inAppBrowserTarget by viewModel.inAppBrowserTarget.collectAsStateWithLifecycle()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageFolders by viewModel.messageFolders.collectAsStateWithLifecycle()
    val studyGuides by viewModel.studyGuides.collectAsStateWithLifecycle()
    val kwtSlots by viewModel.kwtSlots.collectAsStateWithLifecycle()
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val customScheduleItems by viewModel.customScheduleItems.collectAsStateWithLifecycle()
    val testPrepChecklists by viewModel.testPrepChecklists.collectAsStateWithLifecycle()
    val quietHoursConfig by viewModel.quietHoursConfig.collectAsStateWithLifecycle()
    val mutedSubjects by viewModel.mutedSubjects.collectAsStateWithLifecycle()
    val smartBusAlertsEnabled by viewModel.smartBusAlertsEnabled.collectAsStateWithLifecycle()
    val busWalkMinutes by viewModel.busWalkMinutes.collectAsStateWithLifecycle()
    val busArrivalBufferMinutes by viewModel.busArrivalBufferMinutes.collectAsStateWithLifecycle()
    val smartBusAdvice by viewModel.smartBusAdvice.collectAsStateWithLifecycle()
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
    val isKwtSheetLoading by viewModel.isKwtSheetLoading.collectAsStateWithLifecycle()
    val activeChecklistKey by viewModel.activeChecklistKey.collectAsStateWithLifecycle()
    val showCustomScheduleDialog by viewModel.showCustomScheduleDialog.collectAsStateWithLifecycle()
    val editingCustomScheduleItem by viewModel.editingCustomScheduleItem.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val manualUpdateMessage by viewModel.manualUpdateMessage.collectAsStateWithLifecycle()

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val uitvalNotificationsEnabled by viewModel.uitvalNotificationsEnabled.collectAsStateWithLifecycle()
    val gradeNotificationsEnabled by viewModel.gradeNotificationsEnabled.collectAsStateWithLifecycle()
    val testNotificationsEnabled by viewModel.testNotificationsEnabled.collectAsStateWithLifecycle()
    val gradeUnboxingEnabled by viewModel.gradeUnboxingEnabled.collectAsStateWithLifecycle()
    val gradeUnboxingMode by viewModel.gradeUnboxingMode.collectAsStateWithLifecycle()
    val unboxedGradeIds by viewModel.unboxedGradeIds.collectAsStateWithLifecycle()
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
    val showGradeExportSheet by viewModel.showGradeExportSheet.collectAsStateWithLifecycle()
    val unboxingTargetGrade by viewModel.unboxingTargetGrade.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    var isHeaderCollapsed by remember(currentTab, currentMeerSection, roosterViewMode) { mutableStateOf(false) }
    var customDialogDateIso by remember {
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
    }
    var customDialogDayOfWeek by remember { mutableIntStateOf(1) }

    val availableSubjects = remember(appointments, gradeSummaries) {
        (appointments.mapNotNull { it.subjectName.cleanSchoolText() } +
            gradeSummaries.mapNotNull { it.subjectName.cleanSchoolText() })
            .distinct()
            .sorted()
    }

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

            val anyNotificationEnabled = uitvalNotificationsEnabled || gradeNotificationsEnabled || testNotificationsEnabled

            fun requestNotifPermissionIfNeeded() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }

            LaunchedEffect(session.isAuthenticated, anyNotificationEnabled) {
                UitvalNotificationHelper.ensureChannel(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    anyNotificationEnabled &&
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

            val collapsedTitle = if (appThemeStyle == AppThemeStyle.ONE_UI && isHeaderCollapsed) {
                when {
                    currentTab == AppTab.MEER && currentMeerSection == MeerSection.INSTELLINGEN -> "Instellingen"
                    currentTab == AppTab.ROOSTER && roosterViewMode == 3 -> "Toetsen"
                    else -> currentTab.title
                }
            } else null

            val topBarComposable: @Composable (Modifier) -> Unit = { barMod ->
                AppTopBar(
                    session = session,
                    lastSyncTime = lastSyncTime,
                    isRefreshing = isRefreshing,
                    agendaStyle = agendaStyle,
                    uiScale = uiScale,
                    appThemeStyle = appThemeStyle,
                    darkModePreference = darkModePreference,
                    collapsedTitle = collapsedTitle,
                    uitvalNotificationsEnabled = uitvalNotificationsEnabled,
                    gradeNotificationsEnabled = gradeNotificationsEnabled,
                    testNotificationsEnabled = testNotificationsEnabled,
                    gradeUnboxingEnabled = gradeUnboxingEnabled,
                    gradeUnboxingMode = gradeUnboxingMode,
                    isOffline = isEffectivelyOffline,
                    onRefreshClick = { viewModel.refreshAll() },
                    onOpenRawConsole = { viewModel.openRawConsole() },
                    onSetAgendaStyle = { viewModel.setAgendaStyle(it) },
                    onSetUiScale = { viewModel.setUiScale(it) },
                    onSetAppThemeStyle = { viewModel.setAppThemeStyle(it) },
                    onSetDarkModePreference = { viewModel.setDarkModePreference(it) },
                    onSetUitvalNotifications = { enabled ->
                        viewModel.setUitvalNotificationsEnabled(enabled)
                        if (enabled) requestNotifPermissionIfNeeded()
                    },
                    onSetGradeNotifications = { enabled ->
                        viewModel.setGradeNotificationsEnabled(enabled)
                        if (enabled) requestNotifPermissionIfNeeded()
                    },
                    onSetTestNotifications = { enabled ->
                        viewModel.setTestNotificationsEnabled(enabled)
                        if (enabled) requestNotifPermissionIfNeeded()
                    },
                    onSetGradeUnboxingEnabled = { viewModel.setGradeUnboxingEnabled(it) },
                    onSetGradeUnboxingMode = { viewModel.setGradeUnboxingMode(it) },
                    onTestUitvalNotification = { viewModel.sendTestUitvalNotification() },
                    onOpenSetupWizard = { viewModel.reopenOnboarding() },
                    onResetAndLogout = { viewModel.resetAppAndLogout() },
                    modifier = barMod
                )
            }

            val bottomBarComposable: @Composable (Modifier) -> Unit = { navMod ->
                AppBottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    modifier = navMod
                )
            }

            val renderTabContent: @Composable (AppTab, Modifier) -> Unit = { tab, contentModifier ->
                when (tab) {
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
                            smartBusAdvice = smartBusAdvice,
                            customScheduleItems = customScheduleItems,
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
                            onAddCustomScheduleItem = {
                                val cal = java.util.Calendar.getInstance()
                                customDialogDateIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                                customDialogDayOfWeek = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                                    java.util.Calendar.MONDAY -> 1
                                    java.util.Calendar.TUESDAY -> 2
                                    java.util.Calendar.WEDNESDAY -> 3
                                    java.util.Calendar.THURSDAY -> 4
                                    java.util.Calendar.FRIDAY -> 5
                                    java.util.Calendar.SATURDAY -> 6
                                    else -> 7
                                }
                                viewModel.openCustomScheduleDialog(null)
                            },
                            onEditCustomScheduleItem = { viewModel.openCustomScheduleDialog(it) },
                            onToggleCustomScheduleItem = { id, done -> viewModel.toggleCustomScheduleItem(id, done) },
                            onDeleteCustomScheduleItem = { id -> viewModel.deleteCustomScheduleItem(id) },
                            onOpenKwtSlot = { appt -> viewModel.openKwtSlotSheet(appt) },
                            onHeaderCollapsedChange = { isHeaderCollapsed = it },
                            modifier = contentModifier
                        )
                    }
                    AppTab.ROOSTER -> {
                        RoosterScreen(
                            appointments = appointments,
                            tasks = tasks,
                            selectedDay = selectedDay,
                            viewMode = roosterViewMode,
                            agendaStyle = agendaStyle,
                            isRefreshing = isRefreshing,
                            kwtSlots = kwtSlots,
                            customScheduleItems = customScheduleItems,
                            testPrepChecklists = testPrepChecklists,
                            onSelectDay = { viewModel.selectDay(it) },
                            onSelectViewMode = { viewModel.setRoosterViewMode(it) },
                            onOpenKwtSlot = { appt -> viewModel.openKwtSlotSheet(appt) },
                            onAddCustomScheduleItem = { dateIso, dow ->
                                customDialogDateIso = dateIso
                                customDialogDayOfWeek = dow
                                viewModel.openCustomScheduleDialog(null)
                            },
                            onEditCustomScheduleItem = { viewModel.openCustomScheduleDialog(it) },
                            onToggleCustomScheduleItem = { id, done -> viewModel.toggleCustomScheduleItem(id, done) },
                            onDeleteCustomScheduleItem = { id -> viewModel.deleteCustomScheduleItem(id) },
                            onOpenTestPrep = { key, subj, title, stof ->
                                viewModel.openTestPrepChecklist(key, subj, title, stof)
                            },
                            onHeaderCollapsedChange = { isHeaderCollapsed = it },
                            modifier = contentModifier
                        )
                    }
                    AppTab.HUISWERK -> {
                        HuiswerkScreen(
                            tasks = tasks,
                            selectedCategory = taskFilter,
                            testPrepChecklists = testPrepChecklists,
                            onSelectCategory = { viewModel.setTaskFilter(it) },
                            onToggleTask = { viewModel.toggleTask(it) },
                            onAddTaskClick = { viewModel.openAddTaskDialog() },
                            onOpenTestPrep = { key, subj, title, stof ->
                                viewModel.openTestPrepChecklist(key, subj, title, stof)
                            },
                            onHeaderCollapsedChange = { isHeaderCollapsed = it },
                            modifier = contentModifier
                        )
                    }
                    AppTab.CIJFERS -> {
                        CijfersScreen(
                            summaries = gradeSummaries,
                            gradeUnboxingEnabled = gradeUnboxingEnabled,
                            unboxedGradeIds = unboxedGradeIds,
                            onOpenWhatIf = { viewModel.openWhatIf(it) },
                            onOpenExportSheet = { viewModel.openGradeExportSheet() },
                            onPlayGradeUnboxing = { grade -> viewModel.openGradeUnboxing(grade) },
                            onHeaderCollapsedChange = { isHeaderCollapsed = it },
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
                            absences = absences,
                            eloUrl = eloUrl,
                            studySites = studySites,
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
                            onSelectSection = { viewModel.selectMeerSection(it) },
                            onSetOfflineMode = { viewModel.setOfflineModeEnabled(it) },
                            onOpenTodayModifySheet = { viewModel.openTodayModifySheet() },
                            onOpenTutorial = { viewModel.openTutorial() },
                            onOpenRawConsole = { viewModel.openRawConsole() },
                            onOpenSetupWizard = { viewModel.reopenOnboarding() },
                            onSetAgendaStyle = { viewModel.setAgendaStyle(it) },
                            onSetUiScale = { viewModel.setUiScale(it) },
                            onSetAppThemeStyle = { viewModel.setAppThemeStyle(it) },
                            onSetDarkModePreference = { viewModel.setDarkModePreference(it) },
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
                            onSetGradeNotifications = { enabled ->
                                viewModel.setGradeNotificationsEnabled(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSetTestNotifications = { enabled ->
                                viewModel.setTestNotificationsEnabled(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSetQuietHoursEnabled = { viewModel.setQuietHoursEnabled(it) },
                            onSetQuietHoursStartMinutes = { viewModel.setQuietHoursStartMinutes(it) },
                            onSetQuietHoursEndMinutes = { viewModel.setQuietHoursEndMinutes(it) },
                            onToggleMutedSubject = { viewModel.toggleMutedSubject(it) },
                            onSetSmartBusAlertsEnabled = { viewModel.setSmartBusAlertsEnabled(it) },
                            onSetBusWalkMinutes = { viewModel.setBusWalkMinutes(it) },
                            onSetBusArrivalBufferMinutes = { viewModel.setBusArrivalBufferMinutes(it) },
                            onSetGradeUnboxingEnabled = { viewModel.setGradeUnboxingEnabled(it) },
                            onSetGradeUnboxingMode = { viewModel.setGradeUnboxingMode(it) },
                            onTestUitvalNotification = { viewModel.sendTestUitvalNotification() },
                            onResetAndLogout = { viewModel.resetAppAndLogout() },
                            onLoadMessageDetail = { id -> viewModel.loadMessageDetail(id) },
                            onMarkMessageRead = { id -> viewModel.markMessageRead(id) },
                            onOpenInAppBrowser = { title, url -> viewModel.openInAppBrowser(title, url) },
                            onUpdateEloUrl = { viewModel.setEloUrl(it) },
                            onAddStudySite = { name, url, cat -> viewModel.addStudySite(name, url, cat) },
                            onRemoveStudySite = { viewModel.removeStudySite(it) },
                            onEnrollKwtOption = { apptId, optId -> viewModel.enrollKwtOption(apptId, optId) },
                            isCheckingUpdate = isCheckingUpdate,
                            manualUpdateMessage = manualUpdateMessage,
                            onCheckForUpdate = { viewModel.checkForUpdateManually() },
                            onExportBackupJson = { viewModel.exportChoicesBackupJson() },
                            onImportBackupJson = { raw -> viewModel.importChoicesBackupJson(raw) },
                            onHeaderCollapsedChange = { isHeaderCollapsed = it },
                            modifier = contentModifier
                        )
                    }
                }
            }

            if (appThemeStyle == AppThemeStyle.ONE_UI) {
                val densityNow = LocalDensity.current
                val backdropLayer = rememberGraphicsLayer()
                var contentOriginInRoot by remember { mutableStateOf(Offset.Zero) }
                var topBarHeightDp by remember { mutableStateOf(96.dp) }
                var bottomBarHeightDp by remember { mutableStateOf(88.dp) }

                CompositionLocalProvider(
                    LocalOneUiBackdropState provides OneUiBackdropState(
                        graphicsLayer = backdropLayer,
                        contentOriginInRoot = contentOriginInRoot
                    ),
                    LocalOneUiScreenInsets provides PaddingValues(
                        top = topBarHeightDp,
                        bottom = bottomBarHeightDp
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords ->
                                    contentOriginInRoot = coords.positionInRoot()
                                }
                                .drawWithContent {
                                    backdropLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(backdropLayer)
                                }
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    (fadeIn(oneUiTween(280)) + scaleIn(
                                        initialScale = 0.985f,
                                        animationSpec = oneUiSpring()
                                    )) togetherWith fadeOut(oneUiTween(180))
                                },
                                label = "oneui_tab_transition"
                            ) { activeTab ->
                                renderTabContent(activeTab, Modifier.fillMaxSize())
                            }
                        }

                        topBarComposable(
                            Modifier
                                .align(Alignment.TopCenter)
                                .onSizeChanged { size ->
                                    topBarHeightDp = with(densityNow) { size.height.toDp() }
                                }
                        )

                        bottomBarComposable(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged { size ->
                                    bottomBarHeightDp = with(densityNow) { size.height.toDp() }
                                }
                        )
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { topBarComposable(Modifier) },
                    bottomBar = { bottomBarComposable(Modifier) }
                ) { innerPadding ->
                    renderTabContent(currentTab, Modifier.padding(innerPadding))
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
                allSubjects = gradeSummaries,
                onDismiss = { viewModel.closeWhatIf() }
            )
        }

        if (showGradeExportSheet) {
            val overallAvg = remember(gradeSummaries) {
                if (gradeSummaries.isNotEmpty()) {
                    round((gradeSummaries.sumOf { it.weightedAverage } / gradeSummaries.size) * 10) / 10.0
                } else 0.0
            }
            GradeExportSheet(
                summaries = gradeSummaries,
                overallAverage = overallAvg,
                studentName = session.studentName,
                appThemeStyle = appThemeStyle,
                onDismiss = { viewModel.closeGradeExportSheet() }
            )
        }

        unboxingTargetGrade?.let { targetGrade ->
            GradeUnboxingDialog(
                grade = targetGrade,
                initialMode = gradeUnboxingMode,
                onChangeMode = { viewModel.setGradeUnboxingMode(it) },
                onRevealComplete = { viewModel.closeGradeUnboxing(markUnboxed = true) },
                onDismiss = { viewModel.closeGradeUnboxing(markUnboxed = true) }
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
                isLoading = isKwtSheetLoading,
                onEnrollOption = { apptId, optId -> viewModel.enrollKwtOption(apptId, optId) },
                onDismiss = { viewModel.closeKwtSlotSheet() }
            )
        }

        activeChecklistKey?.let { key ->
            val checklist = testPrepChecklists[key]
            if (checklist != null) {
                TestPrepChecklistSheet(
                    checklist = checklist,
                    onToggleTopic = { topicId -> viewModel.toggleTestPrepTopic(key, topicId) },
                    onAddTopic = { label -> viewModel.addTestPrepTopic(key, label) },
                    onRemoveTopic = { topicId -> viewModel.removeTestPrepTopic(key, topicId) },
                    onDismiss = { viewModel.closeTestPrepChecklist() }
                )
            }
        }

        if (showCustomScheduleDialog) {
            CustomScheduleItemDialog(
                existingItem = editingCustomScheduleItem,
                initialDateIso = customDialogDateIso,
                initialDayOfWeek = customDialogDayOfWeek,
                onSave = { item -> viewModel.saveCustomScheduleItem(item) },
                onDelete = { id -> viewModel.deleteCustomScheduleItem(id) },
                onDismiss = { viewModel.closeCustomScheduleDialog() }
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
