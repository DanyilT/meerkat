package com.danyt.meerkat.core.makers

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MakerListTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val csv = """
        Registry,Assignment,Organization Name,Organization Address
        MA-L,002272,American Micro-Fuel Device Corp.,2181 Buchanan Loop Ferndale WA US 98248
        MA-L,00D0EF,IGT,"9295 PROTOTYPE DRIVE RENO NV US 89511"
        MA-L,086195,"Rockwell Automation, Inc.","1 Allen-Bradley Dr.
        Mayfield Heights OH US 44124-6118"
        MA-L,BAD,Too short,Nowhere
        MA-L,3C5AB4,"Google, Inc.",Mountain View
    """.trimIndent()

    @Test
    fun parsesQuotedFieldsAndLineBreaks() {
        val makers = parseRegistry(csv.reader())
        assertEquals(4, makers.size)
        assertEquals("American Micro-Fuel Device Corp.", makers[0x002272])
        assertEquals("Rockwell Automation, Inc.", makers[0x086195])
        assertEquals("Google, Inc.", makers[0x3C5AB4])
    }

    @Test
    fun lookupSkipsRandomAddresses() {
        val lookup = MakerLookup(parseRegistry(csv.reader()))
        assertEquals("Google, Inc.", lookup.of("3c:5a:b4:12:34:56"))
        assertEquals("IGT", lookup.of("00-D0-EF-00-00-01"))
        // The second-lowest bit of the first byte set: a made-up private address.
        assertNull(lookup.of("3E:5A:B4:12:34:56"))
        assertNull(lookup.of("12:34"))
    }

    @Test
    fun savedListRoundTrips() = runBlocking {
        // Saving is done by download(); here the saved form is written by hand and read back.
        val dir = folder.newFolder()
        File(dir, "makers.txt").writeText("002272\tAmerican Micro-Fuel Device Corp.\n3C5AB4\tGoogle, Inc.\nbad line\n")
        val list = MakerList(dir, source = "http://localhost:1/none")
        list.load()
        assertEquals("Google, Inc.", list.lookup.value?.of("3C:5A:B4:00:00:00"))
        assertEquals(2, list.status.value.count)
        list.delete()
        assertNull(list.lookup.value)
        assertNull(list.status.value.count)
        assertFalse(File(dir, "makers.txt").exists())
    }

    @Test
    fun downloadsSavesAndLooksUp() = runBlocking {
        // A small web server standing in for the registry, with enough rows to count as a real answer.
        val rows = (0 until 1_200).joinToString("\n") { index ->
            "MA-L,${index.toString(16).padStart(6, '0').uppercase()},Maker $index,Somewhere"
        }
        val body = ("Registry,Assignment,Organization Name,Organization Address\n" + rows).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oui.csv") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val dir = folder.newFolder()
            val list = MakerList(dir, source = "http://127.0.0.1:${server.address.port}/oui.csv")
            assertTrue(list.download())
            assertEquals(1_200, list.status.value.count)
            assertEquals("Maker 255", list.lookup.value?.of("00:00:FF:12:34:56"))
            // A fresh instance reads what was saved.
            val again = MakerList(dir, source = "http://127.0.0.1:1/none")
            again.load()
            assertEquals("Maker 255", again.lookup.value?.of("00-00-FF-00-00-00"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun tooFewEntriesIsAFailure() = runBlocking {
        val body = "Registry,Assignment,Organization Name,Organization Address\nMA-L,000001,One,Here".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oui.csv") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val list = MakerList(folder.newFolder(), source = "http://127.0.0.1:${server.address.port}/oui.csv")
            assertFalse(list.download())
            assertTrue(list.status.value.failed)
            assertNull(list.lookup.value)
        } finally {
            server.stop(0)
        }
    }
}
