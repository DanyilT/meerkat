package com.danyt.meerkat.core.net

/**
 * Tidies an address field while the user types.
 *
 * - Only digits and dots are kept, plus `/` and `-` with [allowRange] (for "192.168.1.0/24" and
 *   "192.168.1.10-50").
 * - While typing at the end, a dot is added as soon as a number is complete: after three digits, or after two
 *   when no third digit could follow (26 and up). After the fourth number a `/` is added with [allowRange].
 * - A digit that would push a number past 255 starts the next number instead: "25" then "6" gives "25.6".
 * - A dot typed after the fourth number becomes a `/` with [allowRange], because number pads have a dot key
 *   but often hide the slash.
 * - Typing a dot or slash right after one that was added automatically does nothing, so both habits work.
 *
 * - A keystroke that could never lead to a valid address is refused: a fifth number, a number above 255, a
 *   prefix length above 32, a second slash.
 *
 * Nothing is added while deleting or editing in the middle, or once a `-` is present, because the end of a
 * range may be a short number such as "50".
 *
 * @param old the text before the change.
 * @param new the text the keyboard asks for.
 * @return the text to show.
 */
fun formatAddressInput(old: String, new: String, allowRange: Boolean): String {
    val allowed = if (allowRange) "0123456789./-" else "0123456789."
    val text = new.filter { it in allowed }
    val result = autoSeparate(old, text, allowRange)
    // Refuse what cannot become an address. If the field somehow already held such text, let the edit through
    // so the user is never stuck.
    return if (isPartialAddress(result, allowRange) || !isPartialAddress(old, allowRange)) result else old
}

private fun autoSeparate(old: String, text: String, allowRange: Boolean): String {
    val typedAtEnd = text.length == old.length + 1 && text.startsWith(old)
    if (!typedAtEnd) return text

    val typed = text.last()
    // A separator typed by hand right after the same one: keep the one that is there.
    if ((typed == '.' || typed == '/') && old.endsWith(typed)) return old
    if ('-' in text || '/' in old) return text
    // A fifth number cannot follow, so a dot here can only mean "now the prefix length".
    if (typed == '.' && allowRange && old.count { it == '.' } == 3 && !old.endsWith('.')) return "$old/"
    if (!typed.isDigit()) return text

    val numbers = text.split('.')
    val last = numbers.last()
    val isFourth = numbers.size == 4
    return when {
        numbers.size > 4 || last.length > 3 -> text
        last.length == 3 && last.toInt() > 255 && !isFourth -> text.dropLast(1) + "." + typed
        last.length == 3 || (last.length == 2 && last.toInt() > 25) -> when {
            !isFourth -> "$text."
            allowRange -> "$text/"
            else -> text
        }
        else -> text
    }
}

/**
 * True when [text] is a valid address or target, or could still become one by typing more.
 *
 * With [allowRange] the forms are those of [parseTarget]: an address, `address/prefix`, `address-address`, or
 * `address-number`. Without it, only a single address.
 */
internal fun isPartialAddress(text: String, allowRange: Boolean): Boolean {
    if (text.isEmpty()) return true
    if (!allowRange) return isPartialDotted(text)
    return when {
        '-' in text -> {
            val start = text.substringBefore('-')
            val end = text.substringAfter('-')
            '/' !in text && Ipv4.parse(start) != null && '-' !in end && isPartialDotted(end)
        }
        '/' in text -> {
            val prefix = text.substringAfter('/')
            Ipv4.parse(text.substringBefore('/')) != null &&
                prefix.length <= 2 && prefix.all { it.isDigit() } && (prefix.isEmpty() || prefix.toInt() <= 32)
        }
        else -> isPartialDotted(text)
    }
}

/** Up to four numbers of at most three digits and at most 255, with single dots between them. */
private fun isPartialDotted(text: String): Boolean {
    if (text.isEmpty()) return true
    val numbers = text.split('.')
    if (numbers.size > 4) return false
    return numbers.withIndex().all { (index, number) ->
        val isLast = index == numbers.lastIndex
        when {
            number.isEmpty() -> isLast && index > 0
            else -> number.length <= 3 && number.all { it.isDigit() } && number.toInt() <= 255
        }
    }
}
