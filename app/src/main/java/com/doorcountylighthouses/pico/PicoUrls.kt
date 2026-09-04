package com.doorcountylighthouses.pico

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object PicoUrls {
    fun normalize(raw: String): String = origins(raw).first()

    fun forPath(raw: String, path: String): List<String> {
        val suffix = "/" + path.trim().trimStart('/')
        return origins(raw).map { it + suffix }
    }

    fun origins(raw: String): List<String> {
        var t = raw.filter { !it.isWhitespace() && it != '\u200B' && it != '\uFEFF' }
            .trim('"', '\'', '“', '”', '<', '>')
        if (t.startsWith("https://", ignoreCase = true)) {
            t = "http://" + t.substring(8)
        }
        if (!t.startsWith("http://", ignoreCase = true)) {
            t = "http://$t"
        }
        t = t.trimEnd('/')
        val hostPort = hostPort(t) ?: return listOf(t)
        val host = hostPort.first
        val typedPort = hostPort.second
        val out = linkedSetOf<String>()
        if (typedPort != null && typedPort != 80) {
            out.add("http://$host:$typedPort")
        }
        out.add("http://$host")
        out.add("http://$host:8080")
        return out.toList()
    }

    fun wifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val networks = try {
            cm.allNetworks
        } catch (_: Exception) {
            emptyArray()
        }
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        val active = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(active) ?: return null
        return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) active else null
    }

    fun openConnection(
        context: Context,
        url: String,
        connectMs: Int,
        readMs: Int,
    ): HttpURLConnection {
        val parsed = URL(url)
        val wifi = wifiNetwork(context)
        val conn = try {
            if (wifi != null) {
                wifi.openConnection(parsed) as HttpURLConnection
            } else {
                parsed.openConnection() as HttpURLConnection
            }
        } catch (_: Exception) {
            parsed.openConnection() as HttpURLConnection
        }
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Connection", "close")
        return conn
    }

    fun netError(error: Throwable): String {
        val m = (error.message ?: error.toString()).ifBlank { "Network error" }
        return when {
            m.contains("Cleartext", ignoreCase = true) ->
                "This app must use http:// (not https) for the chart."
            m.contains("Unable to resolve", ignoreCase = true) ||
                m.contains("UnknownHost", ignoreCase = true) ->
                "That address is not a valid IP. Example: 192.168.1.22"
            m.contains("ECONNREFUSED", ignoreCase = true) ||
                m.contains("Connection refused", ignoreCase = true) ->
                "Nothing answered at that address. Check the IP on the OLED/matrix, and that the phone is on the same Wi-Fi."
            m.contains("ENETUNREACH", ignoreCase = true) ||
                m.contains("No route", ignoreCase = true) ||
                m.contains("ENETUNREACH", ignoreCase = true) ||
                m.contains("Network is unreachable", ignoreCase = true) ->
                "Phone is not on the same Wi-Fi as the chart."
            m.contains("timed out", ignoreCase = true) ||
                m.contains("timeout", ignoreCase = true) ||
                m.contains("failed to connect", ignoreCase = true) ->
                "Timed out. Join the same Wi-Fi as the chart and use its home-network IP, not GreatLakes-Setup, unless you are in setup."
            else -> m
        }
    }

    private fun hostPort(withScheme: String): Pair<String, Int?>? {
        try {
            val u = URI(withScheme)
            val host = u.host
            if (!host.isNullOrBlank()) {
                val port = if (u.port > 0) u.port else null
                return host.trim('[', ']') to port
            }
        } catch (_: Exception) {
        }
        val rest = withScheme.removePrefix("http://").removePrefix("HTTP://")
            .substringBefore('/').substringBefore('?')
        if (rest.isBlank()) return null
        if (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            if (end > 1) {
                val host = rest.substring(1, end)
                val port = rest.substring(end + 1).removePrefix(":").toIntOrNull()
                return host to port
            }
        }
        val host = rest.substringBefore(':')
        val port = if (':' in rest) rest.substringAfter(':').toIntOrNull() else null
        if (host.isBlank()) return null
        return host to port
    }
}
