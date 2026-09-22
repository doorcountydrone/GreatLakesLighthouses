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
    val characteristicB: String = "",
    val lightColorB: String = "",
    val periodSB: Double = 1.0,
    val onSB: List<Double> = listOf(1.0),
    val offSB: List<Double> = listOf(0.0),
    val metar: String = "",
    val metarFallback: String = "",
    val metarName: String = "",
    val pairKind: String = "",
    val memberIds: List<String> = emptyList(),
) {
    val displayCharacteristic: String
        get() = when {
            characteristicB.isBlank() -> characteristic
            characteristic.isBlank() -> characteristicB
            else -> "$characteristic / $characteristicB"
        }

    val ledPlayHint: String
        get() = describeLedPlay(
            lightColor, periodS, onS, offS,
            lightColorB, periodSB, onSB, offSB, characteristicB,
        )

    fun matches(query: String): Boolean = searchScore(query) > 0

    fun searchScore(query: String): Int = catalogSearchScore(this, query)
}

fun colorWord(code: String): String = when (code.uppercase()) {
    "G" -> "green"
    "R" -> "red"
    else -> "white"
}

fun isFixedPattern(offS: List<Double>, onS: List<Double>): Boolean =
    offS.size == 1 && offS.first() <= 0.0 && onS.size <= 1

fun describeLedPlay(
    color: String,
    periodS: Double,
    onS: List<Double>,
    offS: List<Double>,
    colorB: String = "",
    periodSB: Double = 1.0,
    onSB: List<Double> = emptyList(),
    offSB: List<Double> = emptyList(),
    characteristicB: String = "",
): String {
    val ca = colorWord(color)
    if (characteristicB.isBlank() && colorB.isBlank()) {
        return if (isFixedPattern(offS, onS)) "One LED: steady $ca" else "One LED: flashes $ca"
    }
    val cb = colorWord(colorB.ifBlank { color })
    return if (ca == cb) {
        if (isFixedPattern(offS, onS) && isFixedPattern(offSB, onSB)) "One LED: steady $ca"
        else "One LED: flashes $ca"
    } else {
        "One LED: $ca, then $cb"
    }
}

private val SEARCH_SKIP = setOf(
    "the", "of", "and", "a", "to",
    "light", "lights", "lighthouse", "lighthouses", "lt",
)

private val SEARCH_ALIASES = mapOf(
    "st" to "saint",
    "saint" to "st",
    "pt" to "point",
    "point" to "pt",
    "isl" to "island",
    "island" to "isl",
    "pierhead" to "pier",
    "pier" to "pierhead",
    "ent" to "entrance",
    "entrance" to "ent",
    "rng" to "range",
    "range" to "rng",
    "chan" to "channel",
    "channel" to "chan",
    "harbor" to "harbour",
    "harbour" to "harbor",
    "mackinaw" to "mackinac",
    "mackinac" to "mackinaw",
    "betsey" to "betsie",
    "betsie" to "betsey",
    "joe" to "joseph",
    "joseph" to "joe",
    "shoals" to "shoal",
    "shoal" to "shoals",
    "brk" to "breakwater",
    "breakw" to "breakwater",
)

private fun catalogNormalize(text: String): String =
    buildString(text.length) {
        var gap = false
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit()) {
                append(ch)
                gap = false
            } else if (!gap) {
                append(' ')
                gap = true
            }
        }
    }.trim()

private fun catalogTokens(normalized: String): List<String> =
    normalized.split(' ').filter { it.isNotEmpty() }

private fun catalogExpand(token: String): List<String> {
    val alias = SEARCH_ALIASES[token] ?: return listOf(token)
    return listOf(token, alias)
}

private fun catalogEditDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        for (j in prev.indices) prev[j] = cur[j]
    }
    return prev[b.length]
}

private fun catalogTokenHits(token: String, hayTokens: List<String>, hayNorm: String): Boolean {
    for (variant in catalogExpand(token)) {
        if (variant.length >= 4 && hayNorm.contains(variant)) return true
        for (word in hayTokens) {
            if (word == variant) return true
            if (variant.length >= 2 && word.startsWith(variant)) return true
            if (variant.length >= 4 && word.contains(variant)) return true
            if (variant.length >= 4 && word.length >= 4 && catalogEditDistance(variant, word) <= 1) {
                return true
            }
        }
    }
    return false
}

