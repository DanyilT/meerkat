package com.danyt.meerkat.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcIdentifyTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun ntagVersions() {
        assertEquals(NxpChip("NTAG215", 504), nxpChip(bytes(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x11, 0x03)))
        assertEquals(NxpChip("NTAG213", 144), nxpChip(bytes(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x0F, 0x03)))
        assertEquals("MIFARE Ultralight EV1", nxpChip(bytes(0x00, 0x04, 0x03, 0x01, 0x01, 0x00, 0x0B, 0x03))?.name)
        assertNull(nxpChip(bytes(0x00, 0x05, 0x04, 0x02, 0x01, 0x00, 0x11, 0x03)))
        assertNull(nxpChip(bytes(0x00, 0x04)))
    }

    @Test
    fun desfire() {
        assertEquals("MIFARE DESFire EV2" to 8192, desfireVersion(bytes(0x04, 0x01, 0x01, 0x12, 0x00, 0x1A, 0x05)))
        assertEquals("MIFARE DESFire EV1" to 4096, desfireVersion(bytes(0x04, 0x01, 0x01, 0x01, 0x00, 0x18, 0x05)))
        assertNull(desfireVersion(bytes(0x04, 0x02, 0x01, 0x01, 0x00, 0x18, 0x05)))
    }

    @Test
    fun paymentDirectory() {
        // FCI template: DF name, then proprietary data holding one application template (61) with AID and label.
        val app = bytes(0x4F, 0x07, 0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10) +
            bytes(0x50, 0x0A) + "VISA DEBIT".toByteArray()
        val template = bytes(0x61, app.size) + app
        val directory = bytes(0xBF, 0x0C, template.size) + template
        val proprietary = bytes(0xA5, directory.size) + directory
        val name = bytes(0x84, 0x0E) + "2PAY.SYS.DDF01".toByteArray()
        val fci = bytes(0x6F, name.size + proprietary.size) + name + proprietary
        val apps = paymentApps(fci + bytes(0x90, 0x00))
        assertEquals(listOf(PaymentApp("Visa", "VISA DEBIT")), apps)
    }

    @Test
    fun paymentDirectoryFailures() {
        assertTrue(paymentApps(bytes(0x6A, 0x82)).isEmpty())
        assertTrue(paymentApps(bytes(0x6F, 0x7F, 0x84, 0x90, 0x00)).isEmpty())
        assertEquals("Maestro", paymentNetwork(bytes(0xA0, 0x00, 0x00, 0x00, 0x04, 0x30, 0x60)))
        assertEquals("A0000099991010", paymentNetwork(bytes(0xA0, 0x00, 0x00, 0x99, 0x99, 0x10, 0x10)))
    }

    @Test
    fun vicinityTags() {
        // ID least significant byte first: ends with the maker (NXP) and E0.
        val id = bytes(0x11, 0x22, 0x33, 0x44, 0x55, 0x01, 0x04, 0xE0)
        // Flags OK, DSFID and AFI and memory present: 28 blocks of 4 bytes.
        val info = bytes(0x00, 0x07) + id + bytes(0x00, 0x00, 27, 3)
        assertEquals(VicinityInfo("NXP", 112), vicinityInfo(id, info))
        assertEquals(VicinityInfo("NXP", null), vicinityInfo(id, null))
        assertEquals(VicinityInfo(null, null), vicinityInfo(bytes(1, 2, 3), bytes(1)))
    }
}
