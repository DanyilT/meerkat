package com.danyt.meerkat.feature.ports

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.IpRange
import com.danyt.meerkat.core.net.LocalNetwork
import com.danyt.meerkat.core.net.PortScanSession
import com.danyt.meerkat.core.net.PortScanState
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.WellKnownPorts
import com.danyt.meerkat.core.net.cidrRange
import com.danyt.meerkat.core.net.commonPorts
import com.danyt.meerkat.core.net.isInScope
import com.danyt.meerkat.core.net.parsePorts
import com.danyt.meerkat.core.net.touchesLocalNetwork
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportHashes
import com.danyt.meerkat.core.reports.ReportStore
import com.danyt.meerkat.core.settings.AppSettings
import com.danyt.meerkat.core.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which ports to scan: the ones with a common use, the range 1 to 1024, or the user's own list. */
enum class PortPreset { Common, WellKnown, Custom }

/**
 * @property host the address as typed.
 * @property hostError true when [host] was refused on the last attempt to scan.
 * @property customPorts the user's own port list as typed, used with [PortPreset.Custom].
 * @property customPortsError true when [customPorts] was refused on the last attempt to scan.
 * @property reportHashes fingerprints of saved and exported reports, for spotting copies.
 * @property scan the running or last scan.
 */
@Immutable
data class PortsUiState(
    val host: String = "",
    val hostError: Boolean = false,
    val preset: PortPreset = PortPreset.Common,
    val customPorts: String = "",
    val customPortsError: Boolean = false,
    val protocol: Protocol = Protocol.Tcp,
    val reportHashes: ReportHashes = ReportHashes(),
    val scan: PortScanState = PortScanState(),
    val hostIsOwn: Boolean = false,
)

/** @param initialHost the address to start with, when the screen was opened from a device. */
class PortsViewModel(
    private val session: PortScanSession,
    private val localNetwork: LocalNetwork,
    private val settingsRepository: SettingsRepository,
    private val reportStore: ReportStore,
    initialHost: String?,
) : ViewModel() {

    /** Null until the saved settings have been read. */
    private val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val form = MutableStateFlow(
        session.state.value.let { last ->
            // Opened from a device: start with that device. Opened on its own: show the last scan again.
            if (initialHost != null) PortsUiState(host = initialHost)
            else PortsUiState(host = last.host?.toString().orEmpty(), protocol = last.protocol)
        },
    )

    init {
        viewModelScope.launch { reportStore.load() }
        refreshOwnAddress()
    }

    /**
     * Checks again whether the host is this device's address as it is now, for example after returning to the
     * screen on another network. Call when the screen comes back to the front.
     */
    fun refreshOwnAddress() = form.update { it.copy(hostIsOwn = isOwn(it.host)) }

    /** The tip is offered once per visit to the screen, until the user says not to show it again. */
    private var macTipClosed = false

    val uiState: StateFlow<PortsUiState> =
        combine(form, session.state, reportStore.hashes) { form, scan, hashes ->
            form.copy(scan = scan, reportHashes = hashes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value.copy(scan = session.state.value))

    /** Fills in this phone's address as it is right now, not as it was when the screen opened. */
    fun useOwnAddress() = form.update { it.copy(host = ownAddress(), hostError = false, hostIsOwn = true) }

    /**
     * True when the MAC address tip should be shown before this scan: the host is on a local network, where
     * the scan exposes the phone's MAC address, and the user has not turned the tip off.
     */
    fun shouldShowMacTip(): Boolean = !macTipClosed && settings.value?.macTipDismissed == false && hostIsLocal()

    /** Hides the MAC address tip. With [forever], it is not offered again; Settings can still show it. */
    fun closeMacTip(forever: Boolean) {
        macTipClosed = true
        if (forever) viewModelScope.launch { settingsRepository.setMacTipDismissed(true) }
    }

    fun setHost(text: String) = form.update { it.copy(host = text, hostError = false, hostIsOwn = isOwn(text)) }

    fun setPreset(preset: PortPreset) = form.update { it.copy(preset = preset) }

    fun setCustomPorts(text: String) = form.update { it.copy(customPorts = text, customPortsError = false) }

    fun setProtocol(protocol: Protocol) = form.update { it.copy(protocol = protocol) }

    /** Checks the host and the port list, and marks what is wrong. Returns true when a scan can start. */
    fun validate(): Boolean {
        val state = form.value
        val hostOk = Ipv4.parse(state.host.trim()) != null
        val portsOk = !portsFor(state).isNullOrEmpty()
        form.update { it.copy(hostError = !hostOk, customPortsError = !portsOk) }
        return hostOk && portsOk
    }

    /** True when the typed host is a local address, which needs the user's permission on Android 17. */
    fun hostIsLocal(): Boolean = hostRange()?.touchesLocalNetwork() ?: false

    /**
     * The scope guard: true when the typed host is not covered by the user's authorized ranges or by a
     * network of their own, so the scan needs an explicit confirmation first.
     */
    fun hostIsOutsideScope(): Boolean {
        val range = hostRange() ?: return false
        // Loopback never leaves this phone, so it is always the user's own.
        if (LoopbackBlock.containsAll(range)) return false
        return !isInScope(range, settings.value?.authorizedRanges.orEmpty(), localNetwork.ownRanges())
    }

    /** Off Wi-Fi and Ethernet, the phone can still check itself through the loopback address. */
    private fun ownAddress(): String = localNetwork.ownAddresses().firstOrNull()?.toString() ?: LOOPBACK

    /**
     * Whether [host] already is what the fill-in button would put there, so it has nothing to add. Read fresh
     * each time: 127.0.0.1 counts only while the device has no other address, and the address can change.
     */
    private fun isOwn(host: String): Boolean = host.trim() == ownAddress()

    private fun hostRange(): IpRange? = Ipv4.parse(form.value.host.trim())?.let { IpRange(it, it) }

    /** Starts the scan. Call after [validate] returned true and any needed network access was granted. */
    fun startScan() {
        val state = form.value
        val host = Ipv4.parse(state.host.trim()) ?: return
        val ports = portsFor(state)?.takeIf { it.isNotEmpty() } ?: return
        session.start(host, ports, state.protocol, state.preset.name)
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

    private fun portsFor(state: PortsUiState): List<Int>? = when (state.preset) {
        PortPreset.Common -> commonPorts(state.protocol)
        PortPreset.WellKnown -> WellKnownPorts
        PortPreset.Custom -> parsePorts(state.customPorts)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        val LoopbackBlock = cidrRange(Ipv4.parse("127.0.0.0")!!, 8, hostsOnly = false)
    }
}
