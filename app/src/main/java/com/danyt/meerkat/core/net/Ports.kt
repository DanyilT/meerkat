package com.danyt.meerkat.core.net

/**
 * Parses a list of ports typed by the user, such as "22, 80, 8000-8010".
 *
 * Returns the ports sorted and without repeats. Empty text gives an empty list. Returns null when the text is
 * malformed, a port is outside 1 to 65535, a range runs backwards, or there are more than [maxCount] ports.
 */
fun parsePorts(text: String, maxCount: Int = MAX_PORT): List<Int>? {
    if (text.isBlank()) return emptyList()
    val ports = sortedSetOf<Int>()
    for (part in text.split(',')) {
        val piece = part.trim()
        val first = parsePort(piece.substringBefore('-').trim()) ?: return null
        val last = if ('-' in piece) parsePort(piece.substringAfter('-').trim()) ?: return null else first
        // Checked before expanding, so a huge range is refused without building it.
        if (first > last || last - first + 1 > maxCount) return null
        ports += first..last
        if (ports.size > maxCount) return null
    }
    return ports.toList()
}

private fun parsePort(text: String): Int? {
    if (text.isEmpty() || text.length > 5 || !text.all { it in '0'..'9' }) return null
    return text.toInt().takeIf { it in 1..MAX_PORT }
}

const val MAX_PORT = 65535
