package com.danyt.meerkat.core.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `Reed-Solomon matches the standard's worked example`() {
        // "01234567" at version 1-M: the data codewords and the 10 error correction codewords from the standard.
        val data = bytes(16, 32, 12, 86, 97, 128, 236, 17, 236, 17, 236, 17, 236, 17, 236, 17)
        val expected = bytes(165, 36, 212, 193, 237, 54, 199, 135, 44, 85)
        assertArrayEquals(expected, reedSolomonRemainder(data, reedSolomonDivisor(10)))
    }

    @Test
    fun `format bits match the standard's table`() {
        assertEquals(0b111011111000100, formatBits(ErrorCorrection.Low, 0))
        assertEquals(0b101010000010010, formatBits(ErrorCorrection.Medium, 0))
        assertEquals(0b011010101011111, formatBits(ErrorCorrection.Quartile, 0))
        assertEquals(0b001011010001001, formatBits(ErrorCorrection.High, 0))
        assertEquals(0b100000011001110, formatBits(ErrorCorrection.Medium, 5))
        assertEquals(0b100101010100000, formatBits(ErrorCorrection.Medium, 7))
        assertEquals(0b110011000101111, formatBits(ErrorCorrection.Low, 4))
        assertEquals(0b110100101110110, formatBits(ErrorCorrection.Low, 7))
    }

    @Test
    fun `version bits match the standard's table`() {
        assertEquals(0b000111110010010100, versionBits(7))
        assertEquals(0b001000010110111100, versionBits(8))
        assertEquals(0b101000110001101001, versionBits(40))
    }

    @Test
    fun `capacities match the standard`() {
        // Total codewords and data codewords per version and level, from the standard's tables.
        assertEquals(26, QrCode.rawDataModules(1) / 8)
        assertEquals(16, QrCode.dataCodewords(1, ErrorCorrection.Medium))
        assertEquals(134, QrCode.rawDataModules(5) / 8)
        assertEquals(86, QrCode.dataCodewords(5, ErrorCorrection.Medium))
        assertEquals(196, QrCode.rawDataModules(7) / 8)
        assertEquals(124, QrCode.dataCodewords(7, ErrorCorrection.Medium))
        assertEquals(3706, QrCode.rawDataModules(40) / 8)
        assertEquals(2334, QrCode.dataCodewords(40, ErrorCorrection.Medium))
        // 2331 bytes of text fit there once the mode and the 16-bit length are taken off.
        QrCode.encode(ByteArray(2331))
        assertThrows(IllegalArgumentException::class.java) { QrCode.encode(ByteArray(2332)) }
        for (version in 1..QrCode.MAX_VERSION) {
            ErrorCorrection.entries.forEach { assertTrue(QrCode.dataCodewords(version, it) > 0) }
        }
    }

    @Test
    fun `the smallest version that fits is chosen`() {
        assertEquals(1, QrCode.encode("HELLO WORLD").version) // 11 bytes fit the 14 of version 1-M.
        assertEquals(2, QrCode.encode("HELLO WORLD 1234").version)
        val wifi = wifiQrText("AndroidShare_1662", "wdyj3rh39nh6aya", "WPA")
        assertEquals(4, QrCode.encode(wifi).version)
        assertEquals(33, QrCode.encode(wifi).size)

    }

    @Test
    fun `fixed patterns are where readers look for them`() {
        val code = QrCode.encode(wifiQrText("Network", "secret", "WPA"))
        val last = code.size - 1
        // The three finder patterns: dark corners and a dark center, with a light ring between.
        listOf(0 to 0, last - 6 to 0, 0 to last - 6).forEach { (x, y) ->
            assertTrue(code[x, y] && code[x + 6, y + 6] && code[x + 3, y + 3])
            assertFalse(code[x + 1, y + 1])
        }
        // Timing patterns alternate between the finders.
        for (i in 8..last - 8) {
            assertEquals(i % 2 == 0, code[i, 6])
            assertEquals(i % 2 == 0, code[6, i])
        }
        assertTrue(code[8, last - 7]) // The dark module.
    }

    @Test
    fun `both copies of the format bits agree and name level M`() {
        val code = QrCode.encode("format check")
        val last = code.size - 1
        val first = (0..5).map { code[8, it] } + listOf(code[8, 7], code[8, 8], code[7, 8]) +
            (9..14).map { code[14 - it, 8] }
        val second = (0..7).map { code[last - it, 8] } + (8..14).map { code[8, last - 14 + it] }
        assertEquals(first, second)
        val value = first.foldIndexed(0) { index, acc, dark -> if (dark) acc or (1 shl index) else acc }
        val mask = (0..7).single { formatBits(ErrorCorrection.Medium, it) == value }
        assertEquals(mask, (0..7).single { formatBits(ErrorCorrection.Medium, it) == value })
    }

    @Test
    fun `Wi-Fi text escapes the characters that would break it`() {
        assertEquals("WIFI:T:WPA;S:Home;P:pass;;", wifiQrText("Home", "pass", "WPA"))
        assertEquals("""WIFI:T:WPA;S:My\;Net\,1;P:a\:b\"c\\d;;""", wifiQrText("My;Net,1", """a:b"c\d""", "WPA"))
        assertEquals("WIFI:T:nopass;S:Cafe;;", wifiQrText("Cafe", null, null))
    }
}
