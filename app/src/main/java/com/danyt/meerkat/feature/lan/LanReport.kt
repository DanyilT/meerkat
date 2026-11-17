package com.danyt.meerkat.feature.lan

import android.content.res.Resources
import com.danyt.meerkat.R
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.ScanStatus
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportKind

/** True when the scan has results worth keeping: it has ended, one way or the other. */
internal val LanUiState.hasReport: Boolean
    get() = scan.status == ScanStatus.Finished || scan.status == ScanStatus.Cancelled

/**
 * Turns the current scan results into a report, with labels in the user's language.
 *
 * The report's time is when the scan started, so building it twice from the same scan gives the same report.
 */
internal fun lanReport(resources: Resources, state: LanUiState): Report {
    val scan = state.scan
    val cancelled = scan.status == ScanStatus.Cancelled
    val status = if (cancelled) R.string.report_status_cancelled else R.string.report_status_finished
    return Report(
        kind = ReportKind.Lan,
        title = scan.target.orEmpty(),
        createdAt = scan.startedAt ?: System.currentTimeMillis(),
        details = listOf(
            resources.getString(R.string.report_tool) to resources.getString(R.string.feature_lan_title),
            resources.getString(R.string.lan_target) to scan.target.orEmpty(),
            resources.getString(R.string.report_mode) to modeName(resources, scan.mode),
            resources.getString(R.string.report_status) to resources.getString(status),
            resources.getString(R.string.report_addresses_checked) to "${scan.scanned} / ${scan.total}",
            resources.getString(R.string.report_devices_found) to scan.devices.size.toString(),
        ),
        columns = listOf(
            resources.getString(R.string.device_address),
            resources.getString(R.string.device_name),
            resources.getString(R.string.device_mac),
            resources.getString(R.string.report_response_ms),
            resources.getString(R.string.device_open_ports),
        ),
        rows = scan.devices.map { device ->
            listOf(
                device.address.toString(),
                device.hostname.orEmpty(),
                device.mac.orEmpty(),
                device.latencyMillis?.toString().orEmpty(),
                device.ports.joinToString(", ") { if (it.protocol == Protocol.Udp) "${it.port}/udp" else "${it.port}" },
            )
        },
    )
}

private fun modeName(resources: Resources, mode: String?): String = when (mode) {
    ScanProfile.Quick.name -> resources.getString(R.string.lan_profile_quick)
    ScanProfile.Full.name -> resources.getString(R.string.lan_profile_full)
    ScanProfile.Custom.name -> resources.getString(R.string.lan_profile_custom)
    else -> ""
}
