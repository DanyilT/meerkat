package com.danyt.meerkat.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefTest {

    /** One short record: header (with message begin and end, short record), type length, payload length. */
    private fun record(format: Int, type: String, payload: ByteArray, last: Boolean = true, first: Boolean = true) =
        byteArrayOf(
            ((if (first) 0x80 else 0) or (if (last) 0x40 else 0) or 0x10 or format).toByte(),
            type.length.toByte(),
            payload.size.toByte(),
        ) + type.toByteArray(Charsets.US_ASCII) + payload

    @Test
    fun uriWithPrefix() {
        val message = record(1, "U", byteArrayOf(0x04) + "example.com/a".toByteArray())
        assertEquals(listOf(NdefContent.Uri("https://example.com/a")), parseNdefMessage(message))
    }

    @Test
    fun textInUtf8() {
        val message = record(1, "T", byteArrayOf(0x02) + "en".toByteArray() + "Hello".toByteArray())
        assertEquals(listOf(NdefContent.Text("Hello", "en")), parseNdefMessage(message))
    }

    @Test
    fun textInUtf16() {
        val payload = byteArrayOf(0x82.toByte()) + "de".toByteArray() + "Hallo".toByteArray(Charsets.UTF_16)
        assertEquals(listOf(NdefContent.Text("Hallo", "de")), parseNdefMessage(record(1, "T", payload)))
    }

    @Test
    fun severalRecordsAndKinds() {
        val message = record(2, "text/plain", "note".toByteArray(), last = false) +
            record(4, "android.com:pkg", "com.example.app".toByteArray(), first = false, last = false) +
            record(2, "image/png", ByteArray(10), first = false)
        assertEquals(
            listOf(
                NdefContent.Mime("text/plain", "note", 4),
                NdefContent.AppLaunch("com.example.app"),
                NdefContent.Mime("image/png", null, 10),
            ),
            parseNdefMessage(message),
        )
    }

    @Test
    fun smartPosterHoldsItsRecords() {
        val inner = record(1, "U", byteArrayOf(0x03) + "a.b".toByteArray(), last = false) +
            record(1, "T", byteArrayOf(0x02) + "en".toByteArray() + "Title".toByteArray(), first = false)
        val parsed = parseNdefMessage(record(1, "Sp", inner))
        assertEquals(
            listOf(NdefContent.SmartPoster(listOf(NdefContent.Uri("http://a.b"), NdefContent.Text("Title", "en")))),
            parsed,
        )
    }

    @Test
    fun truncatedDataKeepsEarlierRecords() {
        val good = record(1, "U", byteArrayOf(0x00) + "tel:1".toByteArray(), last = false)
        val broken = byteArrayOf(0x51, 1, 50, 'U'.code.toByte(), 0x00)
        assertEquals(listOf(NdefContent.Uri("tel:1")), parseNdefMessage(good + broken))
        assertTrue(parseNdefMessage(byteArrayOf(0x51)).isEmpty())
        assertTrue(parseNdefMessage(ByteArray(0)).isEmpty())
    }

    @Test
    fun longRecordLength() {
        val payload = byteArrayOf(0x00) + "x".toByteArray()
        val message = byteArrayOf(0xC1.toByte(), 1, 0, 0, 0, payload.size.toByte(), 'U'.code.toByte()) + payload
        assertEquals(listOf(NdefContent.Uri("x")), parseNdefMessage(message))
    }

    @Test
    fun controlCharactersAreRemoved() {
        val message = record(1, "T", byteArrayOf(0x02) + "en".toByteArray() + "a\u0007b\nc".toByteArray())
        assertEquals(listOf(NdefContent.Text("ab\nc", "en")), parseNdefMessage(message))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun wsc(type: Int, value: ByteArray) =
        bytes(type shr 8, type and 0xFF, value.size shr 8, value.size and 0xFF) + value

    @Test
    fun wifiSetupWithoutPassword() {
        val credential = wsc(0x1045, "Home".toByteArray()) + wsc(0x1003, bytes(0x00, 0x20)) +
            wsc(0x1027, "secret123".toByteArray())
        val payload = wsc(0x100E, credential)
        val parsed = parseNdefMessage(record(2, "application/vnd.wfa.wsc", payload))
        assertEquals(listOf(NdefContent.WifiConfig("Home", WifiTagSecurity.Wpa2)), parsed)
    }

    @Test
    fun bluetoothPairing() {
        // Length, address 11:22:33:44:55:66 least significant byte first, then the full name "Speaker".
        val name = "Speaker".toByteArray()
        val payload = bytes(0x00, 0x00, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11, name.size + 1, 0x09) + name
        val parsed = parseNdefMessage(record(2, "application/vnd.bluetooth.ep.oob", payload))
        assertEquals(listOf(NdefContent.BluetoothPairing("11:22:33:44:55:66", "Speaker")), parsed)
    }

    @Test
    fun directionMarksAreDropped() {
        // A right-to-left override would make the link read as a different address.
        assertEquals("x:moc.lapyap.www//:sptth", cleanName("x:\u202Emoc.lapyap.www//:sptth"))
        assertEquals("Home", cleanName("\u2066Ho\u200Fme\u2069"))
    }
}
