package com.danyt.meerkat.core.nearby

/**
 * What an Apple device announces in its Bluetooth Low Energy messages ("Continuity"), as far as it is known
 * from public research. Apple does not document these, so they are shown as hints.
 *
 * @property messages the kinds of message it sends, by type number (see [AppleMessage]).
 * @property model the earbuds or headphones model number, from a pairing message.
 * @property left, [right], [case] battery levels in percent from a pairing message, or null when unknown.
 */
data class AppleInfo(
    val messages: List<Int> = emptyList(),
    val model: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
)

/** Apple message types that are commonly seen. */
object AppleMessage {
    const val AIRPRINT = 0x03
    const val AIRDROP = 0x05
    const val HOMEKIT = 0x06
    const val PROXIMITY_PAIRING = 0x07
    const val SIRI = 0x08
    const val AIRPLAY_TARGET = 0x09
    const val AIRPLAY_SOURCE = 0x0A
    const val MAGIC_SWITCH = 0x0B
    const val HANDOFF = 0x0C
    const val HOTSPOT_TARGET = 0x0D
    const val HOTSPOT_SOURCE = 0x0E
    const val NEARBY_ACTION = 0x0F
    const val NEARBY_INFO = 0x10
    const val FIND_MY = 0x12
}

/** Earbuds and headphones by the model number in their pairing message. */
val AppleAudioModels = mapOf(
    0x0220 to "AirPods (1st generation)",
    0x0F20 to "AirPods (2nd generation)",
    0x1320 to "AirPods (3rd generation)",
    0x0E20 to "AirPods Pro",
    0x1420 to "AirPods Pro (2nd generation)",
    0x0A20 to "AirPods Max",
)

/**
 * Reads Apple's manufacturer data: a series of messages, each a type byte, a length byte, and that many bytes.
 * Stops at the first message whose length runs past the data.
 */
fun parseApple(data: ByteArray): AppleInfo {
    var info = AppleInfo()
    val types = mutableListOf<Int>()
    var at = 0
    while (at + 2 <= data.size) {
        val type = data[at].toInt() and 0xFF
        val length = data[at + 1].toInt() and 0xFF
        val start = at + 2
        if (start + length > data.size) break
        types += type
        if (type == AppleMessage.PROXIMITY_PAIRING && length >= PAIRING_MIN_LENGTH) {
            info = withPairing(info, data.copyOfRange(start, start + length))
        }
        at = start + length
    }
    return info.copy(messages = types.distinct())
}

/**
 * A pairing message from earbuds: a prefix byte, the model (2 bytes), a status byte, the two earbuds' battery
 * levels (a 4-bit value each), then charging flags and the case's level. Levels run 0 to 10 (tens of percent);
 * 15 means unknown. A status bit says which earbud's level comes first.
 */
private fun withPairing(info: AppleInfo, payload: ByteArray): AppleInfo {
    val model = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
    val status = payload[3].toInt() and 0xFF
    val pods = payload[4].toInt() and 0xFF
    val case = payload[5].toInt() and 0x0F
    val leftFirst = (status shr 4) and 0x02 == 0
    val high = pods shr 4
    val low = pods and 0x0F
    return info.copy(
        model = model,
        left = level(if (leftFirst) high else low),
        right = level(if (leftFirst) low else high),
        case = level(case),
    )
}

private fun level(value: Int): Int? = if (value in 0..10) value * 10 else null

/**
 * The appearance a Low Energy device announces about itself (what kind of device it is), read from its raw
 * advertisement, or null when it does not say. The advertisement is a series of structures: a length byte, a
 * type byte, and the data.
 */
fun appearanceOf(advertisement: ByteArray): Int? {
    var at = 0
    while (at < advertisement.size) {
        val length = advertisement[at].toInt() and 0xFF
        if (length == 0 || at + 1 + length > advertisement.size) return null
        val type = advertisement[at + 1].toInt() and 0xFF
        if (type == AD_APPEARANCE && length >= 3) {
            return (advertisement[at + 2].toInt() and 0xFF) or ((advertisement[at + 3].toInt() and 0xFF) shl 8)
        }
        at += 1 + length
    }
    return null
}

/** The category of an appearance value: its upper ten bits, such as 3 for a watch. */
fun appearanceCategory(appearance: Int): Int = appearance shr 6

/** Appearance categories that are commonly seen, from the Bluetooth assigned numbers. */
object AppearanceCategory {
    const val PHONE = 0x01
    const val COMPUTER = 0x02
    const val WATCH = 0x03
    const val CLOCK = 0x04
    const val DISPLAY = 0x05
    const val REMOTE = 0x06
    const val GLASSES = 0x07
    const val TAG = 0x08
    const val KEYRING = 0x09
    const val MEDIA_PLAYER = 0x0A
    const val THERMOMETER = 0x0C
    const val HEART_RATE = 0x0D
    const val INPUT = 0x0F
    const val SENSOR = 0x15
    const val LIGHT = 0x16
    const val LIGHT_SOURCE = 0x1F
    const val SPEAKER = 0x21
    const val AUDIO_SOURCE = 0x22
    const val HEADPHONES = 0x25
    const val AV_EQUIPMENT = 0x27
    const val HEARING_AID = 0x29
    const val GAMING = 0x2A
}

private const val AD_APPEARANCE = 0x19
private const val PAIRING_MIN_LENGTH = 6
