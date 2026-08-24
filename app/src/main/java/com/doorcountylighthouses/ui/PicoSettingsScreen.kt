package com.doorcountylighthouses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.doorcountylighthouses.data.BRIGHTNESS_SLIDER_MAX
import com.doorcountylighthouses.data.GPIO_CHOICES
import com.doorcountylighthouses.data.ISO_WEEKDAY_NAMES
import com.doorcountylighthouses.data.PicoConfig
import com.doorcountylighthouses.pico.PicoConfigApi
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.Fog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicoSettingsScreen(
    picoBaseUrl: String,
    onPicoBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val api = remember { PicoConfigApi() }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var ledPin by remember { mutableIntStateOf(0) }
    var pinMenuExpanded by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0.18f) }
    var minBrightness by remember { mutableStateOf("2") }
    var maxBrightness by remember { mutableFloatStateOf(18f) }
    var timezoneOffsetHours by remember { mutableStateOf("-5") }
    var sleepEnabled by remember { mutableStateOf(false) }
    var sleepAtHour by remember { mutableStateOf("22") }
    var sleepAtMinute by remember { mutableStateOf("0") }
    var wakeAtHour by remember { mutableStateOf("6") }
    var wakeAtMinute by remember { mutableStateOf("0") }
    var weekendModeEnabled by remember { mutableStateOf(false) }
    var weekendOffWeekday by remember { mutableIntStateOf(4) }
    var weekendOffHour by remember { mutableStateOf("18") }
    var weekendOffMinute by remember { mutableStateOf("0") }
    var weekendOnWeekday by remember { mutableIntStateOf(0) }
    var weekendOnHour by remember { mutableStateOf("6") }
    var weekendOnMinute by remember { mutableStateOf("0") }
    var weekendOffMenuExpanded by remember { mutableStateOf(false) }
    var weekendOnMenuExpanded by remember { mutableStateOf(false) }
    var firmwareVersion by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun applyConfig(cfg: PicoConfig) {
        ssid = cfg.ssid
        password = ""
        ledPin = cfg.ledPin
        brightness = cfg.brightness
        minBrightness = cfg.minBrightness.toString()
        maxBrightness = cfg.maxBrightness.toFloat().coerceIn(2f, BRIGHTNESS_SLIDER_MAX.toFloat())
        timezoneOffsetHours = cfg.timezoneOffsetHours.toString()
        sleepEnabled = cfg.sleepEnabled
        sleepAtHour = cfg.sleepAtHour.toString()
        sleepAtMinute = cfg.sleepAtMinute.toString()
        wakeAtHour = cfg.wakeAtHour.toString()
        wakeAtMinute = cfg.wakeAtMinute.toString()
        weekendModeEnabled = cfg.weekendModeEnabled
        weekendOffWeekday = cfg.weekendOffWeekday
        weekendOffHour = cfg.weekendOffHour.toString()
        weekendOffMinute = cfg.weekendOffMinute.toString()
        weekendOnWeekday = cfg.weekendOnWeekday
        weekendOnHour = cfg.weekendOnHour.toString()
        weekendOnMinute = cfg.weekendOnMinute.toString()
        firmwareVersion = cfg.firmwareVersion
    }

    fun currentConfig() = PicoConfig(
        ssid = ssid,
        password = password,
        ledPin = ledPin,
        brightness = maxBrightness / 255f,
        minBrightness = minBrightness.toIntOrNull()?.coerceIn(0, BRIGHTNESS_SLIDER_MAX) ?: 2,
        maxBrightness = maxBrightness.toInt().coerceIn(1, BRIGHTNESS_SLIDER_MAX),
        sleepEnabled = sleepEnabled,
        sleepAtHour = sleepAtHour.toIntOrNull() ?: 22,
        sleepAtMinute = sleepAtMinute.toIntOrNull() ?: 0,
        wakeAtHour = wakeAtHour.toIntOrNull() ?: 6,
        wakeAtMinute = wakeAtMinute.toIntOrNull() ?: 0,
        timezoneOffsetHours = timezoneOffsetHours.toIntOrNull() ?: -5,
        weekendModeEnabled = weekendModeEnabled,
        weekendOffWeekday = weekendOffWeekday,
        weekendOffHour = weekendOffHour.toIntOrNull() ?: 18,
        weekendOffMinute = weekendOffMinute.toIntOrNull() ?: 0,
        weekendOnWeekday = weekendOnWeekday,
        weekendOnHour = weekendOnHour.toIntOrNull() ?: 6,
        weekendOnMinute = weekendOnMinute.toIntOrNull() ?: 0,
    )

    fun loadFromPico() {
        isLoading = true
        statusMessage = "Fetching Pico settings…"
        scope.launch {
            when (val result = api.fetch(picoBaseUrl)) {
                is PicoConfigApi.FetchResult.Success -> {
                    applyConfig(result.config)
                    statusMessage = "Loaded settings from Pico" +
                        (result.config.firmwareVersion?.let { " (v$it)" } ?: "")
                }
                is PicoConfigApi.FetchResult.Error ->
                    statusMessage = "Fetch failed: ${result.message}"
            }
            isLoading = false
        }
    }

    fun saveToPico(reboot: Boolean) {
        isLoading = true
        statusMessage = if (reboot) "Saving and rebooting…" else "Saving to Pico…"
        scope.launch {
            when (val result = api.save(picoBaseUrl, currentConfig(), reboot)) {
                PicoConfigApi.SaveResult.Success ->
                    statusMessage = if (reboot) "Saved. Pico is rebooting." else "Saved Pico settings"
                is PicoConfigApi.SaveResult.Error ->
                    statusMessage = "Save failed: ${result.message}. Copy firmware 0.4.0+ to the Pico."
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadFromPico()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Pico settings",
            style = MaterialTheme.typography.headlineSmall,
            color = Amber,
        )
        Text(
            text = "Sleep and weekend times use the UTC offset below. NTP must sync after the Pico joins Wi-Fi. Changing the strip pin takes effect after Save, or Save & reboot if the LEDs look stuck.",
            style = MaterialTheme.typography.bodySmall,
            color = Fog,
        )
        firmwareVersion?.let {
            Text(text = "Firmware v$it", style = MaterialTheme.typography.labelSmall, color = Fog)
        }
        OutlinedTextField(
            value = picoBaseUrl,
            onValueChange = onPicoBaseUrlChange,
            label = { Text("Pico address") },
            supportingText = { Text("GreatLakes-Setup: 192.168.4.1  ·  Home Wi-Fi: Pico LAN IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = settingsFieldColors(),
        )
        Text(
            text = "Home Wi-Fi",
            style = MaterialTheme.typography.titleSmall,
            color = Amber,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "This is the router the Pico joins for internet — not the GreatLakes-Setup network. Leave password blank to keep the one already saved on the Pico.",
            style = MaterialTheme.typography.bodySmall,
            color = Fog,
        )
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("SSID") },
            supportingText = { Text("Home or hotel Wi-Fi name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = settingsFieldColors(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            supportingText = { Text("Leave blank to keep the current password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            colors = settingsFieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { loadFromPico() },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) { Text("Fetch") }
            Button(
                onClick = { saveToPico(reboot = false) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text("Save") }
            OutlinedButton(onClick = { saveToPico(reboot = true) }, enabled = !isLoading) {
                Text("Save & reboot")
            }
        }
        statusMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = Amber)
        }

        Text(
            text = "Strip data pin",
            style = MaterialTheme.typography.titleSmall,
            color = Amber,
            modifier = Modifier.padding(top = 8.dp),
        )
        ExposedDropdownMenuBox(
            expanded = pinMenuExpanded,
            onExpandedChange = { pinMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = GPIO_CHOICES.firstOrNull { it.pin == ledPin }?.label ?: "GPIO $ledPin",
                onValueChange = {},
                readOnly = true,
                label = { Text("LED_PIN") },
                supportingText = { Text("GPIO for the WS2812 strip. Default is 0, same as MetarMap.") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pinMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = !isLoading,
                colors = settingsFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = pinMenuExpanded,
                onDismissRequest = { pinMenuExpanded = false },
            ) {
                GPIO_CHOICES.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = {
                            ledPin = choice.pin
                            pinMenuExpanded = false
                        },
                    )
                }
            }
        }

        Text(
            text = "Max brightness: ${maxBrightness.toInt()} (bright-room ceiling)",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = maxBrightness,
            onValueChange = { maxBrightness = it },
            valueRange = 2f..BRIGHTNESS_SLIDER_MAX.toFloat(),
            enabled = !isLoading,
        )
        OutlinedTextField(
            value = minBrightness,
            onValueChange = { s -> if (s.isEmpty() || s.all { it.isDigit() } && s.toIntOrNull() in 0..BRIGHTNESS_SLIDER_MAX) minBrightness = s },
            label = { Text("Min brightness (0-$BRIGHTNESS_SLIDER_MAX)") },
            supportingText = { Text("Use 2 in the dark so WS2812 red/white still look right, same as MetarMap.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
        )

        Text(
            text = "Display sleep schedule",
            style = MaterialTheme.typography.titleSmall,
            color = Amber,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = timezoneOffsetHours,
            onValueChange = { s ->
                if (s.isEmpty() || s == "-" || s == "+" || s.toIntOrNull()?.let { it in -12..14 } == true) {
                    timezoneOffsetHours = s
                }
            },
            label = { Text("Time offset (hours from UTC)") },
            supportingText = { Text("Central: -6 standard, -5 daylight. Eastern: -5 / -4.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = settingsFieldColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Turn LEDs off at set times (after NTP has set the clock)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = sleepEnabled, onCheckedChange = { sleepEnabled = it }, enabled = !isLoading)
        }
        if (sleepEnabled) {
            TimeRow(
                label = "Off at",
                hour = sleepAtHour,
                minute = sleepAtMinute,
                onHour = { sleepAtHour = it },
                onMinute = { sleepAtMinute = it },
                enabled = !isLoading,
            )
            TimeRow(
                label = "On at",
                hour = wakeAtHour,
                minute = wakeAtMinute,
                onHour = { wakeAtHour = it },
                onMinute = { wakeAtMinute = it },
                enabled = !isLoading,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Weekend / long off block", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Extra off period by weekday (e.g. Fri 18:00 → Mon 06:00). Stacks with nightly sleep. Uses the same UTC offset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fog,
                )
            }
            Switch(
                checked = weekendModeEnabled,
                onCheckedChange = { weekendModeEnabled = it },
                enabled = !isLoading,
            )
        }
        if (weekendModeEnabled) {
            WeekdayDropdown(
                label = "Off — weekday",
                selected = weekendOffWeekday,
                expanded = weekendOffMenuExpanded,
                onExpanded = { weekendOffMenuExpanded = it },
                onSelect = { weekendOffWeekday = it },
                enabled = !isLoading,
            )
            TimeRow(
                label = "Off at",
                hour = weekendOffHour,
                minute = weekendOffMinute,
                onHour = { weekendOffHour = it },
                onMinute = { weekendOffMinute = it },
                enabled = !isLoading,
            )
            WeekdayDropdown(
                label = "On — weekday",
                selected = weekendOnWeekday,
                expanded = weekendOnMenuExpanded,
                onExpanded = { weekendOnMenuExpanded = it },
                onSelect = { weekendOnWeekday = it },
                enabled = !isLoading,
            )
            TimeRow(
                label = "On at",
                hour = weekendOnHour,
                minute = weekendOnMinute,
                onHour = { weekendOnHour = it },
                onMinute = { weekendOnMinute = it },
                enabled = !isLoading,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeRow(
    label: String,
    hour: String,
    minute: String,
    onHour: (String) -> Unit,
    onMinute: (String) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
        OutlinedTextField(
            value = hour,
            onValueChange = { s -> if (s.isEmpty() || s.all { it.isDigit() } && s.toIntOrNull() in 0..23) onHour(s) },
            modifier = Modifier.width(64.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
        )
        Text(text = ":", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = minute,
            onValueChange = { s -> if (s.isEmpty() || s.all { it.isDigit() } && s.toIntOrNull() in 0..59) onMinute(s) },
            modifier = Modifier.width(64.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayDropdown(
    label: String,
    selected: Int,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpanded) {
        OutlinedTextField(
            value = ISO_WEEKDAY_NAMES.getOrElse(selected.coerceIn(0, 6)) { "Friday" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = settingsFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            ISO_WEEKDAY_NAMES.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(i)
                        onExpanded(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = Amber,
    unfocusedLabelColor = Fog,
    cursorColor = Amber,
    focusedBorderColor = Amber,
    unfocusedBorderColor = Fog,
)
