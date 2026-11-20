package com.danyt.meerkat.core.nearby

import java.util.Calendar

/**
 * What a bank card gives any contactless reader that asks: its number, expiry date, and sometimes the holder's
 * name. Read only when the user turned this on, and kept in memory only.
 *
 * @property number the card number, digits only.
 * @property expiry the expiry date as "MM/YY".
 */
data class CardDetails(val number: String?, val expiry: String?, val holder: String?) {
    /** The number in groups of four, such as "4111 1111 1111 1111". */
    val formattedNumber: String? get() = number?.chunked(4)?.joinToString(" ")
}

/** SELECT by name for a payment application [aid], as a contactless terminal sends it. */
fun selectApplication(aid: ByteArray): ByteArray =
    byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

/** The identifiers (tag 4F) of the payment applications in a payment directory answer. */
fun applicationIds(directory: ByteArray): List<ByteArray> {
    if (!directory.succeeded()) return emptyList()
    return findAll(directory.copyOf(directory.size - 2), TAG_AID)
}

/**
 * GET PROCESSING OPTIONS, answering the card's list of wanted terminal data (its PDOL, tag 9F38 in the
 * selection answer) with plain values: no amount, today's date, a random number. It asks the card what to read;
 * it does not make a payment.
 */
fun processingOptions(
    selection: ByteArray,
    today: Calendar = Calendar.getInstance(),
    readerAbilities: ByteArray = ReaderAbilities.first(),
    amount: ByteArray = ByteArray(AMOUNT_LENGTH),
): ByteArray {
    val pdol = if (selection.succeeded()) {
        findAll(selection.copyOf(selection.size - 2), TAG_PDOL).firstOrNull()
    } else {
        null
    }
    val data = pdol?.let { terminalData(it, today, readerAbilities, amount) } ?: ByteArray(0)
    val header = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, (data.size + 2).toByte())
    return header + byteArrayOf(0x83.toByte(), data.size.toByte()) + data + byteArrayOf(0x00)
}

/** Fills a data object list (pairs of tag and length) with the values a simple terminal would give. */
internal fun terminalData(
    dol: ByteArray,
    today: Calendar,
    readerAbilities: ByteArray,
    amount: ByteArray = ByteArray(AMOUNT_LENGTH),
): ByteArray {
    val out = mutableListOf<Byte>()
    var at = 0
    while (at < dol.size) {
        var tag = dol[at++].toInt() and 0xFF
        if (tag and 0x1F == 0x1F && at < dol.size) tag = (tag shl 8) or (dol[at++].toInt() and 0xFF)
        if (at >= dol.size) break
        val length = dol[at++].toInt() and 0xFF
        val value = when (tag) {
            0x9F66 -> readerAbilities // Terminal transaction qualifiers: what this "reader" can do.
            0x9F02 -> amount // Amount, in BCD cents. Nothing is ever sent to a bank, so no payment happens.
            0x9F1A, 0x5F2A -> byteArrayOf(0x08, 0x26) // Terminal country and currency codes.
            0x9A -> bcdDate(today)
            0x9F37 -> ByteArray(4).also { java.security.SecureRandom().nextBytes(it) } // Unpredictable number.
            0x9F35 -> byteArrayOf(0x22) // Terminal type: attended, offline capable.
            else -> ByteArray(0) // Amounts, results, and everything else: zero.
        }
        repeat(length) { out += value.getOrElse(it) { 0 } }
    }
    return out.toByteArray()
}

private fun bcdDate(today: Calendar): ByteArray {
    fun bcd(value: Int) = (((value / 10) shl 4) or (value % 10)).toByte()
    return byteArrayOf(
        bcd(today.get(Calendar.YEAR) % 100),
        bcd(today.get(Calendar.MONTH) + 1),
        bcd(today.get(Calendar.DAY_OF_MONTH)),
    )
}

/**
 * Terminal transaction qualifiers to offer, in order. Cards differ in which they accept, so when a card refuses
 * the processing options, the next is tried.
 */
val ReaderAbilities: List<ByteArray> = listOf(
    byteArrayOf(0xF0.toByte(), 0x20, 0x40, 0x00),
    byteArrayOf(0x36, 0x00, 0x40, 0x00),
    byteArrayOf(0x26, 0x00, 0x00, 0x00),
)

/**
 * The ways to ask for processing options, in order: each reader ability with no amount, then one cent, which
 * some cards want before they answer. Each is only a question to the card; no payment is made.
 */
val ProcessingAttempts: List<Pair<ByteArray, ByteArray>> =
    ReaderAbilities.map { it to ByteArray(AMOUNT_LENGTH) } +
        (ReaderAbilities.first() to byteArrayOf(0, 0, 0, 0, 0, 0x01))

/** Payment applications to try one by one on a card that has no payment directory. */
val KnownPaymentApplications: List<ByteArray> = listOf(
    "A0000000031010", "A0000000032010", "A0000000041010", "A0000000043060", "A00000002501",
    "A0000000651010", "A0000001523010", "A000000333010101", "A0000002771010", "A0000006581010", "A0000000421010",
).map { hexText -> ByteArray(hexText.length / 2) { hexText.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }

/** The status word at the end of a card's answer, such as "6985", or null for an answer too short to have one. */
fun statusWord(answer: ByteArray): String? = if (answer.size < 2) {
    null
} else {
    answer.copyOfRange(answer.size - 2, answer.size).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }.uppercase()
}

/** Whether an answer from a card is a success (status 90 00). */
fun cardAnswered(answer: ByteArray): Boolean = answer.succeeded()

/** Whether an answer says the record or file asked for does not exist (6A83 or 6A82). */
fun noSuchRecord(answer: ByteArray): Boolean = answer.size == 2 && answer[0] == 0x6A.toByte() &&
    (answer[1] == 0x83.toByte() || answer[1] == 0x82.toByte())

