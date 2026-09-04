package com.doorcountylighthouses.data

import android.content.Context
import com.doorcountylighthouses.pico.PicoUrls

private const val PREFS_NAME = "doorlights_prefs"
private const val KEY_PICO_BASE_URL = "pico_base_url"
private const val KEY_PICO_LAN_URL = "pico_lan_url"

const val DEFAULT_PICO_BASE_URL = "http://192.168.4.1"

fun loadPicoBaseUrl(context: Context): String {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PICO_BASE_URL, null)?.trim().orEmpty()
    if (saved.isEmpty()) return DEFAULT_PICO_BASE_URL
    return PicoUrls.normalize(saved)
}

fun savePicoBaseUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_PICO_BASE_URL, url.trim())
        .apply()
}

fun loadPicoLanUrl(context: Context): String? {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PICO_LAN_URL, null)?.trim().orEmpty()
    if (saved.isEmpty()) return null
    val url = PicoUrls.normalize(saved)
    return if (PicoUrls.isSetup(url)) null else url
}

fun rememberWorkingPicoUrl(context: Context, requestUrl: String): String {
    val stored = PicoUrls.preferredOrigin(requestUrl)
    savePicoBaseUrl(context, stored)
    if (!PicoUrls.isSetup(stored)) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PICO_LAN_URL, stored)
            .apply()
    }
    return stored
}
