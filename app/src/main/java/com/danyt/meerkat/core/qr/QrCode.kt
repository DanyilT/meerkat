package com.danyt.meerkat.core.qr

/** How much of a QR code can be damaged and still be read: about 7, 15, 25, or 30 percent. */
enum class ErrorCorrection(internal val formatBits: Int) { Low(1), Medium(0), Quartile(3), High(2) }

/**
 * A QR code symbol: a square of dark and light modules.
 *
 * Written for this app to avoid a library. It encodes bytes only (UTF-8 text), which every reader supports,
 * following ISO/IEC 18004. Draw it with a light border ("quiet zone") of 4 modules on every side.
 */
class QrCode private constructor(val version: Int, val size: Int, private val modules: Array<BooleanArray>) {

    /** True when the module at column [x], row [y] is dark. */
    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /** Encodes [text] as UTF-8 in the smallest version that fits. Throws when it is too long for any. */
        fun encode(text: String, errorCorrection: ErrorCorrection = ErrorCorrection.Medium): QrCode =
            encode(text.toByteArray(Charsets.UTF_8), errorCorrection)

        /**
         * Encodes [data] in the smallest version that fits.
         *
         * @param mask the mask pattern 0 to 7, or -1 to pick the one that reads best.
         */
        fun encode(data: ByteArray, errorCorrection: ErrorCorrection = ErrorCorrection.Medium, mask: Int = -1): QrCode {
            require(mask in -1..7) { "Mask must be -1 to 7" }
            val version = (1..MAX_VERSION).firstOrNull { version ->
                4 + countBits(version) + data.size * 8 <= dataCodewords(version, errorCorrection) * 8
            } ?: throw IllegalArgumentException("Data is too long for a QR code")
            val codewords = addErrorCorrection(dataBits(data, version, errorCorrection), version, errorCorrection)
            return Builder(version, errorCorrection).build(codewords, mask)
        }

        internal const val MAX_VERSION = 40

        /** Byte mode, with its 8 or 16 bit length. */
        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        /** The data codewords: mode, length, the bytes, a terminator, and padding up to the capacity. */
        private fun dataBits(data: ByteArray, version: Int, errorCorrection: ErrorCorrection): ByteArray {
            val capacity = dataCodewords(version, errorCorrection) * 8
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, countBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            return bits.toBytes()
        }

        /** Splits the data into blocks, adds Reed-Solomon codewords to each, and interleaves them. */
        internal fun addErrorCorrection(data: ByteArray, version: Int, errorCorrection: ErrorCorrection): ByteArray {
            val numBlocks = NUM_BLOCKS[errorCorrection.ordinal][version]
            val eccLength = ECC_PER_BLOCK[errorCorrection.ordinal][version]
            val raw = rawDataModules(version) / 8
            val numShortBlocks = numBlocks - raw % numBlocks
            val shortBlockLength = raw / numBlocks
            val divisor = reedSolomonDivisor(eccLength)
            var offset = 0
            val blocks = List(numBlocks) { index ->
                val length = shortBlockLength - eccLength + if (index < numShortBlocks) 0 else 1
                val block = data.copyOfRange(offset, offset + length)
                offset += length
                val ecc = reedSolomonRemainder(block, divisor)
                // Short blocks get a placeholder byte so every block has the same length for interleaving.
                (if (index < numShortBlocks) block + 0 else block) + ecc
            }
            val result = ByteArray(raw)
            var position = 0
            for (i in blocks[0].indices) {
                blocks.forEachIndexed { index, block ->
                    if (i != shortBlockLength - eccLength || index >= numShortBlocks) result[position++] = block[i]
                }
            }
            return result
        }

        /** How many modules hold data and error correction: everything except the fixed patterns. */
        internal fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val alignment = version / 7 + 2
                result -= (25 * alignment - 10) * alignment - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        internal fun dataCodewords(version: Int, errorCorrection: ErrorCorrection): Int =
            rawDataModules(version) / 8 -
                ECC_PER_BLOCK[errorCorrection.ordinal][version] * NUM_BLOCKS[errorCorrection.ordinal][version]

