package com.danyt.meerkat.core.nearby

// Reading what common NFC chips say about themselves when asked politely: commands any reader may send, which
// unlock nothing. Every answer comes from whatever card is held to the device, so lengths are always checked.

/** An NXP sticker or ticket chip, from its version answer. [userMemory] is the space for data, in bytes. */
data class NxpChip(val name: String, val userMemory: Int?)

/**
 * Reads the answer to NXP's GET_VERSION command (0x60), which NTAG and MIFARE Ultralight EV1 chips give: a
 * header, the vendor, the product type and subtype, the version, the memory size code, and the protocol.
 */
fun nxpChip(version: ByteArray): NxpChip? {
    if (version.size < 8 || version[1].toInt() != NXP) return null
    val type = version[2].toInt() and 0xFF
    val size = version[6].toInt() and 0xFF
    return when (type) {
        NXP_NTAG -> when (size) {
            0x0B -> NxpChip("NTAG210", 48)
            0x0E -> NxpChip("NTAG212", 128)
            0x0F -> NxpChip("NTAG213", 144)
            0x11 -> NxpChip("NTAG215", 504)
            0x13 -> NxpChip("NTAG216", 888)
            else -> NxpChip("NTAG", null)
        }
        NXP_ULTRALIGHT -> when (size) {
            0x0B -> NxpChip("MIFARE Ultralight EV1", 48)
            0x0E -> NxpChip("MIFARE Ultralight EV1", 128)
            else -> NxpChip("MIFARE Ultralight", null)
        }
        else -> null
    }
}

/**
 * Reads the first part of a MIFARE DESFire GetVersion answer: vendor, type, subtype, major and minor version,
 * memory size code, and protocol. Returns the chip name and its memory in bytes, or null for another chip.
 */
fun desfireVersion(hardware: ByteArray): Pair<String, Int>? {
    if (hardware.size < 7 || hardware[0].toInt() != NXP || hardware[1].toInt() != DESFIRE) return null
    val name = when (hardware[3].toInt() and 0xFF) {
        0x00 -> "MIFARE DESFire"
        0x01 -> "MIFARE DESFire EV1"
        0x12 -> "MIFARE DESFire EV2"
        0x33 -> "MIFARE DESFire EV3"
        else -> "MIFARE DESFire"
    }
    // The size code is twice the power of two of the memory; an odd code means "a bit more", ignored here.
    val memory = 1 shl ((hardware[5].toInt() and 0xFF) shr 1)
    return name to memory
}

/** A payment application on a bank card: its network, and the name the card gives it, such as "VISA DEBIT". */
data class PaymentApp(val network: String, val label: String?)

/**
 * Reads a bank card's answer to selecting its payment directory ("2PAY.SYS.DDF01"): the payment applications it
 * offers, each with an identifier (tag 4F) and a name (tag 50). The directory holds no card number or expiry date;
 * those are read only when the user turned that on (see [cardDetails]). Returns an empty list when the answer is
 * not a success.
 */
fun paymentApps(answer: ByteArray): List<PaymentApp> {
    if (answer.size < 2 || answer[answer.size - 2] != 0x90.toByte() || answer[answer.size - 1] != 0.toByte()) {
        return emptyList()
    }
    val apps = mutableListOf<PaymentApp>()
    fun walk(data: ByteArray, depth: Int) {
        var aid: ByteArray? = null
        var label: String? = null
        for ((tag, value) in berTlv(data)) {
            when {
                tag == TAG_AID -> aid = value
                tag == TAG_LABEL -> label = cleanName(value.toString(Charsets.US_ASCII)).ifEmpty { null }
                isConstructed(tag) && depth < MAX_TLV_DEPTH -> walk(value, depth + 1)
            }
        }
        aid?.let { apps += PaymentApp(paymentNetwork(it), label) }
    }
    walk(answer.copyOf(answer.size - 2), 0)
    return apps
}

/** The network a payment application identifier belongs to, by its registered provider prefix. */
fun paymentNetwork(aid: ByteArray): String {
    val hex = aid.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
    if (hex.startsWith("A0000000043060")) return "Maestro"
    return PaymentProviders.entries.firstOrNull { hex.startsWith(it.key) }?.value ?: hex
}

