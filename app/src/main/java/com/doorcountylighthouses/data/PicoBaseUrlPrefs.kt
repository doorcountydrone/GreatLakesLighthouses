package com.doorcountylighthouses.data

import android.content.Context

private const val PREFS_NAME = "doorlights_prefs"
private const val KEY_PICO_BASE_URL = "pico_base_url"

const val DEFAULT_PICO_BASE_URL = "http://192.168.4.1"

fun loadPicoBaseUrl(context: Context): String {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PICO_BASE_URL, null)?.trim().orEmpty()
    if (saved.isEmpty()) return DEFAULT_PICO_BASE_URL
    return if (saved.startsWith("http://", ignoreCase = true) || saved.startsWith("https://", ignoreCase = true)) {
        saved
    } else {
        "http://$saved"
    }
}

fun savePicoBaseUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_PICO_BASE_URL, url.trim())
        .apply()
}
