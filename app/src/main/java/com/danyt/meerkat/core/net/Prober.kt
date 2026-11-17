package com.danyt.meerkat.core.net

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException

enum class Protocol { Tcp, Udp }

/**
 * What a probe learned about a port.
 *
 * - [Open]: something answered.
 * - [Closed]: the host answered that nothing listens there.
 * - [Filtered]: no answer at all (TCP). The host is off, or a firewall drops the traffic.
 * - [OpenOrFiltered]: no answer (UDP). UDP services often stay silent, so this cannot be told apart from filtered.
 */
enum class PortState { Open, Closed, Filtered, OpenOrFiltered }

/** The result of probing one port. [latencyMillis] is set only when the host answered. */
data class PortResult(
    val port: Int,
    val protocol: Protocol,
    val state: PortState,
    val latencyMillis: Long? = null,
) {
    /** True when the result proves a machine is there: an open port, or an active refusal. */
    val hostResponded: Boolean get() = state == PortState.Open || state == PortState.Closed
}

/**
 * Sends single probes. Every call blocks for at most about the given timeout, so callers run it on an IO thread.
 *
 * These are the scan types an app can do without root. A SYN ("half-open") scan needs raw sockets, which
 * Android gives only to root, so it is not offered.
 */
interface Prober {
    /** ICMP echo ("ping"). Returns the round-trip time in milliseconds, or null when there was no reply. */
    fun ping(address: Ipv4, timeoutMillis: Int): Long?

    /** TCP connect: a full connection that is closed again at once. */
    fun tcp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult

    /** UDP: sends one datagram, shaped for the usual service on that port, and waits for an answer or a refusal. */
    fun udp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult
}

/** The real [Prober], using ordinary sockets. */
class SocketProber : Prober {

    override fun ping(address: Ipv4, timeoutMillis: Int): Long? {
        // Setting the receive timeout on a raw file descriptor needs Android 10. Older versions use the
        // platform check instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return pingFallback(address, timeoutMillis)
        // Android lets apps open ICMP datagram sockets: real echo requests without raw-socket rights.
        val socket = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
        } catch (_: ErrnoException) {
            return pingFallback(address, timeoutMillis)
        }
        try {
            val timeout = StructTimeval.fromMillis(timeoutMillis.toLong())
            Os.setsockoptTimeval(socket, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, timeout)
            // Connecting makes the kernel hand us only replies from this address.
            Os.connect(socket, address.toInetAddress(), 0)
            val request = icmpEchoRequest(sequence = 1)
            val start = System.nanoTime()
            Os.write(socket, request, 0, request.size)
            val reply = ByteArray(64)
            val read = Os.read(socket, reply, 0, reply.size)
            return if (read >= ICMP_HEADER_SIZE && reply[0].toInt() == ICMP_ECHO_REPLY) millisSince(start) else null
        } catch (_: ErrnoException) {
            return null // Timed out, or the network reported the host unreachable.
        } catch (_: IOException) {
            return null
        } finally {
            try {
                Os.close(socket)
            } catch (_: ErrnoException) {
                // Nothing useful to do if closing fails.
            }
        }
    }

    /** For the rare device that forbids ICMP sockets: the platform check, which pings or tries TCP port 7. */
    private fun pingFallback(address: Ipv4, timeoutMillis: Int): Long? {
        val start = System.nanoTime()
        return try {
            if (address.toInetAddress().isReachable(timeoutMillis)) millisSince(start) else null
        } catch (_: IOException) {
            null
        }
    }

    override fun tcp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult {
        val start = System.nanoTime()
        val state = try {
            Socket().use { it.connect(InetSocketAddress(address.toInetAddress(), port), timeoutMillis) }
            PortState.Open
        } catch (_: SocketTimeoutException) {
            PortState.Filtered
        } catch (e: IOException) {
            if (e.isConnectionRefused()) PortState.Closed else PortState.Filtered
        }
        val latency = if (state == PortState.Filtered) null else millisSince(start)
        return PortResult(port, Protocol.Tcp, state, latency)
    }

    override fun udp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult {
        val start = System.nanoTime()
        val state = try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis
                // A connected socket is told when the host refuses the port.
                socket.connect(address.toInetAddress(), port)
                val payload = udpPayload(port)
                socket.send(DatagramPacket(payload, payload.size))
                socket.receive(DatagramPacket(ByteArray(UDP_REPLY_SIZE), UDP_REPLY_SIZE))
            }
            PortState.Open
        } catch (_: PortUnreachableException) {
            PortState.Closed
        } catch (_: SocketTimeoutException) {
            PortState.OpenOrFiltered
        } catch (_: IOException) {
            PortState.Filtered
        }
        val latency = if (state == PortState.Open || state == PortState.Closed) millisSince(start) else null
        return PortResult(port, Protocol.Udp, state, latency)
    }
}

private const val ICMP_ECHO_REQUEST = 8
private const val ICMP_ECHO_REPLY = 0
private const val ICMP_HEADER_SIZE = 8
private const val ICMP_PAYLOAD_SIZE = 8
private const val UDP_REPLY_SIZE = 1024

private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

/** Android and desktop Java word a refused connection differently; both mention it in the message. */
private fun Throwable.isConnectionRefused(): Boolean {
    val text = message ?: return false
    return text.contains("ECONNREFUSED") || text.contains("refused", ignoreCase = true)
}

/** Builds an ICMP echo request with a valid checksum. The kernel fills in the identifier. */
internal fun icmpEchoRequest(sequence: Int): ByteArray {
    val packet = ByteArray(ICMP_HEADER_SIZE + ICMP_PAYLOAD_SIZE)
    packet[0] = ICMP_ECHO_REQUEST.toByte()
    packet[6] = (sequence shr 8).toByte()
    packet[7] = sequence.toByte()
    var sum = 0
    for (index in packet.indices step 2) sum += packet.u16(index)
    while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
    val checksum = sum.inv() and 0xFFFF
    packet[2] = (checksum shr 8).toByte()
    packet[3] = checksum.toByte()
    return packet
}

/**
 * The datagram to send to a UDP [port]. Most services ignore data they do not understand, so well-known ports
 * get a real request for their protocol. Other ports get an empty datagram.
 */
internal fun udpPayload(port: Int): ByteArray = when (port) {
    53 -> dnsQuery("", DNS_TYPE_NS)
    123 -> ByteArray(48).also { it[0] = 0x1B } // NTP version 3, client mode.
    NETBIOS_PORT -> netbiosStatusQuery()
    1900 -> SSDP_SEARCH.toByteArray(Charsets.US_ASCII)
    MDNS_PORT -> dnsQuery("_services._dns-sd._udp.local", DNS_TYPE_PTR, multicastDns = true)
    else -> ByteArray(0)
}

internal const val MDNS_PORT = 5353

private const val SSDP_SEARCH =
    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n"
