package com.doorcountylighthouses.data

const val BRIGHTNESS_SLIDER_MAX = 30
const val CYCLE_DELAY_MIN = 30
const val CYCLE_DELAY_MAX = 3600
const val MATRIX_SCROLL_SPEED_MIN = 1
const val MATRIX_SCROLL_SPEED_MAX = 10
const val MATRIX_SCROLL_SPEED_DEFAULT = 7
const val SCROLL_TIMES_MIN = 1
const val SCROLL_TIMES_MAX = 10
const val SCROLL_TIMES_DEFAULT = 1
const val TOUR_FLASH_S_MIN = 10
const val TOUR_FLASH_S_MAX = 1800
const val TOUR_FLASH_S_DEFAULT = 180
const val TOUR_STEP_S_MIN = 2
const val TOUR_STEP_S_MAX = 120
const val TOUR_STEP_S_DEFAULT = 10

data class PicoConfig(
    val ssid: String = "",
    val password: String = "",
    val ledPin: Int = 0,
    val brightness: Float = 0.18f,
    val minBrightness: Int = 0,
    val maxBrightness: Int = 18,
    val numLeds: Int = 13,
    val cycleDelay: Int = 300,
    val sleepEnabled: Boolean = false,
    val sleepAtHour: Int = 22,
    val sleepAtMinute: Int = 0,
    val wakeAtHour: Int = 6,
    val wakeAtMinute: Int = 0,
    val timezoneOffsetHours: Int = -5,
    val weekendModeEnabled: Boolean = false,
    val weekendOffWeekday: Int = 4,
    val weekendOffHour: Int = 18,
    val weekendOffMinute: Int = 0,
    val weekendOnWeekday: Int = 0,
    val weekendOnHour: Int = 6,
    val weekendOnMinute: Int = 0,
    val firmwareVersion: String? = null,
    val updateAvailable: Boolean = false,
    val updateVersion: String? = null,
    val displayType: String = "NONE",
    val matrixScroll: String = "WEATHER",
    val matrixScrollSpeed: Int = MATRIX_SCROLL_SPEED_DEFAULT,
    val scrollTimes: Int = SCROLL_TIMES_DEFAULT,
    val lightShow: String = "TOUR",
    val tourFlashS: Int = TOUR_FLASH_S_DEFAULT,
    val tourStepS: Int = TOUR_STEP_S_DEFAULT,
    val beaconPulse: Boolean = true,
)

data class GpioChoice(val pin: Int, val label: String)

val GPIO_CHOICES: List<GpioChoice> = (0..28).map { pin ->
    val note = when (pin) {
        0 -> "default strip"
        1 -> "LED matrix"
        15 -> "setup button"
        16 -> "OLED SDA"
        17 -> "OLED SCL"
        18 -> "OLED 3.3V"
        19 -> "OLED GND"
        21 -> "LDR drive"
        23, 24, 25 -> "internal"
        26 -> "LDR"
        else -> null
    }
    GpioChoice(pin, if (note != null) "GPIO $pin — $note" else "GPIO $pin")
}

val ISO_WEEKDAY_NAMES = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

data class DisplayChoice(val id: String, val label: String)

val DISPLAY_CHOICES = listOf(
    DisplayChoice("NONE", "LED strip only"),
    DisplayChoice("OLED", "OLED (128×64)"),
    DisplayChoice("LED_MATRIX", "LED matrix (8×32)"),
)

val MATRIX_SCROLL_CHOICES = listOf(
    DisplayChoice("WEATHER", "Weather only"),
    DisplayChoice("ALL", "All lights"),
)

val LIGHT_SHOW_CHOICES = listOf(
    DisplayChoice("FLASH", "Keep flashing"),
    DisplayChoice("TOUR", "Populate one by one"),
)
