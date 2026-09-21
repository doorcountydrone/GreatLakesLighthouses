package com.doorcountylighthouses.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.doorcountylighthouses.data.AppUpdate
import com.doorcountylighthouses.data.AppUpdateChecker
import com.doorcountylighthouses.ui.theme.Amber
import com.doorcountylighthouses.ui.theme.Navy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAppUpdate(): AppUpdate? {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    LaunchedEffect(Unit) {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        if (installed.isBlank()) return@LaunchedEffect
        update = withContext(Dispatchers.IO) { AppUpdateChecker.check(installed) }
    }
    return update
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateBanner(update: AppUpdate, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { runCatching { uriHandler.openUri(update.downloadUrl) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Amber),
    ) {
        Text(
            text = "App update available — v${update.latestVersion}",
            style = MaterialTheme.typography.titleSmall,
            color = Navy,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
        )
        Text(
            text = "Tap to download the APK from GitHub.",
            style = MaterialTheme.typography.bodySmall,
            color = Navy,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        )
    }
}
