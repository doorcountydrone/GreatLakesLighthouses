package com.doorcountylighthouses.data

data class PicoConfig(
    val ledPin: Int = 0,
    val brightness: Float = 0.18f,
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
)

data class GpioChoice(val pin: Int, val label: String)

val GPIO_CHOICES: List<GpioChoice> = (0..28).map { pin ->
    val note = when (pin) {
        0 -> "default strip"
        15 -> "setup button"
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
