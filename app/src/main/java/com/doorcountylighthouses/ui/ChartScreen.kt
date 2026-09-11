package com.doorcountylighthouses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.doorcountylighthouses.BuildConfig
import com.doorcountylighthouses.data.CatalogEntry
import com.doorcountylighthouses.data.CatalogRepository
import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.loadChartAidFilter
import com.doorcountylighthouses.data.loadChartBuoyFilter
import com.doorcountylighthouses.data.loadChartShowsCatalog
import com.doorcountylighthouses.data.loadChartUsesNoaa
import com.doorcountylighthouses.data.saveChartAidFilter
import com.doorcountylighthouses.data.saveChartBuoyFilter
import com.doorcountylighthouses.data.saveChartShowsCatalog
import com.doorcountylighthouses.data.saveChartUsesNoaa
import com.doorcountylighthouses.pico.PicoLighthousesApi
import com.doorcountylighthouses.pico.PicoUrls
import kotlinx.coroutines.launch
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.CardNavy
import com.doorcountylighthouses.ui.theme.Cream
import com.doorcountylighthouses.ui.theme.Fog
import com.doorcountylighthouses.ui.theme.Navy
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.net.URL

private val DoorCounty = LatLng(45.05, -87.12)

@Composable
fun ChartScreen(
    picoBaseUrl: String,
    onPicoBaseUrlChange: (String) -> Unit,
    lights: List<Lighthouse>,
    onLightsChange: (List<Lighthouse>, save: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picoApi = remember { PicoLighthousesApi(context) }
    val catalog = remember { CatalogRepository.load(context) }
    var buoyFilter by remember { mutableStateOf(loadChartBuoyFilter(context)) }
    var aidFilter by remember { mutableStateOf(loadChartAidFilter(context)) }
    var selected by remember { mutableStateOf<Lighthouse?>(null) }
    var catalogPick by remember { mutableStateOf<CatalogNear?>(null) }
    var pendingSelectId by remember { mutableStateOf<String?>(null) }
    var identifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lights, selected?.id, pendingSelectId) {
        val prefer = pendingSelectId
        if (prefer != null) {
            val added = lights.find { it.id == prefer }
            if (added != null) {
                selected = added
                pendingSelectId = null
                return@LaunchedEffect
            }
        }
        val id = selected?.id ?: return@LaunchedEffect
        selected = lights.find { it.id == id }
    }
    val placed = remember(lights) { lights.filter { it.hasCoordinates } }
    val available = remember(catalog, lights, buoyFilter, aidFilter) {
        catalog.filter { entry ->
            (entry.lat != 0.0 || entry.lon != 0.0) &&
                !CatalogRepository.alreadyOnMap(entry, lights) &&
                CatalogRepository.matchesChartBuoyFilter(entry, buoyFilter) &&
                CatalogRepository.matchesChartAidFilter(entry, aidFilter)
        }
    }
    val missing = lights.size - placed.size

    fun identify(light: Lighthouse) {
        val led = light.led
        val label = light.shortName.ifBlank { light.name }
        identifying = true
        statusMessage = "Identifying LED ${light.displayLed} ($label) on the Pico…"
        scope.launch {
            statusMessage = when (val result = picoApi.identify(PicoUrls.normalize(picoBaseUrl), led)) {
                is PicoLighthousesApi.IdentifyResult.Success -> {
                    if (result.usedUrl != picoBaseUrl) onPicoBaseUrlChange(result.usedUrl)
                    "LED ${result.led + 1} only on the Pico for ${result.ms / 1000} seconds."
                }
                is PicoLighthousesApi.IdentifyResult.Error ->
                    "Identify failed: ${result.message}"
            }
            identifying = false
        }
    }

    fun addFromCatalog(entry: CatalogEntry) {
        if (CatalogRepository.alreadyOnMap(entry, lights)) {
            catalogPick = null
            return
        }
        pendingSelectId = entry.id
        catalogPick = null
        statusMessage = "Added to your list. Drag on Lights for strip order, then Save to Pico."
        onLightsChange(lights + CatalogRepository.toLighthouse(entry), true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Chart",
            style = MaterialTheme.typography.headlineSmall,
            color = Amber,
        )
        Text(
            text = when {
                !BuildConfig.HAS_MAPS_KEY -> "Add a Maps key to secrets.properties on this computer."
                lights.isEmpty() && available.isEmpty() -> "Fetch your list on the Lights tab."
                lights.isEmpty() -> "Faint dots are catalog. Lighthouses hides pier numbers. Tap a dot or the water."
                placed.isEmpty() -> "This list has no coordinates yet. Tap a catalog dot or the water to add one."
                missing > 0 -> "${placed.size} on your list, ${available.size} catalog dots. Custom lights without a location stay off the map."
                else -> "${placed.size} on your list. Bold pin = Identify. Lighthouses / Lights, then buoys."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Navy),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !BuildConfig.HAS_MAPS_KEY -> Text(
                    text = "Add a Maps key to secrets.properties on this computer. That file is not pushed to GitHub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                    modifier = Modifier.padding(16.dp),
                )
                placed.isEmpty() && available.isEmpty() -> Text(
                    text = "Nothing to plot yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                )
                else -> LightMap(
                    lights = placed,
                    catalog = available,
                    buoyFilter = buoyFilter,
                    onBuoyFilterChange = {
                        buoyFilter = it
                        saveChartBuoyFilter(context, it)
                    },
                    aidFilter = aidFilter,
                    onAidFilterChange = {
                        aidFilter = it
                        saveChartAidFilter(context, it)
                    },
                    onSelectLight = {
                        selected = it
                        catalogPick = null
                        statusMessage = null
                    },
                    onSelectCatalog = { entry, miles ->
                        selected = null
                        catalogPick = CatalogNear(entry, miles)
                        statusMessage = null
                    },
                    onEmptyTap = { point ->
                        statusMessage = null
                        val strip = nearestLight(placed, point)
                        val cat = nearestCatalog(available, point)
                        if (strip != null && (cat == null || strip.miles <= cat.miles)) {
                            selected = strip.light
                            catalogPick = null
                        } else {
                            selected = null
                            catalogPick = cat
                        }
                    },
                )
            }
            catalogPick?.let { pick ->
                CatalogInfoCard(
                    pick = pick,
                    nextLed = lights.size + 1,
                    onAdd = { addFromCatalog(pick.entry) },
                    onDismiss = { catalogPick = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
            selected?.let { light ->
                LightInfoCard(
                    light = light,
                    statusMessage = statusMessage,
                    identifying = identifying,
                    onIdentify = { identify(light) },
                    onDismiss = {
                        selected = null
                        statusMessage = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun LightInfoCard(
    light: Lighthouse,
    statusMessage: String?,
    identifying: Boolean,
    onIdentify: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledNumber = light.displayLed
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = light.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Cream,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Fog)
                }
            }
            Text(
                text = light.characteristic.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = Amber,
            )
            Text(
                text = buildString {
                    append("LED $ledNumber")
                    if (light.skip) append("  ·  skipped")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
            )
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }
            Button(
                onClick = onIdentify,
                enabled = !identifying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Navy,
                ),
            ) {
                Text(if (identifying) "Identifying LED $ledNumber…" else "Identify LED $ledNumber")
            }
        }
    }
}

@Composable
private fun CatalogInfoCard(
    pick: CatalogNear,
    nextLed: Int,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = pick.entry
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Cream,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Fog)
                }
            }
            Text(
                text = entry.characteristic.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = Amber,
            )
            Text(
                text = buildString {
                    append(entry.region)
                    append("  ·  ")
                    append(formatMiles(pick.miles))
                    append("  ·  not on your list")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
            )
            Text(
                text = "Adds as LED $nextLed. Drag on Lights to match the strip, then Save to Pico.",
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Navy,
                ),
            ) {
                Text("Add to list")
            }
        }
    }
}

