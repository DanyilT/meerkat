package com.danyt.meerkat.core.reports

import com.danyt.meerkat.core.storage.AesGcmSealer
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val report = Report(
        kind = ReportKind.Lan,
        title = "192.168.1.0/24",
        createdAt = 1_780_000_000_000,
        details = listOf("Profile" to "Quick", "Devices" to "2"),
        columns = listOf("Address", "Name", "Open ports"),
        rows = listOf(
            listOf("192.168.1.1", "router \"main\"", "53, 80"),
            listOf("192.168.1.20", "nas\nline two é中", ""),
        ),
    )

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun newSealer() = newKey().let { key -> AesGcmSealer { key } }

    @Test
    fun `JSON survives a round trip, including quotes, new lines, and other alphabets`() {
        assertEquals(report, reportFromJson(report.toJson()))
    }

    @Test
    fun `the JSON reader handles the whole syntax`() {
        val value = Json.read(""" { "a" : [1, -2.5, true, false, null, "xA\n\/"], "b" : {} , "c":[] } """)
        val expected = mapOf(
            "a" to listOf(1L, -2.5, true, false, null, "xA\n/"),
            "b" to emptyMap<String, Any?>(),
            "c" to emptyList<Any?>(),
        )
        assertEquals(expected, value)
    }

    @Test
    fun `broken JSON is refused`() {
        val deep = "[".repeat(40) + "]".repeat(40)
        listOf("", "{", "[1,]", "{\"a\":}", "\"open", "{\"a\":1} extra", "tru", "{'a':1}", deep)
            .forEach { text -> assertThrows(text, JsonException::class.java) { Json.read(text) } }
    }

    @Test
    fun `files that are not reports are refused`() {
        val wrong = listOf(
            "not json",
            "[]",
            """{"format":"other","version":1}""",
            report.toJson().replace("\"version\":1", "\"version\":2"),
            report.toJson().replace("\"kind\":\"lan\"", "\"kind\":\"teleport\""),
            report.toJson().replace("\"columns\":[\"Address\",", "\"columns\":["),
            report.toJson().replace("\"title\":\"192.168.1.0/24\"", "\"title\":5"),
        )
        wrong.forEach { text -> assertThrows(text, ReportFormatException::class.java) { reportFromJson(text) } }
    }

    @Test
    fun `CSV quotes cells that need it`() {
        val lines = report.toCsv().split("\r\n")
        assertEquals("Address,Name,Open ports", lines[0])
        assertEquals("192.168.1.1,\"router \"\"main\"\"\",\"53, 80\"", lines[1])
        assertTrue(report.toCsv().contains("\"nas\nline two"))
    }

    @Test
    fun `CSV defuses cells a spreadsheet would run as a formula`() {
        val hostile = report.copy(rows = listOf(listOf("=HYPERLINK(\"http://x\")", "+1", "@cmd")))
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\",'+1,'@cmd", hostile.toCsv().split("\r\n")[1])
    }

    @Test
    fun `text export lines the columns up`() {
        val lines = report.toText().lines()
        assertEquals("192.168.1.0/24", lines[0])
        assertTrue(lines.contains("Profile  Quick"))
        val header = lines.indexOfFirst { it.startsWith("Address") }
        assertEquals(lines[header].indexOf("Name"), lines[header + 2].indexOf("router"))
        assertTrue(lines[header + 1].all { it == '-' || it == ' ' })
        // A new line inside a cell must not break the table.
        assertTrue(lines.any { it.contains("nas line two") })
    }

    @Test
    fun `sealed data opens again and is not readable as it is`() {
        val sealer = newSealer()
        val plain = "secret network map".toByteArray()
        val sealed = sealer.seal(plain)
        assertArrayEquals(plain, sealer.open(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("secret"))
        assertFalse(sealed.contentEquals(sealer.seal(plain)))
    }

    @Test
    fun `changed data or another key is refused`() {
        val sealer = newSealer()
        val sealed = sealer.seal("data".toByteArray())
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { sealer.open(changed) }
        assertThrows(GeneralSecurityException::class.java) { newSealer().open(sealed) }
        assertThrows(GeneralSecurityException::class.java) { sealer.open(ByteArray(5)) }
    }

    @Test
    fun `the store saves, lists newest first, reloads, and deletes`() = runBlocking {
        val directory = File(folder.root, "reports")
        val sealer = newSealer()
        val store = ReportStore(directory, sealer)
        assertNull(store.reports.value)

        val older = store.save(report)
        val newer = store.save(report.copy(title = "10.0.0.5", createdAt = report.createdAt + 1000))
        assertEquals(listOf(newer, older), store.reports.value)
        assertTrue(older.id.isNotEmpty() && older.id != newer.id)

        // Nothing readable on disk.
        directory.listFiles()!!.forEach { file ->
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("192.168"))
        }

        val reopened = ReportStore(directory, sealer).also { it.load() }
        assertEquals(listOf(newer, older), reopened.reports.value)

        reopened.delete(older.id)
        reopened.delete("../../etc/passwd")
        assertEquals(listOf(newer), reopened.reports.value)
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test
    fun `files the store cannot read are skipped`() = runBlocking {
        val directory = File(folder.root, "reports").apply { mkdirs() }
        val sealer = newSealer()
        val kept = ReportStore(directory, sealer).save(report)
        File(directory, "11111111-2222-3333-4444-555555555555.report").writeBytes(ByteArray(64))
        File(directory, "notes.txt").writeText("hello")

        // A store with another key, as after the Keystore key was lost, reads nothing and does not crash.
        assertEquals(emptyList<Report>(), ReportStore(directory, newSealer()).also { it.load() }.reports.value)
        assertEquals(listOf(kept), ReportStore(directory, sealer).also { it.load() }.reports.value)
    }

    @Test
    fun `the fingerprint is stable and follows the content, not the copy`() {
        val hash = report.contentHash()
        assertEquals(64, hash.length)
        assertEquals(hash, report.copy(id = "stored-somewhere").contentHash())
        assertEquals(hash, reportFromJson(report.toJson()).contentHash())
        // Exporting what was imported gives the very same file.
        assertEquals(report.toJson(), reportFromJson(report.toJson()).toJson())
        assertFalse(hash == report.copy(title = "192.168.2.0/24").contentHash())
        assertFalse(hash == report.copy(createdAt = report.createdAt + 1).contentHash())
        assertFalse(hash == report.copy(rows = report.rows.reversed()).contentHash())
    }

    @Test
    fun `exports carry the scan time in UTC and the fingerprint`() {
        val json = report.toJson()
        assertTrue(json.contains("\"createdAtUtc\":\"2026-05-28 20:26:40 UTC\""))
        assertTrue(json.contains("\"sha256\":\"${report.contentHash()}\""))
        val text = report.toText().lines()
        assertEquals("2026-05-28 20:26:40 UTC", text[1])
        assertEquals("SHA-256 ${report.contentHash()}", text[2])
        assertEquals("meerkat-lan-20260528-202640Z", report.fileName())
    }

    @Test
    fun `the store knows which reports are saved and which were exported`() = runBlocking {
        val directory = File(folder.root, "reports")
        val sealer = newSealer()
        val store = ReportStore(directory, sealer)
        val hash = report.contentHash()
        store.load()
        assertEquals(ReportHashes(), store.hashes.value)

        val saved = store.save(report)
        store.markExported(hash)
        store.markExported("not a hash")
        assertEquals(ReportHashes(saved = setOf(hash), exported = setOf(hash)), store.hashes.value)

        // Deleting the saved copy does not forget the export, and both survive a restart.
        store.delete(saved.id)
        val reopened = ReportStore(directory, sealer).also { it.load() }
        assertEquals(ReportHashes(saved = emptySet(), exported = setOf(hash)), reopened.hashes.value)
    }
}
