package com.decentespresso.dcamp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Design tokens mirrored from dcamp's dcamp.css (:root light "paper" vs
 * :root[data-theme=dark]). Ported 1:1 from the iOS app's DcampTheme.swift.
 */
@Immutable
data class DcampColors(
    val bg: Color,
    val panel: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentInk: Color,
    val link: Color,
    val decentBg: Color,
    val bubbleOther: Color,
    /** Sender's own DM/chat bubble (white text on it must hold ≥4.5:1). */
    val bubbleMine: Color,
    /** Destructive / error TEXT (sign out, login errors); count badges keep their solid red. */
    val danger: Color,
    /** DM purple used as a text tint (search type pill). */
    val dm: Color,
    /** True for the dark palette (WebViews and system bars need the bare flag). */
    val dark: Boolean = false,
)

val DcampLightColors = DcampColors(
    bg          = Color(0xFFFDF9F3),
    panel       = Color(0xFFFFFFFF),
    ink         = Color(0xFF1C1C1C),
    inkSoft     = Color(0xFF6B6B6B),
    muted       = Color(0xFF8A8579),
    line        = Color(0xFFECE6DB),
    lineStrong  = Color(0xFFDDD5C7),
    accent      = Color(0xFF16A34A),
    accentInk   = Color(0xFF0F7A37),
    link        = Color(0xFF1F6FD6),
    decentBg    = Color(0xFF1C1C1C),
    bubbleOther = Color(0xFFF0EDE5),
    bubbleMine  = Color(0xFF1F6FD6),   // = link; white 4.9:1
    danger      = Color(0xFFDC2626),
    dm          = Color(0xFF7A48CC),
)

val DcampDarkColors = DcampColors(
    bg          = Color(0xFF1A1712),
    panel       = Color(0xFF24201A),
    ink         = Color(0xFFF2EFE9),
    inkSoft     = Color(0xFFB8B2A8),
    muted       = Color(0xFF968F82),
    line        = Color(0xFF332F28),
    lineStrong  = Color(0xFF474137),
    accent      = Color(0xFF2C8A54),
    accentInk   = Color(0xFF60C887),
    link        = Color(0xFF66A3EC),
    decentBg    = Color(0xFF4D473C),
    bubbleOther = Color(0xFF373228),
    bubbleMine  = Color(0xFF2F6CBF),   // NOT link (#66A3EC): white on it was 2.6:1
    danger      = Color(0xFFF06A6E),
    dm          = Color(0xFFB194EE),
    dark        = true,
)

val LocalDcampColors = staticCompositionLocalOf { DcampLightColors }

/** "RRGGBB" (no alpha) for passing a token to a WebView. */
fun Color.hex6(): String = "%06X".format(toArgb() and 0xFFFFFF)