@Composable
private fun LightMap(
    lights: List<Lighthouse>,
    catalog: List<CatalogEntry>,
    buoyFilter: String,
    onBuoyFilterChange: (String) -> Unit,
    aidFilter: String,
    onAidFilterChange: (String) -> Unit,
    onSelectLight: (Lighthouse) -> Unit,
    onSelectCatalog: (CatalogEntry, Double) -> Unit,
    onEmptyTap: (LatLng) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = context.resources.displayMetrics.density
    var icons by remember { mutableStateOf<LighthouseIcons?>(null) }
    var catalogIcons by remember { mutableStateOf<CatalogIcons?>(null) }
    var satellite by remember { mutableStateOf(false) }
    var showNoaa by remember { mutableStateOf(loadChartUsesNoaa(context)) }
    var showCatalog by remember { mutableStateOf(loadChartShowsCatalog(context)) }
    var ignoreEmptyTap by remember { mutableStateOf(false) }
    val noaaTiles = remember { NoaaEncTileProvider() }
    val cameraPositionState = rememberCameraPositionState {
        position = fitLights(lights)
    }

    LaunchedEffect(density) {
        runCatching {
            MapsInitializer.initialize(context)
            val scale = density.coerceIn(2f, 3.5f)
            icons = LighthouseIcons(scale)
            catalogIcons = CatalogIcons(scale)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = when {
                    showNoaa -> MapType.NONE
                    satellite -> MapType.HYBRID
                    else -> MapType.NORMAL
                },
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                mapToolbarEnabled = false,
            ),
            onMapClick = { point ->
                if (ignoreEmptyTap) {
                    ignoreEmptyTap = false
                } else {
                    onEmptyTap(point)
                }
            },
        ) {
            if (showNoaa) {
                TileOverlay(
                    tileProvider = noaaTiles,
                    fadeIn = false,
                )
            }
            val ghosts = catalogIcons
            if (showCatalog && ghosts != null) {
                catalog.forEach { entry ->
                    key("cat-${entry.id}") {
                        Marker(
                            state = rememberMarkerState(position = LatLng(entry.lat, entry.lon)),
                            title = entry.shortName.ifBlank { entry.name },
                            snippet = entry.characteristic,
                            icon = ghosts.iconFor(
                                entry.lightColor,
                                CatalogRepository.isBuoy(entry),
                                CatalogRepository.isLighthouse(entry),
                            ),
                            anchor = if (CatalogRepository.isLighthouse(entry)) {
                                Offset(0.5f, 0.92f)
                            } else {
                                Offset(0.5f, 0.5f)
                            },
                            alpha = 0.72f,
                            zIndex = 0f,
                            onClick = {
                                ignoreEmptyTap = true
                                onSelectCatalog(entry, 0.0)
                                true
                            },
                        )
                    }
                }
            }
            val ready = icons
            if (ready != null) {
                lights.forEach { light ->
                    key(light.id) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(light.lat, light.lon)),
                            title = light.shortName.ifBlank { light.name },
                            snippet = buildString {
                                append("LED ${light.displayLed}")
                                if (light.characteristic.isNotBlank()) append("  ${light.characteristic}")
                                if (light.skip) append("  skipped")
                            },
                            icon = ready.iconFor(light),
                            anchor = Offset(0.5f, ready.anchorY),
                            alpha = if (light.skip) 0.55f else 1f,
                            zIndex = 2f,
                            onClick = {
                                ignoreEmptyTap = true
                                onSelectLight(light)
                                true
                            },
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChip(
                        selected = !showNoaa,
                        onClick = {
                            showNoaa = false
                            saveChartUsesNoaa(context, false)
                        },
                        label = "Map",
                    )
                    CompactChip(
                        selected = showNoaa,
                        onClick = {
                            showNoaa = true
                            saveChartUsesNoaa(context, true)
                        },
                        label = "NOAA",
                    )
                    CompactChip(
                        selected = showCatalog,
                        onClick = {
                            showCatalog = !showCatalog
                            saveChartShowsCatalog(context, showCatalog)
                        },
                        label = "Catalog",
                    )
                }
                if (!showNoaa) {
                    CompactChip(
                        selected = satellite,
                        onClick = { satellite = !satellite },
                        label = "Satellite",
                    )
                }
            }
            if (showCatalog) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChip(
                        selected = aidFilter == CatalogRepository.AID_TOWERS,
                        onClick = { onAidFilterChange(CatalogRepository.AID_TOWERS) },
                        label = "Lighthouses",
                    )
                    CompactChip(
                        selected = aidFilter == CatalogRepository.AID_MARKS,
                        onClick = { onAidFilterChange(CatalogRepository.AID_MARKS) },
                        label = "Lights",
                    )
                    CompactChip(
                        selected = aidFilter == CatalogRepository.AID_BOTH,
                        onClick = { onAidFilterChange(CatalogRepository.AID_BOTH) },
                        label = "All",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChip(
                        selected = buoyFilter == CatalogRepository.BUOY_NONE,
                        onClick = { onBuoyFilterChange(CatalogRepository.BUOY_NONE) },
                        label = "None",
                    )
                    CompactChip(
                        selected = buoyFilter == CatalogRepository.BUOY_GREEN,
                        onClick = { onBuoyFilterChange(CatalogRepository.BUOY_GREEN) },
                        label = "Green",
                    )
                    CompactChip(
                        selected = buoyFilter == CatalogRepository.BUOY_RED,
                        onClick = { onBuoyFilterChange(CatalogRepository.BUOY_RED) },
                        label = "Red",
                    )
                    CompactChip(
                        selected = buoyFilter == CatalogRepository.BUOY_BOTH,
                        onClick = { onBuoyFilterChange(CatalogRepository.BUOY_BOTH) },
                        label = "Both",
                    )
                }
            }
        }
        if (showNoaa) {
            Text(
                text = "NOAA ENC · not for navigation",
                style = MaterialTheme.typography.labelSmall,
                color = Cream,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Navy.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Surface(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Amber else Navy.copy(alpha = 0.88f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Navy else Cream,
                maxLines = 1,
            )
        }
    }
}

