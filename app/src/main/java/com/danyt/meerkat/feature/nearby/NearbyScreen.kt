package com.danyt.meerkat.feature.nearby

import android.Manifest
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.nearby.BluetoothAddressType
import com.danyt.meerkat.core.nearby.BluetoothKind
import com.danyt.meerkat.core.nearby.CellReading
import com.danyt.meerkat.core.permission.openAppSettings
import com.danyt.meerkat.core.permission.openSystemSettings
import com.danyt.meerkat.core.permission.rememberPermissionsRequest
import com.danyt.meerkat.core.reports.ToolReportSheet
import kotlinx.coroutines.launch

/**
 * The Nearby tool: Wi-Fi networks, Bluetooth devices, mobile network cells, and NFC tags around the device, one
 * tab each. A radio is used only while its tab is in front, and scans run only when the user starts them.
 *
 * @param initialTab the key of the tab to open on, from the route; null opens Wi-Fi.
 * @param onTabShown called with the key of each tab the user looks at, so the tool can reopen on the last one.
 */
@Composable
fun NearbyScreen(
    viewModel: NearbyViewModel,
    intros: Intros,
    initialTab: String?,
    onBack: () -> Unit,
    onOpenReport: (id: String) -> Unit,
    onTabShown: (key: String) -> Unit = {},
    onOpenToolSettings: () -> Unit = {},
    onScanNetwork: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val intro = rememberIntro(intros, IntroKeys.NEARBY)
    var tab by rememberSaveable { mutableStateOf(NearbyTab.of(initialTab)) }
    val wifi by viewModel.wifi.state.collectAsStateWithLifecycle()
    val bluetooth by viewModel.bluetooth.state.collectAsStateWithLifecycle()
    val cell by viewModel.cell.state.collectAsStateWithLifecycle()
    val nfc by viewModel.nfc.state.collectAsStateWithLifecycle()
    val hashes by viewModel.reportHashes.collectAsStateWithLifecycle()
    val chartBand by viewModel.chartBand.collectAsStateWithLifecycle()
    val folded by viewModel.folded.collectAsStateWithLifecycle()
    val makers by viewModel.makers.collectAsStateWithLifecycle()
    val fold = { key: String -> Fold(key in folded) { viewModel.setFolded(key, it) } }
    // The explanation to show after a permission was refused, as a string resource.
    var denied by rememberSaveable { mutableStateOf<Int?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var filteringBluetooth by rememberSaveable { mutableStateOf(false) }
    // The item whose details are open: an access point's or device's address, or a cell's position in the list.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(tab) { onTabShown(tab.key) }

    // Bank card details on screen: keep them out of screenshots and the recent apps view.
    val secret = tab == NearbyTab.Nfc && nfc.cardDetailsAllowed && nfc.tags.any { it.card != null }
    DisposableEffect(secret, activity) {
        val window = activity?.window
        if (secret) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secret) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Only the radio of the tab in front is used, and only while the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tab, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            when (tab) {
                NearbyTab.Wifi -> viewModel.wifi.watch()
                NearbyTab.Bluetooth -> viewModel.bluetooth.watch()
                NearbyTab.Cell -> viewModel.cell.watch()
                NearbyTab.Nfc -> activity?.let { viewModel.nfc.watch(it) }
            }
        }
    }

    val askWifi = rememberPermissionsRequest(viewModel.wifi.permissions) { _ ->
        viewModel.wifi.refreshStatus()
        // Approximate location alone (Android 12) is not enough for results, so the check is on the outcome.
        if (!viewModel.wifi.state.value.permitted) denied = R.string.nearby_wifi_permission
    }
    val askBluetooth = rememberPermissionsRequest(viewModel.bluetooth.permissions) { granted ->
        viewModel.bluetooth.refreshStatus()
        if (granted.isEmpty()) denied = R.string.nearby_bt_permission else viewModel.bluetooth.startByUser()
    }
    // Starting a Wi-Fi scan needs precise location, even where reading the results does not.
    val askWifiScan = rememberPermissionsRequest(viewModel.wifi.scanPermissions) { granted ->
        viewModel.wifi.refreshStatus()
        if (Manifest.permission.ACCESS_FINE_LOCATION in granted) {
            viewModel.wifi.scan()
        } else {
            denied = R.string.nearby_wifi_scan_permission
        }
    }
    val askCell = rememberPermissionsRequest(viewModel.cell.permissions) { granted ->
        // Cell details need precise location; approximate alone is not enough.
        if (Manifest.permission.ACCESS_FINE_LOCATION !in granted) denied = R.string.nearby_cell_permission
        scope.launch { viewModel.cell.refresh() }
    }
    val openSettings: (String) -> Unit = { openSystemSettings(context, it) }
    val animate = AppTheme.animationsEnabled

    AppScaffold(
        title = stringResource(R.string.feature_nearby_title),
        onTitleClick = onBack,
        actions = {
            if (intro.enabled) IconButton(AppIcons.Info, stringResource(R.string.intro_button), intro.open)
            IconButton(
                icon = AppIcons.Reports,
                contentDescription = stringResource(R.string.report_button),
                onClick = { showReport = true },
                enabled = hasReport(tab, wifi, bluetooth, cell, nfc),
            )
        },
    ) {
        item(key = "tabs") {
            SegmentedControl(
                options = NearbyTab.entries.map { stringResource(it.title) },
                selectedIndex = tab.ordinal,
                onSelected = {
                    tab = NearbyTab.entries[it]
                    selected = null
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            )
        }
        when (tab) {
            NearbyTab.Wifi -> wifiItems(
                state = wifi,
                animate = animate,
                actions = WifiActions(
                    onAllow = askWifi,
                    onScan = { if (wifi.scanPermitted) viewModel.wifi.scan() else askWifiScan() },
                    onOpenSettings = openSettings,
                    onSelect = { selected = it },
                    onFilter = { filtering = true },
                ),
                chart = ChartChoice(chartBand, viewModel::pickChartBand, fold(NearbySections.CHANNELS)),
            )
            NearbyTab.Bluetooth -> bluetoothItems(
                state = bluetooth,
                animate = animate,
                actions = BluetoothActions(
                    // Asking first also covers a permission withdrawn since the tab was opened.
                    onAllow = askBluetooth,
                    onStart = askBluetooth,
                    onStop = viewModel.bluetooth::stopByUser,
                    onOpenSettings = openSettings,
                    onSelect = { selected = it },
                    onHideUnnamed = viewModel::setHideUnnamed,
                    onFilter = { filteringBluetooth = true },
                ),
                mapFold = fold(NearbySections.BLUETOOTH_MAP),
            )
            NearbyTab.Cell -> cellItems(
                state = cell,
                animate = animate,
                actions = CellActions(askCell, openSettings) { selected = it },
                mapFold = fold(NearbySections.CELL_MAP),
            )
            NearbyTab.Nfc -> nfcItems(
                state = nfc,
                animate = animate,
                actions = NfcActions(openSettings, viewModel.nfc::clear, onOpenToolSettings, viewModel.nfc::reread),
            )
        }
    }

    // Details of the tapped item. They stay open while the list updates, and close if the item goes away.
    selected?.let { key ->
        val close = { selected = null }
        when (tab) {
            NearbyTab.Wifi -> wifi.networks.firstOrNull { it.bssid == key }?.let { network ->
                val siblings = wifi.networks.filter { network.ssid != null && it.ssid == network.ssid }
                WifiDetails(
                    network = network,
                    accessPoints = siblings,
                    connection = wifi.connection,
                    onSelect = { selected = it },
                    onDismiss = close,
                    maker = makers?.of(network.bssid),
                    onScanNetwork = onScanNetwork,
                )
            }
            NearbyTab.Bluetooth ->
                bluetooth.shown.firstOrNull { it.address == key }?.let { device ->
                    // Only a device's own (public) address names its maker; made-up ones belong to no one.
                    val public = when (device.addressType) {
                        BluetoothAddressType.Public -> true
                        BluetoothAddressType.Random -> false
                        null -> device.kind != BluetoothKind.LowEnergy
                    }
                    BluetoothDetails(device, close, maker = makers?.of(device.address)?.takeIf { public })
                }
            NearbyTab.Cell -> (cell.reading as? CellReading.Cells)?.cells?.firstOrNull { it.selectionKey() == key }
                ?.let { CellDetails(it, close) }
            NearbyTab.Nfc -> Unit
        }
    }

    if (filtering) {
        WifiFilterSheet(wifi.filter, wifi.networks, viewModel::setWifiFilter, onDismiss = { filtering = false })
    }

    if (filteringBluetooth) {
        BluetoothFilterSheet(
            state = bluetooth,
            onHideUnnamed = viewModel::setHideUnnamed,
            onFilterOnMap = viewModel::setBluetoothFilterOnMap,
            onDismiss = { filteringBluetooth = false },
        )
    }

    ToolReportSheet(
        visible = showReport,
        report = { nearbyReport(resources, tab, wifi, bluetooth, cell, nfc) },
        hashes = hashes,
        onDismiss = { showReport = false },
        onExported = viewModel::markExported,
        onSave = viewModel::saveReport,
        onOpenReport = onOpenReport,
    )

    IntroDialog(intro, stringResource(R.string.feature_nearby_title)) {
        InfoText(stringResource(R.string.intro_nearby_what))
        InfoHeading(stringResource(R.string.nearby_tab_wifi))
        InfoText(stringResource(R.string.intro_nearby_wifi))
        InfoHeading(stringResource(R.string.nearby_tab_bluetooth))
        InfoText(stringResource(R.string.intro_nearby_bluetooth))
        InfoHeading(stringResource(R.string.nearby_tab_cell))
        InfoText(stringResource(R.string.intro_nearby_cell))
        InfoHeading(stringResource(R.string.nearby_tab_nfc))
        InfoText(stringResource(R.string.intro_nearby_nfc))
        InfoHeading(stringResource(R.string.intro_nearby_privacy_heading))
        InfoText(stringResource(R.string.intro_nearby_privacy))
        InfoText(stringResource(R.string.intro_report_nearby))
    }

    denied?.let { message ->
        ConfirmDialog(
            title = stringResource(R.string.nearby_permission_title),
            message = stringResource(message),
            confirmLabel = stringResource(R.string.lan_access_open_settings),
            onConfirm = {
                denied = null
                openAppSettings(context)
            },
            onDismiss = { denied = null },
        )
    }
}
