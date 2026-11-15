package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GithubReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/EpicMemazGuy/Agenda-plus/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AgendaPlus-Android/1.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name").ifBlank { json.optString("tagName", "") }
            if (tagName.isBlank()) return@runCatching null

            val releaseNotes = json.optString("body", "Geen opmerkingen beschikbaar.")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets") ?: json.optJSONArray("Assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "").lowercase()
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url").ifBlank { asset.optString("url") }
                        break
                    }
                }
            }

            val fallbackUrl = json.optString("html_url").ifBlank { "https://github.com/EpicMemazGuy/Agenda-plus/releases" }
            val downloadUrl = apkUrl ?: fallbackUrl

            val installedVersionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "0.1.2"

            if (isVersionNewer(installedVersionName, tagName)) {
                GithubReleaseInfo(tagName = tagName, releaseNotes = releaseNotes, downloadUrl = downloadUrl)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseVersionComponents(v: String): List<Int> {
        val clean = v.lowercase().removePrefix("v").substringBefore("-")
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    private fun isVersionNewer(installed: String, remote: String): Boolean {
        val instParts = parseVersionComponents(installed)
        val remParts = parseVersionComponents(remote)
        val maxLen = maxOf(instParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val v1 = instParts.getOrElse(i) { 0 }
            val v2 = remParts.getOrElse(i) { 0 }
            if (v2 > v1) return true
            if (v2 < v1) return false
        }
        return false
    }
}
