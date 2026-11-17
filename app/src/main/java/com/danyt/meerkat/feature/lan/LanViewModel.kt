package com.danyt.meerkat.feature.lan

import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.net.IpRange
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.LanScanSession
import com.danyt.meerkat.core.net.LanScanState
import com.danyt.meerkat.core.net.LocalNetwork
import com.danyt.meerkat.core.net.ScanConfig
import com.danyt.meerkat.core.net.TargetError
import com.danyt.meerkat.core.net.TargetResult
import com.danyt.meerkat.core.net.isInScope
import com.danyt.meerkat.core.net.parsePorts
import com.danyt.meerkat.core.net.parseTarget
import com.danyt.meerkat.core.net.touchesLocalNetwork
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportHashes
import com.danyt.meerkat.core.reports.ReportStore
import com.danyt.meerkat.core.settings.AppSettings
import com.danyt.meerkat.core.settings.SavedScanOptions
import com.danyt.meerkat.core.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanProfile { Quick, Full, Custom }

/**
 * The Custom profile as the user types it. Fields stay text so half-typed values are kept; the parsed values
 * are null while a field is invalid.
 */
@Immutable
data class CustomOptions(
    val ping: Boolean = true,
    val tcpPorts: String = SavedScanOptions().tcpPorts,
    val udpPorts: String = "",
    val timeout: String = "500",
    val concurrency: String = "64",
    val resolveNames: Boolean = true,
    val dnsServer: String = "",
) {
    /** False while the DNS server field holds something that is not an address. Empty is fine. */
    val dnsServerValid: Boolean get() = dnsServer.isBlank() || Ipv4.parse(dnsServer.trim()) != null

    val tcpPortList: List<Int>? get() = parsePorts(tcpPorts, MAX_PORTS_PER_HOST)
    val udpPortList: List<Int>? get() = parsePorts(udpPorts, MAX_PORTS_PER_HOST)
    val timeoutMillis: Int? get() = timeout.trim().toIntOrNull()?.takeIf { it in TIMEOUT_RANGE }
    val concurrencyValue: Int? get() = concurrency.trim().toIntOrNull()?.takeIf { it in CONCURRENCY_RANGE }

    /** True when every field is valid but no probe is switched on, so a scan could not find anything. */
    val hasNoProbe: Boolean get() = !ping && tcpPortList?.isEmpty() == true && udpPortList?.isEmpty() == true

    /** The scan settings these options describe, or null while something is invalid. */
    val config: ScanConfig?
        get() {
            val tcp = tcpPortList ?: return null
            val udp = udpPortList ?: return null
            val timeoutValue = timeoutMillis ?: return null
            val threads = concurrencyValue ?: return null
            if (hasNoProbe || !dnsServerValid) return null
            return ScanConfig(ping, tcp, udp, timeoutValue, threads, resolveNames, Ipv4.parse(dnsServer.trim()))
        }

    fun toSaved() = SavedScanOptions(ping, tcpPorts, udpPorts, timeout, concurrency, resolveNames, dnsServer)

    companion object {
        /** A network scan probes every port on every address, so the list per host is kept short. */
        const val MAX_PORTS_PER_HOST = 100
        val TIMEOUT_RANGE = 50..10_000
        val CONCURRENCY_RANGE = 1..256

        fun from(saved: SavedScanOptions) = CustomOptions(
            saved.ping, saved.tcpPorts, saved.udpPorts, saved.timeout, saved.concurrency, saved.resolveNames,
            saved.dnsServer,
        )
    }
}

/**
 * @property target the target as typed.
 * @property targetError why [target] was refused on the last attempt to scan, or null.
 * @property currentNetwork the Wi-Fi or Ethernet network the phone is on, in CIDR form, or null when there is none.
 * @property currentNetworkWired true when [currentNetwork] is Ethernet, false for Wi-Fi.
 * @property hotspotNetwork the network of the phone's own hotspot, in CIDR form, or null when it is off.
 * @property ownAddresses this phone's own addresses, so its row in the results can be marked.
 * @property showMacTip true when the MAC address privacy tip should be offered.
 * @property reportHashes fingerprints of saved and exported reports, for spotting copies.
 * @property scan the running or last scan.
 */
@Immutable
data class LanUiState(
    val target: String = "",
    val targetError: TargetError? = null,
    val currentNetwork: String? = null,
    val currentNetworkWired: Boolean = false,
    val hotspotNetwork: String? = null,
    val ownAddresses: List<Ipv4> = emptyList(),
    val profile: ScanProfile = ScanProfile.Quick,
    val custom: CustomOptions = CustomOptions(),
    val showMacTip: Boolean = false,
    val reportHashes: ReportHashes = ReportHashes(),
    val scan: LanScanState = LanScanState(),
)

