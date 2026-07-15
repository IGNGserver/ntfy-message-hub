package top.lvziwang.ntfyhub.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import top.lvziwang.ntfyhub.model.AppSettings
import top.lvziwang.ntfyhub.model.ServerConfig
import top.lvziwang.ntfyhub.model.ThemePreset
import top.lvziwang.ntfyhub.model.ThemeMode

private val Context.dataStore by preferencesDataStore(name = "hub_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            serverConfig = ServerConfig(
                baseUrl = preferences[BASE_URL].orEmpty(),
                accessKey = "",
                rememberSession = preferences[REMEMBER_SESSION] ?: true
            ),
            hasSavedSession = !(preferences[SESSION_COOKIE].isNullOrBlank() || preferences[SESSION_HOST].isNullOrBlank()),
            themeMode = preferences[THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
            themePreset = preferences[THEME_PRESET]?.let(ThemePreset::valueOf) ?: ThemePreset.OCEAN,
            vibrationEnabled = preferences[VIBRATION] ?: true,
            localStorageEnabled = preferences[LOCAL_STORAGE] ?: false
        )
    }

    suspend fun updateServerConfig(config: ServerConfig) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL] = config.baseUrl.trim()
            preferences[REMEMBER_SESSION] = config.rememberSession
        }
    }

    suspend fun saveSession(host: String, cookieValue: String) {
        context.dataStore.edit { preferences ->
            preferences[SESSION_HOST] = host
            preferences[SESSION_COOKIE] = cookieValue
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_HOST)
            preferences.remove(SESSION_COOKIE)
        }
    }

    suspend fun getSavedSession(): Pair<String, String>? {
        val preferences = context.dataStore.data.firstOrNull() ?: return null
        val host = preferences[SESSION_HOST].orEmpty()
        val cookie = preferences[SESSION_COOKIE].orEmpty()
        return if (host.isBlank() || cookie.isBlank()) null else host to cookie
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateThemePreset(themePreset: ThemePreset) {
        context.dataStore.edit { it[THEME_PRESET] = themePreset.name }
    }

    suspend fun updateVibration(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATION] = enabled }
    }

    suspend fun updateLocalStorage(enabled: Boolean) {
        context.dataStore.edit { it[LOCAL_STORAGE] = enabled }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val REMEMBER_SESSION = booleanPreferencesKey("remember_session")
        val SESSION_HOST = stringPreferencesKey("session_host")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val VIBRATION = booleanPreferencesKey("vibration")
        val LOCAL_STORAGE = booleanPreferencesKey("local_storage")
    }
}
