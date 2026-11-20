package com.danyt.meerkat.core.net

import com.danyt.meerkat.core.text.isHiddenControl
import java.io.ByteArrayOutputStream

internal const val DNS_TYPE_NS = 2
internal const val DNS_TYPE_PTR = 12

private const val DNS_HEADER_SIZE = 12
private const val MAX_POINTER_JUMPS = 16
private const val MAX_HOSTNAME_LENGTH = 253

/**
 * Builds a DNS query with one question.
 *
 * With [multicastDns], the packet follows mDNS rules instead: no recursion flag, and the "unicast response"
 * bit set so the answer comes straight back to us.
 */
internal fun dnsQuery(name: String, type: Int, multicastDns: Boolean = false): ByteArray {
    val out = ByteArrayOutputStream()
    fun u16(value: Int) {
        out.write(value shr 8)
        out.write(value)
    }
    u16(0) // Id. Each query uses its own socket, so answers need no matching.
    u16(if (multicastDns) 0 else 0x0100) // Flags: recursion desired.
    u16(1) // One question.
    repeat(3) { u16(0) }
    name.split('.').filter { it.isNotEmpty() }.forEach { label ->
        val bytes = label.toByteArray(Charsets.US_ASCII)
        out.write(bytes.size)
        out.write(bytes)
    }
    out.write(0)
    u16(type)
    u16(if (multicastDns) 0x8001 else 0x0001) // Class IN.
    return out.toByteArray()
}

/** The name to ask for when looking up the host name of [address]: "10.1.168.192.in-addr.arpa". */
internal fun reverseName(address: Ipv4): String =
    address.toBytes().reversed().joinToString(".") { (it.toInt() and 0xFF).toString() } + ".in-addr.arpa"

/** Reads the first PTR answer out of a DNS response. Returns null when there is none or the packet is damaged. */
internal fun parsePtrAnswer(packet: ByteArray): String? {
    try {
        if (packet.size < DNS_HEADER_SIZE) return null
        var position = DNS_HEADER_SIZE
        repeat(packet.u16(4)) { position = skipName(packet, position) + 4 }
        repeat(packet.u16(6)) {
            position = skipName(packet, position)
            val type = packet.u16(position)
            val length = packet.u16(position + 8)
            position += 10
            if (type == DNS_TYPE_PTR) return sanitizeHostname(readName(packet, position))
            position += length
        }
    } catch (_: IndexOutOfBoundsException) {
        // A truncated or hostile packet. Treat it as no answer.
    }
    return null
}

/**
 * Cleans a name that came from the network before it is shown: printable characters only (no direction marks
 * that could make it read as another name), no trailing dot,
 * limited length. Returns null when nothing usable is left.
 */
internal fun sanitizeHostname(name: String): String? {
    val clean = name.filter { (it.code in 0x21..0x7E || it.code > 0xA0) && !it.isHiddenControl() }
        .trimEnd('.').take(MAX_HOSTNAME_LENGTH)
    return clean.ifEmpty { null }
}

/** Reads the two bytes at [index] as an unsigned number, most significant byte first. */
internal fun ByteArray.u16(index: Int): Int =
    ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)

private fun skipName(packet: ByteArray, start: Int): Int {
    var position = start
    while (true) {
        val length = packet[position].toInt() and 0xFF
        when {
            length == 0 -> return position + 1
            length and 0xC0 == 0xC0 -> return position + 2
            else -> position += length + 1
        }
    }
}

private fun readName(packet: ByteArray, start: Int): String {
    val labels = mutableListOf<String>()
    var position = start
    var jumps = 0
    while (true) {
        val length = packet[position].toInt() and 0xFF
        when {
            length == 0 -> break
            // A pointer to an earlier part of the packet. The limit stops a packet that points at itself.
            length and 0xC0 == 0xC0 -> {
                if (++jumps > MAX_POINTER_JUMPS) throw IndexOutOfBoundsException("Too many name pointers")
                position = ((length and 0x3F) shl 8) or (packet[position + 1].toInt() and 0xFF)
            }
            else -> {
                labels += String(packet, position + 1, length, Charsets.UTF_8)
                position += length + 1
            }
        }
    }
    return labels.joinToString(".")
}
