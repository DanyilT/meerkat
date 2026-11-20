package com.danyt.meerkat.core.nearby

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.danyt.meerkat.core.text.isHiddenControl
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Wi-Fi generations, as the access point announces them. */
enum class WifiStandard { Legacy, N, Ac, Ax, Ad, Be }

/**
 * One access point in range. A network name with several access points (a mesh, or 2.4 and 5 GHz radios of one
 * router) shows up once per access point.
 *
 * @property ssid the network name, or null for a hidden network.
 * @property bssid the access point's MAC address.
 * @property dbm received signal strength.
 * @property frequency frequency of its primary 20 MHz channel, in MHz.
 * @property channelWidth channel width in MHz, or null when not reported.
 * @property centerFrequency centre of the whole channel in MHz, which differs from [frequency] on wide
 *   channels; null when it is the same or not reported.
 * @property standard the newest Wi-Fi generation it supports, or null on Android 10 and older.
 * @property seenAt when it was last heard, in milliseconds since the epoch.
 * @property extras what it announces about itself, read on Android 11 and newer; null before that.
 */
data class WifiNetwork(
    val ssid: String?,
    val bssid: String,
    val dbm: Int,
    val frequency: Int,
    val channelWidth: Int?,
    val centerFrequency: Int? = null,
    val security: List<WifiSecurity>,
    val standard: WifiStandard?,
    val passpoint: Boolean,
    val seenAt: Long,
    val extras: WifiExtras? = null,
) {
    val band: WifiBand? get() = wifiBand(frequency)
    val channel: Int? get() = wifiChannel(frequency)
}

/**
 * The Wi-Fi network this device is connected to. [bssid] and [ssid] are known only with location access;
 * without it Android hides them. Speeds are in Mbit/s.
 */
data class WifiConnection(
    val bssid: String?,
    val ssid: String?,
    val ipAddress: String?,
    val linkSpeed: Int?,
    val txSpeed: Int?,
    val rxSpeed: Int?,
) {
    /**
     * Whether [network] is the one this device is on: the same BSSID, or, when Android hides the BSSID (no
     * location access), the same network name.
     */
    fun isOn(network: WifiNetwork): Boolean =
        bssid?.let { it == network.bssid } ?: (ssid != null && network.ssid == ssid)
}

/**
 * The scan results Android holds. [fresh] is false when they come from before the last scan request, because
 * the scan was refused or failed.
 */
data class WifiScanUpdate(val networks: List<WifiNetwork>, val fresh: Boolean)

/**
 * Lists Wi-Fi access points in range, from the scans Android runs.
 *
 * Android limits scan requests to about four every two minutes per app while in front, and refuses the rest.
 * Results from scans other apps or the system asked for arrive here too, so the list keeps updating anyway.
 * Scanning only listens for access points' announcements and asks them to announce; nothing is joined.
 */
