package com.danyt.meerkat.feature.ports

import com.danyt.meerkat.core.design.InfoTextWithIcon
import com.danyt.meerkat.core.design.FieldButton
import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.help.PortsHelpDialog
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.InfoHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.DataColumn
import com.danyt.meerkat.core.design.DataTable
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.ProgressBar
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.design.TextField
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.PortResult
import com.danyt.meerkat.core.net.PortScanState
import com.danyt.meerkat.core.net.PortState
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.ScanStatus
import com.danyt.meerkat.core.net.formatAddressInput
import com.danyt.meerkat.core.net.serviceName
import com.danyt.meerkat.core.permission.openAppSettings
import com.danyt.meerkat.core.permission.rememberLocalNetworkRequest
import com.danyt.meerkat.core.privacy.MacPrivacyDialog
import com.danyt.meerkat.core.reports.ToolReportSheet

/** The port scanner: pick a host and a set of ports, run the scan, and list what is open. */
@Composable
fun PortsScreen(
    viewModel: PortsViewModel,
    intros: Intros,
    onBack: () -> Unit,
    onOpenScope: () -> Unit,
    onOpenReport: (id: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var accessDenied by rememberSaveable { mutableStateOf(false) }
    var confirmScope by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showPortsHelp by rememberSaveable { mutableStateOf(false) }
    val intro = rememberIntro(intros, IntroKeys.PORTS)
    val resources = LocalResources.current
    var showMacTip by rememberSaveable { mutableStateOf(false) }
    val requestAccess = rememberLocalNetworkRequest { granted ->
        if (granted) viewModel.startScan() else accessDenied = true
    }

    // Only local addresses are behind the permission. Hosts on the internet need none.
    val proceed = { if (viewModel.hostIsLocal()) requestAccess() else viewModel.startScan() }
    // The order before a scan: the MAC address tip (local hosts only), then the scope guard, then the permission.
    val afterTip = { if (viewModel.hostIsOutsideScope()) confirmScope = true else proceed() }


    PortsContent(
        state = state,
        actions = PortsActions(
            onBack = onBack,
            onHost = viewModel::setHost,
            onPreset = viewModel::setPreset,
            onCustomPorts = viewModel::setCustomPorts,
            onProtocol = viewModel::setProtocol,
            onScan = {
                focusManager.clearFocus()
                if (viewModel.validate()) {
                    if (viewModel.shouldShowMacTip()) showMacTip = true else afterTip()
                }
            },
            onCancel = viewModel::cancelScan,
            onUseOwnAddress = viewModel::useOwnAddress,
            onReport = { showReport = true },
            onOpenScope = onOpenScope,
            onInfo = intro.open.takeIf { intro.enabled },
            onPortsHelp = { showPortsHelp = true },
        ),
    )

    IntroDialog(intro, stringResource(R.string.feature_ports_title)) {
        InfoTextWithIcon(stringResource(R.string.intro_ports_what), AppIcons.Pin)
        InfoHeading(stringResource(R.string.intro_modes))
        InfoItem(stringResource(R.string.ports_preset_common), stringResource(R.string.intro_ports_common))
        InfoItem(stringResource(R.string.ports_preset_well_known), stringResource(R.string.intro_ports_well_known))
        InfoItem(stringResource(R.string.ports_preset_custom), stringResource(R.string.intro_ports_custom))
        InfoHeading(stringResource(R.string.intro_features))
        InfoText(stringResource(R.string.intro_ports_features))
        InfoText(stringResource(R.string.intro_report))
        InfoText(stringResource(R.string.intro_scope))
    }
    if (showPortsHelp) PortsHelpDialog(state.protocol, onDismiss = { showPortsHelp = false })

    ToolReportSheet(
        visible = showReport,
        report = { portsReport(resources, state) },
        hashes = state.reportHashes,
        onDismiss = { showReport = false },
        onExported = viewModel::markExported,
        onSave = viewModel::saveReport,
        onOpenReport = onOpenReport,
    )

    if (showMacTip) {
        // Closing the tip carries on with the scan. Leaving for Wi-Fi settings does not.
        val close = { forever: Boolean, scan: Boolean ->
            showMacTip = false
            viewModel.closeMacTip(forever)
            if (scan) afterTip()
        }
        MacPrivacyDialog(
            onDismiss = { close(false, true) },
            onDontShowAgain = { close(true, true) },
            onOpenedSettings = { close(false, false) },
        )
    }

    if (confirmScope) {
        ConfirmDialog(
            title = stringResource(R.string.scope_warning_title),
            message = stringResource(R.string.scope_warning_message, state.host.trim()),
            confirmLabel = stringResource(R.string.scope_warning_confirm),
            destructive = true,
            onConfirm = {
                confirmScope = false
                proceed()
            },
            onDismiss = { confirmScope = false },
        )
    }
    if (accessDenied) {
        ConfirmDialog(
            title = stringResource(R.string.lan_access_title),
            message = stringResource(R.string.lan_access_message),
            confirmLabel = stringResource(R.string.lan_access_open_settings),
            onConfirm = {
                accessDenied = false
                openAppSettings(context)
            },
            onDismiss = { accessDenied = false },
        )
    }
}

private class PortsActions(
    val onBack: () -> Unit = {},
    val onHost: (String) -> Unit = {},
    val onPreset: (PortPreset) -> Unit = {},
    val onCustomPorts: (String) -> Unit = {},
    val onProtocol: (Protocol) -> Unit = {},
    val onScan: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onUseOwnAddress: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onOpenScope: () -> Unit = {},
    val onInfo: (() -> Unit)? = null,
    val onPortsHelp: () -> Unit = {},
)

@Composable
private fun PortsContent(state: PortsUiState, actions: PortsActions) {
    val scan = state.scan
    val running = scan.status == ScanStatus.Running
    AppScaffold(
        title = stringResource(R.string.feature_ports_title),
        onTitleClick = actions.onBack,
        actions = {
            actions.onInfo?.let { IconButton(AppIcons.Info, stringResource(R.string.intro_button), it) }
            IconButton(AppIcons.Shield, stringResource(R.string.scope_title), actions.onOpenScope)
            IconButton(
                icon = AppIcons.Reports,
                contentDescription = stringResource(R.string.report_button),
                onClick = actions.onReport,
                enabled = state.hasReport,
            )
        },
    ) {
        item(key = "setup") { PortsSetup(state, running, actions) }
        if (scan.status != ScanStatus.Idle) {
            item(key = "status") { PortsProgress(scan, running) }
            if (scan.open.isNotEmpty()) {
                item(key = "results") {
                    val unknown = stringResource(R.string.ports_service_unknown)
                    DataTable(
                        columns = listOf(
                            DataColumn(stringResource(R.string.ports_column_port)),
                            DataColumn(stringResource(R.string.ports_column_service), weight = 2f),
                            DataColumn(stringResource(R.string.ports_column_time), alignEnd = true),
                        ),
                        rows = scan.open.map { result ->
                            listOf(
                                result.port.toString(),
                                serviceName(result.port, result.protocol) ?: unknown,
                                when (val millis = result.latencyMillis) {
                                    null -> ""
                                    0L -> stringResource(R.string.lan_latency_under_one)
                                    else -> stringResource(R.string.lan_latency, millis)
                                },
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortsSetup(state: PortsUiState, running: Boolean, actions: PortsActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = state.host,
            onValueChange = actions.onHost,
            label = stringResource(R.string.ports_host),
            placeholder = stringResource(R.string.ports_host_placeholder),
            error = stringResource(R.string.ports_host_error).takeIf { state.hostError },
            enabled = !running,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
            transformInput = { old, new -> formatAddressInput(old, new, allowRange = false) },
            clearable = true,
            trailing = if (running) {
                null
            } else {
                {
                    FieldButton(
                        icon = AppIcons.Pin,
                        contentDescription = stringResource(R.string.ports_use_own),
                        onClick = actions.onUseOwnAddress,
                    )
                }
            },
        )
        SegmentedControl(
            options = listOf(stringResource(R.string.ports_tcp), stringResource(R.string.ports_udp)),
            selectedIndex = state.protocol.ordinal,
            onSelected = { if (!running) actions.onProtocol(Protocol.entries[it]) },
        )
        SegmentedControl(
            options = PortPreset.entries.map { stringResource(it.label) },
            selectedIndex = state.preset.ordinal,
            onSelected = { if (!running) actions.onPreset(PortPreset.entries[it]) },
        )
        if (state.preset == PortPreset.Custom) {
            TextField(
                value = state.customPorts,
                onValueChange = actions.onCustomPorts,
                label = stringResource(R.string.ports_custom),
                placeholder = stringResource(R.string.lan_options_ports_placeholder),
                error = stringResource(R.string.ports_custom_error).takeIf { state.customPortsError },
                enabled = !running,
                onHelp = actions.onPortsHelp,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
        }
        if (state.protocol == Protocol.Udp) {
            Text(
                text = stringResource(R.string.ports_udp_hint),
                style = AppTheme.typography.subhead,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (running) {
            SecondaryButton(stringResource(R.string.lan_cancel_scan), actions.onCancel, Modifier.fillMaxWidth())
        } else {
            PrimaryButton(stringResource(R.string.lan_scan), actions.onScan, Modifier.fillMaxWidth())
        }
    }
}

/**
 * What the results below belong to: the host, protocol, and port set of the scan that produced them, which
 * may differ from what is in the form now, and how the scan went.
 */
@Composable
private fun PortsProgress(scan: PortScanState, running: Boolean) {
    val colors = AppTheme.colors
    val preset = PortPreset.entries.firstOrNull { it.name == scan.preset }?.let { stringResource(it.label) }
    val protocol = stringResource(if (scan.protocol == Protocol.Udp) R.string.ports_udp else R.string.ports_tcp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = listOfNotNull(scan.host?.toString(), protocol, preset).joinToString(" \u00b7 "),
            style = AppTheme.typography.headline,
            color = colors.label,
        )
        if (running) ProgressBar(if (scan.total == 0) 0f else scan.scanned.toFloat() / scan.total)
        val status = when (scan.status) {
            ScanStatus.Cancelled -> R.plurals.ports_status_cancelled
            ScanStatus.Finished -> R.plurals.ports_status_finished
            else -> R.plurals.ports_status_running
        }
        Text(
            text = pluralStringResource(status, scan.total, scan.scanned, scan.total) + " " +
                stringResource(R.string.ports_summary, scan.open.size, scan.closed, scan.silent),
            style = AppTheme.typography.subhead,
            color = colors.secondaryLabel,
        )
    }
}

/** The name shown for each set of ports. */
internal val PortPreset.label: Int
    get() = when (this) {
        PortPreset.Common -> R.string.ports_preset_common
        PortPreset.WellKnown -> R.string.ports_preset_well_known
        PortPreset.Custom -> R.string.ports_preset_custom
    }

@Preview
@Composable
private fun PortsScreenPreview() {
    val open = listOf(PortResult(22, Protocol.Tcp, PortState.Open, 3), PortResult(443, Protocol.Tcp, PortState.Open, 4))
    val host = Ipv4.parse("192.168.1.20")
    AppTheme {
        PortsContent(
            state = PortsUiState(
                host = "192.168.1.20",
                scan = PortScanState(host, Protocol.Tcp, ScanStatus.Finished, 100, open, closed = 98),
            ),
            actions = PortsActions(),
        )
    }
}
