package com.danyt.meerkat.core.reports

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes [text] to a document the user picked with the system file picker. Returns false when it could not
 * be written.
 */
suspend fun writeDocument(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        // "wt" empties the file first, in case the user chose to replace an existing one.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * Reads a document the user picked as text. Returns null when it cannot be read or is larger than [maxBytes],
 * so an oversized file is never loaded into memory whole.
 */
suspend fun readDocument(context: Context, uri: Uri, maxBytes: Int = MAX_REPORT_BYTES): String? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(maxBytes + 1)
                var size = 0
                while (size < bytes.size) {
                    val read = input.read(bytes, size, bytes.size - size)
                    if (read < 0) break
                    size += read
                }
                if (size > maxBytes) null else String(bytes, 0, size, Charsets.UTF_8)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
