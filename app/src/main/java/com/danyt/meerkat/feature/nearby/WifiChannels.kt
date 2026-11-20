package com.danyt.meerkat.feature.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.danyt.meerkat.core.design.HeaderButton
import com.danyt.meerkat.core.design.InfoDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.channelCrowding
import com.danyt.meerkat.core.nearby.span
import com.danyt.meerkat.core.nearby.wifiChannelFrequency

/**
 * How crowded the Wi-Fi channels are: every network as a hump over the channels it covers, as tall as its
 * signal is strong, with the busiest channel named below. A band switch appears when networks use several bands.
 *
 * @param pickedBand the band the user last picked, or null. When no network uses it right now, the band with the
 *   most networks is shown instead, without replacing the user's pick, so their band returns once it has
 *   networks again.
 * @param onPickBand called only when the user picks a band.
 * @param filtered true when [networks] is the filter's result, which the header then says.
 * @param fold whether the chart is folded away; the chevron in the header toggles it.
 * @param onSelect called with an access point's address when its hump is tapped.
 */
@Composable
internal fun ChannelsSection(
    networks: List<WifiNetwork>,
    filtered: Boolean,
    pickedBand: WifiBand?,
    onPickBand: (WifiBand) -> Unit,
    fold: Fold,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val bands = ChartBands.filter { band -> networks.any { it.band == band } }
    if (bands.isEmpty()) return
    val band = pickedBand?.takeIf { it in bands } ?: bands.maxBy { band -> networks.count { it.band == band } }
    var allChannels by rememberSaveable { mutableStateOf(false) }
    val crowding = channelCrowding(networks, band)
    // Neighbouring channels often tie, because a network reaches into them; the one networks are set to wins.
    val busiest = crowding.maxWith(
        compareBy<Pair<Int, Int>> { it.second }
            .thenBy { (channel, _) -> networks.count { it.band == band && it.channel == channel } },
    )
    GroupedSection(
        modifier = modifier,
        header = stringResource(if (filtered) R.string.nearby_channels_filtered else R.string.nearby_channels),
        expanded = !fold.folded,
        headerAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderButton(stringResource(R.string.nearby_channels_all)) { allChannels = true }
                FoldButton(fold)
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bands.size > 1) {
                SegmentedControl(
                    options = bands.map { resources.band(it) },
                    selectedIndex = bands.indexOf(band),
                    onSelected = { onPickBand(bands[it]) },
                )
            }
            val summary = buildList {
                if (busiest.second > 0) {
                    val (channel, count) = busiest
                    add(resources.getQuantityString(R.plurals.nearby_channels_busiest, count, channel, count))
                }
                // On 2.4 GHz only channels 1, 6, and 11 do not overlap, so the quietest of them is the one to use.
                if (band == WifiBand.Band2Ghz) {
                    val quiet = crowding.filter { it.first in NonOverlapping }.minBy { it.second }
                    add(resources.getString(R.string.nearby_channels_quietest, quiet.first))
                }
            }.joinToString("\n")
            ChannelChart(
                networks = networks.filter { it.band == band },
                band = band,
                onSelect = onSelect,
                modifier = Modifier.semantics { contentDescription = summary },
            )
            Text(summary, style = AppTheme.typography.subhead, color = AppTheme.colors.secondaryLabel)
        }
    }
    if (allChannels) AllChannelsDialog(networks, startBand = band, onDismiss = { allChannels = false })
}

/**
 * Every channel of a band in one list, each row tinted from green (free) to red (crowded) by how many networks
 * reach it, with the channel load the access points report where they do.
 */
