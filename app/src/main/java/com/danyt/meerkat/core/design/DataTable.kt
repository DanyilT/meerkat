package com.danyt.meerkat.core.design

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R

/**
 * One column of a [DataTable].
 *
 * @property title header text.
 * @property weight share of the table width, relative to the other columns.
 * @property alignEnd right-align the column, for numbers.
 * @property sortable whether tapping the header requests sorting by this column.
 */
@Immutable
data class DataColumn(
    val title: String,
    val weight: Float = 1f,
    val alignEnd: Boolean = false,
    val sortable: Boolean = false,
)

/** Which column a [DataTable] is sorted by, and in which direction. */
@Immutable
data class SortState(val columnIndex: Int, val ascending: Boolean = true)

/**
 * A simple table on a card: header row, then rows separated by hairlines.
 *
 * The table only displays data. To support sorting, mark columns as sortable, keep a [SortState], and pass
 * [rows] already sorted. Tapping a sortable header calls [onSortChange] with the new state: the same column
 * flips direction, a different column starts ascending.
 *
 * @param rows cell text per row, in the same order as [columns].
 * @param onRowLongClick called with a row's index on a long press, for example to copy the row.
 * @param sort current sort, shown as an arrow in the header.
 */
@Composable
fun DataTable(
    columns: List<DataColumn>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    sort: SortState? = null,
    onSortChange: ((SortState) -> Unit)? = null,
    onRowLongClick: ((Int) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(colors.card).border(1.dp, colors.outline, shape)) {
        TableRow(minHeight = 36) {
            columns.forEachIndexed { index, column ->
                val sorted = sort?.columnIndex == index
                val click = if (column.sortable && onSortChange != null) {
                    val sortLabel = stringResource(R.string.sort_by, column.title)
                    Modifier.clickable(role = Role.Button, onClickLabel = sortLabel) {
                        onSortChange(SortState(index, ascending = if (sorted) !sort.ascending else true))
                    }
                } else {
                    Modifier
                }
                Row(
                    modifier = Modifier.weight(column.weight).then(click).padding(vertical = 4.dp),
                    horizontalArrangement = if (column.alignEnd) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = column.title,
                        style = AppTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (sorted) colors.accent else colors.secondaryLabel,
                        maxLines = 1,
                    )
                    if (sorted) {
                        Icon(
                            imageVector = if (sort.ascending) AppIcons.ChevronUp else AppIcons.ChevronDown,
                            contentDescription = stringResource(
                                if (sort.ascending) R.string.sort_ascending else R.string.sort_descending,
                            ),
                            tint = colors.accent,
                            modifier = Modifier.padding(start = 3.dp).size(11.dp),
                        )
                    }
                }
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            Hairline()
            val press = if (onRowLongClick != null) {
                Modifier.combinedClickable(onLongClick = { onRowLongClick(rowIndex) }, onClick = {})
            } else {
                Modifier
            }
            TableRow(minHeight = 44, modifier = press) {
                columns.forEachIndexed { index, column ->
                    Text(
                        text = row.getOrElse(index) { "" },
                        style = AppTheme.typography.subhead,
                        color = colors.label,
                        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(column.weight),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(minHeight: Int, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Preview
@Composable
private fun DataTablePreview() {
    AppTheme {
        var sort by remember { mutableStateOf(SortState(0)) }
        val data = listOf(listOf("22", "ssh", "3 ms"), listOf("80", "http", "5 ms"), listOf("443", "https", "4 ms"))
        val rows = if (sort.ascending) data else data.reversed()
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            DataTable(
                columns = listOf(
                    DataColumn("Port", sortable = true),
                    DataColumn("Service", weight = 2f),
                    DataColumn("Latency", alignEnd = true),
                ),
                rows = rows,
                sort = sort,
                onSortChange = { sort = it },
            )
        }
    }
}
