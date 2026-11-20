package com.danyt.meerkat.core.nearby

import com.danyt.meerkat.core.text.isHiddenControl

/** The security a Wi-Fi setup tag names for its network, strongest first. */
enum class WifiTagSecurity { Wpa2, Wpa, Enterprise, Open }

/** What one NDEF record on a tag holds, decoded as far as it is useful to show. */
sealed interface NdefContent {
    /** Text, with its language code such as "en". */
    data class Text(val text: String, val language: String) : NdefContent

    /** A link or other address, such as a web page, phone number, or e-mail address. */
    data class Uri(val uri: String) : NdefContent

    /** Data with a media type. [text] is set for text types; other data is only measured. */
    data class Mime(val type: String, val text: String?, val size: Int) : NdefContent

    /** An Android app the tag asks to open. */
    data class AppLaunch(val packageName: String) : NdefContent

    /**
     * A Wi-Fi network the tag offers to join: its name and how it is secured, as "WPA2", "WPA", "Enterprise",
     * or "Open". The password the tag also holds is never read out.
     */
    data class WifiConfig(val ssid: String?, val security: WifiTagSecurity?) : NdefContent

    /** A Bluetooth device the tag offers to pair with: its address and, when given, its name. */
    data class BluetoothPairing(val address: String?, val name: String?) : NdefContent

    /** A link with a title and other details, stored as records of its own. */
    data class SmartPoster(val parts: List<NdefContent>) : NdefContent

    /** A record of a type defined by some organisation, such as "example.com:mytype". */
    data class External(val type: String, val size: Int) : NdefContent

    /** A record this app does not decode. [format] is the record's type name format, 0 to 7. */
    data class Other(val format: Int, val type: String, val size: Int) : NdefContent
}

/**
 * Decodes an NDEF message, the standard format for data on NFC tags.
 *
 * Tags can be written by anyone, so nothing is trusted: lengths are checked against the data, text is cleaned
 * and shortened, and decoding stops at the first malformed record, keeping what came before it.
 */
fun parseNdefMessage(bytes: ByteArray, depth: Int = 0): List<NdefContent> {
    val records = mutableListOf<NdefContent>()
    var offset = 0
    while (offset < bytes.size && records.size < MAX_RECORDS) {
        val header = bytes[offset].toInt() and 0xFF
        val format = header and 0x07
        val short = header and FLAG_SHORT_RECORD != 0
        val hasId = header and FLAG_ID_LENGTH != 0
        var at = offset + 1
        val typeLength = bytes.u8(at++) ?: break
        val payloadLength = if (short) {
            bytes.u8(at++) ?: break
        } else {
            val length = bytes.u32(at) ?: break
            at += 4
            length
        }
        val idLength = if (hasId) bytes.u8(at++) ?: break else 0
        val end = at.toLong() + typeLength + idLength + payloadLength
        if (payloadLength < 0 || end > bytes.size) break
        val type = bytes.copyOfRange(at, at + typeLength)
        val payloadStart = at + typeLength + idLength
        val payload = bytes.copyOfRange(payloadStart, payloadStart + payloadLength)
        records += decodeRecord(format, type, payload, depth)
        offset = end.toInt()
        if (header and FLAG_MESSAGE_END != 0) break
    }
    return records
}

private fun decodeRecord(format: Int, type: ByteArray, payload: ByteArray, depth: Int): NdefContent {
    val typeText = cleanText(type.toString(Charsets.US_ASCII))
    return when (format) {
        FORMAT_WELL_KNOWN -> when (typeText) {
            "T" -> decodeText(payload) ?: NdefContent.Other(format, typeText, payload.size)
            "U" -> decodeUri(payload)
            "Sp" -> if (depth < MAX_DEPTH) {
                NdefContent.SmartPoster(parseNdefMessage(payload, depth + 1))
            } else {
                NdefContent.Other(format, typeText, payload.size)
            }
            else -> NdefContent.Other(format, typeText, payload.size)
        }
        FORMAT_MIME -> {
            val mime = typeText.lowercase()
            when (mime) {
                MIME_WIFI -> return decodeWifi(payload)
                MIME_BLUETOOTH -> return decodeBluetooth(payload)
            }
            val readable = mime.startsWith("text/") || mime == "application/json" || mime.endsWith("+json")
            NdefContent.Mime(mime, if (readable) cleanText(payload.toString(Charsets.UTF_8)) else null, payload.size)
        }
        FORMAT_ABSOLUTE_URI -> NdefContent.Uri(typeText)
        FORMAT_EXTERNAL -> if (typeText == ANDROID_APP_TYPE) {
            NdefContent.AppLaunch(cleanText(payload.toString(Charsets.UTF_8)))
        } else {
            NdefContent.External(typeText, payload.size)
        }
        else -> NdefContent.Other(format, typeText, payload.size)
    }
}

/**
 * A Wi-Fi setup record (Wi-Fi Protected Setup attributes: a 2-byte type, a 2-byte length, the value), usually
 * inside a credential attribute. Reads the network name and the kind of security; skips the password.
 */
