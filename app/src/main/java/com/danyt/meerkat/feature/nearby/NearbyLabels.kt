package com.danyt.meerkat.feature.nearby

import android.bluetooth.BluetoothClass
import android.content.res.Resources
import com.danyt.meerkat.R
import com.danyt.meerkat.core.nearby.AppearanceCategory
import com.danyt.meerkat.core.nearby.AppleAudioModels
import com.danyt.meerkat.core.nearby.AppleInfo
import com.danyt.meerkat.core.nearby.AppleMessage
import com.danyt.meerkat.core.nearby.BluetoothKind
import com.danyt.meerkat.core.nearby.BluetoothSighting
import com.danyt.meerkat.core.nearby.CellTech
import com.danyt.meerkat.core.nearby.CellTower
import com.danyt.meerkat.core.nearby.FrameProtection
import com.danyt.meerkat.core.nearby.NdefContent
import com.danyt.meerkat.core.nearby.NfcFact
import com.danyt.meerkat.core.nearby.NfcNote
import com.danyt.meerkat.core.nearby.NfcTag
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.WifiSecurity
import com.danyt.meerkat.core.nearby.WifiStandard
import com.danyt.meerkat.core.nearby.WifiTagSecurity
import com.danyt.meerkat.core.nearby.appearanceCategory
import com.danyt.meerkat.core.nearby.companyName
import com.danyt.meerkat.core.nearby.shortUuidText
import kotlin.math.roundToInt

// Text for what the radios report, shared by the screen and the reports.

internal fun Resources.band(band: WifiBand?): String = when (band) {
    WifiBand.Band2Ghz -> getString(R.string.nearby_band_2)
    WifiBand.Band5Ghz -> getString(R.string.nearby_band_5)
    WifiBand.Band6Ghz -> getString(R.string.nearby_band_6)
    WifiBand.Band60Ghz -> getString(R.string.nearby_band_60)
    null -> ""
}

/**
 * Security for the list: every protocol the network accepts, oldest first, such as "WPA/WPA2" or
 * "WPA2/WPA3"; Enterprise ones say so.
 */
internal fun Resources.securityShort(list: List<WifiSecurity>): String = list.reversed().map { security ->
    when {
        security.generation != null -> {
            val name = "WPA" + if (security.generation == 1) "" else security.generation
            if (security.enterprise) getString(R.string.nearby_security_enterprise, name) else name
        }
        security == WifiSecurity.Owe -> getString(R.string.nearby_security_owe)
        security == WifiSecurity.Wep -> "WEP"
        else -> getString(R.string.nearby_security_open)
    }
}.distinct().joinToString("/")

/** Security in full, such as "WPA3 Personal, WPA2 Personal" or "WPA2 Enterprise". */
internal fun Resources.securityFull(list: List<WifiSecurity>, separator: String = ", "): String =
    list.joinToString(separator) {
        when (it) {
            WifiSecurity.Wpa3Enterprise -> getString(R.string.nearby_security_wpa3_enterprise)
            WifiSecurity.Wpa3 -> getString(R.string.nearby_security_personal, "WPA3")
            WifiSecurity.Owe -> getString(R.string.nearby_security_owe)
            WifiSecurity.Wpa2Enterprise -> getString(R.string.nearby_security_enterprise, "WPA2")
            WifiSecurity.Wpa2 -> getString(R.string.nearby_security_personal, "WPA2")
            WifiSecurity.WpaEnterprise -> getString(R.string.nearby_security_enterprise, "WPA")
            WifiSecurity.Wpa -> getString(R.string.nearby_security_personal, "WPA")
            WifiSecurity.Wep -> "WEP"
            WifiSecurity.Open -> getString(R.string.nearby_security_open)
        }
    }

internal fun Resources.frameProtection(value: FrameProtection): String = getString(
    when (value) {
        FrameProtection.Required -> R.string.nearby_wifi_pmf_required
        FrameProtection.Optional -> R.string.nearby_wifi_pmf_optional
        FrameProtection.Off -> R.string.nearby_wifi_pmf_off
    },
)

/** The Wi-Fi generation by its marketing name. Wi-Fi 6 on the 6 GHz band is called Wi-Fi 6E. */
internal fun Resources.standard(network: WifiNetwork): String = when (network.standard) {
    WifiStandard.Legacy -> getString(R.string.nearby_standard_legacy)
    WifiStandard.N -> "Wi-Fi 4"
    WifiStandard.Ac -> "Wi-Fi 5"
    WifiStandard.Ax -> if (network.band == WifiBand.Band6Ghz) "Wi-Fi 6E" else "Wi-Fi 6"
    WifiStandard.Ad -> "WiGig"
    WifiStandard.Be -> "Wi-Fi 7"
    null -> ""
}