/** Reads BER-TLV data, the format of smart card answers: (tag, value) pairs at one level. */
internal fun berTlv(data: ByteArray): List<Pair<Int, ByteArray>> {
    val items = mutableListOf<Pair<Int, ByteArray>>()
    var at = 0
    while (at < data.size) {
        var tag = data[at++].toInt() and 0xFF
        if (tag == 0x00 || tag == 0xFF) continue // Padding.
        if (tag and 0x1F == 0x1F) {
            // A multi-byte tag: more bytes follow while the top bit is set.
            do {
                if (at >= data.size) return items
                val next = data[at++].toInt() and 0xFF
                tag = (tag shl 8) or next
            } while (next and 0x80 != 0 && tag < 0xFFFFFF)
        }
        if (at >= data.size) return items
        var length = data[at++].toInt() and 0xFF
        if (length and 0x80 != 0) {
            val count = length and 0x7F
            if (count > 2 || at + count > data.size) return items
            length = 0
            repeat(count) { length = (length shl 8) or (data[at++].toInt() and 0xFF) }
        }
        if (at + length > data.size) return items
        items += tag to data.copyOfRange(at, at + length)
        at += length
    }
    return items
}

/** Whether a BER-TLV [tag] holds more tags inside (bit 6 of its first byte). */
internal fun isConstructed(tag: Int): Boolean {
    var first = tag
    while (first > 0xFF) first = first shr 8
    return first and 0x20 != 0
}

/** What an ISO 15693 tag (libraries, ski passes, industry) says about itself. Unknown parts are null. */
data class VicinityInfo(val manufacturer: String?, val memory: Int?)

/**
 * Reads an ISO 15693 tag's maker from its ID, and its memory from its answer to Get System Information
 * (command 0x2B): flags, which fields follow, the ID, then optionally DSFID, AFI, and the block count and size.
 */
fun vicinityInfo(id: ByteArray, systemInfo: ByteArray?): VicinityInfo {
    // The ID starts with E0 and the maker's code; Android gives it least significant byte first.
    val maker = when {
        id.size == 8 && id[7] == 0xE0.toByte() -> id[6].toInt() and 0xFF
        id.size == 8 && id[0] == 0xE0.toByte() -> id[1].toInt() and 0xFF
        else -> null
    }
    var memory: Int? = null
    if (systemInfo != null && systemInfo.size >= 10 && systemInfo[0].toInt() and 0x01 == 0) {
        val present = systemInfo[1].toInt()
        var at = 10
        if (present and 0x01 != 0) at++ // DSFID.
        if (present and 0x02 != 0) at++ // AFI.
        if (present and 0x04 != 0 && at + 1 < systemInfo.size) {
            val blocks = (systemInfo[at].toInt() and 0xFF) + 1
            val blockSize = (systemInfo[at + 1].toInt() and 0x1F) + 1
            memory = blocks * blockSize
        }
    }
    return VicinityInfo(maker?.let { ChipMakers[it] ?: "0x" + it.toString(16).padStart(2, '0').uppercase() }, memory)
}

private const val NXP = 0x04
private const val NXP_NTAG = 0x04
private const val NXP_ULTRALIGHT = 0x03
private const val DESFIRE = 0x01
private const val TAG_AID = 0x4F
private const val TAG_LABEL = 0x50
private const val MAX_TLV_DEPTH = 4

/** Payment networks by the registered provider part of their application identifiers. */
private val PaymentProviders = linkedMapOf(
    "A000000003" to "Visa",
    "A000000004" to "Mastercard",
    "A000000025" to "American Express",
    "A000000065" to "JCB",
    "A000000152" to "Discover",
    "A000000333" to "UnionPay",
    "A000000277" to "Interac",
    "A000000658" to "Mir",
    "A000000042" to "Cartes Bancaires",
)

/** Chip makers by their ISO/IEC 7816-6 code, the ones seen most. */
private val ChipMakers = mapOf(
    0x02 to "STMicroelectronics",
    0x04 to "NXP",
    0x05 to "Infineon",
    0x07 to "Texas Instruments",
    0x16 to "EM Microelectronic",
)
