package com.danyt.meerkat.feature.nearby

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.BottomSheet
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.Menu
import com.danyt.meerkat.core.design.MenuItem
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.design.HeaderButton
import com.danyt.meerkat.core.design.TextField
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.wifiBand
import com.danyt.meerkat.core.nearby.wifiChannel
import com.danyt.meerkat.core.nearby.wifiChannelFrequency
import com.danyt.meerkat.core.nearby.wifiChannels

/**
 * Filter options for the Wi-Fi list: name or address search, band, channel, security and its type, and
 * generation. Changes apply as they are made and are saved; swipe down to close. Every option is offered, with
 * how many networks match it now, so the user can wait for one that is not around yet.
 */
@Composable
internal fun WifiFilterSheet(
    filter: WifiFilter,
    networks: List<WifiNetwork>,
    onChange: (WifiFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    BottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.nearby_filter),
        titleStart = if (filter.isActive) {
            {
                HeaderButton(stringResource(R.string.nearby_filter_clear)) {
                    onChange(WifiFilter(applyToChart = filter.applyToChart))
                }
            }
        } else {
            null
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(R.string.nearby_filter_search),
                placeholder = stringResource(R.string.nearby_filter_search_hint),
                clearable = true,
            )
            val bands = listOf(null, WifiBand.Band2Ghz, WifiBand.Band5Ghz, WifiBand.Band6Ghz)
            SegmentedControl(
                options = bands.map { it?.let(resources::band) ?: stringResource(R.string.nearby_filter_all) },
                selectedIndex = bands.indexOf(filter.band).coerceAtLeast(0),
                onSelected = { index ->
                    // Channels of another band would hide everything, so they are dropped with the band.
                    val band = bands[index]
                    val channels = filter.channels.filter { band == null || wifiBand(it) == band }.toSet()
                    onChange(filter.copy(band = band, channels = channels))
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            GroupedSection {
                // Every channel of the chosen band, or of 2.4 and 5 GHz (and 6 GHz when used) for all bands.
                val channelBands = filter.band?.let(::listOf) ?: listOfNotNull(
                    WifiBand.Band2Ghz,
                    WifiBand.Band5Ghz,
                    WifiBand.Band6Ghz.takeIf { band -> networks.any { it.band == band } },
                )
                // Channels by their centre frequency, since numbers repeat between bands.
                val channels = channelBands.flatMap { band ->
                    wifiChannels(band).map { wifiChannelFrequency(band, it) }
                }
                MultiChoiceRow(
                    title = stringResource(R.string.nearby_channel),
                    values = filter.channels,
                    options = channels,
                    label = { frequency ->
                        val channel = wifiChannel(frequency) ?: 0
                        if (filter.band == null) {
                            val band = resources.band(wifiBand(frequency))
                            stringResource(R.string.nearby_channel_in_band, channel, band)
                        } else {
                            stringResource(R.string.nearby_channel_number, channel)
                        }
                    },
                    shortLabel = { frequency -> (wifiChannel(frequency) ?: 0).toString() },
                    count = { frequency -> networks.count { it.frequency == frequency } },
                    onChange = { onChange(filter.copy(channels = it)) },
                )
                // Several can be picked: a network passes when it offers any of them (such as WPA or WPA2).
                MultiChoiceRow(
                    title = stringResource(R.string.nearby_filter_security),
                    values = filter.security,
                    options = SecurityKind.entries,
                    label = { resources.securityKind(it) },
                    count = { kind -> networks.count { network -> network.security.any { it.kind == kind } } },
                    onChange = { onChange(filter.copy(security = it)) },
                )
                ChoiceRow(
                    title = stringResource(R.string.nearby_filter_type),
                    value = filter.securityType,
                    options = SecurityType.entries,
                    label = { resources.securityType(it) },
                    count = { type -> networks.count { WifiFilter(securityType = type).matches(it) } },
                    onPick = { onChange(filter.copy(securityType = it)) },
                )
                MultiChoiceRow(
                    title = stringResource(R.string.nearby_standard),
                    values = filter.standards,
                    options = StandardKind.entries,
                    label = { resources.standardKind(it) },
                    count = { kind -> networks.count { it.standardKind == kind } },
                    onChange = { onChange(filter.copy(standards = it)) },
                )
            }
            GroupedSection {
                GroupedSwitchRow(
                    title = stringResource(R.string.nearby_filter_chart),
                    checked = filter.applyToChart,
                    onCheckedChange = { onChange(filter.copy(applyToChart = it)) },
                )
            }
        }
    }
}

/**
 * A row showing one filter choice, with a menu of the options: "Any", then each one with how many networks
 * match it now.
 */
