package com.decentespresso.dcamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.decentespresso.dcamp.api.DcampRoute

/** Navigation destinations for the single-column stack (mirrors the iOS Dest enum / web app). */
sealed class Dest {
    data class Board(val id: Int) : Dest()
    data class Thread(val id: Int) : Dest()
    data object Chat : Dest()
    data object Dms : Dest()
    data class Dm(val id: Int) : Dest()
    data object Search : Dest()
    data object Find : Dest()
    data object Notifications : Dest()
    data object Settings : Dest()
    data object EmailSummaries : Dest()
    data object Help : Dest()
}

/**
 * App-wide navigation router: a single-column back stack (like iOS NavigationStack path)
 * plus a person-card sheet target. Cross-content links (mentions, cross-posts) route here.
 */
class Router {
    val backStack = mutableStateListOf<Dest>()
    var personID: Int? by mutableStateOf(null)
    var threadSiblings: List<Int> = emptyList()

    /** Per-screen state kept while that screen is in the back stack (see [rememberRetained]),
     *  keyed by depth: 0 = forums home, n = backStack[n-1]. Dropped when the screen is popped. */
    private val retained = mutableMapOf<Int, MutableMap<String, Any?>>()
    fun retainedAt(depth: Int): MutableMap<String, Any?> = retained.getOrPut(depth) { mutableMapOf() }

    fun push(dest: Dest) { backStack.add(dest); retained.remove(backStack.size) }
    fun pop() { if (backStack.isNotEmpty()) { retained.remove(backStack.size); backStack.removeAt(backStack.lastIndex) } }
    fun popToRoot() { backStack.clear(); retained.keys.retainAll { it == 0 } }

    fun open(routeToken: String?) {
        val pid = DcampRoute.personID(routeToken)
        val mid = DcampRoute.messageID(routeToken)
        val dm = DcampRoute.dmID(routeToken)
        when {
            pid != null -> personID = pid
            mid != null -> push(Dest.Thread(mid))
            dm != null -> push(Dest.Dm(dm))
        }
    }
}

val LocalRouter = staticCompositionLocalOf<Router> { error("Router not provided") }

/** The current screen's retained-state map, provided by MainView per back-stack depth. */
val LocalRetained = staticCompositionLocalOf<MutableMap<String, Any?>> { mutableMapOf() }

/**
 * Like `remember`, but the value survives the screen leaving composition while another screen
 * is pushed over it, so going back finds its loaded rows and scroll position as they were.
 */
@Composable
fun <T> rememberRetained(key: String, init: () -> T): T {
    val store = LocalRetained.current
    @Suppress("UNCHECKED_CAST")
    return remember(store) { store.getOrPut(key) { init() } as T }
}
