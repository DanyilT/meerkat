package com.danyt.meerkat.feature.reports

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.DataColumn
import com.danyt.meerkat.core.design.DataTable
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.TextField
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportKind
import com.danyt.meerkat.core.reports.ReportSheet
import com.danyt.meerkat.core.reports.contentHash
import com.danyt.meerkat.core.reports.formatReportDate

/** One saved report: its details and its table, with edit, export, and delete. */
@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val report = state.report

    // Back while editing leaves edit mode and drops the changes.
    BackHandler(enabled = editing) { editing = false }

    ReportContent(
        state = state,
        edit = if (editing) ReportEdit(name, notes, onName = { name = it }, onNotes = { notes = it }) else null,
        actions = ReportActions(
            onBack = onBack,
            onEdit = {
                name = report?.name.orEmpty()
                notes = report?.description.orEmpty()
                editing = true
            },
            onCancelEdit = { editing = false },
            onSaveEdit = {
                viewModel.rename(name, notes)
                editing = false
            },
            onExport = { showExport = true },
            onDelete = { confirmDelete = true },
        ),
    )

    if (showExport && report != null) {
        ReportSheet(
            report = report,
            hashes = state.hashes,
            onDismiss = { showExport = false },
            onExported = viewModel::markExported,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.report_delete_title),
            message = stringResource(R.string.report_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The title and notes while they are being edited. */
private class ReportEdit(
    val name: String,
    val notes: String,
    val onName: (String) -> Unit,
    val onNotes: (String) -> Unit,
)

private class ReportActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onSaveEdit: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@Composable
private fun ReportContent(state: ReportUiState, edit: ReportEdit?, actions: ReportActions) {
    val report = state.report
    val animate = AppTheme.animationsEnabled
    val copy = rememberCopyText()
    // While editing, the large title follows what is typed.
    val title = when {
        report == null -> stringResource(R.string.report_title)
        edit != null -> edit.name.ifBlank { report.title }
        else -> report.displayTitle
    }
    AppScaffold(
        title = title,
        onTitleClick = actions.onBack,
        actions = { ReportBarButtons(editing = edit != null, enabled = report != null, actions) },
    ) {
        if (report == null) {
            if (state.loaded) {
                item { Note(stringResource(R.string.report_missing), Modifier.padding(horizontal = 16.dp)) }
            }
            return@AppScaffold
        }
        // The notes under the title, which turn into the title and notes fields in edit mode.
        item(key = "head") {
            AnimatedContent(
                targetState = edit != null,
                transitionSpec = {
                    if (animate) {
                        (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "reportHead",
            ) { editing ->
                if (editing && edit != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextField(
                            value = edit.name,
                            onValueChange = edit.onName,
                            label = stringResource(R.string.report_edit_title),
                            placeholder = report.title,
                            clearable = true,
                        )
                        TextField(
                            value = edit.notes,
                            onValueChange = edit.onNotes,
                            label = stringResource(R.string.report_edit_notes),
                            placeholder = stringResource(R.string.report_edit_notes_placeholder),
                            singleLine = false,
                        )
                    }
                } else if (report.description.isNotBlank()) {
                    Text(
                        text = report.description,
                        style = AppTheme.typography.subhead,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp),
                    )
                } else {
                    // Keeps the animation anchored when there are no notes to show.
                    Row(Modifier.fillMaxWidth()) {}
                }
            }
        }
        item(key = "details") {
            GroupedSection(
                header = stringResource(R.string.device_details),
                footer = stringResource(R.string.report_copy_hint),
            ) {
                val date = formatReportDate(report.createdAt)
                GroupedRow(stringResource(R.string.report_saved_on), value = date, onLongClick = { copy(date) })
                report.details.forEach { (label, value) ->
                    GroupedRow(label, value = value, onLongClick = { copy(value) })
                }
                // The start of the fingerprint is enough to compare two reports by eye. Copying gives all of it.
                val hash = remember(report) { report.contentHash() }
                GroupedRow(
                    title = stringResource(R.string.report_fingerprint),
                    value = hash.take(FINGERPRINT_SHOWN),
                    onLongClick = { copy(hash) },
                )
            }
        }
        if (report.rows.isNotEmpty()) {
            item(key = "table") {
                DataTable(
                    columns = report.columns.map { DataColumn(it) },
                    rows = report.rows,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp),
                    onRowLongClick = { index -> copy(report.rows[index].joinToString("\t")) },
                )
            }
        }
    }
}

/** The top-right buttons. The normal set and the edit-mode set cross-fade when edit mode starts or ends. */
@Composable
private fun ReportBarButtons(editing: Boolean, enabled: Boolean, actions: ReportActions) {
    val animate = AppTheme.animationsEnabled
    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            if (animate) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        contentAlignment = Alignment.CenterEnd,
        label = "reportActions",
    ) { inEditMode ->
        Row {
            if (inEditMode) {
                IconButton(AppIcons.Close, stringResource(R.string.report_edit_cancel), actions.onCancelEdit)
                IconButton(AppIcons.Check, stringResource(R.string.report_edit_done), actions.onSaveEdit)
            } else {
                IconButton(AppIcons.Edit, stringResource(R.string.report_edit), actions.onEdit, enabled = enabled)
                IconButton(AppIcons.Export, stringResource(R.string.report_export), actions.onExport, enabled = enabled)
                IconButton(AppIcons.Trash, stringResource(R.string.action_delete), actions.onDelete, enabled = enabled)
            }
        }
    }
}

private const val FINGERPRINT_SHOWN = 16

private val PreviewReport = Report(
    id = "a",
    kind = ReportKind.Ports,
    title = "192.168.1.20",
    createdAt = 1_780_000_000_000,
    details = listOf("Protocol" to "TCP", "Ports checked" to "100 / 100"),
    columns = listOf("Port", "Service"),
    rows = listOf(listOf("22", "SSH"), listOf("443", "HTTPS")),
    name = "NAS before the firmware update",
    description = "Baseline scan. Compare after updating.",
)

@Preview
@Composable
private fun ReportScreenPreview() {
    AppTheme { ReportContent(ReportUiState(PreviewReport, loaded = true), edit = null, ReportActions()) }
}

@Preview
@Composable
private fun ReportScreenEditingPreview() {
    AppTheme {
        val edit = ReportEdit(PreviewReport.name, PreviewReport.description, onName = {}, onNotes = {})
        ReportContent(ReportUiState(PreviewReport, loaded = true), edit, ReportActions())
    }
}
