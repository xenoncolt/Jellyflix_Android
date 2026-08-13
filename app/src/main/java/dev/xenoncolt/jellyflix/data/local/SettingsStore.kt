package dev.xenoncolt.jellyflix.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "movhubs")

/**
 * Bearer token + user preferences.
 *
 * The token is a 180-day credential, so it lives in DataStore rather than in
 * memory. It is not encrypted at rest: on a non-rooted device app-private
 * storage is already isolated, and EncryptedSharedPreferences is deprecated.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val AuthToken = stringPreferencesKey("auth_token")
        val PkceVerifier = stringPreferencesKey("pkce_verifier")
        val WifiOnly = booleanPreferencesKey("wifi_only")
        val SequentialDownload = booleanPreferencesKey("sequential_download")
        val DownloadTreeUri = stringPreferencesKey("download_tree_uri")
        val MaxUploadKbps = intPreferencesKey("max_upload_kbps")
        val MaxDownloadKbps = intPreferencesKey("max_download_kbps")
        val PreferredResolution = stringPreferencesKey("preferred_resolution")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[Keys.AuthToken] }

    /**
     * Read synchronously-ish for the OkHttp interceptor. Interceptors run on a
     * background thread, so blocking here is safe, but we keep the last value
     * cached so the common path never touches disk.
     */
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenLoaded = false

    suspend fun currentToken(): String? {
        if (!tokenLoaded) {
            cachedToken = context.dataStore.data.first()[Keys.AuthToken]
            tokenLoaded = true
        }
        return cachedToken
    }

    fun cachedTokenOrNull(): String? = cachedToken

    suspend fun setAuthToken(token: String?) {
        cachedToken = token
        tokenLoaded = true
        context.dataStore.edit { prefs ->
            if (token == null) prefs.remove(Keys.AuthToken) else prefs[Keys.AuthToken] = token
        }
    }

    suspend fun setPkceVerifier(verifier: String?) {
        context.dataStore.edit { prefs ->
            if (verifier == null) prefs.remove(Keys.PkceVerifier)
            else prefs[Keys.PkceVerifier] = verifier
        }
    }

    suspend fun pkceVerifier(): String? = context.dataStore.data.first()[Keys.PkceVerifier]

    // --- Download preferences ----------------------------------------------

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.WifiOnly] ?: true }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.WifiOnly] = value }
    }

    val sequentialDownload: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SequentialDownload] ?: false }

    suspend fun setSequentialDownload(value: Boolean) {
        context.dataStore.edit { it[Keys.SequentialDownload] = value }
    }

    val downloadTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.DownloadTreeUri] }

    suspend fun setDownloadTreeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.DownloadTreeUri) else prefs[Keys.DownloadTreeUri] = uri
        }
    }

    val maxUploadKbps: Flow<Int> = context.dataStore.data.map { it[Keys.MaxUploadKbps] ?: 0 }
    val maxDownloadKbps: Flow<Int> = context.dataStore.data.map { it[Keys.MaxDownloadKbps] ?: 0 }

    suspend fun setMaxUploadKbps(value: Int) {
        context.dataStore.edit { it[Keys.MaxUploadKbps] = value }
    }

    suspend fun setMaxDownloadKbps(value: Int) {
        context.dataStore.edit { it[Keys.MaxDownloadKbps] = value }
    }

    val preferredResolution: Flow<String?> =
        context.dataStore.data.map { it[Keys.PreferredResolution] }

    suspend fun setPreferredResolution(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.PreferredResolution)
            else prefs[Keys.PreferredResolution] = value
        }
    }
}
