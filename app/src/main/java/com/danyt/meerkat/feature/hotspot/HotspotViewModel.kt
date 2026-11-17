package com.danyt.meerkat.feature.hotspot

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.net.HotspotState
import com.danyt.meerkat.core.net.LocalHotspot
import com.danyt.meerkat.core.net.LocalNetwork
import com.danyt.meerkat.core.net.LocalSubnet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * @property network the network of whichever hotspot is on (the system one or the local-only one), in CIDR
 *   form, or null when none is detected.
 * @property ownAddress this phone's address on that network.
 * @property local the app's own local-only hotspot.
 * @property localSupported false on Android 12 and older, where the local-only hotspot is not offered.
 */
@Immutable
data class HotspotUiState(
    val network: String? = null,
    val ownAddress: String? = null,
    val local: HotspotState = HotspotState.Off,
    val localSupported: Boolean = false,
)

class HotspotViewModel(private val hotspot: LocalHotspot, private val localNetwork: LocalNetwork) : ViewModel() {
    private val detected = MutableStateFlow(localNetwork.hotspotSubnet())

    val uiState: StateFlow<HotspotUiState> = combine(detected, hotspot.state) { subnet, local ->
        state(subnet, local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(detected.value, hotspot.state.value))

    /** Looks for an active hotspot again. Reads local interfaces only; nothing is sent on the network. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { detected.value = localNetwork.hotspotSubnet() }
    }

    fun startLocal() = hotspot.start()

    fun stopLocal() {
        hotspot.stop()
        refresh()
    }

    private fun state(subnet: LocalSubnet?, local: HotspotState) = HotspotUiState(
        network = subnet?.cidr,
        ownAddress = subnet?.address?.toString(),
        local = local,
        localSupported = hotspot.isSupported,
    )
}
