package com.danyt.meerkat.core.reports

import com.danyt.meerkat.core.storage.Sealer
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The fingerprints the app knows about, for spotting copies.
 *
 * @property saved fingerprints of the reports stored in the app.
 * @property exported fingerprints of the reports that were written to a file before.
 */
data class ReportHashes(val saved: Set<String> = emptySet(), val exported: Set<String> = emptySet())

/**
 * Keeps saved reports as encrypted files, one per report, in [directory].
 *
 * Reports can hold a map of someone's network, so they are never written in the clear. All disk work runs on
 * [dispatcher].
 */
class ReportStore(
    private val directory: File,
    private val sealer: Sealer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _reports = MutableStateFlow<List<Report>?>(null)

    /** The saved reports, newest first. Null until [load] has run. */
    val reports: StateFlow<List<Report>?> = _reports.asStateFlow()

    private val _hashes = MutableStateFlow(ReportHashes())

    /** Fingerprints of the saved and the exported reports. Empty until [load] has run. */
    val hashes: StateFlow<ReportHashes> = _hashes.asStateFlow()

    private var exported: List<String> = emptyList()

    /** Reads the saved reports from disk, once. Later calls do nothing. Files that cannot be read are skipped. */
    suspend fun load() = withContext(dispatcher) {
        mutex.withLock {
            if (_reports.value != null) return@withLock
            val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }.orEmpty()
            exported = try {
                exportedFile().takeIf { it.isFile }?.readLines().orEmpty().filter(HashPattern::matches)
            } catch (_: IOException) {
                emptyList()
            }
            publish(files.mapNotNull(::read).sortedByDescending { it.createdAt })
        }
    }

    /**
     * Remembers that the report with fingerprint [hash] was written to a file, so a later export of the same
     * report can say so. Only the fingerprint is kept, never the report or where the file went.
     */
    suspend fun markExported(hash: String) = withContext(dispatcher) {
        load()
        mutex.withLock {
            if (!HashPattern.matches(hash) || hash in exported) return@withLock
            exported = (exported + hash).takeLast(MAX_EXPORTED)
            try {
                if (directory.isDirectory || directory.mkdirs()) exportedFile().writeText(exported.joinToString("\n"))
            } catch (_: IOException) {
                // The note about an earlier export is a convenience. Losing it is not worth an error.
            }
            publish(_reports.value.orEmpty())
        }
    }

    private fun publish(reports: List<Report>) {
        _reports.value = reports
        _hashes.value = ReportHashes(saved = reports.mapTo(HashSet()) { it.contentHash() }, exported = exported.toSet())
    }

    /**
     * Saves [report] under a new id and returns the stored copy. Throws [IOException] when the file cannot be
     * written.
     */
    suspend fun save(report: Report): Report = withContext(dispatcher) {
        load()
        mutex.withLock {
            val stored = report.copy(id = UUID.randomUUID().toString())
            write(stored)
            publish((listOf(stored) + _reports.value.orEmpty()).sortedByDescending { it.createdAt })
            stored
        }
    }

    /**
     * Replaces the stored report that has the same id as [report], for example after the user renamed it.
     * Does nothing when no such report is stored. Throws [IOException] when the file cannot be written.
     */
    suspend fun update(report: Report) = withContext(dispatcher) {
        load()
        mutex.withLock {
            val current = _reports.value.orEmpty()
            if (current.none { it.id == report.id }) return@withLock
            write(report)
            publish(current.map { if (it.id == report.id) report else it })
        }
    }

    /** Deletes every saved report. The record of past exports is kept. Returns how many were deleted. */
    suspend fun deleteAll(): Int = withContext(dispatcher) {
        load()
        mutex.withLock {
            val current = _reports.value.orEmpty()
            current.forEach { fileFor(it.id).delete() }
            publish(emptyList())
            current.size
        }
    }

    private fun write(report: Report) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create the reports folder")
            // Written to a side file first, so a crash half way never leaves a broken report behind.
            val target = fileFor(report.id)
            val temporary = File(directory, target.name + ".tmp")
            temporary.writeBytes(sealer.seal(report.toJson().toByteArray(Charsets.UTF_8)))
            if (!temporary.renameTo(target)) throw IOException("Cannot store the report")
        } catch (e: GeneralSecurityException) {
            throw IOException("Cannot encrypt the report", e)
        }
    }

    /** Deletes the report with [id]. Unknown ids are ignored. */
    suspend fun delete(id: String) = withContext(dispatcher) {
        load()
        mutex.withLock {
            if (isValidId(id)) fileFor(id).delete()
            publish(_reports.value.orEmpty().filterNot { it.id == id })
        }
    }

    private fun read(file: File): Report? = try {
        val id = file.name.removeSuffix(EXTENSION)
        if (!isValidId(id) || file.length() > MAX_FILE_BYTES) {
            null
        } else {
            reportFromJson(sealer.open(file.readBytes()).toString(Charsets.UTF_8)).copy(id = id)
        }
    } catch (_: IOException) {
        null
    } catch (_: GeneralSecurityException) {
        null // Damaged, or sealed with a key that no longer exists.
    } catch (_: ReportFormatException) {
        null
    }

    private fun exportedFile() = File(directory, "exported.txt")

    private fun fileFor(id: String) = File(directory, id + EXTENSION)

    /** Ids become file names, so only the form this store creates is accepted. */
    private fun isValidId(id: String) = IdPattern.matches(id)

    private companion object {
        const val EXTENSION = ".report"
        const val MAX_FILE_BYTES = MAX_REPORT_BYTES * 2L
        const val MAX_EXPORTED = 1000
        val HashPattern = Regex("[0-9a-f]{64}")
        val IdPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    }
}
