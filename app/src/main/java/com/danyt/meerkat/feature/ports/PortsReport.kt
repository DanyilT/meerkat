package com.danyt.meerkat.feature.ports

import android.content.res.Resources
import com.danyt.meerkat.R
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.ScanStatus
import com.danyt.meerkat.core.net.serviceName
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportKind

/** True when the scan has results worth keeping: it has ended, one way or the other. */
internal val PortsUiState.hasReport: Boolean
    get() = scan.status == ScanStatus.Finished || scan.status == ScanStatus.Cancelled

/**
 * Turns the current scan results into a report, with labels in the user's language.
 *
 * The report's time is when the scan started, so building it twice from the same scan gives the same report.
 */
internal fun portsReport(resources: Resources, state: PortsUiState): Report {
    val scan = state.scan
    val cancelled = scan.status == ScanStatus.Cancelled
    val status = if (cancelled) R.string.report_status_cancelled else R.string.report_status_finished
    val protocol = if (scan.protocol == Protocol.Udp) "UDP" else "TCP"
    return Report(
        kind = ReportKind.Ports,
        title = scan.host?.toString().orEmpty(),
        createdAt = scan.startedAt ?: System.currentTimeMillis(),
        details = listOf(
            resources.getString(R.string.report_tool) to resources.getString(R.string.feature_ports_title),
            resources.getString(R.string.ports_host) to scan.host?.toString().orEmpty(),
            resources.getString(R.string.report_protocol) to protocol,
            resources.getString(R.string.report_ports_set) to when (scan.preset) {
                PortPreset.Common.name -> resources.getString(R.string.ports_preset_common)
                PortPreset.WellKnown.name -> resources.getString(R.string.ports_preset_well_known)
                PortPreset.Custom.name -> resources.getString(R.string.ports_preset_custom)
                else -> ""
            },
            resources.getString(R.string.report_status) to resources.getString(status),
            resources.getString(R.string.report_ports_checked) to "${scan.scanned} / ${scan.total}",
            resources.getString(R.string.report_ports_result) to
                resources.getString(R.string.ports_summary, scan.open.size, scan.closed, scan.silent),
        ),
        columns = listOf(
            resources.getString(R.string.ports_column_port),
            resources.getString(R.string.report_protocol),
            resources.getString(R.string.ports_column_service),
            resources.getString(R.string.report_response_ms),
        ),
        rows = scan.open.map { result ->
            listOf(
                result.port.toString(),
                protocol,
                serviceName(result.port, result.protocol).orEmpty(),
                result.latencyMillis?.toString().orEmpty(),
            )
        },
    )
}
