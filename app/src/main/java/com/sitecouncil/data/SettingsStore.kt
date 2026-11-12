package com.sitecouncil.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Einstellungen inklusive API-Key, verschlüsselt über den Android Keystore. */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore und Datei passen nicht mehr zusammen (z. B. nach Neuinstallation): neu anlegen.
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var personaCount: Int
        get() = prefs.getInt(KEY_PERSONAS, DEFAULT_PERSONAS).coerceIn(MIN_PERSONAS, MAX_PERSONAS)
        set(value) = prefs.edit().putInt(KEY_PERSONAS, value.coerceIn(MIN_PERSONAS, MAX_PERSONAS)).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE).orEmpty().ifBlank { DEFAULT_LANGUAGE }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.trim()).apply()

    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP, value).apply()

    companion object {
        private const val FILE = "sitecouncil_secure"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_PERSONAS = "persona_count"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DESKTOP = "desktop_mode"

        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_LANGUAGE = "Deutsch"
        const val DEFAULT_PERSONAS = 5
        const val MIN_PERSONAS = 3
        const val MAX_PERSONAS = 12
    }
}
