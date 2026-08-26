package com.doorcountylighthouses.data

import android.content.Context
import org.json.JSONObject

data class CatalogEntry(
    val id: String,
    val name: String,
    val shortName: String,
    val lat: Double,
    val lon: Double,
    val region: String,
    val characteristic: String,
    val lightColor: String,
    val periodS: Double,
    val onS: List<Double>,
    val offS: List<Double>,
    val metar: String = "",
    val metarFallback: String = "",
    val metarName: String = "",
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            shortName.lowercase().contains(q) ||
            region.lowercase().contains(q) ||
            characteristic.lowercase().contains(q) ||
            metar.lowercase().contains(q) ||
            metarName.lowercase().contains(q)
    }
}

object CatalogRepository {
    const val SHORE_ALL = "*"

    val REGION_ORDER = listOf(
        "Indiana / Chicago",
        "Wisconsin / Illinois",
        "Green Bay",
        "Michigan",
        "Straits / North",
    )

    fun regionChipLabel(region: String): String = when (region) {
        "Indiana / Chicago" -> "Chicago"
        "Wisconsin / Illinois" -> "Wisconsin"
        "Straits / North" -> "Straits"
        else -> region
    }

    fun load(context: Context): List<CatalogEntry> {
        val json = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val array = root.optJSONArray("lighthouses") ?: return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val light = obj.optJSONObject("light")
                val doubles: (org.json.JSONArray?) -> List<Double> = { arr ->
                    if (arr == null) emptyList()
                    else buildList(arr.length()) {
                        for (n in 0 until arr.length()) add(arr.optDouble(n))
                    }
                }
                add(
                    CatalogEntry(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        shortName = obj.optString("short_name").ifBlank { obj.optString("name") },
                        lat = obj.optDouble("lat"),
                        lon = obj.optDouble("lon"),
                        region = obj.optString("region"),
                        characteristic = light?.optString("char").orEmpty(),
                        lightColor = light?.optString("color", "W") ?: "W",
                        periodS = light?.optDouble("period_s", 1.0) ?: 1.0,
                        onS = doubles(light?.optJSONArray("on_s")).ifEmpty { listOf(1.0) },
                        offS = doubles(light?.optJSONArray("off_s")).ifEmpty { listOf(0.0) },
                        metar = obj.optString("metar"),
                        metarFallback = obj.optString("metar_fallback"),
                        metarName = obj.optString("metar_name"),
                    )
                )
            }
        }
    }

    fun toLighthouse(entry: CatalogEntry): Lighthouse {
        return Lighthouse(
            id = entry.id,
            name = entry.name,
            shortName = entry.shortName,
            led = 0,
            lat = entry.lat,
            lon = entry.lon,
            water = entry.region,
            metar = entry.metar,
            metarFallback = entry.metarFallback,
            metarName = entry.metarName,
            characteristic = entry.characteristic,
            lightColor = entry.lightColor,
            periodS = entry.periodS,
            onS = entry.onS,
            offS = entry.offS,
        )
    }

    fun alreadyOnMap(entry: CatalogEntry, lights: List<Lighthouse>): Boolean {
        return lights.any { existing ->
            existing.id == entry.id ||
                existing.name.equals(entry.name, ignoreCase = true) ||
                (existing.lat != 0.0 &&
                    kotlin.math.abs(existing.lat - entry.lat) < 0.002 &&
                    kotlin.math.abs(existing.lon - entry.lon) < 0.002)
        }
    }
}