class WifiScanner(context: Context) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(WifiManager::class.java)
    private val location = appContext.getSystemService(LocationManager::class.java)

    /** False on devices without Wi-Fi. */
    val isSupported: Boolean get() = wifi != null

    /**
     * Whether a scan can run now: Wi-Fi is on, or the system's "Wi-Fi scanning" setting lets it scan with
     * Wi-Fi off.
     */
    @Suppress("DEPRECATION") // isScanAlwaysAvailable still answers; its replacement needs a system permission.
    val canScan: Boolean get() = wifi?.isWifiEnabled == true || wifi?.isScanAlwaysAvailable == true

    /**
     * True when Android hides scan results while Location is off. Android 13 and newer do not, because the app
     * declares that it never uses Wi-Fi to locate the user.
     */
    val needsLocationOn: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !isLocationOn()

    private fun isLocationOn() = location?.let(LocationManagerCompat::isLocationEnabled) == true

    /** The runtime permissions that scan results need on this Android version. */
    val permissions: List<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            // Precise location from Android 10; asked together with approximate, as Android 12 requires.
            else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    /**
     * The one permission that reading results really needs: Nearby devices on Android 13 and newer, precise
     * location on Android 10 to 12L (approximate alone gives an empty list), and any location before that.
     */
    val readPermission: String
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.NEARBY_WIFI_DEVICES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.ACCESS_FINE_LOCATION
            else -> Manifest.permission.ACCESS_COARSE_LOCATION
        }

    /**
     * The runtime permissions needed to start a scan. Reading results needs only [permissions] on Android 13 and
     * newer, but asking for a new scan still needs precise location on every version.
     */
    val scanPermissions: List<String> =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * The current results, then new ones whenever Android has them, until collection stops. Reading needs the
     * [permissions]; without them the list is empty.
     */
    fun updates(): Flow<WifiScanUpdate> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
                trySend(WifiScanUpdate(results(), fresh))
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(WifiScanUpdate(results(), fresh = false))
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    /**
     * Asks Android for a new scan. Returns false when it refused, usually because of the scan limit; the
     * results then stay as they were. The answer arrives through [updates].
     */
    @Suppress("DEPRECATION") // Still the only way to ask for a scan; Android limits how often it works.
    fun startScan(): Boolean = try {
        wifi?.startScan() == true
    } catch (_: SecurityException) {
        false
    }

    /** The network this device is connected to, or null when it is not on Wi-Fi. */
    @Suppress("DEPRECATION") // The replacement needs a network callback; this is a one-off read of the same data.
    fun connection(): WifiConnection? {
        val info = try {
            wifi?.connectionInfo
        } catch (_: SecurityException) {
            null
        } ?: return null
        // Android answers with placeholders when it hides the network, or when there is none.
        val bssid = info.bssid?.uppercase()?.takeIf { it != HIDDEN_BSSID }
        val ssid = info.ssid?.removeSurrounding("\"")
            ?.takeIf { it.isNotEmpty() && it != UNKNOWN_SSID }
            ?.let(::cleanName)
        val ip = info.ipAddress.takeIf { it != 0 }?.let { value ->
            (0 until 4).joinToString(".") { ((value shr (it * 8)) and 0xFF).toString() }
        }
        if (bssid == null && ip == null) return null
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return WifiConnection(
            bssid = bssid,
            ssid = ssid,
            ipAddress = ip,
            linkSpeed = info.linkSpeed.takeIf { it > 0 },
            txSpeed = if (modern) info.txLinkSpeedMbps.takeIf { it > 0 } else null,
            rxSpeed = if (modern) info.rxLinkSpeedMbps.takeIf { it > 0 } else null,
        )
    }

    /** The results Android holds now, without waiting for a scan. Empty without the [permissions]. */
    fun currentResults(): List<WifiNetwork> = results()

    private fun results(): List<WifiNetwork> {
        val raw = try {
            wifi?.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        val sinceBoot = SystemClock.elapsedRealtime()
        return raw.map { it.toNetwork(now - sinceBoot) }
    }

    @Suppress("DEPRECATION") // SSID is deprecated for a type that needs Android 13; the text is the same.
    private fun ScanResult.toNetwork(bootTime: Long) = WifiNetwork(
        ssid = SSID?.let(::cleanName)?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() },
        bssid = BSSID.orEmpty().uppercase(),
        dbm = level,
        frequency = frequency,
        channelWidth = channelWidthMhz(channelWidth),
        centerFrequency = centerFreq0.takeIf { it > 0 && channelWidth != ScanResult.CHANNEL_WIDTH_20MHZ },
        security = wifiSecurity(capabilities.orEmpty()),
        standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) standard(wifiStandard) else null,
        passpoint = isPasspointNetwork,
        seenAt = bootTime + timestamp / 1000,
        extras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readExtras(this) else null,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readExtras(result: ScanResult): WifiExtras = parseWifiElements(
        result.informationElements.map { element ->
            val buffer = element.bytes.duplicate()
            WifiElement(element.id, ByteArray(buffer.remaining()).also(buffer::get), element.idExt)
        },
    )

    private fun channelWidthMhz(width: Int): Int? = when (width) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        CHANNEL_WIDTH_320MHZ -> 320
        else -> null
    }

    private fun standard(value: Int): WifiStandard? = when (value) {
        ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.Legacy
        ScanResult.WIFI_STANDARD_11N -> WifiStandard.N
        ScanResult.WIFI_STANDARD_11AC -> WifiStandard.Ac
        ScanResult.WIFI_STANDARD_11AX -> WifiStandard.Ax
        ScanResult.WIFI_STANDARD_11AD -> WifiStandard.Ad
        WIFI_STANDARD_11BE -> WifiStandard.Be
        else -> null
    }

    private companion object {
        const val HIDDEN_BSSID = "02:00:00:00:00:00"
        const val UNKNOWN_SSID = "<unknown ssid>"

        // Added in Android 13; the values are fixed, so older SDK levels can compare against them too.
        const val CHANNEL_WIDTH_320MHZ = 5
        const val WIFI_STANDARD_11BE = 8
    }
}

/**
 * Makes a name received over the air safe to show: control characters and direction marks are dropped and very
 * long names cut.
 * Names come from any device in range, so they are never trusted.
 */
fun cleanName(name: String): String =
    name.filterNot { it.isHiddenControl() }.trim().take(MAX_NAME_LENGTH)

private const val MAX_NAME_LENGTH = 64

/** The 20 MHz channels of [band] that networks commonly use, in order. */
fun wifiChannels(band: WifiBand): List<Int> = when (band) {
    WifiBand.Band2Ghz -> (1..14).toList()
    WifiBand.Band5Ghz -> (36..64 step 4) + (100..144 step 4) + (149..177 step 4)
    WifiBand.Band6Ghz -> (1..233 step 4).toList()
    WifiBand.Band60Ghz -> (1..6).toList()
}

/** The span a network's signal covers, from its lowest to its highest frequency in MHz. */
fun WifiNetwork.span(): ClosedFloatingPointRange<Float> {
    val center = (centerFrequency ?: frequency).toFloat()
    val half = (channelWidth ?: 20) / 2f
    return (center - half)..(center + half)
}

/**
 * How many networks reach into each channel of [band], as (channel, count) pairs in channel order. A network
 * counts for every channel whose centre lies inside its span, so overlapping 2.4 GHz networks and wide 5 GHz
 * ones count where they actually cause interference. Channel 14 is left out unless a network uses it.
 */
fun channelCrowding(networks: List<WifiNetwork>, band: WifiBand): List<Pair<Int, Int>> {
    val inBand = networks.filter { it.band == band }
    return wifiChannels(band)
        .filter { channel -> channel != 14 || inBand.any { it.channel == 14 } }
        .map { channel ->
            val center = wifiChannelFrequency(band, channel).toFloat()
            channel to inBand.count { network ->
                val span = network.span()
                center > span.start && center < span.endInclusive
            }
        }
}
