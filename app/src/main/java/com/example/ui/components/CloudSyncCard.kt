package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AuthSession
import com.example.model.cleanSchoolText
import com.example.ui.CloudSyncUiState
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.NothingOutline
import com.example.ui.theme.NothingRed

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun CloudSyncCard(
    cloudState: CloudSyncUiState,
    session: AuthSession,
    onGoogleSignIn: (Activity) -> Unit,
    onSyncNow: () -> Unit,
    onRestoreFromCloud: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85
    val isDark = LocalDarkThemeActive.current

    val resolvedSchool = cloudState.schoolName.cleanSchoolText()
        ?: session.school?.name?.cleanSchoolText()
        ?: "Nog geen school gekoppeld"
    val resolvedName = cloudState.displayName.cleanSchoolText()
        ?: session.studentName.cleanSchoolText()
        ?: "Google-account"
    val resolvedEmail = cloudState.email.cleanSchoolText() ?: "E-mailadres onbekend"

    Surface(
        shape = appCardShape(standard = 20.dp, nothing = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = nothingCardBorder(isAlert = cloudState.isError),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_sync_card")
    ) {
        Column(
            modifier = Modifier.padding(if (compactMode) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                        color = if (cloudState.isSignedIn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (cloudState.isSignedIn) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                                contentDescription = "Cloud Sync & Account",
                                tint = if (cloudState.isSignedIn) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (nothingMode) "CLOUD SYNC & ACCOUNT" else "Cloud Sync & Account",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (oneUiMode) FontWeight.Medium else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (cloudState.isSignedIn) {
                                "Gekoppeld met Firebase Firestore"
                            } else {
                                "Bewaar Magister-token & instellingen in de cloud"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (cloudState.isSignedIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (oneUiMode) {
                        OneUiPillBadge(text = "Verbonden")
                    } else {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (nothingMode) NothingRed else MaterialTheme.colorScheme.primaryContainer,
                            border = if (nothingMode) BorderStroke(1.dp, NothingRed) else null
                        ) {
                            Text(
                                text = if (nothingMode) "ACTIEF" else "Verbonden",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (nothingMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!cloudState.isSignedIn) {
                // Google Sign-In button matching Material You, Nothing OS, or One UI 8.5
                val buttonContainer = when {
                    nothingMode -> MaterialTheme.colorScheme.surfaceContainerHighest
                    oneUiMode && isDark -> Color(0xFF1F2229)
                    oneUiMode && !isDark -> Color.White
                    isDark -> Color(0xFF1F242D)
                    else -> Color.White
                }
                val buttonContent = MaterialTheme.colorScheme.onSurface
                val buttonBorder = when {
                    nothingMode -> BorderStroke(1.dp, NothingOutline)
                    oneUiMode -> oneUiInnerHighlightBorder()
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }

                Button(
                    onClick = { activity?.let(onGoogleSignIn) },
                    enabled = !cloudState.isBusy && activity != null,
                    shape = RoundedCornerShape(percent = 50),
                    border = buttonBorder,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (nothingMode || oneUiMode) 0.dp else 1.dp,
                        pressedElevation = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainer,
                        contentColor = buttonContent,
                        disabledContainerColor = buttonContainer.copy(alpha = 0.6f),
                        disabledContentColor = buttonContent.copy(alpha = 0.6f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("google_signin_button")
                ) {
                    if (cloudState.isBusy) {
                        if (nothingMode) {
                            NothingDotMatrixSpinner()
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Verbinden met Google...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google logo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Inloggen met Google (Firebase Cloud Sync)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (oneUiMode) FontWeight.SemiBold else FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Connected Account Details Card (Name, Email, School)
                Surface(
                    shape = appInnerCardShape(standard = 14.dp, nothing = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = nothingCardBorder(),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_account_status_box")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (nothingMode) Color.Black else Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_google_logo),
                                        contentDescription = "Google Account",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resolvedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("cloud_account_name")
                                )
                                Text(
                                    text = resolvedEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("cloud_account_email")
                                )
                            }
                        }

                        NothingAwareDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "School",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = resolvedSchool,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("cloud_account_school")
                            )
                        }
                    }
                }

                // Sync & Restore actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !cloudState.isBusy,
                        shape = RoundedCornerShape(percent = 50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cloud_sync_now_button")
                    ) {
                        if (cloudState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Synchroniseer nu",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreFromCloud,
                        enabled = !cloudState.isBusy,
                        shape = RoundedCornerShape(percent = 50),
                        border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else ButtonDefaults.outlinedButtonBorder(enabled = !cloudState.isBusy),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cloud_restore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Herstel",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !cloudState.isBusy,
                    shape = RoundedCornerShape(percent = 50),
                    border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else ButtonDefaults.outlinedButtonBorder(enabled = !cloudState.isBusy),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("cloud_signout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Google Cloud Sync uitloggen",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            cloudState.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cloudState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("cloud_sync_status_message")
                )
            }
        }
    }
}
