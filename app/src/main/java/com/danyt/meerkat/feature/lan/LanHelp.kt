package com.danyt.meerkat.feature.lan

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.net.ScanConfig

/**
 * Explains exactly what a scan with [config] sends, port by port and packet by packet.
 *
 * @param config the settings of the selected profile, or null when the Custom profile is not valid yet.
 */
@Composable
internal fun ScanHelpDialog(profileName: String, config: ScanConfig?, onDismiss: () -> Unit) {
    InfoDialog(title = stringResource(R.string.help_scan_title, profileName), onDismiss = onDismiss) {
        InfoText(stringResource(R.string.help_scan_intro))
        if (config == null) {
            InfoText(stringResource(R.string.help_scan_invalid))
            return@InfoDialog
        }
        if (config.ping) {
            InfoHeading(stringResource(R.string.lan_options_ping))
            InfoText(stringResource(R.string.help_scan_ping))
        }
        if (config.tcpPorts.isNotEmpty()) {
            InfoHeading(stringResource(R.string.help_scan_tcp_heading))
            InfoText(stringResource(R.string.help_scan_tcp))
            InfoText(stringResource(R.string.help_scan_ports, config.tcpPorts.joinToString(", ")))
        }
        if (config.udpPorts.isNotEmpty()) {
            InfoHeading(stringResource(R.string.help_scan_udp_heading))
            InfoText(stringResource(R.string.help_scan_udp))
            InfoText(stringResource(R.string.help_scan_ports, config.udpPorts.joinToString(", ")))
        }
        InfoHeading(stringResource(R.string.help_scan_timing_heading))
        val timing = pluralStringResource(
            R.plurals.help_scan_timing,
            config.concurrency,
            config.concurrency,
            config.timeoutMillis,
        )
        InfoText(timing)
        InfoHeading(stringResource(R.string.lan_options_names))
        if (config.resolveNames) {
            NamesHelp(config.dnsServer?.toString())
        } else {
            InfoText(stringResource(R.string.help_scan_names_off))
        }
        InfoHeading(stringResource(R.string.help_scan_limits_heading))
        InfoText(stringResource(R.string.help_scan_limits))
    }
}

/** Explains where device names come from. */
@Composable
internal fun NamesHelpDialog(dnsServer: String?, onDismiss: () -> Unit) {
    InfoDialog(title = stringResource(R.string.lan_options_names), onDismiss = onDismiss) { NamesHelp(dnsServer) }
}

@Composable
private fun ColumnScope.NamesHelp(dnsServer: String?) {
    InfoText(stringResource(R.string.help_names_intro))
    InfoItem(
        label = stringResource(R.string.help_names_dns_label),
        text = if (dnsServer == null) {
            stringResource(R.string.help_names_dns_default)
        } else {
            stringResource(R.string.help_names_dns_custom, dnsServer)
        },
    )
    InfoItem(stringResource(R.string.help_names_mdns_label), stringResource(R.string.help_names_mdns))
    InfoItem(stringResource(R.string.help_names_netbios_label), stringResource(R.string.help_names_netbios))
    InfoText(stringResource(R.string.help_names_privacy))
}

/** The LAN scanner's introduction for a new user. */
@Composable
internal fun ColumnScope.LanIntro() {
    InfoText(stringResource(R.string.intro_lan_what))
    InfoHeading(stringResource(R.string.intro_modes))
    InfoItem(stringResource(R.string.lan_profile_quick), stringResource(R.string.lan_profile_quick_description))
    InfoItem(stringResource(R.string.lan_profile_full), stringResource(R.string.lan_profile_full_description))
    InfoItem(stringResource(R.string.lan_profile_custom), stringResource(R.string.lan_profile_custom_description))
    InfoHeading(stringResource(R.string.intro_features))
    InfoText(stringResource(R.string.intro_lan_features))
    InfoText(stringResource(R.string.intro_report))
    InfoText(stringResource(R.string.intro_scope))
}
