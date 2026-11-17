package com.danyt.meerkat.feature.lan

import com.danyt.meerkat.core.net.LocalNetwork
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.net.Device
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.LanScanSession
import com.danyt.meerkat.core.net.LanScanState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/** Follows one device from the scan results. [address] is the dotted address the screen was opened for. */
class DeviceViewModel(session: LanScanSession, localNetwork: LocalNetwork, val address: String) : ViewModel() {
    private val ip = Ipv4.parse(address)

    /** True when the device is this phone. There is no SSH server to connect to on the phone itself. */
    val isOwnDevice: Boolean = ip != null && ip in localNetwork.ownAddresses()

    /**
     * The device, or null when the results no longer hold it. While a scan is still running the device keeps
     * updating, for example when its name arrives. If a new scan drops it, the last known details stay.
     */
    val device: StateFlow<Device?> = session.state
        .mapNotNull(::find)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), find(session.state.value))

    private fun find(scan: LanScanState): Device? = scan.devices.firstOrNull { it.address == ip }
}
