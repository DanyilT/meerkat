package com.danyt.meerkat.feature.reports

import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.InfoHeading
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.LoadingIndicator
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportKind
import com.danyt.meerkat.core.reports.formatReportDate
import com.danyt.meerkat.core.reports.readDocument
import kotlinx.coroutines.launch

/** The saved reports, newest first, with a way to bring in a report that was exported as JSON. */
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    intros: Intros,
    onBack: () -> Unit,
    onOpenReport: (id: String) -> Unit,
) {
    val intro = rememberIntro(intros, IntroKeys.REPORTS)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.import(readDocument(context, uri)) }
    }
    // Some file managers label .json files as plain text or as unknown data, so all three are offered.
    val import = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    ReportsContent(state.reports, onBack, import, onOpenReport, onInfo = intro.open.takeIf { intro.enabled })

    IntroDialog(intro, stringResource(R.string.feature_reports_title)) {
        InfoText(stringResource(R.string.intro_reports_what))
        InfoHeading(stringResource(R.string.intro_features))
        InfoText(stringResource(R.string.intro_reports_features))
        InfoText(stringResource(R.string.intro_reports_encrypted))
    }

    state.duplicate?.let { report ->
        ConfirmDialog(
            title = stringResource(R.string.report_duplicate_title),
            message = stringResource(R.string.report_duplicate_import_message, report.title),
            confirmLabel = stringResource(R.string.report_duplicate_import_confirm),
            onConfirm = { viewModel.resolveDuplicate(keep = true) },
            onDismiss = { viewModel.resolveDuplicate(keep = false) },
        )
    }
    state.importError?.let { error ->
        ConfirmDialog(
            title = stringResource(R.string.reports_import_failed),
            message = stringResource(
                when (error) {
                    ImportError.Unreadable -> R.string.reports_import_unreadable
                    ImportError.NotAReport -> R.string.reports_import_not_report
                    ImportError.NotSaved -> R.string.report_save_failed_message
                },
            ),
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = null,
            onConfirm = viewModel::onImportErrorShown,
            onDismiss = viewModel::onImportErrorShown,
        )
    }
}

@Composable
private fun ReportsContent(
    reports: List<Report>?,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenReport: (String) -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    AppScaffold(
        title = stringResource(R.string.feature_reports_title),
        onTitleClick = onBack,
        actions = {
            onInfo?.let { IconButton(AppIcons.Info, stringResource(R.string.intro_button), it) }
            IconButton(AppIcons.Plus, stringResource(R.string.reports_import), onImport)
        },
    ) {
        when {
            reports == null -> item {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            reports.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.reports_empty_title),
                    icon = AppIcons.Reports,
                    message = stringResource(R.string.reports_empty_message),
                    action = { PrimaryButton(stringResource(R.string.reports_import), onImport) },
                    modifier = Modifier.fillParentMaxHeight(0.7f),
                )
            }
            else -> {
                // One section per tool, in the order the tools are listed; the list keeps its newest-first order
                // within each. The footer goes under the last section.
                val groups = reports.groupBy { it.kind }.toSortedMap(compareBy { it.ordinal })
                val last = groups.lastKey()
                groups.forEach { (kind, kindReports) ->
                    item(key = kind.code) {
                        GroupedSection(
                            header = stringResource(kind.label),
                            footer = if (kind == last) stringResource(R.string.reports_footer) else null,
                        ) {
                            kindReports.forEach { report ->
                                GroupedRow(
                                    title = report.displayTitle,
                                    subtitle = formatReportDate(report.createdAt),
                                    icon = kind.icon,
                                    chevron = true,
                                    onClick = { onOpenReport(report.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val ReportKind.label: Int
    get() = when (this) {
        ReportKind.Lan -> R.string.feature_lan_title
        ReportKind.Ports -> R.string.feature_ports_title
        ReportKind.Wifi -> R.string.report_kind_wifi
        ReportKind.Bluetooth -> R.string.report_kind_bluetooth
        ReportKind.Cell -> R.string.report_kind_cell
        ReportKind.Nfc -> R.string.report_kind_nfc
    }

private val ReportKind.icon
    get() = when (this) {
        ReportKind.Lan -> AppIcons.Lan
        ReportKind.Ports -> AppIcons.Ports
        ReportKind.Wifi -> AppIcons.Wifi
        ReportKind.Bluetooth -> AppIcons.Bluetooth
        ReportKind.Cell -> AppIcons.Cell
        ReportKind.Nfc -> AppIcons.Nfc
    }

@Preview
@Composable
private fun ReportsScreenPreview() {
    val reports = listOf(
        Report("a", ReportKind.Lan, "192.168.1.0/24", 1_780_000_000_000, emptyList(), emptyList(), emptyList()),
        Report("b", ReportKind.Ports, "192.168.1.20", 1_779_000_000_000, emptyList(), emptyList(), emptyList()),
    )
    AppTheme { ReportsContent(reports, onBack = {}, onImport = {}, onOpenReport = {}) }
}
