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
import androidx.compose.ui.graphics.RectangleShape
import com.example.model.AgendaStyle
import com.example.model.AppThemeStyle
import com.example.model.AuthSession
import com.example.model.DarkModePreference
import com.example.model.GradeUnboxingMode
import com.example.model.UiScalePreset
import com.example.model.cleanSchoolText
import com.example.services.WebSyncStatus
import com.example.ui.theme.LocalDarkThemeActive
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
    webSyncState: WebSyncStatus = WebSyncStatus(),
    onPairUri: (String) -> Result<Unit> = { Result.success(Unit) },
    onSyncNow: () -> Unit = {},
    onPullLatest: () -> Unit = {},
    onUnpair: () -> Unit = {},
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

    val isDark = LocalDarkThemeActive.current
    val topBarInner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = appScreenHorizontalPadding(), vertical = if (oneUiMode) 8.dp else 10.dp)
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = collapsedTitle ?: "AGENDA+",
                                style = if (collapsedTitle != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (collapsedTitle == null) {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else null
                                ) {
                                    Text(
                                        text = session.provider.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (collapsedTitle == null) {
                            Text(
                                text = session.school?.name?.cleanSchoolText() ?: "Magister",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action Badges & Profile Trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isOffline) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.testTag("offline_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Verversen",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Account & Settings Profile Trigger Pill
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { showAccountSheet = true }
                            .testTag("topbar_account_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = firstName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = firstName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (oneUiMode) {
        Box(modifier = modifier.fillMaxWidth()) {
            topBarInner()
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            topBarInner()
        }
    }

    // Account & Settings Bottom Sheet
    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Account & Instellingen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAccountSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                // Student Identity Card
                Surface(
                    shape = appCardShape(standard = 16.dp, nothing = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = firstName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
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
                                if (!cleanStudentClass.isNullOrBlank()) {
                                    Text(
                                        text = "Klas $cleanStudentClass",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

                WebSyncCard(
                    syncState = webSyncState,
                    session = session,
                    onPairUri = onPairUri,
                    onSyncNow = onSyncNow,
                    onPullLatest = onPullLatest,
                    onUnpair = onUnpair,
                    compactMode = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Logout & Reset action
                OutlinedButton(
                    onClick = {
                        showAccountSheet = false
                        onResetAndLogout()
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("logout_reset_button")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inloggen / Account ontkoppelen")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
