package com.example.data

import android.content.Context
import com.example.model.*
import com.example.network.AuthResult
import com.example.network.CitySearchResult
import com.example.network.MagisterClient
import com.example.network.TransitClient
import com.example.network.WeatherClient
import com.example.notifications.ScheduleMonitorJobService
import com.example.notifications.UitvalNotificationHelper
import com.example.util.LauncherIconManager
import com.example.util.NetworkStatusHelper
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AgendaRepository(private val context: Context? = null) {

    private val prefs: AppPreferencesStore? = context?.let { AppPreferencesStore(it) }
    private val weatherClient = WeatherClient()
    private val transitClient = TransitClient(prefs)
    private val magisterClient = MagisterClient()

    val magisterTenants = listOf(
        SchoolTenant("1", "St. Ignatius Gymnasium", "Amsterdam", "ignatius", SchoolProvider.MAGISTER),
        SchoolTenant("2", "Barlaeus Gymnasium", "Amsterdam", "barlaeus", SchoolProvider.MAGISTER),
        SchoolTenant("3", "Vossius Gymnasium", "Amsterdam", "vossius", SchoolProvider.MAGISTER),
        SchoolTenant("4", "Stedelijk Gymnasium Leiden", "Leiden", "sgl", SchoolProvider.MAGISTER),
        SchoolTenant("5", "Utrechts Stedelijk Gymnasium", "Utrecht", "usg", SchoolProvider.MAGISTER),
        SchoolTenant("6", "Wolfert van Borselen", "Rotterdam", "wolfert", SchoolProvider.MAGISTER),
        SchoolTenant("7", "Dalton Den Haag", "Den Haag", "daltondenhaag", SchoolProvider.MAGISTER),
        SchoolTenant("8", "Het 4e Gymnasium", "Amsterdam", "het4egymnasium", SchoolProvider.MAGISTER),
        SchoolTenant("9", "Elde College", "Schijndel", "elde", SchoolProvider.MAGISTER)
    )

    private val initialSession: AuthSession = prefs?.loadSession() ?: AuthSession()

    private val _currentSession = MutableStateFlow(initialSession)
    val currentSession: StateFlow<AuthSession> = _currentSession.asStateFlow()

    private val _appointments = MutableStateFlow(prefs?.loadAppointments() ?: emptyList())
    val appointments: StateFlow<List<Appointment>> = _appointments.asStateFlow()

    private val _disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    val disruptions: StateFlow<List<Disruption>> = _disruptions.asStateFlow()

    private val _tasks = MutableStateFlow(prefs?.loadTasks() ?: emptyList())
    val tasks: StateFlow<List<HomeworkTask>> = _tasks.asStateFlow()

    private val _gradeSummaries = MutableStateFlow(prefs?.loadGrades() ?: emptyList())
    val gradeSummaries: StateFlow<List<SubjectGradeSummary>> = _gradeSummaries.asStateFlow()

    private val _messages = MutableStateFlow(prefs?.loadMessages() ?: emptyList())
    val messages: StateFlow<List<MagisterMessage>> = _messages.asStateFlow()

    private val _messageFolders = MutableStateFlow(
        listOf(
            MessageFolderInfo(1L, "Postvak In", 0, MessageFolder.INBOX),
            MessageFolderInfo(2L, "Verzonden", 0, MessageFolder.SENT)
        )
    )
    val messageFolders: StateFlow<List<MessageFolderInfo>> = _messageFolders.asStateFlow()

    private val _studyGuides = MutableStateFlow(prefs?.loadStudyGuides() ?: emptyList())
    val studyGuides: StateFlow<List<StudyGuide>> = _studyGuides.asStateFlow()

    private val _kwtSlots = MutableStateFlow(prefs?.loadKwtSlots() ?: emptyList())
    val kwtSlots: StateFlow<List<KwtSlot>> = _kwtSlots.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs?.loadLastSyncTime() ?: "Nog niet gesynchroniseerd")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(
        (prefs?.hasCompletedOnboarding() == true) || initialSession.isAuthenticated
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _hasSeenTutorial = MutableStateFlow(prefs?.hasSeenTutorial() ?: false)
    val hasSeenTutorial: StateFlow<Boolean> = _hasSeenTutorial.asStateFlow()

    private val _navPromptDismissed = MutableStateFlow(prefs?.isNavPromptDismissed() ?: false)
    val navPromptDismissed: StateFlow<Boolean> = _navPromptDismissed.asStateFlow()

    private val _offlineModeEnabled = MutableStateFlow(prefs?.isOfflineModeEnabled() ?: false)
    val offlineModeEnabled: StateFlow<Boolean> = _offlineModeEnabled.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(NetworkStatusHelper.isOnline(context))
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _todayBlocks = MutableStateFlow(
        prefs?.loadTodayBlocksConfig() ?: AppPreferencesStore.defaultTodayBlocks()
    )
    val todayBlocks: StateFlow<List<TodayBlockConfig>> = _todayBlocks.asStateFlow()

    private val _schoolAddress = MutableStateFlow(prefs?.loadSchoolAddress())
    val schoolAddress: StateFlow<SchoolAddress?> = _schoolAddress.asStateFlow()

    private val _savedBusLine = MutableStateFlow(prefs?.getSavedBusLine() ?: "")
    val savedBusLine: StateFlow<String> = _savedBusLine.asStateFlow()

    private val _busTrackingState = MutableStateFlow(
        BusTrackingState(lineNumber = prefs?.getSavedBusLine() ?: "")
    )
    val busTrackingState: StateFlow<BusTrackingState> = _busTrackingState.asStateFlow()

    private val _agendaStyle = MutableStateFlow(prefs?.getAgendaStyle() ?: AgendaStyle.COMPACT)
    val agendaStyle: StateFlow<AgendaStyle> = _agendaStyle.asStateFlow()

    private val _uiScale = MutableStateFlow(prefs?.getUiScale() ?: 1.0f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    private val _appThemeStyle = MutableStateFlow(prefs?.getAppThemeStyle() ?: AppThemeStyle.STANDARD)
    val appThemeStyle: StateFlow<AppThemeStyle> = _appThemeStyle.asStateFlow()

    private val _darkModePreference = MutableStateFlow(prefs?.getDarkModePreference() ?: DarkModePreference.SYSTEM)
    val darkModePreference: StateFlow<DarkModePreference> = _darkModePreference.asStateFlow()

    private val _eloUrl = MutableStateFlow(prefs?.getEloUrl() ?: AppPreferencesStore.DEFAULT_ELO_URL)
    val eloUrl: StateFlow<String> = _eloUrl.asStateFlow()

    private val _studySites = MutableStateFlow(prefs?.loadStudySites() ?: AppPreferencesStore.defaultStudySites())
    val studySites: StateFlow<List<StudySite>> = _studySites.asStateFlow()

    private val _weatherInfo = MutableStateFlow<WeatherInfo?>(null)
    val weatherInfo: StateFlow<WeatherInfo?> = _weatherInfo.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(false)
    val isWeatherLoading: StateFlow<Boolean> = _isWeatherLoading.asStateFlow()

    private val _uitvalNotificationsEnabled = MutableStateFlow(prefs?.isUitvalNotificationsEnabled() ?: true)
    val uitvalNotificationsEnabled: StateFlow<Boolean> = _uitvalNotificationsEnabled.asStateFlow()

    private val _gradeNotificationsEnabled = MutableStateFlow(prefs?.isGradeNotificationsEnabled() ?: true)
    val gradeNotificationsEnabled: StateFlow<Boolean> = _gradeNotificationsEnabled.asStateFlow()

    private val _testNotificationsEnabled = MutableStateFlow(prefs?.isTestNotificationsEnabled() ?: true)
    val testNotificationsEnabled: StateFlow<Boolean> = _testNotificationsEnabled.asStateFlow()

    private val _gradeUnboxingEnabled = MutableStateFlow(prefs?.isGradeUnboxingEnabled() ?: true)
    val gradeUnboxingEnabled: StateFlow<Boolean> = _gradeUnboxingEnabled.asStateFlow()

    private val _gradeUnboxingMode = MutableStateFlow(prefs?.getGradeUnboxingMode() ?: GradeUnboxingMode.CASE_OPENING)
    val gradeUnboxingMode: StateFlow<GradeUnboxingMode> = _gradeUnboxingMode.asStateFlow()

    private val _unboxedGradeIds = MutableStateFlow(prefs?.getUnboxedGradeIds() ?: emptySet())
    val unboxedGradeIds: StateFlow<Set<String>> = _unboxedGradeIds.asStateFlow()

    init {
        if (initialSession.isAuthenticated && isAnyNotificationEnabled()) {
            context?.let { ScheduleMonitorJobService.schedule(it) }
        }
    }

    fun getTenantsForProvider(provider: SchoolProvider): List<SchoolTenant> = magisterTenants

    fun getAuthorizationUrl(tenant: SchoolTenant?, verifier: String, state: String): String {
        return magisterClient.buildAuthorizationUrl(tenant, verifier, state)
    }

    fun checkConnectivity(): Boolean {
        val online = NetworkStatusHelper.isOnline(context)
        _isDeviceOnline.value = online
        return online
    }

    fun isEffectivelyOffline(): Boolean = _offlineModeEnabled.value || !checkConnectivity()

    suspend fun tryLogin(
        tenant: SchoolTenant?,
        authCodeOrToken: String,
        verifier: String,
        idTokenHint: String? = null
    ): AuthResult {
        val trimmed = authCodeOrToken.trim()
        val isJwtToken = trimmed.startsWith("eyJ") && trimmed.count { it == '.' } == 2
        val isBearerToken = idTokenHint == null && (isJwtToken || (trimmed.length > 150 && !trimmed.contains(" ")))

        val resolvedTenant = tenant ?: SchoolTenant("auto", "Magister", "Nederland", "accounts", SchoolProvider.MAGISTER)
        return if (isBearerToken) {
            magisterClient.verifyExistingBearerToken(resolvedTenant, trimmed)
        } else {
            magisterClient.exchangeAuthorizationCode(tenant, trimmed, verifier, idTokenHint)
        }
    }

    suspend fun applySuccessfulAuth(result: AuthResult.Success, fallbackTenant: SchoolTenant) {
        val finalTenant = result.resolvedTenant ?: fallbackTenant
        val cleanName = result.studentName.cleanSchoolText() ?: "Leerling"
        val cleanId = result.studentId.cleanSchoolText() ?: ""
        val cleanClass = result.studentClass.cleanSchoolText() ?: ""

        val session = AuthSession(
            isAuthenticated = true,
            studentName = cleanName,
            studentId = cleanId,
            studentClass = cleanClass,
            school = finalTenant,
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            personId = result.personId,
            provider = SchoolProvider.MAGISTER
        )
        _currentSession.value = session
        prefs?.saveSession(session)
        prefs?.setOnboardingCompleted(true)
        _hasCompletedOnboarding.value = true

        if (prefs != null && !prefs.hasCustomWeatherLocation()) {
            val schoolCity = finalTenant.city.cleanSchoolText()
            if (schoolCity != null && !schoolCity.equals("Nederland", ignoreCase = true)) {
                val matches = weatherClient.searchCity(schoolCity)
                val first = matches.firstOrNull()
                if (first != null) {
                    prefs.setWeatherLocation(first.name, first.latitude, first.longitude)
                }
            }
        }

        context?.let { ScheduleMonitorJobService.schedule(it) }
        syncLiveSchoolData(finalTenant, result.accessToken, result.personId)
        refreshWeather()
    }

    suspend fun syncLiveSchoolData(tenant: SchoolTenant, token: String, personId: Long) {
        if (isEffectivelyOffline()) {
            _lastSyncTime.value = "Offline-cache actief"
            return
        }

        _isRefreshing.value = true
        try {
            val prevAppointments = _appointments.value
            val bundle = magisterClient.fetchAllMagisterData(tenant, token, personId)

            val cleanedAppointments = bundle.appointments.map { appt ->
                appt.copy(
                    title = appt.title.cleanSchoolText() ?: "Les",
                    subjectName = appt.subjectName.cleanSchoolText() ?: "Les",
                    teacherName = appt.teacherName.cleanSchoolText() ?: "",
                    teacherInitials = appt.teacherInitials.cleanSchoolText() ?: "",
                    classroom = appt.classroom.cleanSchoolText() ?: "",
                    originalClassroom = appt.originalClassroom.cleanSchoolText(),
                    homework = appt.homework.cleanSchoolText()
                )
            }

            _appointments.value = cleanedAppointments
            _disruptions.value = bundle.disruptions
            _gradeSummaries.value = bundle.grades
            _tasks.value = bundle.tasks
            _messages.value = bundle.messages
            if (bundle.messageFolders.isNotEmpty()) {
                _messageFolders.value = bundle.messageFolders
            }
            _studyGuides.value = bundle.studyGuides
            _kwtSlots.value = bundle.kwtSlots

            if (prefs != null && !prefs.hasUnboxedBaseline()) {
                val allGradeIds = bundle.grades.flatMap { it.grades }.map { it.id }.toSet()
                if (allGradeIds.isNotEmpty()) {
                    _unboxedGradeIds.value = allGradeIds
                    prefs.setUnboxedGradeIds(allGradeIds)
                }
            }

            val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val syncLabel = "Magister gesynchroniseerd ($timeStr)"
            _lastSyncTime.value = syncLabel

            prefs?.saveAppointments(cleanedAppointments)
            prefs?.saveGrades(bundle.grades)
            prefs?.saveTasks(bundle.tasks)
            prefs?.saveMessages(bundle.messages)
            prefs?.saveStudyGuides(bundle.studyGuides)
            prefs?.saveKwtSlots(bundle.kwtSlots)
            prefs?.saveLastSyncTime(syncLabel)

            context?.let { ctx ->
                UitvalNotificationHelper.evaluateAllNotifications(
                    context = ctx,
                    previousAppointments = prevAppointments,
                    newAppointments = cleanedAppointments,
                    newGrades = bundle.grades,
                    newTasks = bundle.tasks
                )
                WidgetUpdater.updateAllWidgets(ctx)
            }
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.warn("Sync", "Sync probleem: ${e.message}")
            _lastSyncTime.value = if (_appointments.value.isNotEmpty()) {
                "Offline-cache (${prefs?.loadLastSyncTime() ?: "lokaal"})"
            } else {
                "Sync mislukt: ${e.message}"
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        _offlineModeEnabled.value = enabled
        prefs?.setOfflineModeEnabled(enabled)
        if (enabled) {
            _lastSyncTime.value = "Offline-modus ingeschakeld"
        } else {
            _lastSyncTime.value = prefs?.loadLastSyncTime() ?: "Online"
        }
    }

    fun markTutorialSeen() {
        _hasSeenTutorial.value = true
        prefs?.setTutorialSeen(true)
    }

    fun dismissNavPrompt() {
        _navPromptDismissed.value = true
        prefs?.setNavPromptDismissed(true)
    }

    fun toggleTodayBlock(type: TodayBlockType, enabled: Boolean) {
        val updated = _todayBlocks.value.map {
            if (it.type == type) it.copy(enabled = enabled) else it
        }
        _todayBlocks.value = updated
        prefs?.saveTodayBlocksConfig(updated)
    }

    fun moveTodayBlock(type: TodayBlockType, delta: Int) {
        val list = _todayBlocks.value.toMutableList()
        val index = list.indexOfFirst { it.type == type }
        val target = index + delta
        if (index >= 0 && target in list.indices) {
            val item = list.removeAt(index)
            list.add(target, item)
            _todayBlocks.value = list
            prefs?.saveTodayBlocksConfig(list)
        }
    }

    suspend fun searchSchoolAddresses(query: String): List<SchoolAddress> {
        if (isEffectivelyOffline()) return emptyList()
        return transitClient.searchSchoolAddress(query)
    }

    fun saveSchoolAddress(address: SchoolAddress) {
        _schoolAddress.value = address
        prefs?.saveSchoolAddress(address)
        // Automatically enable SCHOOL_NAV block when user saves a school address
        toggleTodayBlock(TodayBlockType.SCHOOL_NAV, true)
    }

    suspend fun setBusLineAndTrack(lineNumber: String, userLat: Double?, userLon: Double?) {
        val clean = lineNumber.trim()
        _savedBusLine.value = clean
        prefs?.setSavedBusLine(clean)
        if (clean.isNotBlank()) {
            toggleTodayBlock(TodayBlockType.BUS_TIME, true)
        }
        refreshBusTracking(userLat, userLon)
    }

    suspend fun refreshBusTracking(userLat: Double?, userLon: Double?) {
        val line = _savedBusLine.value.trim()
        if (line.isBlank()) {
            _busTrackingState.value = BusTrackingState()
            return
        }
        if (isEffectivelyOffline()) {
            _busTrackingState.value = _busTrackingState.value.copy(
                lineNumber = line,
                isLoading = false,
                errorMessage = "Offline-modus actief: live busgegevens tijdelijk niet beschikbaar."
            )
            return
        }
        val schoolAddr = _schoolAddress.value ?: prefs?.loadSchoolAddress()
        val cityHint = listOfNotNull(
            _currentSession.value.school?.city,
            _currentSession.value.school?.name,
            schoolAddr?.streetAndCity,
            schoolAddr?.displayName,
            prefs?.getWeatherLocation()?.cityName
        ).joinToString(" ")
        val updated = transitClient.fetchLiveBusTracking(line, cityHint, userLat, userLon)
        _busTrackingState.value = updated
    }

    suspend fun loadMessageDetail(messageId: String): String? {
        if (isEffectivelyOffline()) return null
        val session = _currentSession.value
        val tenant = session.school ?: return null
        val token = session.accessToken ?: return null
        return magisterClient.fetchMessageDetail(tenant, token, messageId)?.also { fullBody ->
            val updated = _messages.value.map { msg ->
                if (msg.id == messageId) msg.copy(body = fullBody) else msg
            }
            _messages.value = updated
            prefs?.saveMessages(updated)
        }
    }

    suspend fun markMessageRead(messageId: String) {
        val updated = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(isRead = true) else msg
        }
        _messages.value = updated
        prefs?.saveMessages(updated)

        if (!isEffectivelyOffline()) {
            val session = _currentSession.value
            val tenant = session.school
            val token = session.accessToken
            if (tenant != null && !token.isNullOrBlank()) {
                magisterClient.markMessageAsRead(tenant, token, messageId)
            }
        }
    }

    suspend fun enrollKwtOption(appointmentId: Long, optionId: Long): Result<Unit> {
        if (isEffectivelyOffline()) {
            return Result.failure(IllegalStateException("KWT kiezen vereist een actieve internetverbinding."))
        }
        val session = _currentSession.value
        val tenant = session.school ?: return Result.failure(IllegalStateException("Niet ingelogd."))
        val token = session.accessToken ?: return Result.failure(IllegalStateException("Geen geldige sessie."))
        val res = magisterClient.enrollKwtOption(tenant, token, session.personId, appointmentId, optionId)
        if (res.isSuccess) {
            val updatedSlots = _kwtSlots.value.map { slot ->
                if (slot.appointmentId == appointmentId) {
                    slot.copy(options = slot.options.map { opt ->
                        opt.copy(isEnrolled = opt.id == optionId)
                    })
                } else slot
            }
            _kwtSlots.value = updatedSlots
            prefs?.saveKwtSlots(updatedSlots)
            refreshAll()
        }
        return res
    }

    fun completeOnboarding(style: AgendaStyle, scale: Float) {
        _agendaStyle.value = style
        _uiScale.value = scale.coerceIn(0.85f, 1.20f)
        _hasCompletedOnboarding.value = true
        prefs?.setAgendaStyle(style)
        prefs?.setUiScale(_uiScale.value)
        prefs?.setOnboardingCompleted(true)
    }

    fun reopenOnboarding() {
        _hasCompletedOnboarding.value = false
        prefs?.setOnboardingCompleted(false)
    }

    fun setAgendaStyle(style: AgendaStyle) {
        _agendaStyle.value = style
        prefs?.setAgendaStyle(style)
    }

    fun setUiScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.20f)
        _uiScale.value = clamped
        prefs?.setUiScale(clamped)
    }

    fun setAppThemeStyle(style: AppThemeStyle) {
        _appThemeStyle.value = style
        prefs?.setAppThemeStyle(style)
        context?.let { ctx ->
            LauncherIconManager.applyThemeIcon(ctx, style)
            WidgetUpdater.updateAllWidgets(ctx)
        }
    }

    fun setDarkModePreference(pref: DarkModePreference) {
        _darkModePreference.value = pref
        prefs?.setDarkModePreference(pref)
        context?.let { WidgetUpdater.updateAllWidgets(it) }
    }

    fun setEloUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        _eloUrl.value = normalized
        prefs?.setEloUrl(normalized)
    }

    fun addStudySite(name: String, url: String, category: String) {
        val cleanName = name.trim().ifBlank { "Studiewebsite" }
        val cleanCat = category.trim().ifBlank { "Studie" }
        val rawUrl = url.trim()
        if (rawUrl.isBlank()) return
        val normalizedUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        val updated = listOf(
            StudySite(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                url = normalizedUrl,
                category = cleanCat,
                isDefault = false
            )
        ) + _studySites.value
        _studySites.value = updated
        prefs?.saveStudySites(updated)
    }

    fun removeStudySite(siteId: String) {
        val updated = _studySites.value.filterNot { it.id == siteId }
        _studySites.value = updated
        prefs?.saveStudySites(updated)
    }

    suspend fun refreshWeather() {
        if (_isWeatherLoading.value || isEffectivelyOffline()) return
        _isWeatherLoading.value = true
        try {
            val loc = prefs?.getWeatherLocation() ?: WeatherLocationPref("Utrecht", 52.0907, 5.1214)
            val info = weatherClient.fetchCurrentWeather(loc.cityName, loc.latitude, loc.longitude)
            if (info != null) {
                _weatherInfo.value = info
            }
        } finally {
            _isWeatherLoading.value = false
        }
    }

    suspend fun searchCities(query: String): List<CitySearchResult> {
        if (isEffectivelyOffline()) return emptyList()
        return weatherClient.searchCity(query)
    }

    suspend fun selectWeatherCity(city: String, lat: Double, lon: Double) {
        prefs?.setWeatherLocation(city, lat, lon)
        if (isEffectivelyOffline()) return
        _isWeatherLoading.value = true
        try {
            val info = weatherClient.fetchCurrentWeather(city, lat, lon)
            if (info != null) {
                _weatherInfo.value = info
            }
        } finally {
            _isWeatherLoading.value = false
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        val updated = _tasks.value.map { task ->
            if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
        }
        _tasks.value = updated
        prefs?.saveTasks(updated)
        context?.let { WidgetUpdater.updateAllWidgets(it) }
    }

    fun addTask(task: HomeworkTask) {
        val updated = listOf(task) + _tasks.value
        _tasks.value = updated
        prefs?.saveTasks(updated)
        context?.let { WidgetUpdater.updateAllWidgets(it) }
    }

    fun isAnyNotificationEnabled(): Boolean =
        _uitvalNotificationsEnabled.value || _gradeNotificationsEnabled.value || _testNotificationsEnabled.value

    private fun updateMonitorSchedule() {
        context?.let { ctx ->
            if (isAnyNotificationEnabled() && _currentSession.value.isAuthenticated) {
                ScheduleMonitorJobService.schedule(ctx)
            } else {
                ScheduleMonitorJobService.cancel(ctx)
            }
        }
    }

    fun setUitvalNotificationsEnabled(enabled: Boolean) {
        _uitvalNotificationsEnabled.value = enabled
        prefs?.setUitvalNotificationsEnabled(enabled)
        updateMonitorSchedule()
    }

    fun setGradeNotificationsEnabled(enabled: Boolean) {
        _gradeNotificationsEnabled.value = enabled
        prefs?.setGradeNotificationsEnabled(enabled)
        updateMonitorSchedule()
    }

    fun setTestNotificationsEnabled(enabled: Boolean) {
        _testNotificationsEnabled.value = enabled
        prefs?.setTestNotificationsEnabled(enabled)
        updateMonitorSchedule()
    }

    fun setGradeUnboxingEnabled(enabled: Boolean) {
        _gradeUnboxingEnabled.value = enabled
        prefs?.setGradeUnboxingEnabled(enabled)
    }

    fun setGradeUnboxingMode(mode: GradeUnboxingMode) {
        _gradeUnboxingMode.value = mode
        prefs?.setGradeUnboxingMode(mode)
    }

    fun markGradeUnboxed(gradeId: String) {
        if (gradeId in _unboxedGradeIds.value) return
        val updated = _unboxedGradeIds.value + gradeId
        _unboxedGradeIds.value = updated
        prefs?.setUnboxedGradeIds(updated)
    }

    fun sendTestUitvalNotification(): Boolean {
        val ctx = context ?: return false
        return UitvalNotificationHelper.sendTestUitvalNotification(ctx)
    }

    fun exportChoicesBackupJson(): String = prefs?.exportChoicesBackupJson() ?: "{}"

    fun importChoicesBackupJson(rawJson: String): Result<Int> {
        val store = prefs ?: return Result.failure(IllegalStateException("Geen opslag beschikbaar."))
        val result = store.importChoicesBackupJson(rawJson)
        if (result.isSuccess) {
            _appThemeStyle.value = store.getAppThemeStyle()
            _darkModePreference.value = store.getDarkModePreference()
            _agendaStyle.value = store.getAgendaStyle()
            _uiScale.value = store.getUiScale()
            _eloUrl.value = store.getEloUrl()
            _savedBusLine.value = store.getSavedBusLine()
            _navPromptDismissed.value = store.isNavPromptDismissed()
            _hasSeenTutorial.value = store.hasSeenTutorial()
            _offlineModeEnabled.value = store.isOfflineModeEnabled()
            _uitvalNotificationsEnabled.value = store.isUitvalNotificationsEnabled()
            _gradeNotificationsEnabled.value = store.isGradeNotificationsEnabled()
            _testNotificationsEnabled.value = store.isTestNotificationsEnabled()
            _gradeUnboxingEnabled.value = store.isGradeUnboxingEnabled()
            _gradeUnboxingMode.value = store.getGradeUnboxingMode()
            _schoolAddress.value = store.loadSchoolAddress()
            _todayBlocks.value = store.loadTodayBlocksConfig()
            _studySites.value = store.loadStudySites()
            updateMonitorSchedule()
            context?.let { WidgetUpdater.updateAllWidgets(it) }
        }
        return result
    }

    fun resetAppAndLogout() {
        _currentSession.value = AuthSession()
        _appointments.value = emptyList()
        _disruptions.value = emptyList()
        _tasks.value = emptyList()
        _gradeSummaries.value = emptyList()
        _messages.value = emptyList()
        _studyGuides.value = emptyList()
        _kwtSlots.value = emptyList()
        _unboxedGradeIds.value = emptySet()
        _lastSyncTime.value = "Uitgelogd"
        prefs?.clearSession()
        context?.let { ctx ->
            ScheduleMonitorJobService.cancel(ctx)
            WidgetUpdater.updateAllWidgets(ctx)
        }
    }

    suspend fun refreshAll() {
        if (isEffectivelyOffline()) {
            _lastSyncTime.value = "Offline-cache actief"
            return
        }
        val session = _currentSession.value
        val tenant = session.school
        var token = session.accessToken
        if (session.isAuthenticated && tenant != null && !token.isNullOrBlank()) {
            if (magisterClient.isAccessTokenExpired(token)) {
                val refreshTok = session.refreshToken
                if (!refreshTok.isNullOrBlank()) {
                    val refreshed = magisterClient.refreshAccessToken(refreshTok)
                    if (refreshed != null) {
                        token = refreshed.first
                        val updatedSession = session.copy(
                            accessToken = refreshed.first,
                            refreshToken = refreshed.second ?: refreshTok
                        )
                        _currentSession.value = updatedSession
                        prefs?.saveSession(updatedSession)
                    }
                }
            }
            syncLiveSchoolData(tenant, token, session.personId)
        } else {
            _lastSyncTime.value = "Niet verbonden"
        }
    }
}