/** The network name, or a placeholder for a network that hides it. */
internal fun Resources.networkName(network: WifiNetwork): String =
    network.ssid ?: getString(R.string.nearby_wifi_hidden)

/** One line about a network: channel, band, security, and generation. */
internal fun Resources.networkSummary(network: WifiNetwork): String = listOfNotNull(
    network.channel?.let { getString(R.string.nearby_channel_short, it) },
    band(network.band).ifEmpty { null },
    securityShort(network.security),
    standard(network).ifEmpty { null },
).joinToString(" · ")

internal fun Resources.bluetoothKind(kind: BluetoothKind): String = getString(
    when (kind) {
        BluetoothKind.Classic -> R.string.nearby_bt_classic
        BluetoothKind.LowEnergy -> R.string.nearby_bt_le
        BluetoothKind.Dual -> R.string.nearby_bt_dual
    },
)

/** What kind of device it says it is, or null when it did not say. */
internal fun Resources.bluetoothClass(major: Int?): String? = when (major) {
    BluetoothClass.Device.Major.COMPUTER -> getString(R.string.nearby_bt_class_computer)
    BluetoothClass.Device.Major.PHONE -> getString(R.string.nearby_bt_class_phone)
    BluetoothClass.Device.Major.NETWORKING -> getString(R.string.nearby_bt_class_networking)
    BluetoothClass.Device.Major.AUDIO_VIDEO -> getString(R.string.nearby_bt_class_audio)
    BluetoothClass.Device.Major.PERIPHERAL -> getString(R.string.nearby_bt_class_peripheral)
    BluetoothClass.Device.Major.IMAGING -> getString(R.string.nearby_bt_class_imaging)
    BluetoothClass.Device.Major.WEARABLE -> getString(R.string.nearby_bt_class_wearable)
    BluetoothClass.Device.Major.TOY -> getString(R.string.nearby_bt_class_toy)
    BluetoothClass.Device.Major.HEALTH -> getString(R.string.nearby_bt_class_health)
    else -> null
}

/**
 * What kind of device it is, from the best source available: the appearance it announces, then what Apple's
 * messages imply, then the classic device class. Null when nothing says.
 */
internal fun Resources.deviceKind(device: BluetoothSighting): String? {
    val byAppearance = device.appearance?.let { appearanceLabel(appearanceCategory(it)) }
    val apple = device.apple
    val byApple = when {
        apple == null -> null
        apple.model != null -> AppleAudioModels[apple.model] ?: getString(R.string.nearby_bt_kind_earbuds)
        AppleMessage.PROXIMITY_PAIRING in apple.messages -> getString(R.string.nearby_bt_kind_earbuds)
        AppleMessage.FIND_MY in apple.messages -> getString(R.string.nearby_bt_kind_find_my)
        AppleMessage.AIRPLAY_TARGET in apple.messages -> getString(R.string.nearby_bt_kind_airplay)
        AppleMessage.NEARBY_INFO in apple.messages -> getString(R.string.nearby_bt_kind_apple)
        else -> null
    }
    return byAppearance ?: byApple ?: bluetoothClass(device.majorClass)
}

private fun Resources.appearanceLabel(category: Int): String? = when (category) {
    AppearanceCategory.PHONE -> R.string.nearby_bt_class_phone
    AppearanceCategory.COMPUTER -> R.string.nearby_bt_class_computer
    AppearanceCategory.WATCH -> R.string.nearby_bt_kind_watch
    AppearanceCategory.CLOCK -> R.string.nearby_bt_kind_clock
    AppearanceCategory.DISPLAY, AppearanceCategory.AV_EQUIPMENT -> R.string.nearby_bt_kind_display
    AppearanceCategory.REMOTE -> R.string.nearby_bt_kind_remote
    AppearanceCategory.GLASSES -> R.string.nearby_bt_kind_glasses
    AppearanceCategory.TAG, AppearanceCategory.KEYRING -> R.string.nearby_bt_kind_tag
    AppearanceCategory.MEDIA_PLAYER, AppearanceCategory.AUDIO_SOURCE -> R.string.nearby_bt_kind_media
    AppearanceCategory.THERMOMETER, AppearanceCategory.SENSOR -> R.string.nearby_bt_kind_sensor
    AppearanceCategory.HEART_RATE -> R.string.nearby_bt_class_health
    AppearanceCategory.INPUT -> R.string.nearby_bt_class_peripheral
    AppearanceCategory.LIGHT, AppearanceCategory.LIGHT_SOURCE -> R.string.nearby_bt_kind_light
    AppearanceCategory.SPEAKER -> R.string.nearby_bt_kind_speaker
    AppearanceCategory.HEADPHONES -> R.string.nearby_bt_kind_earbuds
    AppearanceCategory.HEARING_AID -> R.string.nearby_bt_kind_hearing_aid
    AppearanceCategory.GAMING -> R.string.nearby_bt_kind_gaming
    else -> null
}?.let(::getString)

