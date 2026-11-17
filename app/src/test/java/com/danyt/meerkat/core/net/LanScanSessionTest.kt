package com.danyt.meerkat.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanScanSessionTest {

    private fun ip(text: String) = Ipv4.parse(text)!!

    /** Hosts whose last number is even answer ping. */
    private val prober = object : Prober {
        override fun ping(address: Ipv4, timeoutMillis: Int): Long? = if (address.bits % 2u == 0u) 1 else null

        override fun tcp(address: Ipv4, port: Int, timeoutMillis: Int) =
            PortResult(port, Protocol.Tcp, PortState.Filtered)

        override fun udp(address: Ipv4, port: Int, timeoutMillis: Int) =
            PortResult(port, Protocol.Udp, PortState.OpenOrFiltered)
    }

    private fun withSession(block: suspend (LanScanSession) -> Unit) = runBlocking {
        val job = Job()
        val scanner = LanScanner(prober, NameResolver { _, _, _ -> HostIdentity() })
        try {
            block(LanScanSession(scanner, CoroutineScope(Dispatchers.Default + job)))
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun LanScanSession.awaitFinished() =
        withTimeout(5_000) { state.first { it.status == ScanStatus.Finished } }

    @Test
    fun `starts idle with no target`() = withSession { session ->
        assertEquals(ScanStatus.Idle, session.state.value.status)
        assertNull(session.state.value.target)
    }

    @Test
    fun `collects devices sorted by address and finishes`() = withSession { session ->
        session.start("10.0.0.1-12", IpRange(ip("10.0.0.1"), ip("10.0.0.12")), ScanConfig(tcpPorts = emptyList()))

        val state = session.awaitFinished()

        assertEquals("10.0.0.1-12", state.target)
        assertEquals(12, state.scanned)
        assertEquals(12, state.total)
        val expected = listOf("10.0.0.2", "10.0.0.4", "10.0.0.6", "10.0.0.8", "10.0.0.10", "10.0.0.12")
        assertEquals(expected, state.devices.map { it.address.toString() })
    }

    @Test
    fun `a new scan replaces the results of the last one`() = withSession { session ->
        val config = ScanConfig(tcpPorts = emptyList())
        session.start("first", IpRange(ip("10.0.0.1"), ip("10.0.0.8")), config)
        session.awaitFinished()

        session.start("second", IpRange(ip("10.0.1.2"), ip("10.0.1.2")), config)
        val state = session.awaitFinished()

        assertEquals("second", state.target)
        assertEquals(listOf("10.0.1.2"), state.devices.map { it.address.toString() })
    }

    @Test
    fun `cancelling keeps the status and does nothing when idle`() = withSession { session ->
        session.cancel()
        assertEquals(ScanStatus.Idle, session.state.value.status)

        session.start("done", IpRange(ip("10.0.0.2"), ip("10.0.0.2")), ScanConfig(tcpPorts = emptyList()))
        session.awaitFinished()
        session.cancel()
        assertEquals(ScanStatus.Finished, session.state.value.status)
    }
}
