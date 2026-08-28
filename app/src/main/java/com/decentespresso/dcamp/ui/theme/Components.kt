package com.decentespresso.dcamp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** White rounded panel with dcamp's warm border + soft shadow (iOS .dcCard). */
@Composable
fun Modifier.dcCard(): Modifier {
    val c = DcampTheme.colors
    return this
        .shadow(6.dp, RoundedCornerShape(DC.radius), clip = false, ambientColor = Color(0x14291E0A), spotColor = Color(0x14291E0A))
        .clip(RoundedCornerShape(DC.radius))
        .background(c.panel)
        .border(1.dp, c.line, RoundedCornerShape(DC.radius))
}

/** Green primary button used app-wide (Continue, Send, Sign in). */
@Composable
fun DCPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = DcampTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White,
            disabledContainerColor = c.accent.copy(alpha = 0.5f), disabledContentColor = Color.White),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Uppercase muted section label ("OTHER", "Forums"). */
@Composable
fun DCSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = DcampTheme.colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
}