/** What an Apple device's messages are for, such as "AirDrop" or "Find My". */
internal fun Resources.appleMessages(apple: AppleInfo): String = apple.messages.mapNotNull { type ->
    when (type) {
        AppleMessage.AIRPRINT -> "AirPrint"
        AppleMessage.AIRDROP -> "AirDrop"
        AppleMessage.HOMEKIT -> "HomeKit"
        AppleMessage.PROXIMITY_PAIRING -> getString(R.string.nearby_apple_pairing)
        AppleMessage.SIRI -> "Siri"
        AppleMessage.AIRPLAY_TARGET, AppleMessage.AIRPLAY_SOURCE -> "AirPlay"
        AppleMessage.MAGIC_SWITCH -> getString(R.string.nearby_apple_watch_switch)
        AppleMessage.HANDOFF -> "Handoff"
        AppleMessage.HOTSPOT_TARGET, AppleMessage.HOTSPOT_SOURCE -> getString(R.string.nearby_apple_hotspot)
        AppleMessage.NEARBY_ACTION -> getString(R.string.nearby_apple_nearby_action)
        AppleMessage.NEARBY_INFO -> getString(R.string.nearby_apple_nearby_info)
        AppleMessage.FIND_MY -> "Find My"
        else -> null
    }
}.distinct().joinToString(", ")

/** Earbud and case levels, such as "Left 80 %, right 70 %, case 50 %", leaving out unknown ones. */
internal fun Resources.earbudBattery(apple: AppleInfo): String? = listOfNotNull(
    apple.left?.let { getString(R.string.nearby_battery_left, it) },
    apple.right?.let { getString(R.string.nearby_battery_right, it) },
    apple.case?.let { getString(R.string.nearby_battery_case, it) },
).joinToString(", ").ifEmpty { null }

internal fun Resources.deviceName(device: BluetoothSighting): String =
    device.name ?: getString(R.string.nearby_bt_unnamed)

/** One line about a device: how it was heard, its maker, and its kind. */
internal fun Resources.deviceSummary(device: BluetoothSighting): String = listOfNotNull(
    deviceKind(device),
    device.manufacturers.firstOrNull()?.let(::companyName),
    bluetoothKind(device.kind),
).joinToString(" · ")

/** A service by its name when it is a well-known one, otherwise by number. */
internal fun Resources.serviceName(uuid: Int): String {
    val name = when (uuid) {
        0x1800 -> R.string.nearby_service_access
        0x180A -> R.string.nearby_service_device_info
        0x180D -> R.string.nearby_service_heart_rate
        0x180F -> R.string.nearby_service_battery
        0x1812 -> R.string.nearby_service_hid
        0xFD6F -> R.string.nearby_service_exposure
        0xFE2C -> R.string.nearby_service_fast_pair
        else -> null
    }
    return name?.let(::getString) ?: shortUuidText(uuid)
}

internal fun Resources.cellTech(tech: CellTech): String = when (tech) {
    CellTech.Gsm -> "GSM (2G)"
    CellTech.Cdma -> "CDMA"
    CellTech.Wcdma -> "WCDMA (3G)"
    CellTech.TdScdma -> "TD-SCDMA (3G)"
    CellTech.Lte -> "LTE (4G)"
    CellTech.Nr -> getString(R.string.nearby_cell_nr)
}

/** The names each technology gives its area, cell, code, and channel numbers. */
internal data class CellLabels(val area: String, val cell: String, val code: String?, val channel: String?)

