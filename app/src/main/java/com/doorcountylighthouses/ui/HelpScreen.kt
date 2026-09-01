package com.doorcountylighthouses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doorcountylighthouses.ui.theme.Amber

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Help & Instructions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
            fontWeight = FontWeight.SemiBold,
        )

        HelpCard(
            title = "Quick start",
            body = "1. On your phone, join the chart’s setup Wi-Fi: name GreatLakes-Setup, password door1234.\n\n" +
                "2. Settings tab: enter your home (or hotel) Wi-Fi name and password — not the setup password — then tap Save & reboot. Wait for the chart to restart and join your network.\n\n" +
                "3. Lights tab: the Door County lights are already there. Change the list if you want, then tap Save to Pico.\n\n" +
                "Done. The lights blink like the real lighthouses. Use this Help tab for more detail.",
        )

        HelpCard(
            title = "What is this chart?",
            body = "Great Lakes Lighthouses is a nautical chart of lights. Each light matches a real lighthouse: white or red, steady or flashing, the same rhythm the lighthouse uses on the water.\n\n" +
                "You do not need to program anything. Use this app, or open the chart’s page in a phone or computer browser. Both do the same jobs: pick which lights are on the strip, and set Wi-Fi, brightness, and sleep.",
        )

        Text(
            text = "Getting started",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Step(
                    title = "1. Connect to the chart’s Wi-Fi",
                    body = "The first time you plug the chart in, it makes its own Wi-Fi named GreatLakes-Setup. Password: door1234. Join that network on your phone. Turn off mobile data if your phone keeps leaving Wi-Fi.\n\n" +
                        "Pico address: 192.168.4.1",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Step(
                    title = "2. Give the chart your home Wi-Fi (Settings)",
                    body = "Type your home or hotel Wi-Fi name and password. That is so the chart can reach the internet — not so your phone can join GreatLakes-Setup.\n\n" +
                        "Tap Save & reboot. After it restarts, put your phone back on home Wi-Fi. The chart’s address is now its home-network address, for example http://192.168.1.22. You can find that in your router’s device list.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Step(
                    title = "3. Set your lights (Lights tab)",
                    body = "Tap Fetch from Pico to see what is already on the chart. A finished Door County chart already has Kewaunee through Rock Island. Save to Pico writes your list. List order is the order of the lights on the strip, south to north.",
                )
            }
        }

        Text(
            text = "Lights tab (app) / Lighthouses (browser)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        HelpCard(
            title = "Your light list",
            body = "• Pico address: 192.168.4.1 on GreatLakes-Setup. After the chart joins home Wi-Fi, use the chart’s home-network address.\n\n" +
                "• Fetch from Pico / Reload: loads the list that is already on the chart.\n\n" +
                "• Save to Pico / Save list: sends your list to the chart. Do this after you add, skip, reorder, or delete lights.\n\n" +
                "• Use / Skip: Skip leaves that light dark but keeps its place on the strip.\n\n" +
                "• Drag the handle on the left to reorder. First in the list is the first light on the strip (usually the southernmost). In the browser, use Up / Down.\n\n" +
                "• Add from catalog: browse by shore (Chicago, Wisconsin, Green Bay, Michigan, Straits) or search by name. Color, flash, and nearest weather station are already filled in. Tap one to add it.\n\n" +
                "• Add custom: type a name, pick a flash pattern, and optionally a nearby weather station (for example KSUE).\n\n" +
                "• Restore defaults: puts back Kewaunee through Rock Island. Then Save so the chart uses that list.",
        )

        HelpCard(
            title = "Finished chart vs building your own",
            body = "A finished Door County chart already has the lights programmed. You do not need to add or remove any.\n\n" +
                "Adding, skipping, or reordering is for a custom chart. Put lights in the same order as the strip on the board. No programming — just the app or the browser page.",
        )

        Text(
            text = "Settings tab",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        HelpCard(
            title = "Wi-Fi, brightness, and sleep",
            body = "• Settings is grouped into Wi-Fi, brightness, sleep, and update. Tap a card to open it. Fetch / Save stay at the top.\n\n" +
                "• Fetch / Reload loads the chart’s current settings. The Wi-Fi password is never shown; leave Password blank to keep the one already saved.\n\n" +
                "• Home Wi-Fi name and password are for your router, not GreatLakes-Setup.\n\n" +
                "• Extra display: LED strip only, OLED (GPIO 16–19), or LED matrix (GPIO 1). The lighthouse strip is always on. Save & reboot after you change this.\n\n" +
                "• LED matrix scroll: Weather only (default) lists lights that currently have rain, fog, snow, lightning, or similar. If none do, GREAT LAKES LIGHTHOUSES scrolls about every 15 seconds. All lights scrolls every light. Matrix scroll speed is 1 (slow) to 10 (fast). The IP scrolls once at startup.\n\n" +
                "• Max brightness is how bright the lights get in a bright room (1 to 30). Min brightness is the dark-room floor (0 = off, 1–2 is a faint glow).\n\n" +
                "• Optional OLED (SSD1306): GPIO 16 data (SDA), 17 clock (SCL), 18 for 3.3 V, 19 for ground. Copy ssd1306.py, writer.py, and sans18.py onto the Pico. The screen scrolls the same text as the matrix (IP once, then light names and weather) using the large sans18 font. Matrix scroll speed also controls the OLED.\n\n" +
                "• Optional LED matrix (8x32 WS2812): data on GPIO 1, 5 V and common ground — not from pin 18. Copy led_matrix.py onto the Pico. Light names use flight-category color; weather codes use MetarMap colors.\n\n" +
                "• Refresh seconds is how often the chart checks online (30 to 3600). 300 is every 5 minutes.\n\n" +
                "• Sleep turns the lights off and on each night. Set the time offset first (Central: -6 in winter, -5 in daylight saving. Eastern: -5 / -4).\n\n" +
                "• Weekend / long off is an extra off stretch, for example Friday evening to Monday morning. It works together with nightly sleep.\n\n" +
                "• Save writes the settings. Save & reboot is the sure way after you change Wi-Fi.\n\n" +
                "In the browser, Pico settings always uses Save & Reboot.",
        )

        HelpCard(
            title = "Firmware updates",
            body = "After the chart joins home Wi-Fi, it checks online for a newer version. It does not install by itself.\n\n" +
                "If an update is waiting, Settings shows it, the browser page shows a yellow banner, and OLED / matrix scroll UPDATE AVAILABLE PRESS BUTTON. Press the setup button once to install, or tap Install firmware update. Hold the button 3 seconds to open GreatLakes-Setup.\n\n" +
                "The chart must be on home Wi-Fi with internet. Your light list and Wi-Fi stay. The chart restarts when the update starts. Wait about 30 seconds, then Fetch to confirm.",
        )

        HelpCard(
            title = "Using a browser instead of the app",
            body = "On GreatLakes-Setup, open http://192.168.4.1 in any browser. After the chart is on home Wi-Fi, open http:// then the chart’s home-network address.\n\n" +
                "The app has three tabs at the bottom: Lights, Settings, and Help. In a browser you will see Lighthouses, Pico settings, and Help. Same jobs: light list, Wi-Fi/brightness/sleep, and these instructions.",
        )

        Text(
            text = "Troubleshooting",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        HelpCard(
            title = "App / phone / browser",
            body = "• Can’t connect: join GreatLakes-Setup (password door1234) and use address 192.168.4.1. Turn off mobile data so the phone stays on that Wi-Fi.\n\n" +
                "• Fetch or Save failed: your phone must be on the same Wi-Fi as the chart. On setup Wi-Fi that is GreatLakes-Setup. After setup, both must be on your home Wi-Fi, and the Pico address must be the chart’s home-network address.\n\n" +
                "• Type http:// in front of the address if the page won’t open.\n\n" +
                "• Phone hotspot: putting the chart on a phone hotspot that uses cellular often fails. Use home or hotel Wi-Fi when you can.",
        )

        HelpCard(
            title = "The lights",
            body = "• A light stays dark: check that it is set to Use, not Skip. Check sleep and weekend off times.\n\n" +
                "• Sleep never turns the lights off: the chart needs home Wi-Fi so it can set the clock, and the time offset must match your local time.\n\n" +
                "• Catalog search is empty: use Restore defaults or Add custom. On a finished chart the catalog is already there.",
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Amber,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Step(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Amber,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp),
    )
}