private data class CatalogNear(val entry: CatalogEntry, val miles: Double)

private data class LightNear(val light: Lighthouse, val miles: Double)

private fun nearestLight(lights: List<Lighthouse>, point: LatLng): LightNear? {
    var best: Lighthouse? = null
    var bestMiles = Double.MAX_VALUE
    for (light in lights) {
        val miles = haversineMiles(point.latitude, point.longitude, light.lat, light.lon)
        if (miles < bestMiles) {
            bestMiles = miles
            best = light
        }
    }
    return best?.let { LightNear(it, bestMiles) }
}

private fun nearestCatalog(entries: List<CatalogEntry>, point: LatLng): CatalogNear? {
    var best: CatalogEntry? = null
    var bestMiles = Double.MAX_VALUE
    for (entry in entries) {
        val miles = haversineMiles(point.latitude, point.longitude, entry.lat, entry.lon)
        if (miles < bestMiles) {
            bestMiles = miles
            best = entry
        }
    }
    return best?.let { CatalogNear(it, bestMiles) }
}

private fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 3958.8
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dLon / 2).let { it * it }
    return 2 * earth * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
}

private fun formatMiles(miles: Double): String = when {
    miles < 0.05 -> "right here"
    miles < 10 -> "${"%.1f".format(java.util.Locale.US, miles)} mi"
    else -> "${miles.toInt()} mi"
}

