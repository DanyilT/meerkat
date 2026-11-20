package com.danyt.meerkat.core.settings

import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /** Persists the scope guard list. Blank entries and repeats are dropped; the order is kept. */
    suspend fun setAuthorizedRanges(ranges: List<String>) {
        val clean = ranges.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { it[Keys.authorizedRanges] = clean.joinToString(RANGE_SEPARATOR) }
    }

    /** Persists whether the MAC address privacy tip should stay hidden. */
    suspend fun setMacTipDismissed(dismissed: Boolean) {
        dataStore.edit { it[Keys.macTipDismissed] = dismissed }
    }

    /** Persists the network scanner's Custom profile, in a single write. */
    suspend fun setCustomScan(options: SavedScanOptions) {
        dataStore.edit {
            it[Keys.scanPing] = options.ping
            it[Keys.scanTcpPorts] = options.tcpPorts
            it[Keys.scanUdpPorts] = options.udpPorts
            it[Keys.scanTimeout] = options.timeout
            it[Keys.scanConcurrency] = options.concurrency
            it[Keys.scanResolveNames] = options.resolveNames
            it[Keys.scanDnsServer] = options.dnsServer
        }
    }

    /**
     * Adds tools that became available since the last start to the dashboard and marks them "New". Runs as one
     * write, so the layout and the badges never disagree. See [reconcileNewTools].
     *
     * @param built routes of the tools that work in this version, in catalog order.
     */
    suspend fun reconcileTools(built: List<String>) {
        dataStore.edit { preferences ->
            val update = reconcileNewTools(
                built = built,
                layout = preferences[Keys.dashboardTiles]?.split(ROUTE_SEPARATOR)?.filter { it.isNotEmpty() },
                known = preferences[Keys.knownTools],
                fresh = preferences[Keys.newTools].orEmpty(),
            ) ?: return@edit
            update.layout?.let { preferences[Keys.dashboardTiles] = it.joinToString(ROUTE_SEPARATOR) }
            preferences[Keys.knownTools] = update.known
            preferences[Keys.newTools] = update.fresh
        }
    }

    /** Removes the "New" badge from a tool, once the user has opened it. */
    suspend fun markToolOpened(route: String) {
        dataStore.edit { preferences ->
            val fresh = preferences[Keys.newTools].orEmpty()
            if (route in fresh) preferences[Keys.newTools] = fresh - route
        }
    }

    /** Persists whether screens introduce themselves and show an info button. */
    suspend fun setIntrosEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.introsEnabled] = enabled }
    }

    /** Remembers that the introduction with [key] was acknowledged. */
    suspend fun markIntroSeen(key: String) {
        dataStore.edit { it[Keys.seenIntros] = it[Keys.seenIntros].orEmpty() + key }
    }

    /** Forgets which introductions were acknowledged, so every screen introduces itself once more. */
    suspend fun resetIntros() {
        dataStore.edit { it.remove(Keys.seenIntros) }
    }

    /**
     * Sets the tab the tool at [route] opens on: a tab key, [AppSettings.LAST_TAB], or null for its first tab.
     */
    suspend fun setStartTab(route: String, tab: String?) {
        dataStore.edit { it[Keys.startTabs] = withEntry(it[Keys.startTabs], route, tab) }
    }

    /** Remembers [tab] as the one the user looked at last in the tool at [route]. */
    suspend fun setLastTab(route: String, tab: String) {
        dataStore.edit { it[Keys.lastTabs] = withEntry(it[Keys.lastTabs], route, tab) }
    }

    /** Whether the Nearby tool hides Bluetooth devices that announce no name. */
    suspend fun setHideUnnamedBluetooth(hide: Boolean) {
        dataStore.edit { it[Keys.hideUnnamedBluetooth] = hide }
    }

    /** Whether the NFC reader reads and shows a bank card's number and expiry date. */
    suspend fun setShowCardDetails(show: Boolean) {
        dataStore.edit { it[Keys.showCardDetails] = show }
    }

    /** Whether the Nearby tool's Bluetooth filter applies to its distance map too. */
    suspend fun setBluetoothFilterOnMap(apply: Boolean) {
        dataStore.edit { it[Keys.bluetoothFilterOnMap] = apply }
    }

    /** Remembers the Wi-Fi band the user picked on the channel chart. */
    suspend fun setChannelChartBand(band: String) {
        dataStore.edit { it[Keys.channelChartBand] = band }
    }

    /** Saves the Nearby tool's Wi-Fi filter, replacing the one saved before. Keys must not contain "=". */
    suspend fun setWifiFilter(entries: Map<String, String>) {
        dataStore.edit { preferences ->
            preferences[Keys.wifiFilter] = entries.map { (key, value) -> key + ENTRY_SEPARATOR + value }.toSet()
        }
    }

    /** Remembers whether the section with [key] is folded away. */
    suspend fun setSectionFolded(key: String, folded: Boolean) {
        dataStore.edit {
            val current = it[Keys.foldedSections].orEmpty()
            it[Keys.foldedSections] = if (folded) current + key else current - key
        }
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
            authorizedRanges = preferences[Keys.authorizedRanges]?.split(RANGE_SEPARATOR)?.filter { it.isNotEmpty() }
                ?: defaults.authorizedRanges,
            macTipDismissed = preferences[Keys.macTipDismissed] ?: defaults.macTipDismissed,
            customScan = SavedScanOptions(
                ping = preferences[Keys.scanPing] ?: defaults.customScan.ping,
                tcpPorts = preferences[Keys.scanTcpPorts] ?: defaults.customScan.tcpPorts,
                udpPorts = preferences[Keys.scanUdpPorts] ?: defaults.customScan.udpPorts,
                timeout = preferences[Keys.scanTimeout] ?: defaults.customScan.timeout,
                concurrency = preferences[Keys.scanConcurrency] ?: defaults.customScan.concurrency,
                resolveNames = preferences[Keys.scanResolveNames] ?: defaults.customScan.resolveNames,
                dnsServer = preferences[Keys.scanDnsServer] ?: defaults.customScan.dnsServer,
            ),
            introsEnabled = preferences[Keys.introsEnabled] ?: defaults.introsEnabled,
            seenIntros = preferences[Keys.seenIntros] ?: defaults.seenIntros,
            newTools = preferences[Keys.newTools] ?: defaults.newTools,
            startTabs = toMap(preferences[Keys.startTabs]),
            lastTabs = toMap(preferences[Keys.lastTabs]),
            hideUnnamedBluetooth = preferences[Keys.hideUnnamedBluetooth] ?: defaults.hideUnnamedBluetooth,
            bluetoothFilterOnMap = preferences[Keys.bluetoothFilterOnMap] ?: defaults.bluetoothFilterOnMap,
            showCardDetails = preferences[Keys.showCardDetails] ?: defaults.showCardDetails,
            channelChartBand = preferences[Keys.channelChartBand],
            foldedSections = preferences[Keys.foldedSections] ?: defaults.foldedSections,
            wifiFilter = toMap(preferences[Keys.wifiFilter]),
        )
    }

    // Maps from route to tab are stored as a set of "route=tab" entries. Neither part contains "=".
    private fun toMap(entries: Set<String>?): Map<String, String> = entries.orEmpty()
        .mapNotNull { entry -> entry.split(ENTRY_SEPARATOR, limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key to value }

    private fun withEntry(entries: Set<String>?, key: String, value: String?): Set<String> {
        val others = entries.orEmpty().filterNot { it.startsWith(key + ENTRY_SEPARATOR) }.toSet()
        return if (value == null) others else others + (key + ENTRY_SEPARATOR + value)
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accentColor = intPreferencesKey("accent_color")
        val monochrome = booleanPreferencesKey("monochrome")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val blurEnabled = booleanPreferencesKey("blur_enabled")
        val dashboardTiles = stringPreferencesKey("dashboard_layout")
        val authorizedRanges = stringPreferencesKey("authorized_ranges")
        val macTipDismissed = booleanPreferencesKey("mac_tip_dismissed")
        val scanPing = booleanPreferencesKey("scan_ping")
        val scanTcpPorts = stringPreferencesKey("scan_tcp_ports")
        val scanUdpPorts = stringPreferencesKey("scan_udp_ports")
        val scanTimeout = stringPreferencesKey("scan_timeout")
        val scanConcurrency = stringPreferencesKey("scan_concurrency")
        val scanResolveNames = booleanPreferencesKey("scan_resolve_names")
        val scanDnsServer = stringPreferencesKey("scan_dns_server")
        val introsEnabled = booleanPreferencesKey("intros_enabled")
        val seenIntros = stringSetPreferencesKey("seen_intros")
        val knownTools = stringSetPreferencesKey("known_tools")
        val newTools = stringSetPreferencesKey("new_tools")
        val startTabs = stringSetPreferencesKey("start_tabs")
        val lastTabs = stringSetPreferencesKey("last_tabs")
        val hideUnnamedBluetooth = booleanPreferencesKey("hide_unnamed_bluetooth")
        val bluetoothFilterOnMap = booleanPreferencesKey("bluetooth_filter_on_map")
        val showCardDetails = booleanPreferencesKey("show_card_details")
        val channelChartBand = stringPreferencesKey("channel_chart_band")
        val foldedSections = stringSetPreferencesKey("folded_sections")
        val wifiFilter = stringSetPreferencesKey("wifi_filter")
    }

    private companion object {
        const val OPAQUE_ALPHA = 0xFF000000.toInt()
        const val ROUTE_SEPARATOR = ","
        const val RANGE_SEPARATOR = "\n"
        const val ENTRY_SEPARATOR = "="
    }
}