        // Error correction codewords per block, and number of blocks, by level (L, M, Q, H) and version.
        // Index 0 is unused. Values from ISO/IEC 18004, table 9.
        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(
                -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
                28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            ),
            intArrayOf(
                -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
                26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            ),
            intArrayOf(
                -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30,
                28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            ),
            intArrayOf(
                -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28,
                30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            ),
        )
        private val NUM_BLOCKS = arrayOf(
            intArrayOf(
                -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
                8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25,
            ),
            intArrayOf(
                -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
                17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
            ),
            intArrayOf(
                -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
                23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68,
            ),
            intArrayOf(
                -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25,
                25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81,
            ),
        )
    }

    /** Lays out one symbol: fixed patterns, data, mask, and format information. */
    private class Builder(private val version: Int, private val errorCorrection: ErrorCorrection) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun build(codewords: ByteArray, requestedMask: Int): QrCode {
            drawFunctionPatterns()
            placeData(codewords)
            val mask = if (requestedMask >= 0) requestedMask else (0..7).minBy { candidate ->
                applyMask(candidate)
                drawFormatBits(candidate)
                penalty().also { applyMask(candidate) } // A mask undoes itself.
            }
            applyMask(mask)
            drawFormatBits(mask)
            return QrCode(version, size, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            reserved[y][x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.lastIndex
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)
                    if (!corner) drawAlignment(positions[i], positions[j])
                }
            }
            drawFormatBits(0) // Reserves the area; the real bits are drawn after masking.
            drawVersionBits()
        }

        /** A finder pattern centered on [x], [y], with its light separator. */
        private fun drawFinder(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) set(xx, yy, distance != 2 && distance != 4)
                }
            }
        }

        private fun drawAlignment(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = IntArray(count)
            result[0] = 6
            var position = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = position
                position -= step
            }
            return result
        }

        private fun drawFormatBits(mask: Int) {
            val bits = formatBits(errorCorrection, mask)
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9..14) set(14 - i, 8, bit(bits, i))
            for (i in 0..7) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8..14) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true) // The dark module, always dark.
        }

        private fun drawVersionBits() {
            if (version < 7) return
            val bits = versionBits(version)
            for (i in 0 until 18) {
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, bit(bits, i))
                set(b, a, bit(bits, i))
            }
        }

        /** Fills the free modules in the standard zigzag, two columns at a time, from the bottom right. */
        private fun placeData(codewords: ByteArray) {
            var index = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5 // Skip the vertical timing pattern.
                for (step in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - step else step
                        if (!reserved[y][x] && index < codewords.size * 8) {
                            modules[y][x] = (codewords[index ushr 3].toInt() shr (7 - (index and 7))) and 1 == 1
                            index++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (!reserved[y][x] && maskHits(mask, x, y)) modules[y][x] = !modules[y][x]
                }
            }
        }

        /** The standard score for how hard a symbol is to read. Lower is better. */
        private fun penalty(): Int {
            var result = 0
            for (line in 0 until size) {
                result += runPenalty { modules[line][it] } + runPenalty { modules[it][line] }
                result += finderLikePenalty { modules[line][it] } + finderLikePenalty { modules[it][line] }
            }
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val color = modules[y][x]
                    if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                        result += 3
                    }
                }
            }
            val total = size * size
            val dark = modules.sumOf { row -> row.count { it } }
            result += ((kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1) * 10
            return result
        }

        private fun runPenalty(at: (Int) -> Boolean): Int {
            var result = 0
            var run = 1
            for (i in 1 until size) {
                if (at(i) == at(i - 1)) {
                    run++
                } else {
                    if (run >= 5) result += run - 2
                    run = 1
                }
            }
            if (run >= 5) result += run - 2
            return result
        }

        private fun finderLikePenalty(at: (Int) -> Boolean): Int {
            var result = 0
            for (i in 0..size - 11) {
                val window = (0 until 11).map { at(i + it) }
                if (window == FinderLike || window == FinderLike.reversed()) result += 40
            }
            return result
        }

        companion object {
            /** Dark-light-dark-dark-dark-light-dark followed by four light modules. */
            val FinderLike = listOf(true, false, true, true, true, false, true, false, false, false, false)
        }
    }
}

