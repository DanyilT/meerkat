package com.danyt.meerkat.core.text

/**
 * Whether [this] character must never be shown from text that came from another device: control characters,
 * and the invisible marks that change the reading direction. Those marks can make "moc.lapyap.www" read as
 * "www.paypal.com", so a name, link, or network could pose as another.
 */
fun Char.isHiddenControl(): Boolean = isISOControl() || this in DirectionMarks

// Arabic letter mark, left-to-right and right-to-left marks, embeddings, overrides, and isolates.
private val DirectionMarks = setOf(
    '\u061C', '\u200E', '\u200F',
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
    '\u2066', '\u2067', '\u2068', '\u2069',
)
