package com.danyt.meerkat.feature.lan

import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.LaunchedEffect
import com.danyt.meerkat.core.design.FieldButton
import com.danyt.meerkat.core.net.ScanConfig
import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.design.HelpButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.Hairline
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.ProgressBar
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.design.TextField
import com.danyt.meerkat.core.net.Device
import com.danyt.meerkat.core.net.Ipv4
import com.danyt.meerkat.core.net.LanScanState
import com.danyt.meerkat.core.net.MAX_TARGET_SIZE
import com.danyt.meerkat.core.net.PortResult
import com.danyt.meerkat.core.net.PortState
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.ScanStatus
import com.danyt.meerkat.core.net.TargetError
import com.danyt.meerkat.core.net.formatAddressInput
import com.danyt.meerkat.core.permission.openAppSettings
import com.danyt.meerkat.core.privacy.MacPrivacyDialog
import com.danyt.meerkat.core.reports.ToolReportSheet
import com.danyt.meerkat.core.permission.rememberLocalNetworkRequest

/** The LAN scanner: choose a target and a profile, run the scan, and watch devices appear. */
@Composable
fun LanScreen(
    viewModel: LanViewModel,
    intros: Intros,
    onBack: () -> Unit,
    onOpenDevice: (address: String) -> Unit,
    onOpenScope: () -> Unit,
    onOpenReport: (id: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var accessDenied by rememberSaveable { mutableStateOf(false) }
    var confirmScope by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showModeHelp by rememberSaveable { mutableStateOf(false) }
    val intro = rememberIntro(intros, IntroKeys.LAN)
    val resources = LocalResources.current
    val requestAccess = rememberLocalNetworkRequest { granted ->
        if (granted) viewModel.startScan() else accessDenied = true
    }

    // Only local addresses are behind the permission. Targets on the internet need none.
    val proceed = { if (viewModel.targetIsLocal()) requestAccess() else viewModel.startScan() }

    // The phone may have changed network while the app was in the background.
    // Wi-Fi and the hotspot can be switched from the quick settings without leaving the app, so look again
    // every few seconds while the screen is in front. This only reads the device's own network settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshNetwork()
                delay(NETWORK_REFRESH_MILLIS)
            }
        }
    }

    LanContent(
        state = state,
        actions = LanActions(
            onBack = onBack,
            onTarget = viewModel::setTarget,
            onUseCurrentNetwork = viewModel::useCurrentNetwork,
            onUseHotspot = viewModel::useHotspotNetwork,
            onProfile = viewModel::setProfile,
            onEditOptions = { showOptions = true },
            onScan = {
                focusManager.clearFocus()
                when {
                    viewModel.validate() -> if (viewModel.targetIsOutsideScope()) confirmScope = true else proceed()
                    // The target is fine but the custom options are not: take the user to them.
                    state.profile == ScanProfile.Custom && state.custom.config == null -> showOptions = true
                }
            },
            onCancel = viewModel::cancelScan,
            onOpenDevice = onOpenDevice,
            onReport = { showReport = true },
            onOpenScope = onOpenScope,
            onInfo = intro.open.takeIf { intro.enabled },
            onModeHelp = { showModeHelp = true },
        ),
    )

    IntroDialog(intro, stringResource(R.string.feature_lan_title)) { LanIntro() }
    if (showModeHelp) {
        val config = when (state.profile) {
            ScanProfile.Quick -> ScanConfig.Quick
            ScanProfile.Full -> ScanConfig.Full
            ScanProfile.Custom -> state.custom.config
        }
        ScanHelpDialog(stringResource(state.profile.label), config, onDismiss = { showModeHelp = false })
    }

    ToolReportSheet(
        visible = showReport,
        report = { lanReport(resources, state) },
        hashes = state.reportHashes,
        onDismiss = { showReport = false },
        onExported = viewModel::markExported,
        onSave = viewModel::saveReport,
        onOpenReport = onOpenReport,
    )

    if (showOptions) {
        ScanOptionsSheet(state.custom, onChange = viewModel::setCustom, onDismiss = { showOptions = false })
    }
    if (confirmScope) {
        ConfirmDialog(
            title = stringResource(R.string.scope_warning_title),
            message = stringResource(R.string.scope_warning_message, state.target.trim()),
            confirmLabel = stringResource(R.string.scope_warning_confirm),
            destructive = true,
            onConfirm = {
                confirmScope = false
                proceed()
            },
            onDismiss = { confirmScope = false },
        )
    } else if (state.showMacTip && !intro.visible) {
        MacPrivacyDialog(
            onDismiss = { viewModel.closeMacTip(forever = false) },
            onDontShowAgain = { viewModel.closeMacTip(forever = true) },
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

private class LanActions(
    val onBack: () -> Unit = {},
    val onTarget: (String) -> Unit = {},
    val onUseCurrentNetwork: () -> Unit = {},
    val onUseHotspot: () -> Unit = {},
    val onProfile: (ScanProfile) -> Unit = {},
    val onEditOptions: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onOpenDevice: (String) -> Unit = {},
    val onReport: () -> Unit = {},
    val onOpenScope: () -> Unit = {},
    val onInfo: (() -> Unit)? = null,
    val onModeHelp: () -> Unit = {},
)

@Composable
private fun LanContent(state: LanUiState, actions: LanActions) {
    val scan = state.scan
    val running = scan.status == ScanStatus.Running
    AppScaffold(
        title = stringResource(R.string.feature_lan_title),
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
        item(key = "setup") { ScanSetup(state, running, actions) }
        if (scan.status != ScanStatus.Idle) {
            item(key = "status") { ScanResultHeader(scan, running) }
            if (scan.devices.isEmpty() && !running) {
                item(key = "none") {
                    EmptyState(
                        title = stringResource(R.string.lan_none_title),
                        icon = AppIcons.Lan,
                        message = stringResource(R.string.lan_none_message),
                    )
                }
            }
            if (scan.devices.isNotEmpty()) {
                item(key = "devices") {
                    val count = scan.devices.size
                    GroupedSection(header = pluralStringResource(R.plurals.lan_devices, count, count)) {
                        // This phone comes first, set apart from the devices that were found around it.
                        val (mine, others) = scan.devices.partition { it.address in state.ownAddresses }
                        mine.forEach { DeviceRow(it, isMe = true, actions.onOpenDevice) }
                        if (mine.isNotEmpty() && others.isNotEmpty()) {
                            Hairline(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        others.forEach { DeviceRow(it, isMe = false, actions.onOpenDevice) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanSetup(state: LanUiState, running: Boolean, actions: LanActions) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Only when there is nothing to fill in: with the hotspot on, its button offers a target.
        if (state.currentNetwork == null && state.hotspotNetwork == null) Note(stringResource(R.string.lan_no_network))
        TextField(
            value = state.target,
            onValueChange = actions.onTarget,
            label = stringResource(R.string.lan_target),
            placeholder = stringResource(R.string.lan_target_placeholder),
            error = state.targetError?.let { stringResource(it.message, MAX_TARGET_SIZE) },
            enabled = !running,
            keyboardOptions = KeyboardOptions(
                // The phone keypad is the number pad that also has the dot, slash, and dash keys.
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onGo = { actions.onScan() }),
            transformInput = { old, new -> formatAddressInput(old, new, allowRange = true) },
            clearable = true,
            // Fill-in buttons: the network the phone is on, and its own hotspot when that is switched on.
            trailing = {
                val network = state.currentNetwork
                if (network != null && !running && state.target.trim() != network) {
                    FieldButton(
                        icon = if (state.currentNetworkWired) AppIcons.Ethernet else AppIcons.Wifi,
                        contentDescription = stringResource(R.string.lan_use_network, network),
                        onClick = actions.onUseCurrentNetwork,
                    )
                }
                val hotspot = state.hotspotNetwork
                if (hotspot != null && !running && state.target.trim() != hotspot) {
                    FieldButton(
                        icon = AppIcons.Hotspot,
                        contentDescription = stringResource(R.string.lan_use_hotspot, hotspot),
                        onClick = actions.onUseHotspot,
                    )
                }
            },
        )

        SegmentedControl(
            options = ScanProfile.entries.map { stringResource(it.label) },
            selectedIndex = state.profile.ordinal,
            onSelected = { if (!running) actions.onProfile(ScanProfile.entries[it]) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(state.profile.description),
                style = AppTheme.typography.subhead,
                color = colors.secondaryLabel,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            HelpButton(actions.onModeHelp)
        }
        if (state.profile == ScanProfile.Custom) {
            SecondaryButton(
                text = stringResource(R.string.lan_options),
                onClick = actions.onEditOptions,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
            )
        }
        if (running) {
            SecondaryButton(
                text = stringResource(R.string.lan_cancel_scan),
                onClick = actions.onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.lan_scan),
                onClick = actions.onScan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * What the results below belong to: the target and mode of the scan that produced them, which may differ from
 * what is in the form now, and how the scan went.
 */
@Composable
private fun ScanResultHeader(scan: LanScanState, running: Boolean) {
    val colors = AppTheme.colors
    val mode = ScanProfile.entries.firstOrNull { it.name == scan.mode }?.let { stringResource(it.label) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = listOfNotNull(scan.target, mode).joinToString(" \u00b7 "),
            style = AppTheme.typography.headline,
            color = colors.label,
        )
        if (running) ProgressBar(if (scan.total == 0) 0f else scan.scanned.toFloat() / scan.total)
        val status = when (scan.status) {
            ScanStatus.Cancelled ->
                pluralStringResource(R.plurals.lan_status_cancelled, scan.total, scan.scanned, scan.total)
            ScanStatus.Finished -> pluralStringResource(R.plurals.lan_status_finished, scan.total, scan.total)
            else -> pluralStringResource(R.plurals.lan_status_running, scan.total, scan.scanned, scan.total)
        }
        val found = pluralStringResource(R.plurals.lan_result_devices, scan.devices.size, scan.devices.size)
        Text("$status $found", style = AppTheme.typography.subhead, color = colors.secondaryLabel)
    }
}

@Composable
private fun DeviceRow(device: Device, isMe: Boolean, onOpen: (String) -> Unit) {
    val ports = device.ports.map { portLabel(it.port, it.protocol) }.joinToString(", ")
    val details = listOfNotNull(
        device.hostname,
        device.mac,
        ports.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.lan_open_ports, it) },
    )
    GroupedRow(
        title = device.address.toString(),
        titleNote = if (isMe) stringResource(R.string.lan_device_me) else null,
        subtitle = details.joinToString("\n").ifEmpty { null },
        value = device.latencyMillis?.let { latencyText(it) },
        chevron = true,
        onClick = { onOpen(device.address.toString()) },
    )
}

private val ScanProfile.label: Int
    get() = when (this) {
        ScanProfile.Quick -> R.string.lan_profile_quick
        ScanProfile.Full -> R.string.lan_profile_full
        ScanProfile.Custom -> R.string.lan_profile_custom
    }

private val ScanProfile.description: Int
    get() = when (this) {
        ScanProfile.Quick -> R.string.lan_profile_quick_description
        ScanProfile.Full -> R.string.lan_profile_full_description
        ScanProfile.Custom -> R.string.lan_profile_custom_description
    }

private val TargetError.message: Int
    get() = when (this) {
        TargetError.Empty -> R.string.lan_target_error_empty
        TargetError.Malformed -> R.string.lan_target_error_malformed
        TargetError.Reversed -> R.string.lan_target_error_reversed
        TargetError.TooLarge -> R.string.lan_target_error_too_large
    }

@Preview
@Composable
private fun LanScreenPreview() {
    val devices = listOf(
        Device(
            address = Ipv4.parse("192.168.1.1")!!,
            hostname = "router.lan",
            latencyMillis = 2,
            ports = listOf(PortResult(80, Protocol.Tcp, PortState.Open)),
        ),
        Device(Ipv4.parse("192.168.1.24")!!, latencyMillis = 14),
    )
    AppTheme {
        LanContent(
            state = LanUiState(
                target = "192.168.1.0/24",
                currentNetwork = "192.168.1.0/24",
                hotspotNetwork = "192.168.43.0/24",
                ownAddresses = listOf(Ipv4.parse("192.168.1.24")!!),
                scan = LanScanState("192.168.1.0/24", ScanStatus.Running, 90, 254, devices),
            ),
            actions = LanActions(),
        )
    }
}

private const val NETWORK_REFRESH_MILLIS = 3_000L
