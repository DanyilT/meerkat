package com.danyt.meerkat.feature.nearby

import androidx.compose.runtime.Immutable
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.WifiSecurity
import com.danyt.meerkat.core.nearby.WifiStandard

/** Security as the filter offers it, from weakest to strongest, by generation (see [SecurityType]). */
enum class SecurityKind { Open, Wep, Wpa, Wpa2, Wpa3, Owe }

/** Whether a WPA network uses one shared password (Personal) or signs each user in (Enterprise). */
enum class SecurityType { Personal, Enterprise }

/** Wi-Fi generations as the filter offers them, oldest first. 6E is Wi-Fi 6 on the 6 GHz band. */
enum class StandardKind { Older, Wifi4, Wifi5, Wifi6, Wifi6E, Wifi7 }

internal val WifiSecurity.kind: SecurityKind
    get() = when (this) {
        WifiSecurity.Wpa3Enterprise, WifiSecurity.Wpa3 -> SecurityKind.Wpa3
        WifiSecurity.Wpa2Enterprise, WifiSecurity.Wpa2 -> SecurityKind.Wpa2
        WifiSecurity.WpaEnterprise, WifiSecurity.Wpa -> SecurityKind.Wpa
        WifiSecurity.Owe -> SecurityKind.Owe
        WifiSecurity.Wep -> SecurityKind.Wep
        WifiSecurity.Open -> SecurityKind.Open
    }

/** The generation of [network], or null on Android 10 and older, where Android does not say. */
internal val WifiNetwork.standardKind: StandardKind?
    get() = when (standard) {
        WifiStandard.Be -> StandardKind.Wifi7
        WifiStandard.Ax -> if (band == WifiBand.Band6Ghz) StandardKind.Wifi6E else StandardKind.Wifi6
        WifiStandard.Ac -> StandardKind.Wifi5
        WifiStandard.N -> StandardKind.Wifi4
        WifiStandard.Legacy, WifiStandard.Ad -> StandardKind.Older
        null -> null
    }

/**
 * Which access points the Wi-Fi list shows. Every set criterion must match; a null one matches everything.
 *
 * @property query part of the network name or of the access point's address, in any case.
 * @property channels the channels to show, by their centre frequency in MHz (channel numbers repeat between
 *   bands); a network on any of them passes. Empty for any.
 * @property security the kinds of security to show; a network that offers any of them passes. Empty for any.
 * @property standards the Wi-Fi generations to show; empty for any.
 * @property securityType Personal or Enterprise, together with [security] or for any WPA generation.
 * @property applyToChart whether the channel chart shows only the networks that pass, too.
 */
@Immutable
data class WifiFilter(
    val query: String = "",
    val band: WifiBand? = null,
    val channels: Set<Int> = emptySet(),
    val security: Set<SecurityKind> = emptySet(),
    val securityType: SecurityType? = null,
    val standards: Set<StandardKind> = emptySet(),
    val applyToChart: Boolean = false,
) {
    /** How many criteria are set. Applying to the chart is a choice about display, not a criterion. */
    val count: Int
        get() = listOf(
            query.isNotBlank(),
            band != null,
            channels.isNotEmpty(),
            security.isNotEmpty(),
            securityType != null,
            standards.isNotEmpty(),
        ).count { it }

    val isActive: Boolean get() = count > 0

    fun matches(network: WifiNetwork): Boolean {
        val text = query.trim()
        return (text.isEmpty() || network.ssid?.contains(text, ignoreCase = true) == true ||
            network.bssid.contains(text, ignoreCase = true)) &&
            (band == null || network.band == band) &&
            (channels.isEmpty() || network.frequency in channels) &&
            (security.isEmpty() && securityType == null || network.security.any(::matchesSecurity)) &&
            (standards.isEmpty() || network.standardKind in standards)
    }

    private fun matchesSecurity(offered: WifiSecurity): Boolean =
        (security.isEmpty() || offered.kind in security) &&
            when (securityType) {
                null -> true
                SecurityType.Personal -> offered.generation != null && !offered.enterprise
                SecurityType.Enterprise -> offered.enterprise
            }

    /** The filter as text entries, for saving. Unset criteria are left out. */
    fun encode(): Map<String, String> = buildMap {
        if (query.isNotEmpty()) put(QUERY, query)
        band?.let { put(BAND, it.name) }
        if (channels.isNotEmpty()) put(CHANNEL, channels.sorted().joinToString(","))
        if (security.isNotEmpty()) put(SECURITY, security.joinToString(",") { it.name })
        securityType?.let { put(SECURITY_TYPE, it.name) }
        if (standards.isNotEmpty()) put(STANDARD, standards.joinToString(",") { it.name })
        if (applyToChart) put(CHART, "1")
    }

    companion object {
        private const val QUERY = "query"
        private const val BAND = "band"
        private const val CHANNEL = "channel"
        private const val SECURITY = "security"
        private const val SECURITY_TYPE = "securityType"
        private const val STANDARD = "standard"
        private const val CHART = "chart"

        /** Reads a saved filter. Entries that no longer make sense are ignored. */
        fun decode(entries: Map<String, String>) = WifiFilter(
            query = entries[QUERY].orEmpty(),
            band = WifiBand.entries.firstOrNull { it.name == entries[BAND] },
            channels = entries[CHANNEL].orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet(),
            security = entries[SECURITY].orEmpty().split(",").mapNotNull { name ->
                SecurityKind.entries.firstOrNull { it.name == name }
            }.toSet(),
            securityType = SecurityType.entries.firstOrNull { it.name == entries[SECURITY_TYPE] },
            standards = entries[STANDARD].orEmpty().split(",").mapNotNull { name ->
                StandardKind.entries.firstOrNull { it.name == name }
            }.toSet(),
            applyToChart = entries[CHART] == "1",
        )
    }
}
