package com.mue.core.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mue_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun saveAccessToken(token: String) = prefs.edit().putString("access_token", token).apply()
    fun clearAccessToken() = prefs.edit().remove("access_token").apply()

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun saveRefreshToken(token: String) = prefs.edit().putString("refresh_token", token).apply()
    fun clearRefreshToken() = prefs.edit().remove("refresh_token").apply()

    fun getGuestToken(): String? = prefs.getString("guest_token", null)
    fun saveGuestToken(token: String) = prefs.edit().putString("guest_token", token).apply()
    fun clearGuestToken() = prefs.edit().remove("guest_token").apply()

    fun getSpotifySpDc(): String? = prefs.getString("spotify_sp_dc", null)
    fun saveSpotifySpDc(cookie: String) = prefs.edit().putString("spotify_sp_dc", cookie).apply()
    fun clearSpotifySpDc() = prefs.edit().remove("spotify_sp_dc").apply()

    fun clearAll() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("guest_token")
            .remove("spotify_sp_dc")
            .apply()
    }
}

