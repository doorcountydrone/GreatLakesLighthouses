package com.doorcountylighthouses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doorcountylighthouses.data.CatalogEntry
import com.doorcountylighthouses.data.CatalogRepository
import com.doorcountylighthouses.data.LightPreset
import com.doorcountylighthouses.data.LightPresets
import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.LighthouseRepository
import com.doorcountylighthouses.pico.PicoLighthousesApi
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.Fog
import com.doorcountylighthouses.ui.theme.IfrRed
import com.doorcountylighthouses.ui.theme.VfrGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LighthouseListScreen(
    picoBaseUrl: String,
    onPicoBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picoApi = remember { PicoLighthousesApi() }
    var lights by remember { mutableStateOf<List<Lighthouse>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lights = LighthouseRepository.load(context)
    }

    fun persist(next: List<Lighthouse>) {
        val numbered = LighthouseRepository.renumber(next)
        lights = numbered
        LighthouseRepository.saveLocal(context, numbered)
    }

    fun move(index: Int, delta: Int) {
        val dest = index + delta
        if (dest !in lights.indices) return
        persist(lights.toMutableList().apply { add(dest, removeAt(index)) })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        ) {
            item {
                Text(
                    text = "Lighthouses",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Amber,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Search the Lake Michigan catalog to add lights (color and flash already filled in). List order is LED order. Save to the Pico when the strip matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fog,
                )
            }
            item {
                OutlinedTextField(
                    value = picoBaseUrl,
                    onValueChange = onPicoBaseUrlChange,
                    label = { Text("Pico address") },
                    supportingText = { Text("GreatLakes-Setup: 192.168.4.1  ·  Home Wi-Fi: Pico LAN IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = fieldColors(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            statusMessage = "Fetching from Pico…"
                            scope.launch {
                                when (val result = picoApi.fetch(picoBaseUrl)) {
                                    is PicoLighthousesApi.FetchResult.Success -> {
                                        persist(result.lights)
                                        statusMessage = "Fetched ${result.lights.size} lights from Pico"
                                    }
                                    is PicoLighthousesApi.FetchResult.Error ->
                                        statusMessage = "Fetch failed: ${result.message}"
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    ) { Text("Fetch from Pico") }
                    Button(
                        onClick = {
                            isLoading = true
                            statusMessage = "Saving to Pico…"
                            scope.launch {
                                when (val result = picoApi.save(picoBaseUrl, lights)) {
                                    PicoLighthousesApi.SaveResult.Success -> {
                                        persist(lights)
                                        statusMessage = "Saved ${lights.count { !it.skip }} lights to Pico"
                                    }
                                    is PicoLighthousesApi.SaveResult.Error ->
                                        statusMessage = "Save failed: ${result.message}. Connect to the Pico and try again."
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text("Save to Pico") }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { showCatalog = true }, enabled = !isLoading) {
                        Text("Add from catalog")
                    }
                    OutlinedButton(onClick = { showAdd = true }, enabled = !isLoading) {
                        Text("Add custom")
                    }
                    OutlinedButton(
                        onClick = {
                            persist(LighthouseRepository.loadBundled(context))
                            statusMessage = "Restored Kewaunee → Rock Island list"
                        },
                        enabled = !isLoading,
                    ) { Text("Restore defaults") }
                }
            }
            item {
                statusMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                Text(
                    text = "List (${lights.size} lights, ${lights.count { !it.skip }} on)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(lights, key = { index, item -> "${item.id}_$index" }) { index, light ->
                EditorCard(
                    light = light,
                    onMoveUp = { move(index, -1) },
                    onMoveDown = { move(index, 1) },
                    onSkipChange = { use ->
                        persist(lights.toMutableList().apply { set(index, light.copy(skip = !use)) })
                    },
                    onDelete = {
                        persist(lights.filterIndexed { i, _ -> i != index })
                        statusMessage = "Removed ${light.shortName}"
                    },
                )
            }
        }
    }

    if (showCatalog) {
        CatalogPickerDialog(
            alreadyOnMap = lights,
            onDismiss = { showCatalog = false },
            onAdd = { entry ->
                persist(lights + CatalogRepository.toLighthouse(entry))
                statusMessage = "Added ${entry.name}"
            },
        )
    }
    if (showAdd) {
        AddLighthouseDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, preset, metar ->
                persist(lights + LighthouseRepository.newLight(name, preset, metar))
                statusMessage = "Added $name"
                showAdd = false
            },
        )
    }
}

@Composable
private fun EditorCard(
    light: Lighthouse,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSkipChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ledSwatch(light.lightColor).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = light.led.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (light.skip) Fog else ledSwatch(light.lightColor),
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = light.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (light.skip) Fog else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = light.characteristic.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
                if (light.metar.isNotBlank()) {
                    Text(
                        text = light.metar,
                        style = MaterialTheme.typography.labelSmall,
                        color = Fog,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (light.skip) "Skip" else "Use",
                    style = MaterialTheme.typography.labelSmall,
                    color = Fog,
                )
                Switch(checked = !light.skip, onCheckedChange = onSkipChange)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${light.name}")
            }
        }
    }
}

@Composable
private fun CatalogPickerDialog(
    alreadyOnMap: List<Lighthouse>,
    onDismiss: () -> Unit,
    onAdd: (CatalogEntry) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember { CatalogRepository.load(context) }
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalog) {
        catalog.filter { it.matches(query) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lake Michigan catalog") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    supportingText = { Text("${results.size} of ${catalog.size} · try Grand Haven, St. Joseph, Point Betsie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results, key = { it.id }) { entry ->
                        val onMap = CatalogRepository.alreadyOnMap(entry, alreadyOnMap)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (!onMap) onAdd(entry) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = entry.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${entry.characteristic} · ${entry.region}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (onMap) Fog else Amber,
                                )
                                if (onMap) {
                                    Text(
                                        text = "Already on this map",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Fog,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLighthouseDialog(
    onDismiss: () -> Unit,
    onAdd: (String, LightPreset, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var metar by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(LightPresets.all.first()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add lighthouse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = preset.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Characteristic") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LightPresets.all.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    preset = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = metar,
                    onValueChange = { metar = it.uppercase().take(4) },
                    label = { Text("Nearby METAR (optional)") },
                    supportingText = { Text("Example: KSUE, KMTW, 3D2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onAdd(name.trim(), preset, metar) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = Amber,
    unfocusedLabelColor = Fog,
    cursorColor = Amber,
    focusedBorderColor = Amber,
    unfocusedBorderColor = Fog,
)

private fun ledSwatch(code: String): Color = when (code.uppercase()) {
    "R" -> IfrRed
    "G" -> VfrGreen
    else -> Color(0xFFFFECD4)
}
