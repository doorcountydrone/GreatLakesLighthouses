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
            text = "App version $versionName",
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
            fontWeight = FontWeight.SemiBold,
        )
        rememberAppUpdate()?.let { AppUpdateBanner(it) }
        Text(
            text = "Chart firmware is on the Settings tab after Fetch — not here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        HelpCard(
            title = "Quick start",
            body = "1. On your phone, join the chart’s setup Wi-Fi: name GreatLakes-Setup, password door1234.\n\n" +
                "2. Settings tab: enter your home (or hotel) Wi-Fi name and password — not the setup password — then tap Save & reboot. Wait for the chart to restart and join your network.\n\n" +
                "3. Lights tab: the Door County lights are already there. Change the list if you want, then tap Save to chart.\n\n" +
                "Done. The lights blink like the real lighthouses. In Settings you can keep them flashing, or populate one by one (dark, then each light in order with the name on OLED or matrix). Use this Help tab for more detail.",
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
                        "Chart address: 192.168.4.1",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Step(
                    title = "2. Give the chart your home Wi-Fi (Settings / Chart settings)",
                    body = "Type your home or hotel Wi-Fi name and password. That is so the chart can reach the internet — not so your phone can join GreatLakes-Setup.\n\n" +
                        "Tap Save & reboot. After it restarts, put your phone back on home Wi-Fi. The chart’s address is now its home-network address, for example http://192.168.1.22. You can find that in your router’s device list.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Step(
                    title = "3. Set your lights (Lights / Lighthouses)",
                    body = "Tap Fetch from chart to see what is already on the chart. A finished Door County chart already has Kewaunee through Rock Island. Save to chart writes your list. List order is the order of the lights on the strip, south to north.",
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
            body = "• Chart address: tap Find chart, or Fetch / Save / Identify. The app tries the box, the last home IP it used, and 192.168.4.1. On success it fills in the working address. LED 1 is the first light on the strip.\n\n" +
                "• Fetch from chart / Reload: loads the list that is already on the chart.\n\n" +
                "• Save to chart / Save list: sends your list to the chart. Do this after you add, skip, reorder, or delete lights.\n\n" +
                "• Use / Skip: Skip leaves that light dark but keeps its place on the strip.\n\n" +
                "• Tap an LED number on the Lights tab (or in the browser) to light only that LED for a few seconds. This works on GreatLakes-Setup at 192.168.4.1, so you can check wiring on a work or hotel network that the chart cannot join.\n\n" +
                "• Drag the handle on the left to reorder. First in the list is the first light on the strip (usually the southernmost). In the browser, use Up / Down.\n\n" +
                "• Add from catalog: pick Lighthouses, Lights, or Buoys, then a shore, or search. Lighthouses are the named towers (Canal North Pierhead counts). Lights are numbered marks and marinas. Front/rear ranges and nearby green/red pairs are one catalog row — they share one LED. The line under the name says whether that LED is one color or green, then red. A few words is enough — st joseph finds St. Joseph. Color, flash, and nearest weather station are already filled in. Tap one to add it.\n\n" +
                "• Add custom: type a name, pick a flash pattern, and optionally a nearby weather station (for example KSUE).\n\n" +
                "• Restore defaults: puts back Kewaunee through Rock Island. Then Save so the chart uses that list.",
        )

        HelpCard(
            title = "Chart tab",
            body = "Bold lighthouse symbols are the lights on your strip. Cream is white, then red or green. A faded lighthouse is skipped. The number is the LED.\n\n" +
                "Faint colored dots are catalog aids not on your list yet. Turn them on with Catalog. Lighthouses shows named towers — Canal North Pierhead, Cana Island, ranges. Lights shows numbered marks, marinas, and breakwaters. All shows both. None / Green / Red / Both is for buoys. Tap a dot to add it, or tap empty water for the nearest catalog aid. New lights go at the end of the list — drag on Lights to match the strip, then Save to chart.\n\n" +
                "Use Map for Google Maps (Satellite is there too), or NOAA for the official ENC nautical chart — depths, channels, and the real aids. Same pins on both. NOAA is not for navigation.\n\n" +
                "Tap a strip lighthouse to Identify that LED. Your phone must be on the same Wi-Fi as the chart. The Chart map needs internet for tiles, so on setup Wi-Fi use the Lights tab instead.\n\n" +
                "Custom lights without coordinates do not appear.",
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
                "• Light show: Keep flashing leaves every light on its real characteristic. Populate one by one goes dark, then lights each lighthouse in list order with the name on OLED or matrix. All lights flash is how many seconds they stay flashing as a set (10 to 1800; 180 is 3 minutes). Each light is how many seconds each lighthouse stays on during the tour (2 to 120). Save after you change this — no reboot needed.\n\n" +
                "• Extra display: LED strip only, OLED (GPIO 16–19), or LED matrix (GPIO 1). The lighthouse strip is always on. Save & reboot after you change this.\n\n" +
                "• LED matrix scroll: Weather only (default) lists lights that currently have rain, fog, snow, lightning, or similar. If none do, GREAT LAKES LIGHTHOUSES scrolls. All lights scrolls every light. Matrix scroll speed is 1 (slow) to 10 (fast). Scroll times is how many times each message crosses the OLED or matrix (1 to 10), then it waits until the text changes. Text enters from the right.\n\n" +
                "• Max brightness is how bright the lights get in a bright room (1 to 30). Min brightness is the dark-room floor (0 = off, 1–2 is a faint glow).\n\n" +
                "• Optional OLED (SSD1306): GPIO 16 data (SDA), 17 clock (SCL), 18 for 3.3 V, 19 for ground. Copy ssd1306.py, writer.py, and sans18.py onto the chart. The screen scrolls the same text as the matrix (IP once, then light names and weather) using the large sans18 font. Matrix scroll speed also controls the OLED.\n\n" +
                "• Optional LED matrix (8x32 WS2812): data on GPIO 1, 5 V and common ground — not from pin 18. Copy led_matrix.py onto the chart. Light names use flight-category color; weather codes use MetarMap colors.\n\n" +
                "• Refresh seconds is how often the chart checks online (30 to 3600). 300 is every 5 minutes.\n\n" +
                "• Beacon pulse on clear weather is a gentle brightness pulse when there is no rain, fog, snow, or similar.\n\n" +
                "• Sleep turns the lights off and on each night. Set the time offset first (Central: -6 in winter, -5 in daylight saving. Eastern: -5 / -4).\n\n" +
                "• Weekend / long off is an extra off stretch, for example Friday evening to Monday morning. It works together with nightly sleep.\n\n" +
                "• Save writes the settings. Save & reboot is the sure way after you change Wi-Fi.\n\n" +
                "In the browser, Chart settings always uses Save & Reboot.",
        )

        HelpCard(
            title = "App updates",
            body = "The phone app checks GitHub for a newer APK. If one is waiting, Help and Settings show App update available. Tap it to download. This is separate from chart firmware.",
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
                "The app has four tabs at the bottom: Lights, Chart, Settings, and Help. In a browser you will see Lighthouses, Chart settings, and Help. Same jobs: light list, Wi-Fi/brightness/sleep, and these instructions. The Chart tab is app-only.",
        )

        Text(
            text = "Troubleshooting",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        HelpCard(
            title = "App / phone / browser",
            body = "• Can’t connect: join GreatLakes-Setup (password door1234) and tap Find chart, or use address 192.168.4.1. Turn off mobile data so the phone stays on that Wi-Fi.\n\n" +
                "• Fetch or Save failed: your phone must be on the same Wi-Fi as the chart. Tap Find chart. On setup Wi-Fi that is GreatLakes-Setup. After setup, both must be on your home Wi-Fi.\n\n" +
                "• Type http:// in front of the address if the page won’t open.\n\n" +
                "• Phone hotspot: with hotel Wi‑Fi off, Find chart and Fetch can work on this phone while the chart is on the hotspot. Weather can use IPv6 on some T-Mobile hotspots. If hotel Wi‑Fi is also on, the app often cannot reach the chart.",
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
