package com.doorcountylighthouses.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LighthouseRepository {
    private const val LOCAL_FILE = "lighthouses.json"

    fun load(context: Context): List<Lighthouse> {
        val local = File(context.filesDir, LOCAL_FILE)
        val json = if (local.exists()) {
            local.readText()
        } else {
            bundledJson(context)
        }
        return parse(json)
    }

    fun loadBundled(context: Context): List<Lighthouse> = parse(bundledJson(context))

    fun saveLocal(context: Context, lights: List<Lighthouse>) {
        File(context.filesDir, LOCAL_FILE).writeText(toJson(renumber(lights)))
    }

    fun bundledJson(context: Context): String =
        context.assets.open("lighthouses.json").bufferedReader().use { it.readText() }

    fun renumber(lights: List<Lighthouse>): List<Lighthouse> =
        lights.mapIndexed { index, light -> light.copy(led = index) }

    fun parse(json: String): List<Lighthouse> {
        val root = JSONObject(json)
        val array = root.optJSONArray("lighthouses") ?: return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(fromJson(array.getJSONObject(i)))
            }
        }.sortedBy { it.led }
    }

    fun toJson(lights: List<Lighthouse>): String {
        val array = JSONArray()
        renumber(lights).forEach { array.put(toJsonObject(it)) }
        return JSONObject()
            .put("version", 3)
            .put("order", "list")
            .put("lighthouses", array)
            .toString()
    }

    fun fromJson(obj: JSONObject): Lighthouse {
        val light = obj.optJSONObject("light")
        val doubles: (JSONArray?) -> List<Double> = { arr ->
            if (arr == null) emptyList()
            else buildList(arr.length()) {
                for (i in 0 until arr.length()) add(arr.optDouble(i))
            }
        }
        val char = light?.optString("char").orEmpty().ifBlank { obj.optString("char") }
        val color = light?.optString("color").orEmpty().ifBlank { obj.optString("light_color", "W") }
        val period = light?.optDouble("period_s", 1.0) ?: 1.0
        val onS = doubles(light?.optJSONArray("on_s")).ifEmpty { listOf(1.0) }
        val offS = doubles(light?.optJSONArray("off_s")).ifEmpty { listOf(0.0) }
        return Lighthouse(
            id = obj.optString("id").ifBlank { "light_${obj.optInt("led")}" },
            name = obj.optString("name"),
            shortName = obj.optString("short_name").ifBlank { obj.optString("name") },
            led = obj.optInt("led"),
            lat = obj.optDouble("lat", 0.0),
            lon = obj.optDouble("lon", 0.0),
            metar = obj.optString("metar"),
            metarFallback = obj.optString("metar_fallback"),
            metarName = obj.optString("metar_name"),
            water = obj.optString("water"),
            active = obj.optBoolean("active", true),
            skip = obj.optBoolean("skip", false),
            characteristic = char,
            lightColor = color.ifBlank { "W" },
            periodS = period,
            onS = onS,
            offS = offS,
        )
    }

    fun toJsonObject(light: Lighthouse): JSONObject {
        val pattern = JSONObject()
            .put("char", light.characteristic)
            .put("color", light.lightColor)
            .put("period_s", light.periodS)
            .put("on_s", JSONArray(light.onS))
            .put("off_s", JSONArray(light.offS))
        return JSONObject()
            .put("id", light.id)
            .put("name", light.name)
            .put("short_name", light.shortName)
            .put("led", light.led)
            .put("lat", light.lat)
            .put("lon", light.lon)
            .put("metar", light.metar)
            .put("metar_fallback", light.metarFallback)
            .put("metar_name", light.metarName)
            .put("water", light.water)
            .put("active", light.active)
            .put("skip", light.skip)
            .put("light", pattern)
    }

    fun newLight(name: String, preset: LightPreset, metar: String = ""): Lighthouse {
        val slug = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "light" }
        return Lighthouse(
            id = "${slug}_${System.currentTimeMillis() % 100000}",
            name = name.trim(),
            shortName = name.trim(),
            led = 0,
            metar = metar.trim().uppercase(),
            metarFallback = metar.trim().uppercase(),
            characteristic = preset.char,
            lightColor = preset.color,
            periodS = preset.periodS,
            onS = preset.onS,
            offS = preset.offS,
        )
    }
}
