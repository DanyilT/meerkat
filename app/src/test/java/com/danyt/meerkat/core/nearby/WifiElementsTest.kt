package com.danyt.meerkat.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiElementsTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun wpsAttribute(type: Int, text: String): ByteArray {
        val value = text.toByteArray()
        return bytes(type shr 8, type and 0xFF, value.size shr 8, value.size and 0xFF) + value
    }

    @Test
    fun wpsGivesBrandAndModel() {
        val wps = bytes(0x00, 0x50, 0xF2, 0x04) +
            wpsAttribute(0x104A, "\u0010") + // Version: ignored.
            wpsAttribute(0x1021, "ExampleCorp") +
            wpsAttribute(0x1023, "Router X") +
            wpsAttribute(0x1024, "RX-200") +
            wpsAttribute(0x1011, "Living room\u0000")
        val extras = parseWifiElements(listOf(WifiElement(221, wps)))
        assertEquals("ExampleCorp", extras.manufacturer)
        assertEquals("Router X", extras.model)
        assertEquals("RX-200", extras.modelNumber)
        assertEquals("Living room", extras.deviceName)
    }

    @Test
    fun truncatedWpsKeepsWhatCameBefore() {
        val wps = bytes(0x00, 0x50, 0xF2, 0x04) + wpsAttribute(0x1021, "Maker") + bytes(0x10, 0x23, 0x00, 0x40, 0x41)
        val extras = parseWifiElements(listOf(WifiElement(221, wps)))
        assertEquals("Maker", extras.manufacturer)
        assertEquals(null, extras.model)
    }

    @Test
    fun loadCountryAndFeatures() {
        val rsn = bytes(
            0x01, 0x00, // Version.
            0x00, 0x0F, 0xAC, 0x04, // Group cipher.
            0x01, 0x00, 0x00, 0x0F, 0xAC, 0x04, // One pairwise cipher.
            0x01, 0x00, 0x00, 0x0F, 0xAC, 0x08, // One key management suite (SAE).
            0xC0, 0x00, // Capabilities: frame protection capable and required.
        )
        val extras = parseWifiElements(
            listOf(
                WifiElement(7, bytes('D'.code, 'E'.code, ' '.code)),
                WifiElement(11, bytes(12, 0, 128, 0, 0)),
                WifiElement(48, rsn),
                WifiElement(54, bytes(0x12, 0x34, 0x01)),
                WifiElement(70, bytes(0x02, 0, 0, 0, 0)),
                WifiElement(127, bytes(0, 0, 0x08)),
                WifiElement(221, bytes(0x00, 0x10, 0x18, 0x02, 0x00)),
            ),
        )
        assertEquals("DE", extras.country)
        assertEquals(12, extras.stations)
        assertEquals(50, extras.channelLoad)
        assertEquals(FrameProtection.Required, extras.frameProtection)
        assertTrue(extras.fastRoaming)
        assertTrue(extras.neighborReports)
        assertTrue(extras.steering)
        assertEquals(listOf("Broadcom"), extras.chipVendors)
    }

    @Test
    fun shortElementsAreIgnored() {
        val extras = parseWifiElements(
            listOf(
                WifiElement(7, bytes(0x44)),
                WifiElement(11, bytes(1)),
                WifiElement(48, bytes(1, 0)),
                WifiElement(221, bytes(0)),
            ),
        )
        assertEquals(WifiExtras(), extras)
        assertFalse(extras.fastRoaming)
    }

    @Test
    fun streamsColourAndMultiLink() {
        // Wi-Fi 4: two streams of rates. Wi-Fi 5: rate map with three streams (the rest set to 3, "none").
        val ht = bytes(0, 0, 0, 0xFF, 0xFF, 0, 0) + ByteArray(12)
        val vht = bytes(0, 0, 0, 0, 0b1110_1010, 0xFF)
        // Wi-Fi 6: four streams, after 17 bytes of capabilities.
        val he = ByteArray(17) + bytes(0b0000_0000, 0xFF)
        val heOperation = bytes(0, 0, 0, 23)
        val extras = parseWifiElements(
            listOf(
                WifiElement(45, ht),
                WifiElement(191, vht),
                WifiElement(255, he, extension = 35),
                WifiElement(255, heOperation, extension = 36),
                WifiElement(255, bytes(0, 0), extension = 107),
            ),
        )
        assertEquals(4, extras.spatialStreams)
        assertEquals(23, extras.bssColor)
        assertTrue(extras.multiLink)
        // A disabled colour is not shown.
        val off = parseWifiElements(listOf(WifiElement(255, bytes(0, 0, 0, 0x80 or 23), extension = 36)))
        assertEquals(null, off.bssColor)
        assertEquals(3, parseWifiElements(listOf(WifiElement(191, vht))).spatialStreams)
        assertEquals(2, parseWifiElements(listOf(WifiElement(45, ht))).spatialStreams)
    }
}
