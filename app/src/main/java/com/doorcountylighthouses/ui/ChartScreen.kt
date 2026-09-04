package com.doorcountylighthouses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.LighthouseRepository
import com.doorcountylighthouses.pico.PicoLighthousesApi
import com.doorcountylighthouses.pico.PicoUrls
import kotlinx.coroutines.launch
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.Cream
import com.doorcountylighthouses.ui.theme.Fog
import com.doorcountylighthouses.ui.theme.Navy
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private val DoorCounty = LatLng(45.05, -87.12)

@Composable
fun ChartScreen(
    picoBaseUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picoApi = remember { PicoLighthousesApi(context) }
    var lights by remember { mutableStateOf(LighthouseRepository.load(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        lights = LighthouseRepository.load(context)
    }
    val placed = remember(lights) { lights.filter { it.hasCoordinates } }
    val missing = lights.size - placed.size

    fun identify(light: Lighthouse) {
        val led = light.led
        val label = light.shortName.ifBlank { light.name }
        statusMessage = "Identifying LED ${led + 1} ($label) on the Pico…"
        scope.launch {
            statusMessage = when (val result = picoApi.identify(PicoUrls.normalize(picoBaseUrl), led)) {
                is PicoLighthousesApi.IdentifyResult.Success ->
                    "LED ${result.led + 1} only on the Pico for ${result.ms / 1000} seconds."
                is PicoLighthousesApi.IdentifyResult.Error ->
                    "Identify failed: ${result.message}"
            }
        }
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
                lights.isEmpty() -> "Fetch your list on the Lights tab."
                placed.isEmpty() -> "This list has no coordinates yet. Fetch from the Pico or add catalog lights."
                missing > 0 -> "${placed.size} of ${lights.size} lights on the chart. Custom lights without a location stay off the map."
                else -> "${placed.size} lights on your list. Tap one to light only that LED on the Pico."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
        )
        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
            )
        }
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
                placed.isEmpty() -> Text(
                    text = "Nothing to plot yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fog,
                )
                else -> LightMap(placed, onIdentify = { identify(it) })
            }
        }
    }
}

@Composable
private fun LightMap(
    lights: List<Lighthouse>,
    onIdentify: (Lighthouse) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = context.resources.displayMetrics.density
    var icons by remember { mutableStateOf<LighthouseIcons?>(null) }
    var satellite by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = fitLights(lights)
    }

    LaunchedEffect(density) {
        runCatching {
            MapsInitializer.initialize(context)
            icons = LighthouseIcons(density.coerceIn(2f, 3.5f))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = if (satellite) MapType.HYBRID else MapType.NORMAL,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                mapToolbarEnabled = false,
            ),
        ) {
            val ready = icons
            if (ready != null) {
                lights.forEach { light ->
                    key(light.id) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(light.lat, light.lon)),
                            title = light.shortName.ifBlank { light.name },
                            snippet = buildString {
                                append("LED ${light.led + 1}")
                                if (light.characteristic.isNotBlank()) append("  ${light.characteristic}")
                                if (light.skip) append("  skipped")
                            },
                            icon = ready.iconFor(light),
                            anchor = Offset(0.5f, ready.anchorY),
                            alpha = if (light.skip) 0.55f else 1f,
                            onClick = {
                                onIdentify(light)
                                false
                            },
                        )
                    }
                }
            }
        }
        FilterChip(
            selected = satellite,
            onClick = { satellite = !satellite },
            label = { Text("Satellite") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Navy.copy(alpha = 0.88f),
                labelColor = Cream,
                selectedContainerColor = Amber,
                selectedLabelColor = Navy,
            ),
        )
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
        val led = light.led + 1
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
