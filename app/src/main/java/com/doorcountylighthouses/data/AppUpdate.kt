package com.doorcountylighthouses.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val latestVersion: String,
    val pageUrl: String,
    val apkUrl: String?,
) {
    val downloadUrl: String get() = apkUrl?.ifBlank { null } ?: pageUrl
}

object AppUpdateChecker {
    private const val API =
        "https://api.github.com/repos/doorcountydrone/GreatLakesLighthouses/releases/latest"

    fun check(installed: String): AppUpdate? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GreatLakesLighthouses")
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            val tag = json.optString("tag_name").ifBlank { return null }
            val latest = tag.trim().removePrefix("v").removePrefix("V")
            if (!isNewer(latest, installed)) return null
            val page = json.optString("html_url").ifBlank {
                "https://github.com/doorcountydrone/GreatLakesLighthouses/releases/latest"
            }
            var apk: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            AppUpdate(latestVersion = latest, pageUrl = page, apkUrl = apk)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun isNewer(latest: String, installed: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(installed)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    private fun versionParts(raw: String): List<Int> =
        raw.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
}
