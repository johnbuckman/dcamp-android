package com.decentespresso.dcamp.api

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri

/**
 * Central configuration, ported from the iOS AppConfig. The app talks to ONE
 * endpoint: POST {assetBase}/support/dcamp/api.adp with action=<name> form params.
 *
 * Server is runtime-switchable (admin-only, from My Settings) so a single build can
 * point at Live (decentespresso.com) or Test. On the Android emulator the host's
 * localhost is reachable as 10.0.2.2, so Test maps there.
 */
object AppConfig {
    private const val PREFS = "dcamp_prefs"
    private const val KEY_LOCAL_DEV = "dcamp_use_local_dev"
    const val KEY_LANG = "dcamp_lang"
    const val KEY_THEME = "dcamp_theme"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Sent as client=native on every request so the server can add native-only fields. */
    const val nativeClient = "native"

    // OAuth2 browser login (/support/oauth2). We reuse the server's built-in first-party
    // client (dcamp-ios → dcamp://auth); no server registration needed. Admin status is
    // captured server-side at authorize time and baked into the token.
    const val oauthClientId = "dcamp-ios"
    const val oauthRedirectScheme = "dcamp"
    const val oauthRedirectUri = "dcamp://auth"

    // Hosts.
    private const val PROD_BASE = "https://decentespresso.com"
    // Emulator → host loopback. (On a real device in Test mode this would need the LAN IP;
    // Test mode is a dev convenience, matching iOS's localhost:8000.)
    private const val LOCAL_BASE = "http://10.0.2.2:8000"

    var useLocalDev: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean(KEY_LOCAL_DEV, false) else false
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_LOCAL_DEV, v).apply() }

    val assetBase: String get() = if (useLocalDev) LOCAL_BASE else PROD_BASE
    val serverLabel: String get() = if (useLocalDev) "10.0.2.2:8000 (Test)" else "decentespresso.com"

    val apiUrl: String get() = "$assetBase/support/dcamp/api.adp"
    val oauthAuthorizeUrl: String get() = "$assetBase/support/oauth2/authorize"
    val oauthTokenUrl: String get() = "$assetBase/support/oauth2/token"

    /** Current UI language pref (empty = server default). */
    var lang: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_LANG, "") ?: "" else ""
        set(v) { if (::prefs.isInitialized) prefs.edit().putString(KEY_LANG, v).apply() }

    var themeRaw: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_THEME, "") ?: "" else ""
        set(v) { if (::prefs.isInitialized) prefs.edit().putString(KEY_THEME, v).apply() }

    /** Persisted forum-filter spec (JSON), mirroring the web's dcamp_forum_filter. */
    var forumFilter: String
        get() = if (::prefs.isInitialized) prefs.getString("dcamp_forum_filter", "") ?: "" else ""
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("dcamp_forum_filter", v).apply() }

    /** Latest GitHub release version the user tapped "Later" on — so we don't nag again for it. */
    var dismissedUpdate: String
        get() = if (::prefs.isInitialized) prefs.getString("dcamp_dismissed_update", "") ?: "" else ""
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("dcamp_dismissed_update", v).apply() }

    /** Epoch millis of the last successful update check — throttles the on-foreground check. */
    var lastUpdateCheck: Long
        get() = if (::prefs.isInitialized) prefs.getLong("dcamp_last_update_check", 0L) else 0L
        set(v) { if (::prefs.isInitialized) prefs.edit().putLong("dcamp_last_update_check", v).apply() }

    /** Whether we've already asked for the Android 13+ notification permission. */
    var askedNotifPermission: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean("dcamp_asked_notif_perm", false) else false
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean("dcamp_asked_notif_perm", v).apply() }

    /** Per-context composer draft (keyed). */
    fun getDraft(key: String): String = if (::prefs.isInitialized) prefs.getString("draft_$key", "") ?: "" else ""
    fun setDraft(key: String, html: String) { if (::prefs.isInitialized) prefs.edit().putString("draft_$key", html).apply() }
    fun clearDraft(key: String) { if (::prefs.isInitialized) prefs.edit().remove("draft_$key").apply() }

    /** Absolute URL from a possibly-relative server path (avatars, attachments). */
    fun absUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return if (path.startsWith("http")) path else assetBase + path
    }

    private val scalableExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")

    /**
     * Route our own hosted raster images through the on-demand resizer
     * (/support/dcamp/img?src=…&w=…). Mirrors the web's dcScale / iOS scaledImageURL:
     * only our own attachment/avatar tree and only resizable raster extensions; foreign
     * URLs and non-raster files are returned unchanged.
     */
    fun scaledImageUrl(url: String, width: Int = 800): String {
        val uri = try { url.toUri() } catch (e: Exception) { return url }
        val path = uri.path ?: return url
        if (!path.startsWith("/support/dcamp/attachments/") && !path.startsWith("/support/dcamp/avatars/")) return url
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in scalableExt) return url
        val enc = android.net.Uri.encode(path)
        return "$assetBase/support/dcamp/img?src=$enc&w=$width"
    }
}
