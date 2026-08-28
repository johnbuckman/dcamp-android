package com.decentespresso.dcamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.ui.theme.DcampTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DcampTheme {
                RootScaffold()
            }
        }
    }
}

@Composable
private fun RootScaffold() {
    // Placeholder shell — replaced by LoginView / MainView in phase 1–2.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("dcamp", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = DcampTheme.colors.accentInk)
    }
}
