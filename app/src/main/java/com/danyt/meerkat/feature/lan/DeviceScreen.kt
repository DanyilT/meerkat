package com.danyt.meerkat.feature.lan

import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.net.Device
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.PortResult
import com.danyt.meerkat.core.net.PortState
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.serviceName

/** One device from the scan: what is known about it, and what can be done with it. */
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onPortScan: (address: String) -> Unit,
    onOpenSsh: () -> Unit,
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val openSsh = onOpenSsh.takeIf { !viewModel.isOwnDevice }
    DeviceContent(viewModel.address, device, onBack, { onPortScan(viewModel.address) }, openSsh)
}

@Composable
private fun DeviceContent(
    address: String,
    device: Device?,
    onBack: () -> Unit,
    onPortScan: () -> Unit,
    onOpenSsh: (() -> Unit)?,
) {
    val copy = rememberCopyText()
    AppScaffold(
        title = address,
        onTitleClick = onBack,
        actions = {
            if (onOpenSsh != null) IconButton(AppIcons.Terminal, stringResource(R.string.device_open_ssh), onOpenSsh)
        },
    ) {
        if (device == null) {
            item {
                Note(stringResource(R.string.device_gone), Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp))
            }
        } else {
            item {
                GroupedSection(header = stringResource(R.string.device_details)) {
                    val ip = device.address.toString()
                    GroupedRow(stringResource(R.string.device_address), value = ip, onLongClick = { copy(ip) })
                    device.hostname?.let {
                        GroupedRow(stringResource(R.string.device_name), value = it, onLongClick = { copy(it) })
                    }
                    device.mac?.let {
                        GroupedRow(stringResource(R.string.device_mac), value = it, onLongClick = { copy(it) })
                    }
                    device.latencyMillis?.let {
                        GroupedRow(stringResource(R.string.device_latency), value = latencyText(it))
                    }
                }
            }
            item {
                GroupedSection(
                    header = stringResource(R.string.device_open_ports),
                    footer = stringResource(R.string.device_open_ports_footer),
                ) {
                    if (device.ports.isEmpty()) {
                        GroupedRow(stringResource(R.string.device_no_open_ports))
                    }
                    device.ports.forEach { port ->
                        GroupedRow(
                            title = portLabel(port.port, port.protocol),
                            value = serviceName(port.port, port.protocol),
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(stringResource(R.string.device_port_scan), onPortScan, Modifier.fillMaxWidth())
            }
        }
    }
}

/** A round-trip time for display. Anything under a millisecond is shown as "<1 ms". */
@Composable
internal fun latencyText(millis: Long): String =
    if (millis < 1) stringResource(R.string.lan_latency_under_one) else stringResource(R.string.lan_latency, millis)

/** A port number for display, marked when it is a UDP port: "53" or "53/udp". */
@Composable
internal fun portLabel(port: Int, protocol: Protocol): String =
    if (protocol == Protocol.Udp) stringResource(R.string.lan_port_udp, port) else port.toString()

@Preview
@Composable
private fun DeviceScreenPreview() {
    val device = Device(
        address = Ipv4.parse("192.168.1.20")!!,
        hostname = "nas.local",
        mac = "00:11:32:AA:BB:CC",
        latencyMillis = 3,
        ports = listOf(PortResult(22, Protocol.Tcp, PortState.Open), PortResult(5353, Protocol.Udp, PortState.Open)),
    )
    AppTheme { DeviceContent("192.168.1.20", device, {}, {}, {}) }
}
