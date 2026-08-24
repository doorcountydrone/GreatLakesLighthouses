package com.doorcountylighthouses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.doorcountylighthouses.data.loadPicoBaseUrl
import com.doorcountylighthouses.data.savePicoBaseUrl
import com.doorcountylighthouses.ui.HelpScreen
import com.doorcountylighthouses.ui.LighthouseListScreen
import com.doorcountylighthouses.ui.PicoSettingsScreen
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.DoorCountyLighthousesTheme
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
                    delay(3000)
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
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Navy),
                    containerColor = Navy,
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Navy,
                            contentColor = Amber,
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Lights") },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Pico settings") },
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Help") },
                            )
                        }
                        when (selectedTab) {
                            0 -> LighthouseListScreen(
                                picoBaseUrl = picoBaseUrl,
                                onPicoBaseUrlChange = {
                                    picoBaseUrl = it
                                    savePicoBaseUrl(context, it)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            1 -> PicoSettingsScreen(
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
                }
            }
        }
    }
}
