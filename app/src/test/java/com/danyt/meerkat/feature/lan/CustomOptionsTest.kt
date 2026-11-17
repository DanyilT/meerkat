package com.danyt.meerkat.feature.lan

import com.danyt.meerkat.core.net.parsePorts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomOptionsTest {

    @Test
    fun `parses ports, ranges, and repeats`() {
        assertEquals(listOf(22, 80, 8000, 8001, 8002), parsePorts(" 80, 22,8000-8002 , 80"))
        assertEquals(emptyList<Int>(), parsePorts("  "))
    }

    @Test
    fun `refuses malformed port lists`() {
        listOf("0", "65536", "80,", "a", "90-80", "1-2-3", "80 443", "-5").forEach { assertNull(it, parsePorts(it)) }
    }

    @Test
    fun `refuses more ports than allowed`() {
        assertNotNull(parsePorts("1-100", maxCount = 100))
        assertNull(parsePorts("1-101", maxCount = 100))
        assertNull(parsePorts("1-60, 100-160", maxCount = 100))
        assertNull(parsePorts("1-65535", maxCount = 100))
    }

    @Test
    fun `the defaults give a valid scan`() {
        val config = CustomOptions().config!!
        assertTrue(config.ping)
        assertEquals(8, config.tcpPorts.size)
        assertEquals(500, config.timeoutMillis)
    }

    @Test
    fun `invalid fields give no scan`() {
        assertNull(CustomOptions(tcpPorts = "http").config)
        assertNull(CustomOptions(udpPorts = "70000").config)
        assertNull(CustomOptions(timeout = "5").config)
        assertNull(CustomOptions(timeout = "").config)
        assertNull(CustomOptions(concurrency = "0").config)
        assertNull(CustomOptions(concurrency = "1000").config)
    }

    @Test
    fun `a scan needs at least one probe`() {
        val nothing = CustomOptions(ping = false, tcpPorts = "", udpPorts = "")
        assertTrue(nothing.hasNoProbe)
        assertNull(nothing.config)
        assertFalse(nothing.copy(udpPorts = "53").hasNoProbe)
        assertNotNull(nothing.copy(udpPorts = "53").config)
    }
}
