package com.decentespresso.dcamp.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the bearer token in EncryptedSharedPreferences (Android Keystore-backed),
 * the equivalent of the iOS KeychainStore. Falls back to plain prefs if the crypto
 * provider is unavailable (should not happen on API 26+).
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dcamp_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("dcamp_secure_plain", Context.MODE_PRIVATE)
    }

    fun loadToken(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }

    fun saveToken(token: String) { prefs.edit().putString(KEY, token).apply() }

    fun clearToken() { prefs.edit().remove(KEY).apply() }

    companion object { private const val KEY = "bearer_token" }
}
