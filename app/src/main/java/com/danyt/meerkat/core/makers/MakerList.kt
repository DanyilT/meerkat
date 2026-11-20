package com.danyt.meerkat.core.makers

import android.util.Log
import com.danyt.meerkat.core.text.isHiddenControl
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The state of the downloaded maker list.
 *
 * @property count how many address prefixes it names, or null when it is not downloaded.
 * @property downloadedAt when it was downloaded, in milliseconds since the epoch.
 * @property sizeBytes how much space it takes on the device.
 * @property downloading true while a download runs.
 * @property failed true when the last download did not work.
 */
data class MakerListStatus(
    val count: Int? = null,
    val downloadedAt: Long? = null,
    val sizeBytes: Long? = null,
    val downloading: Boolean = false,
    val failed: Boolean = false,
)

/** Answers who registered a MAC address prefix. Built from the downloaded list. */
class MakerLookup internal constructor(private val makers: Map<Int, String>) {
    /**
     * The organisation that registered the first half of [mac], or null when unknown. Addresses a device made
     * up for privacy (their "locally administered" bit is set) belong to no one, so they get null.
     */
    fun of(mac: String): String? {
        val hex = mac.filter { it.isLetterOrDigit() }
        if (hex.length < PREFIX_DIGITS) return null
        val prefix = hex.take(PREFIX_DIGITS).toIntOrNull(16) ?: return null
        if ((prefix shr 16) and LOCALLY_ADMINISTERED != 0) return null
        return makers[prefix]
    }

    val size: Int get() = makers.size
}

/**
 * Who registered each MAC address prefix (the first three bytes, an "OUI"), from the IEEE's public registry.
 *
 * Nothing is bundled with the app: the list is downloaded from the IEEE only when the user asks, kept in the
 * app's own files (not the cache, so clearing the cache keeps it), and can be deleted again.
 */
class MakerList(private val dir: File, private val source: String = IEEE_REGISTRY) {
    private val file = File(dir, "makers.txt")
    private val _status = MutableStateFlow(MakerListStatus())
    val status: StateFlow<MakerListStatus> = _status.asStateFlow()
    private val _lookup = MutableStateFlow<MakerLookup?>(null)

    /** The loaded list, or null until [load] has read it (or when it is not downloaded). */
    val lookup: StateFlow<MakerLookup?> = _lookup.asStateFlow()

    /** Reads the downloaded list into memory, if there is one and it is not loaded yet. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (_lookup.value == null && file.exists()) {
            val makers = try {
                readSaved(file.bufferedReader())
            } catch (_: IOException) {
                emptyMap()
            }
            if (makers.isNotEmpty()) _lookup.value = MakerLookup(makers)
        }
        refreshStatus()
    }

    /**
     * Downloads the registry and replaces the saved list. Returns false when it failed; the old list, if any,
     * stays as it was.
     */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        _status.update { it.copy(downloading = true, failed = false) }
        val makers = try {
            fetch()
        } catch (e: IOException) {
            Log.w(TAG, "Maker list download failed", e)
            emptyMap()
        } catch (e: SecurityException) {
            Log.w(TAG, "Maker list download not allowed", e)
            emptyMap()
        }
        // A real registry has tens of thousands of entries; far fewer means the answer was something else.
        val saved = makers.size >= MIN_ENTRIES && save(makers)
        if (saved) _lookup.value = MakerLookup(makers)
        refreshStatus()
        _status.update { it.copy(downloading = false, failed = !saved) }
        saved
    }

    /** Writes the list next to the old one, then swaps it in, so a failed write never leaves half a list. */
    private fun save(makers: Map<Int, String>): Boolean = try {
        dir.mkdirs()
        val partial = File(dir, "makers.tmp")
        partial.bufferedWriter().use { out ->
            makers.toSortedMap().forEach { (prefix, name) ->
                out.write(prefix.toString(16).padStart(PREFIX_DIGITS, '0').uppercase())
                out.write("\t")
                out.write(name)
                out.write("\n")
            }
        }
        partial.renameTo(file)
    } catch (_: IOException) {
        false
    }

    /** Deletes the saved list. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        _lookup.value = null
        refreshStatus()
    }

    private fun refreshStatus() {
        val exists = file.exists()
        _status.update {
            it.copy(
                count = if (exists) _lookup.value?.size ?: it.count else null,
                downloadedAt = if (exists) file.lastModified() else null,
                sizeBytes = if (exists) file.length() else null,
            )
        }
    }

    private fun fetch(): Map<Int, String> {
        val connection = URL(source).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            // A plain name: the registry refuses requests without one. Nothing about the user is sent.
            connection.setRequestProperty("User-Agent", "MeerkatToolbox")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Maker list download answered ${connection.responseCode}")
                return emptyMap()
            }
            return connection.inputStream.use { parseRegistry(LimitedStream(it, MAX_DOWNLOAD_BYTES).reader()) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** The IEEE's list of large (MA-L) address blocks, which covers almost all devices. */
        const val IEEE_REGISTRY = "https://standards-oui.ieee.org/oui/oui.csv"
        private const val MIN_ENTRIES = 1_000
        private const val TIMEOUT_MILLIS = 20_000
        private const val MAX_DOWNLOAD_BYTES = 30L * 1024 * 1024
        private const val TAG = "MakerList"
    }
}

