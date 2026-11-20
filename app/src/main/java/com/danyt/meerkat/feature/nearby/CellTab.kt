package com.danyt.meerkat.feature.nearby

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.LoadingIndicator
import com.danyt.meerkat.core.design.RangeDiagram
import com.danyt.meerkat.core.design.RangePoint
import com.danyt.meerkat.core.design.RangeRing
import com.danyt.meerkat.core.design.animated
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.design.stableAngle
import com.danyt.meerkat.core.nearby.CellReading
import com.danyt.meerkat.core.nearby.CellTech
import com.danyt.meerkat.core.nearby.CellTower
import com.danyt.meerkat.core.nearby.distanceMeters
import com.danyt.meerkat.core.nearby.distanceStepMeters

internal class CellActions(
    val onAllow: () -> Unit,
    val onOpenSettings: (action: String) -> Unit,
    val onSelect: (key: String) -> Unit,
)

/**
 * Names one cell across refreshes. The modem lists cells in no fixed order and their number changes, so a
 * position in the list would point at another cell after the next read.
 */
internal fun CellTower.selectionKey(): String =
    listOf(tech, registered, mcc, mnc, area, cellId, code, channel).joinToString("|")

/** The Cell tab: the cell the device uses, and the neighbouring cells its modem hears. */
internal fun LazyListScope.cellItems(
    state: CellTabState,
    animate: Boolean,
    actions: CellActions,
    mapFold: Fold,
) {
    when (val reading = state.reading) {
        // Asking the modem takes a moment; a centred spinner shows it is on its way.
        null -> item(key = "cell-loading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp).animated(this, animate),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }
        CellReading.Unsupported -> item(key = "cell-none") {
            EmptyState(
                title = stringResource(R.string.nearby_cell_unsupported),
                modifier = Modifier.animated(this, animate),
            )
        }
        CellReading.NoPermission -> item(key = "cell-permission") {
            PermissionPrompt(
                text = stringResource(R.string.nearby_cell_permission),
                onAllow = actions.onAllow,
                modifier = Modifier.animated(this, animate),
            )
        }
        CellReading.LocationOff -> item(key = "cell-location") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_cell_location),
                button = stringResource(R.string.nearby_open_location_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                modifier = Modifier.animated(this, animate),
            )
        }
        is CellReading.Cells -> {
            val serving = reading.cells.filter { it.registered }
            val neighbours = reading.cells.filterNot { it.registered }
            if (reading.cells.isNotEmpty()) {
                item(key = "cell-diagram") {
                    CellDiagram(reading.cells, mapFold, Modifier.animated(this, animate), actions.onSelect)
                }
            }
            item(key = "cell-serving") {
                // The clock is read here, not for the whole screen, so only this footer redraws every second.
                val now = rememberNow()
                GroupedSection(
                    modifier = Modifier.animated(this, animate),
                    header = stringResource(R.string.nearby_cell_serving),
                    footer = listOfNotNull(
                        stringResource(R.string.nearby_cell_two_sims).takeIf { serving.size > 1 },
                        state.updatedAt?.let { stringResource(R.string.nearby_updated, agoText(it, now)) },
                    ).joinToString("\n").ifEmpty { null },
                ) {
                    if (serving.isEmpty()) {
                        GroupedRow(
                            title = stringResource(R.string.nearby_cell_empty),
                            titleColor = AppTheme.colors.secondaryLabel,
                        )
                    }
                    serving.forEach { cell -> CellRow(cell) { actions.onSelect(cell.selectionKey()) } }
                }
            }
            if (neighbours.isNotEmpty()) {
                item(key = "cell-neighbours") {
                    // Neighbours are read with the cell in use, every few seconds.
                    val now = rememberNow()
                    GroupedSection(
                        modifier = Modifier.animated(this, animate),
                        header = stringResource(R.string.nearby_cell_neighbours),
                        footer = state.updatedAt?.let { stringResource(R.string.nearby_updated, agoText(it, now)) },
                    ) {
                        neighbours.forEach { cell -> CellRow(cell) { actions.onSelect(cell.selectionKey()) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellRow(cell: CellTower, onClick: () -> Unit) {
    val resources = LocalResources.current
    val copy = rememberCopyText()
    GroupedRow(
        title = resources.cellTech(cell.tech),
        titleNote = cell.operator,
        subtitle = cellSummary(cell).ifEmpty { null },
        trailing = { SignalValue(cell.dbm, cell.level) },
        onClick = onClick,
        onLongClick = cell.cellId?.let { id -> { copy(id.toString()) } },
    )
}

/** Every number the modem reported for one cell. */
@Composable
internal fun CellDetails(cell: CellTower, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val labels = cellLabels(cell.tech)
    val title = resources.cellTech(cell.tech)
    InfoDialog(title = title, onDismiss = onDismiss) {
        val width = 112.dp
        val role = if (cell.registered) R.string.nearby_cell_serving_one else R.string.nearby_cell_neighbour_one
        InfoItem(stringResource(R.string.nearby_cell_role), stringResource(role), labelWidth = width)
        cell.operator?.let { InfoItem(stringResource(R.string.nearby_cell_operator), it, labelWidth = width) }
        cell.plmn()?.let { InfoItem(stringResource(R.string.nearby_cell_plmn), it, labelWidth = width) }
        cell.area?.let { InfoItem(labels.area, it.toString(), labelWidth = width) }
        cell.cellId?.let { InfoItem(labels.cell, it.toString(), labelWidth = width) }
        if (labels.code != null) cell.code?.let { InfoItem(labels.code, it.toString(), labelWidth = width) }
        if (labels.channel != null) cell.channel?.let { InfoItem(labels.channel, it.toString(), labelWidth = width) }
        if (cell.bands.isNotEmpty()) {
            InfoItem(stringResource(R.string.nearby_cell_bands), cell.bands.joinToString(", "), labelWidth = width)
        }
        cell.bandwidthKhz?.let {
            InfoItem(
                stringResource(R.string.nearby_width),
                stringResource(R.string.nearby_mhz, it / 1000),
                labelWidth = width,
            )
        }
        cell.dbm?.let {
            InfoItem(
                stringResource(R.string.nearby_signal),
                stringResource(R.string.nearby_dbm, it),
                labelWidth = width,
            )
        }
        cell.quality?.let { InfoItem(qualityLabel(cell), stringResource(R.string.nearby_db, it), labelWidth = width) }
        cell.snr?.let { InfoItem(snrLabel(cell), stringResource(R.string.nearby_db, it), labelWidth = width) }
        cell.timingAdvance?.let {
            // 5G gives it in microseconds; the others in steps of their own.
            val value = if (cell.tech == CellTech.Nr) stringResource(R.string.nearby_microseconds, it) else "$it"
            InfoItem(stringResource(R.string.nearby_cell_timing_advance), value, labelWidth = width)
        }
        // Measured by the network for the cell in use (GSM, LTE, 5G); other cells have no distance to show.
        cell.distanceMeters()?.let { meters ->
            val text = if (meters == 0) {
                stringResource(R.string.nearby_under, resources.distance(cell.distanceStepMeters() ?: 0))
            } else {
                resources.approxDistance(meters.toFloat())
            }
            InfoItem(stringResource(R.string.nearby_distance), text, labelWidth = width)
        }
    }
}

private fun qualityLabel(cell: CellTower) = if (cell.tech == CellTech.Wcdma) "Ec/No" else "RSRQ"

private fun snrLabel(cell: CellTower) = if (cell.tech == CellTech.Nr) "SINR" else "RSSNR"

/**
 * The cells around this device: closer to the centre for a stronger signal. The cell in use is at the top (two
 * with two SIMs, at the top and bottom) and names its distance when the modem reports one; neighbours are spread
 * around the circle. Phones cannot tell which direction a mast is in, so the angles are not real.
 */
@Composable
private fun CellDiagram(
    cells: List<CellTower>,
    fold: Fold,
    modifier: Modifier,
    onSelect: (key: String) -> Unit,
) {
    val resources = LocalResources.current
    var servingSeen = 0
    val points = cells.map { cell ->
        val distance = cell.distanceMeters()?.let { meters -> resources.distance(meters) }
        // The cell in use by its network's name, neighbours by the code that tells them apart.
        val tag = if (cell.registered) cell.operator else cell.code?.toString()
        val name = listOfNotNull(cellTechShort(cell.tech), tag).joinToString(" ")
        RangePoint(
            key = cell.selectionKey(),
            label = listOfNotNull(name, distance).joinToString(" · "),
            distance = signalDistance(cell.dbm),
            angle = if (cell.registered) {
                180f * servingSeen++
            } else {
                stableAngle("${cell.tech}${cell.cellId}${cell.code}")
            },
            emphasized = cell.registered,
        )
    }
    GroupedSection(
        modifier = modifier,
        header = stringResource(R.string.nearby_cell_map),
        footer = stringResource(R.string.nearby_cell_map_hint),
        expanded = !fold.folded,
        headerAction = { FoldButton(fold) },
    ) {
        RangeDiagram(
            points = points,
            rings = SignalRings.map { dbm -> RangeRing(signalDistance(dbm), stringResource(R.string.nearby_dbm, dbm)) },
            centerLabel = stringResource(R.string.nearby_this_device),
            description = stringResource(R.string.nearby_cell_map_hint),
            modifier = Modifier.padding(8.dp),
            onSelect = onSelect,
        )
    }
}

/** Where a signal sits on the diagram: -50 dBm or stronger near the centre, -130 dBm at the edge. */
private fun signalDistance(dbm: Int?): Float = if (dbm == null) 1f else ((-dbm - 50) / 80f).coerceIn(0.12f, 1f)

private val SignalRings = listOf(-70, -90, -110, -130)

private fun cellTechShort(tech: CellTech): String = when (tech) {
    CellTech.Gsm -> "2G"
    CellTech.Wcdma, CellTech.TdScdma, CellTech.Cdma -> "3G"
    CellTech.Lte -> "4G"
    CellTech.Nr -> "5G"
}
