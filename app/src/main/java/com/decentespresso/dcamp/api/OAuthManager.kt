package com.decentespresso.dcamp.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Drives browser sign-in (OAuth2 Authorization Code + PKCE) against /support/oauth2.
 * Opens the authorize page in a Chrome Custom Tab (shares the browser's cookies, so an
 * already-logged-in member gets one-tap SSO) and captures the dcamp://auth?code=… redirect.
 * Ported from iOS OAuthService.
 */
object OAuthManager {

    data class AuthResult(val code: String, val verifier: String)

    private class Pending(val verifier: String, val state: String, val deferred: CompletableDeferred<AuthResult>)

    @Volatile private var pending: Pending? = null

    /** Launch the Custom Tab and suspend until the redirect arrives (or the flow is cancelled). */
    suspend fun authorize(context: Context): AuthResult {
        val verifier = randomUrlSafe(32)
        val challenge = s256Challenge(verifier)
        val state = randomUrlSafe(16)
        val deferred = CompletableDeferred<AuthResult>()
        pending = Pending(verifier, state, deferred)

        val authorize = Uri.parse(AppConfig.oauthAuthorizeUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", AppConfig.oauthClientId)
            .appendQueryParameter("redirect_uri", AppConfig.oauthRedirectUri)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
        intent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, authorize)

        return deferred.await()
    }

    /** Called from MainActivity.onNewIntent when the dcamp://auth redirect fires. */
    fun onRedirect(uri: Uri) {
        val p = pending ?: return
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code.isNullOrEmpty() || state != p.state) {
            p.deferred.completeExceptionally(ApiException("Sign-in didn’t complete."))
        } else {
            p.deferred.complete(AuthResult(code, p.verifier))
        }
        pending = null
    }

    /** Called if the user backs out of the Custom Tab without completing sign-in. */
    fun cancelIfPending() {
        pending?.deferred?.completeExceptionally(CancellationSignal())
        pending = null
    }

    class CancellationSignal : Exception("cancelled")

    // MARK: PKCE
    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun s256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
