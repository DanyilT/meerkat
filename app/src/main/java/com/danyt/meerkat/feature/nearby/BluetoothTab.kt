package com.danyt.meerkat.feature.nearby

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.BottomSheet
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.design.HeaderButton
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.RangeDiagram
import com.danyt.meerkat.core.design.RangePoint
import com.danyt.meerkat.core.design.RangeRing
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.animated
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.design.stableAngle
import com.danyt.meerkat.core.nearby.BluetoothAddressType
import com.danyt.meerkat.core.nearby.BluetoothFailure
import com.danyt.meerkat.core.nearby.BluetoothSighting
import com.danyt.meerkat.core.nearby.bluetoothDistanceMeters
import com.danyt.meerkat.core.nearby.companyName
import com.danyt.meerkat.core.nearby.signalLevel
import kotlin.math.log10

internal class BluetoothActions(
    val onAllow: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onOpenSettings: (action: String) -> Unit,
    val onSelect: (address: String) -> Unit,
    val onHideUnnamed: (Boolean) -> Unit,
    val onFilter: () -> Unit,
)

/** The Bluetooth tab: devices heard during the last scan, strongest first. */
internal fun LazyListScope.bluetoothItems(
    state: BluetoothTabState,
    animate: Boolean,
    actions: BluetoothActions,
    mapFold: Fold,
) {
    if (!state.supported) {
        item(key = "bt-none") {
            EmptyState(
                title = stringResource(R.string.nearby_bt_unsupported),
                modifier = Modifier.animated(this, animate),
            )
        }
        return
    }
    if (!state.permitted) {
        item(key = "bt-permission") {
            PermissionPrompt(
                text = stringResource(R.string.nearby_bt_permission),
                onAllow = actions.onAllow,
                modifier = Modifier.animated(this, animate),
            )
        }
        return
    }
    if (!state.enabled) {
        item(key = "bt-off") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_bt_off),
                button = stringResource(R.string.nearby_open_bt_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
                modifier = Modifier.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    if (state.locationNeeded) {
        item(key = "bt-location") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_bt_location),
                button = stringResource(R.string.nearby_open_location_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                modifier = Modifier.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    item(key = "bt-scan") {
        Column(
            modifier = NearbyInset.padding(bottom = 16.dp).animated(this, animate),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Scanning keeps going while the tab is open, so the button stops it rather than showing progress.
            if (state.scanning) {
                SecondaryButton(stringResource(R.string.nearby_stop), actions.onStop, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    text = stringResource(R.string.nearby_scan),
                    onClick = actions.onStart,
                    enabled = state.enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    state.failure?.takeIf { it != BluetoothFailure.Off }?.let { failure ->
        item(key = "bt-failure") {
            val text = when (failure) {
                BluetoothFailure.NoPermission -> R.string.nearby_bt_permission
                else -> R.string.nearby_bt_failed
            }
            Note(stringResource(text), NearbyInset.padding(bottom = 16.dp).animated(this, animate))
        }
    }
    if (state.startedAt != null || state.devices.isNotEmpty()) {
        if (state.onMap.isNotEmpty()) {
            item(key = "bt-diagram") {
                val filtered = state.filterOnMap && state.hideUnnamed
                BluetoothDiagram(state.onMap, filtered, mapFold, Modifier.animated(this, animate), actions.onSelect)
            }
        }
        item(key = "bt-list") {
            val resources = LocalResources.current
            val copy = rememberCopyText()
            val count = state.shown.size
            val total = state.devices.size
            // Android 12 and newer leave out beacons for apps that promise not to locate the user.
            val footer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stringResource(R.string.nearby_bt_beacons)
            } else {
                null
            }
            GroupedSection(
                modifier = Modifier.animated(this, animate),
                header = if (count < total) {
                    resources.getQuantityString(R.plurals.nearby_bt_count_filtered, total, count, total)
                } else {
                    resources.getQuantityString(R.plurals.nearby_bt_count, count, count)
                },
                footer = footer,
                // For now hiding unnamed devices is the only filter, so the button is named after it.
                headerAction = {
                    val label = if (state.hideUnnamed) {
                        R.string.nearby_bt_hide_unnamed_on
                    } else {
                        R.string.nearby_bt_hide_unnamed_short
                    }
                    HeaderButton(stringResource(label), actions.onFilter)
                },
            ) {
                if (state.shown.isEmpty()) {
                    val empty = if (state.scanning) R.string.nearby_bt_listening else R.string.nearby_bt_empty
                    GroupedRow(
                        title = stringResource(empty),
                        titleColor = AppTheme.colors.secondaryLabel,
                        subtitle = stringResource(R.string.nearby_bt_empty_hint),
                    )
                }
                state.shown.forEach { device ->
                    GroupedRow(
                        title = resources.deviceName(device),
                        titleColor = if (device.name == null) AppTheme.colors.secondaryLabel else AppTheme.colors.label,
                        subtitle = resources.deviceSummary(device),
                        trailing = { SignalValue(device.dbm, signalLevel(device.dbm)) },
                        onClick = { actions.onSelect(device.address) },
                        onLongClick = { copy(device.address) },
                    )
                }
            }
        }
    }
}

/** Everything heard from one device, with its signal over the last two minutes. */
@Composable
internal fun BluetoothDetails(device: BluetoothSighting, onDismiss: () -> Unit, maker: String? = null) {
    val now = rememberNow()
    val resources = LocalResources.current
    InfoDialog(title = resources.deviceName(device), onDismiss = onDismiss) {
        val item: @Composable (Int, String) -> Unit = { label, value ->
            InfoItem(stringResource(label), value, labelWidth = DetailWidth)
        }
        item(R.string.nearby_address, device.address)
        device.addressType?.let { type ->
            val label = when (type) {
                BluetoothAddressType.Public -> R.string.nearby_bt_address_public
                BluetoothAddressType.Random -> R.string.nearby_bt_address_random
            }
            item(R.string.nearby_bt_address_type, stringResource(label))
        }
        item(R.string.nearby_signal, stringResource(R.string.nearby_dbm, device.dbm))
        // Estimated like the distance map: from the signal and, when the device says it, its transmit power.
        item(R.string.nearby_distance, resources.approxDistance(bluetoothDistanceMeters(device.dbm, device.txPower)))
        resources.deviceKind(device)?.let { item(R.string.nearby_bt_kind, it) }
        item(R.string.nearby_bt_type, resources.bluetoothKind(device.kind))
        if (device.manufacturers.isNotEmpty()) {
            item(R.string.nearby_bt_maker, device.manufacturers.joinToString(", ") { companyName(it) })
        }
        maker?.let { item(R.string.maker_registered_to, it) }
        val battery = device.apple?.let { resources.earbudBattery(it) }
            ?: device.battery?.let { stringResource(R.string.nearby_percent, it) }
        battery?.let { item(R.string.nearby_bt_battery, it) }
        if (device.services.isNotEmpty()) {
            item(R.string.nearby_bt_services, device.services.joinToString(", ") { resources.serviceName(it) })
        }
        device.connectable?.let {
            item(R.string.nearby_bt_connectable, stringResource(if (it) R.string.nearby_yes else R.string.nearby_no))
        }
        device.txPower?.let { item(R.string.nearby_bt_tx_power, stringResource(R.string.nearby_dbm, it)) }
        item(R.string.nearby_last_seen, agoText(device.seenAt, now))
        device.apple?.takeIf { it.messages.isNotEmpty() }?.let { apple ->
            item(R.string.nearby_bt_apple, resources.appleMessages(apple))
            InfoText(stringResource(R.string.nearby_bt_apple_hint))
        }
        InfoHeading(stringResource(R.string.nearby_bt_signal_history))
        SignalHistory(device.history, now)
    }
}

private val DetailWidth = 120.dp

/**
 * The devices around this device by estimated distance, on a log scale so near ones do not crowd the centre.
 * The estimate comes from signal strength, so it is rough; directions are not known and only spread points out.
 */
@Composable
private fun BluetoothDiagram(
    devices: List<BluetoothSighting>,
    filtered: Boolean,
    fold: Fold,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    val resources = LocalResources.current
    // The nearest ones only, so the diagram stays readable in a crowded place.
    val points = devices.take(MAX_DIAGRAM_POINTS).map { device ->
        RangePoint(
            key = device.address,
            label = device.name ?: resources.deviceKind(device) ?: device.address.takeLast(5),
            distance = meterDistance(bluetoothDistanceMeters(device.dbm, device.txPower)),
            angle = stableAngle(device.address),
        )
    }
    GroupedSection(
        modifier = modifier,
        header = stringResource(if (filtered) R.string.nearby_bt_map_filtered else R.string.nearby_bt_map),
        footer = stringResource(R.string.nearby_bt_map_hint),
        expanded = !fold.folded,
        headerAction = { FoldButton(fold) },
    ) {
        RangeDiagram(
            points = points,
            rings = MeterRings.map { RangeRing(meterDistance(it), stringResource(R.string.nearby_meters, it.toInt())) },
            centerLabel = stringResource(R.string.nearby_this_device),
            description = stringResource(R.string.nearby_bt_map_hint),
            modifier = Modifier.padding(8.dp),
            onSelect = onSelect,
        )
    }
}

/** 30 cm at the centre to 30 m at the edge, on a log scale. */
private fun meterDistance(meters: Float): Float =
    (log10(meters / 0.3f) / log10(100f)).coerceIn(0.08f, 1f)

private val MeterRings = listOf(1f, 3f, 10f, 30f)
private const val MAX_DIAGRAM_POINTS = 30

/** Filter options for the Bluetooth list. For now one: leaving out devices that announce no name. */
@Composable
internal fun BluetoothFilterSheet(
    state: BluetoothTabState,
    onHideUnnamed: (Boolean) -> Unit,
    onFilterOnMap: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismiss = onDismiss, title = stringResource(R.string.nearby_filter)) {
        val unnamed = state.devices.count { it.name == null }
        val resources = LocalResources.current
        GroupedSection {
            GroupedSwitchRow(
                title = stringResource(R.string.nearby_bt_hide_unnamed),
                subtitle = resources.getQuantityString(R.plurals.nearby_bt_unnamed_count, unnamed, unnamed),
                checked = state.hideUnnamed,
                onCheckedChange = onHideUnnamed,
            )
        }
        GroupedSection {
            GroupedSwitchRow(
                title = stringResource(R.string.nearby_bt_filter_map),
                checked = state.filterOnMap,
                onCheckedChange = onFilterOnMap,
            )
        }
    }
}
