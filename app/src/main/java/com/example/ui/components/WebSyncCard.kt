package com.example.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AuthSession
import com.example.model.cleanSchoolText
import com.example.services.WebSyncStatus
import com.example.ui.dialogs.QrPairingDialog
import com.example.ui.theme.NothingOutline
import com.example.ui.theme.NothingRed

@Composable
fun WebSyncCard(
    syncState: WebSyncStatus,
    session: AuthSession,
    onPairUri: (String) -> Result<Unit>,
    onSyncNow: () -> Unit,
    onPullLatest: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85

    var showPairDialog by remember { mutableStateOf(false) }

    val resolvedSchool = session.school?.name?.cleanSchoolText() ?: "Alleen Web-sync"

    Surface(
        shape = appCardShape(standard = 20.dp, nothing = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = nothingCardBorder(isAlert = syncState.errorMessage != null),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("web_sync_card")
    ) {
        Column(
            modifier = Modifier.padding(if (compactMode) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        shape = appThumbnailShape(standard = 12.dp, nothing = 12.dp),
                        color = if (syncState.isPaired) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (syncState.isPaired) Icons.Default.CheckCircle else Icons.Default.QrCodeScanner,
                                contentDescription = "Koppel met Agenda+ Web",
                                tint = if (syncState.isPaired) {
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
                            text = if (nothingMode) "KOPPEL MET AGENDA+ WEB" else "Koppel met Agenda+ Web",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (oneUiMode) FontWeight.Medium else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (syncState.isPaired) {
                                "Gekoppeld met Web • AES-256-GCM versleuteld"
                            } else {
                                "Zonder account • End-to-end versleuteld via QR-code"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (syncState.isPaired) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (oneUiMode) {
                        OneUiPillBadge(text = "Gekoppeld")
                    } else {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (nothingMode) NothingRed else MaterialTheme.colorScheme.primaryContainer,
                            border = if (nothingMode) BorderStroke(1.dp, NothingRed) else null
                        ) {
                            Text(
                                text = if (nothingMode) "GEKOPPELD" else "Gekoppeld",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (nothingMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!syncState.isPaired) {
                Button(
                    onClick = { showPairDialog = true },
                    enabled = !syncState.isBusy,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("scan_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scan QR-code van Web",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    shape = appInnerCardShape(standard = 14.dp, nothing = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = nothingCardBorder(),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_account_status_box")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gekoppeld met Web (${syncState.syncId.take(8)}…)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (syncState.lastSyncTimestamp > 0L) {
                                        "Laatste sync: ${DateFormat.format("dd-MM-yyyy HH:mm:ss", syncState.lastSyncTimestamp)}"
                                    } else {
                                        "Laatste sync: Nog niet gesynchroniseerd"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncState.isBusy,
                        shape = RoundedCornerShape(percent = 50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("web_sync_now_button")
                    ) {
                        if (syncState.isBusy) {
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
                                text = "Nu synchroniseren",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onPullLatest,
                        enabled = !syncState.isBusy,
                        shape = RoundedCornerShape(percent = 50),
                        border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else ButtonDefaults.outlinedButtonBorder(enabled = !syncState.isBusy),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("web_pull_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ophalen",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onUnpair,
                    enabled = !syncState.isBusy,
                    shape = RoundedCornerShape(percent = 50),
                    border = if (nothingMode) BorderStroke(1.dp, NothingOutline) else ButtonDefaults.outlinedButtonBorder(enabled = !syncState.isBusy),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("web_unpair_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Koppeling verbreken",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            syncState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("web_sync_error_message")
                )
            }
        }
    }

    if (showPairDialog) {
        QrPairingDialog(
            onPairUri = onPairUri,
            onDismiss = { showPairDialog = false }
        )
    }
}
