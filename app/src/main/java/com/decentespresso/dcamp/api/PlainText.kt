package com.decentespresso.dcamp.api

/** Cheap HTML→text + entity decoding, ported from iOS PlainText / Inline.decodeEntities. */
object PlainText {
    private val tagRe = Regex("<[^>]+>")
    private val wsRe = Regex("\\s+")
    private val numEntityRe = Regex("&#(\\d+);")

    private val entityMap = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&mdash;" to "—",
        "&ndash;" to "–", "&hellip;" to "…", "&rsquo;" to "’", "&lsquo;" to "‘",
        "&ldquo;" to "“", "&rdquo;" to "”",
    )

    fun strip(html: String): String {
        var s = tagRe.replace(html, " ")
        s = decodeEntities(s)
        return wsRe.replace(s, " ").trim()
    }

    fun decodeEntities(s: String): String {
        if (!s.contains("&")) return s
        var r = s
        for ((k, v) in entityMap) r = r.replace(k, v)
        if (r.contains("&#")) {
            r = numEntityRe.replace(r) { m ->
                val code = m.groupValues[1].toIntOrNull()
                if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else m.value
            }
        }
        return r
    }
}
