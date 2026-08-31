package com.decentespresso.dcamp.api

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** App-wide session + bootstrap state. Drives the login gate and holds the board list. */
class SessionStore(private val api: DcampApi, private val deviceID: String) {

    enum class Phase { LAUNCHING, LOGGED_OUT, LOGGED_IN }

    data class State(
        val phase: Phase = Phase.LAUNCHING,
        val me: Person? = null,
        val boards: List<Board> = emptyList(),
        val categories: List<Category> = emptyList(),
        val canPost: Boolean = false,
        val isAdmin: Boolean = false,
        val bcLinked: Boolean = false,
        val showRegions: Boolean = false,
        val showRoasters: Boolean = false,
        val pingUnread: Int = 0,
        val chatUnread: Int = 0,
        val lang: String = "en",
        val loggingIn: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** ApiException.code from the most recent loadBootstrap failure (null on success). */
    var lastErrorCode: String? = null
        private set

    /** Called once on launch: resume a saved token or show the login screen. */
    suspend fun start() {
        if (api.isAuthenticated) {
            if (!loadBootstrap()) {
                // Only a real auth rejection (401/403) means the SAVED token is stale → log out.
                // A network/offline failure must NOT nuke the session: keep the token, enter the
                // app optimistically (the offline overlay shows), and reload once back online.
                if (lastErrorCode == "http401" || lastErrorCode == "http403") {
                    api.setToken(null)
                    _state.update { it.copy(phase = Phase.LOGGED_OUT) }
                } else {
                    _state.update { it.copy(phase = Phase.LOGGED_IN) }
                }
            }
        } else {
            _state.update { it.copy(phase = Phase.LOGGED_OUT) }
        }
    }

    /** Dev/Test login by email+pw (mirrors the iOS DEBUG smoke path). */
    suspend fun loginEmailPassword(email: String, password: String) {
        _state.update { it.copy(loggingIn = true, errorMessage = null) }
        try {
            api.login(email, password, deviceID)
            if (!loadBootstrap()) {
                _state.update { it.copy(errorMessage = it.errorMessage ?: "Signed in, but couldn’t load the forum.") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = (e as? ApiException)?.messageText ?: e.message) }
        } finally {
            _state.update { it.copy(loggingIn = false) }
        }
    }

    /** Production browser sign-in (OAuth2 + PKCE). The password never touches the app. */
    suspend fun loginWithBrowser(context: Context) {
        _state.update { it.copy(loggingIn = true, errorMessage = null) }
        try {
            val result = OAuthManager.authorize(context)
            api.exchangeOAuth(result.code, result.verifier, deviceID)
            // A first bootstrap can transiently fail while the browser tab is dismissing;
            // retry once and NEVER drop the freshly-minted token.
            if (!loadBootstrap()) {
                kotlinx.coroutines.delay(500)
                if (!loadBootstrap()) {
                    _state.update { it.copy(errorMessage = it.errorMessage ?: "Signed in, but couldn’t load the forum. Pull to refresh.") }
                }
            }
        } catch (_: OAuthManager.CancellationSignal) {
            // User dismissed the browser — not an error.
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = (e as? ApiException)?.messageText ?: e.message) }
        } finally {
            _state.update { it.copy(loggingIn = false) }
        }
    }

    /** Load the bootstrap payload into session state. Returns success; never drops the token. */
    suspend fun loadBootstrap(): Boolean {
        return try {
            val b: Bootstrap = api.call("bootstrap")
            b.lang?.let { AppConfig.lang = it }
            b.dcampTheme?.let { AppConfig.themeRaw = ThemeModeValue.normalize(it) }
            _state.update {
                it.copy(
                    phase = Phase.LOGGED_IN,
                    me = b.me,
                    boards = b.projects,
                    categories = b.categories ?: emptyList(),
                    canPost = b.canPostBool,
                    isAdmin = b.admin,
                    bcLinked = b.bcLinkedBool,
                    showRegions = (b.showRegions ?: 0) != 0,
                    showRoasters = (b.showRoasters ?: 0) != 0,
                    pingUnread = b.pingUnread ?: 0,
                    chatUnread = b.chatUnread ?: 0,
                    lang = b.lang ?: "en",
                )
            }
            lastErrorCode = null
            true
        } catch (e: Exception) {
            lastErrorCode = (e as? ApiException)?.code
            _state.update { it.copy(errorMessage = (e as? ApiException)?.messageText ?: e.message) }
            false
        }
    }

    suspend fun refresh() { loadBootstrap() }

    suspend fun logout() {
        api.logout()
        _state.update {
            State(phase = Phase.LOGGED_OUT)
        }
    }

    /** Switch which server the app talks to (admin-only). OAuth token is invalid on the
     *  other server, so revoke first, then flip, landing on the login screen. */
    suspend fun switchServer(local: Boolean) {
        if (local == AppConfig.useLocalDev) return
        logout()
        AppConfig.useLocalDev = local
    }

    fun clearError() { _state.update { it.copy(errorMessage = null) } }

    companion object {
        fun deviceID(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "android-unknown"
    }
}

/** Small helper mirroring iOS ThemeMode.from server-value normalization. */
object ThemeModeValue {
    fun normalize(raw: String?): String = when (raw) { "light" -> "light"; "dark" -> "dark"; else -> "" }
}