/**
 * Reads the IEEE registry in CSV form: a header, then rows of registry, assignment (six hex digits), organisation
 * name, and address. Fields may be quoted and hold commas or line breaks. Rows that do not fit are skipped.
 */
internal fun parseRegistry(reader: Reader): Map<Int, String> {
    val makers = HashMap<Int, String>()
    var header = true
    readCsv(reader) { fields ->
        if (header) {
            header = false
            return@readCsv
        }
        val assignment = fields.getOrNull(1)?.trim() ?: return@readCsv
        val name = fields.getOrNull(2)?.let(::cleanMaker)?.ifEmpty { null } ?: return@readCsv
        if (assignment.length != PREFIX_DIGITS) return@readCsv
        val prefix = assignment.toIntOrNull(16) ?: return@readCsv
        makers[prefix] = name
    }
    return makers
}

/** Reads the list as saved by [MakerList]: one "PREFIX<tab>Name" per line. */
internal fun readSaved(reader: Reader): Map<Int, String> {
    val makers = HashMap<Int, String>()
    reader.useLines { lines ->
        lines.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab == PREFIX_DIGITS) {
                line.substring(0, tab).toIntOrNull(16)?.let { makers[it] = line.substring(tab + 1) }
            }
        }
    }
    return makers
}

/** Calls [onRow] with the fields of each CSV row, honouring quotes ("a, b" and doubled "" inside them). */
private fun readCsv(reader: Reader, onRow: (List<String>) -> Unit) {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var pending = -1
    while (true) {
        val code = if (pending >= 0) pending.also { pending = -1 } else reader.read()
        if (code < 0) break
        val char = code.toChar()
        when {
            quoted && char == '"' -> {
                val next = reader.read()
                if (next == '"'.code) field.append('"') else {
                    quoted = false
                    pending = next
                }
            }
            quoted -> field.append(char)
            char == '"' -> quoted = true
            char == ',' -> {
                fields += field.toString()
                field.clear()
            }
            char == '\n' -> {
                fields += field.toString()
                field.clear()
                onRow(fields.toList())
                fields.clear()
            }
            char != '\r' -> field.append(char)
        }
    }
    if (field.isNotEmpty() || fields.isNotEmpty()) {
        fields += field.toString()
        onRow(fields.toList())
    }
}

/** Keeps a registry name safe to show: one line, no control characters, not too long. */
private fun cleanMaker(name: String): String =
    name.filterNot { it.isHiddenControl() }.trim().take(MAX_NAME_LENGTH)

/** Stops reading after [limit] bytes, so a wrong or hostile answer cannot fill the device. */
private class LimitedStream(private val inner: InputStream, private var limit: Long) : InputStream() {
    override fun read(): Int {
        if (limit <= 0) throw IOException("Download too large")
        return inner.read().also { if (it >= 0) limit-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (limit <= 0) throw IOException("Download too large")
        val count = inner.read(buffer, offset, minOf(length.toLong(), limit).toInt())
        if (count > 0) limit -= count
        return count
    }

    override fun close() = inner.close()
}

private const val PREFIX_DIGITS = 6
private const val LOCALLY_ADMINISTERED = 0x02
private const val MAX_NAME_LENGTH = 80