@Composable
private fun <T : Any> ChoiceRow(
    title: String,
    value: T?,
    options: List<T>,
    label: @Composable (T) -> String,
    count: (T) -> Int,
    onPick: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val any = stringResource(R.string.nearby_filter_any)
    val colors = AppTheme.colors
    Box {
        GroupedRow(
            title = title,
            value = value?.let { label(it) } ?: any,
            onClick = { open = true },
            trailing = {
                Icon(
                    imageVector = AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(15.dp),
                )
            },
        )
        Menu(expanded = open, onDismiss = { open = false }) {
            (listOf(null) + options).forEach { option ->
                MenuItem(
                    text = option?.let { label(it) } ?: any,
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    trailing = when {
                        option == value -> {
                            { Icon(AppIcons.Check, contentDescription = null, tint = colors.accent) }
                        }
                        option != null -> {
                            {
                                val matching = count(option)
                                Text(
                                    text = matching.toString(),
                                    style = AppTheme.typography.subhead,
                                    color = if (matching > 0) colors.secondaryLabel else colors.tertiaryLabel,
                                )
                            }
                        }
                        else -> null
                    },
                )
            }
        }
    }
}

/**
 * Like [ChoiceRow], but several options can be ticked: tapping one adds or removes it and keeps the menu open.
 * "Any" clears them all.
 */
@Composable
private fun <T : Comparable<T>> MultiChoiceRow(
    title: String,
    values: Set<T>,
    options: List<T>,
    label: @Composable (T) -> String,
    count: (T) -> Int,
    onChange: (Set<T>) -> Unit,
    shortLabel: @Composable (T) -> String = label,
) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val any = stringResource(R.string.nearby_filter_any)
    val shown = if (values.isEmpty()) any else values.sorted().map { shortLabel(it) }.joinToString(", ")
    Box {
        GroupedRow(
            title = title,
            value = shown,
            onClick = { open = true },
            trailing = {
                Icon(
                    imageVector = AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(15.dp),
                )
            },
        )
        Menu(expanded = open, onDismiss = { open = false }) {
            MenuItem(
                text = any,
                onClick = {
                    open = false
                    onChange(emptySet())
                },
                trailing = if (values.isEmpty()) {
                    { Icon(AppIcons.Check, contentDescription = null, tint = colors.accent) }
                } else {
                    null
                },
            )
            options.forEach { option ->
                val ticked = option in values
                MenuItem(
                    text = label(option),
                    onClick = { onChange(if (ticked) values - option else values + option) },
                    trailing = {
                        if (ticked) {
                            Icon(AppIcons.Check, contentDescription = null, tint = colors.accent)
                        } else {
                            val matching = count(option)
                            Text(
                                text = matching.toString(),
                                style = AppTheme.typography.subhead,
                                color = if (matching > 0) colors.secondaryLabel else colors.tertiaryLabel,
                            )
                        }
                    },
                )
            }
        }
    }
}

internal fun Resources.securityType(type: SecurityType): String = getString(
    when (type) {
        SecurityType.Personal -> R.string.nearby_filter_personal
        SecurityType.Enterprise -> R.string.nearby_filter_enterprise
    },
)

internal fun Resources.securityKind(kind: SecurityKind): String = when (kind) {
    SecurityKind.Wpa3 -> "WPA3"
    SecurityKind.Wpa2 -> "WPA2"
    SecurityKind.Wpa -> "WPA"
    SecurityKind.Owe -> getString(R.string.nearby_security_owe)
    SecurityKind.Wep -> "WEP"
    SecurityKind.Open -> getString(R.string.nearby_security_open)
}

internal fun Resources.standardKind(kind: StandardKind): String = when (kind) {
    StandardKind.Wifi7 -> "Wi-Fi 7"
    StandardKind.Wifi6E -> "Wi-Fi 6E"
    StandardKind.Wifi6 -> "Wi-Fi 6"
    StandardKind.Wifi5 -> "Wi-Fi 5"
    StandardKind.Wifi4 -> "Wi-Fi 4"
    StandardKind.Older -> getString(R.string.nearby_standard_legacy)
}

/** The filter in words, one "criterion: value" line each, for a report's details. */
internal fun Resources.filterLines(filter: WifiFilter): List<Pair<String, String>> = listOfNotNull(
    filter.query.trim().takeIf { it.isNotEmpty() }?.let { getString(R.string.nearby_filter_search) to it },
    filter.band?.let { getString(R.string.nearby_band) to band(it) },
    filter.channels.takeIf { it.isNotEmpty() }?.let { channels ->
        getString(R.string.nearby_channel) to channels.sorted().joinToString(", ") { frequency ->
            getString(R.string.nearby_channel_in_band, wifiChannel(frequency) ?: 0, band(wifiBand(frequency)))
        }
    },
    filter.security.takeIf { it.isNotEmpty() }?.let { kinds ->
        getString(R.string.nearby_filter_security) to kinds.sorted().joinToString(", ") { securityKind(it) }
    },
    filter.securityType?.let { getString(R.string.nearby_filter_type) to securityType(it) },
    filter.standards.takeIf { it.isNotEmpty() }?.let { kinds ->
        getString(R.string.nearby_standard) to kinds.sorted().joinToString(", ") { standardKind(it) }
    },
)
