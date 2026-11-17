package com.danyt.meerkat.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortScanSessionTest {

    private fun ip(text: String) = Ipv4.parse(text)!!

    /** Ports 22 and 443 are open, ports below 100 are closed, everything else is silent. */
    private val prober = object : Prober {
        override fun ping(address: Ipv4, timeoutMillis: Int): Long? = null

        override fun tcp(address: Ipv4, port: Int, timeoutMillis: Int): PortResult {
            val state = when {
                port == 22 || port == 443 -> PortState.Open
                port < 100 -> PortState.Closed
                else -> PortState.Filtered
            }
            return PortResult(port, Protocol.Tcp, state)
        }

        override fun udp(address: Ipv4, port: Int, timeoutMillis: Int) =
            PortResult(port, Protocol.Udp, PortState.OpenOrFiltered)
    }

    @Test
    fun `counts every port and lists the open ones in order`() = runBlocking {
        val job = Job()
        val session = PortScanSession(PortScanner(prober), CoroutineScope(Dispatchers.Default + job))
        try {
            session.start(ip("10.0.0.5"), listOf(443, 8080, 22, 80, 21, 9000), Protocol.Tcp)
            val state = withTimeout(5_000) { session.state.first { it.status == ScanStatus.Finished } }

            assertEquals(ip("10.0.0.5"), state.host)
            assertEquals(listOf(22, 443), state.open.map { it.port })
            assertEquals(2, state.closed)
            assertEquals(2, state.silent)
            assertEquals(6, state.scanned)
            assertEquals(6, state.total)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `names common services per protocol`() {
        assertEquals("SSH", serviceName(22, Protocol.Tcp))
        assertEquals("NTP", serviceName(123, Protocol.Udp))
        assertNull(serviceName(123, Protocol.Tcp))
        assertNull(serviceName(40000, Protocol.Tcp))
    }

    @Test
    fun `the common ports are sorted and include the usual ones`() {
        val tcp = commonPorts(Protocol.Tcp)
        assertEquals(tcp.sorted(), tcp)
        assertTrue(tcp.containsAll(listOf(22, 80, 443, 445, 3389)))
        assertTrue(commonPorts(Protocol.Udp).containsAll(listOf(53, 123, 5353)))
    }

    @Test
    fun `knows which ranges reach into local address space`() {
        fun range(first: String, last: String) = IpRange(ip(first), ip(last))
        assertTrue(range("192.168.1.1", "192.168.1.254").touchesLocalNetwork())
        assertTrue(range("10.0.0.1", "10.0.0.1").touchesLocalNetwork())
        assertTrue(range("172.31.255.255", "172.32.0.5").touchesLocalNetwork())
        assertTrue(range("9.255.255.0", "11.0.0.1").touchesLocalNetwork())
        assertFalse(range("8.8.8.8", "8.8.8.8").touchesLocalNetwork())
        assertFalse(range("172.32.0.0", "172.32.0.9").touchesLocalNetwork())
    }
}
