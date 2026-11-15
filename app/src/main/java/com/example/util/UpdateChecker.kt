package com.example.util

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

    internal data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preReleaseRank: Int,
        val preReleaseNumber: Int
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            if (preReleaseRank != other.preReleaseRank) return preReleaseRank.compareTo(other.preReleaseRank)
            return preReleaseNumber.compareTo(other.preReleaseNumber)
        }
    }

    suspend fun checkForUpdate(context: Context): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val latestJson = fetchLatestReleaseJson() ?: return@runCatching null

            val tagName = latestJson.optString("tag_name").ifBlank { latestJson.optString("tagName", "") }
            if (tagName.isBlank()) return@runCatching null

            val releaseNotes = latestJson.optString("body").ifBlank { "Geen opmerkingen beschikbaar." }

            var apkUrl: String? = null
            val assets = latestJson.optJSONArray("assets") ?: latestJson.optJSONArray("Assets")
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

            val fallbackUrl = latestJson.optString("html_url")
                .ifBlank { "https://github.com/EpicMemazGuy/Agenda-plus/releases" }
            val downloadUrl = apkUrl ?: fallbackUrl

            val installedVersionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME

            if (isVersionNewer(installedVersionName, tagName)) {
                GithubReleaseInfo(tagName = tagName, releaseNotes = releaseNotes, downloadUrl = downloadUrl)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchLatestReleaseJson(): JSONObject? {
        val latestReq = Request.Builder()
            .url("https://api.github.com/repos/EpicMemazGuy/Agenda-plus/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AgendaPlus-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(latestReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    return JSONObject(body)
                }
            }
        }

        // Fallback to /releases so pre-releases (like v0.1.2-pre-alpha) are also discovered cleanly
        val listReq = Request.Builder()
            .url("https://api.github.com/repos/EpicMemazGuy/Agenda-plus/releases?per_page=10")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AgendaPlus-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(listReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val arr = JSONArray(body)
            var bestRelease: JSONObject? = null
            var bestVersion: SemanticVersion? = null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("draft", false)) continue
                val tag = obj.optString("tag_name").ifBlank { obj.optString("tagName", "") }
                if (tag.isBlank()) continue
                val parsed = parseSemanticVersion(tag)
                if (bestVersion == null || parsed > bestVersion) {
                    bestVersion = parsed
                    bestRelease = obj
                }
            }
            return bestRelease
        }
    }

    internal fun parseSemanticVersion(raw: String): SemanticVersion {
        val cleaned = raw.trim().lowercase().removePrefix("v").substringBefore("+")
        val corePart = cleaned.substringBefore("-")
        val prePart = if ("-" in cleaned) cleaned.substringAfter("-") else ""

        val nums = corePart.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val major = nums.getOrElse(0) { 0 }
        val minor = nums.getOrElse(1) { 0 }
        val patch = nums.getOrElse(2) { 0 }

        val (rank, preNum) = if (prePart.isBlank()) {
            Int.MAX_VALUE to 0
        } else {
            val baseRank = when {
                "pre-alpha" in prePart || "prealpha" in prePart -> 1
                "alpha" in prePart -> 2
                "beta" in prePart -> 3
                "rc" in prePart -> 4
                else -> 0
            }
            val trailingDigits = Regex("(\\d+)\\s*$").find(prePart)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            baseRank to trailingDigits
        }

        return SemanticVersion(major, minor, patch, rank, preNum)
    }

    internal fun isVersionNewer(installed: String, remote: String): Boolean {
        return parseSemanticVersion(remote) > parseSemanticVersion(installed)
    }
}
