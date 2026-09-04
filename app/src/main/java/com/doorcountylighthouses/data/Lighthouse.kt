package com.doorcountylighthouses.data

data class Lighthouse(
    val id: String,
    val name: String,
    val shortName: String,
    val led: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val metar: String = "",
    val metarFallback: String = "",
    val metarName: String = "",
    val water: String = "",
    val active: Boolean = true,
    val skip: Boolean = false,
    val characteristic: String,
    val lightColor: String,
    val periodS: Double = 1.0,
    val onS: List<Double> = listOf(1.0),
    val offS: List<Double> = listOf(0.0),
) {
    val hasCoordinates: Boolean
        get() = lat != 0.0 || lon != 0.0

    /** Number on the strip and in the UI. First light is 1. [led] is 0-based on the wire. */
    val displayLed: Int
        get() = led + 1
}

data class LightPreset(
    val char: String,
    val color: String,
    val periodS: Double,
    val onS: List<Double>,
    val offS: List<Double>,
    val label: String,
)

object LightPresets {
    val all = listOf(
        LightPreset("F W", "W", 1.0, listOf(1.0), listOf(0.0), "F W — steady white"),
        LightPreset("F R", "R", 1.0, listOf(1.0), listOf(0.0), "F R — steady red"),
        LightPreset("F G", "G", 1.0, listOf(1.0), listOf(0.0), "F G — steady green"),
        LightPreset("Fl W 4s", "W", 4.0, listOf(0.5), listOf(3.5), "Fl W 4s — white flash 4s"),
        LightPreset("Fl W 6s", "W", 6.0, listOf(1.0), listOf(5.0), "Fl W 6s — white flash 6s"),
        LightPreset("Fl R 2.5s", "R", 2.5, listOf(0.5), listOf(2.0), "Fl R 2.5s — red flash 2.5s"),
        LightPreset("Fl R 6s", "R", 6.0, listOf(1.0), listOf(5.0), "Fl R 6s — red flash 6s"),
        LightPreset("Fl R 10s", "R", 10.0, listOf(1.0), listOf(9.0), "Fl R 10s — red flash 10s"),
        LightPreset("Iso W 6s", "W", 6.0, listOf(3.0), listOf(3.0), "Iso W 6s — white 3s on/off"),
        LightPreset("Iso R 6s", "R", 6.0, listOf(3.0), listOf(3.0), "Iso R 6s — red 3s on/off"),
        LightPreset("Fl(2) W 6s", "W", 6.0, listOf(1.0, 1.0), listOf(1.0, 3.0), "Fl(2) W 6s — two white flashes"),
    )

    fun matching(char: String): LightPreset? =
        all.firstOrNull { it.char.equals(char, ignoreCase = true) }
}
