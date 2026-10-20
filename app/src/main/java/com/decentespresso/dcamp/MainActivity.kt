package com.decentespresso.dcamp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.OAuthManager
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.ThemeMode
import com.decentespresso.dcamp.ui.views.LoginView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthRedirect(intent)
        setContent {
            val session = Services.session
            val state by session.state.collectAsState()

            LaunchedEffect(Unit) {
                com.decentespresso.dcamp.ui.theme.ThemeState.mode = ThemeMode.from(AppConfig.themeRaw)
                session.start()
            }

            // Load server-driven UI strings once we know the viewer's language.
            LaunchedEffect(state.phase, state.lang) {
                if (state.phase == SessionStore.Phase.LOGGED_IN) {
                    com.decentespresso.dcamp.ui.UIStrings.lang = state.lang
                    com.decentespresso.dcamp.ui.UIStrings.load(Services.api)
                }
            }

            DcampTheme(mode = com.decentespresso.dcamp.ui.theme.ThemeState.mode) {
                // System bars + window background follow the IN-APP theme (enableEdgeToEdge's
                // default follows the system setting, so "Dark" on a light phone gave dark-on-dark
                // status-bar icons, and the light window showed through behind the keyboard).
                val c = DcampTheme.colors
                DisposableEffect(c.dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    val bars = if (c.dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(c.bg.toArgb()))
                    onDispose {}
                }
                when (state.phase) {
                    SessionStore.Phase.LAUNCHING -> LaunchScreen()
                    SessionStore.Phase.LOGGED_OUT -> LoginView(session, state)
                    SessionStore.Phase.LOGGED_IN -> com.decentespresso.dcamp.ui.views.MainView(session, state)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == AppConfig.oauthRedirectScheme && data.host == "auth") {
            OAuthManager.onRedirect(data)
        }
    }
}

@Composable
private fun LaunchScreen() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(DcampTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(color = DcampTheme.colors.accent) }
}

/** Phase-1 placeholder: proves auth + bootstrap decoded. Replaced by MainView in Phase 2. */
@Composable
private fun HomePlaceholder(state: SessionStore.State) {
    val c = DcampTheme.colors
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(c.bg).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("dcamp", fontSize = 30.sp, fontWeight = FontWeight.Black, color = c.accentInk)
            Text("Signed in as ${state.me?.name ?: "?"}", fontSize = 16.sp, color = c.ink)
            Text("${state.boards.size} forums · ${state.categories.size} categories", fontSize = 14.sp, color = c.inkSoft)
            Text("canPost=${state.canPost} · admin=${state.isAdmin} · bcLinked=${state.bcLinked}",
                fontSize = 12.sp, color = c.muted)
            Text("lang=${state.lang} · unread ping=${state.pingUnread} chat=${state.chatUnread}",
                fontSize = 12.sp, color = c.muted)
        }
    }
}
