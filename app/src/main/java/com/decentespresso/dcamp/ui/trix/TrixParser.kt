package com.decentespresso.dcamp.ui.trix

import com.decentespresso.dcamp.api.AppConfig

/**
 * Parses the Trix HTML dcamp stores into native block structures. Ported from iOS
 * TrixParser.swift. Blocks keep raw inner-HTML strings for text; the renderer styles
 * them (and wires links) at composition time via [TrixInline].
 */
sealed class TrixBlock {
    data class Paragraph(val html: String) : TrixBlock()
    data class Heading(val html: String, val level: Int) : TrixBlock()
    data class Quote(val children: List<TrixBlock>) : TrixBlock()
    data class ListBlock(val itemsHtml: List<String>, val ordered: Boolean) : TrixBlock()
    data class Image(val url: String) : TrixBlock()
    data class YouTube(val id: String) : TrixBlock()
    data class Table(val rowsHtml: List<List<String>>) : TrixBlock()
    data object Rule : TrixBlock()
}

object TrixParser {

    fun parse(html: String): List<TrixBlock> {
        val blocks = mutableListOf<TrixBlock>()
        val scanner = BlockScanner(html)
        var node = scanner.next()
        while (node != null) {
            val (tag, inner, attrs) = node
            when (tag) {
                "p" -> {
                    val img = firstImageURL(inner)
                    val yt = youtubeID(inner, attrs)
                    when {
                        img != null -> blocks.add(TrixBlock.Image(img))
                        yt != null -> blocks.add(TrixBlock.YouTube(yt))
                        else -> if (hasText(inner)) blocks.add(TrixBlock.Paragraph(inner))
                    }
                }
                "h1", "h2", "h3" -> blocks.add(TrixBlock.Heading(inner, tag.drop(1).toIntOrNull() ?: 2))
                "ul", "ol" -> {
                    val items = listItems(inner)
                    if (items.isNotEmpty()) blocks.add(TrixBlock.ListBlock(items, tag == "ol"))
                }
                "blockquote" -> blocks.add(TrixBlock.Quote(parse(inner)))
                "figure", "div" -> {
                    val img = firstImageURL(inner)
                    val yt = youtubeID(inner, attrs)
                    when {
                        inner.contains("<hr", ignoreCase = true) -> blocks.add(TrixBlock.Rule)
                        img != null -> blocks.add(TrixBlock.Image(img))
                        yt != null -> blocks.add(TrixBlock.YouTube(yt))
                        else -> if (hasText(inner)) blocks.add(TrixBlock.Paragraph(inner))
                    }
                }
                "table" -> {
                    val rows = tableRows(inner)
                    if (rows.isNotEmpty()) blocks.add(TrixBlock.Table(rows))
                }
                "hr" -> blocks.add(TrixBlock.Rule)
                "img" -> attrValue(attrs, "src")?.let { AppConfig.absUrl(it) }?.let { blocks.add(TrixBlock.Image(it)) }
                "iframe" -> youtubeID("", attrs)?.let { blocks.add(TrixBlock.YouTube(it)) }
                else -> if (hasText(inner)) blocks.add(TrixBlock.Paragraph(inner))
            }
            node = scanner.next()
        }
        if (blocks.isEmpty() && hasText(html)) blocks.add(TrixBlock.Paragraph(html))
        return blocks
    }

    private fun hasText(inner: String): Boolean =
        com.decentespresso.dcamp.api.PlainText.strip(inner).isNotEmpty() ||
            inner.contains("<img", ignoreCase = true)

    private val liRe = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val trRe = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cellRe = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun listItems(inner: String): List<String> =
        liRe.findAll(inner).map { it.groupValues[1] }.filter { hasText(it) }.toList()

    private fun tableRows(inner: String): List<List<String>> =
        trRe.findAll(inner).map { m ->
            cellRe.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
        }.filter { it.isNotEmpty() }.toList()

    private val imgSrcRe = Regex("<img[^>]+src=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private fun firstImageURL(html: String): String? =
        imgSrcRe.find(html)?.groupValues?.get(1)?.let { AppConfig.absUrl(it) }

    private val ytRe = Regex("(?:youtube\\.com/(?:embed/|watch\\?v=)|youtu\\.be/)([A-Za-z0-9_-]{6,})", RegexOption.IGNORE_CASE)
    private fun youtubeID(html: String, attrs: String): String? {
        for (hay in listOf(attrs, html)) ytRe.find(hay)?.let { return it.groupValues[1] }
        return null
    }

    fun attrValue(attrs: String, name: String): String? =
        Regex("$name=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
}

/**
 * Splits HTML into top-level (tag, innerHTML, attrs) tuples in document order.
 * Ported from iOS BlockScanner. Nestable tags (blockquote/ul/ol/table) find their
 * LAST balanced close so nested content re-parses correctly.
 */
private class BlockScanner(private val s: String) {
    private var i = 0
    private val blockTags = setOf("p", "ul", "ol", "h1", "h2", "h3", "blockquote", "figure", "div", "hr", "img", "iframe", "table")

    data class Node(val tag: String, val inner: String, val attrs: String)

    fun next(): Node? {
        while (i < s.length) {
            val lt = s.indexOf('<', i)
            if (lt < 0) { i = s.length; return null }
            val gt = s.indexOf('>', lt)
            if (gt < 0) { i = s.length; return null }
            val tagContent = s.substring(lt + 1, gt)
            i = gt + 1
            val tag = tagName(tagContent)
            if (tag !in blockTags) continue
            val attrs = tagContent

            if (tag == "hr" || tag == "img" || tagContent.endsWith("/")) {
                return Node(tag, "", attrs)
            }
            val close = "</$tag>"
            val cr = s.indexOf(close, i, ignoreCase = true)
            if (cr < 0) {
                val inner = s.substring(i); i = s.length
                return Node(tag, inner, attrs)
            }
            var end = cr
            if (tag == "blockquote" || tag == "ul" || tag == "ol" || tag == "table") {
                end = lastBalancedClose("<$tag", close, i) ?: cr
            }
            val inner = s.substring(i, end)
            i = end + close.length
            return Node(tag, inner, attrs)
        }
        return null
    }

    private fun lastBalancedClose(open: String, close: String, start: Int): Int? {
        var depth = 1
        var pos = start
        while (pos < s.length) {
            val o = s.indexOf(open, pos, ignoreCase = true)
            val c = s.indexOf(close, pos, ignoreCase = true)
            if (c < 0) return null
            if (o in 0 until c) { depth++; pos = o + open.length }
            else { depth--; pos = c + close.length; if (depth == 0) return c }
        }
        return null
    }

    private fun tagName(content: String): String {
        var t = content
        if (t.startsWith("/")) t = t.substring(1)
        val sb = StringBuilder()
        for (ch in t) {
            if (ch == ' ' || ch == '>' || ch == '/' || ch == '\n') break
            sb.append(ch)
        }
        return sb.toString().lowercase()
    }
}
