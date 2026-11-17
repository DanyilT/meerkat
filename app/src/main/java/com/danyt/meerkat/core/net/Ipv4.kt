package com.danyt.meerkat.core.net

import java.net.InetAddress

/** An IPv4 address held as its 32 bits, so addresses sort and compare in numeric order. */
@JvmInline
value class Ipv4(val bits: UInt) : Comparable<Ipv4> {
    override fun compareTo(other: Ipv4): Int = bits.compareTo(other.bits)

    /** The four bytes of the address, most significant first. */
    fun toBytes(): ByteArray = ByteArray(4) { (bits shr (24 - 8 * it)).toByte() }

    /** Converts to a platform address. Never performs a name lookup. */
    fun toInetAddress(): InetAddress = InetAddress.getByAddress(toBytes())

    override fun toString(): String = toBytes().joinToString(".") { (it.toInt() and 0xFF).toString() }

    companion object {
        /**
         * Parses dotted notation such as "192.168.1.10". Returns null for anything else.
         *
         * Host names are rejected rather than resolved, so parsing never touches the network.
         */
        fun parse(text: String): Ipv4? {
            val parts = text.split('.')
            if (parts.size != 4) return null
            var bits = 0u
            for (part in parts) {
                if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
                val octet = part.toInt()
                if (octet > 255) return null
                bits = (bits shl 8) or octet.toUInt()
            }
            return Ipv4(bits)
        }
    }
}

/** An inclusive range of addresses. [first] is never above [last]. */
data class IpRange(val first: Ipv4, val last: Ipv4) {
    init {
        require(first <= last) { "Range start is above its end" }
    }

    /** How many addresses the range holds. */
    val size: Long get() = (last.bits - first.bits).toLong() + 1

    /** True when [address] lies inside this range. */
    operator fun contains(address: Ipv4): Boolean = address >= first && address <= last

    /** True when every address of [other] lies inside this range. */
    fun containsAll(other: IpRange): Boolean = other.first >= first && other.last <= last

    /** Every address in the range, lowest first. */
    fun addresses(): Sequence<Ipv4> = (first.bits..last.bits).asSequence().map(::Ipv4)
}

/**
 * The range covered by the block that [address] belongs to with the given [prefixLength] (0 to 32).
 *
 * With [hostsOnly], the network and broadcast addresses are left out, as they are not devices. Blocks of one or
 * two addresses (/32 and /31) have neither, so they are returned whole.
 */
fun cidrRange(address: Ipv4, prefixLength: Int, hostsOnly: Boolean): IpRange {
    require(prefixLength in 0..32) { "Prefix length out of range" }
    val mask = if (prefixLength == 0) 0u else UInt.MAX_VALUE shl (32 - prefixLength)
    val network = address.bits and mask
    val broadcast = network or mask.inv()
    return if (hostsOnly && prefixLength < 31) {
        IpRange(Ipv4(network + 1u), Ipv4(broadcast - 1u))
    } else {
        IpRange(Ipv4(network), Ipv4(broadcast))
    }
}
