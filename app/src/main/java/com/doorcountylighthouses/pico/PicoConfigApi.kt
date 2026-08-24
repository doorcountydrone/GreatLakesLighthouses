package com.doorcountylighthouses.pico

import com.doorcountylighthouses.data.BRIGHTNESS_SLIDER_MAX
import com.doorcountylighthouses.data.PicoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class PicoConfigApi {
    sealed class FetchResult {
        data class Success(val config: PicoConfig) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    suspend fun fetch(baseUrl: String): FetchResult = withContext(Dispatchers.IO) {
        var last = "Fetch failed"
        for (url in configUrls(baseUrl, "config")) {
            when (val result = getOnce(url)) {
                is FetchResult.Success -> return@withContext result
                is FetchResult.Error -> last = result.message
            }
        }
        FetchResult.Error(last)
    }

    suspend fun save(baseUrl: String, config: PicoConfig, reboot: Boolean = false): SaveResult =
        withContext(Dispatchers.IO) {
            val body = toJson(config, reboot)
            var last = "Save failed"
            for (path in listOf("update-config", "configure")) {
                for (url in configUrls(baseUrl, path)) {
                    when (val result = postOnce(url, body)) {
                        SaveResult.Success -> return@withContext result
                        is SaveResult.Error -> last = result.message
                    }
                }
            }
            SaveResult.Error(last)
        }

    sealed class UpdateResult {
        data class Success(val message: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    suspend fun startUpdate(baseUrl: String): UpdateResult = withContext(Dispatchers.IO) {
        var last = "Update failed"
        for (url in startUpdateUrls(baseUrl)) {
            when (val result = postStartUpdateOnce(url)) {
                is UpdateResult.Success -> return@withContext result
                is UpdateResult.Error -> {
                    last = result.message
                    if (result.message.contains("No update", ignoreCase = true)) {
                        return@withContext result
                    }
                }
            }
        }
        UpdateResult.Error(last)
    }

    private fun startUpdateUrls(rawBase: String): List<String> {
        val t = rawBase.trim().trimEnd('/')
        val withScheme = when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            else -> "http://$t"
        }
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return listOf("$withScheme/start-update")
        }
        val host = uri.host ?: return listOf("$withScheme/start-update")
        val scheme = uri.scheme ?: "http"
        val urls = linkedSetOf<String>()
        if (uri.port > 0) urls.add("$scheme://$host:${uri.port}/start-update")
        urls.add("$scheme://$host/start-update")
        urls.add("$scheme://$host:8080/start-update")
        return urls.toList()
    }

    private fun postStartUpdateOnce(url: String): UpdateResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Connection", "close")
                setRequestProperty("Content-Type", "text/plain")
                setRequestProperty("Content-Length", "0")
            }
            conn.outputStream.use { /* empty body */ }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when (code) {
                200 -> {
                    if (text.contains("installing", ignoreCase = true) || text.isBlank()) {
                        UpdateResult.Success("Update started. The map will restart.")
                    } else {
                        UpdateResult.Error("Unexpected reply from the map: $text")
                    }
                }
                409 -> UpdateResult.Error(
                    "No update to install. The map is already current, or it cannot reach the internet. Use home Wi-Fi, then Fetch and try again.",
                )
                in 500..599 -> UpdateResult.Error("Map update failed: ${text.ifBlank { "HTTP $code" }}")
                else -> UpdateResult.Error("HTTP $code ${text.take(200)}")
            }
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            val likelyReboot = listOf(
                "reset",
                "broken pipe",
                "end of stream",
                "connection closed",
                "connection reset",
                "unexpected end",
                "software caused connection abort",
            ).any { m.contains(it, ignoreCase = true) }
            if (likelyReboot) {
                UpdateResult.Success("Update may have started (the map reboots and drops the connection). Wait about 30 seconds, then Fetch.")
            } else {
                UpdateResult.Error(e.message ?: e.toString())
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun configUrls(rawBase: String, path: String): List<String> {
        val t = rawBase.trim().trimEnd('/')
        val withScheme = when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            else -> "http://$t"
        }
        val origin = try {
            val u = URI(withScheme)
            val host = u.host ?: return listOf("$withScheme/$path")
            val portPart = if (u.port > 0) ":${u.port}" else ""
            "${u.scheme}://$host$portPart"
        } catch (_: Exception) {
            withScheme
        }.trimEnd('/')
        return listOf("$origin/$path")
    }

    private fun getOnce(url: String): FetchResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (!json.has("led_pin")) {
                return FetchResult.Error("Pico firmware is too old for settings. Copy main.py and wifi_manager.py (0.4.0+) to the Pico.")
            }
            FetchResult.Success(fromJson(json))
        } catch (e: Exception) {
            FetchResult.Error(e.message ?: e.toString())
        } finally {
            conn?.disconnect()
        }
    }

    private fun postOnce(url: String, body: String): SaveResult {
        var conn: HttpURLConnection? = null
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", bytes.size.toString())
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: return SaveResult.Error("Invalid response from Pico")
                val saved = json.optString("message").contains("saved", ignoreCase = true)
                if (json.optBoolean("ok", false) && saved) {
                    SaveResult.Success
                } else {
                    SaveResult.Error(json.optString("message", "Pico firmware is too old. Copy 0.4.0+ files."))
                }
            } else {
                SaveResult.Error("HTTP $code")
            }
        } catch (e: Exception) {
            SaveResult.Error(e.message ?: e.toString())
        } finally {
            conn?.disconnect()
        }
    }

    private fun toJson(config: PicoConfig, reboot: Boolean): String = JSONObject().apply {
        val ssid = config.ssid.trim()
        if (ssid.isNotEmpty()) put("ssid", ssid)
        val password = config.password
        if (password.isNotEmpty()) put("password", password)
        put("led_pin", config.ledPin.coerceIn(0, 28))
        put("min_brightness", config.minBrightness.coerceIn(0, BRIGHTNESS_SLIDER_MAX))
        put("max_brightness", config.maxBrightness.coerceIn(1, BRIGHTNESS_SLIDER_MAX))
        put("brightness", (config.maxBrightness.coerceIn(1, BRIGHTNESS_SLIDER_MAX) / 255.0).coerceIn(0.02, 1.0))
        put("sleep_enabled", config.sleepEnabled)
        put("sleep_at_hour", config.sleepAtHour.coerceIn(0, 23))
        put("sleep_at_minute", config.sleepAtMinute.coerceIn(0, 59))
        put("wake_at_hour", config.wakeAtHour.coerceIn(0, 23))
        put("wake_at_minute", config.wakeAtMinute.coerceIn(0, 59))
        put("timezone_offset_hours", config.timezoneOffsetHours.coerceIn(-12, 14))
        put("weekend_mode_enabled", config.weekendModeEnabled)
        put("weekend_off_weekday", config.weekendOffWeekday.coerceIn(0, 6))
        put("weekend_off_hour", config.weekendOffHour.coerceIn(0, 23))
        put("weekend_off_minute", config.weekendOffMinute.coerceIn(0, 59))
        put("weekend_on_weekday", config.weekendOnWeekday.coerceIn(0, 6))
        put("weekend_on_hour", config.weekendOnHour.coerceIn(0, 23))
        put("weekend_on_minute", config.weekendOnMinute.coerceIn(0, 59))
        put("reboot", reboot)
    }.toString()

    private fun fromJson(json: JSONObject) = PicoConfig(
        ssid = json.optString("ssid").orEmpty(),
        password = "",
        ledPin = json.intLoose("led_pin", 0).coerceIn(0, 28),
        brightness = json.floatLoose("brightness", 0.18f).coerceIn(0.02f, 1f),
        minBrightness = json.intLoose("min_brightness", 2).coerceIn(0, BRIGHTNESS_SLIDER_MAX),
        maxBrightness = run {
            val fromMax = if (json.has("max_brightness")) json.intLoose("max_brightness", 18) else null
            (fromMax ?: (json.floatLoose("brightness", 0.18f) * 255f).toInt()).coerceIn(1, BRIGHTNESS_SLIDER_MAX)
        },
        numLeds = json.intLoose("num_leds", 13),
        cycleDelay = json.intLoose("cycle_delay", 300),
        sleepEnabled = json.optBoolean("sleep_enabled", false),
        sleepAtHour = json.intLoose("sleep_at_hour", 22).coerceIn(0, 23),
        sleepAtMinute = json.intLoose("sleep_at_minute", 0).coerceIn(0, 59),
        wakeAtHour = json.intLoose("wake_at_hour", 6).coerceIn(0, 23),
        wakeAtMinute = json.intLoose("wake_at_minute", 0).coerceIn(0, 59),
        timezoneOffsetHours = json.intLoose("timezone_offset_hours", -5).coerceIn(-12, 14),
        weekendModeEnabled = json.optBoolean("weekend_mode_enabled", false),
        weekendOffWeekday = json.intLoose("weekend_off_weekday", 4).coerceIn(0, 6),
        weekendOffHour = json.intLoose("weekend_off_hour", 18).coerceIn(0, 23),
        weekendOffMinute = json.intLoose("weekend_off_minute", 0).coerceIn(0, 59),
        weekendOnWeekday = json.intLoose("weekend_on_weekday", 0).coerceIn(0, 6),
        weekendOnHour = json.intLoose("weekend_on_hour", 6).coerceIn(0, 23),
        weekendOnMinute = json.intLoose("weekend_on_minute", 0).coerceIn(0, 59),
        firmwareVersion = json.optString("version").ifBlank { null },
        updateAvailable = json.optBoolean("update_available", false),
        updateVersion = json.optString("update_version").ifBlank { null },
    )

    private fun JSONObject.intLoose(key: String, default: Int): Int = when (val v = opt(key)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Float -> v.toInt()
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

    private fun JSONObject.floatLoose(key: String, default: Float): Float = when (val v = opt(key)) {
        is Float -> v
        is Double -> v.toFloat()
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull() ?: default
        else -> default
    }
}
