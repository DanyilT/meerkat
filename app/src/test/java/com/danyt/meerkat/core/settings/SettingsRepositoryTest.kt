package com.danyt.meerkat.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Runs [block] against a store backed by [file], then shuts the store down so the file can be reopened. */
    private fun withStore(file: File, block: suspend (DataStore<Preferences>) -> Unit) = runBlocking {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun newFile() = File(folder.root, "settings.preferences_pb")

    @Test
    fun `emits defaults when nothing is stored`() = withStore(newFile()) { store ->
        assertEquals(AppSettings(), SettingsRepository(store).settings.first())
    }

    @Test
    fun `stores every setting`() = withStore(newFile()) { store ->
        val repository = SettingsRepository(store)

        repository.setThemeMode(ThemeMode.Dark)
        repository.setAccent(0xFF34C759.toInt(), monochrome = true)
        repository.setAnimationsEnabled(false)
        repository.setBlurEnabled(false)

        val expected = AppSettings(
            themeMode = ThemeMode.Dark,
            accentColor = 0xFF34C759.toInt(),
            monochrome = true,
            animationsEnabled = false,
            blurEnabled = false,
        )
        assertEquals(expected, repository.settings.first())
    }

    @Test
    fun `settings survive reopening the store`() {
        val file = newFile()
        withStore(file) { store ->
            SettingsRepository(store).setThemeMode(ThemeMode.Light)
            SettingsRepository(store).setAccent(0xFFFF9500.toInt(), monochrome = false)
        }
        withStore(file) { store ->
            val settings = SettingsRepository(store).settings.first()
            assertEquals(ThemeMode.Light, settings.themeMode)
            assertEquals(0xFFFF9500.toInt(), settings.accentColor)
        }
    }

    @Test
    fun `accent color is always stored opaque`() = withStore(newFile()) { store ->
        val repository = SettingsRepository(store)
        repository.setAccent(0x00123456, monochrome = false)
        assertEquals(0xFF123456.toInt(), repository.settings.first().accentColor)
    }

    @Test
    fun `unknown theme value falls back to system`() = withStore(newFile()) { store ->
        store.edit { it[stringPreferencesKey("theme_mode")] = "Sepia" }
        assertEquals(ThemeMode.System, SettingsRepository(store).settings.first().themeMode)
    }

    @Test
    fun `dashboard tiles are null until chosen, then keep their order`() = withStore(newFile()) { store ->
        val repository = SettingsRepository(store)
        assertEquals(null, repository.settings.first().dashboardTiles)

        repository.setDashboardTiles(listOf("ssh", "lan", "tools"))
        assertEquals(listOf("ssh", "lan", "tools"), repository.settings.first().dashboardTiles)

        repository.setDashboardTiles(emptyList())
        assertEquals(emptyList<String>(), repository.settings.first().dashboardTiles)
    }

    @Test
    fun `authorized ranges are cleaned and keep their order`() = withStore(newFile()) { store ->
        val repository = SettingsRepository(store)
        assertEquals(emptyList<String>(), repository.settings.first().authorizedRanges)

        repository.setAuthorizedRanges(listOf(" 10.0.0.0/8 ", "", "192.168.1.1-50", "10.0.0.0/8"))
        assertEquals(listOf("10.0.0.0/8", "192.168.1.1-50"), repository.settings.first().authorizedRanges)

        repository.setAuthorizedRanges(emptyList())
        assertEquals(emptyList<String>(), repository.settings.first().authorizedRanges)
    }

    @Test
    fun `the MAC tip choice and the custom scan profile survive a restart`() {
        val file = newFile()
        val custom = SavedScanOptions(ping = false, tcpPorts = "22, 8000-8010", udpPorts = "53", timeout = "abc")
        withStore(file) { store ->
            val repository = SettingsRepository(store)
            assertEquals(false, repository.settings.first().macTipDismissed)
            assertEquals(SavedScanOptions(), repository.settings.first().customScan)
            repository.setMacTipDismissed(true)
            repository.setCustomScan(custom)
        }
        withStore(file) { store ->
            val settings = SettingsRepository(store).settings.first()
            assertEquals(true, settings.macTipDismissed)
            assertEquals(custom, settings.customScan)
        }
    }
}