@Composable
private fun AllChannelsDialog(networks: List<WifiNetwork>, startBand: WifiBand, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    // 2.4 and 5 GHz always, 6 GHz when a network uses it.
    val bands = listOf(WifiBand.Band2Ghz, WifiBand.Band5Ghz) +
        listOfNotNull(WifiBand.Band6Ghz.takeIf { band -> networks.any { it.band == band } })
    var band by rememberSaveable { mutableStateOf(startBand.takeIf { it in bands } ?: bands.first()) }
    InfoDialog(
        title = stringResource(R.string.nearby_channels_all),
        onDismiss = onDismiss,
        // The band switch stays at the top while the channels scroll.
        pinned = {
            SegmentedControl(
                options = bands.map { resources.band(it) },
                selectedIndex = bands.indexOf(band),
                onSelected = { band = bands[it] },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            channelCrowding(networks, band).forEach { (channel, count) ->
                // The load the access points on this channel report, averaged, where any report it.
                val loads = networks.filter { it.band == band && it.channel == channel }
                    .mapNotNull { it.extras?.channelLoad }
                val load = loads.takeIf { it.isNotEmpty() }?.average()?.toInt()
                ChannelRow(
                    channel = channel,
                    frequency = wifiChannelFrequency(band, channel),
                    count = count,
                    load = load,
                    crowding = maxOf(count / CROWDED_COUNT, (load ?: 0) / 100f).coerceIn(0f, 1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.nearby_channels_all_hint),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.secondaryLabel,
        )
    }
}

@Composable
private fun ChannelRow(channel: Int, frequency: Int, count: Int, load: Int?, crowding: Float) {
    val colors = AppTheme.colors
    val resources = LocalResources.current
    // Green at no crowding, through yellow, to red when crowded.
    val tint = Color.hsv(hue = 130f * (1f - crowding), saturation = 0.65f, value = 0.85f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.nearby_channel_number, channel),
            style = AppTheme.typography.body,
            color = colors.label,
            modifier = Modifier.weight(1f),
        )
        val detail = listOfNotNull(
            resources.getQuantityString(R.plurals.nearby_channel_networks, count, count),
            load?.let { stringResource(R.string.nearby_channel_load, it) },
            stringResource(R.string.nearby_mhz, frequency),
        ).joinToString(" · ")
        Text(detail, style = AppTheme.typography.subhead, color = colors.secondaryLabel)
    }
}

/** How many networks reaching one channel count as fully crowded, for the colour. */
private const val CROWDED_COUNT = 5f

@Composable
private fun ChannelChart(
    networks: List<WifiNetwork>,
    band: WifiBand,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = AppTheme.typography.caption.copy(color = colors.secondaryLabel)
    val nameStyle = AppTheme.typography.caption.copy(color = colors.label)
    val hidden = stringResource(R.string.nearby_wifi_hidden)
    val range = BandRange.getValue(band)
    val currentNetworks by rememberUpdatedState(networks)
    val tap = Modifier.pointerInput(band) {
        detectTapGestures { at ->
            val scale = ChartScale(size.width.toFloat(), size.height.toFloat(), range, density)
            // The strongest network whose hump covers the tapped spot.
            currentNetworks
                .filter { network ->
                    val span = network.span()
                    val under = at.y >= scale.y(network.dbm) - 24.dp.toPx()
                    at.x in scale.x(span.start)..scale.x(span.endInclusive) && under
                }
                .maxByOrNull { it.dbm }
                ?.let { onSelect(it.bssid) }
        }
    }
    Canvas(modifier.fillMaxWidth().height(190.dp).then(tap)) {
        val scale = ChartScale(size.width, size.height, range, density)
        val bottom = scale.bottom
        fun x(frequency: Float) = scale.x(frequency)
        fun y(dbm: Int) = scale.y(dbm)

        drawLine(colors.outline, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        Ticks.getValue(band).forEach { channel ->
            val label = measurer.measure(channel.toString(), axisStyle)
            val center = x(wifiChannelFrequency(band, channel).toFloat())
            val left = (center - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(left, bottom + 4.dp.toPx()))
        }

        // Weakest first, so the strongest networks are drawn on top.
        val sorted = networks.sortedBy { it.dbm }
        val line = Stroke(width = 1.5.dp.toPx())
        sorted.forEach { network ->
            val span = network.span()
            val left = x(span.start)
            val right = x(span.endInclusive)
            // A cubic curve reaches three quarters of the way to its control points, so they sit higher than the
            // peak by that much.
            val control = bottom + (y(network.dbm) - bottom) / 0.75f
            val width = right - left
            val hump = Path().apply {
                moveTo(left, bottom)
                cubicTo(left + width * 0.12f, control, right - width * 0.12f, control, right, bottom)
            }
            drawPath(hump, colors.accent.copy(alpha = 0.10f))
            drawPath(hump, colors.accent.copy(alpha = 0.85f), style = line)
        }

        // Names above the peaks, strongest first, skipping any that would overlap one already placed.
        val placed = mutableListOf<Rect>()
        sorted.asReversed().forEach { network ->
            val span = network.span()
            val center = x((span.start + span.endInclusive) / 2f)
            val name = measurer.measure(
                text = network.ssid ?: hidden,
                style = nameStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (size.width / 3).toInt()),
            )
            val left = (center - name.size.width / 2f).coerceIn(0f, size.width - name.size.width)
            val labelTop = (y(network.dbm) - name.size.height - 2.dp.toPx()).coerceAtLeast(0f)
            val box = Rect(left, labelTop, left + name.size.width, labelTop + name.size.height)
            if (placed.none { it.overlaps(box) }) {
                placed += box
                drawText(name, topLeft = Offset(left, labelTop))
            }
        }
    }
}

/** Where frequencies and signal levels fall on the chart, shared by drawing and tapping. */
private class ChartScale(
    private val width: Float,
    height: Float,
    private val range: ClosedFloatingPointRange<Float>,
    density: Float,
) {
    private val top = 18 * density
    val bottom = height - 20 * density

    fun x(frequency: Float) = (frequency - range.start) / (range.endInclusive - range.start) * width

    /** From -100 dBm (nothing) at the axis to -30 dBm (very close) at the top. */
    fun y(dbm: Int) = bottom - (dbm + 100).coerceIn(0, 70) / 70f * (bottom - top)
}

/** The bands the chart can show, in display order. 60 GHz networks are too rare to chart. */
private val ChartBands = listOf(WifiBand.Band2Ghz, WifiBand.Band5Ghz, WifiBand.Band6Ghz)

/** The frequencies each band's chart spans, in MHz. */
private val BandRange = mapOf(
    WifiBand.Band2Ghz to 2400f..2490f,
    WifiBand.Band5Ghz to 5160f..5895f,
    WifiBand.Band6Ghz to 5945f..7125f,
)

/** Channel numbers written under each band's axis. */
private val Ticks = mapOf(
    WifiBand.Band2Ghz to (1..13).toList(),
    WifiBand.Band5Ghz to listOf(36, 52, 100, 116, 132, 149, 165),
    WifiBand.Band6Ghz to listOf(1, 37, 69, 101, 133, 165, 197, 229),
)

private val NonOverlapping = setOf(1, 6, 11)
