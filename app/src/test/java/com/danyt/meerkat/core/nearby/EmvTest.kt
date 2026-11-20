package com.danyt.meerkat.core.nearby

import java.util.Calendar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmvTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private val ok = bytes(0x90, 0x00)

    @Test
    fun processingOptionsFillThePdol() {
        // PDOL asking for the reader abilities (4), amount (6), and date (3).
        val pdol = bytes(0x9F, 0x66, 0x04, 0x9F, 0x02, 0x06, 0x9A, 0x03)
        val selection = bytes(0x6F, pdol.size + 5, 0xA5, pdol.size + 3, 0x9F, 0x38, pdol.size) + pdol + ok
        val day = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 17) }
        val command = processingOptions(selection, day)
        val data = bytes(0xF0, 0x20, 0x40, 0x00) + ByteArray(6) + bytes(0x26, 0x11, 0x17)
        assertArrayEquals(bytes(0x80, 0xA8, 0x00, 0x00, data.size + 2, 0x83, data.size) + data + bytes(0x00), command)
        // No PDOL: an empty data object.
        assertArrayEquals(bytes(0x80, 0xA8, 0x00, 0x00, 0x02, 0x83, 0x00, 0x00), processingOptions(ok))
    }

    @Test
    fun recordRangesInBothForms() {
        // Short form: capabilities 1900, then file 1 records 1-2 and file 2 record 1.
        val short = bytes(0x80, 0x0A, 0x19, 0x00, 0x08, 0x01, 0x02, 0x00, 0x10, 0x01, 0x01, 0x00) + ok
        assertEquals(listOf(RecordRange(1, 1, 2), RecordRange(2, 1, 1)), recordRanges(short))
        val long = bytes(0x77, 0x0A, 0x82, 0x02, 0x19, 0x00, 0x94, 0x04, 0x18, 0x01, 0x03, 0x00) + ok
        assertEquals(listOf(RecordRange(3, 1, 3)), recordRanges(long))
        assertEquals(emptyList<RecordRange>(), recordRanges(bytes(0x69, 0x85)))
        assertArrayEquals(bytes(0x00, 0xB2, 0x02, 0x1C, 0x00), readRecord(file = 3, record = 2))
    }

    @Test
    fun detailsFromRecords() {
        val pan = bytes(0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val expiry = bytes(0x5F, 0x24, 0x03, 0x27, 0x12, 0x31)
        val holder = bytes(0x5F, 0x20, 0x02) + " /".toByteArray()
        val record = bytes(0x70, pan.size + expiry.size + holder.size) + pan + expiry + holder + ok
        val details = cardDetails(listOf(record))
        assertEquals(CardDetails("4111111111111111", "12/27", null), details)
        assertEquals("4111 1111 1111 1111", details?.formattedNumber)
    }

    @Test
    fun detailsFromTrack2() {
        // 5413 3300 8909 9000, separator D, expiry 2512, then service code and padding.
        val track = bytes(0x57, 0x0D, 0x54, 0x13, 0x33, 0x00, 0x89, 0x09, 0x90, 0x00, 0xD2, 0x51, 0x22, 0x01, 0x1F)
        val details = cardDetails(listOf(bytes(0x77, track.size) + track + ok))
        assertEquals("5413330089099000", details?.number)
        assertEquals("12/25", details?.expiry)
        assertNull(cardDetails(listOf(bytes(0x6A, 0x82))))
    }

    @Test
    fun applicationIdsFromDirectory() {
        val app = bytes(0x61, 0x09, 0x4F, 0x07, 0xA0, 0x00, 0x00, 0x00, 0x04, 0x10, 0x10)
        val ids = applicationIds(bytes(0x6F, app.size + 2, 0xA5, app.size) + app + ok)
        assertEquals(1, ids.size)
        assertEquals("Mastercard", paymentNetwork(ids.first()))
    }

    @Test
    fun detailsFromOtherTracks() {
        // 9F6B holds track 2 on some contactless cards.
        val track = bytes(0x9F, 0x6B, 0x09, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0xD2)
        val withYear = track + bytes(0x80, 0x00) // Not enough for an expiry: only the number is read.
        assertEquals("4111111111111111", cardDetails(listOf(bytes(0x70, withYear.size) + withYear + ok))?.number)
        // Track 1: text with the number, the holder, and YYMM.
        val text = "B4111111111111111^DOE/JANE^2712101".toByteArray()
        val track1 = bytes(0x70, text.size + 2, 0x56, text.size) + text + ok
        assertEquals(CardDetails("4111111111111111", "12/27", "DOE/JANE"), cardDetails(listOf(track1)))
    }

    @Test
    fun recordStatus() {
        assertEquals(true, noSuchRecord(bytes(0x6A, 0x83)))
        assertEquals(false, noSuchRecord(bytes(0x90, 0x00)))
        assertEquals(true, cardAnswered(bytes(0x01, 0x90, 0x00)))
        assertEquals(11, KnownPaymentApplications.size)
    }

    @Test
    fun amountAndStatus() {
        val pdol = bytes(0x9F, 0x02, 0x06)
        val selection = bytes(0x6F, pdol.size + 5, 0xA5, pdol.size + 3, 0x9F, 0x38, pdol.size) + pdol + ok
        val cent = processingOptions(selection, amount = ProcessingAttempts.last().second)
        assertArrayEquals(bytes(0x80, 0xA8, 0x00, 0x00, 0x08, 0x83, 0x06, 0, 0, 0, 0, 0, 0x01, 0x00), cent)
        assertEquals(4, ProcessingAttempts.size)
        assertEquals("6985", statusWord(bytes(0x69, 0x85)))
        assertEquals(null, statusWord(bytes(0x69)))
    }
}
