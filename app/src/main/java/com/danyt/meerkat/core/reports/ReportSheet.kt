package com.danyt.meerkat.core.reports

import androidx.compose.runtime.saveable.rememberSaveable
import com.danyt.meerkat.core.design.LocalSheetClose
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.BottomSheet
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.Note
import kotlinx.coroutines.launch

/**
 * The choices for a report: keep it in the app, or write it to a file the user picks.
 *
 * Exports go through the system file picker, so the app needs no storage permission and the user decides
 * where the file lands. Exported files are not encrypted.
 *
 * The sheet uses the report's fingerprint to spot copies: it asks before saving a report that is already in
 * the app, and says so when the report was exported before.
 *
 * @param hashes the fingerprints of saved and exported reports, from [ReportStore.hashes].
 * @param onExported called with the report's fingerprint after it was written to a file.
 * @param onSave saves the report inside the app. The sheet closes itself afterwards. Null hides that choice,
 *   for a report opened from the app.
 */
@Composable
fun ReportSheet(
    report: Report,
    hashes: ReportHashes,
    onDismiss: () -> Unit,
    onExported: (hash: String) -> Unit,
    onSave: (() -> Unit)? = null,
) {
    BottomSheet(onDismiss = onDismiss, title = stringResource(R.string.report_sheet_title)) {
        ReportChoices(report, hashes, onExported, onSave)
    }
}

/**
 * The report sheet for a tool screen, together with the message that follows a save.
 *
 * After a successful save the message offers to open the saved report. After a failed one it says so.
 *
 * @param visible whether the sheet is open.
 * @param report builds the report from the tool's current results. Called when the sheet opens and on save.
 * @param onSave stores a report and reports back the stored copy, or null when storing failed.
 * @param onOpenReport opens the saved report with the given id.
 */
@Composable
fun ToolReportSheet(
    visible: Boolean,
    report: () -> Report,
    hashes: ReportHashes,
    onDismiss: () -> Unit,
    onExported: (hash: String) -> Unit,
    onSave: (Report, onResult: (Report?) -> Unit) -> Unit,
    onOpenReport: (id: String) -> Unit,
) {
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    if (visible) {
        ReportSheet(
            report = remember { report() },
            hashes = hashes,
            onDismiss = onDismiss,
            onExported = onExported,
            onSave = {
                onSave(report()) { stored ->
                    savedId = stored?.id
                    failed = stored == null
                }
            },
        )
    }
    savedId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.report_saved_title),
            message = stringResource(R.string.report_saved_message),
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = stringResource(R.string.report_open),
            onConfirm = { savedId = null },
            onSecondary = {
                savedId = null
                onOpenReport(id)
            },
            onDismiss = { savedId = null },
        )
    }
    if (failed) {
        ConfirmDialog(
            title = stringResource(R.string.report_save_failed_title),
            message = stringResource(R.string.report_save_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = null,
            onConfirm = { failed = false },
            onDismiss = { failed = false },
        )
    }
}

@Composable
private fun ReportChoices(report: Report, hashes: ReportHashes, onExported: (String) -> Unit, onSave: (() -> Unit)?) {
    // Closes the sheet with its animation. Removing it directly would make it vanish.
    val close = LocalSheetClose.current
    val hash = remember(report) { report.contentHash() }
    val alreadySaved = hash in hashes.saved
    var failed by remember { mutableStateOf(false) }
    var confirmCopy by remember { mutableStateOf(false) }
    val onWritten = { ok: Boolean ->
        if (ok) {
            onExported(hash)
            close()
        } else {
            failed = true
        }
    }
    val exportText = rememberExporter("text/plain", onWritten) { report.toText() }
    val exportCsv = rememberExporter("text/csv", onWritten) { report.toCsv() }
    val exportJson = rememberExporter("application/json", onWritten) { report.toJson() }
    val name = report.fileName()
    val save = {
        onSave?.invoke()
        close()
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (onSave != null) {
            GroupedRow(
                title = stringResource(R.string.report_save),
                subtitle = stringResource(
                    if (alreadySaved) R.string.report_save_duplicate else R.string.report_save_description,
                ),
                titleColor = AppTheme.colors.accent,
                onClick = { if (alreadySaved) confirmCopy = true else save() },
            )
        }
        GroupedRow(
            title = stringResource(R.string.report_export_text),
            subtitle = stringResource(R.string.report_export_text_description),
            titleColor = AppTheme.colors.accent,
            onClick = { exportText("$name.txt") },
        )
        GroupedRow(
            title = stringResource(R.string.report_export_csv),
            subtitle = stringResource(R.string.report_export_csv_description),
            titleColor = AppTheme.colors.accent,
            onClick = { exportCsv("$name.csv") },
        )
        GroupedRow(
            title = stringResource(R.string.report_export_json),
            subtitle = stringResource(R.string.report_export_json_description),
            titleColor = AppTheme.colors.accent,
            onClick = { exportJson("$name.json") },
        )
        Note(
            text = stringResource(
                when {
                    failed -> R.string.report_export_failed
                    hash in hashes.exported -> R.string.report_export_duplicate
                    else -> R.string.report_export_note
                },
            ),
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
        )
    }

    if (confirmCopy) {
        ConfirmDialog(
            title = stringResource(R.string.report_duplicate_title),
            message = stringResource(R.string.report_duplicate_save_message),
            confirmLabel = stringResource(R.string.report_duplicate_save_confirm),
            onConfirm = {
                confirmCopy = false
                save()
            },
            onDismiss = { confirmCopy = false },
        )
    }
}

/**
 * Returns a function that asks the user where to save a file of [mimeType], given a suggested name, and
 * writes [content] there. [onDone] gets true on success and false on failure. Backing out of the picker
 * reports nothing.
 */
@Composable
private fun rememberExporter(mimeType: String, onDone: (Boolean) -> Unit, content: () -> String): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val currentOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri != null) scope.launch { currentOnDone(writeDocument(context, uri, currentContent())) }
    }
    return { fileName -> launcher.launch(fileName) }
}