private class CatalogIcons(private val scale: Float) {
    private val cache = mutableMapOf<String, BitmapDescriptor>()

    fun iconFor(color: String, buoy: Boolean, lighthouse: Boolean): BitmapDescriptor {
        val tone = when {
            color.equals("R", ignoreCase = true) -> "R"
            color.equals("G", ignoreCase = true) -> "G"
            else -> "W"
        }
        val key = when {
            buoy -> "B-$tone"
            lighthouse -> "T-$tone"
            else -> tone
        }
        return cache.getOrPut(key) {
            when {
                buoy -> catalogDiamond(scale, tone)
                lighthouse -> catalogTower(scale, tone)
                else -> catalogDot(scale, tone)
            }
        }
    }
}

private fun catalogFill(colorKey: String): Int = when (colorKey) {
    "R" -> 0xFFE74C3C.toInt()
    "G" -> 0xFF2ECC71.toInt()
    else -> 0xFFF4EBD0.toInt()
}

private fun catalogTower(scale: Float, colorKey: String): BitmapDescriptor {
    val w = (16 * scale).toInt().coerceAtLeast(16)
    val h = (22 * scale).toInt().coerceAtLeast(22)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.2f * scale
        color = 0xFF0B1F3A.toInt()
    }
    val cx = w / 2f
    val body = catalogFill(colorKey)
    val baseTop = h * 0.78f
    val galleryY = h * 0.34f
    val lanternTop = h * 0.16f

    val tower = Path().apply {
        moveTo(cx - 3.2f * scale, galleryY)
        lineTo(cx + 3.2f * scale, galleryY)
        lineTo(cx + 5.4f * scale, baseTop)
        lineTo(cx - 5.4f * scale, baseTop)
        close()
    }
    fill.color = body
    canvas.drawPath(tower, fill)
    canvas.drawPath(tower, stroke)

    fill.color = darken(body, 0.78f)
    canvas.drawRect(cx - 6.2f * scale, baseTop, cx + 6.2f * scale, h - 1.2f * scale, fill)
    canvas.drawRect(cx - 6.2f * scale, baseTop, cx + 6.2f * scale, h - 1.2f * scale, stroke)

    fill.color = darken(body, 0.72f)
    canvas.drawRoundRect(
        cx - 4.6f * scale,
        galleryY - 1.4f * scale,
        cx + 4.6f * scale,
        galleryY + 2.2f * scale,
        1f * scale,
        1f * scale,
        fill,
    )

    fill.color = when (colorKey) {
        "R" -> 0xFFFFC9C2.toInt()
        "G" -> 0xFFC8F5D8.toInt()
        else -> 0xFFE8A838.toInt()
    }
    canvas.drawRoundRect(
        cx - 2.6f * scale,
        lanternTop + 1.6f * scale,
        cx + 2.6f * scale,
        galleryY - 0.8f * scale,
        0.8f * scale,
        0.8f * scale,
        fill,
    )

    val roof = Path().apply {
        moveTo(cx - 3.6f * scale, lanternTop + 1.8f * scale)
        lineTo(cx, lanternTop - 0.4f * scale)
        lineTo(cx + 3.6f * scale, lanternTop + 1.8f * scale)
        close()
    }
    fill.color = 0xFF0B1F3A.toInt()
    canvas.drawPath(roof, fill)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun catalogDiamond(scale: Float, colorKey: String): BitmapDescriptor {
    val size = (20 * scale).toInt().coerceAtLeast(20)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = catalogFill(colorKey)
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.4f * scale
        color = 0xFF0B1F3A.toInt()
    }
    val c = size / 2f
    val inset = 1.4f * scale
    val diamond = Path().apply {
        moveTo(c, inset)
        lineTo(size - inset, c)
        lineTo(c, size - inset)
        lineTo(inset, c)
        close()
    }
    canvas.drawPath(diamond, fill)
    canvas.drawPath(diamond, stroke)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun catalogDot(scale: Float, colorKey: String): BitmapDescriptor {
    val size = (18 * scale).toInt().coerceAtLeast(18)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * scale
        color = 0xFF0B1F3A.toInt()
    }
    fill.color = catalogFill(colorKey)
    val r = size / 2f
    val inset = 1.2f * scale
    canvas.drawCircle(r, r, r - inset, fill)
    canvas.drawCircle(r, r, r - inset, stroke)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private class NoaaEncTileProvider : UrlTileProvider(TILE, TILE) {
    override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) return null
        val n = 1 shl zoom
        if (x !in 0 until n || y !in 0 until n) return null
        val origin = 20037508.342789244
        val size = (origin * 2.0) / n
        val minX = -origin + x * size
        val maxX = -origin + (x + 1) * size
        val maxY = origin - y * size
        val minY = origin - (y + 1) * size
        val url = "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap" +
            "&LAYERS=0,1,2,3,4,5,6&STYLES=&CRS=EPSG:3857" +
            "&BBOX=$minX,$minY,$maxX,$maxY" +
            "&WIDTH=$TILE&HEIGHT=$TILE&FORMAT=image/png&TRANSPARENT=FALSE"
        return runCatching { URL(url) }.getOrNull()
    }

    companion object {
        private const val TILE = 256
        private const val MIN_ZOOM = 5
        private const val MAX_ZOOM = 16
        private const val WMS =
            "https://gis.charttools.noaa.gov/arcgis/rest/services/MCS/ENCOnline/MapServer/exts/MaritimeChartService/WMSServer"
    }
}

