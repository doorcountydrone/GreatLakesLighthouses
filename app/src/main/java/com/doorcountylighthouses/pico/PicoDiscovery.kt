package com.doorcountylighthouses.pico

import android.content.Context
import android.net.ConnectivityManager
import com.doorcountylighthouses.data.loadPicoLanUrl
import com.doorcountylighthouses.data.rememberWorkingPicoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

object PicoDiscovery {
    private const val CHART_NAME = "GreatLakesLighthouses"
    private const val AP_ORIGIN = "http://192.168.4.1"

    sealed class Result {
        data class Found(val url: String, val message: String) : Result()
        data class Error(val message: String) : Result()
    }

    private data class Hit(val url: String, val version: String, val setup: Boolean)

    suspend fun find(context: Context, currentUrl: String): Result = withContext(Dispatchers.IO) {
        if (PicoUrls.wifiNetwork(context) == null) {
            return@withContext Result.Error(
                "Join the same Wi-Fi as the chart, then try again. Turn off mobile data if the phone leaves Wi-Fi.",
            )
        }

        val tried = linkedSetOf<String>()
        fun remember(origin: String) {
            PicoUrls.origins(origin).forEach { tried.add(it) }
        }
        remember(currentUrl)
        loadPicoLanUrl(context)?.let { remember(it) }
        remember(AP_ORIGIN)

        for (origin in tried) {
            probe(context, origin)?.let { return@withContext announce(context, it) }
        }

        val self = wifiIpv4(context)
            ?: return@withContext Result.Error(
                "Could not read this phone’s Wi-Fi address. Join the chart’s Wi-Fi and try again.",
            )
        val hit = scanSlash24(context, self, tried)
        if (hit != null) return@withContext announce(context, hit)

        Result.Error(
            "No chart answered on this Wi-Fi. Join GreatLakes-Setup (door1234) and Find again, or type the IP from the OLED.",
        )
    }

    private fun announce(context: Context, hit: Hit): Result.Found {
        val stored = rememberWorkingPicoUrl(context, hit.url)
        val where = if (hit.setup) "GreatLakes-Setup" else "this Wi-Fi"
        val ver = if (hit.version.isNotBlank()) " v${hit.version}" else ""
        return Result.Found(stored, "Found the chart$ver on $where: $stored")
    }

    private fun wifiIpv4(context: Context): Inet4Address? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val wifi = PicoUrls.wifiNetwork(context) ?: return null
        val links = cm.getLinkProperties(wifi) ?: return null
        for (addr in links.linkAddresses) {
            val ip = addr.address
            if (ip is Inet4Address && !ip.isLoopbackAddress && !ip.isLinkLocalAddress) {
                return ip
            }
        }
        return null
    }

    private suspend fun scanSlash24(
        context: Context,
        self: Inet4Address,
        alreadyTried: Set<String>,
    ): Hit? = coroutineScope {
        val selfHost = self.hostAddress ?: return@coroutineScope null
        val raw = self.address
        if (raw.size != 4) return@coroutineScope null
        val skip = alreadyTried.toMutableSet()
        PicoUrls.origins("http://$selfHost").forEach { skip.add(it) }
        val first = AtomicReference<Hit?>(null)
        val gate = Semaphore(32)
        (1..254).map { octet ->
            async(Dispatchers.IO) {
                if (first.get() != null) return@async
                gate.withPermit {
                    if (first.get() != null) return@withPermit
                    val bytes = raw.copyOf()
                    bytes[3] = octet.toByte()
                    val host = InetAddress.getByAddress(bytes).hostAddress ?: return@withPermit
                    for (origin in PicoUrls.origins("http://$host")) {
                        if (origin in skip) continue
                        val hit = probe(context, origin) ?: continue
                        first.compareAndSet(null, hit)
                        return@withPermit
                    }
                }
            }
        }.awaitAll()
        first.get()
    }

    private fun probe(context: Context, origin: String): Hit? {
        var conn: HttpURLConnection? = null
        return try {
            conn = PicoUrls.openConnection(context, "$origin/status", 280, 400).apply {
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            if (json.optString("name") != CHART_NAME) return null
            Hit(
                url = PicoUrls.normalize(origin),
                version = json.optString("version"),
                setup = json.optString("mode") == "setup",
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
