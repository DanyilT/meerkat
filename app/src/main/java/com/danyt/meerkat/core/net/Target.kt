package com.danyt.meerkat.core.net

/** The most addresses one scan may cover. A /20 network fits; anything larger must be split by the user. */
const val MAX_TARGET_SIZE = 4096L

/** Why a typed target was rejected. The UI turns each into a message. */
enum class TargetError { Empty, Malformed, Reversed, TooLarge }

/** The outcome of [parseTarget]. */
sealed interface TargetResult {
    data class Valid(val range: IpRange) : TargetResult

    data class Invalid(val error: TargetError) : TargetResult
}

/**
 * Parses what the user typed as a scan target.
 *
 * Accepted forms:
 * - a single address: `192.168.1.10`
 * - a CIDR block: `192.168.1.0/24`
 * - a range: `192.168.1.10-192.168.1.50`, or `192.168.1.10-50` for a range within the last number
 *
 * A slash with nothing after it is ignored, so `192.168.1.10/` means the single address.
 *
 * @param hostsOnly leave the network and broadcast addresses out of a CIDR block. Use false when the block
 *   describes an area (a scope-guard range) and not a list of devices to probe.
 * @param maxSize the largest number of addresses allowed.
 */
fun parseTarget(text: String, hostsOnly: Boolean = true, maxSize: Long = MAX_TARGET_SIZE): TargetResult {
    val input = text.trim().removeSuffix("/").trim()
    if (input.isEmpty()) return TargetResult.Invalid(TargetError.Empty)
    val range = when {
        '/' in input -> parseCidr(input, hostsOnly)
        '-' in input -> {
            val start = Ipv4.parse(input.substringBefore('-').trim())
            val end = start?.let { parseRangeEnd(it, input.substringAfter('-').trim()) }
            when {
                start == null || end == null -> null
                start > end -> return TargetResult.Invalid(TargetError.Reversed)
                else -> IpRange(start, end)
            }
        }
        else -> Ipv4.parse(input)?.let { IpRange(it, it) }
    } ?: return TargetResult.Invalid(TargetError.Malformed)
    return if (range.size > maxSize) TargetResult.Invalid(TargetError.TooLarge) else TargetResult.Valid(range)
}

private fun parseCidr(input: String, hostsOnly: Boolean): IpRange? {
    val address = Ipv4.parse(input.substringBefore('/').trim()) ?: return null
    val prefixText = input.substringAfter('/').trim()
    if (prefixText.isEmpty() || prefixText.length > 2 || !prefixText.all { it in '0'..'9' }) return null
    val prefix = prefixText.toInt()
    return if (prefix > 32) null else cidrRange(address, prefix, hostsOnly)
}

/** The end of a range: a full address, or just the last number, which then replaces the last number of [start]. */
private fun parseRangeEnd(start: Ipv4, text: String): Ipv4? {
    if ('.' in text) return Ipv4.parse(text)
    if (text.isEmpty() || text.length > 3 || !text.all { it in '0'..'9' }) return null
    val octet = text.toInt()
    return if (octet > 255) null else Ipv4((start.bits and 0xFFFFFF00u) or octet.toUInt())
}

/**
 * The scope guard check: true when [target] lies completely inside one of the [authorized] ranges.
 *
 * A target that only partly overlaps a range, or is covered only by several ranges together, is not authorized.
 */
fun isAuthorized(target: IpRange, authorized: List<IpRange>): Boolean = authorized.any { it.containsAll(target) }

/**
 * Parses one scope guard entry: an address, a range, or a CIDR block of any size. CIDR blocks are taken whole.
 * Returns null when the text is not valid.
 */
fun parseScopeRange(text: String): IpRange? =
    (parseTarget(text, hostsOnly = false, maxSize = Long.MAX_VALUE) as? TargetResult.Valid)?.range

/**
 * The full scope guard check for a scan.
 *
 * @param saved the user's list of authorized ranges, as text. Entries that do not parse are ignored.
 * @param own networks that are always allowed, such as the one the phone is connected to.
 */
fun isInScope(target: IpRange, saved: List<String>, own: List<IpRange>): Boolean =
    isAuthorized(target, own + saved.mapNotNull(::parseScopeRange))
