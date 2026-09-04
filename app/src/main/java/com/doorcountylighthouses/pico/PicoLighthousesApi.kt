package com.doorcountylighthouses.pico

import android.content.Context
import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.LighthouseRepository
import com.doorcountylighthouses.data.loadPicoLanUrl
import com.doorcountylighthouses.data.rememberWorkingPicoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection

class PicoLighthousesApi(private val context: Context) {
    sealed class FetchResult {
        data class Success(val lights: List<Lighthouse>, val usedUrl: String) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    sealed class SaveResult {
        data class Success(val usedUrl: String) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    suspend fun fetch(baseUrl: String): FetchResult = withContext(Dispatchers.IO) {
        var last = "Fetch failed"
        for (url in PicoUrls.forPath(baseUrl, "lighthouses", loadPicoLanUrl(context))) {
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
        for (url in PicoUrls.forPath(baseUrl, "lighthouses", loadPicoLanUrl(context))) {
            when (val result = postOnce(url, body)) {
                is SaveResult.Success -> return@withContext result
                is SaveResult.Error -> last = result.message
            }
        }
        SaveResult.Error(last)
    }

    sealed class IdentifyResult {
        data class Success(val led: Int, val ms: Int, val usedUrl: String) : IdentifyResult()
        data class Error(val message: String) : IdentifyResult()
    }

    suspend fun identify(baseUrl: String, led: Int, ms: Int = 8000): IdentifyResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("led", led).put("ms", ms).toString()
            var last = "Identify failed"
            for (url in PicoUrls.forPath(baseUrl, "identify", loadPicoLanUrl(context))) {
                when (val result = postIdentifyOnce(url, body)) {
                    is IdentifyResult.Success -> return@withContext result
                    is IdentifyResult.Error -> last = result.message
                }
            }
            IdentifyResult.Error(last)
        }

    private fun postIdentifyOnce(url: String, body: String): IdentifyResult {
        var conn: HttpURLConnection? = null
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn = PicoUrls.openConnection(context, url, 12000, 12000).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", bytes.size.toString())
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                return IdentifyResult.Error("HTTP $code")
            }
            val root = runCatching { JSONObject(text) }.getOrNull()
            if (root != null && root.optBoolean("ok", true) && root.optBoolean("identifying", true)) {
                IdentifyResult.Success(root.optInt("led"), root.optInt("ms", 8000), rememberWorkingPicoUrl(context, url))
            } else if (text.contains("see") && text.contains("/status")) {
                IdentifyResult.Error("Chart firmware needs 0.6.23+ for identify. Copy main.py or install the update.")
            } else {
                IdentifyResult.Error(root?.optString("message").orEmpty().ifBlank { "Identify failed" })
            }
        } catch (e: Exception) {
            IdentifyResult.Error(PicoUrls.netError(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun getOnce(url: String): FetchResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = PicoUrls.openConnection(context, url, 15000, 15000).apply {
                requestMethod = "GET"
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
            FetchResult.Success(lights, rememberWorkingPicoUrl(context, url))
        } catch (e: Exception) {
            FetchResult.Error(PicoUrls.netError(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun postOnce(url: String, body: String): SaveResult {
        var conn: HttpURLConnection? = null
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn = PicoUrls.openConnection(context, url, 15000, 15000).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", bytes.size.toString())
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                val ok = runCatching { JSONObject(text).optBoolean("ok", true) }.getOrDefault(true)
                if (ok) SaveResult.Success(rememberWorkingPicoUrl(context, url))
                else SaveResult.Error(JSONObject(text).optString("message", "Failed"))
            } else {
                SaveResult.Error("HTTP $code")
            }
        } catch (e: Exception) {
            SaveResult.Error(PicoUrls.netError(e))
        } finally {
            conn?.disconnect()
        }
    }
}
