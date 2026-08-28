package com.decentespresso.dcamp.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.decentespresso.dcamp.api.Person

/** Circular avatar: async image when available, else a colored initials circle. */
@Composable
fun Avatar(
    person: Person?,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    val url = person?.avatarUrl
    var mod = Modifier.size(size).clip(CircleShape)
    if (onClick != null && (person?.id ?: 0) > 0) mod = mod.clickable { onClick() }

    Box(mod, contentAlignment = Alignment.Center) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = person?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { InitialsCircle(person, size) },
                error = { InitialsCircle(person, size) },
            )
        } else {
            InitialsCircle(person, size)
        }
    }
}

@Composable
private fun InitialsCircle(person: Person?, size: Dp) {
    val bg = hexColor(person?.avatarColor) ?: Color(0xFF9E9E9E)
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            person?.initials ?: "?",
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