internal fun maskHits(mask: Int, x: Int, y: Int): Boolean = when (mask) {
    0 -> (x + y) % 2 == 0
    1 -> y % 2 == 0
    2 -> x % 3 == 0
    3 -> (x + y) % 3 == 0
    4 -> (x / 3 + y / 2) % 2 == 0
    5 -> x * y % 2 + x * y % 3 == 0
    6 -> (x * y % 2 + x * y % 3) % 2 == 0
    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
}

/** The 15 format bits: error correction level and mask, protected by a BCH code and XORed with 0x5412. */
internal fun formatBits(errorCorrection: ErrorCorrection, mask: Int): Int {
    val data = errorCorrection.formatBits shl 3 or mask
    var remainder = data
    repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
    return (data shl 10 or remainder) xor 0x5412
}

/** The 18 version bits, used from version 7 on. */
internal fun versionBits(version: Int): Int {
    var remainder = version
    repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
    return version shl 12 or remainder
}

private fun bit(value: Int, index: Int) = (value ushr index) and 1 != 0

internal fun reedSolomonDivisor(degree: Int): ByteArray {
    val result = ByteArray(degree)
    result[degree - 1] = 1
    var root = 1
    repeat(degree) {
        for (j in result.indices) {
            result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
            if (j + 1 < result.size) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
        }
        root = gfMultiply(root, 0x02)
    }
    return result
}

internal fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
    val result = ByteArray(divisor.size)
    for (byte in data) {
        val factor = (byte.toInt() xor result[0].toInt()) and 0xFF
        System.arraycopy(result, 1, result, 0, result.size - 1)
        result[result.size - 1] = 0
        for (i in result.indices) {
            result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
        }
    }
    return result
}

/** Multiplication in GF(2^8) with the QR polynomial x^8 + x^4 + x^3 + x^2 + 1. */
private fun gfMultiply(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
        z = (z shl 1) xor ((z ushr 7) * 0x11D)
        z = z xor (((y ushr i) and 1) * x)
    }
    return z
}

private class BitBuffer {
    private val bits = ArrayList<Boolean>()
    val size: Int get() = bits.size

    fun append(value: Int, length: Int) {
        for (i in length - 1 downTo 0) bits += (value ushr i) and 1 == 1
    }

    fun toBytes(): ByteArray = ByteArray(bits.size / 8) { index ->
        (0 until 8).fold(0) { acc, i -> acc shl 1 or if (bits[index * 8 + i]) 1 else 0 }.toByte()
    }
}

/**
 * The text of a "join this Wi-Fi" QR code, as camera apps and Android's Wi-Fi settings read it.
 *
 * @param security "WPA" for WPA2 and WPA2/WPA3, "SAE" for WPA3 only, or null for an open network.
 */
fun wifiQrText(ssid: String, password: String?, security: String?): String {
    fun escape(value: String) = buildString {
        value.forEach { char ->
            if (char in ESCAPED) append('\\')
            append(char)
        }
    }
    return if (security == null || password.isNullOrEmpty()) {
        "WIFI:T:nopass;S:${escape(ssid)};;"
    } else {
        "WIFI:T:$security;S:${escape(ssid)};P:${escape(password)};;"
    }
}

/** Characters with a meaning in the Wi-Fi QR format, which must be preceded by a backslash. */
private const val ESCAPED = "\\;,\":"
