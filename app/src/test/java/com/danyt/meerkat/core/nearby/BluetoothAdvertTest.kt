package com.danyt.meerkat.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothAdvertTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun appearanceFromAdvertisement() {
        // Flags, then appearance 0x00C1 (a watch: category 3), then a name.
        val advert = bytes(2, 0x01, 0x06, 3, 0x19, 0xC1, 0x00, 4, 0x09, 'a'.code, 'b'.code, 'c'.code)
        val appearance = appearanceOf(advert)
        assertEquals(0x00C1, appearance)
        assertEquals(AppearanceCategory.WATCH, appearanceCategory(appearance!!))
    }

    @Test
    fun appearanceMissingOrBroken() {
        assertNull(appearanceOf(bytes(2, 0x01, 0x06)))
        assertNull(appearanceOf(bytes(9, 0x19, 0x01)))
        assertNull(appearanceOf(ByteArray(0)))
    }

    @Test
    fun appleMessagesAndEarbudBattery() {
        // Nearby Info, then a pairing message from AirPods Pro: left 80 %, right 70 %, case 50 %.
        val pairing = bytes(0x01, 0x0E, 0x20, 0x00, 0x87, 0x05, 0, 0)
        val data = bytes(0x10, 2, 0x11, 0x22) + bytes(0x07, pairing.size) + pairing
        val info = parseApple(data)
        assertEquals(listOf(AppleMessage.NEARBY_INFO, AppleMessage.PROXIMITY_PAIRING), info.messages)
        assertEquals(0x0E20, info.model)
        assertEquals("AirPods Pro", AppleAudioModels[info.model])
        assertEquals(80, info.left)
        assertEquals(70, info.right)
        assertEquals(50, info.case)
    }

    @Test
    fun flippedEarbudsAndUnknownLevels() {
        // Status bit set: the right earbud's level comes first. 15 means unknown.
        val pairing = bytes(0x01, 0x0F, 0x20, 0x20, 0x9F, 0x0F)
        val info = parseApple(bytes(0x07, pairing.size) + pairing)
        assertEquals(90, info.right)
        assertNull(info.left)
        assertNull(info.case)
    }

    @Test
    fun truncatedAppleDataKeepsEarlierMessages() {
        val info = parseApple(bytes(0x0C, 1, 0x00, 0x12, 30, 0x01))
        assertEquals(listOf(AppleMessage.HANDOFF), info.messages)
    }
}
