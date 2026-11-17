package com.danyt.meerkat.core.net

import java.util.Locale

internal const val NETBIOS_PORT = 137

/** Where the list of names starts in a node status response: header, question name, and record fields. */
private const val NAME_COUNT_OFFSET = 56
private const val NAME_ENTRY_SIZE = 18
private const val NAME_LENGTH = 15
private const val MAC_SIZE = 6
private const val GROUP_FLAG = 0x80

/** What a NetBIOS node status answer tells us about a machine. Either part can be missing. */
internal data class NetbiosInfo(val name: String?, val mac: String?)

/** Builds the NetBIOS "node status" query, which asks a machine for its names and adapter address. */
internal fun netbiosStatusQuery(): ByteArray {
    val packet = ByteArray(50)
    packet[5] = 1 // One question. Id and flags stay zero.
    packet[12] = 0x20 // Name length: 32 encoded characters.
    // The wildcard name "*" padded with zero bytes. Each byte becomes two letters: 'A' plus each half of it.
    packet[13] = 'C'.code.toByte()
    packet[14] = 'K'.code.toByte()
    for (index in 15..44) packet[index] = 'A'.code.toByte()
    packet[47] = 0x21 // Type: node status.
    packet[49] = 0x01 // Class: internet.
    return packet
}

/** Reads a node status response. Returns null when the packet is not one or is damaged. */
internal fun parseNetbiosStatus(packet: ByteArray): NetbiosInfo? {
    if (packet.size <= NAME_COUNT_OFFSET) return null
    val count = packet[NAME_COUNT_OFFSET].toInt() and 0xFF
    val namesStart = NAME_COUNT_OFFSET + 1
    val macStart = namesStart + count * NAME_ENTRY_SIZE
    if (packet.size < macStart) return null

    // The machine's own name is the first unique (not group) entry with suffix 0x00.
    val name = (0 until count).firstNotNullOfOrNull { index ->
        val entry = namesStart + index * NAME_ENTRY_SIZE
        val suffix = packet[entry + NAME_LENGTH].toInt() and 0xFF
        val isGroup = packet[entry + NAME_LENGTH + 1].toInt() and GROUP_FLAG != 0
        if (suffix != 0 || isGroup) return@firstNotNullOfOrNull null
        sanitizeHostname(String(packet, entry, NAME_LENGTH, Charsets.US_ASCII).trim())
    }
    val mac = if (packet.size >= macStart + MAC_SIZE) {
        val bytes = packet.copyOfRange(macStart, macStart + MAC_SIZE)
        // Samba and some others report all zeros, which is no address at all.
        if (bytes.all { it.toInt() == 0 }) null else bytes.joinToString(":") { String.format(Locale.US, "%02X", it) }
    } else {
        null
    }
    return if (name == null && mac == null) null else NetbiosInfo(name, mac)
}
