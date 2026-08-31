package com.decentespresso.dcamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.Countries
import com.decentespresso.dcamp.api.SavedFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The forum filter — involvement view + a set of countries — shared across every board
 *  and the thread view, persisted between launches (mirrors the web SPA's FORUM_FILTER).
 *  Compose-state backed for reactive UI. Ported from iOS ForumFilterStore. */
class ForumFilterStore {
    enum class View { popular, posted, commented, mentioned }

    var view by mutableStateOf<View?>(null)
        private set
    val countries = mutableStateListOf<String>()
    var on by mutableStateOf(false)
        private set

    init { load() }

    val selected: Boolean get() = view != null || countries.isNotEmpty()
    val active: Boolean get() = on && selected

    /** Query params for the `messages` action (empty when the filter is off). */
    val messageParams: Map<String, String>
        get() {
            if (!active) return emptyMap()
            val p = mutableMapOf<String, String>()
            when (view) {
                View.popular -> p["popular"] = "1"
                View.posted, View.commented, View.mentioned -> p["mine"] = view!!.name
                null -> {}
            }
            if (countries.isNotEmpty()) p["countries"] = countries.joinToString(",")
            return p
        }

    val label: String
        get() {
            val bits = mutableListOf<String>()
            view?.let {
                bits.add(when (it) {
                    View.popular -> "Popular"; View.posted -> "I posted"
                    View.commented -> "I commented"; View.mentioned -> "I'm mentioned"
                })
            }
            if (countries.isNotEmpty()) {
                val head = countries.take(3).joinToString(", ") { Countries.name(it) }
                bits.add(head + if (countries.size > 3) " +${countries.size - 3}" else "")
            }
            return bits.joinToString(" · ")
        }

    fun toggleView(v: View) { view = if (view == v) null else v; onChanged() }
    fun toggleCountry(code: String) { if (countries.contains(code)) countries.remove(code) else countries.add(code); onChanged() }
    fun hasCountry(code: String) = countries.contains(code)
    fun toggleOn() { if (selected) { on = !on; save() } }
    fun clear() { view = null; countries.clear(); on = false; save() }

    fun applySpec(spec: SavedFilterSpec) {
        view = spec.view?.let { runCatching { View.valueOf(it) }.getOrNull() }
        countries.clear(); countries.addAll(spec.countries)
        on = selected; save()
    }

    val spec: SavedFilterSpec get() = SavedFilterSpec(view?.name, countries.toList())

    private fun onChanged() { on = selected; save() }

    private fun save() {
        AppConfig.forumFilter = Json.encodeToString(SavedFilterSpec.serializer(),
            SavedFilterSpec(view?.name, countries.toList(), on))
    }
    private fun load() {
        val raw = AppConfig.forumFilter
        if (raw.isEmpty()) return
        val s = runCatching { Json.decodeFromString(SavedFilterSpec.serializer(), raw) }.getOrNull() ?: return
        view = s.view?.let { runCatching { View.valueOf(it) }.getOrNull() }
        countries.clear(); countries.addAll(s.countries)
        on = s.on && selected
    }
}

val LocalForumFilter = androidx.compose.runtime.staticCompositionLocalOf { ForumFilterStore() }

@Serializable
data class SavedFilterSpec(val view: String? = null, val countries: List<String> = emptyList(), val on: Boolean = false)

fun SavedFilter.parsedSpec(): SavedFilterSpec =
    runCatching { Json.decodeFromString(SavedFilterSpec.serializer(), spec) }.getOrDefault(SavedFilterSpec())
