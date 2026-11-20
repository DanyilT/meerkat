package com.danyt.meerkat.core.nearby

/** Wi-Fi frequency bands. */
enum class WifiBand { Band2Ghz, Band5Ghz, Band6Ghz, Band60Ghz }

/** The band a Wi-Fi [frequency] in MHz belongs to, or null for a frequency outside them. */
fun wifiBand(frequency: Int): WifiBand? = when (frequency) {
    in 2400..2500 -> WifiBand.Band2Ghz
    in 4900..5900 -> WifiBand.Band5Ghz
    in 5925..7125 -> WifiBand.Band6Ghz
    in 58_000..71_000 -> WifiBand.Band60Ghz
    else -> null
}

/** The Wi-Fi channel number of a [frequency] in MHz, or null when it is not a channel's centre. */
fun wifiChannel(frequency: Int): Int? = when {
    frequency == 2484 -> 14
    frequency in 2412..2472 && (frequency - 2407) % 5 == 0 -> (frequency - 2407) / 5
    frequency in 4910..5895 && frequency % 5 == 0 -> (frequency - 5000) / 5
    // 6 GHz channels start at 5955 MHz (channel 1); 5935 MHz is the odd channel 2.
    frequency == 5935 -> 2
    frequency in 5955..7115 && (frequency - 5950) % 5 == 0 -> (frequency - 5950) / 5
    frequency in 58_320..70_200 && (frequency - 56_160) % 2160 == 0 -> (frequency - 56_160) / 2160
    else -> null
}

/** The centre frequency in MHz of Wi-Fi [channel] on [band]: the reverse of [wifiChannel]. */
fun wifiChannelFrequency(band: WifiBand, channel: Int): Int = when (band) {
    WifiBand.Band2Ghz -> if (channel == 14) 2484 else 2407 + channel * 5
    WifiBand.Band5Ghz -> 5000 + channel * 5
    WifiBand.Band6Ghz -> if (channel == 2) 5935 else 5950 + channel * 5
    WifiBand.Band60Ghz -> 56_160 + channel * 2160
}

/**
 * Security of a Wi-Fi network, from strongest to weakest. "Personal" uses one shared password; "Enterprise"
 * signs each user in against a server.
 *
 * @property generation the WPA generation (3, 2, or 1), or null for Enhanced Open, WEP, and open networks.
 * @property enterprise true for the Enterprise kinds.
 */
enum class WifiSecurity(val generation: Int?, val enterprise: Boolean) {
    Wpa3Enterprise(3, true),
    Wpa3(3, false),
    Owe(null, false),
    Wpa2Enterprise(2, true),
    Wpa2(2, false),
    WpaEnterprise(1, true),
    Wpa(1, false),
    Wep(null, false),
    Open(null, false),
}

/**
 * Reads the security a network offers from Android's capability text, such as
 * "[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS][WPS]". A network can offer several (for example WPA2 and WPA3 together for
 * older devices), so all of them are returned, strongest first.
 */
fun wifiSecurity(capabilities: String): List<WifiSecurity> {
    val blocks = Regex("""\[([^\]]*)]""").findAll(capabilities).map { it.groupValues[1].uppercase() }.toList()
    val found = mutableSetOf<WifiSecurity>()
    for (block in blocks) {
        val protocol = block.substringBefore('-')
        val keys = block.substringAfter('-', "").substringBefore('-').split('+')
        val wpa1 = protocol == "WPA"
        when (protocol) {
            "WEP" -> found += WifiSecurity.Wep
            "WPA", "WPA2", "RSN" -> keys.forEach { key ->
                // Key names may carry a prefix, as in "FT/SAE" for fast roaming.
                found += when {
                    "SUITE_B" in key -> WifiSecurity.Wpa3Enterprise
                    "SAE" in key -> WifiSecurity.Wpa3
                    "OWE" in key -> WifiSecurity.Owe
                    "EAP" in key -> if (wpa1) WifiSecurity.WpaEnterprise else WifiSecurity.Wpa2Enterprise
                    wpa1 -> WifiSecurity.Wpa
                    else -> WifiSecurity.Wpa2
                }
            }
            "OWE_TRANSITION" -> found += WifiSecurity.Owe
        }
    }
    return if (found.isEmpty()) listOf(WifiSecurity.Open) else found.sorted()
}

/**
 * How strong a received signal is, from 0 (barely there) to 4 (excellent), for a signal-bars display.
 *
 * Wi-Fi and Bluetooth use the same scale: phones hear Bluetooth at similar levels at a shorter range.
 */
fun signalLevel(dbm: Int): Int = when {
    dbm >= -55 -> 4
    dbm >= -67 -> 3
    dbm >= -78 -> 2
    dbm >= -89 -> 1
    else -> 0
}

/**
 * True when a MAC address was made up by the device rather than burnt in by the maker: its "locally
 * administered" bit is set. Phones use such random addresses for privacy, and so do many hotspots.
 */
fun isRandomMac(mac: String): Boolean {
    val first = mac.take(2).toIntOrNull(16) ?: return false
    return first and 0x02 != 0
}

/**
 * A rough distance in metres to an access point, from its signal and frequency by the free-space path loss rule
 * (the same estimate common Wi-Fi analysers use). Walls and floors weaken the signal, so indoors the real distance
 * is often shorter; treat it as "near" or "far", not a measurement.
 */
fun wifiDistanceMeters(dbm: Int, frequencyMhz: Int): Float {
    if (frequencyMhz <= 0) return 0f
    val exponent = (FREE_SPACE_CONSTANT - 20 * kotlin.math.log10(frequencyMhz.toDouble()) + kotlin.math.abs(dbm)) / 20
    return Math.pow(10.0, exponent).toFloat().coerceIn(0.1f, 1_000f)
}

/** The free-space path loss constant for distances in metres and frequencies in MHz. */
private const val FREE_SPACE_CONSTANT = 27.55
