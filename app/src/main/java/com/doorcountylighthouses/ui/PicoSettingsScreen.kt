package com.doorcountylighthouses.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.doorcountylighthouses.data.BRIGHTNESS_SLIDER_MAX
import com.doorcountylighthouses.data.CYCLE_DELAY_MAX
import com.doorcountylighthouses.data.CYCLE_DELAY_MIN
import com.doorcountylighthouses.data.DISPLAY_CHOICES
import com.doorcountylighthouses.data.GPIO_CHOICES
import com.doorcountylighthouses.data.ISO_WEEKDAY_NAMES
import com.doorcountylighthouses.data.LIGHT_SHOW_CHOICES
import com.doorcountylighthouses.data.MATRIX_SCROLL_CHOICES
import com.doorcountylighthouses.data.MATRIX_SCROLL_SPEED_DEFAULT
import com.doorcountylighthouses.data.MATRIX_SCROLL_SPEED_MAX
import com.doorcountylighthouses.data.MATRIX_SCROLL_SPEED_MIN
import com.doorcountylighthouses.data.PicoConfig
import com.doorcountylighthouses.data.SCROLL_TIMES_DEFAULT
import com.doorcountylighthouses.data.SCROLL_TIMES_MAX
import com.doorcountylighthouses.data.SCROLL_TIMES_MIN
import com.doorcountylighthouses.data.TOUR_FLASH_S_DEFAULT
import com.doorcountylighthouses.data.TOUR_FLASH_S_MAX
import com.doorcountylighthouses.data.TOUR_FLASH_S_MIN
import com.doorcountylighthouses.data.TOUR_STEP_S_DEFAULT
import com.doorcountylighthouses.data.TOUR_STEP_S_MAX
import com.doorcountylighthouses.data.TOUR_STEP_S_MIN
import com.doorcountylighthouses.pico.PicoConfigApi
import com.doorcountylighthouses.pico.PicoDiscovery
import com.doorcountylighthouses.pico.PicoUrls
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
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val api = remember { PicoConfigApi(context) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var ledPin by remember { mutableIntStateOf(0) }
    var pinMenuExpanded by remember { mutableStateOf(false) }
    var displayType by remember { mutableStateOf("NONE") }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    var lightShow by remember { mutableStateOf("TOUR") }
    var lightShowMenuExpanded by remember { mutableStateOf(false) }
    var tourFlashS by remember { mutableStateOf(TOUR_FLASH_S_DEFAULT.toString()) }
    var tourStepS by remember { mutableStateOf(TOUR_STEP_S_DEFAULT.toString()) }
    var matrixScroll by remember { mutableStateOf("WEATHER") }
    var matrixScrollMenuExpanded by remember { mutableStateOf(false) }
    var matrixScrollSpeed by remember { mutableFloatStateOf(MATRIX_SCROLL_SPEED_DEFAULT.toFloat()) }
    var scrollTimes by remember { mutableFloatStateOf(SCROLL_TIMES_DEFAULT.toFloat()) }
    var brightness by remember { mutableFloatStateOf(0.18f) }
    var minBrightness by remember { mutableStateOf("2") }
    var maxBrightness by remember { mutableFloatStateOf(18f) }
    var cycleDelay by remember { mutableStateOf("300") }
    var beaconPulse by remember { mutableStateOf(true) }
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
    var updateAvailable by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var wifiOpen by remember { mutableStateOf(true) }
    var brightnessOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var updateOpen by remember { mutableStateOf(true) }

    fun applyConfig(cfg: PicoConfig) {
        ssid = cfg.ssid
        password = ""
        ledPin = cfg.ledPin
        displayType = cfg.displayType
        lightShow = cfg.lightShow
        tourFlashS = cfg.tourFlashS.coerceIn(TOUR_FLASH_S_MIN, TOUR_FLASH_S_MAX).toString()
        tourStepS = cfg.tourStepS.coerceIn(TOUR_STEP_S_MIN, TOUR_STEP_S_MAX).toString()
        matrixScroll = cfg.matrixScroll
        matrixScrollSpeed = cfg.matrixScrollSpeed.toFloat().coerceIn(
            MATRIX_SCROLL_SPEED_MIN.toFloat(),
            MATRIX_SCROLL_SPEED_MAX.toFloat(),
        )
        scrollTimes = cfg.scrollTimes.toFloat().coerceIn(
            SCROLL_TIMES_MIN.toFloat(),
            SCROLL_TIMES_MAX.toFloat(),
        )
        brightness = cfg.brightness
        minBrightness = cfg.minBrightness.toString()
        maxBrightness = cfg.maxBrightness.toFloat().coerceIn(2f, BRIGHTNESS_SLIDER_MAX.toFloat())
        cycleDelay = cfg.cycleDelay.coerceIn(CYCLE_DELAY_MIN, CYCLE_DELAY_MAX).toString()
        beaconPulse = cfg.beaconPulse
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
        updateAvailable = cfg.updateAvailable
        updateVersion = cfg.updateVersion
    }

    fun currentConfig() = PicoConfig(
        ssid = ssid,
        password = password,
        ledPin = ledPin,
        displayType = displayType,
        lightShow = lightShow,
        tourFlashS = tourFlashS.toIntOrNull()?.coerceIn(TOUR_FLASH_S_MIN, TOUR_FLASH_S_MAX) ?: TOUR_FLASH_S_DEFAULT,
        tourStepS = tourStepS.toIntOrNull()?.coerceIn(TOUR_STEP_S_MIN, TOUR_STEP_S_MAX) ?: TOUR_STEP_S_DEFAULT,
        matrixScroll = matrixScroll,
        matrixScrollSpeed = matrixScrollSpeed.toInt().coerceIn(MATRIX_SCROLL_SPEED_MIN, MATRIX_SCROLL_SPEED_MAX),
        scrollTimes = scrollTimes.toInt().coerceIn(SCROLL_TIMES_MIN, SCROLL_TIMES_MAX),
        brightness = maxBrightness / 255f,
        minBrightness = minBrightness.toIntOrNull()?.coerceIn(0, BRIGHTNESS_SLIDER_MAX) ?: 0,
        maxBrightness = maxBrightness.toInt().coerceIn(1, BRIGHTNESS_SLIDER_MAX),
        cycleDelay = cycleDelay.toIntOrNull()?.coerceIn(CYCLE_DELAY_MIN, CYCLE_DELAY_MAX) ?: 300,
        beaconPulse = beaconPulse,
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
        statusMessage = "Fetching settings…"
        scope.launch {
            val url = PicoUrls.normalize(picoBaseUrl)
            if (url != picoBaseUrl) onPicoBaseUrlChange(url)
            when (val result = api.fetch(url)) {
                is PicoConfigApi.FetchResult.Success -> {
                    applyConfig(result.config)
                    if (result.usedUrl != picoBaseUrl) onPicoBaseUrlChange(result.usedUrl)
                    if (result.config.updateAvailable) updateOpen = true
                    statusMessage = "Loaded settings from the chart" +
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
        statusMessage = if (reboot) "Saving and rebooting…" else "Saving to the chart…"
        scope.launch {
            val url = PicoUrls.normalize(picoBaseUrl)
            if (url != picoBaseUrl) onPicoBaseUrlChange(url)
            when (val result = api.save(url, currentConfig(), reboot)) {
                is PicoConfigApi.SaveResult.Success -> {
                    if (result.usedUrl != picoBaseUrl) onPicoBaseUrlChange(result.usedUrl)
                    statusMessage = if (reboot) "Saved. The chart is rebooting." else "Saved settings"
                }
                is PicoConfigApi.SaveResult.Error ->
                    statusMessage = "Save failed: ${result.message}. Copy firmware 0.4.0+ to the chart."
            }
            isLoading = false
        }
    }

    fun installFirmwareUpdate() {
        isLoading = true
        statusMessage = "Starting firmware update…"
        scope.launch {
            val url = PicoUrls.normalize(picoBaseUrl)
            if (url != picoBaseUrl) onPicoBaseUrlChange(url)
            when (val result = api.startUpdate(url)) {
                is PicoConfigApi.UpdateResult.Success ->
                    statusMessage = result.message
                is PicoConfigApi.UpdateResult.Error ->
                    statusMessage = result.message
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = Amber,
        )
        Text(
            text = when {
                firmwareVersion == null -> "Chart firmware: Fetch to load"
                updateAvailable ->
                    "Chart firmware v$firmwareVersion — update available" +
                        (updateVersion?.let { " v$it" } ?: "")
                else -> "Chart firmware v$firmwareVersion — up to date"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (updateAvailable) Amber else Fog,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = picoBaseUrl,
            onValueChange = onPicoBaseUrlChange,
            label = { Text("Chart address") },
            supportingText = { Text("Find chart, or Fetch — it tries this address, the last home IP, and 192.168.4.1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = settingsFieldColors(),
        )
        OutlinedButton(
            onClick = {
                isLoading = true
                statusMessage = "Looking for the chart on this Wi-Fi…"
                scope.launch {
                    when (val result = PicoDiscovery.find(context, picoBaseUrl)) {
                        is PicoDiscovery.Result.Found -> {
                            onPicoBaseUrlChange(result.url)
                            statusMessage = result.message
                        }
                        is PicoDiscovery.Result.Error -> statusMessage = result.message
                    }
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Find chart") }
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

        SettingsSection(
            title = "Wi-Fi",
            summary = ssid.ifBlank { "Home network for the chart" },
            expanded = wifiOpen,
            onToggle = { wifiOpen = !wifiOpen },
        ) {
            Text(
                text = "This is the router the chart joins for internet — not the GreatLakes-Setup network. Leave password blank to keep the one already saved on the chart.",
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
        }

        SettingsSection(
            title = "Brightness",
            summary = buildString {
                append(LIGHT_SHOW_CHOICES.firstOrNull { it.id == lightShow }?.label ?: "Populate one by one")
                if (lightShow == "TOUR") {
                    append(" · flash ${tourFlashS.ifBlank { TOUR_FLASH_S_DEFAULT.toString() }}s · each ${tourStepS.ifBlank { TOUR_STEP_S_DEFAULT.toString() }}s")
                }
                append(" · ")
                append(DISPLAY_CHOICES.firstOrNull { it.id == displayType }?.label ?: "LED strip only")
                if (displayType == "LED_MATRIX") {
                    append(" · ")
                    append(MATRIX_SCROLL_CHOICES.firstOrNull { it.id == matrixScroll }?.label ?: "Weather only")
                    append(" · speed ${matrixScrollSpeed.toInt()} · ${scrollTimes.toInt()}×")
                }
                append(" · max ${maxBrightness.toInt()} · min ${minBrightness.ifBlank { "2" }} · refresh ${cycleDelay.ifBlank { "300" }}s")
            },
            expanded = brightnessOpen,
            onToggle = { brightnessOpen = !brightnessOpen },
        ) {
            ExposedDropdownMenuBox(
                expanded = lightShowMenuExpanded,
                onExpandedChange = { lightShowMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = LIGHT_SHOW_CHOICES.firstOrNull { it.id == lightShow }?.label ?: "Populate one by one",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Light show") },
                    supportingText = { Text("Keep flashing leaves every light on. One by one goes dark, then lights each lighthouse in list order with the name on OLED or matrix.") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(lightShowMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = !isLoading,
                    colors = settingsFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = lightShowMenuExpanded,
                    onDismissRequest = { lightShowMenuExpanded = false },
                ) {
                    LIGHT_SHOW_CHOICES.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = {
                                lightShow = choice.id
                                lightShowMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tourFlashS,
                onValueChange = { s ->
                    if (s.isEmpty() || (s.all { it.isDigit() } && s.length <= 4 && (s.toIntOrNull() ?: 0) <= TOUR_FLASH_S_MAX)) {
                        tourFlashS = s
                    }
                },
                label = { Text("All lights flash (seconds, $TOUR_FLASH_S_MIN-$TOUR_FLASH_S_MAX)") },
                supportingText = { Text("How long every light stays flashing before the one-by-one tour. 180 is 3 minutes.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
            )
            OutlinedTextField(
                value = tourStepS,
                onValueChange = { s ->
                    if (s.isEmpty() || (s.all { it.isDigit() } && s.length <= 3 && (s.toIntOrNull() ?: 0) <= TOUR_STEP_S_MAX)) {
                        tourStepS = s
                    }
                },
                label = { Text("Each light (seconds, $TOUR_STEP_S_MIN-$TOUR_STEP_S_MAX)") },
                supportingText = { Text("How long each lighthouse stays on during populate one by one, with the name on OLED or matrix.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
            )
            ExposedDropdownMenuBox(
                expanded = displayMenuExpanded,
                onExpandedChange = { displayMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = DISPLAY_CHOICES.firstOrNull { it.id == displayType }?.label ?: "LED strip only",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Extra display") },
                    supportingText = { Text("Lighthouse strip is always on. OLED uses GPIO 16–19. Matrix uses GPIO 1 and 5 V.") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(displayMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = !isLoading,
                    colors = settingsFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = displayMenuExpanded,
                    onDismissRequest = { displayMenuExpanded = false },
                ) {
                    DISPLAY_CHOICES.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = {
                                displayType = choice.id
                                displayMenuExpanded = false
                            },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = matrixScrollMenuExpanded,
                onExpandedChange = { matrixScrollMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = MATRIX_SCROLL_CHOICES.firstOrNull { it.id == matrixScroll }?.label ?: "Weather only",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LED matrix scroll") },
                    supportingText = { Text("IP once at startup. Weather only lists lights with rain/fog/snow/lightning. If none, GREAT LAKES LIGHTHOUSES every 15 seconds.") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matrixScrollMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = !isLoading,
                    colors = settingsFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = matrixScrollMenuExpanded,
                    onDismissRequest = { matrixScrollMenuExpanded = false },
                ) {
                    MATRIX_SCROLL_CHOICES.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = {
                                matrixScroll = choice.id
                                matrixScrollMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                text = "Matrix scroll speed: ${matrixScrollSpeed.toInt()} (1 slow, 10 fast)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = matrixScrollSpeed,
                onValueChange = { matrixScrollSpeed = it },
                valueRange = MATRIX_SCROLL_SPEED_MIN.toFloat()..MATRIX_SCROLL_SPEED_MAX.toFloat(),
                steps = MATRIX_SCROLL_SPEED_MAX - MATRIX_SCROLL_SPEED_MIN - 1,
                enabled = !isLoading,
            )
            Text(
                text = "Scroll times: ${scrollTimes.toInt()} (then it waits until the text changes)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = scrollTimes,
                onValueChange = { scrollTimes = it },
                valueRange = SCROLL_TIMES_MIN.toFloat()..SCROLL_TIMES_MAX.toFloat(),
                steps = SCROLL_TIMES_MAX - SCROLL_TIMES_MIN - 1,
                enabled = !isLoading,
            )
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
            Text(
                text = "Min brightness: $minBrightness (0 = off in the dark)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = (minBrightness.toIntOrNull() ?: 0).toFloat().coerceIn(0f, BRIGHTNESS_SLIDER_MAX.toFloat()),
                onValueChange = { minBrightness = it.toInt().toString() },
                valueRange = 0f..BRIGHTNESS_SLIDER_MAX.toFloat(),
                enabled = !isLoading,
            )
            OutlinedTextField(
                value = cycleDelay,
                onValueChange = { s ->
                    if (s.isEmpty() || (s.all { it.isDigit() } && s.length <= 4 && (s.toIntOrNull() ?: 0) <= CYCLE_DELAY_MAX)) {
                        cycleDelay = s
                    }
                },
                label = { Text("Refresh seconds ($CYCLE_DELAY_MIN-$CYCLE_DELAY_MAX)") },
                supportingText = { Text("How often the chart checks online. 300 is every 5 minutes.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Beacon pulse on clear weather",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = beaconPulse, onCheckedChange = { beaconPulse = it }, enabled = !isLoading)
            }
            Text(
                text = "Strip data pin",
                style = MaterialTheme.typography.titleSmall,
                color = Amber,
            )
            ExposedDropdownMenuBox(
                expanded = pinMenuExpanded,
                onExpandedChange = { pinMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = GPIO_CHOICES.firstOrNull { it.pin == ledPin }?.label ?: "GPIO $ledPin",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LED pin") },
                    supportingText = { Text("GPIO for the LED strip. Default is 0.") },
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
        }

        SettingsSection(
            title = "Sleep",
            summary = buildString {
                if (sleepEnabled) append("Off ${sleepAtHour.ifBlank { "22" }}:${sleepAtMinute.padStart(2, '0').ifBlank { "00" }}")
                else append("Nightly sleep off")
                if (weekendModeEnabled) append(" · weekend block on")
            },
            expanded = sleepOpen,
            onToggle = { sleepOpen = !sleepOpen },
        ) {
            Text(
                text = "Times use the UTC offset. The chart sets its clock after it joins Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
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
                    text = "Turn LEDs off at set times",
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
                        text = "Extra off period by weekday (e.g. Fri 18:00 → Mon 06:00). Stacks with nightly sleep.",
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
        }

        SettingsSection(
            title = "Update",
            summary = buildString {
                append(firmwareVersion?.let { "Firmware v$it" } ?: "Firmware")
                if (updateAvailable) {
                    append(" · update available")
                    updateVersion?.let { append(" (v$it)") }
                }
            },
            expanded = updateOpen,
            onToggle = { updateOpen = !updateOpen },
            highlight = updateAvailable,
        ) {
            firmwareVersion?.let {
                Text(
                    text = if (updateAvailable) {
                        "Firmware v$it — update available" + (updateVersion?.let { v -> " (v$v)" } ?: "")
                    } else {
                        "Firmware v$it"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (updateAvailable) Amber else Fog,
                )
            }
            Button(
                onClick = { installFirmwareUpdate() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (updateAvailable) Amber else MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (updateAvailable) {
                        "Install firmware update" + (updateVersion?.let { " (v$it)" } ?: "")
                    } else {
                        "Install firmware update"
                    },
                )
            }
            Text(
                text = "The chart must be on home Wi-Fi with internet. Your light list and Wi-Fi stay. The chart restarts when the update starts.",
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Amber,
                    )
                    if (!expanded && summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Fog,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = Fog,
                )
            }
            if (expanded) {
                content()
            }
        }
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
