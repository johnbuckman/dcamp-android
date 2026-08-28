package com.decentespresso.dcamp.api

/**
 * Parses dcamp internal routes ("/message/123", "#/message/123?c=4", "#/p/5",
 * "/dm/12", clean "/dcamp/<board>/<slug>") to targets. Ported from iOS DcampRoute.
 */
object DcampRoute {
    private fun digitsAfter(route: String, marker: String): Int? {
        val i = route.indexOf(marker)
        if (i < 0) return null
        val rest = route.substring(i + marker.length)
        val digits = rest.takeWhile { it.isDigit() }
        return digits.toIntOrNull()
    }

    fun messageID(route: String?): Int? = route?.let { digitsAfter(it, "message/") }

    fun personID(route: String?): Int? = route?.let { digitsAfter(it, "/p/") }

    fun commentID(route: String?): Int? = route?.let { digitsAfter(it, "c=") }

    fun dmID(route: String?): Int? {
        if (route == null) return null
        for (prefix in listOf("dm/", "pings/", "ping/")) {
            digitsAfter(route, prefix)?.let { return it }
        }
        return null
    }

    /** Split a clean message URL "/dcamp/<board>/<slug>[/<cid>]" into (board slug, message slug). */
    fun cleanMsgParts(route: String?): Pair<String, String>? {
        if (route == null) return null
        var path = route
        if (path.startsWith("/support")) path = path.removePrefix("/support")
        if (!path.startsWith("/dcamp/")) return null
        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.size < 3 || parts[0] != "dcamp") return null
        return parts[1] to parts[2]
    }
}
