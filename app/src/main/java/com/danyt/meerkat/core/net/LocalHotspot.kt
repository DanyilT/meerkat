package com.danyt.meerkat.core.net

import android.net.wifi.SoftApConfiguration
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why a local-only hotspot could not be started. */
enum class HotspotFailure { Unsupported, NoPermission, IncompatibleMode, NoChannel, Disallowed, Other }

/** The app's own local-only hotspot. */
sealed interface HotspotState {
    data object Off : HotspotState

    data object Starting : HotspotState

    /**
     * Running. The system picks the name and password; either can be missing on unusual builds.
     *
     * @property security the security as written in Wi-Fi QR codes: "WPA", "SAE", or null for an open network.
     */
    data class On(val ssid: String?, val passphrase: String?, val security: String? = "WPA") : HotspotState

    data class Failed(val reason: HotspotFailure) : HotspotState
}

/**
 * Starts and stops a local-only hotspot: a Wi-Fi network that other devices can join to reach this phone, but
 * which does not share its internet connection.
 *
 * That is the only hotspot an ordinary app may start. The normal, internet-sharing hotspot can only be turned
 * on by the user in system settings. It lives for the whole app, because the hotspot stops as soon as nobody
 * holds on to it.
 *
 * Needs Android 13 or newer, where it asks for "Nearby devices" instead of the location permission.
 */
class LocalHotspot(context: Context) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(WifiManager::class.java)
    private val _state = MutableStateFlow<HotspotState>(HotspotState.Off)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    /** The hotspot's state, updated by the system's callbacks. */
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    /** False on Android 12 and older, and on devices without Wi-Fi. */
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && wifi != null

    /** Asks the system for a local-only hotspot. Call after the "Nearby devices" permission was granted. */
    @SuppressLint("MissingPermission") // Checked right below; lint cannot follow the helper.
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || wifi == null) {
            _state.value = HotspotState.Failed(HotspotFailure.Unsupported)
            return
        }
        if (reservation != null || _state.value == HotspotState.Starting) return
        if (appContext.checkSelfPermission(NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            _state.value = HotspotState.Failed(HotspotFailure.NoPermission)
            return
        }
        _state.value = HotspotState.Starting
        try {
            wifi.startLocalOnlyHotspot(createCallback(), Handler(Looper.getMainLooper()))
        } catch (_: IllegalStateException) {
            // Thrown when this app already holds a hotspot request the system has not released yet.
            _state.value = HotspotState.Failed(HotspotFailure.IncompatibleMode)
        } catch (_: SecurityException) {
            _state.value = HotspotState.Failed(HotspotFailure.NoPermission)
        }
    }

    /** Releases the hotspot. The system switches it off once no app holds it. */
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) reservation?.close()
        reservation = null
        _state.value = HotspotState.Off
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createCallback() = object : WifiManager.LocalOnlyHotspotCallback() {
        override fun onStarted(started: WifiManager.LocalOnlyHotspotReservation) {
            reservation = started
            val config = started.softApConfiguration
            val security = when (config.securityType) {
                SoftApConfiguration.SECURITY_TYPE_OPEN -> null
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> "SAE"
                else -> "WPA" // WPA2, and WPA2/WPA3 mixed, which every device can join as WPA.
            }
            val ssid = config.wifiSsid?.toString()?.removeSurrounding("\"")
            _state.value = HotspotState.On(ssid, config.passphrase, security)
        }

        override fun onStopped() {
            reservation = null
            _state.value = HotspotState.Off
        }

        override fun onFailed(reason: Int) {
            reservation = null
            _state.value = HotspotState.Failed(
                when (reason) {
                    ERROR_INCOMPATIBLE_MODE -> HotspotFailure.IncompatibleMode
                    ERROR_NO_CHANNEL -> HotspotFailure.NoChannel
                    ERROR_TETHERING_DISALLOWED -> HotspotFailure.Disallowed
                    else -> HotspotFailure.Other
                },
            )
        }
    }

    companion object {
        /** The runtime permission a local-only hotspot needs on Android 13 and newer. */
        const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    }
}
