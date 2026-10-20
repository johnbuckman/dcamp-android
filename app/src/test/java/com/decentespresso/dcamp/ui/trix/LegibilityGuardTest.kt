package com.decentespresso.dcamp.ui.trix

import androidx.compose.ui.graphics.Color
import com.decentespresso.dcamp.ui.theme.DcampColors
import com.decentespresso.dcamp.ui.theme.DcampDarkColors
import com.decentespresso.dcamp.ui.theme.DcampLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TrixInline.legibleFg: author colours must read on whatever background they land on, in both themes. */
class LegibilityGuardTest {
    private val navy = Color(0xFF000080)
    private val yellow = Color(0xFFFFEB3B)

    private fun readable(fg: Color?, bg: Color?, c: DcampColors): Double {
        val back = bg ?: c.panel
        return TrixInline.contrast(TrixInline.legibleFg(fg, bg, c.ink, c.panel) ?: c.ink, back)
    }

    @Test fun navyKeptInLightDroppedInDark() {
        assertEquals(navy, TrixInline.legibleFg(navy, null, DcampLightColors.ink, DcampLightColors.panel))
        assertNull(TrixInline.legibleFg(navy, null, DcampDarkColors.ink, DcampDarkColors.panel))   // → theme ink
    }

    @Test fun highlightWithoutTextColourReadsInDark() {
        // light ink on a yellow highlight was ~1.2:1; must flip to dark text
        assertTrue(readable(null, yellow, DcampDarkColors) >= 4.5)
        assertTrue(readable(null, yellow, DcampLightColors) >= 4.5)
    }

    @Test fun everyNamedColourReadsInBothThemes() {
        val picks = listOf(Color.Red, Color.Blue, Color.Yellow, Color(0xFF16A34A), Color(0xFF9C27B0),
            Color(0xFF795548), Color.Cyan, Color(0xFF3F51B5), Color.Black, Color.White)
        for (c in listOf(DcampLightColors, DcampDarkColors)) for (p in picks) {
            assertTrue("$p dark=${c.dark}", readable(p, null, c) >= 4.5)
            assertTrue("$p on yellow dark=${c.dark}", readable(p, yellow, c) >= 4.5)
        }
    }

    @Test fun mineBubbleWhiteTextContrast() {
        for (c in listOf(DcampLightColors, DcampDarkColors))
            assertTrue("dark=${c.dark}", TrixInline.contrast(Color.White, c.bubbleMine) >= 4.5)
    }
}
