package com.decentespresso.dcamp.ui.theme

import androidx.compose.ui.graphics.Color

/** Color from "#rrggbb" (iOS Color(hex:)). */
fun hexColor(hex: String?): Color? {
    var s = hex ?: return null
    if (s.isEmpty()) return null
    if (s.startsWith("#")) s = s.substring(1)
    if (s.length != 6) return null
    val v = s.toLongOrNull(16) ?: return null
    return Color(
        red = ((v shr 16) and 0xFF) / 255f,
        green = ((v shr 8) and 0xFF) / 255f,
        blue = (v and 0xFF) / 255f,
        alpha = 1f,
    )
}

/** Relative time from Unix epoch seconds, abbreviated (mirrors iOS RelativeTime). */
object RelativeTime {
    fun string(epoch: Int?): String {
        if (epoch == null || epoch <= 0) return ""
        val now = System.currentTimeMillis() / 1000
        val diff = now - epoch
        val future = diff < 0
        val s = kotlin.math.abs(diff)
        val label = when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86400 -> "${s / 3600}h"
            s < 604800 -> "${s / 86400}d"
            s < 2629800 -> "${s / 604800}w"
            s < 31557600 -> "${s / 2629800}mo"
            else -> "${s / 31557600}y"
        }
        return if (future) "in $label" else "$label ago"
    }
}
