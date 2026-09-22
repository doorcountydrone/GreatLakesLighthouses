package com.doorcountylighthouses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.doorcountylighthouses.data.Lighthouse
import com.doorcountylighthouses.data.LighthouseRepository
import com.doorcountylighthouses.data.loadPicoBaseUrl
import com.doorcountylighthouses.data.savePicoBaseUrl
import com.doorcountylighthouses.pico.PicoLighthousesApi
import com.doorcountylighthouses.pico.PicoUrls
import com.doorcountylighthouses.ui.ChartScreen
import com.doorcountylighthouses.ui.HelpScreen
import com.doorcountylighthouses.ui.LighthouseListScreen
import com.doorcountylighthouses.ui.PicoSettingsScreen
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.DoorCountyLighthousesTheme
import com.doorcountylighthouses.ui.theme.Fog
import com.doorcountylighthouses.ui.theme.Navy
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoorCountyLighthousesTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(800)
                    showSplash = false
                }
                if (showSplash) {
                    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
                        Image(
                            painter = painterResource(R.drawable.splash),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    val context = LocalContext.current.applicationContext
                    var selectedTab by remember { mutableIntStateOf(0) }
                    var picoBaseUrl by remember { mutableStateOf(loadPicoBaseUrl(context)) }
                    var lights by remember { mutableStateOf(LighthouseRepository.load(context)) }
                    var fetchNote by remember { mutableStateOf<String?>(null) }
                    var listDirty by remember { mutableStateOf(false) }
                    var pendingLeaveTab by remember { mutableStateOf<Int?>(null) }
                    val persistLights: (List<Lighthouse>, Boolean) -> Unit = { next, save ->
                        val numbered = LighthouseRepository.renumber(next)
                        lights = numbered
                        if (save) LighthouseRepository.saveLocal(context, numbered)
                        listDirty = true
                    }
                    val markSynced: () -> Unit = { listDirty = false }
                    fun trySelectTab(tab: Int) {
                        val leavingListTabs = selectedTab <= 1 && tab >= 2
                        if (listDirty && leavingListTabs) {
                            pendingLeaveTab = tab
                        } else {
                            selectedTab = tab
                        }
                    }
                    BackHandler(enabled = listDirty) {
                        pendingLeaveTab = -1
                    }
                    LaunchedEffect(Unit) {
                        fetchNote = "Fetching from the chart…"
                        when (val result = PicoLighthousesApi(context).fetch(PicoUrls.normalize(picoBaseUrl))) {
                            is PicoLighthousesApi.FetchResult.Success -> {
                                persistLights(result.lights, true)
                                markSynced()
                                if (result.usedUrl != picoBaseUrl) {
                                    picoBaseUrl = result.usedUrl
                                    savePicoBaseUrl(context, result.usedUrl)
                                }
                                fetchNote = "Fetched ${result.lights.size} lights from the chart"
                            }
                            is PicoLighthousesApi.FetchResult.Error ->
                                fetchNote = "Chart not reached — using the list on this phone"
                        }
                    }
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Navy,
                        selectedTextColor = Amber,
                        indicatorColor = Amber,
                        unselectedIconColor = Fog,
                        unselectedTextColor = Fog,
                    )
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Navy),
                        containerColor = Navy,
                        bottomBar = {
                            NavigationBar(containerColor = Navy, contentColor = Amber) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { trySelectTab(0) },
                                    icon = { Icon(painterResource(R.drawable.ic_lighthouse), contentDescription = "Lights") },
                                    label = { Text("Lights") },
                                    colors = navColors,
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { trySelectTab(1) },
                                    icon = { Icon(Icons.Filled.Map, contentDescription = "Chart") },
                                    label = { Text("Chart") },
                                    colors = navColors,
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { trySelectTab(2) },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    colors = navColors,
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { trySelectTab(3) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help") },
                                    label = { Text("Help") },
                                    colors = navColors,
                                )
                            }
                        },
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (selectedTab) {
                                0 -> LighthouseListScreen(
                                    picoBaseUrl = picoBaseUrl,
                                    onPicoBaseUrlChange = {
                                        picoBaseUrl = it
                                        savePicoBaseUrl(context, it)
                                    },
                                    lights = lights,
                                    onLightsChange = persistLights,
                                    onSyncedWithChart = markSynced,
                                    listDirty = listDirty,
                                    initialStatus = fetchNote,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                1 -> ChartScreen(
                                    picoBaseUrl = picoBaseUrl,
                                    onPicoBaseUrlChange = {
                                        picoBaseUrl = it
                                        savePicoBaseUrl(context, it)
                                    },
                                    lights = lights,
                                    onLightsChange = persistLights,
                                    listDirty = listDirty,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                2 -> PicoSettingsScreen(
                                    picoBaseUrl = picoBaseUrl,
                                    onPicoBaseUrlChange = {
                                        picoBaseUrl = it
                                        savePicoBaseUrl(context, it)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                else -> HelpScreen(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    pendingLeaveTab?.let { target ->
                        AlertDialog(
                            onDismissRequest = { pendingLeaveTab = null },
                            title = { Text("List not saved to the chart") },
                            text = {
                                Text("Adds, skips, reorders, and deletes stay on this phone until you tap Save to chart.")
                            },
                            confirmButton = {
                                TextButton(onClick = { pendingLeaveTab = null }) {
                                    Text("Stay")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        pendingLeaveTab = null
                                        if (target < 0) {
                                            finish()
                                        } else {
                                            selectedTab = target
                                        }
                                    },
                                ) { Text("Leave anyway") }
                            },
                        )
                    }
                }
            }
        }
    }
}