private fun decodeWifi(payload: ByteArray): NdefContent.WifiConfig {
    var ssid: String? = null
    var security: WifiTagSecurity? = null
    fun walk(data: ByteArray, depth: Int) {
        var at = 0
        while (at + 4 <= data.size) {
            val type = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
            val length = ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)
            val start = at + 4
            if (start + length > data.size) return
            val value = data.copyOfRange(start, start + length)
            when (type) {
                WSC_CREDENTIAL -> if (depth < 2) walk(value, depth + 1)
                WSC_SSID -> ssid = cleanText(value.toString(Charsets.UTF_8)).ifEmpty { null }
                WSC_AUTH_TYPE -> if (value.size >= 2) {
                    val flags = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                    security = when {
                        flags and 0x0020 != 0 -> WifiTagSecurity.Wpa2
                        flags and 0x0002 != 0 -> WifiTagSecurity.Wpa
                        flags and 0x0018 != 0 -> WifiTagSecurity.Enterprise
                        flags and 0x0001 != 0 -> WifiTagSecurity.Open
                        else -> null
                    }
                }
            }
            at = start + length
        }
    }
    walk(payload, 0)
    return NdefContent.WifiConfig(ssid, security)
}

/**
 * A Bluetooth pairing record: a 2-byte length, the device address (least significant byte first), then
 * extended inquiry data, from which the device name is taken.
 */
private fun decodeBluetooth(payload: ByteArray): NdefContent.BluetoothPairing {
    if (payload.size < 8) return NdefContent.BluetoothPairing(null, null)
    val address = (7 downTo 2).joinToString(":") { (payload[it].toInt() and 0xFF).toString(16).padStart(2, '0') }
        .uppercase()
    var name: String? = null
    var at = 8
    while (at < payload.size) {
        val length = payload[at].toInt() and 0xFF
        if (length == 0 || at + 1 + length > payload.size) break
        val type = payload[at + 1].toInt() and 0xFF
        if (type == EIR_SHORT_NAME || type == EIR_FULL_NAME) {
            name = cleanText(payload.copyOfRange(at + 2, at + 1 + length).toString(Charsets.UTF_8)).ifEmpty { null }
        }
        at += 1 + length
    }
    return NdefContent.BluetoothPairing(address, name)
}

/** A text record: a status byte (encoding and language length), the language code, then the text. */
private fun decodeText(payload: ByteArray): NdefContent.Text? {
    if (payload.isEmpty()) return null
    val status = payload[0].toInt() and 0xFF
    val languageLength = status and 0x3F
    if (1 + languageLength > payload.size) return null
    val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
    val language = payload.copyOfRange(1, 1 + languageLength).toString(Charsets.US_ASCII)
    val text = payload.copyOfRange(1 + languageLength, payload.size).toString(charset)
    return NdefContent.Text(cleanText(text), cleanText(language))
}

/** A URI record: one byte that stands for a common beginning such as "https://www.", then the rest. */
private fun decodeUri(payload: ByteArray): NdefContent.Uri {
    if (payload.isEmpty()) return NdefContent.Uri("")
    val prefix = UriPrefixes.getOrElse(payload[0].toInt() and 0xFF) { "" }
    return NdefContent.Uri(cleanText(prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)))
}

/** Keeps line breaks, drops other control characters and direction marks, and shortens very long text. */
private fun cleanText(text: String): String {
    val kept = text.filter { it == '\n' || !it.isHiddenControl() }.trim()
    return if (kept.length > MAX_TEXT_LENGTH) kept.take(MAX_TEXT_LENGTH) + "…" else kept
}

private fun ByteArray.u8(index: Int): Int? = getOrNull(index)?.toInt()?.and(0xFF)

private fun ByteArray.u32(index: Int): Int? {
    if (index + 4 > size) return null
    val value = (u8(index)!!.toLong() shl 24) or (u8(index + 1)!!.toLong() shl 16) or
        (u8(index + 2)!!.toLong() shl 8) or u8(index + 3)!!.toLong()
    return if (value > Int.MAX_VALUE) -1 else value.toInt()
}

private const val FLAG_MESSAGE_END = 0x40
private const val FLAG_SHORT_RECORD = 0x10
private const val FLAG_ID_LENGTH = 0x08
private const val FORMAT_WELL_KNOWN = 1
private const val FORMAT_MIME = 2
private const val FORMAT_ABSOLUTE_URI = 3
private const val FORMAT_EXTERNAL = 4
private const val ANDROID_APP_TYPE = "android.com:pkg"
private const val MIME_WIFI = "application/vnd.wfa.wsc"
private const val MIME_BLUETOOTH = "application/vnd.bluetooth.ep.oob"
private const val WSC_CREDENTIAL = 0x100E
private const val WSC_SSID = 0x1045
private const val WSC_AUTH_TYPE = 0x1003
private const val EIR_SHORT_NAME = 0x08
private const val EIR_FULL_NAME = 0x09
private const val MAX_RECORDS = 32
private const val MAX_DEPTH = 2
private const val MAX_TEXT_LENGTH = 1000

/** The URI beginnings a URI record's first byte stands for, from the NFC Forum URI record specification. */
private val UriPrefixes = listOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@",
    "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:",
    "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
    "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
    "urn:nfc:",
)
