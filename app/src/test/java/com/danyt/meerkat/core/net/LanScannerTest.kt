package com.danyt.meerkat.core.net

import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScannerTest {

    private fun ip(text: String) = Ipv4.parse(text)!!

    /** A pretend network: which hosts answer ping, and the state of the ports that are not simply silent. */
    private class FakeProber(
        private val pingable: Map<Ipv4, Long> = emptyMap(),
        private val tcp: Map<Pair<Ipv4, Int>, PortState> = emptyMap(),
        private val udp: Map<Pair<Ipv4, Int>, PortState> = emptyMap(),
    ) : Prober {
        val calls = ConcurrentLinkedQueue<String>()

        override fun ping(address: Ipv4, timeoutMillis: Int): Long? {
            calls += "ping $address"
            return pingable[address]
        }

        override fun tcp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult {
            calls += "tcp $address:$port"
            val state = tcp[address to port] ?: PortState.Filtered
            return PortResult(port, Protocol.Tcp, state, if (state == PortState.Filtered) null else 9)
        }

        override fun udp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult {
            calls += "udp $address:$port"
            val state = udp[address to port] ?: PortState.OpenOrFiltered
            return PortResult(port, Protocol.Udp, state, if (state == PortState.OpenOrFiltered) null else 9)
        }
    }

    private val noNames = NameResolver { _, _, _ -> HostIdentity() }
    private val target = IpRange(ip("10.0.0.1"), ip("10.0.0.6"))

    private fun scan(prober: Prober, config: ScanConfig, resolver: NameResolver = noNames) = runBlocking {
        LanScanner(prober, resolver).scan(target, config).toList()
    }

    private fun List<ScanEvent>.devices() =
        filterIsInstance<ScanEvent.Found>().map { it.device }.sortedBy { it.address }

    @Test
    fun `finds hosts by ping, open port, refused port, and UDP answer`() {
        val prober = FakeProber(
            pingable = mapOf(ip("10.0.0.1") to 3L),
            tcp = mapOf((ip("10.0.0.2") to 80) to PortState.Open, (ip("10.0.0.3") to 22) to PortState.Closed),
            udp = mapOf((ip("10.0.0.4") to 53) to PortState.Open, (ip("10.0.0.5") to 53) to PortState.Closed),
        )
        val config = ScanConfig(tcpPorts = listOf(80, 22), udpPorts = listOf(53))

        val devices = scan(prober, config).devices()

        val found = devices.map { it.address.toString() }
        assertEquals(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5"), found)
        assertEquals(3L, devices[0].latencyMillis)
        assertEquals(listOf(80), devices[1].ports.map { it.port })
        // A refusal proves the host is up, but a closed port is not listed.
        assertTrue(devices[2].ports.isEmpty())
        assertEquals(9L, devices[2].latencyMillis)
        assertEquals(Protocol.Udp, devices[3].ports.single().protocol)
    }

    @Test
    fun `silent hosts are not reported`() {
        assertTrue(scan(FakeProber(), ScanConfig.Quick).devices().isEmpty())
    }

    @Test
    fun `progress starts at zero and ends at the total`() {
        val progress = scan(FakeProber(), ScanConfig.Quick).filterIsInstance<ScanEvent.Progress>()
        assertEquals(ScanEvent.Progress(0, 6), progress.first())
        assertEquals((0..6).toList(), progress.map { it.scanned }.sorted())
    }

    @Test
    fun `only the enabled probes are sent`() {
        val prober = FakeProber()
        scan(prober, ScanConfig(ping = false, tcpPorts = listOf(443), udpPorts = emptyList()))
        assertEquals(6, prober.calls.size)
        assertTrue(prober.calls.all { it.startsWith("tcp ") && it.endsWith(":443") })
    }

    @Test
    fun `names are resolved only for hosts that were found`() {
        val asked = ConcurrentLinkedQueue<Ipv4>()
        val resolver = NameResolver { address, _, _ ->
            asked += address
            HostIdentity("nas.local", "00:11:22:33:44:55")
        }
        val prober = FakeProber(pingable = mapOf(ip("10.0.0.4") to 1L))

        val found = scan(prober, ScanConfig(resolveNames = true), resolver).filterIsInstance<ScanEvent.Found>()

        assertEquals(listOf(ip("10.0.0.4")), asked.toList())
        // First the bare host, then the same host again with its name.
        assertNull(found[0].device.hostname)
        assertEquals("nas.local", found[1].device.hostname)
        assertEquals("00:11:22:33:44:55", found[1].device.mac)
    }

    @Test
    fun `the port scanner reports every port once`() = runBlocking {
        val host = ip("10.0.0.2")
        val prober = FakeProber(tcp = mapOf((host to 80) to PortState.Open, (host to 81) to PortState.Closed))

        val results = PortScanner(prober).scan(host, (79..82).toList(), concurrency = 2).toList().sortedBy { it.port }

        assertEquals(listOf(79, 80, 81, 82), results.map { it.port })
        assertEquals(
            listOf(PortState.Filtered, PortState.Open, PortState.Closed, PortState.Filtered),
            results.map { it.state },
        )
    }

    @Test
    fun `a real TCP probe tells an open port from a closed one`() {
        val loopback = ip("127.0.0.1")
        val prober = SocketProber()
        ServerSocket(0).use { server ->
            assertEquals(PortState.Open, prober.tcp(loopback, server.localPort, 1000).state)
        }
        // A port that was just released: nothing listens there, so the connection is refused.
        val closedPort = ServerSocket(0).use { it.localPort }
        assertEquals(PortState.Closed, prober.tcp(loopback, closedPort, 1000).state)
    }
}
