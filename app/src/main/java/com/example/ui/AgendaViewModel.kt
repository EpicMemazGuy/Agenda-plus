package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AgendaRepository
import com.example.data.db.CustomScheduleItem
import com.example.model.*
import com.example.network.AuthResult
import com.example.network.CitySearchResult
import com.example.network.PkceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val title: String) {
    TODAY("Vandaag"),
    ROOSTER("Rooster"),
    HUISWERK("Huiswerk"),
    CIJFERS("Cijfers"),
    MEER("Meer")
}

enum class MeerSection(val titleDutch: String) {
    HOME("Meer"),
    BERICHTEN("Magister Berichten"),
    AFWEZIGHEID("Afwezigheid"),
    STUDIEWIJZERS("Studiewijzers & Bronnen"),
    KWT("Keuzelessen (KWT)"),
    INSTELLINGEN("Instellingen")
}

data class AuthUiError(
    val title: String,
    val message: String,
    val technicalDetails: String
)

data class InAppBrowserTarget(
    val title: String,
    val url: String
)

class AgendaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AgendaRepository(application.applicationContext)

    val currentSession: StateFlow<AuthSession> = repository.currentSession
    val appointments: StateFlow<List<Appointment>> = repository.appointments
    val disruptions: StateFlow<List<Disruption>> = repository.disruptions
    val tasks: StateFlow<List<HomeworkTask>> = repository.tasks
    val gradeSummaries: StateFlow<List<SubjectGradeSummary>> = repository.gradeSummaries
    val messages: StateFlow<List<MagisterMessage>> = repository.messages
    val messageFolders: StateFlow<List<MessageFolderInfo>> = repository.messageFolders
    val studyGuides: StateFlow<List<StudyGuide>> = repository.studyGuides
    val kwtSlots: StateFlow<List<KwtSlot>> = repository.kwtSlots
    val absences: StateFlow<List<AbsenceRecord>> = repository.absences
    val customScheduleItems: StateFlow<List<CustomScheduleItem>> = repository.customScheduleItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val testPrepChecklists: StateFlow<Map<String, TestPrepChecklist>> = repository.testPrepChecklists
    val quietHoursConfig: StateFlow<QuietHoursConfig> = repository.quietHoursConfig
    val mutedSubjects: StateFlow<Set<String>> = repository.mutedSubjects
    val smartBusAlertsEnabled: StateFlow<Boolean> = repository.smartBusAlertsEnabled
    val busWalkMinutes: StateFlow<Int> = repository.busWalkMinutes
    val busArrivalBufferMinutes: StateFlow<Int> = repository.busArrivalBufferMinutes
    val smartBusAdvice: StateFlow<SmartBusAdvice?> = repository.smartBusAdvice
    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing
    val lastSyncTime: StateFlow<String> = repository.lastSyncTime

    val hasCompletedOnboarding: StateFlow<Boolean> = repository.hasCompletedOnboarding
    val hasSeenTutorial: StateFlow<Boolean> = repository.hasSeenTutorial
    val navPromptDismissed: StateFlow<Boolean> = repository.navPromptDismissed
    val offlineModeEnabled: StateFlow<Boolean> = repository.offlineModeEnabled
    val isDeviceOnline: StateFlow<Boolean> = repository.isDeviceOnline
    val todayBlocks: StateFlow<List<TodayBlockConfig>> = repository.todayBlocks
    val schoolAddress: StateFlow<SchoolAddress?> = repository.schoolAddress
    val savedBusLine: StateFlow<String> = repository.savedBusLine
    val busTrackingState: StateFlow<BusTrackingState> = repository.busTrackingState

    val agendaStyle: StateFlow<AgendaStyle> = repository.agendaStyle
    val uiScale: StateFlow<Float> = repository.uiScale
    val appThemeStyle: StateFlow<AppThemeStyle> = repository.appThemeStyle
    val darkModePreference: StateFlow<DarkModePreference> = repository.darkModePreference
    val eloUrl: StateFlow<String> = repository.eloUrl
    val studySites: StateFlow<List<StudySite>> = repository.studySites
    val weatherInfo: StateFlow<WeatherInfo?> = repository.weatherInfo
    val isWeatherLoading: StateFlow<Boolean> = repository.isWeatherLoading
    val uitvalNotificationsEnabled: StateFlow<Boolean> = repository.uitvalNotificationsEnabled
    val gradeNotificationsEnabled: StateFlow<Boolean> = repository.gradeNotificationsEnabled
    val testNotificationsEnabled: StateFlow<Boolean> = repository.testNotificationsEnabled
    val gradeUnboxingEnabled: StateFlow<Boolean> = repository.gradeUnboxingEnabled
    val gradeUnboxingMode: StateFlow<GradeUnboxingMode> = repository.gradeUnboxingMode
    val unboxedGradeIds: StateFlow<Set<String>> = repository.unboxedGradeIds

    private val _currentTab = MutableStateFlow(AppTab.TODAY)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _currentMeerSection = MutableStateFlow(MeerSection.HOME)
    val currentMeerSection: StateFlow<MeerSection> = _currentMeerSection.asStateFlow()

    private val _updateInfo = MutableStateFlow<com.example.util.GithubReleaseInfo?>(null)
    val updateInfo: StateFlow<com.example.util.GithubReleaseInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _manualUpdateMessage = MutableStateFlow<String?>(null)
    val manualUpdateMessage: StateFlow<String?> = _manualUpdateMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val info = com.example.util.UpdateChecker.checkForUpdate(application.applicationContext)
            if (info != null) {
                _updateInfo.value = info
            }
        }
    }

    fun checkForUpdateManually() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _manualUpdateMessage.value = null
            when (val result = com.example.util.UpdateChecker.checkForUpdateDetailed(getApplication<Application>().applicationContext)) {
                is com.example.util.UpdateCheckResult.UpdateAvailable -> {
                    _updateInfo.value = result.release
                    _manualUpdateMessage.value = null
                }
                is com.example.util.UpdateCheckResult.UpToDate -> {
                    _manualUpdateMessage.value = "Je gebruikt al de nieuwste versie (v${result.currentVersion})."
                }
                is com.example.util.UpdateCheckResult.Error -> {
                    _manualUpdateMessage.value = result.message
                }
            }
            _isCheckingUpdate.value = false
        }
    }

    fun exportChoicesBackupJson(): String = repository.exportChoicesBackupJson()

    fun importChoicesBackupJson(rawJson: String): Result<Int> = repository.importChoicesBackupJson(rawJson)

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    private val _showTutorialDialog = MutableStateFlow(false)
    val showTutorialDialog: StateFlow<Boolean> = _showTutorialDialog.asStateFlow()

    private val _showTodayModifySheet = MutableStateFlow(false)
    val showTodayModifySheet: StateFlow<Boolean> = _showTodayModifySheet.asStateFlow()

    private val _activeKwtSlotForSheet = MutableStateFlow<KwtSlot?>(null)
    val activeKwtSlotForSheet: StateFlow<KwtSlot?> = _activeKwtSlotForSheet.asStateFlow()

    private val _isKwtSheetLoading = MutableStateFlow(false)
    val isKwtSheetLoading: StateFlow<Boolean> = _isKwtSheetLoading.asStateFlow()

    private val _activeChecklistKey = MutableStateFlow<String?>(null)
    val activeChecklistKey: StateFlow<String?> = _activeChecklistKey.asStateFlow()

    private val _showCustomScheduleDialog = MutableStateFlow(false)
    val showCustomScheduleDialog: StateFlow<Boolean> = _showCustomScheduleDialog.asStateFlow()

    private val _editingCustomScheduleItem = MutableStateFlow<CustomScheduleItem?>(null)
    val editingCustomScheduleItem: StateFlow<CustomScheduleItem?> = _editingCustomScheduleItem.asStateFlow()

    private fun currentSchoolDay(): Int {
        return when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            else -> 1
        }
    }

    private val _selectedDay = MutableStateFlow(currentSchoolDay())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _roosterViewMode = MutableStateFlow(0) // 0: Dag, 1: 3-Dagen, 2: Week, 3: Toetsen
    val roosterViewMode: StateFlow<Int> = _roosterViewMode.asStateFlow()

    private val _taskFilter = MutableStateFlow<TaskCategory?>(null)
    val taskFilter: StateFlow<TaskCategory?> = _taskFilter.asStateFlow()

    private val _selectedProviderForLogin = MutableStateFlow(SchoolProvider.MAGISTER)
    val selectedProviderForLogin: StateFlow<SchoolProvider> = _selectedProviderForLogin.asStateFlow()

    private val _showLoginDialog = MutableStateFlow(false)
    val showLoginDialog: StateFlow<Boolean> = _showLoginDialog.asStateFlow()

    private val _showOAuthWebView = MutableStateFlow(false)
    val showOAuthWebView: StateFlow<Boolean> = _showOAuthWebView.asStateFlow()

    private val _oAuthTargetTenant = MutableStateFlow<SchoolTenant?>(null)
    val oAuthTargetTenant: StateFlow<SchoolTenant?> = _oAuthTargetTenant.asStateFlow()

    private val _oAuthUrl = MutableStateFlow("")
    val oAuthUrl: StateFlow<String> = _oAuthUrl.asStateFlow()

    private val _inAppBrowserTarget = MutableStateFlow<InAppBrowserTarget?>(null)
    val inAppBrowserTarget: StateFlow<InAppBrowserTarget?> = _inAppBrowserTarget.asStateFlow()

    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    private val _selectedSubjectForWhatIf = MutableStateFlow<SubjectGradeSummary?>(null)
    val selectedSubjectForWhatIf: StateFlow<SubjectGradeSummary?> = _selectedSubjectForWhatIf.asStateFlow()

    private val _showGradeExportSheet = MutableStateFlow(false)
    val showGradeExportSheet: StateFlow<Boolean> = _showGradeExportSheet.asStateFlow()

    private val _unboxingTargetGrade = MutableStateFlow<GradeItem?>(null)
    val unboxingTargetGrade: StateFlow<GradeItem?> = _unboxingTargetGrade.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<AuthUiError?>(null)
    val authError: StateFlow<AuthUiError?> = _authError.asStateFlow()

    private val _showRawConsole = MutableStateFlow(false)
    val showRawConsole: StateFlow<Boolean> = _showRawConsole.asStateFlow()

    var activeVerifier: String = PkceHelper.generateVerifier()
        private set
    var activeState: String = PkceHelper.generateRandomState()
        private set
    private var lastProcessedAuthCode: String? = null

    private var lastResumeSyncMs = 0L

    init {
        viewModelScope.launch {
            repository.checkConnectivity()
            repository.refreshWeather()
            if (repository.savedBusLine.value.isNotBlank()) {
                repository.refreshBusTracking(null, null)
            }
        }
        viewModelScope.launch {
            if (repository.currentSession.value.isAuthenticated) {
                if (!repository.hasSeenTutorial.value) {
                    _showTutorialDialog.value = true
                }
                lastResumeSyncMs = System.currentTimeMillis()
                repository.refreshAll()
            }
            while (true) {
                delay(60_000L)
                repository.checkConnectivity()
                if (repository.currentSession.value.isAuthenticated && !repository.isEffectivelyOffline()) {
                    repository.refreshAll()
                }
            }
        }
    }

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (now - lastResumeSyncMs < 5_000L) return
        lastResumeSyncMs = now
        viewModelScope.launch {
            repository.checkConnectivity()
            if (repository.currentSession.value.isAuthenticated && !repository.isEffectivelyOffline()) {
                repository.refreshAll()
            }
        }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        repository.setOfflineModeEnabled(enabled)
        if (!enabled && repository.currentSession.value.isAuthenticated) {
            refreshAll()
        }
    }

    fun openTutorial() {
        _showTutorialDialog.value = true
    }

    fun closeAndMarkTutorialSeen() {
        _showTutorialDialog.value = false
        repository.markTutorialSeen()
    }

    fun openTodayModifySheet() {
        _showTodayModifySheet.value = true
    }

    fun closeTodayModifySheet() {
        _showTodayModifySheet.value = false
    }

    fun dismissNavPrompt() {
        repository.dismissNavPrompt()
    }

    fun toggleTodayBlock(type: TodayBlockType, enabled: Boolean) {
        repository.toggleTodayBlock(type, enabled)
    }

    fun moveTodayBlock(type: TodayBlockType, delta: Int) {
        repository.moveTodayBlock(type, delta)
    }

    suspend fun searchSchoolAddresses(query: String): List<SchoolAddress> =
        repository.searchSchoolAddresses(query)

    fun saveSchoolAddress(address: SchoolAddress) {
        repository.saveSchoolAddress(address)
    }

    fun setBusLineAndTrack(line: String, userLat: Double? = null, userLon: Double? = null) {
        viewModelScope.launch {
            repository.setBusLineAndTrack(line, userLat, userLon)
        }
    }

    fun refreshBusTracking(userLat: Double? = null, userLon: Double? = null) {
        viewModelScope.launch {
            repository.refreshBusTracking(userLat, userLon)
        }
    }

    fun selectMeerSection(section: MeerSection) {
        _currentMeerSection.value = section
    }

    fun openKwtSlotSheet(appointment: Appointment) {
        val existing = kwtSlots.value.firstOrNull { it.appointmentId == appointment.id }
        _activeKwtSlotForSheet.value = existing ?: KwtSlot(
            appointmentId = appointment.id,
            title = appointment.title,
            dateIso = appointment.dateIso,
            startTime = appointment.startTime,
            endTime = appointment.endTime,
            periodIndex = appointment.periodIndex,
            options = emptyList()
        )
        if (existing == null || existing.options.isEmpty()) {
            viewModelScope.launch {
                _isKwtSheetLoading.value = true
                val live = repository.fetchKwtSlotForAppointment(appointment)
                if (live != null && _activeKwtSlotForSheet.value?.appointmentId == appointment.id) {
                    _activeKwtSlotForSheet.value = live
                }
                _isKwtSheetLoading.value = false
            }
        }
    }

    fun closeKwtSlotSheet() {
        _activeKwtSlotForSheet.value = null
        _isKwtSheetLoading.value = false
    }

    fun openTestPrepChecklist(key: String, subject: String, title: String, toetsstof: String) {
        repository.openOrCreateTestPrepChecklist(key, subject, title, toetsstof)
        _activeChecklistKey.value = key
    }

    fun closeTestPrepChecklist() {
        _activeChecklistKey.value = null
    }

    fun toggleTestPrepTopic(key: String, topicId: String) {
        repository.toggleTestPrepTopic(key, topicId)
    }

    fun addTestPrepTopic(key: String, label: String) {
        repository.addTestPrepTopic(key, label)
    }

    fun removeTestPrepTopic(key: String, topicId: String) {
        repository.removeTestPrepTopic(key, topicId)
    }

    fun openCustomScheduleDialog(item: CustomScheduleItem? = null) {
        _editingCustomScheduleItem.value = item
        _showCustomScheduleDialog.value = true
    }

    fun closeCustomScheduleDialog() {
        _showCustomScheduleDialog.value = false
        _editingCustomScheduleItem.value = null
    }

    fun saveCustomScheduleItem(item: CustomScheduleItem) {
        viewModelScope.launch {
            repository.upsertCustomScheduleItem(item)
            closeCustomScheduleDialog()
        }
    }

    fun toggleCustomScheduleItem(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setCustomScheduleItemCompleted(id, completed)
        }
    }

    fun deleteCustomScheduleItem(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomScheduleItem(id)
        }
    }

    suspend fun enrollKwtOption(appointmentId: Long, optionId: Long): Result<Unit> {
        return repository.enrollKwtOption(appointmentId, optionId)
    }

    suspend fun loadMessageDetail(messageId: String): String? = repository.loadMessageDetail(messageId)

    fun markMessageRead(messageId: String) {
        viewModelScope.launch { repository.markMessageRead(messageId) }
    }

    fun isAnyNotificationEnabled(): Boolean = repository.isAnyNotificationEnabled()

    fun setUitvalNotificationsEnabled(enabled: Boolean) {
        repository.setUitvalNotificationsEnabled(enabled)
    }

    fun setGradeNotificationsEnabled(enabled: Boolean) {
        repository.setGradeNotificationsEnabled(enabled)
    }

    fun setTestNotificationsEnabled(enabled: Boolean) {
        repository.setTestNotificationsEnabled(enabled)
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        repository.setQuietHoursEnabled(enabled)
    }

    fun setQuietHoursStartMinutes(minutes: Int) {
        repository.setQuietHoursStartMinutes(minutes)
    }

    fun setQuietHoursEndMinutes(minutes: Int) {
        repository.setQuietHoursEndMinutes(minutes)
    }

    fun toggleMutedSubject(subjectName: String) {
        repository.toggleMutedSubject(subjectName)
    }

    fun setSmartBusAlertsEnabled(enabled: Boolean) {
        repository.setSmartBusAlertsEnabled(enabled)
    }

    fun setBusWalkMinutes(minutes: Int) {
        repository.setBusWalkMinutes(minutes)
    }

    fun setBusArrivalBufferMinutes(minutes: Int) {
        repository.setBusArrivalBufferMinutes(minutes)
    }

    fun setGradeUnboxingEnabled(enabled: Boolean) {
        repository.setGradeUnboxingEnabled(enabled)
    }

    fun setGradeUnboxingMode(mode: GradeUnboxingMode) {
        repository.setGradeUnboxingMode(mode)
    }

    fun openGradeExportSheet() {
        _showGradeExportSheet.value = true
    }

    fun closeGradeExportSheet() {
        _showGradeExportSheet.value = false
    }

    fun openGradeUnboxing(grade: GradeItem) {
        _unboxingTargetGrade.value = grade
    }

    fun closeGradeUnboxing(markUnboxed: Boolean = true) {
        val target = _unboxingTargetGrade.value
        if (markUnboxed && target != null) {
            repository.markGradeUnboxed(target.id)
        }
        _unboxingTargetGrade.value = null
    }

    fun sendTestUitvalNotification(): Boolean = repository.sendTestUitvalNotification()

    fun openRawConsole() {
        _showRawConsole.value = true
    }

    fun closeRawConsole() {
        _showRawConsole.value = false
    }

    fun getTenantsForProvider(provider: SchoolProvider): List<SchoolTenant> =
        repository.getTenantsForProvider(provider)

    fun resetPkceChallenge() {
        activeVerifier = PkceHelper.generateVerifier()
        activeState = PkceHelper.generateRandomState()
        lastProcessedAuthCode = null
    }

    fun selectTab(tab: AppTab) {
        if (tab != AppTab.MEER && _currentTab.value == AppTab.MEER) {
            _currentMeerSection.value = MeerSection.HOME
        }
        _currentTab.value = tab
    }

    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    fun setRoosterViewMode(mode: Int) {
        _roosterViewMode.value = mode
    }

    fun setTaskFilter(category: TaskCategory?) {
        _taskFilter.value = category
    }

    fun toggleTask(taskId: String) {
        repository.toggleTaskCompletion(taskId)
    }

    fun addTask(
        subject: String,
        title: String,
        description: String,
        dueDate: String,
        category: TaskCategory,
        priority: TaskPriority
    ) {
        val newTask = HomeworkTask(
            id = UUID.randomUUID().toString(),
            subject = subject.ifBlank { "Algemeen" },
            title = title.ifBlank { "Taak" },
            description = description,
            dueDay = "Binnenkort",
            dueDate = dueDate.ifBlank { "Deze week" },
            category = category,
            isCompleted = false,
            priority = priority
        )
        repository.addTask(newTask)
        _showAddTaskDialog.value = false
    }

    fun completeOnboarding(style: AgendaStyle, scale: Float) {
        repository.completeOnboarding(style, scale)
    }

    fun reopenOnboarding() {
        repository.reopenOnboarding()
    }

    fun setAgendaStyle(style: AgendaStyle) {
        repository.setAgendaStyle(style)
    }

    fun setUiScale(scale: Float) {
        repository.setUiScale(scale)
    }

    fun setAppThemeStyle(style: AppThemeStyle) {
        repository.setAppThemeStyle(style)
    }

    fun setDarkModePreference(pref: DarkModePreference) {
        repository.setDarkModePreference(pref)
    }

    fun openInAppBrowser(title: String, url: String) {
        _inAppBrowserTarget.value = InAppBrowserTarget(title = title, url = url)
    }

    fun openEloInApp() {
        _inAppBrowserTarget.value = InAppBrowserTarget(
            title = "ELO • itslearning",
            url = eloUrl.value
        )
    }

    fun closeInAppBrowser() {
        _inAppBrowserTarget.value = null
    }

    fun setEloUrl(url: String) {
        repository.setEloUrl(url)
    }

    fun addStudySite(name: String, url: String, category: String) {
        repository.addStudySite(name, url, category)
    }

    fun removeStudySite(siteId: String) {
        repository.removeStudySite(siteId)
    }

    fun refreshWeather() {
        viewModelScope.launch { repository.refreshWeather() }
    }

    suspend fun searchCities(query: String): List<CitySearchResult> =
        repository.searchCities(query)

    fun selectWeatherCity(city: String, lat: Double, lon: Double) {
        viewModelScope.launch { repository.selectWeatherCity(city, lat, lon) }
    }

    fun openWhatIf(subject: SubjectGradeSummary) {
        _selectedSubjectForWhatIf.value = subject
    }

    fun closeWhatIf() {
        _selectedSubjectForWhatIf.value = null
    }

    fun selectProviderAndOpenLogin(provider: SchoolProvider = SchoolProvider.MAGISTER) {
        _selectedProviderForLogin.value = SchoolProvider.MAGISTER
        resetPkceChallenge()
        _authError.value = null
        val url = repository.getAuthorizationUrl(null, activeVerifier, activeState)
        _oAuthTargetTenant.value = null
        _oAuthUrl.value = url
        _showOAuthWebView.value = true
    }

    fun openLoginDialog() {
        resetPkceChallenge()
        _authError.value = null
        _showLoginDialog.value = true
    }

    fun closeLoginDialog() {
        _showLoginDialog.value = false
        _authError.value = null
    }

    fun openOAuthWebView(tenant: SchoolTenant?) {
        resetPkceChallenge()
        val url = repository.getAuthorizationUrl(tenant, activeVerifier, activeState)
        _oAuthTargetTenant.value = tenant
        _oAuthUrl.value = url
        _showOAuthWebView.value = true
    }

    fun closeOAuthWebView() {
        _showOAuthWebView.value = false
    }

    fun onOAuthCodeCaptured(code: String, idToken: String? = null) {
        val tenant = _oAuthTargetTenant.value
        _showOAuthWebView.value = false
        performSchoolAuth(tenant, code, idToken)
    }

    fun openAddTaskDialog() {
        _showAddTaskDialog.value = true
    }

    fun closeAddTaskDialog() {
        _showAddTaskDialog.value = false
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun performSchoolAuth(tenant: SchoolTenant?, authCodeOrToken: String, idTokenHint: String? = null) {
        val cleanCode = authCodeOrToken.trim()
        if (cleanCode.isBlank()) return
        if (lastProcessedAuthCode == cleanCode) {
            com.example.util.RawConsoleLogger.info("Auth", "Dubbele aanroep voor dezelfde code genegeerd")
            return
        }
        lastProcessedAuthCode = cleanCode

        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            com.example.util.RawConsoleLogger.info("Auth", "Authenticatie starten voor tenant: ${tenant?.subdomain ?: "automatisch"}")

            val result = repository.tryLogin(
                tenant = tenant,
                authCodeOrToken = cleanCode,
                verifier = activeVerifier,
                idTokenHint = idTokenHint
            )

            _isAuthenticating.value = false

            when (result) {
                is AuthResult.Success -> {
                    com.example.util.RawConsoleLogger.info("Auth", "Ingelogd: ${result.studentName}, Tenant: ${result.resolvedTenant?.subdomain}")
                    val fallbackTenant = tenant ?: result.resolvedTenant ?: repository.magisterTenants.first()
                    repository.applySuccessfulAuth(result, fallbackTenant)
                    _showLoginDialog.value = false
                    if (!repository.hasSeenTutorial.value) {
                        _showTutorialDialog.value = true
                    }
                }
                is AuthResult.Error -> {
                    lastProcessedAuthCode = null
                    com.example.util.RawConsoleLogger.error("Auth", "Fout (${result.errorCode}): ${result.errorMessage}\n${result.technicalDetails}")
                    _authError.value = AuthUiError(
                        title = result.errorMessage,
                        message = "Controleer of de code of het token klopt en of je school toegang heeft ingeschakeld.",
                        technicalDetails = result.technicalDetails
                    )
                    _showRawConsole.value = true
                }
            }
        }
    }

    fun resetAppAndLogout() {
        lastProcessedAuthCode = null
        repository.resetAppAndLogout()
        _showLoginDialog.value = false
        _showOAuthWebView.value = false
        _inAppBrowserTarget.value = null
        _showAddTaskDialog.value = false
        _selectedSubjectForWhatIf.value = null
        _currentMeerSection.value = MeerSection.HOME
        _currentTab.value = AppTab.TODAY
    }

    fun refreshAll() {
        viewModelScope.launch { repository.refreshAll() }
    }
}
