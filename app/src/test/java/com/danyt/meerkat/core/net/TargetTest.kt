package com.danyt.meerkat.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTest {

    private fun ip(text: String) = Ipv4.parse(text)!!

    private fun range(first: String, last: String) = IpRange(ip(first), ip(last))

    private fun valid(text: String) = (parseTarget(text) as TargetResult.Valid).range

    private fun error(text: String) = (parseTarget(text) as TargetResult.Invalid).error

    @Test
    fun `parses and prints an address`() {
        assertEquals("192.168.1.10", ip("192.168.1.10").toString())
        assertEquals("255.255.255.255", ip("255.255.255.255").toString())
    }

    @Test
    fun `rejects text that is not an address`() {
        listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.x", "1..2.3", "router.local", "-1.2.3.4", "1.2.3.0004")
            .forEach { assertNull(it, Ipv4.parse(it)) }
    }

    @Test
    fun `addresses above 127 sort in numeric order`() {
        assertTrue(ip("10.0.0.1") < ip("192.168.0.1"))
        assertTrue(ip("192.168.0.9") < ip("192.168.0.10"))
    }

    @Test
    fun `a single address is a range of one`() {
        assertEquals(range("10.0.0.5", "10.0.0.5"), valid(" 10.0.0.5 "))
    }

    @Test
    fun `a CIDR block leaves out the network and broadcast addresses`() {
        val hosts = valid("192.168.1.77/24")
        assertEquals(range("192.168.1.1", "192.168.1.254"), hosts)
        assertEquals(254L, hosts.size)
    }

    @Test
    fun `tiny CIDR blocks are kept whole`() {
        assertEquals(range("10.0.0.4", "10.0.0.5"), valid("10.0.0.4/31"))
        assertEquals(range("10.0.0.4", "10.0.0.4"), valid("10.0.0.4/32"))
    }

    @Test
    fun `an area block keeps every address`() {
        val block = (parseTarget("10.0.0.0/8", hostsOnly = false, maxSize = Long.MAX_VALUE) as TargetResult.Valid).range
        assertEquals(range("10.0.0.0", "10.255.255.255"), block)
    }

    @Test
    fun `parses full and short ranges`() {
        assertEquals(range("192.168.1.10", "192.168.2.20"), valid("192.168.1.10 - 192.168.2.20"))
        assertEquals(range("192.168.1.10", "192.168.1.50"), valid("192.168.1.10-50"))
    }

    @Test
    fun `reports why a target is invalid`() {
        assertEquals(TargetError.Empty, error("   "))
        assertEquals(TargetError.Malformed, error("192.168.1.0/33"))
        assertEquals(TargetError.Malformed, error("192.168.1.10-"))
        assertEquals(TargetError.Malformed, error("example.com"))
        assertEquals(TargetError.Reversed, error("192.168.1.50-10"))
        assertEquals(TargetError.TooLarge, error("10.0.0.0/8"))
    }

    @Test
    fun `the size limit allows a 20 bit network and refuses a 19 bit one`() {
        assertEquals(4094L, valid("172.16.0.0/20").size)
        assertEquals(TargetError.TooLarge, error("172.16.0.0/19"))
    }

    @Test
    fun `a range lists its addresses in order`() {
        val addresses = range("10.0.0.254", "10.0.1.1").addresses().map { it.toString() }.toList()
        assertEquals(listOf("10.0.0.254", "10.0.0.255", "10.0.1.0", "10.0.1.1"), addresses)
    }

    @Test
    fun `a target is authorized only when one range covers all of it`() {
        val home = range("192.168.1.0", "192.168.1.255")
        val lab = range("10.0.0.0", "10.0.0.255")
        assertTrue(isAuthorized(range("192.168.1.1", "192.168.1.254"), listOf(lab, home)))
        assertFalse(isAuthorized(range("192.168.1.200", "192.168.2.5"), listOf(lab, home)))
        assertFalse(isAuthorized(range("8.8.8.8", "8.8.8.8"), listOf(lab, home)))
        assertFalse(isAuthorized(range("192.168.1.1", "192.168.1.1"), emptyList()))
    }

    @Test
    fun `the scope check counts own networks and saved ranges, and skips broken entries`() {
        val own = listOf(range("192.168.1.0", "192.168.1.255"))
        val saved = listOf("not a range", "10.0.0.0/8", "172.16.5.1-50")
        assertTrue(isInScope(valid("192.168.1.0/24"), saved, own))
        assertTrue(isInScope(valid("10.20.30.0/24"), saved, own))
        assertTrue(isInScope(valid("172.16.5.20"), saved, emptyList()))
        assertFalse(isInScope(valid("172.16.5.60"), saved, own))
        assertFalse(isInScope(valid("8.8.8.8"), saved, own))
        assertFalse(isInScope(valid("192.168.1.0/24"), emptyList(), emptyList()))
    }

    @Test
    fun `a slash with nothing after it means the single address`() {
        assertEquals(range("10.0.0.5", "10.0.0.5"), valid("10.0.0.5/"))
        assertEquals(TargetError.Empty, error("/"))
    }

    /** Types [keys] one at a time, as a keyboard would, and returns what the field shows. */
    private fun type(keys: String, allowRange: Boolean = true, start: String = ""): String =
        keys.fold(start) { text, key -> formatAddressInput(text, text + key, allowRange) }

    @Test
    fun `dots and the slash are added while typing digits`() {
        assertEquals("192.168.1", type("1921681"))
        assertEquals("192.168.100.", type("192168100"))
        assertEquals("192.168.1.100/", type("1921681.100"))
        assertEquals("192.168.1.100/24", type("1921681.10024"))
        // 26 and up cannot take a third digit, so the number is complete after two.
        assertEquals("10.0.0.99/", type("10.0.0.99"))
        // 16 could still become 160-169, so the user ends that number with a dot.
        assertEquals("172.16.5.20", type("17216.5.20"))
        assertEquals("172.165.", type("172165"))
    }

    @Test
    fun `a digit that would pass 255 starts the next number`() {
        assertEquals("25.6", type("256"))
        assertEquals("192.168.25.9", type("192168259"))
    }

    @Test
    fun `separators typed by hand are not doubled`() {
        assertEquals("192.168.1.1", type("192.168.1.1"))
        assertEquals("192.168.100.5", type("192.168.100.5"))
        assertEquals("192.168.1.100/24", type("192.168.1.100/24"))
    }

    @Test
    fun `a dot after the fourth number becomes the slash`() {
        assertEquals("192.168.1.10/24", type("192.168.1.10.24"))
        assertEquals("192.168.1.10", type("192.168.1.10.", allowRange = false))
        assertEquals("192.168.1.", type("192.168.1.."))
    }

    @Test
    fun `other characters are dropped, and ranges are left alone`() {
        assertEquals("192.168.1.10-50", type("192.168.1.10-50"))
        assertEquals("10.0.0.1", type("10a.0 .0x.1"))
        assertEquals("10.0.0.1", formatAddressInput("", "host 10.0.0.1!", allowRange = true))
    }

    @Test
    fun `a host field takes no slash or dash`() {
        assertEquals("192.168.1.100", type("1921681.100", allowRange = false))
        assertEquals("10.0.0.124", type("10.0.0.1/24", allowRange = false))
    }

    @Test
    fun `keystrokes that cannot lead to an address are refused`() {
        // The two cases from the bug report: endless digits after the slash, and after the fourth number.
        assertEquals("192.168.1.0/8", type("192.168.1.0/88888888888"))
        assertEquals("192.168.1.111", type("192.168.1.111111111111.222", allowRange = false))
        assertEquals("192.168.1.0/32", type("192.168.1.0/329"))
        assertEquals("192.168.1.0/3", type("192.168.1.0/33"))
        // The fifth dot is refused, so the 5 joins the fourth number.
        assertEquals("1.2.3.45", type("1.2.3.4.5", allowRange = false))
        assertEquals("192.168.1.10-192.168.1.50", type("192.168.1.10-192.168.1.50"))
        assertEquals("192.168.1.10-50", type("192.168.1.10-50-/9999"))
        assertEquals("", type("./-"))
        // A pasted address is taken whole when valid, and dropped when not.
        assertEquals("10.0.0.0/24", formatAddressInput("", "10.0.0.0/24", allowRange = true))
        assertEquals("10.0", formatAddressInput("10.0", "10.0.0.0.0.0", allowRange = true))
    }

    @Test
    fun `deleting and editing in the middle add nothing`() {
        assertEquals("192.168", formatAddressInput("192.168.", "192.168", allowRange = true))
        assertEquals("192.168.11.1", formatAddressInput("192.168.1.1", "192.168.11.1", allowRange = true))
        // An edit in the middle that breaks the address is refused.
        assertEquals("192.168.1.1", formatAddressInput("192.168.1.1", "1992.168.1.1", allowRange = true))
    }
}