internal fun cellLabels(tech: CellTech): CellLabels = when (tech) {
    CellTech.Gsm -> CellLabels("LAC", "CID", "BSIC", "ARFCN")
    CellTech.Wcdma -> CellLabels("LAC", "CID", "PSC", "UARFCN")
    CellTech.TdScdma -> CellLabels("LAC", "CID", "CPID", "UARFCN")
    CellTech.Cdma -> CellLabels("NID", "BID", null, null)
    CellTech.Lte -> CellLabels("TAC", "ECI", "PCI", "EARFCN")
    CellTech.Nr -> CellLabels("TAC", "NCI", "PCI", "NR-ARFCN")
}

/** Mobile country and network code together, such as "262-01". */
internal fun CellTower.plmn(): String? = if (mcc != null && mnc != null) "$mcc-$mnc" else null

/** One line about a cell: its network code, area, and identity. */
internal fun cellSummary(cell: CellTower): String {
    val labels = cellLabels(cell.tech)
    return listOfNotNull(
        cell.plmn(),
        cell.area?.let { "${labels.area} $it" },
        cell.cellId?.let { "${labels.cell} $it" },
        if (cell.cellId == null) cell.code?.let { "${labels.code} $it" } else null,
    ).joinToString(" · ")
}

internal fun Resources.nfcFact(fact: NfcFact): String = when (fact) {
    NfcFact.Chip -> getString(R.string.nearby_nfc_chip)
    NfcFact.ChipMaker -> getString(R.string.nearby_nfc_chip_maker)
    NfcFact.Applications -> getString(R.string.nearby_nfc_apps)
    NfcFact.PaymentNetwork -> getString(R.string.nearby_nfc_payment_network)
    NfcFact.CardName -> getString(R.string.nearby_nfc_card_name)
    NfcFact.Atqa -> "ATQA"
    NfcFact.Sak -> "SAK"
    NfcFact.MifareClassic -> "MIFARE Classic"
    NfcFact.MifareUltralight -> "MIFARE Ultralight"
    NfcFact.MemoryBytes -> getString(R.string.nearby_nfc_memory)
    NfcFact.Sectors -> getString(R.string.nearby_nfc_sectors)
    NfcFact.HistoricalBytes -> getString(R.string.nearby_nfc_historical)
    NfcFact.HigherLayer -> getString(R.string.nearby_nfc_higher_layer)
    NfcFact.ApplicationData -> getString(R.string.nearby_nfc_application_data)
    NfcFact.ProtocolInfo -> getString(R.string.nearby_nfc_protocol_info)
    NfcFact.Manufacturer -> getString(R.string.nearby_nfc_manufacturer)
    NfcFact.SystemCode -> getString(R.string.nearby_nfc_system_code)
    NfcFact.Dsfid -> "DSFID"
    NfcFact.MaxTransfer -> getString(R.string.nearby_nfc_max_transfer)
}

/** A record as a label and its content, for a row or a report cell. */
internal fun Resources.ndefRecord(content: NdefContent): Pair<String, String> = when (content) {
    is NdefContent.Text -> getString(R.string.nearby_ndef_text, content.language) to content.text
    is NdefContent.Uri -> getString(R.string.nearby_ndef_uri) to content.uri
    is NdefContent.Mime ->
        content.type to (content.text ?: bytes(content.size))
    is NdefContent.AppLaunch -> getString(R.string.nearby_ndef_app) to content.packageName
    is NdefContent.WifiConfig -> getString(R.string.nearby_ndef_wifi) to
        listOfNotNull(content.ssid, content.security?.let(::tagSecurity)).joinToString(" · ")
    is NdefContent.BluetoothPairing -> getString(R.string.nearby_ndef_bluetooth) to
        listOfNotNull(content.name, content.address).joinToString(" · ")
    is NdefContent.SmartPoster -> getString(R.string.nearby_ndef_poster) to
        content.parts.joinToString("\n") { part -> ndefRecord(part).let { "${it.first}: ${it.second}" } }
    is NdefContent.External -> content.type to bytes(content.size)
    is NdefContent.Other -> content.type.ifEmpty { getString(R.string.nearby_ndef_other) } to
        bytes(content.size)
}

/**
 * What holding the tag to a phone does, in words, from its first record that asks for something: open a link,
 * call, write, join a Wi-Fi network, pair, and so on. Null when the tag holds only data.
 */
