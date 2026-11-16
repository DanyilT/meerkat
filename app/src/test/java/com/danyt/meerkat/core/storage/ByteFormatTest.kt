package com.danyt.meerkat.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `formats byte counts`() {
        assertEquals("0 bytes", formatBytes(0))
        assertEquals("1 byte", formatBytes(1))
        assertEquals("1023 bytes", formatBytes(1023))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("20 MB", formatBytes(20L * 1024 * 1024))
        assertEquals("2.3 GB", formatBytes((2.3 * 1024 * 1024 * 1024).toLong()))
    }
}
