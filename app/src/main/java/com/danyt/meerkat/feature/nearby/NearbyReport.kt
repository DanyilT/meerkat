package com.danyt.meerkat.feature.nearby

import android.content.res.Resources
import com.danyt.meerkat.R
import com.danyt.meerkat.core.nearby.CellReading
import com.danyt.meerkat.core.nearby.companyName
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportKind

/** True when [tab] has something to put in a report. */
internal fun hasReport(
    tab: NearbyTab,
    wifi: WifiTabState,
    bluetooth: BluetoothTabState,
    cell: CellTabState,
    nfc: NfcTabState,
): Boolean = when (tab) {
    NearbyTab.Wifi -> wifi.shown.isNotEmpty()
    NearbyTab.Bluetooth -> bluetooth.shown.isNotEmpty()
    NearbyTab.Cell -> (cell.reading as? CellReading.Cells)?.cells?.isNotEmpty() == true
    NearbyTab.Nfc -> nfc.tags.isNotEmpty()
}

/**
 * Turns what [tab] shows into a report, with labels in the user's language. The time is when the data arrived,
 * so building it twice from the same data gives the same report.
 */
internal fun nearbyReport(
    resources: Resources,
    tab: NearbyTab,
    wifi: WifiTabState,
    bluetooth: BluetoothTabState,
    cell: CellTabState,
    nfc: NfcTabState,
): Report = when (tab) {
    NearbyTab.Wifi -> wifiReport(resources, wifi)
    NearbyTab.Bluetooth -> bluetoothReport(resources, bluetooth)
    NearbyTab.Cell -> cellReport(resources, cell)
    NearbyTab.Nfc -> nfcReport(resources, nfc)
}

private fun Resources.tool(tab: NearbyTab) =
    getString(R.string.report_tool) to getString(R.string.nearby_report_tool, getString(tab.title))

/** The access points the list shows. With a filter set, the report says so and lists its criteria. */
private fun wifiReport(resources: Resources, state: WifiTabState): Report = with(resources) {
    val count = state.shown.size
    val total = state.networks.size
    val filter = filterLines(state.filter).map { (label, value) ->
        getString(R.string.nearby_report_filter, label) to value
    }
    Report(
        kind = ReportKind.Wifi,
        title = if (state.filter.isActive) {
            getQuantityString(R.plurals.nearby_wifi_count_filtered, total, count, total)
        } else {
            getQuantityString(R.plurals.nearby_wifi_count, count, count)
        },
        createdAt = state.updatedAt ?: System.currentTimeMillis(),
        details = listOf(tool(NearbyTab.Wifi)) + filter,
        columns = listOf(
            getString(R.string.nearby_name),
            getString(R.string.nearby_bssid),
            getString(R.string.nearby_signal_dbm),
            getString(R.string.nearby_channel),
            getString(R.string.nearby_band),
            getString(R.string.nearby_security),
            getString(R.string.nearby_standard),
        ),
        rows = state.shown.map {
            listOf(
                it.ssid.orEmpty(),
                it.bssid,
                it.dbm.toString(),
                it.channel?.toString().orEmpty(),
                band(it.band),
                securityFull(it.security),
                standard(it),
            )
        },
    )
}

private fun bluetoothReport(resources: Resources, state: BluetoothTabState): Report = with(resources) {
    val count = state.shown.size
    Report(
        kind = ReportKind.Bluetooth,
        title = getQuantityString(R.plurals.nearby_bt_count, count, count),
        createdAt = state.startedAt ?: System.currentTimeMillis(),
        details = listOf(tool(NearbyTab.Bluetooth)),
        columns = listOf(
            getString(R.string.nearby_name),
            getString(R.string.nearby_address),
            getString(R.string.nearby_signal_dbm),
            getString(R.string.nearby_bt_type),
            getString(R.string.nearby_bt_maker),
            getString(R.string.nearby_bt_device_class),
            getString(R.string.nearby_bt_services),
        ),
        rows = state.shown.map { device ->
            listOf(
                device.name.orEmpty(),
                device.address,
                device.dbm.toString(),
                bluetoothKind(device.kind),
                device.manufacturers.joinToString(", ") { companyName(it) },
                deviceKind(device).orEmpty(),
                device.services.joinToString(", ") { serviceName(it) },
            )
        },
    )
}

private fun cellReport(resources: Resources, state: CellTabState): Report = with(resources) {
    val reading = state.reading as? CellReading.Cells
    val cells = reading?.cells.orEmpty()
    Report(
        kind = ReportKind.Cell,
        title = reading?.operatorName ?: getString(R.string.nearby_tab_cell),
        createdAt = state.updatedAt ?: System.currentTimeMillis(),
        details = listOfNotNull(
            tool(NearbyTab.Cell),
            reading?.operatorName?.let { getString(R.string.nearby_cell_operator) to it },
        ),
        columns = listOf(
            getString(R.string.nearby_cell_role),
            getString(R.string.nearby_cell_tech),
            getString(R.string.nearby_cell_operator),
            getString(R.string.nearby_cell_plmn),
            getString(R.string.nearby_cell_area),
            getString(R.string.nearby_cell_id),
            getString(R.string.nearby_cell_code),
            getString(R.string.nearby_channel),
            getString(R.string.nearby_signal_dbm),
        ),
        rows = cells.map { cell ->
            val role = if (cell.registered) R.string.nearby_cell_serving_one else R.string.nearby_cell_neighbour_one
            listOf(
                getString(role),
                cellTech(cell.tech),
                cell.operator.orEmpty(),
                cell.plmn().orEmpty(),
                cell.area?.toString().orEmpty(),
                cell.cellId?.toString().orEmpty(),
                cell.code?.toString().orEmpty(),
                cell.channel?.toString().orEmpty(),
                cell.dbm?.toString().orEmpty(),
            )
        },
    )
}

private fun nfcReport(resources: Resources, state: NfcTabState): Report = with(resources) {
    val count = state.tags.size
    Report(
        kind = ReportKind.Nfc,
        title = getQuantityString(R.plurals.nearby_nfc_count, count, count),
        // The first tag read; the list is newest first.
        createdAt = state.tags.lastOrNull()?.readAt ?: System.currentTimeMillis(),
        details = listOf(tool(NearbyTab.Nfc)),
        columns = listOf(
            getString(R.string.nearby_nfc_read),
            getString(R.string.nearby_nfc_id),
            getString(R.string.nearby_nfc_techs),
            getString(R.string.nearby_nfc_details),
            getString(R.string.nearby_nfc_ndef),
        ),
        rows = state.tags.map { tag ->
            listOf(
                clockTime(tag.readAt),
                tag.id,
                tag.techs.joinToString(", "),
                tag.facts.joinToString("; ") { (fact, value) -> "${nfcFact(fact)}: $value" },
                tag.ndef?.records.orEmpty().joinToString("\n") { record ->
                    ndefRecord(record).let { "${it.first}: ${it.second}" }
                },
            )
        },
    )
}
