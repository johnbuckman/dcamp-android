package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.BuildConfig
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.ui.theme.DCPrimaryButton
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.dcCard
import kotlinx.coroutines.launch

/**
 * Browser sign-in (OAuth2 + PKCE); the app never handles the password. In debug builds
 * a collapsible "Dev login (Test server)" email/pw form is also shown, mirroring the iOS
 * DEBUG smoke path, so the app can be verified against the local NaviServer.
 */
@Composable
fun LoginView(session: SessionStore, state: SessionStore.State) {
    val c = DcampTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDev by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(c.bg).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 400.dp).dcCard().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("dcamp", fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.ink)

            Text("Welcome to the Decent Diaspora", fontSize = 21.sp, fontWeight = FontWeight.Black,
                color = c.ink, textAlign = TextAlign.Center)
            Text("Sign in with your existing Decent support account.", fontSize = 15.sp,
                color = c.inkSoft, textAlign = TextAlign.Center)

            state.errorMessage?.let {
                Text(it, fontSize = 13.sp, color = c.danger,
                    textAlign = TextAlign.Center)
            }

            DCPrimaryButton(
                onClick = { scope.launch { session.loginWithBrowser(context) } },
                enabled = !state.loggingIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loggingIn) CircularProgressIndicator(strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 4.dp), color = androidx.compose.ui.graphics.Color.White)
                Text(if (state.loggingIn) "Signing in…" else "Sign in with Decent",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Text("Opens a secure browser. Your password is never entered in the app.",
                fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)

            if (BuildConfig.DEBUG) {
                TextButton(onClick = { showDev = !showDev }) {
                    Text(if (showDev) "Hide dev login" else "Dev login (Test server)", fontSize = 12.sp, color = c.muted)
                }
                if (showDev) {
                    Text("Server: ${AppConfig.serverLabel}", fontSize = 11.sp, color = c.muted)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("email") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("password") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    DCPrimaryButton(
                        onClick = { scope.launch { session.loginEmailPassword(email.trim(), password) } },
                        enabled = !state.loggingIn && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Dev sign in", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
