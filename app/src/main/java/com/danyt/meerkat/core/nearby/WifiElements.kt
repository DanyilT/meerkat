package com.danyt.meerkat.core.nearby

/** Whether an access point protects its management frames (deauthentication and the like) from forgery. */
enum class FrameProtection { Required, Optional, Off }

/**
 * Facts an access point announces about itself in the information elements of its beacons and probe answers.
 * Every field is optional: access points choose what to include, and many include little.
 *
 * @property manufacturer, [model], [modelNumber], [deviceName] what the Wi-Fi Protected Setup block says about
 *   the device. Usually the router's brand and model, as its maker filled them in.
 * @property chipVendors makers named by their own vendor blocks, often the Wi-Fi chip's maker.
 * @property stations how many devices are connected, when the access point says so.
 * @property channelLoad how busy the channel is, in percent, as the access point measures it.
 * @property country the country code it is set up for, such as "DE".
 * @property fastRoaming 802.11r: devices can move between access points of the network without signing in again.
 * @property neighborReports 802.11k: it can tell devices about nearby access points of the network.
 * @property steering 802.11v: it can ask devices to move to a better access point.
 * @property spatialStreams how many data streams it can receive at once (MIMO), from its Wi-Fi 4, 5, or 6
 *   capabilities. More streams mean more speed for devices that have as many antennas.
 * @property bssColor the Wi-Fi 6 "colour" (1 to 63) that tells its transmissions apart from a neighbour's on the
 *   same channel, or null when it has none or has switched it off.
 * @property multiLink Wi-Fi 7 multi-link: it can use several bands at once with one device.
 */
data class WifiExtras(
    val manufacturer: String? = null,
    val model: String? = null,
    val modelNumber: String? = null,
    val deviceName: String? = null,
    val chipVendors: List<String> = emptyList(),
    val stations: Int? = null,
    val channelLoad: Int? = null,
    val country: String? = null,
    val frameProtection: FrameProtection? = null,
    val fastRoaming: Boolean = false,
    val neighborReports: Boolean = false,
    val steering: Boolean = false,
    val spatialStreams: Int? = null,
    val bssColor: Int? = null,
    val multiLink: Boolean = false,
)

/**
 * One information element as an access point sends it. [extension] is the second-level ID of elements with
 * ID 255, which newer standards use; 0 for others. [data] is the content after the IDs.
 */
class WifiElement(val id: Int, val data: ByteArray, val extension: Int = 0)

/**
 * Reads the information elements of one access point.
 *
 * The data comes from any device in range, so nothing is trusted: every length is checked and text is cleaned.
 */
fun parseWifiElements(elements: List<WifiElement>): WifiExtras {
    var extras = WifiExtras()
    val vendors = linkedSetOf<String>()
    var streams = 0
    for (element in elements) {
        val data = element.data
        when (element.id) {
            ELEMENT_HT_CAPABILITIES -> if (data.size >= HT_MCS_OFFSET + 4) {
                // One byte of supported rates per stream; a stream is there when any rate is.
                streams = maxOf(streams, (0 until 4).count { data[HT_MCS_OFFSET + it].toInt() != 0 })
            }
            ELEMENT_VHT_CAPABILITIES -> if (data.size >= VHT_MCS_OFFSET + 2) {
                streams = maxOf(streams, streamsFromMcsMap(data, VHT_MCS_OFFSET))
            }
            ELEMENT_EXTENSION -> when (element.extension) {
                EXTENSION_HE_CAPABILITIES -> if (data.size >= HE_MCS_OFFSET + 2) {
                    streams = maxOf(streams, streamsFromMcsMap(data, HE_MCS_OFFSET))
                }
                EXTENSION_HE_OPERATION -> if (data.size > HE_COLOR_OFFSET) {
                    val color = data[HE_COLOR_OFFSET].toInt() and 0xFF
                    val disabled = color and BSS_COLOR_DISABLED != 0
                    extras = extras.copy(bssColor = (color and 0x3F).takeIf { !disabled && it != 0 })
                }
                EXTENSION_MULTI_LINK -> extras = extras.copy(multiLink = true)
            }
            ELEMENT_COUNTRY -> if (data.size >= 2) {
                val code = String(data, 0, 2, Charsets.US_ASCII)
                if (code.all { it in 'A'..'Z' }) extras = extras.copy(country = code)
            }
            ELEMENT_BSS_LOAD -> if (data.size >= 3) {
                val stations = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                val load = (data[2].toInt() and 0xFF) * 100 / 255
                extras = extras.copy(stations = stations, channelLoad = load)
            }
            ELEMENT_RSN -> rsnFrameProtection(data)?.let { extras = extras.copy(frameProtection = it) }
            ELEMENT_MOBILITY_DOMAIN -> extras = extras.copy(fastRoaming = true)
            ELEMENT_RADIO_MEASUREMENT -> if (data.isNotEmpty() && data[0].toInt() and NEIGHBOR_REPORT_BIT != 0) {
                extras = extras.copy(neighborReports = true)
            }
            ELEMENT_EXTENDED_CAPABILITIES -> if (data.size > 2 && data[2].toInt() and BSS_TRANSITION_BIT != 0) {
                extras = extras.copy(steering = true)
            }
            ELEMENT_VENDOR -> if (data.size >= 4) {
                val oui = data.copyOfRange(0, 3).toHex()
                if (oui == MICROSOFT_OUI && data[3].toInt() == WPS_TYPE) {
                    extras = withWps(extras, data.copyOfRange(4, data.size))
                } else {
                    ChipVendors[oui]?.let(vendors::add)
                }
            }
        }
    }
    return extras.copy(chipVendors = vendors.toList(), spatialStreams = streams.takeIf { it > 0 })
}