/**
 * Why a bank card's details could not be read, when the card did not refuse with a status word of its own (a
 * refusal is kept as its status word, such as "6985").
 */
object CardProblem {
    /** The card left the field while it was being read. */
    const val LEFT = "left"

    /** No payment application answered. */
    const val NO_APPLICATION = "no-app"

    /** The card answered, but none of its records held the number. */
    const val NO_NUMBER = "no-number"
}

/** A file and the range of records in it that the card says hold its data. */
data class RecordRange(val file: Int, val first: Int, val last: Int)

/**
 * The records to read, from the processing options answer: either a short form (tag 80: two bytes of
 * capabilities, then the list) or a full one (tag 77 holding tag 94).
 */
fun recordRanges(options: ByteArray): List<RecordRange> {
    if (!options.succeeded()) return emptyList()
    val body = options.copyOf(options.size - 2)
    val list = berTlv(body).firstNotNullOfOrNull { (tag, value) ->
        when (tag) {
            0x80 -> value.drop(2).toByteArray()
            0x77 -> berTlv(value).firstOrNull { it.first == 0x94 }?.second
            else -> null
        }
    } ?: return emptyList()
    return list.toList().chunked(4).filter { it.size == 4 }.map { entry ->
        RecordRange(
            file = (entry[0].toInt() and 0xFF) shr 3,
            first = entry[1].toInt() and 0xFF,
            last = entry[2].toInt() and 0xFF,
        )
    }.filter { it.file in 1..30 && it.first in 1..it.last }
}

/** READ RECORD for one record of a file. */
fun readRecord(file: Int, record: Int): ByteArray =
    byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((file shl 3) or 0x04).toByte(), 0x00)

/**
 * Finds the card's number, expiry date, and holder name in any of [answers] (the processing options and the
 * records): tags 5A, 5F24, and 5F20, or the magnetic-stripe-style tag 57 that holds number and expiry together.
 */
fun cardDetails(answers: List<ByteArray>): CardDetails? {
    var number: String? = null
    var expiry: String? = null
    var holder: String? = null
    for (answer in answers.filter { it.succeeded() }) {
        val body = answer.copyOf(answer.size - 2)
        findAll(body, TAG_PAN).firstOrNull()?.let { number = number ?: digits(it) }
        findAll(body, TAG_EXPIRY).firstOrNull()?.let { value ->
            val text = hex(value)
            if (text.length >= 4) expiry = expiry ?: "${text.substring(2, 4)}/${text.substring(0, 2)}"
        }
        // Track 2 equivalent data: under tag 57, or 9F6B on some contactless cards.
        (findAll(body, TAG_TRACK2) + findAll(body, TAG_TRACK2_CONTACTLESS)).firstOrNull()?.let { value ->
            val text = hex(value)
            val separator = text.indexOf('D')
            if (separator > 0) {
                number = number ?: text.substring(0, separator)
                if (text.length >= separator + 5) {
                    val yymm = text.substring(separator + 1, separator + 5)
                    expiry = expiry ?: "${yymm.substring(2)}/${yymm.substring(0, 2)}"
                }
            }
        }
        // Track 1 data: text such as "B4111111111111111^SURNAME/NAME^2712...".
        findAll(body, TAG_TRACK1).firstOrNull()?.let { value ->
            val parts = value.toString(Charsets.US_ASCII).split('^')
            if (parts.size >= 3) {
                number = number ?: parts[0].trimStart('B', 'b').filter(Char::isDigit)
                holder = holder ?: cleanName(parts[1]).trim('/', ' ').ifEmpty { null }
                if (parts[2].length >= 4 && parts[2].take(4).all(Char::isDigit)) {
                    expiry = expiry ?: "${parts[2].substring(2, 4)}/${parts[2].substring(0, 2)}"
                }
            }
        }
        findAll(body, TAG_HOLDER).firstOrNull()?.let { value ->
            // Many cards leave the name blank or write a placeholder such as " /".
            holder = holder ?: cleanName(value.toString(Charsets.US_ASCII)).trim('/', ' ').ifEmpty { null }
        }
    }
    val validNumber = number?.takeIf { it.length in 12..19 && it.all(Char::isDigit) }
    if (validNumber == null && expiry == null) return null
    return CardDetails(validNumber, expiry, holder)
}

/** Every value with [tag] anywhere in the BER-TLV [data], searching inside constructed values too. */
internal fun findAll(data: ByteArray, tag: Int, depth: Int = 0): List<ByteArray> {
    if (depth > MAX_DEPTH) return emptyList()
    return berTlv(data).flatMap { (found, value) ->
        when {
            found == tag -> listOf(value)
            isConstructed(found) -> findAll(value, tag, depth + 1)
            else -> emptyList()
        }
    }
}

/** Whether a card's answer ends with the status word 90 00, "done". */
private fun ByteArray.succeeded(): Boolean =
    size >= 2 && this[size - 2] == 0x90.toByte() && this[size - 1] == 0.toByte()

private fun hex(value: ByteArray) =
    value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()

/** BCD digits, without the F that pads an odd count. */
private fun digits(value: ByteArray) = hex(value).trimEnd('F')

private const val TAG_AID = 0x4F
private const val TAG_PDOL = 0x9F38
private const val TAG_PAN = 0x5A
private const val TAG_EXPIRY = 0x5F24
private const val TAG_TRACK2 = 0x57
private const val TAG_TRACK2_CONTACTLESS = 0x9F6B
private const val TAG_TRACK1 = 0x56
private const val TAG_HOLDER = 0x5F20
private const val MAX_DEPTH = 5
private const val AMOUNT_LENGTH = 6
