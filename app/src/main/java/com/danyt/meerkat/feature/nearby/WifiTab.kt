package com.danyt.meerkat.feature.nearby

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.Hairline
import com.danyt.meerkat.core.design.HeaderButton
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.animated
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiConnection
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.isRandomMac
import com.danyt.meerkat.core.nearby.signalLevel
import com.danyt.meerkat.core.nearby.wifiDistanceMeters

/** The channel chart's remembered choices and how to change them. */
internal class ChartChoice(val band: WifiBand?, val onPickBand: (WifiBand) -> Unit, val fold: Fold)

/** What the Wi-Fi tab can ask the screen to do. */
internal class WifiActions(
    val onAllow: () -> Unit,
    val onScan: () -> Unit,
    val onOpenSettings: (action: String) -> Unit,
    val onSelect: (bssid: String) -> Unit,
    val onFilter: () -> Unit,
)

/** The Wi-Fi tab: access points in range, strongest first. Tap one for details, hold it to copy its address. */
internal fun LazyListScope.wifiItems(
    state: WifiTabState,
    animate: Boolean,
    actions: WifiActions,
    chart: ChartChoice,
) {
    if (!state.supported) {
        item(key = "wifi-none") {
            EmptyState(
                title = stringResource(R.string.nearby_wifi_unsupported),
                modifier = Modifier.animated(this, animate),
            )
        }
        return
    }
    if (!state.permitted) {
        item(key = "wifi-permission") {
            PermissionPrompt(
                text = stringResource(R.string.nearby_wifi_permission),
                onAllow = actions.onAllow,
                modifier = Modifier.animated(this, animate),
            )
        }
        return
    }
    if (!state.canScan) {
        item(key = "wifi-off") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_wifi_off),
                button = stringResource(R.string.nearby_open_wifi_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_WIFI_SETTINGS) },
                modifier = Modifier.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    if (state.locationNeeded) {
        item(key = "wifi-location") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_wifi_location),
                button = stringResource(R.string.nearby_open_location_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                modifier = Modifier.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    item(key = "wifi-scan") {
        PrimaryButton(
            text = stringResource(if (state.scanning) R.string.nearby_scanning else R.string.nearby_scan),
            onClick = actions.onScan,
            enabled = !state.scanning && state.canScan,
            modifier = NearbyInset.fillMaxWidth().padding(bottom = 16.dp).animated(this, animate),
        )
    }
    if (!state.scanPermitted) {
        item(key = "wifi-scan-permission") {
            Note(
                text = stringResource(R.string.nearby_wifi_scan_location),
                modifier = NearbyInset.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    if (state.refused) {
        item(key = "wifi-refused") {
            Note(
                text = stringResource(R.string.nearby_wifi_refused),
                modifier = NearbyInset.padding(bottom = 16.dp).animated(this, animate),
            )
        }
    }
    if ((if (state.filter.applyToChart) state.shown else state.networks).isNotEmpty()) {
        item(key = "wifi-channels") {
            ChannelsSection(
                networks = if (state.filter.applyToChart) state.shown else state.networks,
                filtered = state.filter.applyToChart && state.filter.isActive,
                pickedBand = chart.band,
                onPickBand = chart.onPickBand,
                fold = chart.fold,
                onSelect = actions.onSelect,
                modifier = Modifier.animated(this, animate),
            )
        }
    }
    item(key = "wifi-list") {
        val resources = LocalResources.current
        val copy = rememberCopyText()
        // Access points with the same name are one network (a mesh, or a router's 2.4 and 5 GHz radios): one row,
        // strongest access point first. Hidden networks have no name to group by, so each stays on its own.
        val groups = remember(state.shown) { groupByNetwork(state.shown) }
        val count = groups.size
        val total = remember(state.networks) { groupByNetwork(state.networks).size }
        // The clock is read here, not for the whole screen, so only this section redraws every second.
        val now = rememberNow()
        val points = state.shown.size
        GroupedSection(
            modifier = Modifier.animated(this, animate),
            header = listOfNotNull(
                if (state.filter.isActive) {
                    resources.getQuantityString(R.plurals.nearby_wifi_count_filtered, total, count, total)
                } else {
                    resources.getQuantityString(R.plurals.nearby_wifi_count, count, count)
                },
                resources.getQuantityString(R.plurals.nearby_wifi_access_points, points, points)
                    .takeIf { points > count },
            ).joinToString(" · "),
            headerAction = {
                val label = if (state.filter.isActive) {
                    stringResource(R.string.nearby_filter_count, state.filter.count)
                } else {
                    stringResource(R.string.nearby_filter)
                }
                HeaderButton(label, actions.onFilter)
            },
            footer = state.updatedAt?.let { stringResource(R.string.nearby_updated, agoText(it, now)) },
        ) {
            if (state.shown.isEmpty()) {
                val empty = if (state.networks.isEmpty()) R.string.nearby_wifi_empty else R.string.nearby_filter_none
                GroupedRow(stringResource(empty), titleColor = AppTheme.colors.secondaryLabel)
            }
            // The network this device is on comes first, set apart like this device in the LAN scanner.
            val connected = state.connection
            fun isConnected(group: List<WifiNetwork>) = connected != null && group.any(connected::isOn)
            val (mine, others) = groups.partition(::isConnected)
            mine.forEach { group ->
                val network = group.first { point -> connected?.bssid == null || point.bssid == connected.bssid }
                GroupedRow(
                    title = resources.networkName(network),
                    titleNote = stringResource(R.string.nearby_wifi_connected),
                    subtitle = listOfNotNull(
                        resources.networkSummary(network),
                        listOfNotNull(
                            connected?.ipAddress,
                            connected?.linkSpeed?.let { stringResource(R.string.nearby_mbps, it) },
                        ).joinToString(" · ").ifEmpty { null },
                    ).joinToString("\n"),
                    trailing = { SignalValue(network.dbm, signalLevel(network.dbm)) },
                    onClick = { actions.onSelect(network.bssid) },
                    onLongClick = { copy(network.bssid) },
                )
            }
            if (mine.isNotEmpty() && others.isNotEmpty()) {
                Hairline(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            others.forEach { group ->
                val network = group.first()
                val more = resources.getQuantityString(R.plurals.nearby_wifi_access_points, group.size, group.size)
                GroupedRow(
                    title = resources.networkName(network),
                    titleColor = if (network.ssid == null) AppTheme.colors.secondaryLabel else AppTheme.colors.label,
                    subtitle = listOfNotNull(resources.networkSummary(network), more.takeIf { group.size > 1 })
                        .joinToString("\n"),
                    trailing = { SignalValue(network.dbm, signalLevel(network.dbm)) },
                    onClick = { actions.onSelect(network.bssid) },
                    onLongClick = { copy(network.bssid) },
                )
            }
        }
    }
}

/**
 * Everything known about one access point. The device part (brand, model) appears only when the access point
 * announces it, which many do in their Wi-Fi Protected Setup information.
 */
@Composable
internal fun WifiDetails(
    network: WifiNetwork,
    accessPoints: List<WifiNetwork>,
    connection: WifiConnection?,
    onSelect: (bssid: String) -> Unit,
    onDismiss: () -> Unit,
    maker: String? = null,
    onScanNetwork: (() -> Unit)? = null,
) {
    val resources = LocalResources.current
    val extras = network.extras
    val connected = connection?.isOn(network) == true
    InfoDialog(
        title = resources.networkName(network),
        onDismiss = onDismiss,
        // On the network this device is on, a shortcut to scanning it for devices.
        action = onScanNetwork?.takeIf { connected }?.let { scan ->
            { HeaderButton(stringResource(R.string.nearby_wifi_scan_network), onClick = scan) }
        },
    ) {
        val item: @Composable (Int, String) -> Unit = { label, value ->
            InfoItem(stringResource(label), value, labelWidth = DetailLabelWidth)
        }
        val device = listOfNotNull(extras?.manufacturer, extras?.model, extras?.modelNumber, extras?.deviceName)
        if (device.isNotEmpty() || extras?.chipVendors?.isNotEmpty() == true || maker != null) {
            InfoHeading(stringResource(R.string.nearby_wifi_device))
            // From the downloaded IEEE list: who registered the access point's address block.
            maker?.let { item(R.string.maker_registered_to, it) }
            extras?.manufacturer?.let { item(R.string.nearby_wifi_brand, it) }
            listOfNotNull(extras?.model, extras?.modelNumber).distinct().joinToString(" ").ifEmpty { null }?.let {
                item(R.string.nearby_wifi_model, it)
            }
            extras?.deviceName?.let { item(R.string.nearby_wifi_device_name, it) }
            extras?.chipVendors?.takeIf { it.isNotEmpty() }?.let {
                item(R.string.nearby_wifi_chip, it.joinToString(", "))
            }
        }
        // This device's own connection, when it is on this access point.
        // Shown only once something is known, such as while the device is still getting an address.
        val own = connection?.takeIf { connected }
        if (own != null && listOfNotNull(own.ipAddress, own.linkSpeed, own.txSpeed, own.rxSpeed).isNotEmpty()) {
            InfoHeading(stringResource(R.string.nearby_wifi_connection))
            own.ipAddress?.let { item(R.string.nearby_wifi_ip, it) }
            own.linkSpeed?.let { item(R.string.nearby_wifi_link_speed, stringResource(R.string.nearby_mbps, it)) }
            own.txSpeed?.let { item(R.string.nearby_wifi_tx_speed, stringResource(R.string.nearby_mbps, it)) }
            own.rxSpeed?.let { item(R.string.nearby_wifi_rx_speed, stringResource(R.string.nearby_mbps, it)) }
        }
        InfoHeading(stringResource(R.string.nearby_wifi_radio))
        val random = if (isRandomMac(network.bssid)) " " + stringResource(R.string.nearby_private_address) else ""
        item(R.string.nearby_bssid, network.bssid + random)
        item(R.string.nearby_signal, stringResource(R.string.nearby_dbm, network.dbm))
        val meters = wifiDistanceMeters(network.dbm, network.frequency)
        if (meters > 0f) item(R.string.nearby_distance, resources.approxDistance(meters))
        item(R.string.nearby_frequency, stringResource(R.string.nearby_mhz, network.frequency))
        network.channel?.let { item(R.string.nearby_channel, it.toString()) }
        item(R.string.nearby_band, resources.band(network.band))
        network.channelWidth?.let { item(R.string.nearby_width, stringResource(R.string.nearby_mhz, it)) }
        resources.standard(network).ifEmpty { null }?.let { item(R.string.nearby_standard, it) }
        extras?.spatialStreams?.let { item(R.string.nearby_wifi_streams, it.toString()) }
        extras?.bssColor?.let { item(R.string.nearby_wifi_bss_color, it.toString()) }
        extras?.stations?.let { item(R.string.nearby_wifi_stations, it.toString()) }
        extras?.channelLoad?.let { item(R.string.nearby_wifi_load, stringResource(R.string.nearby_percent, it)) }
        extras?.country?.let { item(R.string.nearby_wifi_country, it) }

        InfoHeading(stringResource(R.string.nearby_security))
        // A network often accepts several, so older devices can still join: one per line.
        item(R.string.nearby_wifi_protocol, resources.securityFull(network.security, separator = "\n"))
        extras?.frameProtection?.let { item(R.string.nearby_wifi_pmf, resources.frameProtection(it)) }
        val features = listOfNotNull(
            stringResource(R.string.nearby_wifi_fast_roaming).takeIf { extras?.fastRoaming == true },
            stringResource(R.string.nearby_wifi_neighbors).takeIf { extras?.neighborReports == true },
            stringResource(R.string.nearby_wifi_steering).takeIf { extras?.steering == true },
            stringResource(R.string.nearby_wifi_multi_link).takeIf { extras?.multiLink == true },
            stringResource(R.string.nearby_passpoint).takeIf { network.passpoint },
        )
        if (features.isNotEmpty()) item(R.string.nearby_wifi_features, features.joinToString("\n"))

        // Other access points of the same network. Tapping one shows its details here instead.
        if (accessPoints.size > 1) {
            InfoHeading(stringResource(R.string.nearby_wifi_access_points_heading))
            accessPoints.forEach { point ->
                val current = point.bssid == network.bssid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (current) AppTheme.colors.fill else Color.Transparent)
                        .clickable(enabled = !current, role = Role.Button) { onSelect(point.bssid) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(point.bssid, style = AppTheme.typography.subhead, color = AppTheme.colors.label)
                        Text(
                            text = listOfNotNull(
                                point.channel?.let { stringResource(R.string.nearby_channel_short, it) },
                                resources.band(point.band).ifEmpty { null },
                            ).joinToString(" · "),
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.secondaryLabel,
                        )
                    }
                    SignalValue(point.dbm, signalLevel(point.dbm))
                }
            }
        }
    }
}

private val DetailLabelWidth = 120.dp

/** Access points grouped into networks by name, keeping the list's order. Hidden networks stay single. */
internal fun groupByNetwork(points: List<WifiNetwork>): List<List<WifiNetwork>> =
    points.groupBy { it.ssid ?: "\u0000" + it.bssid }.values.toList()
