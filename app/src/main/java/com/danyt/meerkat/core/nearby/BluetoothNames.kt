package com.danyt.meerkat.core.nearby

/**
 * Names of common makers by their Bluetooth company identifier, as assigned by the Bluetooth SIG. Only a
 * handful that are seen often; others are shown by number.
 */
private val Companies = mapOf(
    0x0002 to "Intel",
    0x0006 to "Microsoft",
    0x000D to "Texas Instruments",
    0x000F to "Broadcom",
    0x004C to "Apple",
    0x0059 to "Nordic Semiconductor",
    0x0075 to "Samsung",
    0x0087 to "Garmin",
    0x00E0 to "Google",
    0x0171 to "Amazon",
    0x027D to "Huawei",
    0x02E5 to "Espressif",
    0x038F to "Xiaomi",
)

/** The maker's name for a Bluetooth company [id], or its number in hex, such as "0x1234". */
fun companyName(id: Int): String = Companies[id] ?: "0x" + id.toString(16).padStart(4, '0').uppercase()

/** A 16-bit Bluetooth number in the usual form, such as "0x180F". */
fun shortUuidText(value: Int): String = "0x" + value.toString(16).padStart(4, '0').uppercase()

/**
 * A rough distance in metres from a received signal, by the usual log-distance rule. [txPower] is the power the
 * device says it sends with, when it says; otherwise a typical phone-class value is assumed. Walls, bodies, and
 * pockets weaken signals, so the estimate tends to run long.
 */
fun bluetoothDistanceMeters(dbm: Int, txPower: Int? = null): Float {
    // The expected signal at one metre: about 41 dB below the transmit power, or a typical -59 dBm.
    val atOneMeter = txPower?.let { it - 41 } ?: -59
    val meters = Math.pow(10.0, (atOneMeter - dbm) / (10.0 * PATH_LOSS_EXPONENT)).toFloat()
    return meters.coerceIn(0.1f, 100f)
}

/** How fast signals fade indoors: 2 in open space, more with walls and furniture. */
private const val PATH_LOSS_EXPONENT = 2.5
