package com.decentespresso.dcamp.ui.trix

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.ui.theme.DcampColors
import com.decentespresso.dcamp.ui.theme.hexColor

/**
 * Converts an inline HTML fragment (contents of a <p>, <li>, <h2>, …) into a styled
 * AnnotatedString: bold/italic/code/underline, colors, links, and dcamp @mention /
 * cross-post links (routed via [onLink]). Ported from iOS TrixInline.Inline.
 */
object TrixInline {

    private data class Colors(val fg: Color?, val bg: Color?)

    fun parse(html: String, colors: DcampColors, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
        var bold = false; var italic = false; var code = false; var under = false
        val hrefStack = ArrayDeque<String>()
        val colorStack = ArrayDeque<Colors>()

        fun spanStyle(): SpanStyle {
            val href = hrefStack.lastOrNull()
            var fg: Color? = colorStack.lastOrNull { it.fg != null }?.fg
            val bg: Color? = colorStack.lastOrNull { it.bg != null }?.bg
            var deco = if (under) TextDecoration.Underline else null
            var weight = if (bold) FontWeight.Bold else null
            if (href != null) {
                val isMention = href.startsWith("#/p/")
                fg = if (isMention) colors.accentInk else colors.link
                if (isMention) weight = FontWeight.Bold else deco = TextDecoration.Underline
            }
            return SpanStyle(
                color = fg ?: Color.Unspecified,
                background = bg ?: Color.Unspecified,
                fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else null,
                fontFamily = if (code) FontFamily.Monospace else null,
                textDecoration = deco,
            )
        }

        fun appendText(raw: String) {
            val text = PlainText.decodeEntities(raw)
            if (text.isEmpty()) return
            val href = hrefStack.lastOrNull()
            if (href != null) {
                withLink(LinkAnnotation.Clickable(
                    tag = href,
                    styles = TextLinkStyles(style = spanStyle()),
                    linkInteractionListener = { onLink(href) },
                )) { append(text) }
            } else {
                withStyle(spanStyle()) { append(text) }
            }
        }

        var i = 0
        while (i < html.length) {
            val lt = html.indexOf('<', i)
            if (lt < 0) { appendText(html.substring(i)); break }
            if (lt > i) appendText(html.substring(i, lt))
            val gt = html.indexOf('>', lt)
            if (gt < 0) break
            val tagContent = html.substring(lt + 1, gt)
            i = gt + 1

            val closing = tagContent.startsWith("/")
            when (tagName(tagContent)) {
                "strong", "b" -> bold = !closing
                "em", "i" -> italic = !closing
                "code" -> code = !closing
                "u" -> under = !closing
                "br" -> append("\n")
                "a" -> if (closing) { if (hrefStack.isNotEmpty()) hrefStack.removeLast() }
                       else hrefStack.addLast(TrixParser.attrValue(tagContent, "href") ?: "")
                "span" -> if (closing) { if (colorStack.isNotEmpty()) colorStack.removeLast() }
                          else colorStack.addLast(cssColors(TrixParser.attrValue(tagContent, "style") ?: ""))
                "mark" -> if (closing) { if (colorStack.isNotEmpty()) colorStack.removeLast() }
                          else {
                              val c = cssColors(TrixParser.attrValue(tagContent, "style") ?: "")
                              colorStack.addLast(Colors(c.fg, c.bg ?: Color(0x59FFEB3B)))
                          }
            }
        }
    }

    /** Is this href an internal dcamp route (vs an external URL to open in a browser)? */
    fun isInternal(href: String): Boolean =
        href.startsWith("#/") || href.startsWith("/dcamp/") || href.startsWith("/support/dcamp/")

    /** The route token for an internal href (strip a leading '#'). */
    fun routeToken(href: String): String = if (href.startsWith("#/")) href.substring(1) else href

    private fun cssColors(style: String): Colors {
        fun value(prop: String): Color? {
            val re = Regex("(?:^|;)\\s*$prop\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            return re.find(style)?.groupValues?.get(1)?.trim()?.let { cssColor(it) }
        }
        return Colors(fg = value("color"), bg = value("background-color") ?: value("background"))
    }

    private val named = mapOf(
        "red" to Color.Red, "green" to Color(0xFF16A34A), "blue" to Color.Blue,
        "orange" to Color(0xFFFF9800), "yellow" to Color.Yellow, "purple" to Color(0xFF9C27B0),
        "pink" to Color(0xFFE91E63), "black" to Color.Black, "white" to Color.White,
        "gray" to Color.Gray, "grey" to Color.Gray, "teal" to Color(0xFF009688),
        "brown" to Color(0xFF795548), "cyan" to Color.Cyan, "indigo" to Color(0xFF3F51B5),
    )

    // A near-black or near-white author colour carries no real colour intent and
    // breaks in one theme (near-black picked in light mode is invisible on the dark
    // background, and vice-versa). Dropping these lets the text inherit the theme
    // colour, legible in BOTH modes — the native counterpart of the web legibility
    // guard. (We don't resolve the exact per-run background here, so this covers the
    // common extreme case rather than a full contrast check.)
    private fun isIllegibleExtreme(r: Double, g: Double, b: Double): Boolean =
        (r < 40 && g < 40 && b < 40) || (r > 220 && g > 220 && b > 220)

    private fun hexRgb(hex: String): Triple<Double, Double, Double>? {
        var h = hex.removePrefix("#")
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        if (h.length != 6) return null
        val n = h.toLongOrNull(16) ?: return null
        return Triple(((n shr 16) and 0xff).toDouble(), ((n shr 8) and 0xff).toDouble(), (n and 0xff).toDouble())
    }

    private fun cssColor(raw: String): Color? {
        val v = raw.trim().lowercase()
        if (v.startsWith("#")) {
            hexRgb(v)?.let { if (isIllegibleExtreme(it.first, it.second, it.third)) return null }
            return hexColor(v)
        }
        if (v.startsWith("rgb")) {
            val nums = v.split(Regex("[^0-9.]")).filter { it.isNotEmpty() }
            if (nums.size >= 3) {
                val r = nums[0].toDoubleOrNull() ?: return null
                val g = nums[1].toDoubleOrNull() ?: return null
                val b = nums[2].toDoubleOrNull() ?: return null
                // near-black/near-white (e.g. the composer's default text colour) → inherit theme
                if (isIllegibleExtreme(r, g, b)) return null
                val a = if (nums.size >= 4) nums[3].toFloatOrNull() ?: 1f else 1f
                return Color((r / 255).toFloat(), (g / 255).toFloat(), (b / 255).toFloat(), a)
            }
            return null
        }
        return named[v]
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
