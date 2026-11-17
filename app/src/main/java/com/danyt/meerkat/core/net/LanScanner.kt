package com.danyt.meerkat.core.net

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * How a network scan probes each address. A host counts as found when any enabled probe gets an answer from it.
 *
 * @property ping send an ICMP echo request.
 * @property tcpPorts ports to try with a TCP connect. An open port or a refusal both prove the host is up.
 * @property udpPorts ports to try with UDP. An answer or a refusal proves the host is up; silence proves nothing.
 * @property timeoutMillis how long each probe waits.
 * @property concurrency how many probes run at the same time.
 * @property dnsServer the DNS server to ask for names, or null for the network's own.
 * @property resolveNames look up a host name (and MAC address, where NetBIOS offers it) for every host found.
 */
data class ScanConfig(
    val ping: Boolean = true,
    val tcpPorts: List<Int> = QuickTcpPorts,
    val udpPorts: List<Int> = emptyList(),
    val timeoutMillis: Int = 500,
    val concurrency: Int = 64,
    val resolveNames: Boolean = false,
    val dnsServer: Ipv4? = null,
) {
    init {
        require(timeoutMillis > 0) { "Timeout must be positive" }
        require(concurrency > 0) { "Concurrency must be positive" }
        require(ping || tcpPorts.isNotEmpty() || udpPorts.isNotEmpty()) { "At least one probe is needed" }
    }

    companion object {
        /** Ping plus a handful of TCP ports. Finds most devices in a few seconds. */
        val Quick = ScanConfig(concurrency = 256)

        /** More TCP ports, common UDP services, and name resolution. Slower, finds and identifies more. */
        val Full = ScanConfig(
            tcpPorts = FullTcpPorts,
            udpPorts = FullUdpPorts,
            concurrency = 256,
            resolveNames = true,
        )
    }
}

/** SSH, DNS, HTTP, HTTPS, SMB, RDP, alternative HTTP, and the iPhone sync port. */
val QuickTcpPorts = listOf(22, 53, 80, 443, 445, 3389, 8080, 62078)

val FullTcpPorts = listOf(
    21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 548, 554, 631, 993, 995, 1883, 3306, 3389, 5000, 5432,
    5900, 7000, 8000, 8008, 8080, 8443, 8888, 9100, 32400, 62078,
)

/** DNS, NTP, NetBIOS, SSDP, and mDNS. */
val FullUdpPorts = listOf(53, 123, 137, 1900, 5353)

/**
 * A host that answered.
 *
 * @property latencyMillis the ping time, or the fastest port answer when ping got no reply.
 * @property ports the open ports found, lowest first. Closed and silent ports are left out.
 */
data class Device(
    val address: Ipv4,
    val hostname: String? = null,
    val mac: String? = null,
    val latencyMillis: Long? = null,
    val ports: List<PortResult> = emptyList(),
)

/** What a running scan reports. */
sealed interface ScanEvent {
    /** [scanned] of [total] addresses are finished. */
    data class Progress(val scanned: Int, val total: Int) : ScanEvent

    /** A host was found. Sent again for the same address when its name has been resolved. */
    data class Found(val device: Device) : ScanEvent
}

/** Scans a range of addresses for hosts. Holds no state; every [scan] call is independent. */
class LanScanner(
    private val prober: Prober,
    private val resolver: NameResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Scans [target] and reports hosts as they are found, in no particular order.
     *
     * The flow is cold: nothing is sent on the network until it is collected, and cancelling the collector
     * stops the scan. Probes already on the wire finish on their own within [ScanConfig.timeoutMillis].
     */
    fun scan(target: IpRange, config: ScanConfig): Flow<ScanEvent> = channelFlow {
        val total = target.size.toInt()
        val probeThreads = dispatcher.limitedParallelism(config.concurrency)
        // All probes for one host run together, so limit hosts to keep the probe count near the setting.
        val probesPerHost = (if (config.ping) 1 else 0) + config.tcpPorts.size + config.udpPorts.size
        val hostSlots = Semaphore((config.concurrency / probesPerHost).coerceAtLeast(1))
        val scanned = AtomicInteger()

        send(ScanEvent.Progress(0, total))
        target.addresses().forEach { address ->
            launch(probeThreads) {
                val device = hostSlots.withPermit { probeHost(address, config) }
                if (device != null) send(ScanEvent.Found(device))
                send(ScanEvent.Progress(scanned.incrementAndGet(), total))
                if (device != null && config.resolveNames) {
                    val identity = resolver.resolve(address, config.timeoutMillis, config.dnsServer)
                    if (!identity.isEmpty) {
                        send(ScanEvent.Found(device.copy(hostname = identity.hostname, mac = identity.mac)))
                    }
                }
            }
        }
    }

    private suspend fun probeHost(address: Ipv4, config: ScanConfig): Device? = coroutineScope {
        val timeout = config.timeoutMillis
        val ping = async { if (config.ping) prober.ping(address, timeout) else null }
        val probes = config.tcpPorts.map { async { prober.tcp(address, it, timeout) } } +
            config.udpPorts.map { async { prober.udp(address, it, timeout) } }
        val pingMillis = ping.await()
        val results = probes.awaitAll()
        if (pingMillis == null && results.none { it.hostResponded }) return@coroutineScope null
        Device(
            address = address,
            latencyMillis = pingMillis ?: results.mapNotNull { it.latencyMillis }.minOrNull(),
            ports = results.filter { it.state == PortState.Open }.sortedBy { it.port },
        )
    }
}

/** Scans the ports of one host. */
class PortScanner(private val prober: Prober, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    /**
     * Probes every port in [ports] on [address] and reports each result as it arrives, in no particular order.
     * One result is sent per port, whatever its state, so the number received is the progress.
     *
     * The flow is cold, and cancelling the collector stops the scan.
     */
    fun scan(
        address: Ipv4,
        ports: List<Int>,
        protocol: Protocol = Protocol.Tcp,
        timeoutMillis: Int = 500,
        concurrency: Int = 64,
    ): Flow<PortResult> = channelFlow {
        val probeThreads = dispatcher.limitedParallelism(concurrency)
        val slots = Semaphore(concurrency)
        ports.forEach { port ->
            launch(probeThreads) {
                val result = slots.withPermit {
                    when (protocol) {
                        Protocol.Tcp -> prober.tcp(address, port, timeoutMillis)
                        Protocol.Udp -> prober.udp(address, port, timeoutMillis)
                    }
                }
                send(result)
            }
        }
    }
}
