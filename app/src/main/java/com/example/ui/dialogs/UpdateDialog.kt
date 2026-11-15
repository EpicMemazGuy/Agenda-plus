package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.model.AppThemeStyle
import com.example.util.ApkUpdateDownloader
import com.example.util.GithubReleaseInfo
import com.example.util.UpdateDownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: GithubReleaseInfo,
    appThemeStyle: AppThemeStyle,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val installedVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
    }
    val isNothing = appThemeStyle == AppThemeStyle.NOTHING_OS

    val containerColor = if (isNothing) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = if (isNothing) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.outlineVariant
    val accentColor = if (isNothing) Color(0xFFD71921) else MaterialTheme.colorScheme.primary
    val primaryTextColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isNothing) Color(0xFFB0B0B0) else MaterialTheme.colorScheme.onSurfaceVariant

    var downloadState by remember(updateInfo.tagName) { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var downloadJob by remember(updateInfo.tagName) { mutableStateOf<Job?>(null) }

    // Hervat automatisch installatie zodra gebruiker terugkeert van "Onbekende apps installeren"-instelling
    DisposableEffect(lifecycleOwner, downloadState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val state = downloadState
                if (state is UpdateDownloadState.AwaitingInstallPermission &&
                    ApkUpdateDownloader.canRequestPackageInstalls(context)
                ) {
                    downloadState = UpdateDownloadState.ReadyToInstall(state.apkFile, state.compatibility)
                    if (state.compatibility.isCompatible) {
                        ApkUpdateDownloader.launchSystemInstaller(context, state.apkFile)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startInAppDownload() {
        if (!updateInfo.isApkAsset) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            onDismiss()
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            downloadState = UpdateDownloadState.Downloading(
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = updateInfo.assetSizeBytes
            )
            val result = ApkUpdateDownloader.downloadApk(
                context = context,
                downloadUrl = updateInfo.downloadUrl,
                expectedSizeBytes = updateInfo.assetSizeBytes
            ) { pct, downloaded, total ->
                downloadState = UpdateDownloadState.Downloading(pct, downloaded, total)
            }

            result.onSuccess { apkFile ->
                val compatibility = ApkUpdateDownloader.checkApkCompatibility(context, apkFile)
                if (!ApkUpdateDownloader.canRequestPackageInstalls(context)) {
                    downloadState = UpdateDownloadState.AwaitingInstallPermission(apkFile, compatibility)
                } else {
                    downloadState = UpdateDownloadState.ReadyToInstall(apkFile, compatibility)
                    if (compatibility.isCompatible) {
                        val launched = ApkUpdateDownloader.launchSystemInstaller(context, apkFile)
                        if (!launched) {
                            downloadState = UpdateDownloadState.Error("Kon het Android-installatiescherm niet openen.")
                        }
                    }
                }
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) {
                    downloadState = UpdateDownloadState.Idle
                } else {
                    downloadState = UpdateDownloadState.Error(
                        err.localizedMessage ?: "Downloaden van de APK is mislukt."
                    )
                }
            }
        }
    }

    val dismissEnabled = downloadState !is UpdateDownloadState.Downloading

    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                .testTag("update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update beschikbaar",
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update beschikbaar (${updateInfo.tagName})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "Geïnstalleerd: v$installedVersion • Nieuw: ${updateInfo.tagName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = borderColor)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Wat is er nieuw",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = updateInfo.releaseNotes.ifBlank { "Geen opmerkingen beschikbaar." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryTextColor
                    )
                }

                when (val state = downloadState) {
                    is UpdateDownloadState.Idle -> Unit

                    is UpdateDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        val pct = state.progressPercent
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                color = accentColor,
                                trackColor = borderColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .testTag("update_download_progress")
                            )
                        } else {
                            LinearProgressIndicator(
                                color = accentColor,
                                trackColor = borderColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .testTag("update_download_progress")
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val totalText = if (state.totalBytes > 0L) " / ${formatMb(state.totalBytes)}" else ""
                        val pctText = if (pct != null) " ($pct%)" else ""
                        Text(
                            text = "APK downloaden: ${formatMb(state.downloadedBytes)}$totalText$pctText",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor
                        )
                    }

                    is UpdateDownloadState.AwaitingInstallPermission -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Geef Agenda+ eenmalig toestemming om app-updates te installeren. Zodra je terugkeert start de installer direct.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        val warning = state.compatibility.warningMessage
                        if (warning != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFD71921).copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Waarschuwing",
                                        tint = Color(0xFFD71921),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryTextColor
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Update gedownload en gecontroleerd. Tik op Installeren om bij te werken met behoud van je gegevens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFD71921).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryTextColor,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (val state = downloadState) {
                    is UpdateDownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_later_button")
                            ) {
                                Text("Later", color = secondaryTextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { startInAppDownload() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_download_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download APK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    downloadJob?.cancel()
                                    downloadState = UpdateDownloadState.Idle
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_cancel_download_button")
                            ) {
                                Text("Annuleren", color = secondaryTextColor)
                            }
                        }
                    }

                    is UpdateDownloadState.AwaitingInstallPermission -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Later", color = secondaryTextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(ApkUpdateDownloader.createUnknownSourcesIntent(context))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_permission_button")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Toestemming", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Sluiten", color = secondaryTextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (!ApkUpdateDownloader.launchSystemInstaller(context, state.apkFile)) {
                                        downloadState = UpdateDownloadState.Error("Kon Android-installer niet starten.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_install_button")
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.compatibility.isCompatible) "Installeren" else "Toch installeren",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_browser_fallback_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browser", color = secondaryTextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { startInAppDownload() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("update_retry_button")
                            ) {
                                Text("Opnieuw", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0L) return "0,0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale("nl", "NL"), "%.1f MB", mb)
}
