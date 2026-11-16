package com.danyt.meerkat.core.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun write(file: File, bytes: Int): File {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
        return file
    }

    @Test
    fun `size counts nested files across all directories`() = runBlocking {
        val first = folder.newFolder("cache")
        val second = folder.newFolder("external")
        write(File(first, "a.bin"), 100)
        write(File(first, "nested/deep/b.bin"), 250)
        write(File(second, "c.bin"), 50)

        assertEquals(400L, CacheManager(listOf(first, second)).sizeBytes())
    }

    @Test
    fun `clear removes contents, keeps the directory, and reports bytes freed`() = runBlocking {
        val cache = folder.newFolder("cache")
        write(File(cache, "a.bin"), 100)
        write(File(cache, "nested/b.bin"), 250)
        val manager = CacheManager(listOf(cache))

        assertEquals(350L, manager.clear())
        assertTrue(cache.isDirectory)
        assertEquals(0, cache.listFiles()!!.size)
        assertEquals(0L, manager.sizeBytes())
    }

    @Test
    fun `empty and missing directories are zero bytes`() = runBlocking {
        val manager = CacheManager(listOf(folder.newFolder("empty"), File(folder.root, "missing")))

        assertEquals(0L, manager.sizeBytes())
        assertEquals(0L, manager.clear())
    }

    @Test
    fun `clear does not follow symbolic links out of the cache`() = runBlocking {
        val cache = folder.newFolder("cache")
        val outside = folder.newFolder("outside")
        val keep = write(File(outside, "keep.bin"), 500)
        write(File(cache, "a.bin"), 10)
        val link = File(cache, "link")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        val manager = CacheManager(listOf(cache))

        assertEquals(10L, manager.sizeBytes())
        assertEquals(10L, manager.clear())
        assertTrue(keep.exists())
        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }
}
