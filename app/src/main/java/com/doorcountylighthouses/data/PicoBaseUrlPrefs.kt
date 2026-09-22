package com.doorcountylighthouses.data

import android.content.Context
import com.doorcountylighthouses.pico.PicoUrls

private const val PREFS_NAME = "doorlights_prefs"
private const val KEY_PICO_BASE_URL = "pico_base_url"
private const val KEY_PICO_LAN_URL = "pico_lan_url"
private const val KEY_CHART_NOAA = "chart_use_noaa"
private const val KEY_CHART_CATALOG = "chart_show_catalog"
private const val KEY_CHART_BUOY_FILTER = "chart_buoy_filter"
private const val KEY_CHART_AID_FILTER = "chart_aid_filter"

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

fun loadChartUsesNoaa(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CHART_NOAA, false)

fun saveChartUsesNoaa(context: Context, useNoaa: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_CHART_NOAA, useNoaa)
        .apply()
}

fun loadChartShowsCatalog(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CHART_CATALOG, true)

fun saveChartShowsCatalog(context: Context, show: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_CHART_CATALOG, show)
        .apply()
}

fun loadChartBuoyFilter(context: Context): String {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CHART_BUOY_FILTER, CatalogRepository.BUOY_NONE)
        ?.trim()
        .orEmpty()
        .uppercase()
    return when (raw) {
        CatalogRepository.BUOY_RED,
        CatalogRepository.BUOY_BOTH,
        CatalogRepository.BUOY_GREEN,
        CatalogRepository.BUOY_NONE,
        -> raw
        else -> CatalogRepository.BUOY_NONE
    }
}

fun saveChartBuoyFilter(context: Context, filter: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_CHART_BUOY_FILTER, filter)
        .apply()
}

fun loadChartAidFilter(context: Context): String {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CHART_AID_FILTER, CatalogRepository.AID_TOWERS)
        ?.trim()
        .orEmpty()
        .uppercase()
    return when (raw) {
        CatalogRepository.AID_NONE,
        CatalogRepository.AID_MARKS,
        CatalogRepository.AID_BOTH,
        CatalogRepository.AID_TOWERS,
        -> raw
        else -> CatalogRepository.AID_TOWERS
    }
}

fun saveChartAidFilter(context: Context, filter: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_CHART_AID_FILTER, filter)
        .apply()
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