private fun fitLights(lights: List<Lighthouse>): CameraPosition {
    if (lights.isEmpty()) {
        return CameraPosition.fromLatLngZoom(DoorCounty, 8f)
    }
    if (lights.size == 1) {
        return CameraPosition.fromLatLngZoom(LatLng(lights[0].lat, lights[0].lon), 11f)
    }
    var minLat = 90.0
    var maxLat = -90.0
    var minLon = 180.0
    var maxLon = -180.0
    lights.forEach { light ->
        minLat = minOf(minLat, light.lat)
        maxLat = maxOf(maxLat, light.lat)
        minLon = minOf(minLon, light.lon)
        maxLon = maxOf(maxLon, light.lon)
    }
    val center = LatLng((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    val span = maxOf(maxLat - minLat, (maxLon - minLon) * 0.7).coerceAtLeast(0.04)
    val zoom = when {
        span > 8 -> 5f
        span > 4 -> 6f
        span > 2 -> 7f
        span > 1 -> 8f
        span > 0.4 -> 9f
        span > 0.15 -> 10f
        else -> 11f
    }
    return CameraPosition.fromLatLngZoom(center, zoom)
}

private class LighthouseIcons(private val scale: Float) {
    private val cache = mutableMapOf<String, BitmapDescriptor>()
    val anchorY: Float

    init {
        val lighthouseH = 52f * scale
        val totalH = lighthouseH + 16f * scale
        anchorY = lighthouseH / totalH
    }

    fun iconFor(light: Lighthouse): BitmapDescriptor {
        val colorKey = when {
            light.skip -> "S"
            light.lightColor.equals("R", ignoreCase = true) -> "R"
            light.lightColor.equals("G", ignoreCase = true) -> "G"
            else -> "W"
        }
        val led = light.displayLed
        return cache.getOrPut("$colorKey-$led") {
            val body: Int
            val lamp: Int
            when (colorKey) {
                "R" -> {
                    body = 0xFFE74C3C.toInt()
                    lamp = 0xFFFFC9C2.toInt()
                }
                "G" -> {
                    body = 0xFF2ECC71.toInt()
                    lamp = 0xFFC8F5D8.toInt()
                }
                "S" -> {
                    body = 0xFF7A93A8.toInt()
                    lamp = 0xFFD5DEE6.toInt()
                }
                else -> {
                    body = 0xFFF4EBD0.toInt()
                    lamp = 0xFFE8A838.toInt()
                }
            }
            lighthouseDescriptor(scale, body, lamp, led)
        }
    }
}

private fun lighthouseDescriptor(scale: Float, body: Int, lamp: Int, led: Int): BitmapDescriptor {
    val w = (40 * scale).toInt()
    val lighthouseH = 52f * scale
    val h = (lighthouseH + 16f * scale).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.6f * scale
        color = 0xFF0B1F3A.toInt()
    }

    val cx = w / 2f
    val baseTop = lighthouseH * 0.78f
    val galleryY = lighthouseH * 0.30f
    val lanternTop = lighthouseH * 0.12f

    val tower = Path().apply {
        moveTo(cx - 5.2f * scale, galleryY)
        lineTo(cx + 5.2f * scale, galleryY)
        lineTo(cx + 9.2f * scale, baseTop)
        lineTo(cx - 9.2f * scale, baseTop)
        close()
    }
    fill.color = body
    canvas.drawPath(tower, fill)
    canvas.drawPath(tower, stroke)

    val base = Path().apply {
        moveTo(cx - 12f * scale, baseTop)
        lineTo(cx + 12f * scale, baseTop)
        lineTo(cx + 11f * scale, lighthouseH - 1.5f * scale)
        lineTo(cx - 11f * scale, lighthouseH - 1.5f * scale)
        close()
    }
    fill.color = darken(body, 0.78f)
    canvas.drawPath(base, fill)
    canvas.drawPath(base, stroke)

    fill.color = darken(body, 0.72f)
    canvas.drawRoundRect(
        cx - 8.2f * scale,
        galleryY - 2.4f * scale,
        cx + 8.2f * scale,
        galleryY + 3.4f * scale,
        1.2f * scale,
        1.2f * scale,
        fill,
    )
    canvas.drawRoundRect(
        cx - 8.2f * scale,
        galleryY - 2.4f * scale,
        cx + 8.2f * scale,
        galleryY + 3.4f * scale,
        1.2f * scale,
        1.2f * scale,
        stroke,
    )

    fill.color = lamp
    canvas.drawRoundRect(
        cx - 4.6f * scale,
        lanternTop + 2.2f * scale,
        cx + 4.6f * scale,
        galleryY - 1.4f * scale,
        1.1f * scale,
        1.1f * scale,
        fill,
    )
    canvas.drawRoundRect(
        cx - 4.6f * scale,
        lanternTop + 2.2f * scale,
        cx + 4.6f * scale,
        galleryY - 1.4f * scale,
        1.1f * scale,
        1.1f * scale,
        stroke,
    )

    val roof = Path().apply {
        moveTo(cx - 6.4f * scale, lanternTop + 2.6f * scale)
        lineTo(cx, lanternTop - 0.4f * scale)
        lineTo(cx + 6.4f * scale, lanternTop + 2.6f * scale)
        close()
    }
    fill.color = 0xFF0B1F3A.toInt()
    canvas.drawPath(roof, fill)

    val label = led.toString()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4EBD0.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 11f * scale
        isFakeBoldText = true
    }
    val tw = text.measureText(label)
    val pad = 3.4f * scale
    val labelTop = lighthouseH + 0.4f * scale
    val labelBottom = h - 1.2f * scale
    fill.color = android.graphics.Color.argb(0xE6, 0x0B, 0x1F, 0x3A)
    canvas.drawRoundRect(
        cx - tw / 2f - pad,
        labelTop,
        cx + tw / 2f + pad,
        labelBottom,
        3f * scale,
        3f * scale,
        fill,
    )
    val textY = labelTop + (labelBottom - labelTop) / 2f - (text.descent() + text.ascent()) / 2f
    canvas.drawText(label, cx, textY, text)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun darken(color: Int, factor: Float): Int {
    val a = android.graphics.Color.alpha(color)
    val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
    val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
    val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(a, r, g, b)
}
