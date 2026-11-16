package com.danyt.meerkat.core.storage

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures and clears the app's cache directories.
 *
 * Symbolic links are never followed: a link is counted as zero bytes and only the link itself is removed, so
 * clearing can never touch files outside the cache directories.
 *
 * @param cacheDirs directories to manage. Missing directories are ignored.
 * @param ioDispatcher dispatcher used for disk access.
 */
class CacheManager(
    private val cacheDirs: List<File>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Returns the total size in bytes of all files in the cache directories. */
    suspend fun sizeBytes(): Long = withContext(ioDispatcher) {
        cacheDirs.sumOf { dir -> children(dir).sumOf(::sizeOf) }
    }

    /**
     * Deletes everything inside the cache directories, keeping the directories themselves.
     *
     * @return the number of bytes actually freed. Files that could not be deleted are not counted.
     */
    suspend fun clear(): Long = withContext(ioDispatcher) {
        cacheDirs.sumOf { dir -> children(dir).sumOf(::delete) }
    }

    private fun children(dir: File): List<File> = dir.listFiles()?.toList().orEmpty()

    private fun sizeOf(file: File): Long = when {
        isSymlink(file) -> 0L
        file.isDirectory -> children(file).sumOf(::sizeOf)
        else -> file.length()
    }

    private fun delete(file: File): Long {
        if (isSymlink(file)) {
            file.delete()
            return 0L
        }
        if (file.isDirectory) {
            val freed = children(file).sumOf(::delete)
            file.delete()
            return freed
        }
        val length = file.length()
        return if (file.delete()) length else 0L
    }

    /** True when [file] itself is a link, regardless of links higher up in its path. */
    private fun isSymlink(file: File): Boolean {
        val parent = file.absoluteFile.parentFile ?: return false
        val resolvedInParent = File(parent.canonicalFile, file.name)
        return resolvedInParent.canonicalFile != resolvedInParent
    }
}