private fun catalogSearchScore(entry: CatalogEntry, query: String): Int {
    if (query.isBlank()) return 1
    val qNorm = catalogNormalize(query)
    if (qNorm.isEmpty()) return 1
    val colorWord = when (entry.lightColor.uppercase()) {
        "G" -> "green"
        "R" -> "red"
        "W" -> "white"
        else -> ""
    }
    val hayNorm = catalogNormalize(
        listOf(
            entry.name,
            entry.shortName,
            entry.region,
            entry.characteristic,
            entry.characteristicB,
            entry.lightColor,
            entry.lightColorB,
            colorWord,
            entry.metar,
            entry.metarName,
            if (entry.pairKind == "range") "front rear range pair" else "",
            if (entry.pairKind == "channel") "green red pair 1 2" else "",
        ).joinToString(" "),
    )
    val hayTokens = catalogTokens(hayNorm)
    val rawTokens = catalogTokens(qNorm)
    val qTokens = rawTokens.filterNot { it in SEARCH_SKIP }.ifEmpty { rawTokens }
    val phraseHit = hayNorm.contains(qNorm)
    val tokensHit = qTokens.all { catalogTokenHits(it, hayTokens, hayNorm) }
    if (!phraseHit && !tokensHit) return 0

    var score = if (phraseHit) 60 else 30
    val nameNorm = catalogNormalize(entry.name)
    val shortNorm = catalogNormalize(entry.shortName)
    if (nameNorm.startsWith(qNorm) || shortNorm.startsWith(qNorm)) score += 40
    else if (nameNorm.contains(qNorm) || shortNorm.contains(qNorm)) score += 20
    val nameTokens = catalogTokens(nameNorm)
    if (qTokens.isNotEmpty()) {
        var i = 0
        for (word in nameTokens) {
            if (i < qTokens.size && catalogTokenHits(qTokens[i], listOf(word), word)) i++
        }
        if (i == qTokens.size) score += 15
    }
    return score
}

object CatalogRepository {
    const val SHORE_ALL = "*"
    const val KIND_ALL = "*"
    const val KIND_LIGHTS = "lights"
    const val KIND_MARKS = "marks"
    const val KIND_BUOYS = "buoys"
    const val BUOY_NONE = "NONE"
    const val BUOY_GREEN = "G"
    const val BUOY_RED = "R"
    const val BUOY_BOTH = "BOTH"
    const val AID_TOWERS = "TOWERS"
    const val AID_MARKS = "MARKS"
    const val AID_BOTH = "FIXED_BOTH"
    const val AID_NONE = "FIXED_NONE"

    val REGION_ORDER = listOf(
        "Indiana / Chicago",
        "Wisconsin / Illinois",
        "Green Bay",
        "Michigan",
        "Lake Huron",
        "Canada Huron",
        "Georgian Bay",
        "North Channel",
        "Lake Erie",
        "Canada Erie",
        "Lake America",
        "Canada Ontario",
        "Straits / North",
        "Lake Superior",
        "Canada Superior",
    )

    private val numberedAid = Regex("""(?:\blight\s+\d+|\bpier\s+no\.?\s*\d+|\s+\d+[a-z]?$)""", RegexOption.IGNORE_CASE)
    private val utilityWords = listOf(
        "marina", "bulkhead", "abutment", "disposal", "guidewall", "yacht",
        "street", "park", "jetty", "basin", "crib", "breakwater", "shoal",
        "ice boom", "dock", "academy", "club",
    )

    fun isBuoy(name: String, shortName: String = ""): Boolean {
        val n = "$name $shortName".lowercase()
        return n.contains("buoy")
    }

    fun isBuoy(entry: CatalogEntry): Boolean = isBuoy(entry.name, entry.shortName)

    fun isLighthouse(name: String, shortName: String = ""): Boolean {
        if (isBuoy(name, shortName)) return false
        if (numberedAid.containsMatchIn(name.trim()) || numberedAid.containsMatchIn(shortName.trim())) return false
        val n = "$name $shortName".lowercase()
        return utilityWords.none { n.contains(it) }
    }