class LanViewModel(
    private val session: LanScanSession,
    private val localNetwork: LocalNetwork,
    private val settingsRepository: SettingsRepository,
    private val reportStore: ReportStore,
    initialTarget: String? = null,
) : ViewModel() {

    // The network is read before the first frame, so the "no network" note never flashes on a connected phone.
    // The target starts from the last scan, so coming back to the screen shows what the results belong to.
    // Every network (Wi-Fi, Ethernet, and the hotspot) is read before the first frame, so the "not on a
    // network" note and the fill-in buttons are right from the start instead of flickering a moment later.
    // This takes a few milliseconds once; the regular checks after that run in the background.
    private val form = MutableStateFlow(
        localNetwork.snapshot().let { snapshot ->
            val network = snapshot.current?.cidr
            LanUiState(
                // Opened for a given target, such as a hotspot: use it. Otherwise the last scan's, then the network.
                target = initialTarget ?: session.state.value.target ?: network.orEmpty(),
                currentNetwork = network,
                currentNetworkWired = snapshot.current?.wired == true,
                hotspotNetwork = snapshot.hotspot?.cidr,
                ownAddresses = snapshot.ownAddresses,
            )
        },
    )

    /** True once the target was filled in by itself, so it is not filled in again after the user clears it. */
    @Volatile
    private var autoFilled = form.value.target.isNotEmpty()

    /** Null until the saved settings have been read. */
    private val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The tip is offered once per visit to the screen, until the user says not to show it again. */
    private val macTipClosed = MutableStateFlow(false)

    val uiState: StateFlow<LanUiState> =
        combine(form, session.state, settings, macTipClosed, reportStore.hashes) { form, scan, settings, tip, hashes ->
            form.copy(
                scan = scan,
                showMacTip = settings?.macTipDismissed == false && !tip,
                reportHashes = hashes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value.copy(scan = session.state.value))

    init {
        viewModelScope.launch { reportStore.load() }
        viewModelScope.launch {
            val saved = settingsRepository.settings.first().customScan
            form.update { it.copy(custom = CustomOptions.from(saved)) }
        }
    }

    /** Reads the current network and hotspot again. Fills in the target when the user has not typed one. */
    fun refreshNetwork() {
        // Listing the network interfaces is slow enough to keep off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = localNetwork.snapshot()
            val subnet = snapshot.current
            val network = subnet?.cidr
            val hotspot = snapshot.hotspot
            form.update {
                it.copy(
                    currentNetwork = network,
                    currentNetworkWired = subnet?.wired == true,
                    hotspotNetwork = hotspot?.cidr,
                    ownAddresses = snapshot.ownAddresses,
                    // Filled in by itself once, from the network the device is on (never the hotspot). After that
                    // an empty field stays empty: the user cleared it.
                    target = if (it.target.isBlank() && !autoFilled && network != null) {
                        autoFilled = true
                        network
                    } else {
                        it.target
                    },
                )
            }
        }
    }

    fun setTarget(text: String) = form.update { it.copy(target = text, targetError = null) }

    fun useCurrentNetwork() = form.update { it.copy(target = it.currentNetwork ?: it.target, targetError = null) }

    fun useHotspotNetwork() = form.update { it.copy(target = it.hotspotNetwork ?: it.target, targetError = null) }

    fun setProfile(profile: ScanProfile) = form.update { it.copy(profile = profile) }

    /** Changes the Custom profile and saves it. */
    fun setCustom(options: CustomOptions) {
        form.update { it.copy(custom = options) }
        viewModelScope.launch { settingsRepository.setCustomScan(options.toSaved()) }
    }

    /** Hides the MAC address tip. With [forever], it is not offered again; Settings can still show it. */
    fun closeMacTip(forever: Boolean) {
        macTipClosed.value = true
        if (forever) viewModelScope.launch { settingsRepository.setMacTipDismissed(true) }
    }

    /** Checks the target and options. Shows the target error, if any. Returns true when a scan can start. */
    fun validate(): Boolean {
        val state = form.value
        val result = parseTarget(state.target)
        form.update { it.copy(targetError = (result as? TargetResult.Invalid)?.error) }
        return result is TargetResult.Valid && configFor(state) != null
    }

    /**
     * The scope guard: true when the typed target is not covered by the user's authorized ranges or by a
     * network of their own, so the scan needs an explicit confirmation first.
     */
    fun targetIsOutsideScope(): Boolean {
        val range = targetRange() ?: return false
        return !isInScope(range, settings.value?.authorizedRanges.orEmpty(), localNetwork.ownRanges())
    }

    /** True when the typed target includes local addresses, which need the user's permission on Android 17. */
    fun targetIsLocal(): Boolean = targetRange()?.touchesLocalNetwork() ?: false

    /** Starts the scan. Call after [validate] returned true and local network access was granted. */
    fun startScan() {
        val state = form.value
        val range = targetRange() ?: return
        val config = configFor(state) ?: return
        session.start(state.target.trim(), range, config, state.profile.name)
    }

    fun cancelScan() = session.cancel()

    /** Remembers that the report with this fingerprint was exported to a file. */
    fun markExported(hash: String) {
        viewModelScope.launch { reportStore.markExported(hash) }
    }

    /** Saves [report] in the app, then gives [onResult] the stored copy, or null when storing failed. */
    fun saveReport(report: Report, onResult: (stored: Report?) -> Unit) {
        viewModelScope.launch {
            onResult(
                try {
                    reportStore.save(report)
                } catch (_: IOException) {
                    null
                },
            )
        }
    }

    private fun targetRange(): IpRange? = (parseTarget(form.value.target) as? TargetResult.Valid)?.range

    private fun configFor(state: LanUiState): ScanConfig? = when (state.profile) {
        ScanProfile.Quick -> ScanConfig.Quick
        ScanProfile.Full -> ScanConfig.Full
        ScanProfile.Custom -> state.custom.config
    }
}
