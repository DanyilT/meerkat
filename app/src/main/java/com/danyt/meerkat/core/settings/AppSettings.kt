package com.danyt.meerkat.core.settings

/** How the app picks between its light and dark palettes. */
enum class ThemeMode { System, Light, Dark }

/**
 * User preferences that apply to the whole app.
 *
 * @property themeMode light, dark, or follow the system.
 * @property accentColor accent as an opaque ARGB integer.
 * @property monochrome when true the accent is replaced by the label color (black or white).
 * @property animationsEnabled when false, transitions and component animations are skipped.
 * @property blurEnabled when false, glass surfaces and screen transitions are drawn without blur.
 * @property dashboardTiles routes of the tools on the dashboard, in display order, or null if the user never
 * changed them.
 * @property authorizedRanges the scope guard list: targets the user said they may scan, as typed (an address,
 * a range, or a CIDR block). Scans outside these ask for confirmation first.
 * @property macTipDismissed true once the user chose not to see the MAC address privacy tip again.
 * @property customScan the Custom profile of the network scanner.
 * @property introsEnabled whether screens introduce themselves to a new user and show an info button.
 * @property seenIntros keys of the introductions the user has already acknowledged.
 * @property newTools routes of tools that recently became available and show a "New" badge until opened.
 * @property startTabs for tools with tabs: the tab each opens on, by route, or [LAST_TAB] for the one used last.
 *   A tool that is not listed opens on its first tab.
 * @property lastTabs for tools with tabs: the tab the user looked at last, by route.
 * @property hideUnnamedBluetooth whether the Nearby tool leaves Bluetooth devices without a name out of its list.
 * @property bluetoothFilterOnMap whether the Bluetooth filter applies to the distance map too.
 * @property showCardDetails whether the NFC reader reads and shows a bank card's number and expiry date.
 * @property channelChartBand the Wi-Fi band the user last picked on the channel chart, by name, or null. A band
 *   the chart falls back to by itself is never stored here.
 * @property foldedSections keys of the sections the user folded away, such as a tool's chart.
 * @property wifiFilter the Nearby tool's Wi-Fi filter as named text entries; the tool reads and writes them.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentColor: Int = DEFAULT_ACCENT_COLOR,
    val monochrome: Boolean = false,
    val animationsEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
    val dashboardTiles: List<String>? = null,
    val authorizedRanges: List<String> = emptyList(),
    val macTipDismissed: Boolean = false,
    val customScan: SavedScanOptions = SavedScanOptions(),
    val introsEnabled: Boolean = true,
    val seenIntros: Set<String> = emptySet(),
    val newTools: Set<String> = emptySet(),
    val startTabs: Map<String, String> = emptyMap(),
    val lastTabs: Map<String, String> = emptyMap(),
    val hideUnnamedBluetooth: Boolean = false,
    val bluetoothFilterOnMap: Boolean = false,
    val showCardDetails: Boolean = false,
    val channelChartBand: String? = null,
    val foldedSections: Set<String> = emptySet(),
    val wifiFilter: Map<String, String> = emptyMap(),
) {
    /**
     * The tab the tool at [route] should open on when no tab was asked for, or null for its first tab.
     */
    fun startTabOf(route: String): String? = when (val choice = startTabs[route]) {
        LAST_TAB -> lastTabs[route]
        else -> choice
    }

    companion object {
        /** In [startTabs]: open the tab used last. */
        const val LAST_TAB = "last"

        /** A violet, used until the user picks an accent. */
        const val DEFAULT_ACCENT_COLOR: Int = 0xFF7C5CFC.toInt()
    }
}

/**
 * The network scanner's Custom profile as the user typed it. The fields are kept as text, so a value the
 * scanner would refuse is still saved and can be corrected later.
 *
 * @property tcpPorts ports to try with TCP, such as "22, 80, 8000-8010".
 * @property udpPorts ports to try with UDP. Empty for none.
 * @property timeout how long each probe waits, in milliseconds.
 * @property concurrency how many probes run at the same time.
 * @property dnsServer the DNS server to ask for device names, as an address. Empty means the network's own.
 */
data class SavedScanOptions(
    val ping: Boolean = true,
    val tcpPorts: String = "22, 53, 80, 443, 445, 3389, 8080, 62078",
    val udpPorts: String = "",
    val timeout: String = "500",
    val concurrency: String = "64",
    val resolveNames: Boolean = true,
    val dnsServer: String = "",
)