internal fun Resources.ndefAction(records: List<NdefContent>): String? = records.firstNotNullOfOrNull { record ->
    when (record) {
        is NdefContent.Uri -> uriAction(record.uri)
        is NdefContent.SmartPoster -> ndefAction(record.parts)
        is NdefContent.AppLaunch -> getString(R.string.nearby_do_app, record.packageName)
        is NdefContent.WifiConfig -> record.ssid?.let { getString(R.string.nearby_do_wifi, it) }
            ?: getString(R.string.nearby_do_wifi_unnamed)
        is NdefContent.BluetoothPairing -> (record.name ?: record.address)
            ?.let { getString(R.string.nearby_do_bluetooth, it) }
            ?: getString(R.string.nearby_do_bluetooth_unnamed)
        is NdefContent.Mime -> when {
            record.type == "text/vcard" || record.type == "text/x-vcard" -> {
                // The contact's full name, from the vCard's FN line.
                val name = record.text?.lineSequence()?.firstOrNull { it.startsWith("FN") }?.substringAfter(':')?.trim()
                name?.ifEmpty { null }?.let { getString(R.string.nearby_do_contact, it) }
                    ?: getString(R.string.nearby_do_contact_unnamed)
            }
            else -> null
        }
        is NdefContent.Text -> getString(R.string.nearby_do_text)
        else -> null
    }
}

/** A Wi-Fi setup tag's security, in the words the Wi-Fi list uses. */
private fun Resources.tagSecurity(security: WifiTagSecurity): String = when (security) {
    WifiTagSecurity.Wpa2 -> "WPA2"
    WifiTagSecurity.Wpa -> "WPA"
    WifiTagSecurity.Enterprise -> getString(R.string.nearby_filter_enterprise)
    WifiTagSecurity.Open -> getString(R.string.nearby_security_open)
}

/**
 * What a tag or card is for, always in words: the action its NDEF data asks for, or else what kind of card it is
 * (a bank card, a smart card for transit or access, a locked access card, a label) or that it holds nothing.
 */
internal fun Resources.tagPurpose(tag: NfcTag): String {
    tag.ndef?.records?.let(::ndefAction)?.let { return it }
    val network = tag.facts.firstOrNull { it.first == NfcFact.PaymentNetwork }?.second
    return when {
        NfcNote.PaymentCard in tag.notes -> network?.let { getString(R.string.nearby_do_pay, it) }
            ?: getString(R.string.nearby_do_pay_unknown)
        NfcNote.LockedByKeys in tag.notes -> getString(R.string.nearby_do_locked_card)
        NfcNote.AppsLocked in tag.notes || tag.facts.any { it.first == NfcFact.Applications } ->
            getString(R.string.nearby_do_smart_card)
        tag.ndef != null && tag.ndef.records.isEmpty() -> getString(R.string.nearby_do_empty)
        tag.ndef != null -> getString(R.string.nearby_do_data)
        "IsoDep" in tag.techs -> getString(R.string.nearby_do_smart_card)
        "NfcV" in tag.techs -> getString(R.string.nearby_do_label)
        else -> getString(R.string.nearby_do_nothing)
    }
}

private fun Resources.uriAction(uri: String): String {
    val scheme = uri.substringBefore(':').lowercase()
    val rest = uri.substringAfter(':').removePrefix("//")
    return when (scheme) {
        "http", "https" -> getString(R.string.nearby_do_web, rest.substringBefore('/'))
        "tel" -> getString(R.string.nearby_do_call, rest)
        "mailto" -> getString(R.string.nearby_do_mail, rest.substringBefore('?'))
        "sms", "smsto" -> getString(R.string.nearby_do_sms, rest.substringBefore('?'))
        "geo" -> getString(R.string.nearby_do_map)
        "market" -> getString(R.string.nearby_do_store)
        else -> getString(R.string.nearby_do_open, uri)
    }
}

/** A size such as "12 bytes". */
internal fun Resources.bytes(count: Int): String = getQuantityString(R.plurals.nearby_ndef_bytes, count, count)

/** An estimated distance, marked as rough with "~": one decimal under a metre, whole metres or km above. */
internal fun Resources.approxDistance(meters: Float): String = getString(
    R.string.nearby_approx,
    if (meters < 1f) getString(R.string.nearby_meters_decimal, meters) else distance(meters.roundToInt()),
)

/** A distance such as "780 m" or "1.4 km". */
internal fun Resources.distance(meters: Int): String = if (meters < 1000) {
    getString(R.string.nearby_meters, meters)
} else {
    getString(R.string.nearby_kilometers, meters / 1000f)
}
