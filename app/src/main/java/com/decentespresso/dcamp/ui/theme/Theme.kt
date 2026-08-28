package com.decentespresso.dcamp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User-selectable appearance (matches the web's dcamp_theme ui-pref; "" == system). */
enum class ThemeMode(val serverValue: String, val label: String) {
    SYSTEM("", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun from(raw: String?): ThemeMode = when (raw) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}

/** Layout constants mirrored from the iOS `DC` enum / dcamp.css `--maxw`. */
object DC {
    val radius = 14.dp
    val maxContentWidth = 1560.dp
}

/** Access dcamp design tokens: `DcampTheme.colors.accent`. */
object DcampTheme {
    val colors: DcampColors
        @Composable @ReadOnlyComposable get() = LocalDcampColors.current
}

private val DcampTypography = Typography(
    // Body defaults tuned to match the iOS 14–15pt content sizing.
    bodyLarge = Typography().bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun DcampTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dc = if (dark) DcampDarkColors else DcampLightColors

    // Map dcamp tokens onto a Material3 scheme so stock M3 components inherit the palette.
    val m3 = if (dark) {
        darkColorScheme(
            primary = dc.accent, onPrimary = androidx.compose.ui.graphics.Color.White,
            background = dc.bg, onBackground = dc.ink,
            surface = dc.panel, onSurface = dc.ink,
            surfaceVariant = dc.panel, onSurfaceVariant = dc.inkSoft,
            outline = dc.lineStrong, error = androidx.compose.ui.graphics.Color(0xFFE5484D),
        )
    } else {
        lightColorScheme(
            primary = dc.accent, onPrimary = androidx.compose.ui.graphics.Color.White,
            background = dc.bg, onBackground = dc.ink,
            surface = dc.panel, onSurface = dc.ink,
            surfaceVariant = dc.panel, onSurfaceVariant = dc.inkSoft,
            outline = dc.lineStrong, error = androidx.compose.ui.graphics.Color(0xFFDC2626),
        )
    }

    CompositionLocalProvider(LocalDcampColors provides dc) {
        MaterialTheme(colorScheme = m3, typography = DcampTypography, content = content)
    }
}
