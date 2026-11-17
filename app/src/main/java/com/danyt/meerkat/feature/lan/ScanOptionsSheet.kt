package com.danyt.meerkat.feature.lan

import com.danyt.meerkat.core.design.MenuItem
import com.danyt.meerkat.core.design.Menu
import com.danyt.meerkat.core.design.FieldButton
import com.danyt.meerkat.core.design.AppIcons
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import com.danyt.meerkat.core.net.formatAddressInput
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.help.PortsHelpDialog
import com.danyt.meerkat.core.design.HelpButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.BottomSheet
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.TextField

/** The Custom profile editor. Changes apply as they are made; swipe down to close. */
@Composable
fun ScanOptionsSheet(options: CustomOptions, onChange: (CustomOptions) -> Unit, onDismiss: () -> Unit) {
    BottomSheet(onDismiss = onDismiss, title = stringResource(R.string.lan_options)) {
        ScanOptions(options, onChange)
    }
}

@Composable
private fun ScanOptions(options: CustomOptions, onChange: (CustomOptions) -> Unit) {
    val portsError = stringResource(R.string.lan_options_ports_error, CustomOptions.MAX_PORTS_PER_HOST)
    val portsKeyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    var portsHelp by rememberSaveable { mutableStateOf<Protocol?>(null) }
    var namesHelp by rememberSaveable { mutableStateOf(false) }
    portsHelp?.let { PortsHelpDialog(it, onDismiss = { portsHelp = null }) }
    if (namesHelp) {
        val server = options.dnsServer.trim().takeIf { it.isNotEmpty() && options.dnsServerValid }
        NamesHelpDialog(server, onDismiss = { namesHelp = false })
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupedSwitchRow(
            title = stringResource(R.string.lan_options_ping),
            subtitle = stringResource(R.string.lan_options_ping_description),
            checked = options.ping,
            onCheckedChange = { onChange(options.copy(ping = it)) },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = options.tcpPorts,
                onValueChange = { onChange(options.copy(tcpPorts = it)) },
                label = stringResource(R.string.lan_options_tcp),
                placeholder = stringResource(R.string.lan_options_ports_placeholder),
                error = portsError.takeIf { options.tcpPortList == null },
                keyboardOptions = portsKeyboard,
                onHelp = { portsHelp = Protocol.Tcp },
            )
            TextField(
                value = options.udpPorts,
                onValueChange = { onChange(options.copy(udpPorts = it)) },
                label = stringResource(R.string.lan_options_udp),
                placeholder = stringResource(R.string.lan_options_none),
                error = portsError.takeIf { options.udpPortList == null },
                keyboardOptions = portsKeyboard,
                onHelp = { portsHelp = Protocol.Udp },
            )
            TextField(
                value = options.timeout,
                onValueChange = { onChange(options.copy(timeout = it)) },
                label = stringResource(R.string.lan_options_timeout),
                error = stringResource(
                    R.string.lan_options_range_error,
                    CustomOptions.TIMEOUT_RANGE.first,
                    CustomOptions.TIMEOUT_RANGE.last,
                ).takeIf { options.timeoutMillis == null },
                keyboardOptions = numberKeyboard,
            )
            TextField(
                value = options.concurrency,
                onValueChange = { onChange(options.copy(concurrency = it)) },
                label = stringResource(R.string.lan_options_concurrency),
                error = stringResource(
                    R.string.lan_options_range_error,
                    CustomOptions.CONCURRENCY_RANGE.first,
                    CustomOptions.CONCURRENCY_RANGE.last,
                ).takeIf { options.concurrencyValue == null },
                keyboardOptions = numberKeyboard,
            )
        }
        GroupedSwitchRow(
            title = stringResource(R.string.lan_options_names),
            subtitle = stringResource(R.string.lan_options_names_description),
            checked = options.resolveNames,
            onCheckedChange = { onChange(options.copy(resolveNames = it)) },
            titleAccessory = { HelpButton(onClick = { namesHelp = true }) },
        )
        if (options.resolveNames) {
            var presetsOpen by remember { mutableStateOf(false) }
            TextField(
                value = options.dnsServer,
                onValueChange = { onChange(options.copy(dnsServer = it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(R.string.lan_options_dns),
                placeholder = stringResource(R.string.lan_options_dns_default),
                error = stringResource(R.string.ports_host_error).takeIf { !options.dnsServerValid },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
                transformInput = { old, new -> formatAddressInput(old, new, allowRange = false) },
                clearable = true,
                // A list of well-known servers to pick from, opened with the chevron. Typing still works.
                trailing = {
                    Box {
                        FieldButton(
                            icon = if (presetsOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                            contentDescription = stringResource(R.string.lan_options_dns_choose),
                            onClick = { presetsOpen = !presetsOpen },
                        )
                        DnsMenu(
                            expanded = presetsOpen,
                            selected = options.dnsServer.trim(),
                            onSelect = { address ->
                                presetsOpen = false
                                onChange(options.copy(dnsServer = address))
                            },
                            onDismiss = { presetsOpen = false },
                        )
                    }
                },
            )
        }
        if (options.hasNoProbe) {
            Note(stringResource(R.string.lan_options_no_probe), Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** DNS servers offered in the list, by name. A null name stands for "the network's own", an empty field. */
private val DnsPresets = listOf(null to "", "Cloudflare" to "1.1.1.1", "Google" to "8.8.8.8", "Quad9" to "9.9.9.9")

/** The list of DNS servers under the field's chevron. The current choice is marked with a check. */
@Composable
private fun DnsMenu(expanded: Boolean, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    Menu(expanded = expanded, onDismiss = onDismiss) {
        DnsPresets.forEach { (name, address) ->
            MenuItem(
                text = name ?: stringResource(R.string.lan_options_dns_network),
                onClick = { onSelect(address) },
                trailing = {
                    if (address == selected) {
                        Icon(AppIcons.Check, contentDescription = null, tint = colors.accent)
                    } else if (address.isNotEmpty()) {
                        Text(address, style = AppTheme.typography.subhead, color = colors.secondaryLabel)
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun ScanOptionsPreview() {
    AppTheme {
        var options by remember { mutableStateOf(CustomOptions(udpPorts = "53, 99999")) }
        Box(Modifier.background(AppTheme.colors.background).padding(top = 16.dp)) {
            ScanOptions(options, onChange = { options = it })
        }
    }
}
