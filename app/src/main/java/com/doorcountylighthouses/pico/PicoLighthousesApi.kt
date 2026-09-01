package com.doorcountylighthouses.pico

import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.LighthouseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class PicoLighthousesApi {
    sealed class FetchResult {
        data class Success(val lights: List<Lighthouse>) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    suspend fun fetch(baseUrl: String): FetchResult = withContext(Dispatchers.IO) {
        var last = "Fetch failed"
        for (url in lighthouseUrls(baseUrl)) {
            when (val result = getOnce(url)) {
                is FetchResult.Success -> return@withContext result
                is FetchResult.Error -> last = result.message
            }
        }
        FetchResult.Error(last)
    }

    suspend fun save(baseUrl: String, lights: List<Lighthouse>): SaveResult = withContext(Dispatchers.IO) {
        val body = LighthouseRepository.toJson(lights)
        var last = "Save failed"
        for (url in lighthouseUrls(baseUrl)) {
            when (val result = postOnce(url, body)) {
                SaveResult.Success -> return@withContext result
                is SaveResult.Error -> last = result.message
            }
        }
        SaveResult.Error(last)
    }

    private fun lighthouseUrls(rawBase: String): List<String> {
        val t = rawBase.trim().trimEnd('/')
        val withScheme = when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            else -> "http://$t"
        }
        val origin = try {
            val u = URI(withScheme)
            val host = u.host ?: return listOf("$withScheme/lighthouses")
            val portPart = if (u.port > 0) ":${u.port}" else ""
            "${u.scheme}://$host$portPart"
        } catch (_: Exception) {
            withScheme
        }.trimEnd('/')
        return listOf("$origin/lighthouses")
    }

    private fun getOnce(url: String): FetchResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim()
            if (text.isEmpty()) {
                return FetchResult.Error("Empty reply from Pico (HTTP $code). Copy lighthouses.json onto the Pico if Save wiped it.")
            }
            if (text.startsWith("<")) {
                return FetchResult.Error("Pico returned a web page, not the light list. Check the Pico address.")
            }
            val root = JSONObject(text)
            val array = root.optJSONArray("lighthouses")
                ?: return FetchResult.Error("No lighthouses in response")
            val lights = buildList(array.length()) {
                for (i in 0 until array.length()) {
                    add(LighthouseRepository.fromJson(array.getJSONObject(i)))
                }
            }
            FetchResult.Success(lights)
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
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", bytes.size.toString())
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                val ok = runCatching { JSONObject(text).optBoolean("ok", true) }.getOrDefault(true)
                if (ok) SaveResult.Success else SaveResult.Error(JSONObject(text).optString("message", "Failed"))
            } else {
                SaveResult.Error("HTTP $code")
            }
        } catch (e: Exception) {
            SaveResult.Error(e.message ?: e.toString())
        } finally {
            conn?.disconnect()
        }
    }
}
