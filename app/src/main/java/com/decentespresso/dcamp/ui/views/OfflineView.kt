package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DcampTheme

/** Full-screen "you're offline" panel with a retry button — mirrors iOS OfflineView. */
@Composable
fun OfflineView(onRetry: () -> Unit) {
    val c = DcampTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = c.muted, modifier = Modifier.size(56.dp))
        Text(T("You're offline"), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp))
        Text(T("dcamp needs an internet connection."), color = c.muted, fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(T("Try again"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp)
                .background(c.accent, RoundedCornerShape(10.dp))
                .clickable { onRetry() }
                .padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
