package com.danyt.meerkat.core.storage

import java.util.Locale

private val UNITS = listOf("KB", "MB", "GB", "TB")

/**
 * Formats a byte count for display, for example "0 bytes", "512 bytes", "1.5 KB" or "20 MB".
 *
 * Uses 1024-based units, at most one decimal place, and drops a trailing ".0".
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return if (bytes == 1L) "1 byte" else "$bytes bytes"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    val text = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    return "$text ${UNITS[unit]}"
}
