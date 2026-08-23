package com.doorcountylighthouses.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LighthouseColorScheme = darkColorScheme(
    primary = Amber,
    secondary = Lake,
    tertiary = Fog,
    background = Navy,
    surface = CardNavy,
    onPrimary = Navy,
    onSecondary = Cream,
    onBackground = Cream,
    onSurface = Cream,
)

@Composable
fun DoorCountyLighthousesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LighthouseColorScheme,
        typography = Typography,
        content = content
    )
}
