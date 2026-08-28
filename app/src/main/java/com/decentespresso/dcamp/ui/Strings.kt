package com.decentespresso.dcamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.decentespresso.dcamp.api.DcampApi
import com.decentespresso.dcamp.api.uiStrings

/**
 * Server-driven UI strings (ui_map). [t] returns the localized string for the viewer's
 * language, falling back to the English default. Loaded once after login. Ported from
 * iOS UIStrings; backed by Compose snapshot state so any composable calling T() recomposes
 * when the map (re)loads.
 */
object UIStrings {
    private var map by mutableStateOf<Map<String, String>>(emptyMap())
    var lang by mutableStateOf("en")

    suspend fun load(api: DcampApi) {
        val l = lang
        if (l == "en" || l.isEmpty()) { map = emptyMap(); return }
        // 1. Pull the server's FULL ui_map (board names/descriptions + registered chrome).
        val m = api.uiMap().toMutableMap()
        // 2. Overlay the app's own catalog (registered + translated on demand, cached server-side).
        val catalog = api.uiStrings(UIStringCatalog.all, l)
        for ((k, v) in catalog) m[k] = v
        map = m
    }

    fun t(english: String): String {
        val l = lang
        if (l == "en" || l.isEmpty()) return english
        return map[english] ?: english
    }
}

/** Localize a UI-chrome string literal. Use in composables: `Text(T("Forums"))`. */
fun T(english: String): String = UIStrings.t(english)

/** Fill {tokens} in a (possibly translated) template. */
fun T(english: String, vararg subs: Pair<String, String>): String {
    var s = UIStrings.t(english)
    for ((k, v) in subs) s = s.replace("{$k}", v)
    return s
}
