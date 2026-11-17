package com.danyt.meerkat.core.net

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

/** What name resolution found out about a host. Either part can be missing. */
data class HostIdentity(val hostname: String? = null, val mac: String? = null) {
    val isEmpty: Boolean get() = hostname == null && mac == null
}

/** Finds a name for a host that is known to be up. Blocks, so callers run it on an IO thread. */
fun interface NameResolver {
    /**
     * Looks up [address]. Each network step waits at most [timeoutMillis].
     *
     * @param dnsServer the DNS server to ask for the reverse record, or null for the ones the network gave
     *   the phone.
     */
    fun resolve(address: Ipv4, timeoutMillis: Int, dnsServer: Ipv4?): HostIdentity
}

/**
 * The real [NameResolver]. It asks, in order:
 * 1. a DNS server for a reverse (PTR) record: the server the network gave the phone, which is usually the
 *    router and often knows its DHCP clients, or the server the user chose,
 * 2. the host itself over mDNS (Apple devices, Linux, printers, many smart devices),
 * 3. the host itself over NetBIOS (Windows and Samba), which can also reveal its MAC address.
 *
 * The NetBIOS step always runs, because it is the only source of a MAC address.
 */
class NetworkNameResolver : NameResolver {

    override fun resolve(address: Ipv4, timeoutMillis: Int, dnsServer: Ipv4?): HostIdentity {
        val netbios = exchange(address, NETBIOS_PORT, netbiosStatusQuery(), timeoutMillis)?.let(::parseNetbiosStatus)
        val fromDns = if (dnsServer == null) reverseDns(address) else reverseDns(address, dnsServer, timeoutMillis)
        val hostname = fromDns ?: multicastDns(address, timeoutMillis) ?: netbios?.name
        return HostIdentity(hostname, netbios?.mac)
    }

    private fun reverseDns(address: Ipv4): String? {
        val name = try {
            address.toInetAddress().canonicalHostName
        } catch (_: SecurityException) {
            return null
        }
        // With no record, the platform hands the address back as text.
        return if (name == address.toString()) null else sanitizeHostname(name)
    }

    /** Asks one chosen server directly, with our own DNS packet, instead of going through the system. */
    private fun reverseDns(address: Ipv4, server: Ipv4, timeoutMillis: Int): String? =
        exchange(server, DNS_PORT, dnsQuery(reverseName(address), DNS_TYPE_PTR), timeoutMillis)?.let(::parsePtrAnswer)

    private fun multicastDns(address: Ipv4, timeoutMillis: Int): String? {
        val query = dnsQuery(reverseName(address), DNS_TYPE_PTR, multicastDns = true)
        return exchange(address, MDNS_PORT, query, timeoutMillis)?.let(::parsePtrAnswer)
    }

    /** Sends one datagram to the host and returns its answer, or null when none came. */
    private fun exchange(address: Ipv4, port: Int, payload: ByteArray, timeoutMillis: Int): ByteArray? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            socket.connect(address.toInetAddress(), port)
            socket.send(DatagramPacket(payload, payload.size))
            val reply = DatagramPacket(ByteArray(REPLY_SIZE), REPLY_SIZE)
            socket.receive(reply)
            reply.data.copyOf(reply.length)
        }
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val REPLY_SIZE = 1500
        const val DNS_PORT = 53
    }
}