/**
 * Counts the streams in a Wi-Fi 5 or 6 rate map: two bits per stream, for up to eight streams, where 3 means
 * "not supported".
 */
private fun streamsFromMcsMap(data: ByteArray, offset: Int): Int {
    val map = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    return (0 until 8).lastOrNull { (map shr (it * 2)) and 0x3 != 0x3 }?.plus(1) ?: 0
}

/** The Wi-Fi Protected Setup block: attributes of a 2-byte type, a 2-byte length, and the value. */
private fun withWps(extras: WifiExtras, data: ByteArray): WifiExtras {
    var result = extras
    var at = 0
    while (at + 4 <= data.size) {
        val type = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
        val length = ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)
        val start = at + 4
        if (start + length > data.size) break
        val text = cleanName(String(data, start, length, Charsets.UTF_8)).ifEmpty { null }
        result = when (type) {
            WPS_MANUFACTURER -> result.copy(manufacturer = text)
            WPS_MODEL_NAME -> result.copy(model = text)
            WPS_MODEL_NUMBER -> result.copy(modelNumber = text)
            WPS_DEVICE_NAME -> result.copy(deviceName = text)
            else -> result
        }
        at = start + length
    }
    return result
}

/**
 * Reads the protected management frames bits from the RSN element: version, group cipher, the pairwise ciphers,
 * the key management suites, then the capabilities.
 */
private fun rsnFrameProtection(data: ByteArray): FrameProtection? {
    var at = 2 + 4 // Version and group cipher.
    fun count(): Int? {
        if (at + 2 > data.size) return null
        return ((data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)).also { at += 2 }
    }
    val pairwise = count() ?: return null
    at += pairwise * 4
    val keyManagement = count() ?: return null
    at += keyManagement * 4
    if (at + 2 > data.size) return null
    val capabilities = data[at].toInt() and 0xFF
    return when {
        capabilities and MFP_REQUIRED != 0 -> FrameProtection.Required
        capabilities and MFP_CAPABLE != 0 -> FrameProtection.Optional
        else -> FrameProtection.Off
    }
}

private const val ELEMENT_COUNTRY = 7
private const val ELEMENT_BSS_LOAD = 11
private const val ELEMENT_RSN = 48
private const val ELEMENT_MOBILITY_DOMAIN = 54
private const val ELEMENT_RADIO_MEASUREMENT = 70
private const val ELEMENT_EXTENDED_CAPABILITIES = 127
private const val ELEMENT_VENDOR = 221
private const val ELEMENT_HT_CAPABILITIES = 45
private const val ELEMENT_VHT_CAPABILITIES = 191
private const val ELEMENT_EXTENSION = 255
private const val EXTENSION_HE_CAPABILITIES = 35
private const val EXTENSION_HE_OPERATION = 36
private const val EXTENSION_MULTI_LINK = 107
private const val HT_MCS_OFFSET = 3 // After capability info (2 bytes) and A-MPDU parameters (1 byte).
private const val VHT_MCS_OFFSET = 4 // After capability info (4 bytes).
private const val HE_MCS_OFFSET = 17 // After MAC (6 bytes) and PHY (11 bytes) capabilities.
private const val HE_COLOR_OFFSET = 3 // After the operation parameters (3 bytes).
private const val BSS_COLOR_DISABLED = 0x80
private const val NEIGHBOR_REPORT_BIT = 0x02
private const val BSS_TRANSITION_BIT = 0x08 // Bit 19 of the field: byte 2, bit 3.
private const val MFP_REQUIRED = 0x40
private const val MFP_CAPABLE = 0x80
private const val MICROSOFT_OUI = "00:50:F2"
private const val WPS_TYPE = 4
private const val WPS_DEVICE_NAME = 0x1011
private const val WPS_MANUFACTURER = 0x1021
private const val WPS_MODEL_NAME = 0x1023
private const val WPS_MODEL_NUMBER = 0x1024

/** Makers that add vendor blocks of their own, by the IEEE company ID (OUI) those blocks start with. */
private val ChipVendors = mapOf(
    "00:10:18" to "Broadcom",
    "00:90:4C" to "Broadcom",
    "00:03:7F" to "Qualcomm Atheros",
    "8C:FD:F0" to "Qualcomm",
    "00:0C:E7" to "MediaTek",
    "00:E0:4C" to "Realtek",
    "00:17:F2" to "Apple",
    "00:40:96" to "Cisco",
    "00:0B:86" to "Aruba",
    "00:15:6D" to "Ubiquiti",
)
