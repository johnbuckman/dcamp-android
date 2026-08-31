package com.decentespresso.dcamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
}

/**
 * App-wide navigation router: a single-column back stack (like iOS NavigationStack path)
 * plus a person-card sheet target. Cross-content links (mentions, cross-posts) route here.
 */
class Router {
    val backStack = mutableStateListOf<Dest>()
    var personID: Int? by mutableStateOf(null)
    var threadSiblings: List<Int> = emptyList()

    fun push(dest: Dest) { backStack.add(dest) }
    fun pop() { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }
    fun popToRoot() { backStack.clear() }

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
