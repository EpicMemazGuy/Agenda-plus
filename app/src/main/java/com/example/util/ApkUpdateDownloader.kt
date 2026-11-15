package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progressPercent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState

    data class AwaitingInstallPermission(
        val apkFile: File,
        val compatibility: ApkCompatibilityCheck
    ) : UpdateDownloadState

    data class ReadyToInstall(
        val apkFile: File,
        val compatibility: ApkCompatibilityCheck
    ) : UpdateDownloadState

    data class Error(val message: String) : UpdateDownloadState
}

data class ApkCompatibilityCheck(
    val isCompatible: Boolean,
    val warningMessage: String? = null
)

object ApkUpdateDownloader {

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedSizeBytes: Long = -1L,
        onProgress: (progressPercent: Int?, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            // Ruim eerdere tijdelijke updatebestanden op
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile) old.delete()
            }

            val partFile = File(updatesDir, "AgendaPlus-update.apk.part")
            val finalFile = File(updatesDir, "AgendaPlus-update.apk")

            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .header("User-Agent", "AgendaPlus-Android/${BuildConfig.VERSION_NAME}")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download mislukt (HTTP ${response.code}).")
                }

                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html")) {
                    error("De downloadlink verwijst naar een webpagina in plaats van een APK-bestand.")
                }

                val body = response.body ?: error("Leeg antwoord ontvangen van GitHub.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes
                var downloaded = 0L
                var lastUiPercent = -1
                var lastEmitTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val percent = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                null
                            }
                            val now = System.currentTimeMillis()
                            val percentChanged = percent != null && percent != lastUiPercent
                            if (percentChanged || now - lastEmitTime >= 150L) {
                                if (percent != null) lastUiPercent = percent
                                lastEmitTime = now
                                onProgress(percent, downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (downloaded < 65_536L) {
                    partFile.delete()
                    error("Het gedownloade bestand is ongeldig of onvolledig.")
                }

                // Controleer ZIP/APK magic bytes ('P', 'K')
                val magic = ByteArray(2)
                partFile.inputStream().use { it.read(magic) }
                if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
                    partFile.delete()
                    error("Het gedownloade bestand is geen geldig Android APK-pakket.")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                finalFile
            }
        }
    }

    fun checkApkCompatibility(context: Context, apkFile: File): ApkCompatibilityCheck {
        return runCatching {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = getPackageArchiveCompat(pm, apkFile.absolutePath, flags)
                ?: return@runCatching ApkCompatibilityCheck(isCompatible = true)

            if (archiveInfo.packageName != context.packageName) {
                return@runCatching ApkCompatibilityCheck(
                    isCompatible = false,
                    warningMessage = "Pakketnaam van de update (${archiveInfo.packageName}) komt niet overeen met ${context.packageName}."
                )
            }

            val installedInfo = getInstalledPackageCompat(pm, context.packageName, flags)
                ?: return@runCatching ApkCompatibilityCheck(isCompatible = true)

            val installedCode = getVersionCodeCompat(installedInfo)
            val archiveCode = getVersionCodeCompat(archiveInfo)
            if (archiveCode in 1 until installedCode) {
                return@runCatching ApkCompatibilityCheck(
                    isCompatible = false,
                    warningMessage = "Deze APK heeft buildnummer $archiveCode, terwijl build $installedCode al is geïnstalleerd (Android staat geen downgrade toe)."
                )
            }

            val installedHashes = extractSigningHashes(installedInfo)
            val archiveHashes = extractSigningHashes(archiveInfo)
            if (installedHashes.isNotEmpty() && archiveHashes.isNotEmpty() && installedHashes.intersect(archiveHashes).isEmpty()) {
                return@runCatching ApkCompatibilityCheck(
                    isCompatible = false,
                    warningMessage = "Je huidige app-versie is nog met een ouder certificaat ondertekend. Maak eerst een keuzes-backup in Instellingen, verwijder de oude app eenmalig en installeer daarna deze nieuwe APK — vanaf nu blijven alle toekomstige updates al je data behouden."
                )
            }

            ApkCompatibilityCheck(isCompatible = true)
        }.getOrDefault(ApkCompatibilityCheck(isCompatible = true))
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        } else {
            true
        }
    }

    fun createUnknownSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun launchSystemInstaller(context: Context, apkFile: File): Boolean {
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveCompat(pm: PackageManager, path: String, flags: Int): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageCompat(pm: PackageManager, pkg: String, flags: Int): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(pkg, flags)
        }
    }

    private fun getVersionCodeCompat(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun extractSigningHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
