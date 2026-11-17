package com.danyt.meerkat.core.net

import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/**
 * The local network the phone is on: its own [address] and the network's [prefixLength].
 *
 * @property wired true for Ethernet, false for Wi-Fi.
 */
data class LocalSubnet(val address: Ipv4, val prefixLength: Int, val wired: Boolean = false) {
    /** The whole network, for the scope guard. */
    val block: IpRange get() = cidrRange(address, prefixLength, hostsOnly = false)

    /** The network in CIDR form, such as "192.168.1.0/24". */
    val cidr: String get() = "${block.first}/$prefixLength"
}

/**
 * The device's own networks at one moment, read together so they cannot disagree.
 *
 * @property current the Wi-Fi or Ethernet network the device is on, or null.
 * @property hotspot the network of the device's own hotspot or tethering, or null.
 */
data class NetworkSnapshot(val current: LocalSubnet?, val hotspot: LocalSubnet?) {
    /** Both networks as ranges, for the scope guard. */
    val ownRanges: List<IpRange> get() = listOfNotNull(current?.block, hotspot?.block)

    /** The device's own address on each network. */
    val ownAddresses: List<Ipv4> get() = listOfNotNull(current?.address, hotspot?.address)
}

/**
 * Reads which local networks the device is on and shares. Needs only ACCESS_NETWORK_STATE and
 * ACCESS_WIFI_STATE, not location. Every call reads the current state; nothing is cached or sent anywhere.
 */
class LocalNetwork(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    /**
     * Both networks at once. Prefer this over the single calls when both are needed: it asks the system once.
     *
     * Listing the network interfaces can take a few milliseconds, so call it off the main thread where possible.
     */
    fun snapshot(): NetworkSnapshot {
        val shared = sharedInterfaces()
        val current = currentSubnet(shared)
        return NetworkSnapshot(current, hotspotSubnet(shared, current))
    }

    /**
     * The Wi-Fi or Ethernet network the device is on, or null when there is none.
     *
     * A mobile data connection is never returned: its "subnet" belongs to the carrier and is not ours to scan.
     * A VPN is skipped too. With one switched on, the default network is the tunnel, whose address says nothing
     * about the local network, so the Wi-Fi or Ethernet network underneath is looked up instead. A hotspot is
     * never returned either, even on phones that list it like a Wi-Fi network.
     */
    fun currentSubnet(): LocalSubnet? = currentSubnet(sharedInterfaces())

    /**
     * The network of the device's own hotspot or USB / Bluetooth tethering, or null when none is switched on.
     *
     * Android announces which interfaces it is sharing in a broadcast that any app may read, so that is asked
     * first. Only when it gives no answer (unusual builds) does this fall back to guessing from interface
     * names, which differ between manufacturers.
     */
    fun hotspotSubnet(): LocalSubnet? = snapshot().hotspot

    /** The networks that are the user's own right now: the one the device is on, and its hotspot. */
    fun ownRanges(): List<IpRange> = snapshot().ownRanges

    /** The device's own addresses on those networks. */
    fun ownAddresses(): List<Ipv4> = snapshot().ownAddresses

    private fun currentSubnet(shared: Set<String>?): LocalSubnet? {
        val manager = connectivity ?: return null
        val sharing = shared.orEmpty()
        val wifiOn = wifi?.isWifiEnabled != false
        @Suppress("DEPRECATION") // The replacement needs a callback; this is a one-off read.
        val candidates = listOfNotNull(manager.activeNetwork) + manager.allNetworks
        return candidates.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            val wired = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val local = wired || (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && wifiOn)
            if (!local || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
            val link = manager.getLinkProperties(network)?.takeIf { it.interfaceName !in sharing }
            val address = link?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                ?: return@firstNotNullOfOrNull null
            LocalSubnet(address.address.address.toIpv4(), address.prefixLength, wired)
        }
    }

    private fun hotspotSubnet(shared: Set<String>?, current: LocalSubnet?): LocalSubnet? = try {
        if (shared != null) {
            shared.firstNotNullOfOrNull { name -> NetworkInterface.getByName(name)?.let(::privateSubnet) }
        } else {
            guessHotspot(current)
        }
    } catch (_: SocketException) {
        null
    }

    /**
     * Names of the interfaces Android is sharing right now (normal hotspot, local-only hotspot, USB, Bluetooth),
     * or null when the system did not say. Read from the last tethering broadcast, which Android keeps.
     */
    private fun sharedInterfaces(): Set<String>? {
        val intent = try {
            ContextCompat.registerReceiver(
                appContext,
                null,
                IntentFilter(ACTION_TETHER_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: SecurityException) {
            null
        } ?: return null
        return (
            intent.getStringArrayListExtra(EXTRA_ACTIVE_TETHER).orEmpty() +
                intent.getStringArrayListExtra(EXTRA_ACTIVE_LOCAL_ONLY).orEmpty()
            ).toSet()
    }

    /** The fallback: a tethering-style interface with a private address that the device is not online through. */
    private fun guessHotspot(current: LocalSubnet?): LocalSubnet? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().firstNotNullOfOrNull { nic ->
            val tethering = TetheringPrefixes.any { nic.name.startsWith(it) }
            if (!tethering) return@firstNotNullOfOrNull null
            privateSubnet(nic)?.takeIf { it.address != current?.address }
        }

    private fun privateSubnet(nic: NetworkInterface): LocalSubnet? {
        if (!nic.isUp || nic.isLoopback) return null
        return nic.interfaceAddresses
            .firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
            ?.let { LocalSubnet(it.address.address.toIpv4(), it.networkPrefixLength.toInt()) }
    }

    private fun ByteArray.toIpv4() = Ipv4(fold(0u) { acc, byte -> (acc shl 8) or (byte.toUInt() and 0xFFu) })

    private companion object {
        // The tethering broadcast and its extras. Public in Android's sources but hidden from the SDK.
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val EXTRA_ACTIVE_TETHER = "tetherArray"
        const val EXTRA_ACTIVE_LOCAL_ONLY = "android.net.extra.ACTIVE_LOCAL_ONLY"

        /** How hotspot, USB, and Bluetooth tethering interfaces are named on common Android builds. */
        val TetheringPrefixes = listOf("ap", "swlan", "softap", "wlan", "wifi_br", "rndis", "usb", "ncm", "bt-pan")
    }
}