    fun isLighthouse(entry: CatalogEntry): Boolean = isLighthouse(entry.name, entry.shortName)

    fun isOtherLight(entry: CatalogEntry): Boolean = !isBuoy(entry) && !isLighthouse(entry)

    fun matchesKind(entry: CatalogEntry, kind: String?): Boolean {
        if (kind.isNullOrEmpty() || kind == KIND_ALL) return true
        return when (kind) {
            KIND_BUOYS -> isBuoy(entry)
            KIND_MARKS -> isOtherLight(entry)
            else -> isLighthouse(entry)
        }
    }

    fun matchesChartBuoyFilter(entry: CatalogEntry, filter: String): Boolean {
        if (!isBuoy(entry)) return true
        val colors = setOf(entry.lightColor, entry.lightColorB).map { it.uppercase() }
        return when (filter) {
            BUOY_NONE -> false
            BUOY_RED -> "R" in colors
            BUOY_GREEN -> "G" in colors
            else -> "G" in colors || "R" in colors
        }
    }

    fun matchesChartAidFilter(entry: CatalogEntry, filter: String): Boolean {
        if (isBuoy(entry)) return true
        return when (filter) {
            AID_NONE -> false
            AID_MARKS -> isOtherLight(entry)
            AID_BOTH -> true
            else -> isLighthouse(entry)
        }
    }

    fun regionChipLabel(region: String): String = when (region) {
        "Indiana / Chicago" -> "Chicago"
        "Wisconsin / Illinois" -> "Wisconsin"
        "Straits / North" -> "Straits"
        "Lake Huron" -> "Huron"
        "Canada Huron" -> "Huron ON"
        "Georgian Bay" -> "Georgian"
        "North Channel" -> "Channel"
        "Lake Erie" -> "Erie"
        "Canada Erie" -> "Erie ON"
        "Lake America" -> "America"
        "Canada Ontario" -> "Ontario ON"
        "Lake Superior" -> "Superior"
        "Canada Superior" -> "Superior ON"
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
                val lightB = obj.optJSONObject("light_b")
                val pair = obj.optJSONObject("pair")
                val doubles: (org.json.JSONArray?) -> List<Double> = { arr ->
                    if (arr == null) emptyList()
                    else buildList(arr.length()) {
                        for (n in 0 until arr.length()) add(arr.optDouble(n))
                    }
                }
                val members = pair?.optJSONArray("members")
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
                        characteristicB = lightB?.optString("char").orEmpty(),
                        lightColorB = lightB?.optString("color").orEmpty(),
                        periodSB = lightB?.optDouble("period_s", 1.0) ?: 1.0,
                        onSB = doubles(lightB?.optJSONArray("on_s")).ifEmpty { listOf(1.0) },
                        offSB = doubles(lightB?.optJSONArray("off_s")).ifEmpty { listOf(0.0) },
                        metar = obj.optString("metar"),
                        metarFallback = obj.optString("metar_fallback"),
                        metarName = obj.optString("metar_name"),
                        pairKind = pair?.optString("kind").orEmpty(),
                        memberIds = if (members == null) emptyList() else buildList(members.length()) {
                            for (n in 0 until members.length()) {
                                val id = members.optString(n)
                                if (id.isNotBlank()) add(id)
                            }
                        },
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
            characteristicB = entry.characteristicB,
            lightColorB = entry.lightColorB,
            periodSB = entry.periodSB,
            onSB = entry.onSB,
            offSB = entry.offSB,
        )
    }

    fun alreadyOnMap(entry: CatalogEntry, lights: List<Lighthouse>): Boolean {
        return lights.any { existing ->
            existing.id == entry.id ||
                existing.id in entry.memberIds ||
                existing.name.equals(entry.name, ignoreCase = true) ||
                (existing.lat != 0.0 &&
                    kotlin.math.abs(existing.lat - entry.lat) < 0.002 &&
                    kotlin.math.abs(existing.lon - entry.lon) < 0.002)
        }
    }
}
