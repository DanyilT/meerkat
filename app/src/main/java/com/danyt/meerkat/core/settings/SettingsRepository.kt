package com.danyt.meerkat.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reads and writes [AppSettings] in a Preferences DataStore.
 *
 * DataStore does its own disk work on Dispatchers.IO, so these functions are safe to call from the main thread.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Current settings, re-emitted on every change. Falls back to defaults if the file cannot be read. */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)
        .distinctUntilChanged()

    /** Persists the light, dark, or system theme choice. */
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    /**
     * Persists the accent color and the monochrome choice together, in a single write. The alpha channel is
     * forced to opaque.
     */
    suspend fun setAccent(argb: Int, monochrome: Boolean) {
        dataStore.edit {
            it[Keys.accentColor] = argb or OPAQUE_ALPHA
            it[Keys.monochrome] = monochrome
        }
    }

    /** Persists whether animations are shown. */
    suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.animationsEnabled] = enabled }
    }

    /** Persists whether blur effects are drawn. */
    suspend fun setBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.blurEnabled] = enabled }
    }

    /** Persists which tools are on the dashboard and in what order, by route. An empty list means no tiles. */
    suspend fun setDashboardTiles(routes: List<String>) {
        dataStore.edit { it[Keys.dashboardTiles] = routes.distinct().joinToString(ROUTE_SEPARATOR) }
    }

    private fun toSettings(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[Keys.themeMode] } ?: defaults.themeMode,
            accentColor = preferences[Keys.accentColor]?.or(OPAQUE_ALPHA) ?: defaults.accentColor,
            monochrome = preferences[Keys.monochrome] ?: defaults.monochrome,
            animationsEnabled = preferences[Keys.animationsEnabled] ?: defaults.animationsEnabled,
            blurEnabled = preferences[Keys.blurEnabled] ?: defaults.blurEnabled,
            dashboardTiles = preferences[Keys.dashboardTiles]?.split(ROUTE_SEPARATOR)?.filter { it.isNotEmpty() },
        )
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accentColor = intPreferencesKey("accent_color")
        val monochrome = booleanPreferencesKey("monochrome")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val blurEnabled = booleanPreferencesKey("blur_enabled")
        val dashboardTiles = stringPreferencesKey("dashboard_layout")
    }

    private companion object {
        const val OPAQUE_ALPHA = 0xFF000000.toInt()
        const val ROUTE_SEPARATOR = ","
    }
}
