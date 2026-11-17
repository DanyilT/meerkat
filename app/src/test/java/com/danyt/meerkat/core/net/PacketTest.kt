package com.danyt.meerkat.core.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun label(text: String) = bytes(text.length) + text.toByteArray(Charsets.US_ASCII)

    /** A response with one question and one PTR answer whose name points back at the question with a pointer. */
    private fun ptrResponse(answerName: ByteArray): ByteArray {
        val header = bytes(0, 0, 0x84, 0, 0, 1, 0, 1, 0, 0, 0, 0)
        val question = label("10") + label("in-addr") + label("arpa") + bytes(0) + bytes(0, 12, 0, 1)
        val answer = bytes(0xC0, 12) + bytes(0, 12, 0, 1, 0, 0, 0, 60, 0, answerName.size) + answerName
        return header + question + answer
    }

    @Test
    fun `builds a DNS query`() {
        val expected = bytes(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) + label("a") + label("bc") + bytes(0, 0, 12, 0, 1)
        assertArrayEquals(expected, dnsQuery("a.bc", DNS_TYPE_PTR))
    }

    @Test
    fun `a multicast query asks for a direct answer and no recursion`() {
        val query = dnsQuery("a", DNS_TYPE_PTR, multicastDns = true)
        assertEquals(0, query.u16(2))
        assertEquals(0x8001, query.u16(query.size - 2))
    }

    @Test
    fun `builds the reverse lookup name`() {
        assertEquals("10.1.168.192.in-addr.arpa", reverseName(Ipv4.parse("192.168.1.10")!!))
    }

    @Test
    fun `reads a PTR answer`() {
        assertEquals("printer.local", parsePtrAnswer(ptrResponse(label("printer") + label("local") + bytes(0))))
    }

    @Test
    fun `follows a pointer inside the answer`() {
        // "nas" followed by a pointer to "arpa" in the question (offset 12 + 3 + 8).
        assertEquals("nas.arpa", parsePtrAnswer(ptrResponse(label("nas") + bytes(0xC0, 23))))
    }

    @Test
    fun `damaged DNS packets give no answer`() {
        val good = ptrResponse(label("printer") + label("local") + bytes(0))
        assertNull(parsePtrAnswer(ByteArray(0)))
        assertNull(parsePtrAnswer(good.copyOf(good.size - 4)))
        // An answer name that points at itself must not loop forever.
        assertNull(parsePtrAnswer(ptrResponse(bytes(0xC0, 41))))
    }

    @Test
    fun `host names from the network are cleaned`() {
        assertEquals("host.local", sanitizeHostname("ho\u0000st\n.local."))
        assertNull(sanitizeHostname("\u0007 \n"))
    }

    private fun netbiosResponse(mac: ByteArray): ByteArray {
        fun entry(name: String, suffix: Int, flags: Int) =
            name.padEnd(15).toByteArray(Charsets.US_ASCII) + bytes(suffix, flags, 0)
        val names = entry("WORKGROUP", 0x00, 0x80) + entry("DESKTOP-1", 0x00, 0x04) + entry("DESKTOP-1", 0x20, 0x04)
        return ByteArray(56) + bytes(3) + names + mac
    }

    @Test
    fun `builds the NetBIOS status query`() {
        val query = netbiosStatusQuery()
        assertEquals(50, query.size)
        assertEquals("CK" + "A".repeat(30), String(query, 13, 32, Charsets.US_ASCII))
        assertEquals(0x21, query.u16(46))
    }

    @Test
    fun `reads the machine name and MAC from a NetBIOS answer`() {
        val info = parseNetbiosStatus(netbiosResponse(bytes(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0xFE)))
        assertEquals(NetbiosInfo("DESKTOP-1", "00:1A:2B:3C:4D:FE"), info)
    }

    @Test
    fun `an all zero MAC is treated as missing`() {
        assertEquals(NetbiosInfo("DESKTOP-1", null), parseNetbiosStatus(netbiosResponse(ByteArray(6))))
    }

    @Test
    fun `damaged NetBIOS packets give no answer`() {
        val good = netbiosResponse(ByteArray(6))
        assertNull(parseNetbiosStatus(ByteArray(10)))
        assertNull(parseNetbiosStatus(good.copyOf(80)))
    }

    @Test
    fun `the ICMP echo request has a valid checksum`() {
        val packet = icmpEchoRequest(sequence = 7)
        assertEquals(8, packet[0].toInt())
        assertEquals(7, packet.u16(6))
        var sum = 0
        for (index in packet.indices step 2) sum += packet.u16(index)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }

    @Test
    fun `known UDP ports get a protocol request and others an empty datagram`() {
        assertEquals(48, udpPayload(123).size)
        assertArrayEquals(netbiosStatusQuery(), udpPayload(137))
        assertEquals(0, udpPayload(40000).size)
    }
}
